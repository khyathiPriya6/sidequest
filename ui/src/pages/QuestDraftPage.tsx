import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Button } from '../components/ui/Button/Button'
import { Card } from '../components/ui/Card/Card'
import { Input } from '../components/ui/Input/Input'
import { getCsrfToken } from '../features/auth/authApi'
import {
  createQuestDraft,
  getOwnedQuest,
  updateQuestDraft,
  QuestApiError,
  type QuestCategory,
  type QuestDifficulty,
  type QuestDraftInput,
} from '../features/quests/questApi'
import styles from './QuestDraftPage.module.css'

interface DraftValues {
  title: string
  description: string
  category: QuestCategory | ''
  difficulty: QuestDifficulty | ''
  rewardXp: string
}

const emptyDraft: DraftValues = { title: '', description: '', category: '', difficulty: '', rewardXp: '' }

export function QuestDraftPage({ mode }: { mode: 'create' | 'edit' }) {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const [values, setValues] = useState<DraftValues>(emptyDraft)
  const [initialValues, setInitialValues] = useState<DraftValues>(emptyDraft)
  const [etag, setEtag] = useState('"0"')
  const [loading, setLoading] = useState(mode === 'edit')
  const [saving, setSaving] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [saveError, setSaveError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [saved, setSaved] = useState(false)
  const [loadAttempt, setLoadAttempt] = useState(0)

  useEffect(() => {
    if (mode !== 'edit') return
    const controller = new AbortController()
    setLoading(true)
    setLoadError('')
    void getOwnedQuest(id, controller.signal).then(result => {
      if (controller.signal.aborted) return
      if (result.quest.status !== 'DRAFT') {
        setLoadError('Only draft quests can be edited.')
        setLoading(false)
        return
      }
      const loaded: DraftValues = {
        title: result.quest.title,
        description: result.quest.description ?? '',
        category: result.quest.category ?? '',
        difficulty: result.quest.difficulty ?? '',
        rewardXp: result.quest.rewardXp?.toString() ?? '',
      }
      setValues(loaded)
      setInitialValues(loaded)
      setEtag(result.etag)
      setLoading(false)
    }).catch(error => {
      if (controller.signal.aborted) return
      setLoadError(error instanceof Error ? error.message : 'Unable to load this draft.')
      setLoading(false)
    })
    return () => controller.abort()
  }, [id, mode, loadAttempt])

  const dirty = useMemo(() => JSON.stringify(values) !== JSON.stringify(initialValues), [values, initialValues])

  function setField<K extends keyof DraftValues>(key: K, value: DraftValues[K]) {
    setSaved(false)
    setValues(current => ({ ...current, [key]: value }))
  }

  function validate(): Record<string, string> {
    const errors: Record<string, string> = {}
    if (values.title.trim() && ([...values.title.trim()].length < 3 || [...values.title.trim()].length > 120)) {
      errors.title = 'Use 3–120 Unicode characters.'
    }
    if (values.description.trim() && ([...values.description.trim()].length < 10 || [...values.description.trim()].length > 3000)) {
      errors.description = 'Use 10–3000 Unicode characters.'
    }
    if (values.rewardXp.trim()) {
      const reward = Number(values.rewardXp)
      if (!Number.isInteger(reward) || reward < 1 || reward > 1000) errors.rewardXp = 'Use a whole number from 1 to 1000.'
    }
    return errors
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (saving) return
    const localErrors = validate()
    setFieldErrors(localErrors)
    setSaveError('')
    if (Object.keys(localErrors).length > 0) return

    const input: QuestDraftInput = {
      title: values.title.trim(),
      description: values.description.trim() ? values.description.trim() : null,
      category: values.category || null,
      difficulty: values.difficulty || null,
      rewardXp: values.rewardXp.trim() ? Number(values.rewardXp) : null,
    }

    setSaving(true)
    try {
      const csrfToken = (await getCsrfToken()).csrfToken
      const result = mode === 'create'
        ? await createQuestDraft(input, csrfToken)
        : await updateQuestDraft(id, input, etag, csrfToken)
      setEtag(result.etag)
      const next: DraftValues = {
        title: result.quest.title,
        description: result.quest.description ?? '',
        category: result.quest.category ?? '',
        difficulty: result.quest.difficulty ?? '',
        rewardXp: result.quest.rewardXp?.toString() ?? '',
      }
      setValues(next)
      setInitialValues(next)
      setSaved(true)
      setFieldErrors({})
      if (mode === 'create') navigate(`/quests/${encodeURIComponent(result.quest.id)}/edit`, { replace: true })
    } catch (error) {
      if (error instanceof QuestApiError) {
        setSaveError(error.status === 412
          ? 'This draft changed in another tab or session. Reload it before saving again.'
          : error.status === 409
            ? 'The draft could not be changed in its current state.'
            : error.message)
        setFieldErrors(Object.fromEntries(error.fieldErrors.map(item => [item.field, item.message])))
      } else {
        setSaveError(error instanceof Error ? error.message : 'Unable to save this draft.')
      }
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <Card className={styles.state} role="status">Loading draft…</Card>
  if (loadError) return <Card className={styles.state} role="alert">
    <h1>Draft unavailable</h1>
    <p>{loadError}</p>
    {mode === 'edit' && <Button variant="secondary" onClick={() => setLoadAttempt(value => value + 1)}>Try again</Button>}
  </Card>

  return <section className={styles.page} aria-labelledby="draft-title">
    <header className={styles.header}>
      <div>
        <p className={styles.eyebrow}>My quests</p>
        <h1 id="draft-title">{mode === 'create' ? 'Create a quest' : 'Edit quest draft'}</h1>
      </div>
      <Link className={styles.back} to="/my-quests">Back to My quests</Link>
    </header>
    <form className={styles.form} onSubmit={submit} noValidate>
      <Card className={styles.fields}>
        <Input label="Title" value={values.title} maxLength={240} error={fieldErrors.title}
          hint="Optional until publication; 3–120 characters."
          onChange={event => setField('title', event.target.value)} />
        <div className={styles.field}>
          <label htmlFor="quest-description">Description</label>
          <textarea id="quest-description" value={values.description} maxLength={3000}
            aria-invalid={fieldErrors.description ? true : undefined}
            aria-describedby={fieldErrors.description ? 'quest-description-error' : undefined}
            onChange={event => setField('description', event.target.value)} />
          {fieldErrors.description && <p id="quest-description-error" className={styles.error}>{fieldErrors.description}</p>}
        </div>
        <div className={styles.row}>
          <label className={styles.field}>
            <span>Category</span>
            <select value={values.category} onChange={event => setField('category', event.target.value as QuestCategory | '')}>
              <option value="">Not specified</option>
              {(['ADVENTURE', 'CREATIVITY', 'FITNESS', 'LEARNING', 'FOOD', 'SOCIAL'] as QuestCategory[]).map(value =>
                <option key={value} value={value}>{value.charAt(0) + value.slice(1).toLowerCase()}</option>)}
            </select>
          </label>
          <label className={styles.field}>
            <span>Difficulty</span>
            <select value={values.difficulty} onChange={event => setField('difficulty', event.target.value as QuestDifficulty | '')}>
              <option value="">Not specified</option>
              {(['EASY', 'MEDIUM', 'HARD'] as QuestDifficulty[]).map(value =>
                <option key={value} value={value}>{value.charAt(0) + value.slice(1).toLowerCase()}</option>)}
            </select>
          </label>
          <Input label="Reward XP" type="number" min="1" max="1000" value={values.rewardXp}
            error={fieldErrors.rewardXp} placeholder="Optional" onChange={event => setField('rewardXp', event.target.value)} />
        </div>
      </Card>
      {saveError && <Card className={styles.alert} role="alert"><p>{saveError}</p></Card>}
      <div className={styles.actions}>
        <span aria-live="polite" className={styles.status}>
          {saving ? 'Saving…' : saved ? 'Saved' : dirty ? 'Unsaved changes' : 'No unsaved changes'}
        </span>
        <Button type="submit" loading={saving}>{mode === 'create' ? 'Save draft' : 'Save changes'}</Button>
      </div>
    </form>
  </section>
}
