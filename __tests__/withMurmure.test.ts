// Tests du config plugin (manifeste + gradle.properties), sans lancer de prebuild.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const withMurmure = require('../plugins/withMurmure.js');

const SERVICE = 'app.murmure.core.MurmureAccessibilityService';
const PERMISSIONS = [
  'android.permission.RECORD_AUDIO',
  'android.permission.INTERNET',
  'android.permission.SYSTEM_ALERT_WINDOW',
  'android.permission.FOREGROUND_SERVICE',
  'android.permission.FOREGROUND_SERVICE_MICROPHONE',
  'android.permission.POST_NOTIFICATIONS',
];

type Node = { $?: Record<string, string>; [k: string]: unknown };
type Manifest = { manifest: { $: object; 'uses-permission'?: Node[]; application: Node[] } };
type Prop = { type: string; key?: string; value?: string };

const modRequest = (modName: string) => ({
  platform: 'android',
  modName,
  projectRoot: '/tmp/x',
  platformProjectRoot: '/tmp/x/android',
  introspect: true,
});

async function runManifest(manifest: Manifest): Promise<Manifest> {
  const cfg = withMurmure({ name: 'Murmure', slug: 'murmure-android' });
  const out = await cfg.mods.android.manifest({ ...cfg, modResults: manifest, modRequest: modRequest('manifest') });
  return out.modResults;
}

async function runGradle(props: Prop[]): Promise<Prop[]> {
  const cfg = withMurmure({ name: 'Murmure', slug: 'murmure-android' });
  const out = await cfg.mods.android.gradleProperties({ ...cfg, modResults: props, modRequest: modRequest('gradleProperties') });
  return out.modResults;
}

const baseManifest = (): Manifest => ({
  manifest: {
    $: {},
    'uses-permission': [{ $: { 'android:name': 'android.permission.INTERNET' } }],
    application: [{ $: { 'android:name': '.MainApplication' } }],
  },
});

const permNames = (m: Manifest) => (m.manifest['uses-permission'] ?? []).map((p) => p.$?.['android:name']);
const services = (m: Manifest) => ((m.manifest.application[0].service as Node[]) ?? []);

describe('withMurmure — manifeste', () => {
  it('ajoute les 6 permissions sans doublon (INTERNET déjà présente)', async () => {
    const m = await runManifest(baseManifest());
    const names = permNames(m);
    for (const p of PERMISSIONS) expect(names.filter((n) => n === p)).toHaveLength(1);
  });

  it('crée la liste de permissions si absente', async () => {
    const m = baseManifest();
    delete m.manifest['uses-permission'];
    const out = await runManifest(m);
    expect(permNames(out).sort()).toEqual([...PERMISSIONS].sort());
  });

  it('déclare le service d’accessibilité complet', async () => {
    const m = await runManifest(baseManifest());
    const svc = services(m);
    expect(svc).toHaveLength(1);
    const s = svc[0];
    expect(s.$).toEqual({
      'android:name': SERVICE,
      'android:exported': 'true',
      'android:label': '@string/murmure_accessibility_label',
      'android:permission': 'android.permission.BIND_ACCESSIBILITY_SERVICE',
      'android:foregroundServiceType': 'microphone',
    });
    expect(JSON.stringify(s['intent-filter'])).toContain('android.accessibilityservice.AccessibilityService');
    expect(s['meta-data']).toEqual([
      { $: { 'android:name': 'android.accessibilityservice', 'android:resource': '@xml/murmure_accessibility_service' } },
    ]);
  });

  it('est idempotent (deux passes → pas de doublon) et conserve les autres services', async () => {
    const m = baseManifest();
    m.manifest.application[0].service = [{ $: { 'android:name': 'com.other.Service' } }];
    const once = await runManifest(m);
    const twice = await runManifest(once);
    expect(services(twice).map((s) => s.$?.['android:name'])).toEqual(['com.other.Service', SERVICE]);
    for (const p of PERMISSIONS) expect(permNames(twice).filter((n) => n === p)).toHaveLength(1);
  });
});

describe('withMurmure — sauvegarde désactivée (cycle 1, A1)', () => {
  it('désactive allowBackup et toute extraction de données', async () => {
    const m = await runManifest(baseManifest());
    const attrs = m.manifest.application[0].$ ?? {};
    expect(attrs['android:allowBackup']).toBe('false');
    expect(attrs['android:fullBackupContent']).toBe('false');
    expect(attrs['android:dataExtractionRules']).toBe('@xml/murmure_data_extraction_rules');
    expect(attrs['android:name']).toBe('.MainApplication');
  });

  it('remplace un allowBackup="true" existant, de façon idempotente', async () => {
    const m = baseManifest();
    m.manifest.application[0].$ = { 'android:name': '.MainApplication', 'android:allowBackup': 'true' };
    const twice = await runManifest(await runManifest(m));
    expect(twice.manifest.application[0].$?.['android:allowBackup']).toBe('false');
  });
});

describe('withMurmure — gradle.properties', () => {
  it('force reactNativeArchitectures=arm64-v8a une seule fois', async () => {
    const props: Prop[] = [
      { type: 'comment', value: 'x' },
      { type: 'property', key: 'reactNativeArchitectures', value: 'armeabi-v7a,arm64-v8a,x86,x86_64' },
      { type: 'property', key: 'newArchEnabled', value: 'true' },
    ];
    const once = await runGradle(props);
    const twice = await runGradle(once);
    const arch = twice.filter((p) => p.key === 'reactNativeArchitectures');
    expect(arch).toEqual([{ type: 'property', key: 'reactNativeArchitectures', value: 'arm64-v8a' }]);
    expect(twice.find((p) => p.key === 'newArchEnabled')?.value).toBe('true');
  });
});
