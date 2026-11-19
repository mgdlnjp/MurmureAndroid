import type { HistoryItem } from '@/modules/murmure-native';
import { filterHistory, normalizeForSearch, wordCount } from '@/src/history';

const item = (id: string, text: string): HistoryItem => ({
  id,
  date: 0,
  text,
  raw: text,
  durationSec: 1,
  language: 'fr',
  appPackage: null,
  outcome: 'SET_TEXT',
  modelId: 'small-q5_1',
});

const items = [
  item('1', "Cet été, on part à la plage"),
  item('2', 'Rendez-vous chez le Médecin demain'),
  item('3', 'Hello world from Murmure'),
];

describe('normalizeForSearch', () => {
  it('removes accents and case', () => {
    expect(normalizeForSearch('Été Élève ÇA')).toBe('ete eleve ca');
  });

  it('normalizes whitespace', () => {
    expect(normalizeForSearch('  a \n\t b  ')).toBe('a b');
  });
});

describe('filterHistory', () => {
  it('returns everything for an empty query', () => {
    expect(filterHistory(items, '')).toEqual(items);
    expect(filterHistory(items, '   ')).toEqual(items);
  });

  it('is accent and case insensitive', () => {
    expect(filterHistory(items, 'ete').map((i) => i.id)).toEqual(['1']);
    expect(filterHistory(items, 'MEDECIN').map((i) => i.id)).toEqual(['2']);
  });

  it('requires all words', () => {
    expect(filterHistory(items, 'plage été').map((i) => i.id)).toEqual(['1']);
    expect(filterHistory(items, 'plage médecin')).toEqual([]);
  });

  it('returns nothing when no match', () => {
    expect(filterHistory(items, 'xyz')).toEqual([]);
  });
});

describe('wordCount', () => {
  it('counts words', () => {
    expect(wordCount('')).toBe(0);
    expect(wordCount('   ')).toBe(0);
    expect(wordCount('Bonjour tout le monde')).toBe(4);
    expect(wordCount('Salut , ça va ?')).toBe(3);
  });
});
