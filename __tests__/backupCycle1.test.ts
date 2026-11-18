// Tester, cycle 1 (A1) : cas limites de la désactivation de la sauvegarde par le config plugin.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const withMurmure = require('../plugins/withMurmure.js');
import fs from 'fs';
import path from 'path';

type Node = { $?: Record<string, string>; [k: string]: unknown };
type Manifest = { manifest: { $: object; 'uses-permission'?: Node[]; application: Node[] } };

async function run(manifest: Manifest): Promise<Manifest> {
  const cfg = withMurmure({ name: 'Murmure', slug: 'murmure-android' });
  const out = await cfg.mods.android.manifest({
    ...cfg,
    modResults: manifest,
    modRequest: { platform: 'android', modName: 'manifest', projectRoot: '/tmp/x', platformProjectRoot: '/tmp/x/android', introspect: true },
  });
  return out.modResults;
}

describe('withMurmure : sauvegarde désactivée (cycle 1)', () => {
  it('pose les 3 attributs sur une <application> Expo minimale sans toucher au nom', async () => {
    const real: Manifest = { manifest: { $: {}, application: [{ $: { 'android:name': '.MainApplication' } }] } };
    const out = await run(real);
    const a = out.manifest.application[0].$!;
    expect(a['android:allowBackup']).toBe('false');
    expect(a['android:fullBackupContent']).toBe('false');
    expect(a['android:dataExtractionRules']).toBe('@xml/murmure_data_extraction_rules');
    expect(a['android:name']).toBe('.MainApplication');
  });

  it('remplace des valeurs existantes contraires (fullBackupContent et dataExtractionRules)', async () => {
    const m: Manifest = {
      manifest: {
        $: {},
        application: [
          {
            $: {
              'android:name': '.MainApplication',
              'android:allowBackup': 'true',
              'android:fullBackupContent': '@xml/secure_store_backup_rules',
              'android:dataExtractionRules': '@xml/secure_store_data_extraction_rules',
            },
          },
        ],
      },
    };
    const a = (await run(m)).manifest.application[0].$!;
    expect(a['android:allowBackup']).toBe('false');
    expect(a['android:fullBackupContent']).toBe('false');
    expect(a['android:dataExtractionRules']).toBe('@xml/murmure_data_extraction_rules');
  });

  it('la ressource XML référencée existe dans le module natif', () => {
    const file = path.join(__dirname, '..', 'modules/murmure-native/android/src/main/res/xml/murmure_data_extraction_rules.xml');
    const xml = fs.readFileSync(file, 'utf8');
    expect(xml).not.toMatch(/<include/);
    expect((xml.match(/<exclude /g) ?? []).length).toBe(18);
  });
});
