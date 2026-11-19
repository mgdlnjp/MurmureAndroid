import { ScrollView, StyleSheet } from 'react-native';

import { ModelPicker } from '@/src/components/ModelPicker';
import { spacing, useTheme } from '@/src/theme';

export default function ModelScreen() {
  const theme = useTheme();
  return (
    <ScrollView style={{ backgroundColor: theme.background }} contentContainerStyle={styles.content}>
      <ModelPicker />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.md, paddingBottom: spacing.xl },
});
