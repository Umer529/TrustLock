// Deploy with: supabase functions deploy handle-approval
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are injected automatically.

import { serve }        from "https://deno.land/std@0.177.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

serve(async (req: Request) => {
  const url    = new URL(req.url)
  const id     = url.searchParams.get("id")
  const action = url.searchParams.get("action")

  if (!id || (action !== "APPROVED" && action !== "DENIED")) {
    return new Response("Invalid request", { status: 400 })
  }

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  )

  const { error } = await supabase
    .from("approval_requests")
    .update({ status: action })
    .eq("id", id)

  if (error) {
    console.error(error)
    return new Response("Failed to update request", { status: 500 })
  }

  const approved = action === "APPROVED"
  const verb     = approved ? "Approved" : "Denied"
  const color    = approved ? "#4CAF50" : "#f44336"
  const icon     = approved ? "&#10003;" : "&#10007;"

  return new Response(
    `<!DOCTYPE html>
<html>
<body style="font-family:Arial,sans-serif;text-align:center;padding:60px 20px;background:#f4f4f4;margin:0;">
  <div style="max-width:400px;margin:auto;background:white;border-radius:10px;padding:40px;
              box-shadow:0 2px 8px rgba(0,0,0,0.08);">
    <div style="font-size:56px;margin-bottom:16px;">${icon}</div>
    <h2 style="color:${color};margin:0 0 12px;">Request ${verb}</h2>
    <p style="color:#555;margin:0;">The user has been notified of your decision.</p>
    <p style="color:#999;font-size:13px;margin-top:20px;">You can safely close this tab.</p>
  </div>
</body>
</html>`,
    { headers: { "Content-Type": "text/html" } },
  )
})
