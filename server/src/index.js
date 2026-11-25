/*
 * Golem's Utils presence server (Cloudflare Worker + D1).
 * Copyright (C) 2026 veno15 - GNU General Public License v3.0.
 *
 * Lets Golem's Utils users see the Golem's Utils logo on each other's nametags.
 * A player checks in with their UUID, then sends a heartbeat every minute. Clients ask which of the players around them are online with Golem's Utils.
 * Only Minecraft UUIDs and timestamps are stored; nothing about which server anyone is on.
 *
 *   POST /auth/start   {}                         -> { serverId }
 *   POST /auth/finish  { uuid, serverId }         -> { token, uuid }
 *   POST /heartbeat    { token }                  -> { ok, online }
 *   GET  /update/latest -> { version, size, sha256 }   newest client build (from the private repo's "latest" release)
 *   GET  /update/jar    -> redirect to that jar's download (needs the GITHUB_TOKEN secret: read access to the repo)
 *   POST /config/get   { token } -> { data, updated }          your synced client settings (data "" if none yet)
 *   POST /config/put   { token, data, updated } -> { ok }   saves them (max 512 KB); updated is when they changed (ms)
 *   POST /lookup       { uuids: [..] (max 300) }  -> { users, capes, owners, broadcast, capeRev }
 *                      (clients call it every ~10 s, so announcements and cape changes reach everyone quickly;
 *                       uuids may be empty to only get the latest announcement and cape revision)
 *   POST /leave        { token }                  -> { ok }
 *   POST /online       { token }                  -> { users: [{ uuid, name }] }  (everyone online now)
 *   GET  /admin/users?key=ADMIN_KEY              -> every player who ever used the client (HTML; &format=json or csv)
 *   GET  /cape/<id>.png                           -> an owner-made cape image
 *   POST /cape/set     { token, cape }            -> { ok }   (pick a cape: a preset id, an owner-made one you were given, or "")
 *   POST /cape/mine    { token }                  -> { cape, given: [{ id, name }] }
 *   POST /owner/verify { token, key }             -> { ok }   (the owner key also marks this account as an owner)
 *   POST /admin/stats  { key }                    -> { total, online, today, users: [..] }
 *   POST /admin/broadcast { key, text }           -> { ok, id }  (shown once to everyone using the client; "" clears)
 *   POST /admin/cape/upload { key, id, name, png } -> { ok }  (png: base64, at most 256 KB)
 *   POST /admin/cape/give   { key, cape, player }  -> { ok }  (player: name or UUID; the cape shows up in their picker)
 *   POST /admin/cape/list   { key }                -> { capes: [{ id, name, given: [names] }] }
 *
 *
 * Golem's Client (the launcher) has its own user stats, kept apart from Golem's Utils':
 *   POST /launcher/checkin { uuid, name, version, state, instance, mc } -> { ok }   every minute while it's open
 *                          (state: "launcher" or "playing"; instance/mc only while playing)
 *   POST /launcher/leave   { uuid }               -> { ok }   when it closes
 *   POST /launcher/stats   { key }                -> { total, in_launcher, playing, new_today, active_today, users }
 *   GET  /launcher/dashboard                      -> the owner's stats page (asks for the owner key)
 *   GET  /launcher/announcement                   -> { id, text, at } the banner on the in-game main menu ({} if none)
 *   POST /launcher/announce { key, text }         -> { ok, id }   posts the banner ("" takes it down)
 *   GET  /launcher/news                           -> { posts: [{ id, title, body, link, at }] } the menu's News page, newest first
 *   POST /launcher/news/post { key, title, body, link } -> { ok, id }   the owner writes a News post
 *   POST /launcher/news/delete { key, id }        -> { ok }       the owner takes a post down
 *
 * The in-game mod's G badge (on the nametags and tab list of Golem's Client players):
 *   POST /client/ping   { uuid }                  -> { ok }   every two minutes while the game is open
 *   POST /client/lookup { uuids: [..] (max 300) } -> { users, owners }   who of them is playing with Golem's Client
 *
 * Golem's Client friends (launcher and game): see friends.js, POST /friends/...
 *
 * The owner key is a secret:  npx wrangler secret put ADMIN_KEY
 */

import { friends, friendsCleanup } from "./friends.js";

const ONLINE_FOR = 5 * 60;          // seconds a heartbeat keeps you "online"
const SESSION_FOR = 24 * 60 * 60;   // seconds a login token lasts
const CHALLENGE_FOR = 2 * 60;
const MAX_LOOKUP = 300;
const UUID = /^[0-9a-f]{32}$/;
const CAPE_ID = /^[a-z0-9_-]{1,32}$/;
const BROADCAST_FOR = 24 * 60 * 60; // a broadcast is shown to clients that check in within a day of it
// emeraldgolem3, the owner: always an owner, with or without having entered the key
const OWNER_UUIDS = new Set(["dabe852b94dd4bb9b18d151c206be824"]);
const LAUNCHER_ONLINE_FOR = 3 * 60; // the launcher checks in every minute
const CLIENT_ONLINE_FOR = 5 * 60;   // the mod pings every two minutes

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { status: 204 });
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname.startsWith("/cape/") && url.pathname.endsWith(".png")) {
      try {
        return await capeImage(env, url.pathname.slice(6, -4));
      } catch (e) {
        return json({ error: "server error" }, 500);
      }
    }
    if (request.method === "GET" && (url.pathname === "/update/latest" || url.pathname === "/update/jar")) {
      try {
        return await update(env, url.pathname === "/update/jar");
      } catch (e) {
        return json({ error: "update server error" }, 502);
      }
    }
    // can this server reach Mojang's session server (needed to check accounts for friends)? A made-up server id
    // that nobody joined should answer 204.
    if (request.method === "GET" && url.pathname === "/health/mojang") {
      const out = {};
      for (const [name, target] of [
        ["sessionserver", "https://sessionserver.mojang.com/session/minecraft/hasJoined?username=emeraldgolem3&serverId=" + randomHex(20)],
        ["api", "https://api.mojang.com/users/profiles/minecraft/emeraldgolem3"],
      ]) {
        try {
          const r = await fetch(target, { headers: { "user-agent": "GolemsClient/1.0" } });
          out[name] = r.status;
        } catch (e) {
          out[name] = String(e && e.message || e);
        }
      }
      return json(out);
    }
    if (request.method === "GET" && url.pathname === "/launcher/dashboard") {
      return new Response(DASHBOARD_HTML, { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" } });
    }
    if (request.method === "GET" && url.pathname === "/launcher/announcement") {
      try {
        return await launcherAnnouncement(env);
      } catch (e) {
        return json({}, 200);
      }
    }
    if (request.method === "GET" && url.pathname === "/launcher/news") {
      try {
        return await launcherNews(env);
      } catch (e) {
        return json({ posts: [] }, 200);
      }
    }
    if (request.method === "GET" && url.pathname === "/admin/users") {
      try {
        return await adminUsers(env, url);
      } catch (e) {
        return json({ error: "server error" }, 500);
      }
    }
    if (request.method !== "POST") return json({ error: "use POST" }, 405);
    let body;
    try {
      body = await request.json();
    } catch {
      return json({ error: "bad json" }, 400);
    }
    const now = Math.floor(Date.now() / 1000);
    const path = new URL(request.url).pathname;
    try {
      const handled = await friends(path, env, body, now, {
        isOwnerKey,
        owners: async (env) => {
          await ensureUsers(env);
          const rows = await env.DB.prepare("SELECT uuid FROM owners").all();
          return new Set([...OWNER_UUIDS, ...rows.results.map((r) => r.uuid)]);
        },
      });
      if (handled) return handled;
      switch (path) {
        case "/auth/start": return await authStart(env, now);
        case "/auth/finish": return await authFinish(env, body, now);
        case "/heartbeat": return await heartbeat(env, body, now);
        case "/lookup": return await lookup(env, body, now);
        case "/leave": return await leave(env, body);
        case "/online": return await onlineList(env, body, now);
        case "/cape/set": return await capeSet(env, body, now);
        case "/cape/mine": return await capeMine(env, body, now);
        case "/owner/verify": return await ownerVerify(env, body, now);
        case "/config/get": return await configGet(env, body, now);
        case "/config/put": return await configPut(env, body, now);
        case "/admin/stats": return await adminStats(env, body, now);
        case "/admin/broadcast": return await adminBroadcast(env, body, now);
        case "/admin/cape/upload": return await adminCapeUpload(env, body, now);
        case "/admin/cape/give": return await adminCapeGive(env, body, now);
        case "/admin/cape/list": return await adminCapeList(env, body);
        case "/launcher/checkin": return await launcherCheckin(env, body, now);
        case "/client/ping": return await clientPing(env, body, now);
        case "/client/lookup": return await clientLookup(env, body, now);
        case "/launcher/leave": return await launcherLeave(env, body);
        case "/launcher/stats": return await launcherStats(env, body, now);
        case "/launcher/announce": return await launcherAnnounce(env, body, now);
        case "/launcher/news/post": return await launcherNewsPost(env, body, now);
        case "/launcher/news/delete": return await launcherNewsDelete(env, body);
        default: return json({ error: "not found" }, 404);
      }
    } catch (e) {
      return json({ error: "server error" }, 500);
    }
  },

  // Clean out old rows every 15 minutes.
  async scheduled(event, env) {
    const now = Math.floor(Date.now() / 1000);
    await env.DB.batch([
      env.DB.prepare("DELETE FROM online WHERE seen < ?").bind(now - ONLINE_FOR),
      env.DB.prepare("DELETE FROM launcher_online WHERE seen < ?").bind(now - LAUNCHER_ONLINE_FOR),
      env.DB.prepare("DELETE FROM client_online WHERE seen < ?").bind(now - CLIENT_ONLINE_FOR),
      env.DB.prepare("DELETE FROM sessions WHERE expires < ?").bind(now),
      env.DB.prepare("DELETE FROM challenges WHERE expires < ?").bind(now),
    ]);
    await friendsCleanup(env, now);
  },
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });
}

function randomHex(bytes) {
  const b = crypto.getRandomValues(new Uint8Array(bytes));
  return [...b].map((x) => x.toString(16).padStart(2, "0")).join("");
}

async function authStart(env, now) {
  const serverId = randomHex(20);
  await env.DB.prepare("INSERT INTO challenges (server_id, expires) VALUES (?, ?)").bind(serverId, now + CHALLENGE_FOR).run();
  return json({ serverId });
}

// Every player who has ever checked in: made on first use, so an existing database needs no manual step.
let usersReady = false;
async function ensureUsers(env) {
  if (usersReady) return;
  await env.DB.batch([
    env.DB.prepare("CREATE TABLE IF NOT EXISTS users (uuid TEXT PRIMARY KEY, name TEXT, first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL)"),
    // which cape each player wears (a preset id or an owner-made cape id)
    env.DB.prepare("CREATE TABLE IF NOT EXISTS capes (uuid TEXT PRIMARY KEY, cape TEXT NOT NULL)"),
    // owner-made capes, and who was given them
    env.DB.prepare("CREATE TABLE IF NOT EXISTS custom_capes (id TEXT PRIMARY KEY, name TEXT NOT NULL, png TEXT NOT NULL, made INTEGER NOT NULL)"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS cape_gifts (uuid TEXT NOT NULL, cape TEXT NOT NULL, PRIMARY KEY (uuid, cape))"),
    // accounts that unlocked the owner version with the owner key
    env.DB.prepare("CREATE TABLE IF NOT EXISTS owners (uuid TEXT PRIMARY KEY, since INTEGER NOT NULL)"),
    // small counters, e.g. when any cape last changed (clients re-ask about capes when it moves)
    env.DB.prepare("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value INTEGER NOT NULL)"),
    // cloud config sync: one saved client config per account (only that account can read or write it)
    env.DB.prepare("CREATE TABLE IF NOT EXISTS configs (uuid TEXT PRIMARY KEY, data TEXT NOT NULL, updated INTEGER NOT NULL)"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS broadcasts (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, at INTEGER NOT NULL)"),
    // Golem's Client (the launcher): everyone who used it, and who has it open right now
    env.DB.prepare("CREATE TABLE IF NOT EXISTS launcher_users (uuid TEXT PRIMARY KEY, name TEXT, version TEXT, first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL)"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS launcher_online (uuid TEXT PRIMARY KEY, seen INTEGER NOT NULL, state TEXT NOT NULL, instance TEXT, mc TEXT)"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS launcher_announcements (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, at INTEGER NOT NULL)"),
    // players in game with Golem's Client right now (the mod pings), for the G badge
    env.DB.prepare("CREATE TABLE IF NOT EXISTS client_online (uuid TEXT PRIMARY KEY, seen INTEGER NOT NULL)"),
    // the in-game News page: the owner's posts
    env.DB.prepare("CREATE TABLE IF NOT EXISTS launcher_news (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, body TEXT NOT NULL, link TEXT, at INTEGER NOT NULL)"),
  ]);
  usersReady = true;
}

async function isOwnerUuid(env, uuid) {
  if (OWNER_UUIDS.has(uuid)) return true;
  return !!(await env.DB.prepare("SELECT uuid FROM owners WHERE uuid = ?").bind(uuid).first());
}

// The latest announcement and cape revision, kept for a few seconds so frequent lookups stay cheap.
let liveCache = { at: 0, broadcast: null, capeRev: 0 };
async function live(env, now) {
  if (Date.now() - liveCache.at < 3000) return liveCache;
  const b = await env.DB.prepare("SELECT id, text, at FROM broadcasts ORDER BY id DESC LIMIT 1").first();
  const r = await env.DB.prepare("SELECT value FROM meta WHERE key = 'cape_rev'").first();
  liveCache = {
    at: Date.now(),
    broadcast: b && b.text && b.at >= now - BROADCAST_FOR ? { id: b.id, text: b.text, at: b.at } : null,
    capeRev: r ? r.value : 0,
  };
  return liveCache;
}

async function bumpCapeRev(env) {
  await env.DB.prepare("INSERT INTO meta (key, value) VALUES ('cape_rev', ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")
    .bind(Date.now()).run();
  liveCache.at = 0;
}

function isOwnerKey(env, key) {
  return !!env.ADMIN_KEY && typeof key === "string" && key.length >= 8 && key === env.ADMIN_KEY;
}

async function authFinish(env, body, now) {
  await ensureUsers(env);
  const uuid = String(body.uuid || "").toLowerCase().replace(/-/g, "");
  const name = /^[A-Za-z0-9_]{1,16}$/.test(String(body.name || "")) ? String(body.name) : null;
  const serverId = String(body.serverId || "");
  if (!UUID.test(uuid) || !/^[0-9a-f]{40}$/.test(serverId)) return json({ error: "bad request" }, 400);
  const challenge = await env.DB.prepare("DELETE FROM challenges WHERE server_id = ? AND expires >= ? RETURNING server_id")
    .bind(serverId, now).first();
  if (!challenge) return json({ error: "unknown or expired challenge" }, 403);

  // Mojang blocks requests from Cloudflare, so the account can't be checked from here. The client's UUID
  // is trusted; the worst a fake check-in can do is show the Golem's Utils logo on someone's nametag.
  const token = randomHex(32);
  await env.DB.batch([
    env.DB.prepare("INSERT INTO sessions (token, uuid, expires) VALUES (?, ?, ?)").bind(token, uuid, now + SESSION_FOR),
    env.DB.prepare("INSERT INTO online (uuid, seen) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET seen = excluded.seen").bind(uuid, now),
    env.DB.prepare("INSERT INTO users (uuid, name, first_seen, last_seen) VALUES (?, ?, ?, ?) "
      + "ON CONFLICT(uuid) DO UPDATE SET last_seen = excluded.last_seen, name = COALESCE(excluded.name, users.name)")
      .bind(uuid, name, now, now),
  ]);
  return json({ token, uuid });
}

async function sessionUuid(env, token, now) {
  if (!/^[0-9a-f]{64}$/.test(String(token || ""))) return null;
  const row = await env.DB.prepare("SELECT uuid FROM sessions WHERE token = ? AND expires >= ?").bind(token, now).first();
  return row ? row.uuid : null;
}

async function heartbeat(env, body, now) {
  const uuid = await sessionUuid(env, body.token, now);
  if (!uuid) return json({ error: "log in again" }, 401);
  await ensureUsers(env);
  await env.DB.batch([
    env.DB.prepare("INSERT INTO online (uuid, seen) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET seen = excluded.seen").bind(uuid, now),
    env.DB.prepare("UPDATE users SET last_seen = ? WHERE uuid = ?").bind(now, uuid),
  ]);
  // how many people are using the client right now (checked in within the last few minutes)
  const row = await env.DB.prepare("SELECT COUNT(*) AS n FROM online WHERE seen >= ?").bind(now - ONLINE_FOR).first();
  // the owner's latest announcement, if it's recent; the client shows each one once
  const { broadcast } = await live(env, now);
  return json({ ok: true, online: row ? row.n : 0, broadcast });
}

async function leave(env, body) {
  const token = String(body.token || "");
  const row = await env.DB.prepare("DELETE FROM sessions WHERE token = ? RETURNING uuid").bind(token).first();
  if (row) await env.DB.prepare("DELETE FROM online WHERE uuid = ?").bind(row.uuid).run();
  return json({ ok: true });
}

async function lookup(env, body, now) {
  const uuids = Array.isArray(body.uuids) ? body.uuids : [];
  const clean = [...new Set(uuids.map((u) => String(u).toLowerCase().replace(/-/g, "")).filter((u) => UUID.test(u)))]
    .slice(0, MAX_LOOKUP);
  await ensureUsers(env);
  const { broadcast, capeRev } = await live(env, now);
  if (clean.length === 0) return json({ users: [], capes: {}, owners: [], broadcast, capeRev });
  const users = [];
  // D1 limits bound parameters per query, so ask in chunks.
  for (let i = 0; i < clean.length; i += 90) {
    const chunk = clean.slice(i, i + 90);
    const marks = chunk.map(() => "?").join(",");
    const rows = await env.DB.prepare(`SELECT uuid FROM online WHERE seen >= ? AND uuid IN (${marks})`)
      .bind(now - ONLINE_FOR, ...chunk).all();
    for (const r of rows.results || []) users.push(r.uuid);
  }
  // capes and owner marks for the players found, so their nametags and capes can be drawn
  const capes = {};
  const owners = [];
  for (let i = 0; i < users.length; i += 90) {
    const chunk = users.slice(i, i + 90);
    const marks = chunk.map(() => "?").join(",");
    const c = await env.DB.prepare(`SELECT uuid, cape FROM capes WHERE uuid IN (${marks})`).bind(...chunk).all();
    for (const r of c.results || []) capes[r.uuid] = r.cape;
    const o = await env.DB.prepare(`SELECT uuid FROM owners WHERE uuid IN (${marks})`).bind(...chunk).all();
    for (const r of o.results || []) owners.push(r.uuid);
  }
  for (const u of users) if (OWNER_UUIDS.has(u) && !owners.includes(u)) owners.push(u);
  return json({ users, capes, owners, broadcast, capeRev });
}

/** Everyone using the client right now, with their names, for the in-game list. Only for logged-in clients. */
async function onlineList(env, body, now) {
  const me = await sessionUuid(env, body.token, now);
  if (!me) return json({ error: "log in again" }, 401);
  await ensureUsers(env);
  const rows = await env.DB.prepare(
    "SELECT online.uuid AS uuid, users.name AS name FROM online LEFT JOIN users ON users.uuid = online.uuid "
    + "WHERE online.seen >= ? ORDER BY users.name COLLATE NOCASE LIMIT 300").bind(now - ONLINE_FOR).all();
  return json({ users: (rows.results || []).map((r) => ({ uuid: r.uuid, name: r.name || null })) });
}

/** The owner's list of every player who has used the client. Needs the ADMIN_KEY secret. */
async function adminUsers(env, url) {
  const key = url.searchParams.get("key") || "";
  if (!env.ADMIN_KEY || key.length < 8 || key !== env.ADMIN_KEY) return json({ error: "not found" }, 404);
  await ensureUsers(env);
  const rows = (await env.DB.prepare("SELECT uuid, name, first_seen, last_seen FROM users ORDER BY last_seen DESC").all()).results || [];
  const day = (t) => new Date(t * 1000).toISOString().replace("T", " ").slice(0, 16) + " UTC";
  const dashed = (u) => `${u.slice(0, 8)}-${u.slice(8, 12)}-${u.slice(12, 16)}-${u.slice(16, 20)}-${u.slice(20)}`;
  const format = url.searchParams.get("format");
  if (format === "json") return json({ count: rows.length, users: rows.map((r) => ({ ...r, uuid: dashed(r.uuid) })) });
  if (format === "csv") {
    const csv = "uuid,name,first_seen,last_seen\n" + rows.map((r) => `${dashed(r.uuid)},${r.name || ""},${day(r.first_seen)},${day(r.last_seen)}`).join("\n");
    return new Response(csv, { headers: { "content-type": "text/csv" } });
  }
  const esc = (s) => String(s || "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
  const body = rows.map((r) => `<tr><td><img src="https://mc-heads.net/avatar/${r.uuid}/24" width="24" height="24" alt=""></td>`
    + `<td>${esc(r.name) || "<i>unknown</i>"}</td><td class="m">${dashed(r.uuid)}</td><td>${day(r.first_seen)}</td><td>${day(r.last_seen)}</td></tr>`).join("");
  const html = `<!doctype html><meta charset="utf-8"><title>Golem's Utils users</title>
<style>body{margin:0;padding:32px;background:#0E0E10;color:#EDEDEF;font:14px Inter,system-ui,sans-serif}
h1{font-size:20px;margin:0 0 4px}p{color:#9A9AA2;margin:0 0 20px}table{border-collapse:collapse;width:100%}
th,td{text-align:left;padding:8px 12px;border-bottom:1px solid #26262B}th{color:#9A9AA2;font-weight:500;font-size:12px}
.m{font-family:ui-monospace,monospace;font-size:12px;color:#B4B4BB}img{image-rendering:pixelated;display:block}</style>
<h1>Golem's Utils users</h1><p>${rows.length} players &middot; newest activity first &middot; also as <a style="color:#E5484D" href="?key=${encodeURIComponent(key)}&format=csv">CSV</a></p>
<table><tr><th></th><th>Name</th><th>UUID</th><th>First seen</th><th>Last seen</th></tr>${body}</table>`;
  return new Response(html, { headers: { "content-type": "text/html; charset=utf-8" } });
}

// ------------------------------------------------------------------ capes

const PRESET_CAPE = /^p_[a-z0-9_]{1,24}$/; // presets ship with the client ("p_" ids)

async function capeSet(env, body, now) {
  const uuid = await sessionUuid(env, body.token, now);
  if (!uuid) return json({ error: "log in again" }, 401);
  await ensureUsers(env);
  const cape = String(body.cape || "");
  if (cape === "") {
    await env.DB.prepare("DELETE FROM capes WHERE uuid = ?").bind(uuid).run();
    await bumpCapeRev(env);
    return json({ ok: true });
  }
  if (cape === "owner") {
    // the owner cape is only for owner accounts
    if (!(await isOwnerUuid(env, uuid))) return json({ error: "not yours" }, 403);
  } else if (!PRESET_CAPE.test(cape)) {
    if (!CAPE_ID.test(cape)) return json({ error: "bad cape" }, 400);
    const gift = await env.DB.prepare("SELECT cape FROM cape_gifts WHERE uuid = ? AND cape = ?").bind(uuid, cape).first();
    if (!gift) return json({ error: "not yours" }, 403);
  }
  await env.DB.prepare("INSERT INTO capes (uuid, cape) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET cape = excluded.cape")
    .bind(uuid, cape).run();
  await bumpCapeRev(env);
  return json({ ok: true });
}

async function capeMine(env, body, now) {
  const uuid = await sessionUuid(env, body.token, now);
  if (!uuid) return json({ error: "log in again" }, 401);
  await ensureUsers(env);
  const row = await env.DB.prepare("SELECT cape FROM capes WHERE uuid = ?").bind(uuid).first();
  const given = await env.DB.prepare(
    "SELECT custom_capes.id AS id, custom_capes.name AS name FROM cape_gifts JOIN custom_capes ON custom_capes.id = cape_gifts.cape "
    + "WHERE cape_gifts.uuid = ? ORDER BY custom_capes.made DESC").bind(uuid).all();
  const owner = await isOwnerUuid(env, uuid);
  return json({ cape: row ? row.cape : "", given: given.results || [], owner });
}

async function capeImage(env, id) {
  if (!CAPE_ID.test(id)) return json({ error: "not found" }, 404);
  await ensureUsers(env);
  const row = await env.DB.prepare("SELECT png FROM custom_capes WHERE id = ?").bind(id).first();
  if (!row) return json({ error: "not found" }, 404);
  const bytes = Uint8Array.from(atob(row.png), (c) => c.charCodeAt(0));
  return new Response(bytes, { headers: { "content-type": "image/png", "cache-control": "public, max-age=300" } });
}

// ------------------------------------------------------------------ owner

async function ownerVerify(env, body, now) {
  const uuid = await sessionUuid(env, body.token, now);
  if (!uuid) return json({ error: "log in again" }, 401);
  if (!isOwnerKey(env, body.key)) return json({ error: "wrong key" }, 403);
  await ensureUsers(env);
  await env.DB.prepare("INSERT INTO owners (uuid, since) VALUES (?, ?) ON CONFLICT(uuid) DO NOTHING").bind(uuid, now).run();
  return json({ ok: true });
}

async function adminStats(env, body, now) {
  if (!isOwnerKey(env, body.key)) return json({ error: "wrong key" }, 403);
  await ensureUsers(env);
  const one = async (sql, ...args) => ((await env.DB.prepare(sql).bind(...args).first()) || { n: 0 }).n;
  const total = await one("SELECT COUNT(*) AS n FROM users");
  const online = await one("SELECT COUNT(*) AS n FROM online WHERE seen >= ?", now - ONLINE_FOR);
  const today = await one("SELECT COUNT(*) AS n FROM users WHERE first_seen >= ?", now - 86400);
  const active = await one("SELECT COUNT(*) AS n FROM users WHERE last_seen >= ?", now - 86400);
  const rows = (await env.DB.prepare(
    "SELECT users.uuid AS uuid, users.name AS name, users.first_seen AS first_seen, users.last_seen AS last_seen, "
    + "(online.seen >= ?) AS online FROM users LEFT JOIN online ON online.uuid = users.uuid ORDER BY users.last_seen DESC LIMIT 1000")
    .bind(now - ONLINE_FOR).all()).results || [];
  const b = await env.DB.prepare("SELECT id, text, at FROM broadcasts ORDER BY id DESC LIMIT 1").first();
  return json({ total, online, today, active, broadcast: b || null,
    users: rows.map((r) => ({ uuid: r.uuid, name: r.name, first_seen: r.first_seen, last_seen: r.last_seen, online: !!r.online })) });
}

async function adminBroadcast(env, body, now) {
  if (!isOwnerKey(env, body.key)) return json({ error: "wrong key" }, 403);
  await ensureUsers(env);
  const text = String(body.text || "").replace(/[\u0000-\u001f]/g, " ").trim().slice(0, 200);
  const row = await env.DB.prepare("INSERT INTO broadcasts (text, at) VALUES (?, ?) RETURNING id").bind(text, now).first();
  liveCache.at = 0;
  return json({ ok: true, id: row ? row.id : 0 });
}

async function adminCapeUpload(env, body, now) {
  if (!isOwnerKey(env, body.key)) return json({ error: "wrong key" }, 403);
  await ensureUsers(env);
  const id = String(body.id || "").toLowerCase();
  const name = String(body.name || "").trim().slice(0, 32);
  const png = String(body.png || "");
  if (!CAPE_ID.test(id) || PRESET_CAPE.test(id) || id === "owner" || !name) return json({ error: "bad id or name" }, 400);
  if (png.length > 350000 || !png.startsWith("iVBORw0KGgo")) return json({ error: "not a PNG or too big" }, 400);
  await env.DB.prepare("INSERT INTO custom_capes (id, name, png, made) VALUES (?, ?, ?, ?) "
    + "ON CONFLICT(id) DO UPDATE SET name = excluded.name, png = excluded.png").bind(id, name, png, now).run();
  return json({ ok: true });
}

async function adminCapeGive(env, body, now) {
  if (!isOwnerKey(env, body.key)) return json({ error: "wrong key" }, 403);
  await ensureUsers(env);
  const cape = String(body.cape || "").toLowerCase();
  const player = String(body.player || "").trim();
  if (!CAPE_ID.test(cape)) return json({ error: "bad cape" }, 400);
  const exists = await env.DB.prepare("SELECT id FROM custom_capes WHERE id = ?").bind(cape).first();
  if (!exists) return json({ error: "no such cape" }, 404);
  let uuid = player.toLowerCase().replace(/-/g, "");
  if (!UUID.test(uuid)) {
    // by name: only players who have used the client are known
    const row = await env.DB.prepare("SELECT uuid FROM users WHERE name = ? COLLATE NOCASE ORDER BY last_seen DESC LIMIT 1").bind(player).first();
    if (!row) return json({ error: "that player hasn't used Golem's Utils yet" }, 404);
    uuid = row.uuid;
  }
  const take = body.take === true;
  if (take) {
    await env.DB.batch([
      env.DB.prepare("DELETE FROM cape_gifts WHERE uuid = ? AND cape = ?").bind(uuid, cape),
      env.DB.prepare("DELETE FROM capes WHERE uuid = ? AND cape = ?").bind(uuid, cape),
    ]);
  } else {
    // given capes are put on straight away; the player can change it in their picker
    await env.DB.batch([
      env.DB.prepare("INSERT INTO cape_gifts (uuid, cape) VALUES (?, ?) ON CONFLICT DO NOTHING").bind(uuid, cape),
      env.DB.prepare("INSERT INTO capes (uuid, cape) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET cape = excluded.cape").bind(uuid, cape),
    ]);
  }
  return json({ ok: true, uuid });
}

async function adminCapeList(env, body) {
  if (!isOwnerKey(env, body.key)) return json({ error: "wrong key" }, 403);
  await ensureUsers(env);
  const capes = (await env.DB.prepare("SELECT id, name FROM custom_capes ORDER BY made DESC").all()).results || [];
  const gifts = (await env.DB.prepare(
    "SELECT cape_gifts.cape AS cape, users.name AS name FROM cape_gifts LEFT JOIN users ON users.uuid = cape_gifts.uuid").all()).results || [];
  return json({ capes: capes.map((c) => ({ id: c.id, name: c.name, given: gifts.filter((g) => g.cape === c.id).map((g) => g.name || "?") })) });
}

// ------------------------------------------------------------------ client updates
// The client's jar lives on the private GitHub repo's "latest" release. The worker reads it with a read-only token
// (secret GITHUB_TOKEN), so clients can update without the repo being public.

const REPO = "VenoServices11/golems-utility";
const JAR = /^golems-utils-(\d+\.\d+\.\d+)\.jar$/;
let releaseCache = { at: 0, asset: null };

async function latestJar(env) {
  if (Date.now() - releaseCache.at < 60_000 && releaseCache.asset) return releaseCache.asset;
  const r = await fetch(`https://api.github.com/repos/${REPO}/releases/tags/latest`, {
    headers: {
      authorization: `Bearer ${env.GITHUB_TOKEN}`,
      accept: "application/vnd.github+json",
      "user-agent": "golems-utils-presence",
    },
  });
  if (!r.ok) throw new Error("github " + r.status);
  const rel = await r.json();
  let asset = null;
  for (const a of rel.assets || []) {
    const m = JAR.exec(a.name);
    if (m) asset = { id: a.id, version: m[1], size: a.size, sha256: (a.digest || "").replace(/^sha256:/, "") };
  }
  // "what's new": the release notes' bullet lines
  if (asset) {
    asset.notes = (rel.body || "").split("\n").map((l) => l.trim()).filter((l) => l.startsWith("- "))
      .map((l) => l.slice(2).trim()).filter((l) => l.length > 0).slice(0, 8);
  }
  releaseCache = { at: Date.now(), asset };
  return asset;
}

async function update(env, wantJar) {
  if (!env.GITHUB_TOKEN) return json({ error: "updates not set up" }, 503);
  const asset = await latestJar(env);
  if (!asset) return json({ error: "no build" }, 404);
  if (!wantJar) return json({ version: asset.version, size: asset.size, sha256: asset.sha256, notes: asset.notes || [] });
  // GitHub answers with a short-lived signed link; hand that to the client so the jar doesn't pass through here
  const r = await fetch(`https://api.github.com/repos/${REPO}/releases/assets/${asset.id}`, {
    headers: {
      authorization: `Bearer ${env.GITHUB_TOKEN}`,
      accept: "application/octet-stream",
      "user-agent": "golems-utils-presence",
    },
    redirect: "manual",
  });
  const to = r.headers.get("location");
  if (r.status >= 300 && r.status < 400 && to) return Response.redirect(to, 302);
  if (r.ok) return new Response(r.body, { headers: { "content-type": "application/java-archive" } });
  return json({ error: "github " + r.status }, 502);
}

// ------------------------------------------------------------------ cloud config sync

const CONFIG_MAX = 512 * 1024;

async function configGet(env, body, now) {
  const uuid = await sessionUuid(env, body.token, now);
  if (!uuid) return json({ error: "log in again" }, 401);
  await ensureUsers(env);
  const row = await env.DB.prepare("SELECT data, updated FROM configs WHERE uuid = ?").bind(uuid).first();
  return json({ data: row ? row.data : "", updated: row ? row.updated : 0 });
}

async function configPut(env, body, now) {
  const uuid = await sessionUuid(env, body.token, now);
  if (!uuid) return json({ error: "log in again" }, 401);
  const data = typeof body.data === "string" ? body.data : "";
  const updated = Number(body.updated) || 0;
  if (!data || data.length > CONFIG_MAX) return json({ error: "too big" }, 413);
  await ensureUsers(env);
  // never replace a newer copy with an older one (two PCs saving at once)
  await env.DB.prepare(
    "INSERT INTO configs (uuid, data, updated) VALUES (?, ?, ?) "
    + "ON CONFLICT(uuid) DO UPDATE SET data = excluded.data, updated = excluded.updated WHERE excluded.updated >= configs.updated")
    .bind(uuid, data, updated).run();
  return json({ ok: true });
}

// ------------------------------------------------------------------ Golem's Client (the launcher)

const SHORT_TEXT = (v, max) => (typeof v === "string" && v.length > 0 ? v.slice(0, max) : null);

/** The launcher checks in every minute while it's open; the UUID is trusted, as for the mod. */
async function launcherCheckin(env, body, now) {
  await ensureUsers(env);
  const uuid = String(body.uuid || "").toLowerCase().replace(/-/g, "");
  if (!UUID.test(uuid)) return json({ error: "bad request" }, 400);
  const name = /^[A-Za-z0-9_]{1,16}$/.test(String(body.name || "")) ? String(body.name) : null;
  const version = /^[0-9A-Za-z.+-]{1,32}$/.test(String(body.version || "")) ? String(body.version) : null;
  const state = body.state === "playing" ? "playing" : "launcher";
  const instance = state === "playing" ? SHORT_TEXT(body.instance, 64) : null;
  const mc = state === "playing" ? SHORT_TEXT(body.mc, 24) : null;
  await env.DB.batch([
    env.DB.prepare("INSERT INTO launcher_users (uuid, name, version, first_seen, last_seen) VALUES (?, ?, ?, ?, ?) "
      + "ON CONFLICT(uuid) DO UPDATE SET last_seen = excluded.last_seen, name = COALESCE(excluded.name, launcher_users.name), "
      + "version = COALESCE(excluded.version, launcher_users.version)")
      .bind(uuid, name, version, now, now),
    env.DB.prepare("INSERT INTO launcher_online (uuid, seen, state, instance, mc) VALUES (?, ?, ?, ?, ?) "
      + "ON CONFLICT(uuid) DO UPDATE SET seen = excluded.seen, state = excluded.state, instance = excluded.instance, mc = excluded.mc")
      .bind(uuid, now, state, instance, mc),
  ]);
  return json({ ok: true });
}

// ------------------------------------------------------------------ the G badge

/** The mod says it's running for this player. */
async function clientPing(env, body, now) {
  await ensureUsers(env);
  const uuid = String(body.uuid || "").toLowerCase().replace(/-/g, "");
  if (!UUID.test(uuid)) return json({ error: "bad request" }, 400);
  await env.DB.prepare("INSERT INTO client_online (uuid, seen) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET seen = excluded.seen")
    .bind(uuid, now).run();
  return json({ ok: true });
}

/** Which of these players are playing with Golem's Client (the mod pinged, or the launcher says it's playing), and which are owners. */
async function clientLookup(env, body, now) {
  const uuids = Array.isArray(body.uuids) ? body.uuids : [];
  const clean = [...new Set(uuids.map((u) => String(u).toLowerCase().replace(/-/g, "")).filter((u) => UUID.test(u)))]
    .slice(0, MAX_LOOKUP);
  await ensureUsers(env);
  const users = new Set();
  const owners = [];
  for (let i = 0; i < clean.length; i += 45) {
    const chunk = clean.slice(i, i + 45);
    const marks = chunk.map(() => "?").join(",");
    const rows = await env.DB.prepare(
      `SELECT uuid FROM client_online WHERE seen >= ? AND uuid IN (${marks}) `
      + `UNION SELECT uuid FROM launcher_online WHERE state = 'playing' AND seen >= ? AND uuid IN (${marks})`)
      .bind(now - CLIENT_ONLINE_FOR, ...chunk, now - LAUNCHER_ONLINE_FOR, ...chunk).all();
    for (const r of rows.results || []) users.add(r.uuid);
  }
  const found = [...users];
  for (let i = 0; i < found.length; i += 90) {
    const chunk = found.slice(i, i + 90);
    const marks = chunk.map(() => "?").join(",");
    const o = await env.DB.prepare(`SELECT uuid FROM owners WHERE uuid IN (${marks})`).bind(...chunk).all();
    for (const r of o.results || []) owners.push(r.uuid);
  }
  for (const u of found) if (OWNER_UUIDS.has(u) && !owners.includes(u)) owners.push(u);
  return json({ users: found, owners });
}

async function launcherLeave(env, body) {
  await ensureUsers(env);
  const uuid = String(body.uuid || "").toLowerCase().replace(/-/g, "");
  if (!UUID.test(uuid)) return json({ error: "bad request" }, 400);
  await env.DB.prepare("DELETE FROM launcher_online WHERE uuid = ?").bind(uuid).run();
  return json({ ok: true });
}

/** The owner's stats for the launcher (the Owner tab and the dashboard). Needs the owner key. */
async function launcherStats(env, body, now) {
  if (!isOwnerKey(env, body.key)) return json({ error: "wrong key" }, 403);
  await ensureUsers(env);
  const since = now - LAUNCHER_ONLINE_FOR;
  const one = async (sql, ...args) => ((await env.DB.prepare(sql).bind(...args).first()) || { n: 0 }).n;
  const total = await one("SELECT COUNT(*) AS n FROM launcher_users");
  const in_launcher = await one("SELECT COUNT(*) AS n FROM launcher_online WHERE seen >= ? AND state = 'launcher'", since);
  const playing = await one("SELECT COUNT(*) AS n FROM launcher_online WHERE seen >= ? AND state = 'playing'", since);
  const new_today = await one("SELECT COUNT(*) AS n FROM launcher_users WHERE first_seen >= ?", now - 86400);
  const active_today = await one("SELECT COUNT(*) AS n FROM launcher_users WHERE last_seen >= ?", now - 86400);
  const rows = (await env.DB.prepare(
    "SELECT u.uuid, u.name, u.version, u.first_seen, u.last_seen, "
    + "CASE WHEN o.seen >= ? THEN o.state END AS state, CASE WHEN o.seen >= ? THEN o.instance END AS instance, "
    + "CASE WHEN o.seen >= ? THEN o.mc END AS mc "
    + "FROM launcher_users u LEFT JOIN launcher_online o ON o.uuid = u.uuid "
    + "ORDER BY (o.seen >= ?) DESC, u.last_seen DESC LIMIT 1000")
    .bind(since, since, since, since).all()).results || [];
  return json({ total, in_launcher, playing, new_today, active_today, users: rows });
}

/** The banner on Golem's Client's main menu: the newest announcement, unless it was taken down ("" posted). */
async function launcherAnnouncement(env) {
  await ensureUsers(env);
  const row = await env.DB.prepare("SELECT id, text, at FROM launcher_announcements ORDER BY id DESC LIMIT 1").first();
  const body = row && row.text ? { id: String(row.id), text: row.text, at: row.at } : {};
  return new Response(JSON.stringify(body), {
    headers: { "content-type": "application/json", "cache-control": "public, max-age=60" },
  });
}

/** The owner posts (or, with "", takes down) the main menu banner. Needs the owner key. */
async function launcherAnnounce(env, body, now) {
  if (!isOwnerKey(env, body.key)) return json({ error: "wrong key" }, 403);
  await ensureUsers(env);
  const text = typeof body.text === "string" ? body.text.trim().slice(0, 140) : "";
  const result = await env.DB.prepare("INSERT INTO launcher_announcements (text, at) VALUES (?, ?)").bind(text, now).run();
  await env.DB.prepare("DELETE FROM launcher_announcements WHERE id < ?").bind((result.meta && result.meta.last_row_id || 0) - 20).run();
  return json({ ok: true, id: String(result.meta && result.meta.last_row_id || "") });
}

/** The News page on Golem's Client's main menu: the owner's posts, newest first. */
async function launcherNews(env) {
  await ensureUsers(env);
  const rows = (await env.DB.prepare("SELECT id, title, body, link, at FROM launcher_news ORDER BY id DESC LIMIT 30").all()).results || [];
  const posts = rows.map(r => ({ id: String(r.id), title: r.title, body: r.body, link: r.link || "", at: r.at }));
  return new Response(JSON.stringify({ posts }), {
    headers: { "content-type": "application/json", "cache-control": "public, max-age=60" },
  });
}

/** The owner writes a News post. Needs the owner key. A link must be an https address. */
async function launcherNewsPost(env, body, now) {
  if (!isOwnerKey(env, body.key)) return json({ error: "wrong key" }, 403);
  await ensureUsers(env);
  const title = typeof body.title === "string" ? body.title.trim().slice(0, 80) : "";
  const text = typeof body.body === "string" ? body.body.trim().slice(0, 2000) : "";
  let link = typeof body.link === "string" ? body.link.trim().slice(0, 300) : "";
  if (link && !/^https:\/\/[^\s]+$/.test(link)) link = "";
  if (!title || !text) return json({ error: "a post needs a title and some text" }, 400);
  const result = await env.DB.prepare("INSERT INTO launcher_news (title, body, link, at) VALUES (?, ?, ?, ?)")
    .bind(title, text, link || null, now).run();
  return json({ ok: true, id: String(result.meta && result.meta.last_row_id || "") });
}

/** The owner takes a News post down. Needs the owner key. */
async function launcherNewsDelete(env, body) {
  if (!isOwnerKey(env, body.key)) return json({ error: "wrong key" }, 403);
  await ensureUsers(env);
  const id = parseInt(body.id, 10);
  if (!Number.isFinite(id)) return json({ error: "bad id" }, 400);
  await env.DB.prepare("DELETE FROM launcher_news WHERE id = ?").bind(id).run();
  return json({ ok: true });
}

const DASHBOARD_HTML = `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Golem's Client users</title>
<link rel="preconnect" href="https://fonts.googleapis.com"><link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=JetBrains+Mono:wght@500&display=swap" rel="stylesheet">
<style>
:root{--bg:#0E0E10;--card:#141417;--line:#26262B;--text:#EDEDEF;--dim:#9A9AA2;--accent:#E5484D;--live:#3DD68C}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:14px Inter,system-ui,sans-serif}
main{max-width:980px;margin:0 auto;padding:32px 16px 48px}
header{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:20px}
h1{font-size:20px;font-weight:600;margin:0}.sub{color:var(--dim);font-size:12px}
.stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:8px;margin-bottom:24px}
.stat{background:var(--card);border:1px solid var(--line);border-radius:8px;padding:12px 14px}
.stat b{display:block;font:500 24px "JetBrains Mono",ui-monospace,monospace}.stat span{color:var(--dim);font-size:12px}
.row{display:flex;align-items:center;gap:12px;background:var(--card);border-radius:8px;padding:8px 12px;margin-bottom:6px}
.row img{width:28px;height:28px;image-rendering:pixelated;border-radius:4px;flex:none}
.who{flex:1;min-width:0}.who b{display:block;font-weight:500}.who span{color:var(--dim);font-size:12px}
.state{font-size:12px;color:var(--dim);text-align:right}.state.live{color:var(--live)}
form{display:flex;gap:8px;flex-wrap:wrap;margin-top:12px}input{flex:1;min-width:200px;background:var(--card);border:1px solid var(--line);border-radius:6px;color:var(--text);padding:8px 10px;font:inherit}
button{background:var(--accent);color:#fff;border:0;border-radius:6px;padding:8px 14px;font:500 13px Inter,system-ui,sans-serif;cursor:pointer}
button.ghost{background:transparent;color:var(--dim);border:1px solid var(--line)}
.err{color:var(--accent);margin-top:8px}.hidden{display:none}
</style></head><body><main>
<header><div><h1>Golem's Client users</h1><div class="sub" id="updated">&nbsp;</div></div><button class="ghost hidden" id="out">Forget key</button></header>
<section id="login"><p class="sub">Enter your owner key (the ADMIN_KEY on this server). It's kept in this browser only.</p>
<form id="f"><input id="k" type="password" placeholder="Owner key" autocomplete="current-password"><button>Open</button></form><div class="err" id="e"></div></section>
<section id="board" class="hidden"><div class="stats" id="s"></div><div id="u"></div></section>
</main><script>
const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
const ago = (t) => { const s = Math.max(0, Date.now() / 1000 - t); return s < 60 ? "just now" : s < 3600 ? Math.floor(s / 60) + " min ago" : s < 86400 ? Math.floor(s / 3600) + " h ago" : Math.floor(s / 86400) + (s < 172800 ? " day ago" : " days ago"); };
let key = ""; try { key = localStorage.getItem("gc-owner-key") || ""; } catch {}
async function load() {
  if (!key) return show(false);
  let r; try { r = await fetch("/launcher/stats", { method: "POST", body: JSON.stringify({ key }) }); } catch { $("e").textContent = "Couldn't reach the server."; return; }
  if (r.status === 403) { key = ""; try { localStorage.removeItem("gc-owner-key"); } catch {} $("e").textContent = "Wrong owner key."; return show(false); }
  const d = await r.json(); show(true);
  $("s").innerHTML = [["Total users", d.total], ["In the launcher", d.in_launcher], ["Playing", d.playing], ["Active today", d.active_today], ["New today", d.new_today]]
    .map(([t, n]) => '<div class="stat"><b>' + n + "</b><span>" + t + "</span></div>").join("");
  $("u").innerHTML = d.users.map((u) => {
    const live = !!u.state, st = u.state === "playing" ? "Playing " + esc(u.instance || "") + (u.mc ? " (" + esc(u.mc) + ")" : "") : u.state ? "In the launcher" : "Last seen " + ago(u.last_seen);
    return '<div class="row"><img alt="" src="https://mc-heads.net/avatar/' + esc(u.uuid) + '/28"><div class="who"><b>' + (esc(u.name) || "<i>Unknown</i>") + "</b><span>" + (u.version ? "v" + esc(u.version) + " · " : "") + "first seen " + ago(u.first_seen) + '</span></div><div class="state' + (live ? " live" : "") + '">' + st + "</div></div>";
  }).join("") || '<p class="sub">Nobody yet.</p>';
  $("updated").textContent = "Updated " + new Date().toLocaleTimeString() + " · refreshes every 20 s";
}
function show(on) { $("login").classList.toggle("hidden", on); $("board").classList.toggle("hidden", !on); $("out").classList.toggle("hidden", !on); }
$("f").onsubmit = (ev) => { ev.preventDefault(); key = $("k").value.trim(); $("e").textContent = ""; try { localStorage.setItem("gc-owner-key", key); } catch {} load(); };
$("out").onclick = () => { key = ""; try { localStorage.removeItem("gc-owner-key"); } catch {} show(false); };
load(); setInterval(load, 20000);
</script></body></html>`;
