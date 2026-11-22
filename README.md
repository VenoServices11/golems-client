<div align="center">

<img src="packages/oneclient_app/icons/128x128.png" alt="Golem's Client" width="96" />

# Golem's Client

A Minecraft launcher by veno15 for Minecraft 1.21.11 with Fabric: one click gets you a set of
performance, HUD, quality-of-life and utility mods, plus a mod browser, multiple instances and a
skin and account manager.

[Discord](https://discord.gg/q67qzHG2kf) · [TikTok](https://www.tiktok.com/@holygrajl)

</div>

## Installing

Windows only for now. Download `Golem's Client_..._x64-setup.exe` from the
[latest release](https://github.com/VenoServices11/golems-client/releases/tag/latest) and run it.

## Privacy

Golem's Client sends no telemetry, analytics or crash reports. It talks to Microsoft, Xbox and Mojang to
sign you in and download the game, to Modrinth for mods, to Polyfrost's public metadata server
(`meta.polyfrost.org`) for game and loader version files, and to this GitHub repository for the
launcher's own data (version list, mod bundles) and updates. Nothing is sent to Polyfrost's other
services (Poly+, Sentry, telemetry): they are blocked in the launcher.

## Building

The Windows installer is built by GitHub Actions (`.github/workflows/build.yml`) on every push, and
builds from `main` are published to the `latest` release. Locally: `cargo build --release --bin oneclient_app`
(Rust 1.97 or newer), then `cargo packager --release --formats nsis` from `packages/oneclient_app`.

The launcher's data files live on the `data` branch. The "Mirror launcher data" workflow made the
first copy of them; edit them there.

## Credits and licence

Golem's Client is based on [OneClient / OneLauncher](https://github.com/Polyfrost/OneLauncher) by
Polyfrost and contributors, and is licensed the same way, under the GNU General Public License v3.0
only (see [LICENSE](./LICENSE) and [ATTRIBUTION.md](./ATTRIBUTION.md)). It is not made by or
affiliated with Polyfrost, and it doesn't use Polyfrost's branding.

Changes from OneClient, made by veno15 starting November 2026: the Golem's Client name, logo, colours
and fonts; links; Polyfrost's online services, crash reporting, terms and translations removed;
CurseForge hidden; Windows and Minecraft 1.21.11 only; the launcher's data served from this repository.
