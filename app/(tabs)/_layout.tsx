import { Tabs } from 'expo-router/js-tabs';
import { type ColorValue, Text } from 'react-native';

import { strings } from '@/src/i18n';
import { palette } from '@/src/theme';

function TabGlyph({ glyph, color }: { glyph: string; color: ColorValue }) {
  return <Text style={{ color, fontSize: 18 }}>{glyph}</Text>;
}

export default function TabsLayout() {
  return (
    <Tabs screenOptions={{ tabBarActiveTintColor: palette.accent }}>
      <Tabs.Screen
        name="index"
        options={{ title: strings.tabs.home, tabBarIcon: ({ color }) => <TabGlyph glyph="●" color={color} /> }}
      />
      <Tabs.Screen
        name="history"
        options={{ title: strings.tabs.history, tabBarIcon: ({ color }) => <TabGlyph glyph="☰" color={color} /> }}
      />
      <Tabs.Screen
        name="settings"
        options={{ title: strings.tabs.settings, tabBarIcon: ({ color }) => <TabGlyph glyph="⚙" color={color} /> }}
      />
    </Tabs>
  );
}
