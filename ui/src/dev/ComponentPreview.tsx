import { useState } from 'react'
import { AppShell } from '../app/layouts/AppShell'
import { Button } from '../components/ui/Button/Button'
import { Input } from '../components/ui/Input/Input'
import { Card } from '../components/ui/Card/Card'
import { Badge } from '../components/ui/Badge/Badge'
import { Modal } from '../components/ui/Modal/Modal'
import { XPProgressBar } from '../features/progress/components/XPProgressBar/XPProgressBar'
import { QuestCard } from '../features/quests/components/QuestCard/QuestCard'
import styles from './ComponentPreview.module.css'

export function ComponentPreview() {
  const [modal, setModal] = useState(false)
  const [name, setName] = useState('')
  const [member, setMember] = useState(false)
  return <AppShell mode={member ? 'member-preview' : 'guest'}>
    <div className={styles.page}>
      <div className={styles.section}>
        <Badge tone="reward">Development preview · Sample data only</Badge>
        <h1>Foundation components</h1>
        <p>Review controls and layouts here. These examples do not create quests, sign in, or award XP.</p>
        <Button variant="secondary" aria-pressed={member} onClick={() => setMember(value => !value)}>Preview member navigation</Button>
      </div>
      <section className={styles.section} aria-labelledby="buttons-title"><h2 id="buttons-title">Buttons</h2>
        <div className={styles.row}>{(['primary', 'secondary', 'ghost', 'destructive'] as const).map(variant => <Button key={variant} variant={variant}>{variant}</Button>)}</div>
        <div className={styles.row}><Button disabled>Disabled</Button><Button loading>Saving sample</Button><Button size="small">Small button</Button></div>
      </section>
      <section className={styles.section} aria-labelledby="fields-title"><h2 id="fields-title">Inputs</h2>
        <div className={styles.grid}>
          <Input label="Sample title" value={name} onChange={event => setName(event.target.value)} hint="Preview only; nothing is saved." required />
          <Input label="Invalid sample" error="Enter a title to continue." defaultValue="" />
          <Input label="Disabled sample" disabled value="Not editable" />
        </div>
      </section>
      <section className={styles.section} aria-labelledby="badges-title"><h2 id="badges-title">Badges and card</h2>
        <Card><div className={styles.row}><Badge>Learning</Badge><Badge tone="accent">Easy</Badge><Badge tone="reward">50 XP reward</Badge><Badge tone="success">Completed</Badge><Badge tone="danger">Unavailable</Badge></div></Card>
      </section>
      <section className={styles.section} aria-labelledby="progress-title"><h2 id="progress-title">Earned progress</h2>
        <Card><XPProgressBar earned={125} target={300} label="Sample earned XP" /></Card>
        <Card><XPProgressBar earned={0} target={0} label="Unavailable progress example" /></Card>
      </section>
      <section className={styles.section} aria-labelledby="quests-title"><h2 id="quests-title">Quest cards — sample content</h2>
        <div className={styles.grid}>
          <QuestCard title="Sample: a creative afternoon" description="Try a small creative challenge. This is a component fixture, not an available quest." category="Creativity" difficulty="Easy" rewardXp={50} href="/quests/sample" />
          <QuestCard title="Sample: a longer title to review wrapping on a narrow screen" description="A longer example helps check the height and alignment of cards without relying on live quest data." category="Learning" difficulty="Medium" rewardXp={100} href="/quests/sample-long" />
          <QuestCard title="" description="" category="" difficulty="Hard" rewardXp={Number.NaN} href="/quests/sample-missing" />
        </div>
      </section>
      <section className={styles.section} aria-labelledby="modal-title"><h2 id="modal-title">Modal</h2>
        <Button onClick={() => setModal(true)}>Open sample dialog</Button>
        <Modal open={modal} onOpenChange={setModal} title="Review a sample dialog" description="This dialog only demonstrates focus and dismissal. Nothing is published.">
          <Input label="Sample note" placeholder="Try keyboard navigation" />
          <Button variant="secondary" onClick={() => setModal(false)}>Done</Button>
        </Modal>
      </section>
    </div>
  </AppShell>
}
