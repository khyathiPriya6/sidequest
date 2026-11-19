import { useEffect, useState, type FormEvent } from 'react'
import { Compass, Search } from 'lucide-react'
import { useLocation, useSearchParams } from 'react-router-dom'
import { Button } from '../components/ui/Button/Button'
import { Card } from '../components/ui/Card/Card'
import { Input } from '../components/ui/Input/Input'
import { QuestCard } from '../features/quests/components/QuestCard/QuestCard'
import { displayEnum, getPublishedQuests, type QuestCategory, type QuestDifficulty, type QuestPage } from '../features/quests/questApi'
import styles from './ExploreShell.module.css'
import pageStyles from './Pages.module.css'

const categories: QuestCategory[] = ['ADVENTURE', 'CREATIVITY', 'FITNESS', 'LEARNING', 'FOOD', 'SOCIAL']
const difficulties: QuestDifficulty[] = ['EASY', 'MEDIUM', 'HARD']
type CardDifficulty = 'Easy' | 'Medium' | 'Hard'

function cardDifficulty(value: QuestDifficulty | null): CardDifficulty {
  if (value === 'MEDIUM') return 'Medium'
  if (value === 'HARD') return 'Hard'
  return 'Easy'
}

type LoadState =
  | { kind: 'loading' }
  | { kind: 'loaded'; results: QuestPage }
  | { kind: 'error'; message: string }

export function ExploreShell() {
  const [searchParams, setSearchParams] = useSearchParams()
  const location = useLocation()
  const queryString = searchParams.toString()
  const query = searchParams.get('q') ?? ''
  const category = searchParams.get('category') ?? ''
  const difficulty = searchParams.get('difficulty') ?? ''
  const [queryInput, setQueryInput] = useState(query)
  const [searchError, setSearchError] = useState('')
  const [state, setState] = useState<LoadState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => setQueryInput(query), [query])

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    setState({ kind: 'loading' })
    void getPublishedQuests(queryString, controller.signal).then(results => {
      if (active) setState({ kind: 'loaded', results })
    }).catch(error => {
      if (!active || controller.signal.aborted) return
      setState({
        kind: 'error',
        message: error instanceof Error ? error.message : 'Unable to load quests.',
      })
    })
    return () => {
      active = false
      controller.abort()
    }
  }, [queryString, attempt])

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const next = new URLSearchParams()
    const trimmed = queryInput.trim()
    if ([...trimmed].length > 120) {
      setSearchError('Search must be 120 characters or fewer.')
      return
    }
    setSearchError('')
    if (trimmed) next.set('q', trimmed)
    if (category) next.set('category', category)
    if (difficulty) next.set('difficulty', difficulty)
    setSearchParams(next)
  }

  function updateFilter(name: 'category' | 'difficulty', value: string) {
    const next = new URLSearchParams(searchParams)
    if (value) next.set(name, value)
    else next.delete(name)
    next.delete('page')
    setSearchParams(next)
  }

  function changePage(nextPage: number) {
    const next = new URLSearchParams(searchParams)
    if (nextPage > 0) next.set('page', String(nextPage))
    else next.delete('page')
    setSearchParams(next)
  }

  const hasFilters = Boolean(query || category || difficulty)
  const hasMatches = state.kind === 'loaded' && state.results.totalElements > 0

  return <div className={pageStyles.page}>
    <section className={pageStyles.hero} aria-labelledby="explore-title">
      <p className={pageStyles.eyebrow}>A little adventure goes a long way</p>
      <h1 id="explore-title">Make today<br />a <span>quest.</span></h1>
      <p>Small challenges. Real momentum.<br />Find something fun and make it happen.</p>
      <a className={pageStyles.action} href="#quest-board">Explore the quest board <span aria-hidden="true">↗</span></a>
    </section>

    <section id="quest-board" aria-labelledby="quest-board-title" className={pageStyles.section}>
      <div className={styles.heading}>
        <div><p className={pageStyles.eyebrow}>Your next adventure</p><h2 id="quest-board-title">The quest board</h2></div>
        {hasMatches && <p className={styles.total}>{state.results.totalElements} {state.results.totalElements === 1 ? 'quest' : 'quests'}</p>}
      </div>

      <form className={styles.filters} role="search" onSubmit={submitSearch}>
        <Input label="Search quests" type="search" placeholder="Try “walking route”" value={queryInput}
          maxLength={240} error={searchError} onChange={event => { setSearchError(''); setQueryInput(event.target.value) }} />
        <label className={styles.selectField}>
          <span>Category</span>
          <select value={category} onChange={event => updateFilter('category', event.target.value)}>
            <option value="">All categories</option>
            {categories.map(value => <option key={value} value={value}>{displayEnum(value)}</option>)}
          </select>
        </label>
        <label className={styles.selectField}>
          <span>Difficulty</span>
          <select value={difficulty} onChange={event => updateFilter('difficulty', event.target.value)}>
            <option value="">All difficulties</option>
            {difficulties.map(value => <option key={value} value={value}>{displayEnum(value)}</option>)}
          </select>
        </label>
        <Button type="submit" className={styles.searchButton}><Search size={18} aria-hidden="true" /> Search</Button>
        {hasFilters && <Button type="button" variant="ghost" onClick={() => { setQueryInput(''); setSearchParams({}) }}>Clear filters</Button>}
      </form>

      <div aria-live="polite" aria-busy={state.kind === 'loading'}>
        {state.kind === 'loading' && <Card className={styles.state} role="status">
          <span className={styles.skeleton} aria-hidden="true" />
          <span>Finding quests for you…</span>
        </Card>}
        {state.kind === 'error' && <Card className={styles.state} role="alert">
          <Compass size={30} aria-hidden="true" />
          <h3>We couldn’t load the quest board.</h3>
          <p>{state.message}</p>
          <Button variant="secondary" onClick={() => setAttempt(value => value + 1)}>Try again</Button>
        </Card>}
        {state.kind === 'loaded' && state.results.content.length > 0 && <div className={styles.grid}>
          {state.results.content.map(quest => <QuestCard key={quest.id} title={quest.title}
            description={quest.description ?? ''} category={displayEnum(quest.category)}
            difficulty={cardDifficulty(quest.difficulty)}
            rewardXp={quest.rewardXp ?? Number.NaN} href={`/quests/${encodeURIComponent(quest.id)}${location.search}`} />)}
        </div>}
        {state.kind === 'loaded' && state.results.content.length === 0 && <Card className={styles.state}>
          <Compass size={30} aria-hidden="true" />
          <h3>{state.results.totalElements === 0 && !hasFilters ? 'Your next great story starts soon.' : 'No quests match those filters.'}</h3>
          <p>{state.results.totalElements === 0 && !hasFilters
            ? 'There are no published quests yet. Check back soon.'
            : 'Try a different search or clear some filters to see more quests.'}</p>
          {hasFilters && <Button variant="secondary" onClick={() => { setQueryInput(''); setSearchParams({}) }}>Clear filters</Button>}
        </Card>}
      </div>

      {state.kind === 'loaded' && state.results.totalPages > 1 && <nav className={styles.pagination} aria-label="Quest pages">
        <Button variant="secondary" disabled={!state.results.hasPrevious} onClick={() => changePage(state.results.page - 1)}>Previous</Button>
        <span aria-current="page">Page {state.results.page + 1} of {state.results.totalPages}</span>
        <Button variant="secondary" disabled={!state.results.hasNext} onClick={() => changePage(state.results.page + 1)}>Next</Button>
      </nav>}

      {state.kind === 'loaded' && state.results.content.length === 0 && state.results.totalElements > 0 && <p className={styles.outOfRange}>
        There are quests on other pages. <Button variant="ghost" onClick={() => changePage(0)}>Go to the first page</Button>
      </p>}
    </section>
  </div>
}
