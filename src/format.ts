import type { UiLanguage } from './i18n';

/** 190085487 → « 181 Mo » / « 181 MB » (base 1024 ; 0 décimale si ≥ 10, 1 sinon). */
export function formatBytes(n: number, lang: UiLanguage): string {
  const units = lang === 'fr' ? ['o', 'Ko', 'Mo', 'Go', 'To'] : ['B', 'KB', 'MB', 'GB', 'TB'];
  let value = Math.max(0, n);
  let i = 0;
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024;
    i++;
  }
  const digits = i === 0 || value >= 10 ? 0 : 1;
  let text = value.toFixed(digits);
  if (lang === 'fr') text = text.replace('.', ',');
  return `${text} ${units[i]}`;
}

/** 75.4 → « 1:15 », 4.2 → « 0:04 ». */
export function formatDuration(sec: number): string {
  const total = Math.max(0, Math.round(sec));
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${s.toString().padStart(2, '0')}`;
}

function sameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

function formatTime(d: Date, lang: UiLanguage): string {
  const h = d.getHours();
  const m = d.getMinutes().toString().padStart(2, '0');
  if (lang === 'fr') return `${h.toString().padStart(2, '0')}:${m}`;
  const h12 = h % 12 === 0 ? 12 : h % 12;
  return `${h12}:${m} ${h < 12 ? 'AM' : 'PM'}`;
}

/** « Aujourd'hui 14:05 » / « Today 2:05 PM » ; « Hier »/« Yesterday » ; sinon date courte. */
export function formatDate(ms: number, lang: UiLanguage, now: number = Date.now()): string {
  const d = new Date(ms);
  const today = new Date(now);
  const yesterday = new Date(now);
  yesterday.setDate(today.getDate() - 1);
  const time = formatTime(d, lang);
  if (sameDay(d, today)) return `${lang === 'fr' ? "Aujourd'hui" : 'Today'} ${time}`;
  if (sameDay(d, yesterday)) return `${lang === 'fr' ? 'Hier' : 'Yesterday'} ${time}`;
  const dd = d.getDate().toString().padStart(2, '0');
  const mm = (d.getMonth() + 1).toString().padStart(2, '0');
  const yyyy = d.getFullYear();
  return lang === 'fr' ? `${dd}/${mm}/${yyyy} ${time}` : `${mm}/${dd}/${yyyy} ${time}`;
}
