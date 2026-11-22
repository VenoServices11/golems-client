# Golem's Client data

Files the launcher downloads from this branch:

- `golems/versions`: the version list and version art
- `golems/bundles`: the mod bundles (`generated/*.mrpack`), their list (`metadata.json`) and the warnings shown when someone turns off an important mod
- `golems/bad-mods.json`, `golems/bad-modpacks.json`, `golems/improvements.json`: mods the launcher warns about, with explanations in `bad_mods_mds`
- `golems/crashes.json`: known crashes and how to fix them
- `meta`: game and Fabric loader version files

When you change a bundle, update its `sha1` in `golems/bundles/metadata.json`.
