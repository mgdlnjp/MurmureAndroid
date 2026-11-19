import { useMemo, useState } from 'react';
import { Alert, FlatList, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import MurmureNative, { type HistoryItem } from '@/modules/murmure-native';
import { formatDate, formatDuration } from '@/src/format';
import { filterHistory, wordCount } from '@/src/history';
import { strings, uiLanguage } from '@/src/i18n';
import { radius, spacing, useTheme } from '@/src/theme';
import { useHistory } from '@/src/useNativeState';

export default function HistoryScreen() {
  const theme = useTheme();
  const [items, refresh] = useHistory();
  const [query, setQuery] = useState('');
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const filtered = useMemo(() => filterHistory(items, query), [items, query]);

  const copy = (item: HistoryItem) => {
    MurmureNative.copyToClipboard(item.text);
    setCopiedId(item.id);
    setTimeout(() => setCopiedId((id) => (id === item.id ? null : id)), 1500);
  };

  const remove = (item: HistoryItem) => {
    MurmureNative.deleteHistoryItem(item.id);
    refresh();
  };

  const clearAll = () => {
    Alert.alert(strings.history.clearTitle, strings.history.clearMessage, [
      { text: strings.history.cancel, style: 'cancel' },
      {
        text: strings.history.clearAll,
        style: 'destructive',
        onPress: () => {
          MurmureNative.clearHistory();
          refresh();
        },
      },
    ]);
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.background }]}>
      <View style={styles.toolbar}>
        <TextInput
          value={query}
          onChangeText={setQuery}
          placeholder={strings.history.search}
          placeholderTextColor={theme.textSecondary}
          style={[styles.search, { color: theme.text, backgroundColor: theme.card, borderColor: theme.border }]}
          autoCorrect={false}
        />
        {items.length > 0 ? (
          <Pressable onPress={clearAll} accessibilityRole="button" style={styles.clear}>
            <Text style={{ color: theme.danger, fontWeight: '600' }}>{strings.history.clearAll}</Text>
          </Pressable>
        ) : null}
      </View>
      <FlatList
        data={filtered}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.list}
        ListEmptyComponent={
          <Text style={[styles.empty, { color: theme.textSecondary }]}>
            {items.length === 0 ? strings.history.empty : strings.history.noResult}
          </Text>
        }
        renderItem={({ item }) => (
          <View style={[styles.card, { backgroundColor: theme.card, borderColor: theme.border }]}>
            <Text style={[styles.text, { color: theme.text }]} selectable>
              {item.text}
            </Text>
            <Text style={[styles.meta, { color: theme.textSecondary }]}>
              {[
                formatDate(item.date, uiLanguage),
                formatDuration(item.durationSec),
                strings.history.words(wordCount(item.text)),
                item.language?.toUpperCase(),
                strings.history.outcome[item.outcome],
              ]
                .filter(Boolean)
                .join(' · ')}
            </Text>
            <View style={styles.actions}>
              <Pressable onPress={() => copy(item)} accessibilityRole="button">
                <Text style={{ color: theme.accent, fontWeight: '600' }}>
                  {copiedId === item.id ? strings.history.copied : strings.history.copy}
                </Text>
              </Pressable>
              <Pressable onPress={() => remove(item)} accessibilityRole="button">
                <Text style={{ color: theme.danger, fontWeight: '600' }}>{strings.history.delete}</Text>
              </Pressable>
            </View>
          </View>
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  toolbar: { flexDirection: 'row', alignItems: 'center', padding: spacing.md, gap: spacing.sm },
  search: { flex: 1, borderWidth: StyleSheet.hairlineWidth, borderRadius: radius.md, paddingHorizontal: 12, paddingVertical: 8, fontSize: 15 },
  clear: { paddingHorizontal: 4 },
  list: { paddingHorizontal: spacing.md, paddingBottom: spacing.xl },
  empty: { textAlign: 'center', marginTop: spacing.xl, fontSize: 15 },
  card: { borderRadius: radius.md, borderWidth: StyleSheet.hairlineWidth, padding: spacing.md, marginBottom: spacing.sm },
  text: { fontSize: 16, lineHeight: 22 },
  meta: { fontSize: 12, marginTop: 6 },
  actions: { flexDirection: 'row', gap: spacing.lg, marginTop: spacing.sm },
});
