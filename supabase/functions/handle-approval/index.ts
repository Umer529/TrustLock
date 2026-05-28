// Deploy: supabase functions deploy handle-approval --no-verify-jwt
// After deploying, enable GitHub Pages on the repo (Settings → Pages → source: main /docs)
// The result page lives at: https://umer529.github.io/TrustLock/result.html

import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const RESULT_PAGE = "https://umer529.github.io/TrustLock/result.html"

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, {
      status: 204,
      headers: { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Methods": "GET,OPTIONS" },
    })
  }

  const url    = new URL(req.url)
  const id     = url.searchParams.get("id")
  const action = url.searchParams.get("action")

  // Redirect invalid requests to the page with an error hash
  if (!id || (action !== "APPROVED" && action !== "DENIED")) {
    return Response.redirect(`${RESULT_PAGE}#invalid`, 302)
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
    console.error("DB error:", error)
    return Response.redirect(`${RESULT_PAGE}#error`, 302)
  }

  // Redirect to the styled GitHub Pages result page
  const hash = action === "APPROVED" ? "approved" : "denied"
  return Response.redirect(`${RESULT_PAGE}#${hash}`, 302)
})
