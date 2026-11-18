import type { HistoryItem } from '@/modules/murmure-native';

/** Minuscules, sans accents (NFD), espaces normalisés. */
export function normalizeForSearch(s: string): string {
  return s
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
}

/** Requête vide → tout ; sinon tous les mots doivent apparaître (insensible casse/accents). */
export function filterHistory(items: HistoryItem[], query: string): HistoryItem[] {
  const words = normalizeForSearch(query).split(' ').filter(Boolean);
  if (words.length === 0) return items;
  return items.filter((item) => {
    const haystack = normalizeForSearch(item.text);
    return words.every((w) => haystack.includes(w));
  });
}

export function wordCount(s: string): number {
  const trimmed = s.trim();
  if (!trimmed) return 0;
  return trimmed.split(/\s+/).filter((w) => /[\p{L}\p{N}]/u.test(w)).length;
}
