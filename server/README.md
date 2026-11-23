# Golem's Client server

A small server on Cloudflare Workers (free plan) with a D1 database. It does two jobs:

- **Golem's Client (the launcher):** user stats for the owner. While the launcher is open with a Microsoft
  account signed in, it checks in every minute with the Minecraft UUID and name, the launcher version, and
  whether a game is running (instance name and Minecraft version). See the numbers in the launcher's
  **Settings → Owner** tab (only shown to emeraldgolem3) or at `https://c418-presence.c418client.workers.dev/launcher/dashboard`.
  Both ask for the owner key.
- **Golem's Utils (the old in-game mod):** nametag logos, capes, announcements, settings sync and updates. The mod
  isn't updated any more, but its users still use this server, so these parts stay. That's also why the server
  keeps its old address (`c418-presence...`): the mod has it built in.

**What it stores:** Minecraft UUIDs and names, when each one first and last checked in, what a launcher user is
playing, short-lived login tokens, cape choices, owner-made cape images, announcements and synced mod settings.
No IP addresses, emails or login tokens from Microsoft or Mojang. (Mojang blocks requests from Cloudflare, so
accounts are not verified.)

## Deploying

**Automatic:** every push that changes this folder deploys it, through the "Deploy server" workflow
(`.github/workflows/deploy-server.yml`). It needs two repository secrets, set once in GitHub
(**Settings → Secrets and variables → Actions**):

- `CLOUDFLARE_API_TOKEN`: a Cloudflare API token made from the "Edit Cloudflare Workers" template, with
  **Account → D1 → Edit** added.
- `CLOUDFLARE_ACCOUNT_ID`: your Cloudflare account ID (on the right of the Workers & Pages overview page).

**By hand:** in this folder run `npx wrangler deploy` (after `npx wrangler login` once).

New tables are created automatically the first time the server runs; `schema.sql` lists them all.

## Owner key

The owner tools (the launcher's Owner tab, the dashboard, the mod's Owner tab) need a secret, set once:

```
npx wrangler secret put ADMIN_KEY
```

## API

| Path | Body | Reply |
| --- | --- | --- |
| `POST /auth/start` | `{}` | `{ serverId }` |
| `POST /auth/finish` | `{ uuid, serverId }` | `{ token, uuid }` |
| `POST /heartbeat` | `{ token }` | `{ ok, online }` (how many people are using the client) |
| `POST /lookup` | `{ uuids: [...] }` (up to 300) | `{ users: [...] }` |
| `POST /leave` | `{ token }` | `{ ok }` |
| `POST /online` | `{ token }` | `{ users: [{ uuid, name }] }` |
| `POST /cape/set` | `{ token, cape }` | `{ ok }` |
| `POST /cape/mine` | `{ token }` | `{ cape, given, owner }` |
| `GET /cape/<id>.png` | | an owner-made cape image |
| `POST /owner/verify` | `{ token, key }` | `{ ok }` |
| `POST /admin/stats` | `{ key }` | `{ total, online, today, active, broadcast, users }` |
| `POST /admin/broadcast` | `{ key, text }` | `{ ok, id }` |
| `POST /admin/cape/upload` | `{ key, id, name, png }` | `{ ok }` |
| `POST /admin/cape/give` | `{ key, cape, player, take? }` | `{ ok }` |
| `POST /admin/cape/list` | `{ key }` | `{ capes }` |
| `POST /launcher/checkin` | `{ uuid, name, version, state, instance, mc }` | `{ ok }` |
| `POST /launcher/leave` | `{ uuid }` | `{ ok }` |
| `POST /launcher/stats` | `{ key }` | `{ total, in_launcher, playing, new_today, active_today, users }` |
| `GET /launcher/dashboard` | | the owner's Golem's Client stats page |

