/**
 * WoW Update Monitor — Cloudflare Worker with FCM Push Notifications
 */

const BLIZZARD_BASE = "http://us.patch.battle.net:1119";

const GAME_ENDPOINTS = {
  anniversary: { path: "/wow_anniversary", name: "ANNIVERSARY / TBC" },
  mop: { path: "/wow_classic", name: "MOP CLASSIC" },
  era: { path: "/wow_classic_era", name: "ERA CLASSIC" },
};

const REGIONS_ORDER = ["us", "eu", "cn", "tw", "kr", "sg"];

// ─── Blizzard Fetch ─────────────────────────────────────

async function fetchBlizzard(path) {
  const [vResp, cResp] = await Promise.all([
    fetch(`${BLIZZARD_BASE}${path}/versions`),
    fetch(`${BLIZZARD_BASE}${path}/cdns`),
  ]);
  if (!vResp.ok || !cResp.ok) return null;
  return { versions: await vResp.text(), cdns: await cResp.text() };
}

function parseVersions(text) {
  return text
    .split("\n")
    .filter((l) => l.trim() && !l.startsWith("##"))
    .slice(1)
    .map((line) => {
      const p = line.trim().split("|");
      return {
        region: (p[0] || "").trim(),
        buildConfig: (p[1] || "").trim(),
        cdnPath: (p[2] || "").trim(),
        buildId: (p[4] || "").trim(),
        versionsName: (p[5] || "").trim(),
      };
    })
    .filter((r) => r.region);
}

function parseCdns(text) {
  return text
    .split("\n")
    .filter((l) => l.trim() && !l.startsWith("##"))
    .slice(1)
    .map((line) => {
      const p = line.trim().split("|");
      return {
        region: (p[0] || "").trim(),
        hosts: p.length >= 5 ? `${p[3].trim()},${p[4].trim()}` : (p[3] || "").trim(),
      };
    })
    .filter((r) => r.region);
}

async function fetchAllGames() {
  const results = {};
  await Promise.all(
    Object.entries(GAME_ENDPOINTS).map(async ([key, ep]) => {
      const data = await fetchBlizzard(ep.path);
      if (!data) return;

      const versions = parseVersions(data.versions);
      const cdns = parseCdns(data.cdns);

      const regions = versions.map((v) => {
        const cdn = cdns.find((c) => c.region === v.region) || {};
        return {
          region: v.region,
          buildVersion: v.versionsName,
          buildNumber: v.buildId,
          buildConfig: v.buildConfig,
          cdnHosts: cdn.hosts || "",
          cdnPath: v.cdnPath,
        };
      });

      regions.sort((a, b) => {
        const ai = REGIONS_ORDER.indexOf(a.region);
        const bi = REGIONS_ORDER.indexOf(b.region);
        return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi);
      });

      results[key] = { key, name: ep.name, regions };
    })
  );
  return results;
}

// ─── Helper: base64 string → Uint8Array ─────────────────

function base64ToUint8Array(base64) {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

// ─── FCM ────────────────────────────────────────────────

async function getAccessToken(env) {
  const sa = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT);
  const now = Math.floor(Date.now() / 1000);

  // Build JWT header and payload
  const header = btoa(JSON.stringify({ alg: "RS256", typ: "JWT" }))
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

  const payload = btoa(
    JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    })
  )
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

  // Extract DER bytes from PEM private key
  const pemBody = sa.private_key
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s/g, "");
  const keyBytes = base64ToUint8Array(pemBody);

  // Import key and sign
  const key = await crypto.subtle.importKey(
    "pkcs8",
    keyBytes,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const enc = new TextEncoder();
  const sig = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    enc.encode(`${header}.${payload}`)
  );

  // Build JWT
  const sigBase64 = btoa(String.fromCharCode(...new Uint8Array(sig)))
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  const jwt = `${header}.${payload}.${sigBase64}`;

  // Exchange for access token
  const resp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  });

  const data = await resp.json();

  if (!data.access_token) {
    console.error("OAuth2 failed:", JSON.stringify(data));
    throw new Error(`OAuth2 error: ${data.error} - ${data.error_description}`);
  }

  return data.access_token;
}

async function sendFCM(deviceToken, title, body, data, env) {
  const accessToken = await getAccessToken(env);
  const sa = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT);

  const message = {
    message: {
      token: deviceToken,
      data: {
        title,
        body,
        ...(data || {}),
      },
      android: {
        priority: "high",
      },
    },
  };

  const resp = await fetch(
    `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(message),
    }
  );

  if (!resp.ok) {
    const errBody = await resp.text();
    console.error(`FCM send failed (${resp.status}):`, errBody);
    return false;
  }

  return true;
}

async function broadcastToTokens(env, title, body, data) {
  const tokensJson = await env.WOW_KV.get("fcm_tokens");
  if (!tokensJson) return { sent: 0, failed: 0, reason: "no tokens" };

  const tokens = JSON.parse(tokensJson);
  if (tokens.length === 0) return { sent: 0, failed: 0, reason: "empty list" };

  let sent = 0;
  let failed = 0;
  const deadTokens = [];

  for (const token of tokens) {
    try {
      const ok = await sendFCM(token, title, body, data, env);
      if (ok) sent++;
      else { failed++; deadTokens.push(token); }
    } catch (e) {
      console.error(`FCM error for token ${token.substring(0, 20)}...:`, e.message);
      failed++;
      deadTokens.push(token);
    }
  }

  if (deadTokens.length > 0) {
    const alive = tokens.filter((t) => !deadTokens.includes(t));
    await env.WOW_KV.put("fcm_tokens", JSON.stringify(alive));
  }

  return { sent, failed };
}

// ─── Telegram ──────────────────────────────────────────

async function sendTelegram(env, text) {
  const token = env.TELEGRAM_TOKEN;
  if (!token) return;
  try {
    await fetch(`https://api.telegram.org/bot${token}/sendMessage`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ chat_id: "-1004240877348", text }),
    });
  } catch (e) {
    console.error("Telegram send failed:", e.message);
  }
}

// ─── App Update Metadata ────────────────────────────────

const APP_VERSION_URL = "https://raw.githubusercontent.com/eygelias/WoWUpdateMonitor-Releases/main/version.json";

async function fetchAppVersion() {
  const resp = await fetch(`${APP_VERSION_URL}?t=${Date.now()}`, {
    headers: { "Cache-Control": "no-cache" },
  });
  if (!resp.ok) throw new Error(`GitHub version fetch failed: ${resp.status}`);
  return await resp.json();
}

async function notifyAppUpdate(env) {
  const v = await fetchAppVersion();
  await env.WOW_KV.put("latest_app_version", JSON.stringify(v));

  const title = "🔔 Nueva versión disponible";
  const body = `${v.appName || "WoWUpdateMonitor"} ${v.versionName} ya está lista. ${v.message || "Actualización obligatoria."}`;

  const result = await broadcastToTokens(env, title, body, {
    type: "app_update",
    versionCode: String(v.versionCode || ""),
    versionName: String(v.versionName || ""),
    appName: String(v.appName || "WoWUpdateMonitor"),
    apkName: String(v.apkName || ""),
    apkUrl: String(v.apkUrl || ""),
    required: String(v.required !== false),
    message: String(v.message || ""),
  });

  return { ok: true, version: v, fcm: result };
}

// ─── Change Detection ───────────────────────────────────

async function detectAndNotify(env) {
  const current = await fetchAllGames();
  if (Object.keys(current).length === 0) {
    return { error: "Failed to fetch from Blizzard" };
  }

  const lastJson = await env.WOW_KV.get("last_versions");
  const last = lastJson ? JSON.parse(lastJson) : {};

  const changes = [];

  for (const [gameKey, game] of Object.entries(current)) {
    const lastGame = last[gameKey];
    if (!lastGame) continue;

    for (const region of game.regions) {
      const lastRegion = lastGame.regions?.find((r) => r.region === region.region);
      if (!lastRegion) continue;

      if (lastRegion.buildNumber !== region.buildNumber || lastRegion.buildConfig !== region.buildConfig) {
        changes.push({
          gameName: game.name,
          gameKey,
          region: region.region,
          oldBuild: lastRegion.buildVersion,
          newBuild: region.buildVersion,
        });
      }
    }
  }

  // Only write to KV if there are changes OR first run (saves KV PUT operations)
  const isFirstRun = Object.keys(last).length === 0;
  if (changes.length > 0 || isFirstRun) {
    await env.WOW_KV.put("last_versions", JSON.stringify(current));
  }

  if (changes.length > 0) {
    const byGame = {};
    for (const c of changes) {
      if (!byGame[c.gameKey]) byGame[c.gameKey] = { name: c.gameName, changes: [] };
      byGame[c.gameKey].changes.push(c);
    }

    for (const [gameKey, info] of Object.entries(byGame)) {
      const regionLines = info.changes
        .map((c) => {
          const flag = { us: "🌎", eu: "🇪🇺", cn: "🇨🇳", tw: "🇹🇼", kr: "🇰🇷", sg: "🌏" }[c.region] || "🌍";
          return `${flag} ${c.region.toUpperCase()}: ${c.oldBuild} ➡️ ${c.newBuild}`;
        })
        .join("\n");

      const title = `🚨 ${info.name} ACTUALIZÓ`;
      const body = regionLines;

      console.log(`Sending FCM for ${gameKey}: ${title}`);
      const sendResult = await broadcastToTokens(env, title, body, {
        type: "version_change",
        game: gameKey,
        changes: JSON.stringify(info.changes),
      });
      console.log(`FCM result for ${gameKey}:`, JSON.stringify(sendResult));

      // Also send to Telegram
      await sendTelegram(env, `${title}\n${body}`);
    }
  }

  return {
    checked: Object.keys(current).length,
    changes: changes.length,
    details: changes,
  };
}

// ─── HTTP Handler ───────────────────────────────────────

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders });
    }

    // GET / — status
    if (path === "/" || path === "") {
      const tokensJson = await env.WOW_KV.get("fcm_tokens");
      const tokens = tokensJson ? JSON.parse(tokensJson) : [];
      return Response.json({
        status: "ok",
        service: "WoW Update Monitor — FCM Push",
        registeredDevices: tokens.length,
        endpoints: ["/fetch", "/register-token", "/unregister-token", "/check", "/simulate-update", "/test-fcm", "/app-version", "/notify-app-update"],
      }, { headers: corsHeaders });
    }

    // GET /fetch — fetch all (supports ?fake=1 for simulated old versions)
    if (path === "/fetch") {
      const fake = url.searchParams.get("fake");
      if (fake === "1") {
        // Return fake old versions for testing transitions
        const current = await fetchAllGames();
        const fakeOld = {};
        for (const [key, game] of Object.entries(current)) {
          fakeOld[key] = {
            key: game.key,
            name: game.name,
            regions: game.regions.map((r) => ({
              ...r,
              buildNumber: "67000",
              buildVersion: key === "anniversary" ? "2.5.4.67000" : key === "mop" ? "5.5.3.67000" : "1.15.7.67000",
              buildConfig: "fake_old_config",
            })),
          };
        }
        return Response.json(fakeOld, { headers: corsHeaders });
      }
      const data = await fetchAllGames();
      return Response.json(data, { headers: corsHeaders });
    }

    // POST /register-token
    if (path === "/register-token" && request.method === "POST") {
      const { token, regions } = await request.json();
      if (!token) return Response.json({ error: "token required" }, { status: 400, headers: corsHeaders });

      const tokensJson = await env.WOW_KV.get("fcm_tokens");
      const tokens = tokensJson ? JSON.parse(tokensJson) : [];
      if (!tokens.includes(token)) {
        tokens.push(token);
        await env.WOW_KV.put("fcm_tokens", JSON.stringify(tokens));
      }
      if (regions) await env.WOW_KV.put(`prefs_${token}`, JSON.stringify(regions));

      return Response.json({ ok: true, totalDevices: tokens.length }, { headers: corsHeaders });
    }

    // POST /unregister-token
    if (path === "/unregister-token" && request.method === "POST") {
      const { token } = await request.json();
      const tokensJson = await env.WOW_KV.get("fcm_tokens");
      const tokens = tokensJson ? JSON.parse(tokensJson) : [];
      const filtered = tokens.filter((t) => t !== token);
      await env.WOW_KV.put("fcm_tokens", JSON.stringify(filtered));
      await env.WOW_KV.delete(`prefs_${token}`);
      return Response.json({ ok: true, remaining: filtered.length }, { headers: corsHeaders });
    }

    // GET /check
    if (path === "/check") {
      const result = await detectAndNotify(env);
      // Also verify FCM send
      if (result.changes > 0) {
        const fcmTest = await broadcastToTokens(env, "📡 Check verificado", `${result.changes} cambios detectados`, { type: "check_verify" });
        result.fcmResult = fcmTest;
      }
      return Response.json(result, { headers: corsHeaders });
    }

    // GET /app-version — current app update metadata from GitHub
    if (path === "/app-version") {
      try {
        const version = await fetchAppVersion();
        return Response.json(version, { headers: corsHeaders });
      } catch (e) {
        const cached = await env.WOW_KV.get("latest_app_version");
        if (cached) return new Response(cached, { headers: { ...corsHeaders, "Content-Type": "application/json" } });
        return Response.json({ error: e.message }, { status: 502, headers: corsHeaders });
      }
    }

    // GET/POST /notify-app-update — manually notify Android devices about new app version
    if (path === "/notify-app-update") {
      try {
        const result = await notifyAppUpdate(env);
        return Response.json(result, { headers: corsHeaders });
      } catch (e) {
        return Response.json({ ok: false, error: e.message }, { status: 500, headers: corsHeaders });
      }
    }

    // GET /test-fcm — direct test push (supports ?title=...&body=... for custom messages)
    if (path === "/test-fcm") {
      const title = url.searchParams.get("title") || "🧪 Test de Notificación";
      const body = url.searchParams.get("body") || "Si ves esto, FCM funciona correctamente.";
      const result = await broadcastToTokens(env, title, body, { type: "test" });
      // Also send to Telegram
      await sendTelegram(env, `${title}\n${body}`);
      return Response.json({ ok: true, ...result }, { headers: corsHeaders });
    }

    // GET /simulate-update — store fake old versions
    if (path === "/simulate-update") {
      const current = await fetchAllGames();
      if (Object.keys(current).length === 0) {
        return Response.json({ error: "Failed to fetch from Blizzard" }, { status: 502, headers: corsHeaders });
      }

      const fakeOld = {};
      for (const [key, game] of Object.entries(current)) {
        fakeOld[key] = {
          key: game.key,
          name: game.name,
          regions: game.regions.map((r) => ({
            ...r,
            buildNumber: "99999",
            buildVersion: "9.9.9.99999",
            buildConfig: "fake_old_config",
          })),
        };
      }

      await env.WOW_KV.put("last_versions", JSON.stringify(fakeOld));
      return Response.json({
        ok: true,
        message: "Fake old versions saved. Next cron will detect changes and send FCM push.",
        gamesStored: Object.keys(fakeOld).length,
      }, { headers: corsHeaders });
    }

    return Response.json({ error: "Not found" }, { status: 404, headers: corsHeaders });
  },

  async scheduled(event, env, ctx) {
    console.log("⏰ Cron triggered — checking WoW updates...");
    const result = await detectAndNotify(env);
    console.log("Result:", JSON.stringify(result));
  },
};
