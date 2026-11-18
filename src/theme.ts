import { useColorScheme } from 'react-native';

export const palette = {
  accent: '#F97316',
  accentDark: '#E2510C',
  danger: '#EF4444',
  success: '#34BD75',
  white: '#FFFFFF',
};

const light = {
  ...palette,
  background: '#FFFFFF',
  card: '#F4F4F5',
  border: '#E4E4E7',
  text: '#111111',
  textSecondary: '#60646C',
};

const dark: typeof light = {
  ...palette,
  background: '#0B0B0C',
  card: '#1C1C1F',
  border: '#2E3135',
  text: '#F5F5F5',
  textSecondary: '#A1A1AA',
};

export type Theme = typeof light;

export const spacing = { xs: 4, sm: 8, md: 16, lg: 24, xl: 32 } as const;
export const radius = { sm: 8, md: 14, lg: 20 } as const;

export function useTheme(): Theme {
  return useColorScheme() === 'dark' ? dark : light;
}
