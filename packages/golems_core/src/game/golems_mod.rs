//! The Golem's Client mod: added to every Fabric game the launcher starts, through Fabric's `fabric.addMods`, so it
//! never sits in an instance's mods folder where it could be removed by accident. It lives in the launcher's own
//! metadata folder and is kept on the newest build published on the repo's `mod` release (`mod.json` lists one jar
//! per Minecraft version). Offline, the last downloaded jar is used.

use std::collections::HashMap;
use std::path::PathBuf;
use std::time::Duration;

use golems_common::paths;
use serde::Deserialize;

use crate::LauncherState;

const MANIFEST_URL: &str =
    "https://github.com/VenoServices11/golems-client/releases/download/mod/mod.json";
const TIMEOUT: Duration = Duration::from_secs(20);

#[derive(Deserialize)]
struct Manifest {
    builds: HashMap<String, Build>,
}

#[derive(Deserialize)]
struct Build {
    url: String,
    sha1: String,
}

fn jar_path(mc_version: &str) -> Option<PathBuf> {
    let safe: String = mc_version
        .chars()
        .filter(|c| c.is_ascii_alphanumeric() || *c == '.' || *c == '-')
        .collect();
    Some(
        paths::metadata_dir()
            .ok()?
            .join("golems-mod")
            .join(format!("golems-client-mod-{safe}.jar")),
    )
}

/// The mod's jar for this Minecraft version, downloading or updating it first when needed. None when there's no
/// build for this version (or nothing downloaded yet and the download failed).
pub async fn jar_for(state: &LauncherState, mc_version: &str) -> Option<PathBuf> {
    let path = jar_path(mc_version)?;
    if let Err(err) = refresh(state, mc_version, &path).await {
        tracing::warn!(mc_version, "couldn't update the Golem's Client mod: {err}");
    }
    path.is_file().then_some(path)
}

async fn refresh(state: &LauncherState, mc_version: &str, path: &PathBuf) -> Result<(), String> {
    let http = state.services.requester.http();
    let manifest: Manifest = http
        .get(MANIFEST_URL)
        .timeout(TIMEOUT)
        .send()
        .await
        .and_then(reqwest::Response::error_for_status)
        .map_err(|err| err.to_string())?
        .json()
        .await
        .map_err(|err| err.to_string())?;

    let Some(build) = manifest.builds.get(mc_version) else {
        // no build for this version: don't load an old one made for another version
        if path.is_file() {
            let _ = golems_io::remove_file(path).await;
        }
        return Ok(());
    };

    if path.is_file()
        && golems_io::sha1_file(path)
            .await
            .is_ok_and(|have| have.eq_ignore_ascii_case(&build.sha1))
    {
        return Ok(());
    }

    let bytes = http
        .get(&build.url)
        .timeout(Duration::from_secs(120))
        .send()
        .await
        .and_then(reqwest::Response::error_for_status)
        .map_err(|err| err.to_string())?
        .bytes()
        .await
        .map_err(|err| err.to_string())?;

    if let Some(dir) = path.parent() {
        golems_io::create_dir_all(dir)
            .await
            .map_err(|err| err.to_string())?;
    }
    let part = path.with_extension("jar.part");
    golems_io::write_atomic(&part, &bytes)
        .await
        .map_err(|err| err.to_string())?;
    let got = golems_io::sha1_file(&part)
        .await
        .map_err(|err| err.to_string())?;
    if !got.eq_ignore_ascii_case(&build.sha1) {
        let _ = golems_io::remove_file(&part).await;
        return Err(format!("checksum mismatch ({got})"));
    }
    golems_io::rename(&part, path)
        .await
        .map_err(|err| err.to_string())?;
    tracing::info!(mc_version, "Golem's Client mod updated");
    Ok(())
}
