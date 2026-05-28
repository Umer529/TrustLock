// Deploy: supabase functions deploy send-approval-email
// Secrets: BREVO_API_KEY, SENDER_EMAIL

import { serve } from "https://deno.land/std@0.177.0/http/server.ts"

const BREVO_API_KEY = Deno.env.get("BREVO_API_KEY")!
const SENDER_EMAIL  = Deno.env.get("SENDER_EMAIL")!
const SUPABASE_URL  = Deno.env.get("SUPABASE_URL")!

serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 })
  }

  try {
    const { requestId, guardianEmail, wardName, description, isAlert } = await req.json()

    let subject: string
    let html: string

    if (isAlert) {
      // ── Security alert — no approve/deny buttons ────────────────────────────
      subject = `⚠ ScreenPact Security Alert — ${wardName}`
      html = alertEmail(wardName, description)
    } else {
      // ── Standard approval request ───────────────────────────────────────────
      const base       = `${SUPABASE_URL}/functions/v1/handle-approval`
      const approveUrl = `${base}?id=${requestId}&action=APPROVED`
      const denyUrl    = `${base}?id=${requestId}&action=DENIED`
      subject = `${wardName} needs your approval — ScreenPact`
      html = approvalEmail(wardName, description, approveUrl, denyUrl)
    }

    const res = await fetch("https://api.brevo.com/v3/smtp/email", {
      method: "POST",
      headers: {
        "api-key":      BREVO_API_KEY,
        "Content-Type": "application/json",
        "Accept":       "application/json",
      },
      body: JSON.stringify({
        sender:      { name: "ScreenPact", email: SENDER_EMAIL },
        to:          [{ email: guardianEmail }],
        subject,
        htmlContent: html,
      }),
    })

    if (!res.ok) {
      const body = await res.text()
      console.error("Brevo error:", body)
      return new Response(JSON.stringify({ error: body }), { status: 500 })
    }

    return new Response(JSON.stringify({ ok: true }), {
      headers: { "Content-Type": "application/json" },
    })
  } catch (e) {
    console.error(e)
    return new Response(JSON.stringify({ error: String(e) }), { status: 500 })
  }
})

// ── Email templates ───────────────────────────────────────────────────────────

function baseWrapper(content: string): string {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width,initial-scale=1"/>
  <title>ScreenPact</title>
</head>
<body style="margin:0;padding:0;background:#f0f2f5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;">
  <table width="100%" cellpadding="0" cellspacing="0" style="padding:40px 16px;">
    <tr><td align="center">
      <table width="100%" cellpadding="0" cellspacing="0" style="max-width:520px;">

        <!-- Header -->
        <tr>
          <td style="background:#1a1a2e;border-radius:12px 12px 0 0;padding:28px 32px;text-align:center;">
            <span style="font-size:22px;font-weight:700;color:#9c6fff;letter-spacing:0.5px;">ScreenPact</span>
          </td>
        </tr>

        <!-- Body -->
        <tr>
          <td style="background:#ffffff;padding:32px;border-radius:0 0 12px 12px;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
            ${content}
          </td>
        </tr>

        <!-- Footer -->
        <tr>
          <td style="padding:20px 0;text-align:center;">
            <p style="margin:0;font-size:11px;color:#aaa;">
              ScreenPact parental control &nbsp;&middot;&nbsp; If you didn't expect this email, you can ignore it.
            </p>
          </td>
        </tr>

      </table>
    </td></tr>
  </table>
</body>
</html>`
}

function approvalEmail(
  wardName: string,
  description: string,
  approveUrl: string,
  denyUrl: string,
): string {
  return baseWrapper(`
    <p style="margin:0 0 6px;font-size:20px;font-weight:700;color:#1a1a2e;">
      Approval Request
    </p>
    <p style="margin:0 0 24px;font-size:14px;color:#888;">
      <strong style="color:#555;">${wardName}</strong> is requesting your approval.
    </p>

    <div style="background:#f7f5ff;border-left:4px solid #9c6fff;border-radius:6px;padding:16px 20px;margin-bottom:28px;">
      <p style="margin:0;font-size:15px;color:#333;line-height:1.5;">&#128203; ${description}</p>
    </div>

    <table cellspacing="0" cellpadding="0" style="width:100%;">
      <tr>
        <td style="padding-right:8px;width:50%;">
          <a href="${approveUrl}"
             style="display:block;background:#4CAF50;color:#ffffff;text-align:center;padding:14px;border-radius:8px;font-size:15px;font-weight:600;text-decoration:none;">
            &#10003;&nbsp; Approve
          </a>
        </td>
        <td style="padding-left:8px;width:50%;">
          <a href="${denyUrl}"
             style="display:block;background:#f44336;color:#ffffff;text-align:center;padding:14px;border-radius:8px;font-size:15px;font-weight:600;text-decoration:none;">
            &#10007;&nbsp; Deny
          </a>
        </td>
      </tr>
    </table>

    <p style="margin:20px 0 0;font-size:12px;color:#bbb;text-align:center;">
      Tap a button above to respond. Your decision is final.
    </p>
  `)
}

function alertEmail(wardName: string, description: string): string {
  return baseWrapper(`
    <p style="margin:0 0 6px;font-size:20px;font-weight:700;color:#e65100;">
      &#9888; Security Alert
    </p>
    <p style="margin:0 0 24px;font-size:14px;color:#888;">
      Action may be required for <strong style="color:#555;">${wardName}</strong>
    </p>

    <div style="background:#fff8f0;border-left:4px solid #e65100;border-radius:6px;padding:16px 20px;margin-bottom:24px;">
      <p style="margin:0;font-size:15px;color:#333;line-height:1.5;">&#128274; ${description}</p>
    </div>

    <p style="margin:0;font-size:13px;color:#888;line-height:1.6;">
      No action is required from you right now, but you may want to follow up with
      <strong>${wardName}</strong> to make sure this was intentional.
    </p>
  `)
}
