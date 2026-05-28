// Deploy: supabase functions deploy send-approval-email
// Secret: supabase secrets set BREVO_API_KEY=your_key_here
//         supabase secrets set SENDER_EMAIL=your_verified_email@gmail.com

import { serve } from "https://deno.land/std@0.177.0/http/server.ts"

const BREVO_API_KEY = Deno.env.get("BREVO_API_KEY")!
const SENDER_EMAIL  = Deno.env.get("SENDER_EMAIL")!
const SUPABASE_URL  = Deno.env.get("SUPABASE_URL")!

serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 })
  }

  try {
    const { requestId, guardianEmail, wardName, description } = await req.json()

    const base       = `${SUPABASE_URL}/functions/v1/handle-approval`
    const approveUrl = `${base}?id=${requestId}&action=APPROVED`
    const denyUrl    = `${base}?id=${requestId}&action=DENIED`

    const html = `<!DOCTYPE html>
<html>
<body style="font-family:Arial,sans-serif;background:#f4f4f4;padding:30px;margin:0;">
  <div style="max-width:520px;margin:auto;background:white;border-radius:10px;padding:32px;box-shadow:0 2px 8px rgba(0,0,0,0.08);">
    <h2 style="margin:0 0 8px;color:#1a1a2e;">ScreenPact — Approval Request</h2>
    <p style="color:#555;margin:0 0 20px;">
      <strong>${wardName}</strong> is requesting your approval.
    </p>
    <div style="background:#f0f0f8;border-radius:8px;padding:16px;margin-bottom:24px;">
      <p style="margin:0;color:#333;font-size:15px;">&#128203; ${description}</p>
    </div>
    <table cellspacing="0" cellpadding="0"><tr>
      <td style="padding-right:12px;">
        <a href="${approveUrl}"
           style="display:inline-block;background:#4CAF50;color:white;padding:12px 28px;
                  text-decoration:none;border-radius:6px;font-size:15px;font-weight:bold;">
          &#10003; Approve
        </a>
      </td>
      <td>
        <a href="${denyUrl}"
           style="display:inline-block;background:#f44336;color:white;padding:12px 28px;
                  text-decoration:none;border-radius:6px;font-size:15px;font-weight:bold;">
          &#10007; Deny
        </a>
      </td>
    </tr></table>
    <p style="color:#999;font-size:12px;margin-top:28px;border-top:1px solid #eee;padding-top:16px;">
      Sent by ScreenPact parental control. If you did not expect this, you can ignore this email.
    </p>
  </div>
</body>
</html>`

    const res = await fetch("https://api.brevo.com/v3/smtp/email", {
      method: "POST",
      headers: {
        "api-key": BREVO_API_KEY,
        "Content-Type": "application/json",
        "Accept": "application/json",
      },
      body: JSON.stringify({
        sender: { name: "ScreenPact", email: SENDER_EMAIL },
        to: [{ email: guardianEmail }],
        subject: `${wardName} needs your approval on ScreenPact`,
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
