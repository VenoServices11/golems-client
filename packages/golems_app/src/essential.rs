use golems_content::packages::ProviderId;

use crate::i18n::t;

#[derive(Debug, Clone, Copy)]
pub struct EssentialPackage {
    pub provider: ProviderId,
    pub project_id: &'static str,
    pub name: &'static str,
    pub disable_body: fn() -> &'static str,
    pub remove_body: fn() -> &'static str,
}

impl PartialEq for EssentialPackage {
    fn eq(&self, other: &Self) -> bool {
        self.provider == other.provider && self.project_id == other.project_id
    }
}

impl Eq for EssentialPackage {}

/// Golem's Client's own social mod, which doesn't exist yet: no project id, so nothing matches it
pub const SOCIAL: EssentialPackage = EssentialPackage {
    provider: ProviderId::Modrinth,
    project_id: "",
    name: "Golem's Plus",
    disable_body: || t("golems.essential.social.disable_body"),
    remove_body: || t("golems.essential.social.remove_body"),
};

pub const ESSENTIAL_PACKAGES: &[EssentialPackage] = &[];

pub fn lookup(provider: ProviderId, package_id: &str) -> Option<&'static EssentialPackage> {
    ESSENTIAL_PACKAGES
        .iter()
        .find(|package| package.provider == provider && package.project_id == package_id)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ordinary_packages_are_not_guarded() {
        assert!(lookup(ProviderId::Modrinth, "AibBIVmj").is_none());
    }
}
