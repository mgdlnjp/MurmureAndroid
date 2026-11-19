import { isFrench, L, pickLanguage, strings } from '@/src/i18n';

describe('pickLanguage', () => {
  it('returns fr for French codes', () => {
    expect(pickLanguage('fr')).toBe('fr');
    expect(pickLanguage('FR')).toBe('fr');
    expect(pickLanguage('fr-CA')).toBe('fr');
  });

  it('returns en otherwise', () => {
    expect(pickLanguage('en')).toBe('en');
    expect(pickLanguage('de')).toBe('en');
    expect(pickLanguage('')).toBe('en');
    expect(pickLanguage(null)).toBe('en');
    expect(pickLanguage(undefined)).toBe('en');
  });
});

describe('L', () => {
  it('picks French with a French phone (mocked locale)', () => {
    expect(isFrench).toBe(true);
    expect(L('Bonjour', 'Hello')).toBe('Bonjour');
    expect(strings.tagline).toBe('Tu parles. Murmure écrit.');
  });

  it('picks English with an English phone', () => {
    jest.resetModules();
    jest.doMock('expo-localization', () => ({ getLocales: () => [{ languageCode: 'en' }] }));
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const i18n = require('../src/i18n') as typeof import('@/src/i18n');
    expect(i18n.isFrench).toBe(false);
    expect(i18n.L('Bonjour', 'Hello')).toBe('Hello');
    expect(i18n.strings.tagline).toBe('You speak. Murmure writes.');
  });
});
