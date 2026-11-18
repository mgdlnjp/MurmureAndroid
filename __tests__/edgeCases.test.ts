// Cas limites complémentaires (Tester) pour la logique TS pure.
import type { HistoryItem, PermissionStatus } from '@/modules/murmure-native';
import { formatBytes, formatDate, formatDuration } from '@/src/format';
import { filterHistory, normalizeForSearch, wordCount } from '@/src/history';
import { isReady, nextOnboardingStep, SKIPPABLE_STEPS, type StepId } from '@/src/onboarding';

describe('formatBytes — cas limites', () => {
  it('valeurs négatives ramenées à 0', () => {
    expect(formatBytes(-5, 'en')).toBe('0 B');
    expect(formatBytes(-5, 'fr')).toBe('0 o');
  });
  it('frontières d’unités', () => {
    expect(formatBytes(1023, 'en')).toBe('1023 B');
    expect(formatBytes(1024, 'en')).toBe('1.0 KB');
    expect(formatBytes(10 * 1024, 'fr')).toBe('10 Ko');
    expect(formatBytes(1024 ** 3, 'fr')).toBe('1,0 Go');
  });
  it('très grandes valeurs plafonnées à la dernière unité', () => {
    expect(formatBytes(1024 ** 5 * 2, 'en')).toBe('2048 TB');
  });
});

describe('formatDuration — cas limites', () => {
  it('négatif → 0:00 ; arrondi à la seconde', () => {
    expect(formatDuration(-3)).toBe('0:00');
    expect(formatDuration(59.5)).toBe('1:00');
    expect(formatDuration(0.4)).toBe('0:00');
  });
  it('au-delà d’une heure reste en minutes', () => {
    expect(formatDuration(3725)).toBe('62:05');
  });
});

describe('formatDate — cas limites', () => {
  it('midi et minuit en anglais', () => {
    const now = new Date(2026, 10, 17, 23, 0).getTime();
    expect(formatDate(new Date(2026, 10, 17, 12, 0).getTime(), 'en', now)).toBe('Today 12:00 PM');
    expect(formatDate(new Date(2026, 10, 17, 0, 0).getTime(), 'en', now)).toBe('Today 12:00 AM');
  });
  it('hier au passage d’un mois / d’une année', () => {
    const now = new Date(2026, 0, 1, 8, 0).getTime();
    expect(formatDate(new Date(2025, 11, 31, 22, 15).getTime(), 'fr', now)).toBe('Hier 22:15');
  });
  it('date future (horloge décalée) → date courte, pas de plantage', () => {
    const now = new Date(2026, 10, 17, 8, 0).getTime();
    expect(formatDate(new Date(2026, 10, 20, 8, 0).getTime(), 'fr', now)).toBe('20/11/2026 08:00');
  });
});

const item = (id: string, text: string): HistoryItem => ({
  id,
  date: 0,
  text,
  raw: text,
  durationSec: 1,
  language: null,
  appPackage: null,
  outcome: 'NOTHING',
  modelId: 'base-q5_1',
});

describe('normalizeForSearch / filterHistory — cas limites', () => {
  it('chaîne vide', () => {
    expect(normalizeForSearch('')).toBe('');
    expect(filterHistory([], 'abc')).toEqual([]);
    expect(filterHistory([], '')).toEqual([]);
  });
  it('ligatures / cédilles / majuscules accentuées', () => {
    expect(normalizeForSearch('ÇÀ ÊTRE Noël')).toBe('ca etre noel');
  });
  it('« ete » trouve « été » (checklist manuelle) et requête accentuée trouve texte non accentué', () => {
    const items = [item('1', 'Un bel été'), item('2', 'Une fete'), item('3', 'Hello')];
    expect(filterHistory(items, 'ete').map((i) => i.id)).toEqual(['1', '2']);
    expect(filterHistory(items, 'FÊTE').map((i) => i.id)).toEqual(['2']);
  });
  it('mots partiels et espaces multiples dans la requête', () => {
    const items = [item('1', 'Rendez-vous demain matin')];
    expect(filterHistory(items, '  rendez   matin ').map((i) => i.id)).toEqual(['1']);
    expect(filterHistory(items, 'dem').map((i) => i.id)).toEqual(['1']);
  });
  it('caractères spéciaux de regex sans plantage', () => {
    const items = [item('1', 'prix (10 €) [promo] *')];
    expect(filterHistory(items, '(10').map((i) => i.id)).toEqual(['1']);
    expect(filterHistory(items, '[promo] *').map((i) => i.id)).toEqual(['1']);
    expect(filterHistory(items, '.*')).toEqual([]);
  });
  it('ne mute pas la liste d’origine', () => {
    const items = [item('1', 'a'), item('2', 'b')];
    const copy = [...items];
    filterHistory(items, 'a');
    expect(items).toEqual(copy);
  });
});

describe('wordCount — cas limites', () => {
  it('apostrophes, tirets, émojis, retours ligne', () => {
    expect(wordCount("l'été arrive")).toBe(2);
    expect(wordCount('rendez-vous\ndemain')).toBe(2);
    expect(wordCount('👍 ok')).toBe(1);
    expect(wordCount('42 ans')).toBe(2);
  });
});

const status = (p: Partial<PermissionStatus>): PermissionStatus => ({
  microphone: true,
  overlay: true,
  accessibility: true,
  notifications: true,
  sdkInt: 34,
  ...p,
});

describe('nextOnboardingStep — cas limites', () => {
  it('welcome prioritaire même si tout est prêt', () => {
    expect(nextOnboardingStep(status({}), true, false)).toBe('welcome');
  });
  it('API 33+ avec accessibilité déjà active : pas d’étape restricted', () => {
    expect(nextOnboardingStep(status({ overlay: false }), false, true, new Set<StepId>(['overlay']))).toBe('model');
    expect(nextOnboardingStep(status({}), false, true)).toBe('model');
  });
  it('modèle installé mais accessibilité absente → accessibility (après restricted passé)', () => {
    expect(nextOnboardingStep(status({ accessibility: false }), true, true)).toBe('restricted');
    expect(nextOnboardingStep(status({ accessibility: false }), true, true, new Set<StepId>(['restricted']))).toBe(
      'accessibility'
    );
  });
  it('API 32 : restricted jamais proposée', () => {
    expect(nextOnboardingStep(status({ sdkInt: 32, accessibility: false }), true, true)).toBe('accessibility');
  });
  it('micro retiré après coup → retour à microphone', () => {
    expect(nextOnboardingStep(status({ microphone: false }), true, true)).toBe('microphone');
  });
  it('étapes passables = notifications, overlay, restricted uniquement', () => {
    expect([...SKIPPABLE_STEPS].sort()).toEqual(['notifications', 'overlay', 'restricted']);
  });
  it('isReady ignore overlay/notifications et sdkInt', () => {
    expect(isReady(status({ overlay: false, notifications: false, sdkInt: 24 }), true)).toBe(true);
    expect(isReady(status({ microphone: false, accessibility: false }), false)).toBe(false);
  });
});
