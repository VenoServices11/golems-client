use freya::prelude::*;
use freya::router::*;

use crate::i18n::t;
use crate::layout::AppShell;
use crate::layout::ClusterShell;
use crate::layout::OnboardingShell;
use crate::layout::RootLayout;
use crate::layout::SettingsShell;

use crate::view::{
    NotFound, Relocating, Startup,
    app::{
        Clusters, Debug, Home, Stats, Wardrobe,
        browser::{Browser, BrowserPackage},
        cluster::{
            ClusterDataPacks, ClusterLogs, ClusterMods, ClusterOverview, ClusterScreenshots,
            ClusterSettings, ClusterShaders, ClusterTextures, ClusterWorlds, ProcessLogs,
        },
        settings::{
            SettingsAccounts, SettingsApis, SettingsAppearance, SettingsChangelog,
            SettingsDeveloper, SettingsJava, SettingsLanguage, SettingsLauncher, SettingsMinecraft,
            SettingsOwner, SettingsStorage, SettingsThemeEditor,
        },
    },
    onboarding::{
        OnboardingAccount, OnboardingBundles, OnboardingLanguage, OnboardingLocation,
        OnboardingMigration, OnboardingPreferences, OnboardingSummary, OnboardingTerms,
        OnboardingWelcome,
    },
};

#[derive(Routable, Debug, Clone, PartialEq)]
#[rustfmt::skip]
pub enum Route {
    #[layout(RootLayout)]
        #[route("/")]
        Startup {},
        #[route("/moving")]
        Relocating {},

        #[layout(OnboardingShell)]
            #[route("/onboarding")]
            OnboardingWelcome {},
            #[route("/onboarding/location")]
            OnboardingLocation {},
            #[route("/onboarding/terms")]
            OnboardingTerms {},
            #[route("/onboarding/migration")]
            OnboardingMigration {},
            #[route("/onboarding/language")]
            OnboardingLanguage {},
            #[route("/onboarding/account")]
            OnboardingAccount {},
            #[route("/onboarding/preferences")]
            OnboardingPreferences {},
            #[route("/onboarding/bundles")]
            OnboardingBundles {},
            #[route("/onboarding/summary")]
            OnboardingSummary {},
        #[end_layout]
        
        #[layout(AppShell)]
            #[route("/app")]
            Home {},
            #[route("/app/clusters")]
            Clusters {},

            #[layout(ClusterShell)]
                #[route("/app/clusters/:cluster_id")]
                ClusterOverview { cluster_id: i64 },
                #[route("/app/clusters/:cluster_id/logs")]
                ClusterLogs { cluster_id: i64 },
                #[route("/app/clusters/:cluster_id/process")]
                ProcessLogs { cluster_id: i64 },
                #[route("/app/clusters/:cluster_id/screenshots")]
                ClusterScreenshots { cluster_id: i64 },
                #[route("/app/clusters/:cluster_id/mods")]
                ClusterMods { cluster_id: i64 },
                #[route("/app/clusters/:cluster_id/shaders")]
                ClusterShaders { cluster_id: i64 },
                #[route("/app/clusters/:cluster_id/textures")]
                ClusterTextures { cluster_id: i64 },
                #[route("/app/clusters/:cluster_id/worlds")]
                ClusterWorlds { cluster_id: i64 },
                #[route("/app/clusters/:cluster_id/datapacks")]
                ClusterDataPacks { cluster_id: i64 },
                #[route("/app/clusters/:cluster_id/settings")]
                ClusterSettings { cluster_id: i64 },
            #[end_layout]

            #[route("/app/browser/:cluster_id/:package_type?:pick_cluster")]
            Browser { cluster_id: i64, package_type: String, pick_cluster: bool },
            #[route("/app/browser/:cluster_id/:package_type/package/:package_id?:add_to_cluster")]
            BrowserPackage {
                cluster_id: i64,
                package_type: String,
                package_id: String,
                add_to_cluster: bool,
            },
            #[route("/app/wardrobe")]
            Wardrobe {},
            #[route("/app/stats")]
            Stats {},
            #[route("/app/debug")]
            Debug {},

            #[layout(SettingsShell)]
                #[route("/app/settings/appearance")]
                SettingsAppearance {},
                #[route("/app/settings/appearance/theme")]
                SettingsThemeEditor {},
                #[route("/app/settings/minecraft")]
                SettingsMinecraft {},
                #[route("/app/settings/accounts")]
                SettingsAccounts {},
                #[route("/app/settings/launcher")]
                SettingsLauncher {},
                #[route("/app/settings/java")]
                SettingsJava {},
                #[route("/app/settings/storage")]
                SettingsStorage {},
                #[route("/app/settings/apis")]
                SettingsApis {},
                #[route("/app/settings/language")]
                SettingsLanguage {},
                #[route("/app/settings/developer")]
                SettingsDeveloper {},
                #[route("/app/settings/changelog")]
                SettingsChangelog {},
                #[route("/app/settings/owner")]
                SettingsOwner {},
                // #[route("/app/settings/feedback")]
                // SettingsFeedback {},
            #[end_layout]
        #[end_layout]

        #[route("/:..path")]
        NotFound { path: Vec<String> },
}

impl Route {
    pub fn title(&self) -> String {
        let title = match self {
            Route::Startup { .. } => t("golems.routes.startup"),
            Route::Relocating { .. } => t("golems.routes.relocating"),
            Route::OnboardingWelcome { .. } => t("golems.routes.onboarding_welcome"),
            Route::OnboardingLocation { .. } => t("golems.routes.onboarding_location"),
            Route::OnboardingTerms { .. } => t("golems.routes.onboarding_terms"),
            Route::OnboardingMigration { .. } => t("golems.routes.onboarding_migration"),
            Route::OnboardingLanguage { .. } => t("golems.routes.onboarding_language"),
            Route::OnboardingAccount { .. } => t("golems.routes.onboarding_account"),
            Route::OnboardingPreferences { .. } => t("golems.routes.onboarding_preferences"),
            Route::OnboardingBundles { .. } => t("golems.routes.onboarding_bundles"),
            Route::OnboardingSummary { .. } => t("golems.routes.onboarding_summary"),
            Route::Home { .. } => t("golems.routes.home"),
            Route::Clusters { .. } => t("golems.routes.clusters"),
            Route::ClusterOverview { .. } => t("golems.routes.cluster_overview"),
            Route::ClusterLogs { .. } => t("golems.routes.cluster_logs"),
            Route::ProcessLogs { .. } => t("golems.routes.process_logs"),
            Route::ClusterScreenshots { .. } => t("golems.routes.cluster_screenshots"),
            Route::ClusterMods { .. } => t("golems.routes.cluster_mods"),
            Route::ClusterShaders { .. } => t("golems.routes.cluster_shaders"),
            Route::ClusterTextures { .. } => t("golems.routes.cluster_textures"),
            Route::ClusterWorlds { .. } => t("golems.routes.cluster_worlds"),
            Route::ClusterDataPacks { .. } => t("golems.routes.cluster_data_packs"),
            Route::ClusterSettings { .. } => t("golems.routes.cluster_settings"),
            Route::Browser { .. } => t("golems.routes.browser"),
            Route::BrowserPackage { .. } => t("golems.routes.browser_package"),
            Route::Wardrobe { .. } => t("golems.routes.wardrobe"),
            Route::Stats { .. } => t("golems.routes.stats"),
            Route::Debug { .. } => t("golems.routes.debug"),
            Route::SettingsAppearance { .. } => t("golems.routes.settings_appearance"),
            Route::SettingsThemeEditor { .. } => t("golems.routes.settings_theme_editor"),
            Route::SettingsMinecraft { .. } => t("golems.routes.settings_minecraft"),
            Route::SettingsAccounts { .. } => t("golems.routes.settings_accounts"),
            Route::SettingsLauncher { .. } => t("golems.routes.settings_launcher"),
            Route::SettingsJava { .. } => t("golems.routes.settings_java"),
            Route::SettingsStorage { .. } => t("golems.routes.settings_storage"),
            Route::SettingsApis { .. } => t("golems.routes.settings_apis"),
            Route::SettingsLanguage { .. } => t("golems.routes.settings_language"),
            Route::SettingsDeveloper { .. } => t("golems.routes.settings_developer"),
            Route::SettingsChangelog { .. } => t("golems.routes.settings_changelog"),
            Route::SettingsOwner { .. } => "Owner",
            // Route::SettingsFeedback { .. } => "Feedback".to_string(),
            Route::NotFound { .. } => t("golems.routes.not_found"),
        };
        title.to_string()
    }
}

pub fn router() -> impl IntoElement {
    Router::<Route>::new(|| RouterConfig::default().with_initial_path(Route::Startup {}))
}
