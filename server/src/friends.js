/*
 * Golem's Client friends: friend requests, friends lists, blocking, and what your friends are doing
 * (in the launcher, in game, and which server, if they share it).
 * Copyright (C) 2026 veno15 - GNU General Public License v3.0.
 *
 * Accounts: Mojang won't answer this server, so accounts can't be checked with Mojang. Instead the first time a
 * Minecraft account signs in to friends, its computer is given a secret key (kept in ~/.golemsclient), and every later
 * sign-in needs that key. The owner can reset an account (POST /admin/friends/reset) so it can be set up again,
 * e.g. on a new computer; its friends stay.
 *
 *   POST /friends/login    { uuid, name, key? }         -> { token, uuid, name, key? }  (key: only when it's new)
 *   POST /friends/state    { token, presence?, since? } -> { me, friends, incoming, outgoing, blocked, inbox, messages? }
 *        inbox: { latest, unread } (newest message id in your chats, chats with unread messages);
 *        messages: the ones newer than `since` (a message id), when it's sent. Messages: see messages.js
 *        presence: { source: "game"|"launcher", server?, mc? }   saved when sent (clients send it every few minutes)
 *   POST /friends/add      { token, name, uuid? }       -> { ok, friends, name }  (friends: true when they had asked you;
 *                                                          uuid: the name's account from Mojang, checked if sent)
 *   POST /friends/accept   { token, id }                -> { ok }
 *   POST /friends/decline  { token, id }                -> { ok }
 *   POST /friends/cancel   { token, id }                -> { ok }
 *   POST /friends/remove   { token, uuid }              -> { ok }
 *   POST /friends/block    { token, uuid }              -> { ok }   (also unfriends and drops requests both ways)
 *   POST /friends/unblock  { token, uuid }              -> { ok }
 *   POST /friends/settings { token, shareServer }       -> { ok }
 *   POST /friends/leave    { token, source }            -> { ok }   (the launcher or game closed)
 *   POST /admin/friends/reset { key, player }           -> { ok }   (player: name or UUID)
 *
 * Stored: Minecraft UUIDs and names, the hash of each account's key, friends, requests, blocks, and for a few
 * minutes what each player is doing (launcher or game, the server's address if they share it, the game version).
 */

import { messages, inbox, messagesCleanup } from "./messages.js";

const UUID = /^[0-9a-f]{32}$/;
const NAME = /^[A-Za-z0-9_]{1,16}$/;
const KEY = /^[0-9a-f]{64}$/;
const SERVER = /^[A-Za-z0-9 ._:'-]{1,64}$/;
const MC = /^[0-9A-Za-z.+ -]{1,24}$/;
const SESSION_FOR = 30 * 24 * 60 * 60;
const ONLINE_FOR = 5 * 60;             // clients send their presence every two minutes
const MAX_FRIENDS = 300;
const MAX_OUTGOING = 50;

let ready = false;
async function ensure(env) {
  if (ready) return;
  await env.DB.batch([
    env.DB.prepare("CREATE TABLE IF NOT EXISTS f_accounts (uuid TEXT PRIMARY KEY, name TEXT, key_hash TEXT NOT NULL, share_server INTEGER NOT NULL DEFAULT 1, made INTEGER NOT NULL, last_seen INTEGER NOT NULL)"),
    env.DB.prepare("CREATE INDEX IF NOT EXISTS f_accounts_name ON f_accounts (name COLLATE NOCASE)"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS f_sessions (token TEXT PRIMARY KEY, uuid TEXT NOT NULL, expires INTEGER NOT NULL)"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS f_friends (uuid TEXT NOT NULL, friend TEXT NOT NULL, since INTEGER NOT NULL, PRIMARY KEY (uuid, friend))"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS f_requests (id INTEGER PRIMARY KEY AUTOINCREMENT, sender TEXT NOT NULL, receiver TEXT NOT NULL, at INTEGER NOT NULL, UNIQUE (sender, receiver))"),
    env.DB.prepare("CREATE INDEX IF NOT EXISTS f_requests_receiver ON f_requests (receiver)"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS f_blocks (uuid TEXT NOT NULL, blocked TEXT NOT NULL, since INTEGER NOT NULL, PRIMARY KEY (uuid, blocked))"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS f_presence (uuid TEXT NOT NULL, source TEXT NOT NULL, seen INTEGER NOT NULL, server TEXT, mc TEXT, PRIMARY KEY (uuid, source))"),
  ]);
  ready = true;
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });
}

function fail(message, status = 400, code = "bad_request") {
  return json({ error: message, code }, status);
}

function randomHex(bytes) {
  const b = crypto.getRandomValues(new Uint8Array(bytes));
  return [...b].map((x) => x.toString(16).padStart(2, "0")).join("");
}

async function sha256(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((x) => x.toString(16).padStart(2, "0")).join("");
}

function cleanUuid(v) {
  const u = String(v || "").toLowerCase().replace(/-/g, "");
  return UUID.test(u) ? u : null;
}

async function me(env, token, now) {
  if (!/^[0-9a-f]{64}$/.test(String(token || ""))) return null;
  const row = await env.DB.prepare("SELECT uuid FROM f_sessions WHERE token = ? AND expires >= ?").bind(token, now).first();
  return row ? row.uuid : null;
}

/** Handles a /friends/... or /admin/friends/... request, or returns null for any other path. */
export async function friends(path, env, body, now, helpers) {
  if (!path.startsWith("/friends/") && !path.startsWith("/admin/friends/") && !path.startsWith("/messages/")) return null;
  await ensure(env);
  if (path === "/friends/login") return login(env, body, now);
  if (path === "/admin/friends/reset") return adminReset(env, body, helpers);
  const uuid = await me(env, body.token, now);
  if (!uuid) return fail("Sign in again.", 401, "login");
  if (path.startsWith("/messages/")) return messages(path, env, body, now, uuid);
  switch (path) {
    case "/friends/state": return state(env, uuid, body, now, helpers);
    case "/friends/add": return add(env, uuid, body, now);
    case "/friends/accept": return answer(env, uuid, body, now, true);
    case "/friends/decline": return answer(env, uuid, body, now, false);
    case "/friends/cancel": return cancel(env, uuid, body);
    case "/friends/remove": return remove(env, uuid, body);
    case "/friends/block": return block(env, uuid, body, now);
    case "/friends/unblock": return unblock(env, uuid, body);
    case "/friends/settings": return settings(env, uuid, body);
    case "/friends/leave": return leave(env, uuid, body);
    default: return fail("not found", 404, "not_found");
  }
}

async function login(env, body, now) {
  const uuid = cleanUuid(body.uuid);
  const name = NAME.test(String(body.name || "")) ? String(body.name) : null;
  if (!uuid || !name) return fail("bad request");
  const key = KEY.test(String(body.key || "")) ? String(body.key) : null;
  const row = await env.DB.prepare("SELECT key_hash FROM f_accounts WHERE uuid = ?").bind(uuid).first();
  let newKey = null;
  if (!row || row.key_hash === "") {
    // a new account, or one the owner reset: this computer gets the key
    newKey = randomHex(32);
    const hash = await sha256(newKey);
    await env.DB.prepare("INSERT INTO f_accounts (uuid, name, key_hash, made, last_seen) VALUES (?, ?, ?, ?, ?) "
      + "ON CONFLICT(uuid) DO UPDATE SET key_hash = excluded.key_hash, name = excluded.name, last_seen = excluded.last_seen")
      .bind(uuid, name, hash, now, now).run();
  } else {
    if (!key || (await sha256(key)) !== row.key_hash) {
      return fail("This account's friends are set up on another computer. Ask veno15 to reset them.", 403, "key");
    }
    await env.DB.prepare("UPDATE f_accounts SET name = ?, last_seen = ? WHERE uuid = ?").bind(name, now, uuid).run();
  }
  // nobody else keeps this name any more (names move between accounts)
  await env.DB.prepare("UPDATE f_accounts SET name = NULL WHERE name = ? COLLATE NOCASE AND uuid != ?").bind(name, uuid).run();
  const token = randomHex(32);
  await env.DB.prepare("INSERT INTO f_sessions (token, uuid, expires) VALUES (?, ?, ?)").bind(token, uuid, now + SESSION_FOR).run();
  const out = { token, uuid, name };
  if (newKey) out.key = newKey;
  return json(out);
}

async function adminReset(env, body, helpers) {
  if (!helpers.isOwnerKey(env, body.key)) return fail("not found", 404, "not_found");
  const player = String(body.player || "");
  const uuid = cleanUuid(player)
    || (NAME.test(player) ? (await env.DB.prepare("SELECT uuid FROM f_accounts WHERE name = ? COLLATE NOCASE").bind(player).first())?.uuid : null);
  if (!uuid) return fail("No such player.", 404, "unknown_player");
  await env.DB.batch([
    env.DB.prepare("UPDATE f_accounts SET key_hash = '' WHERE uuid = ?").bind(uuid),
    env.DB.prepare("DELETE FROM f_sessions WHERE uuid = ?").bind(uuid),
  ]);
  return json({ ok: true });
}

function status(row, now) {
  if (row.gseen && row.gseen >= now - ONLINE_FOR) {
    return { state: "game", server: row.share_server ? row.gserver || null : null, mc: row.gmc || null };
  }
  if (row.lseen && row.lseen >= now - ONLINE_FOR) return { state: "launcher" };
  return { state: "offline", lastSeen: Math.max(row.last_seen || 0, row.gseen || 0, row.lseen || 0) };
}

async function state(env, uuid, body, now, helpers) {
  const p = body.presence;
  if (p && (p.source === "game" || p.source === "launcher")) {
    const server = p.source === "game" && SERVER.test(String(p.server || "")) ? String(p.server) : null;
    const mc = p.source === "game" && MC.test(String(p.mc || "")) ? String(p.mc) : null;
    await env.DB.batch([
      env.DB.prepare("INSERT INTO f_presence (uuid, source, seen, server, mc) VALUES (?, ?, ?, ?, ?) "
        + "ON CONFLICT(uuid, source) DO UPDATE SET seen = excluded.seen, server = excluded.server, mc = excluded.mc")
        .bind(uuid, p.source, now, server, mc),
      env.DB.prepare("UPDATE f_accounts SET last_seen = ? WHERE uuid = ?").bind(now, uuid),
    ]);
  }
  const [mine, friendRows, incoming, outgoing, blocked] = await env.DB.batch([
    env.DB.prepare("SELECT name, share_server FROM f_accounts WHERE uuid = ?").bind(uuid),
    env.DB.prepare("SELECT f.friend AS uuid, f.since, a.name, a.share_server, a.last_seen, "
      + "g.seen AS gseen, g.server AS gserver, g.mc AS gmc, l.seen AS lseen "
      + "FROM f_friends f JOIN f_accounts a ON a.uuid = f.friend "
      + "LEFT JOIN f_presence g ON g.uuid = f.friend AND g.source = 'game' "
      + "LEFT JOIN f_presence l ON l.uuid = f.friend AND l.source = 'launcher' "
      + "WHERE f.uuid = ?").bind(uuid),
    env.DB.prepare("SELECT r.id, r.sender AS uuid, a.name, r.at FROM f_requests r LEFT JOIN f_accounts a ON a.uuid = r.sender "
      + "WHERE r.receiver = ? ORDER BY r.at DESC").bind(uuid),
    env.DB.prepare("SELECT r.id, r.receiver AS uuid, a.name, r.at FROM f_requests r LEFT JOIN f_accounts a ON a.uuid = r.receiver "
      + "WHERE r.sender = ? ORDER BY r.at DESC").bind(uuid),
    env.DB.prepare("SELECT b.blocked AS uuid, a.name, b.since FROM f_blocks b LEFT JOIN f_accounts a ON a.uuid = b.blocked "
      + "WHERE b.uuid = ? ORDER BY b.since DESC").bind(uuid),
  ]);
  const owners = friendRows.results.length ? await helpers.owners(env) : new Set();
  const friendsOut = friendRows.results.map((r) => ({
    uuid: r.uuid, name: r.name || null, since: r.since, owner: owners.has(r.uuid), status: status(r, now),
  }));
  const me0 = mine.results[0] || {};
  const chats = await inbox(env, uuid, body.since);
  return json({
    ...chats,
    me: { uuid, name: me0.name || null, shareServer: me0.share_server !== 0 },
    friends: friendsOut,
    incoming: incoming.results.map((r) => ({ id: r.id, uuid: r.uuid, name: r.name || null, at: r.at })),
    outgoing: outgoing.results.map((r) => ({ id: r.id, uuid: r.uuid, name: r.name || null, at: r.at })),
    blocked: blocked.results.map((r) => ({ uuid: r.uuid, name: r.name || null, since: r.since })),
  });
}

async function isBlockedEitherWay(env, a, b) {
  return !!(await env.DB.prepare("SELECT 1 FROM f_blocks WHERE (uuid = ? AND blocked = ?) OR (uuid = ? AND blocked = ?)")
    .bind(a, b, b, a).first());
}

async function befriend(env, a, b, now) {
  await env.DB.batch([
    env.DB.prepare("INSERT OR IGNORE INTO f_friends (uuid, friend, since) VALUES (?, ?, ?)").bind(a, b, now),
    env.DB.prepare("INSERT OR IGNORE INTO f_friends (uuid, friend, since) VALUES (?, ?, ?)").bind(b, a, now),
    env.DB.prepare("DELETE FROM f_requests WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)").bind(a, b, b, a),
  ]);
}

async function add(env, uuid, body, now) {
  const name = String(body.name || "").trim();
  if (!NAME.test(name)) return fail("That isn't a Minecraft name.", 400, "bad_name");
  const them = await env.DB.prepare("SELECT uuid, name FROM f_accounts WHERE name = ? COLLATE NOCASE").bind(name).first();
  // the client looks the name up with Mojang and sends the account's UUID, so a made-up account can't take a name
  const real = cleanUuid(body.uuid);
  if (!them || (real && them.uuid !== real)) {
    return fail(`${name} hasn't used Golem's Client friends yet. They need to open Golem's Client once first.`, 404, "unknown_player");
  }
  if (them.uuid === uuid) return fail("That's you.", 400, "self");
  if (await isBlockedEitherWay(env, uuid, them.uuid)) return fail(`You can't add ${them.name}.`, 403, "blocked");
  if (await env.DB.prepare("SELECT 1 FROM f_friends WHERE uuid = ? AND friend = ?").bind(uuid, them.uuid).first()) {
    return fail(`You're already friends with ${them.name}.`, 409, "already_friends");
  }
  // they asked you already: you're friends now
  if (await env.DB.prepare("SELECT 1 FROM f_requests WHERE sender = ? AND receiver = ?").bind(them.uuid, uuid).first()) {
    await befriend(env, uuid, them.uuid, now);
    return json({ ok: true, friends: true, name: them.name });
  }
  const counts = await env.DB.batch([
    env.DB.prepare("SELECT COUNT(*) AS n FROM f_friends WHERE uuid = ?").bind(uuid),
    env.DB.prepare("SELECT COUNT(*) AS n FROM f_requests WHERE sender = ?").bind(uuid),
  ]);
  if (counts[0].results[0].n >= MAX_FRIENDS) return fail(`You can have up to ${MAX_FRIENDS} friends.`, 409, "too_many");
  if (counts[1].results[0].n >= MAX_OUTGOING) return fail("You have too many requests waiting. Cancel some first.", 409, "too_many");
  await env.DB.prepare("INSERT OR IGNORE INTO f_requests (sender, receiver, at) VALUES (?, ?, ?)").bind(uuid, them.uuid, now).run();
  return json({ ok: true, friends: false, name: them.name });
}

async function answer(env, uuid, body, now, yes) {
  const id = Number(body.id);
  if (!Number.isInteger(id)) return fail("bad request");
  const req = await env.DB.prepare("SELECT sender FROM f_requests WHERE id = ? AND receiver = ?").bind(id, uuid).first();
  if (!req) return fail("That request isn't there any more.", 404, "gone");
  if (yes) {
    const n = (await env.DB.prepare("SELECT COUNT(*) AS n FROM f_friends WHERE uuid = ?").bind(uuid).first()).n;
    if (n >= MAX_FRIENDS) return fail(`You can have up to ${MAX_FRIENDS} friends.`, 409, "too_many");
    await befriend(env, uuid, req.sender, now);
  } else {
    await env.DB.prepare("DELETE FROM f_requests WHERE id = ?").bind(id).run();
  }
  return json({ ok: true });
}

async function cancel(env, uuid, body) {
  const id = Number(body.id);
  if (!Number.isInteger(id)) return fail("bad request");
  await env.DB.prepare("DELETE FROM f_requests WHERE id = ? AND sender = ?").bind(id, uuid).run();
  return json({ ok: true });
}

async function remove(env, uuid, body) {
  const them = cleanUuid(body.uuid);
  if (!them) return fail("bad request");
  await env.DB.prepare("DELETE FROM f_friends WHERE (uuid = ? AND friend = ?) OR (uuid = ? AND friend = ?)").bind(uuid, them, them, uuid).run();
  return json({ ok: true });
}

async function block(env, uuid, body, now) {
  const them = cleanUuid(body.uuid);
  if (!them || them === uuid) return fail("bad request");
  await env.DB.batch([
    env.DB.prepare("INSERT OR IGNORE INTO f_blocks (uuid, blocked, since) VALUES (?, ?, ?)").bind(uuid, them, now),
    env.DB.prepare("DELETE FROM f_friends WHERE (uuid = ? AND friend = ?) OR (uuid = ? AND friend = ?)").bind(uuid, them, them, uuid),
    env.DB.prepare("DELETE FROM f_requests WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)").bind(uuid, them, them, uuid),
  ]);
  return json({ ok: true });
}

async function unblock(env, uuid, body) {
  const them = cleanUuid(body.uuid);
  if (!them) return fail("bad request");
  await env.DB.prepare("DELETE FROM f_blocks WHERE uuid = ? AND blocked = ?").bind(uuid, them).run();
  return json({ ok: true });
}

async function settings(env, uuid, body) {
  if (typeof body.shareServer === "boolean") {
    await env.DB.prepare("UPDATE f_accounts SET share_server = ? WHERE uuid = ?").bind(body.shareServer ? 1 : 0, uuid).run();
  }
  return json({ ok: true });
}

async function leave(env, uuid, body) {
  const source = body.source === "launcher" ? "launcher" : "game";
  await env.DB.prepare("DELETE FROM f_presence WHERE uuid = ? AND source = ?").bind(uuid, source).run();
  return json({ ok: true });
}

/** For the 15-minute clean-up. */
export async function friendsCleanup(env, now) {
  await ensure(env);
  await env.DB.batch([
    env.DB.prepare("DELETE FROM f_presence WHERE seen < ?").bind(now - 2 * ONLINE_FOR),
    env.DB.prepare("DELETE FROM f_sessions WHERE expires < ?").bind(now),
  ]);
  await messagesCleanup(env, now);
}
