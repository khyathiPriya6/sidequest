import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams, useBlocker, useBeforeUnload } from 'react-router-dom'
import { Modal } from '../components/ui/Modal/Modal'
import { beforeSignOutEvent, useAuth } from '../features/auth/AuthContext'
import { ApiError } from '../lib/api'
import { Button } from '../components/ui/Button/Button'
import { Card } from '../components/ui/Card/Card'
import { Input } from '../components/ui/Input/Input'
import { getCsrfToken } from '../features/auth/authApi'
import {
  createQuestDraft,
  getOwnedQuest,
  updateQuestDraft,
  publishQuest,
  QuestApiError,
  type QuestCategory,
  type QuestDifficulty,
  type QuestDraftInput,
} from '../features/quests/questApi'
import styles from './QuestDraftPage.module.css'
import { getOwnerReview, submitQuestReview, appealQuest, reviewLabel, type OwnerReviewStatus } from '../features/safety/safetyApi'

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
  const { state: authState } = useAuth()
  const busy = useRef(false)
  const allowNavigation = useRef(false)
  const [recovery, setRecovery] = useState(false)
  const [signOutAction, setSignOutAction] = useState<(() => void) | null>(null)
  const [reloadOpen, setReloadOpen] = useState(false)
  const [values, setValues] = useState<DraftValues>(emptyDraft)
  const [initialValues, setInitialValues] = useState<DraftValues>(emptyDraft)
  const [etag, setEtag] = useState<string | null>(null)
  const [loading, setLoading] = useState(mode === 'edit')
  const [saving, setSaving] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [saveError, setSaveError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [saved, setSaved] = useState(false)
  const [review, setReview] = useState<OwnerReviewStatus | null>(null)
  const [reviewError, setReviewError] = useState('')
  const [appealReason, setAppealReason] = useState('')
  const [loadAttempt, setLoadAttempt] = useState(0)

  useEffect(() => {
    if (mode !== 'edit') return
    const controller = new AbortController()
    setLoading(true)
    setEtag(null)
    setLoadError('')
    setReview(null)
    setReviewError('')
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
      void getOwnerReview(id, controller.signal).then(status => {
        if (!controller.signal.aborted) setReview(status)
      }).catch(error => {
        if (!controller.signal.aborted) setReviewError(error instanceof Error ? error.message : 'Unable to check review status.')
      })
      setRecovery(false)
      setLoading(false)
    }).catch(error => {
      if (controller.signal.aborted) return
      setLoadError(error instanceof Error ? error.message : 'Unable to load this draft.')
      setLoading(false)
    })
    return () => controller.abort()
  }, [id, mode, loadAttempt])

  const dirty = useMemo(() => JSON.stringify(values) !== JSON.stringify(initialValues), [values, initialValues])

  useEffect(() => {
    const guard = (event: Event) => {
      if (!dirty && !busy.current) return
      event.preventDefault()
      setSignOutAction(() => (event as CustomEvent<() => void>).detail)
    }
    window.addEventListener(beforeSignOutEvent, guard)
    return () => window.removeEventListener(beforeSignOutEvent, guard)
  }, [dirty])

  const blocker = useBlocker(() => dirty && !allowNavigation.current && authState.status === 'authenticated')
  useBeforeUnload(event => { if (dirty && !allowNavigation.current) { event.preventDefault(); event.returnValue = '' } })

  function handleFailure(error: unknown) {
    if (error instanceof ApiError && (error.status === 0 || error.status >= 500 || error.status === 412)) {
      setRecovery(true)
      setEtag(null)
    }
  }

  function setField<K extends keyof DraftValues>(key: K, value: DraftValues[K]) {
    setSaved(false)
    allowNavigation.current = false
    setValues(current => ({ ...current, [key]: value }))
  }

  function validate(): Record<string, string> {
    const errors: Record<string, string> = {}
    if ([...values.title.trim()].length < 3 || [...values.title.trim()].length > 120) {
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
    if (busy.current || recovery || (mode === 'edit' && !etag)) return
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

    busy.current = true
    setSaving(true)
    try {
      const csrfToken = (await getCsrfToken()).csrfToken
      const result = mode === 'create'
        ? await createQuestDraft(input, csrfToken)
        : await updateQuestDraft(id, input, etag!, csrfToken)
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
      if (mode === 'edit') {
        setReview({ state: 'UNREVIEWED', reasonCode: null, version: 0 })
        setReviewError('')
      }
      setFieldErrors({})
      if (mode === 'create') {
        allowNavigation.current = true
        navigate(`/quests/${encodeURIComponent(result.quest.id)}/edit`, { replace: true })
      }
    } catch (error) {
      handleFailure(error)
      if (error instanceof QuestApiError) {
        setSaveError(error.status === 412
          ? 'This draft changed in another tab or session. Reload it before saving again.'
          : error.status === 409
            ? 'The draft could not be changed in its current state.'
            : error.message)
        setFieldErrors(error.fieldErrors.reduce<Record<string, string>>((errors, item) => { errors[item.field] = [errors[item.field], item.message].filter(Boolean).join(' '); return errors }, {}))
      } else {
        setSaveError(error instanceof Error ? error.message : 'Unable to save this draft.')
      }
    } finally {
      busy.current = false
      setSaving(false)
    }
  }

  async function refreshReview() {
    if (mode !== 'edit' || busy.current || dirty || recovery) return
    busy.current = true
    setSaving(true)
    setReviewError('')
    try {
      setReview(await getOwnerReview(id))
    } catch (error) {
      setReview(null)
      setReviewError(error instanceof Error ? error.message : 'Unable to verify review status.')
    } finally {
      busy.current = false
      setSaving(false)
    }
  }

  async function requestReview() {
    if (mode !== 'edit' || busy.current || dirty || !etag || recovery || !review
      || !['UNREVIEWED', 'REJECTED'].includes(review.state)) return
    busy.current = true
    setSaving(true)
    setSaveError('')
    try {
      const status = await submitQuestReview(id, etag)
      setReview(status)
      setSaved(true)
    } catch (error) {
      handleFailure(error)
      setSaveError(error instanceof Error ? error.message : 'Unable to submit for review. Reload your quest before retrying.')
    } finally {
      busy.current = false
      setSaving(false)
    }
  }

  async function appeal() {
    if (mode !== 'edit' || busy.current || dirty || recovery || !review
      || review.state !== 'REJECTED' || appealReason.trim().length < 10 || appealReason.trim().length > 1000) return
    busy.current = true
    setSaving(true)
    setSaveError('')
    try {
      await appealQuest(id, review, appealReason)
      setAppealReason('')
      const updated = await getOwnerReview(id)
      setReview(updated)
    } catch (error) {
      handleFailure(error)
      setSaveError(error instanceof Error ? error.message : 'Unable to appeal. Reload your review status before retrying.')
      // Outcome may be uncertain. Explicitly reload rather than automatically repeating.
      try { setReview(await getOwnerReview(id)) } catch { setReview(null) }
    } finally {
      busy.current = false
      setSaving(false)
    }
  }

  async function publish() {
    if (mode !== 'edit' || busy.current || dirty || !etag || recovery || review?.state !== 'APPROVED') return
    busy.current = true
    setSaving(true)
    setSaveError('')
    try {
      const csrfToken = (await getCsrfToken()).csrfToken
      const result = await publishQuest(id, etag, csrfToken)
      setEtag(result.etag)
      setSaved(true)
      allowNavigation.current = true
      navigate(`/quests/${encodeURIComponent(result.quest.id)}`, { replace: true })
    } catch (error) {
      handleFailure(error)
      if (error instanceof QuestApiError) {
        setSaveError(error.status === 422
          ? 'This quest is not ready to publish. Complete the required fields below.'
          : error.status === 412
            ? 'This draft changed in another tab or session. Reload it before publishing.'
            : error.status === 409
              ? 'This quest cannot be published from its current state.'
              : error.message)
        setFieldErrors(error.fieldErrors.reduce<Record<string, string>>((errors, item) => { errors[item.field] = [errors[item.field], item.message].filter(Boolean).join(' '); return errors }, {}))
      } else {
        setSaveError(error instanceof Error ? error.message : 'Unable to publish this quest.')
      }
    } finally {
      busy.current = false
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
      <Card><fieldset className={styles.fields} disabled={saving} aria-label="Quest fields">
        <Input label="Title" aria-required="true" disabled={review?.state === 'UNDER_REVIEW'} value={values.title} maxLength={240} error={fieldErrors.title}
          hint="Required to save a draft; 3–120 Unicode characters."
          onChange={event => setField('title', event.target.value)} />
        <div className={styles.field}>
          <label htmlFor="quest-description">Description</label>
          <textarea id="quest-description" disabled={review?.state === 'UNDER_REVIEW'} value={values.description} maxLength={6000}
            aria-invalid={fieldErrors.description ? true : undefined}
            aria-describedby={fieldErrors.description ? 'quest-description-error' : undefined}
            onChange={event => setField('description', event.target.value)} />
          {fieldErrors.description && <p id="quest-description-error" className={styles.error}>{fieldErrors.description}</p>}
        </div>
        <div className={styles.row}>
          <div className={styles.field}>
            <label htmlFor="quest-category">Category</label>
            <select id="quest-category" disabled={review?.state === 'UNDER_REVIEW'} aria-invalid={!!fieldErrors.category} aria-describedby={fieldErrors.category ? "category-error" : undefined} value={values.category} onChange={event => setField('category', event.target.value as QuestCategory | '')}>
              <option value="">Not specified</option>
              {(['ADVENTURE', 'CREATIVITY', 'FITNESS', 'LEARNING', 'FOOD', 'SOCIAL'] as QuestCategory[]).map(value =>
                <option key={value} value={value}>{value.charAt(0) + value.slice(1).toLowerCase()}</option>)}
            </select>
            {fieldErrors.category && <span id="category-error" className={styles.error}>{fieldErrors.category}</span>}
          </div>
          <div className={styles.field}>
            <label htmlFor="quest-difficulty">Difficulty</label>
            <select id="quest-difficulty" disabled={review?.state === 'UNDER_REVIEW'} aria-invalid={!!fieldErrors.difficulty} aria-describedby={fieldErrors.difficulty ? "difficulty-error" : undefined} value={values.difficulty} onChange={event => setField('difficulty', event.target.value as QuestDifficulty | '')}>
              <option value="">Not specified</option>
              {(['EASY', 'MEDIUM', 'HARD'] as QuestDifficulty[]).map(value =>
                <option key={value} value={value}>{value.charAt(0) + value.slice(1).toLowerCase()}</option>)}
            </select>
            {fieldErrors.difficulty && <span id="difficulty-error" className={styles.error}>{fieldErrors.difficulty}</span>}
          </div>
          <Input label="Reward XP" disabled={review?.state === 'UNDER_REVIEW'} type="number" min="1" max="1000" value={values.rewardXp}
            error={fieldErrors.rewardXp} placeholder="Optional" onChange={event => setField('rewardXp', event.target.value)} />
        </div>
      </fieldset></Card>
      {saveError && <Card className={styles.alert} role="alert"><p>{saveError}</p></Card>}
      {mode === 'edit' && <Card className={styles.review} aria-label="Quest safety review">
        <h2>Safety review</h2>
        {reviewError && <p role="alert">Unable to load review status: {reviewError}. Reload before publishing.</p>}
        {!review && !reviewError && <p role="status">Checking review status…</p>}
        {review && <p role="status">{reviewLabel(review)}</p>}
        {review?.reasonCode && <p>Review category: {review.reasonCode.replaceAll('_', ' ').toLowerCase()}.</p>}
        {review?.state === 'PENDING_REVIEW' && <p>Your draft is private until a moderator approves it. You can still edit it, but editing resets the review.</p>}
        {review?.state === 'REJECTED' && <p>You may revise your Quest and submit it again, or explain why this rejection should be reconsidered.</p>}
        {review?.state === 'UNDER_REVIEW' && <p>Your appeal is pending. Editing is temporarily unavailable until a moderator decides.</p>}
        {review?.state === 'REJECTED' && !dirty && <div className={styles.appeal}>
          <label htmlFor="appeal-reason">Why should this rejection be reconsidered?</label>
          <textarea id="appeal-reason" minLength={10} maxLength={1000} value={appealReason}
            onChange={event => setAppealReason(event.target.value)} />
          <Button type="button" variant="secondary" loading={saving}
            disabled={saving || recovery || appealReason.trim().length < 10 || appealReason.trim().length > 1000}
            onClick={() => void appeal()}>Request appeal</Button>
        </div>}
        {review?.state === 'APPROVED' && <p>Approved. You may publish now. Editing this draft requires another review.</p>}
        {review?.state === 'HIDDEN' && <p>This quest is not publicly available. Contact moderation for a review before further action.</p>}
        {review && ['UNREVIEWED', 'REJECTED'].includes(review.state) &&
          <Button type="button" variant="secondary" loading={saving}
            disabled={dirty || recovery || !etag || saving}
            onClick={() => void requestReview()}>Submit for safety review</Button>}
        {review?.state === 'PENDING_REVIEW' && <Button type="button" variant="secondary"
          disabled={saving || dirty || recovery} onClick={() => void refreshReview()}>
          Check for review decision
        </Button>}
        {reviewError && <Button type="button" variant="secondary"
          disabled={saving || dirty || recovery} onClick={() => void refreshReview()}>
          Retry review status
        </Button>}
      </Card>}
      {dirty && mode === 'edit' && <p>Save your changes before submitting for review or publishing.</p>}
      {recovery && <Card><p>The result of this change must be checked before submitting again. Your entered values are preserved here.</p>
        {mode === 'edit' ? <Button type="button" onClick={() => setReloadOpen(true)}>Reload saved quest</Button>
          : <Link to="/my-quests">Check My quests before creating another draft</Link>}
      </Card>}
      <div className={styles.actions}>
        <span aria-live="polite" className={styles.status}>
          {saving ? 'Saving…' : saved ? 'Saved' : dirty ? 'Unsaved changes' : 'No unsaved changes'}
        </span>
        <div className={styles.actionButtons}>
          {mode === 'edit' && <Button variant="secondary" type="button" loading={saving}
            disabled={dirty || recovery || !etag || review?.state !== 'APPROVED'}
            onClick={() => void publish()}>Publish approved quest</Button>}
          <Button type="submit" loading={saving} disabled={recovery || review?.state === 'UNDER_REVIEW' || (mode === 'edit' && !etag)}>{mode === 'create' ? 'Save draft' : 'Save changes'}</Button>
        </div>
      </div>
    </form>
    <Modal open={blocker.state === 'blocked'} title="Discard unsaved changes?" description="Your latest changes have not been saved."
      onOpenChange={() => blocker.state === 'blocked' && blocker.reset()}>
      <Button onClick={() => blocker.state === 'blocked' && blocker.reset()}>Keep editing</Button>
      <Button variant="destructive" onClick={() => blocker.state === 'blocked' && blocker.proceed()}>Discard and leave</Button>
    </Modal>
    <Modal open={!!signOutAction} title="Discard changes and sign out?" description="Unsaved changes will be lost if you sign out."
      onOpenChange={() => setSignOutAction(null)}>
      <Button onClick={() => setSignOutAction(null)}>Keep editing</Button>
      <Button disabled={saving} variant="destructive" onClick={() => { allowNavigation.current = true; signOutAction?.(); setSignOutAction(null) }}>Discard and sign out</Button>
    </Modal>
    <Modal open={reloadOpen} title="Reload saved quest?" description="This replaces your entered values with the server's saved version."
      onOpenChange={setReloadOpen}>
      <Button onClick={() => setReloadOpen(false)}>Keep editing</Button>
      <Button onClick={() => { setReloadOpen(false); setSaveError(''); setLoadAttempt(value => value + 1) }}>Reload and discard local changes</Button>
    </Modal>
  </section>
}
