import { useEffect, useState } from 'react'
import {
  ActivityIndicator, FlatList, Keyboard, Pressable, SafeAreaView, ScrollView,
  StatusBar, StyleSheet, Text, TextInput, View,
} from 'react-native'
import { questDetails, questFeed, type Quest, type QuestPage } from './src/api'

// Keep native UI colors aligned with ui/src/styles/tokens.css.
const palette = {
  bg: '#0B1020', surface: '#151C30', raised: '#202A43', accent: '#A78BFA',
  text: '#F5F7FF', muted: '#C0CBE2', border: '#364460', danger: '#FB8794',
  reward: '#F6C665',
}

function Pill({ children }: { children: string }) {
  return <Text style={styles.pill}>{children}</Text>
}
function QuestCard({ quest, onPress }: { quest: Quest; onPress: () => void }) {
  return <Pressable accessibilityRole="button" accessibilityLabel={'View ' + quest.title} onPress={onPress}
    style={({ pressed }) => [styles.card, pressed && { opacity: 0.8 }]}>
    <View style={styles.pills}>
      <Pill>{quest.category ?? 'QUEST'}</Pill>
      {quest.difficulty && <Pill>{quest.difficulty}</Pill>}
    </View>
    <Text style={styles.cardTitle}>{quest.title}</Text>
    <Text numberOfLines={2} style={styles.body}>{quest.description || 'An adventure is waiting.'}</Text>
    <View style={styles.cardBottom}>
      <Text style={styles.reward}>✦ {quest.rewardXp ?? 0} XP</Text>
      <Text style={styles.open}>Explore  →</Text>
    </View>
  </Pressable>
}

export default function App() {
  const [query, setQuery] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<QuestPage | null>(null)
  const [selected, setSelected] = useState<Quest | null>(null)
  const [loading, setLoading] = useState(true)
  const [detailLoading, setDetailLoading] = useState(false)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError('')
    void questFeed(search, page, controller.signal).then(data => {
      if (!controller.signal.aborted) setResult(data)
    }).catch(err => {
      if (!controller.signal.aborted) setError(err instanceof Error ? err.message : 'Unable to load Quests.')
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [search, page, revision])

  function openQuest(quest: Quest) {
    setSelected(quest)
    setDetailLoading(true)
    setError('')
    const controller = new AbortController()
    void questDetails(quest.id, controller.signal).then(setSelected).catch(err => {
      setError(err instanceof Error ? err.message : 'Unable to load Quest.')
    }).finally(() => setDetailLoading(false))
  }

  return <SafeAreaView style={styles.root}>
    <StatusBar barStyle="light-content" />
    <View style={styles.header}>
      <Text style={styles.brand}>✦ SideQuest</Text>
      <Text style={styles.headerSub}>Your story, one Quest at a time.</Text>
    </View>
    {selected ? <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
      <Pressable accessibilityRole="button" onPress={() => { setSelected(null); setError('') }} style={styles.back}>
        <Text style={styles.backText}>← Explore Quests</Text>
      </Pressable>
      <View style={styles.pills}><Pill>{selected.category ?? 'QUEST'}</Pill>
        {selected.difficulty && <Pill>{selected.difficulty}</Pill>}</View>
      <Text style={styles.heroTitle}>{selected.title}</Text>
      <Text style={styles.description}>{selected.description || 'No description provided.'}</Text>
      <Text style={styles.reward}>✦ {selected.rewardXp ?? 0} XP</Text>
      {detailLoading && <ActivityIndicator color={palette.accent} />}
      {error !== '' && <Text accessibilityRole="alert" style={styles.error}>{error}</Text>}
      <View style={styles.notice}><Text style={styles.noticeTitle}>Ready for an adventure?</Text>
        <Text style={styles.body}>Quest joining and XP will be available after native session authentication is integrated.</Text></View>
    </ScrollView> : <>
      <View style={styles.intro}>
        <Text style={styles.eyebrow}>DISCOVER SOMETHING NEW</Text>
        <Text style={styles.heroTitle}>Make today a little more adventurous.</Text>
        <TextInput style={styles.input} accessibilityLabel="Search Quests" placeholder="Search Quests…"
          placeholderTextColor={palette.muted} value={query} onChangeText={setQuery} returnKeyType="search"
          onSubmitEditing={() => { setPage(0); setSearch(query.trim()); Keyboard.dismiss() }} />
        <Pressable style={styles.searchButton} accessibilityRole="button"
          onPress={() => { setPage(0); setSearch(query.trim()); Keyboard.dismiss() }}>
          <Text style={styles.searchText}>Search Quests</Text>
        </Pressable>
      </View>
      {loading && <ActivityIndicator style={styles.loader} color={palette.accent} />}
      {error !== '' && <View style={styles.message}><Text accessibilityRole="alert" style={styles.error}>{error}</Text>
        <Pressable accessibilityRole="button" onPress={() => setRevision(x => x + 1)}><Text style={styles.open}>Retry connection →</Text></Pressable></View>}
      {!loading && !error && <FlatList data={result?.content ?? []} keyExtractor={item => item.id}
        contentContainerStyle={styles.list}
        ListEmptyComponent={<View style={styles.message}><Text style={styles.body}>No Quests found. Try another search.</Text></View>}
        renderItem={({ item }) => <QuestCard quest={item} onPress={() => openQuest(item)} />}
        ListFooterComponent={result && result.totalPages > 1 ? <View style={styles.pagination}>
          <Pressable accessibilityRole="button" disabled={!result.hasPrevious} onPress={() => setPage(p => Math.max(0, p - 1))}>
            <Text style={[styles.open, !result.hasPrevious && styles.disabled]}>← Previous</Text></Pressable>
          <Text style={styles.body}>{result.page + 1} / {result.totalPages}</Text>
          <Pressable accessibilityRole="button" disabled={!result.hasNext} onPress={() => setPage(p => p + 1)}>
            <Text style={[styles.open, !result.hasNext && styles.disabled]}>Next →</Text></Pressable>
        </View> : undefined}
      />}
    </>}
    <View style={styles.footer}><Text style={styles.footerText}>EXPLORE  ✦  SIDEQUEST MOBILE</Text></View>
  </SafeAreaView>
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: palette.bg },
  header: { paddingHorizontal: 22, paddingTop: 20, paddingBottom: 14, borderBottomWidth: 1, borderBottomColor: palette.border },
  brand: { fontSize: 23, fontWeight: '800', letterSpacing: -0.8, color: palette.accent },
  headerSub: { color: palette.muted, fontSize: 12, marginTop: 3 },
  intro: { padding: 22, paddingBottom: 12 },
  eyebrow: { color: palette.accent, fontSize: 11, fontWeight: '700', letterSpacing: 2 },
  heroTitle: { color: palette.text, fontSize: 31, fontWeight: '800', letterSpacing: -0.8, lineHeight: 38, marginVertical: 14 },
  input: { backgroundColor: palette.surface, borderColor: palette.border, borderWidth: 1, borderRadius: 14, color: palette.text, fontSize: 16, padding: 15, marginTop: 4 },
  searchButton: { backgroundColor: palette.accent, padding: 13, borderRadius: 13, alignItems: 'center', marginTop: 10 },
  searchText: { color: palette.bg, fontWeight: '800' },
  list: { paddingHorizontal: 22, paddingVertical: 12, paddingBottom: 30, gap: 12 },
  card: { padding: 20, borderRadius: 18, backgroundColor: palette.surface, borderColor: palette.border, borderWidth: 1, gap: 12 },
  pills: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  pill: { color: palette.accent, backgroundColor: palette.raised, paddingHorizontal: 11, paddingVertical: 6, borderRadius: 30, fontSize: 10, letterSpacing: 0.8, fontWeight: '700' },
  cardTitle: { fontSize: 20, fontWeight: '700', color: palette.text },
  body: { color: palette.muted, fontSize: 14, lineHeight: 22 },
  cardBottom: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 6 },
  reward: { color: palette.reward, fontSize: 16, fontWeight: '700' },
  open: { color: palette.accent, fontWeight: '700' },
  content: { padding: 24, gap: 15 },
  back: { paddingVertical: 12 },
  backText: { color: palette.accent, fontWeight: '700' },
  description: { color: palette.text, fontSize: 16, lineHeight: 26 },
  notice: { marginTop: 16, backgroundColor: palette.surface, borderRadius: 16, padding: 18, gap: 8 },
  noticeTitle: { color: palette.text, fontSize: 18, fontWeight: '700' },
  loader: { marginVertical: 35 },
  message: { padding: 24, gap: 12 },
  error: { color: palette.danger, lineHeight: 21 },
  disabled: { opacity: 0.35 },
  pagination: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 20 },
  footer: { padding: 12, borderTopWidth: 1, borderTopColor: palette.border, alignItems: 'center' },
  footerText: { color: palette.muted, fontSize: 10, letterSpacing: 1.2 },
})
