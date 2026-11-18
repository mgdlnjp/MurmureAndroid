import { formatBytes, formatDate, formatDuration } from '@/src/format';

describe('formatBytes', () => {
  it('formats model sizes', () => {
    expect(formatBytes(190085487, 'fr')).toBe('181 Mo');
    expect(formatBytes(190085487, 'en')).toBe('181 MB');
    expect(formatBytes(59707625, 'fr')).toBe('57 Mo');
    expect(formatBytes(59707625, 'en')).toBe('57 MB');
  });

  it('uses one decimal below 10', () => {
    expect(formatBytes(1.5 * 1024 * 1024, 'en')).toBe('1.5 MB');
    expect(formatBytes(1.5 * 1024 * 1024, 'fr')).toBe('1,5 Mo');
  });

  it('handles small values', () => {
    expect(formatBytes(0, 'en')).toBe('0 B');
    expect(formatBytes(512, 'fr')).toBe('512 o');
  });
});

describe('formatDuration', () => {
  it('formats m:ss', () => {
    expect(formatDuration(75.4)).toBe('1:15');
    expect(formatDuration(4.2)).toBe('0:04');
    expect(formatDuration(0)).toBe('0:00');
    expect(formatDuration(120)).toBe('2:00');
  });
});

describe('formatDate', () => {
  const now = new Date(2026, 10, 17, 18, 0).getTime();

  it('formats today', () => {
    const ms = new Date(2026, 10, 17, 14, 5).getTime();
    expect(formatDate(ms, 'fr', now)).toBe("Aujourd'hui 14:05");
    expect(formatDate(ms, 'en', now)).toBe('Today 2:05 PM');
  });

  it('formats yesterday', () => {
    const ms = new Date(2026, 10, 16, 9, 30).getTime();
    expect(formatDate(ms, 'fr', now)).toBe('Hier 09:30');
    expect(formatDate(ms, 'en', now)).toBe('Yesterday 9:30 AM');
  });

  it('formats older dates', () => {
    const ms = new Date(2026, 0, 3, 0, 7).getTime();
    expect(formatDate(ms, 'fr', now)).toBe('03/01/2026 00:07');
    expect(formatDate(ms, 'en', now)).toBe('01/03/2026 12:07 AM');
  });
});
