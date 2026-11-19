// Critère 8 : toutes les chaînes visibles existent en FR et en EN (aucune vide, structure identique).
type Tree = { [k: string]: unknown };

function loadStrings(languageCode: string): Tree {
  let result: Tree = {};
  jest.isolateModules(() => {
    jest.doMock('expo-localization', () => ({ getLocales: () => [{ languageCode }] }));
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    result = require('../src/i18n').strings as Tree;
  });
  return result;
}

function leaves(tree: Tree, prefix = ''): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(tree)) {
    const key = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === 'object') Object.assign(out, leaves(v as Tree, key));
    else out[key] = v;
  }
  return out;
}

describe('i18n — complétude FR/EN', () => {
  const fr = leaves(loadStrings('fr'));
  const en = leaves(loadStrings('en'));

  it('mêmes clés dans les deux langues', () => {
    expect(Object.keys(fr).sort()).toEqual(Object.keys(en).sort());
  });

  it('aucune chaîne vide', () => {
    for (const [k, v] of [...Object.entries(fr), ...Object.entries(en)]) {
      if (typeof v === 'string') expect({ k, empty: v.trim() === '' }).toEqual({ k, empty: false });
    }
  });

  it('les chaînes dynamiques fonctionnent dans les deux langues', () => {
    const frWords = fr['history.words'] as (n: number) => string;
    const enWords = en['history.words'] as (n: number) => string;
    expect(frWords(0)).toBe('0 mot');
    expect(frWords(1)).toBe('1 mot');
    expect(frWords(2)).toBe('2 mots');
    expect(enWords(1)).toBe('1 word');
    expect(enWords(0)).toBe('0 words');
    expect((fr['onboarding.stepOf'] as (i: number, n: number) => string)(2, 8)).toBe('Étape 2 sur 8');
    expect((en['onboarding.stepOf'] as (i: number, n: number) => string)(2, 8)).toBe('Step 2 of 8');
  });

  it('slogan dans les deux langues', () => {
    expect(fr.tagline).toBe('Tu parles. Murmure écrit.');
    expect(en.tagline).toBe('You speak. Murmure writes.');
  });

  it('les traductions diffèrent (sauf noms propres / identiques par nature)', () => {
    const allowedSame = new Set(['appName', 'settings.languageAuto', 'onboarding.notifTitle', 'settings.version']);
    const same = Object.keys(fr).filter(
      (k) => typeof fr[k] === 'string' && fr[k] === en[k] && !allowedSame.has(k)
    );
    expect(same).toEqual([]);
  });
});

describe('i18n — locale absente ou en erreur', () => {
  it('getLocales vide → EN', () => {
    jest.isolateModules(() => {
      jest.doMock('expo-localization', () => ({ getLocales: () => [] }));
      // eslint-disable-next-line @typescript-eslint/no-require-imports
      expect(require('../src/i18n').uiLanguage).toBe('en');
    });
  });
  it('getLocales qui lève → EN sans planter', () => {
    jest.isolateModules(() => {
      jest.doMock('expo-localization', () => ({
        getLocales: () => {
          throw new Error('boom');
        },
      }));
      // eslint-disable-next-line @typescript-eslint/no-require-imports
      expect(require('../src/i18n').uiLanguage).toBe('en');
    });
  });
  it('fr-CA (languageCode « fr ») → FR', () => {
    jest.isolateModules(() => {
      jest.doMock('expo-localization', () => ({ getLocales: () => [{ languageCode: 'fr', regionCode: 'CA' }] }));
      // eslint-disable-next-line @typescript-eslint/no-require-imports
      expect(require('../src/i18n').isFrench).toBe(true);
    });
  });
});
