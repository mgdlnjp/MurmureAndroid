/**
 * Config plugin Murmure : permissions, service d'accessibilité, sauvegarde désactivée et ABI unique.
 * Idempotent : relancer `expo prebuild` ne crée aucun doublon.
 */
const { AndroidConfig, withAndroidManifest, withGradleProperties } = require('expo/config-plugins');

const SERVICE_NAME = 'app.murmure.core.MurmureAccessibilityService';

const PERMISSIONS = [
  'android.permission.RECORD_AUDIO',
  'android.permission.INTERNET',
  'android.permission.SYSTEM_ALERT_WINDOW',
  'android.permission.FOREGROUND_SERVICE',
  'android.permission.FOREGROUND_SERVICE_MICROPHONE',
  'android.permission.POST_NOTIFICATIONS',
];

function buildServiceNode() {
  return {
    $: {
      'android:name': SERVICE_NAME,
      'android:exported': 'true',
      'android:label': '@string/murmure_accessibility_label',
      'android:permission': 'android.permission.BIND_ACCESSIBILITY_SERVICE',
      'android:foregroundServiceType': 'microphone',
    },
    'intent-filter': [
      { action: [{ $: { 'android:name': 'android.accessibilityservice.AccessibilityService' } }] },
    ],
    'meta-data': [
      {
        $: {
          'android:name': 'android.accessibilityservice',
          'android:resource': '@xml/murmure_accessibility_service',
        },
      },
    ],
  };
}

function addPermissions(manifest) {
  if (!Array.isArray(manifest['uses-permission'])) {
    manifest['uses-permission'] = [];
  }
  const existing = new Set(manifest['uses-permission'].map((p) => p.$ && p.$['android:name']));
  for (const name of PERMISSIONS) {
    if (!existing.has(name)) {
      manifest['uses-permission'].push({ $: { 'android:name': name } });
    }
  }
}

function addAccessibilityService(manifest) {
  const app = AndroidConfig.Manifest.getMainApplicationOrThrow(manifest);
  const services = Array.isArray(app.service) ? app.service : [];
  app.service = services.filter((s) => !(s.$ && s.$['android:name'] === SERVICE_NAME));
  app.service.push(buildServiceNode());
}

const DATA_EXTRACTION_RULES = '@xml/murmure_data_extraction_rules';

/**
 * Aucune sauvegarde : allowBackup=false couvre Auto Backup jusqu'à Android 11, mais pas le transfert
 * d'appareil à appareil sur Android 12+ ; d'où des dataExtractionRules qui excluent tout
 * (history.json, murmure_prefs, whisper-models/).
 */
function disableBackup(androidManifest) {
  const app = AndroidConfig.Manifest.getMainApplicationOrThrow(androidManifest);
  app.$ = app.$ || {};
  app.$['android:allowBackup'] = 'false';
  app.$['android:fullBackupContent'] = 'false';
  app.$['android:dataExtractionRules'] = DATA_EXTRACTION_RULES;
}

function withMurmureManifest(config) {
  return withAndroidManifest(config, (cfg) => {
    const manifest = cfg.modResults.manifest;
    addPermissions(manifest);
    addAccessibilityService(cfg.modResults);
    disableBackup(cfg.modResults);
    return cfg;
  });
}

function withArm64Only(config) {
  return withGradleProperties(config, (cfg) => {
    const key = 'reactNativeArchitectures';
    cfg.modResults = cfg.modResults.filter((item) => !(item.type === 'property' && item.key === key));
    cfg.modResults.push({ type: 'property', key, value: 'arm64-v8a' });
    return cfg;
  });
}

module.exports = function withMurmure(config) {
  config = withMurmureManifest(config);
  config = withArm64Only(config);
  return config;
};
