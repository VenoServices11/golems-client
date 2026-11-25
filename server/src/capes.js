/*
 * Golem's Client capes: which cape each player wears, and which capes they have.
 * Copyright (C) 2026 veno15 - GNU General Public License v3.0.
 *
 * Capes: "graphite" and "midnight" are free for everyone, "owner" is the owner's, and "supporter" and "og" are given
 * by the owner. Their pictures ship with the game; only which cape someone wears is kept here.
 *
 *   POST /capes/mine        { token }                     -> { wearing, capes: [{ id, name, kind }] }   (signed in, see friends.js)
 *   POST /capes/wear        { token, cape }               -> { ok }   ("" for none)
 *   POST /capes/lookup      { uuids: [..] (max 300) }     -> { capes: { uuid: cape } }   what the players around you wear
 *   POST /admin/capes/give  { key, player, cape, take? }  -> { ok, player }   (player: name or UUID)
 *   POST /admin/capes/list  { key }                       -> { capes: [{ id, name, people: [names] }] }
 */

const UUID = /^[0-9a-f]{32}$/;
const NAME = /^[A-Za-z0-9_]{1,16}$/;
const MAX_LOOKUP = 300;

export const CAPES = [
  { id: "graphite", name: "Graphite", kind: "free" },
  { id: "midnight", name: "Midnight", kind: "free" },
  { id: "owner", name: "Owner", kind: "owner" },
  { id: "supporter", name: "Supporter", kind: "gift" },
  { id: "og", name: "OG", kind: "gift" },
];
const BY_ID = new Map(CAPES.map((c) => [c.id, c]));

let ready = false;
async function ensure(env) {
  if (ready) return;
  await env.DB.batch([
    env.DB.prepare("CREATE TABLE IF NOT EXISTS gc_capes (uuid TEXT PRIMARY KEY, cape TEXT NOT NULL, since INTEGER NOT NULL)"),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS gc_cape_gifts (uuid TEXT NOT NULL, cape TEXT NOT NULL, since INTEGER NOT NULL, PRIMARY KEY (uuid, cape))"),
  ]);
  ready = true;
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });
}

function fail(message, status = 400, code = "bad_request") {
  return json({ error: message, code }, status);
}

function cleanUuid(v) {
  const u = String(v || "").toLowerCase().replace(/-/g, "");
  return UUID.test(u) ? u : null;
}

/** The capes this player may wear. */
async function owned(env, uuid, helpers) {
  const gifts = await env.DB.prepare("SELECT cape FROM gc_cape_gifts WHERE uuid = ?").bind(uuid).all();
  const given = new Set(gifts.results.map((r) => r.cape));
  const owner = (await helpers.owners(env)).has(uuid);
  return CAPES.filter((c) => c.kind === "free" || (c.kind === "owner" && owner) || given.has(c.id) || (owner && c.kind === "gift"));
}

/** Signed-in requests (the session is checked in friends.js). */
export async function capes(path, env, body, now, uuid, helpers) {
  await ensure(env);
  if (path === "/capes/mine") {
    const list = await owned(env, uuid, helpers);
    const row = await env.DB.prepare("SELECT cape FROM gc_capes WHERE uuid = ?").bind(uuid).first();
    return json({ wearing: row ? row.cape : "", capes: list });
  }
  if (path === "/capes/wear") {
    const cape = String(body.cape || "");
    if (cape === "") {
      await env.DB.prepare("DELETE FROM gc_capes WHERE uuid = ?").bind(uuid).run();
      return json({ ok: true });
    }
    if (!(await owned(env, uuid, helpers)).some((c) => c.id === cape)) return fail("You don't have that cape.", 403, "not_yours");
    await env.DB.prepare("INSERT INTO gc_capes (uuid, cape, since) VALUES (?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET cape = excluded.cape, since = excluded.since")
      .bind(uuid, cape, now).run();
    return json({ ok: true });
  }
  return fail("not found", 404, "not_found");
}

/** Requests that need no sign-in (lookup) or the owner key (admin). */
export async function capesOpen(path, env, body, now, helpers) {
  if (path !== "/capes/lookup" && !path.startsWith("/admin/capes/")) return null;
  await ensure(env);
  if (path === "/capes/lookup") {
    const ids = [...new Set((Array.isArray(body.uuids) ? body.uuids : []).map(cleanUuid).filter(Boolean))].slice(0, MAX_LOOKUP);
    const out = {};
    for (let i = 0; i < ids.length; i += 90) {
      const chunk = ids.slice(i, i + 90);
      const rows = await env.DB.prepare(`SELECT uuid, cape FROM gc_capes WHERE uuid IN (${chunk.map(() => "?").join(",")})`).bind(...chunk).all();
      for (const r of rows.results) out[r.uuid] = r.cape;
    }
    return json({ capes: out });
  }
  if (!helpers.isOwnerKey(env, body.key)) return fail("not found", 404, "not_found");
  if (path === "/admin/capes/give") {
    const cape = BY_ID.get(String(body.cape || ""));
    if (!cape || cape.kind !== "gift") return fail("Pick a cape you can give.");
    const player = String(body.player || "").trim();
    const found = cleanUuid(player)
      ? { uuid: cleanUuid(player), name: null }
      : NAME.test(player) ? await env.DB.prepare("SELECT uuid, name FROM f_accounts WHERE name = ? COLLATE NOCASE").bind(player).first() : null;
    if (!found) return fail(`${player || "That player"} hasn't used Golem's Client yet.`, 404, "unknown_player");
    if (body.take) {
      await env.DB.batch([
        env.DB.prepare("DELETE FROM gc_cape_gifts WHERE uuid = ? AND cape = ?").bind(found.uuid, cape.id),
        env.DB.prepare("DELETE FROM gc_capes WHERE uuid = ? AND cape = ?").bind(found.uuid, cape.id),
      ]);
    } else {
      await env.DB.prepare("INSERT OR IGNORE INTO gc_cape_gifts (uuid, cape, since) VALUES (?, ?, ?)").bind(found.uuid, cape.id, now).run();
    }
    return json({ ok: true, player: found.name || found.uuid });
  }
  if (path === "/admin/capes/list") {
    const rows = await env.DB.prepare("SELECT g.cape, a.name, g.uuid FROM gc_cape_gifts g LEFT JOIN f_accounts a ON a.uuid = g.uuid ORDER BY g.since").all();
    return json({
      capes: CAPES.filter((c) => c.kind === "gift").map((c) => ({
        id: c.id, name: c.name, people: rows.results.filter((r) => r.cape === c.id).map((r) => r.name || r.uuid),
      })),
    });
  }
  return fail("not found", 404, "not_found");
}
