(function () {
  "use strict";

  const params = new URLSearchParams(window.location.hash.replace(/^#/, ""));
  const raw = params.get("textech_sso");
  if (!raw) return;

  try {
    const session = JSON.parse(raw);
    if (!session || typeof session.token !== "string" || typeof session.username !== "string") {
      throw new Error("invalid portal session payload");
    }
    localStorage.setItem("token", session.token);
    localStorage.setItem("user", session.username);
    document.cookie = `astrbot_dashboard_jwt=${encodeURIComponent(session.token)}; Path=/; Max-Age=43200; Secure; SameSite=Lax`;
    window.history.replaceState({}, "", `${window.location.pathname}${window.location.search}`);
    window.location.replace("/dashboard/default");
  } catch (_error) {
    window.history.replaceState({}, "", `${window.location.pathname}${window.location.search}`);
  }
})();
