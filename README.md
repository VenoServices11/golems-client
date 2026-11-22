<div align="center">

<img src="packages/golems_app/icons/128x128.png" alt="Golem's Client" width="96" />

# Golem's Client

A Minecraft launcher by veno15 for Minecraft 1.21.11 with Fabric: one click gets you a set of
performance, HUD, quality-of-life and utility mods, plus a mod browser, multiple instances and a
skin and account manager.

[Discord](https://discord.gg/q67qzHG2kf) · [TikTok](https://www.tiktok.com/@holygrajl)

</div>

## Installing

Windows only for now. Download `GolemsClient_..._x64-setup.exe` from the
[latest release](https://github.com/VenoServices11/golems-client/releases/tag/latest) and run it.

## Privacy

Golem's Client sends no telemetry, analytics or crash reports. It talks to Microsoft, Xbox and Mojang to
sign you in and download the game, to Modrinth for mods, to the Java download sites, and to this
GitHub repository for its own data (version files, mod bundles, known crashes) and updates.

## Building

The Windows installer is built by GitHub Actions (`.github/workflows/build.yml`) on every push, and
builds from `main` are published to the `latest` release. Locally: `cargo build --release --bin golems_app`
(Rust 1.97 or newer), then `cargo packager --release --formats nsis` from `packages/golems_app`.

The launcher's data files live on the `data` branch: edit them there.

## Licence

GNU General Public License v3.0 only. See [LICENSE](./LICENSE) and [ATTRIBUTION.md](./ATTRIBUTION.md).
