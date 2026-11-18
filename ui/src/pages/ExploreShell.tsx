import { Compass } from 'lucide-react'
import { Card } from '../components/ui/Card/Card'
import styles from './Pages.module.css'

export function ExploreShell() {
  return <div className={styles.page}>
    <section className={styles.hero} aria-labelledby="explore-title">
      <p className={styles.eyebrow}>A little adventure goes a long way</p>
      <h1 id="explore-title">Make today<br />a <span>quest.</span></h1>
      <p>Small challenges. Real momentum.<br />Find something fun and make it happen.</p>
      <a className={styles.action} href="#quest-board">Explore the quest board <span aria-hidden="true">↗</span></a>
    </section>
    <section id="quest-board" aria-labelledby="quest-board-title" className={styles.section}>
      <div><p className={styles.eyebrow}>Your next adventure</p><h2 id="quest-board-title">The quest board</h2></div>
      <Card className={styles.empty}>
        <span className={styles.emptyIcon}><Compass size={32} aria-hidden="true" /></span>
        <h3>Your next great story starts soon.</h3>
        <p>We’re getting the first quests ready. Check back shortly.</p>
      </Card>
    </section>
  </div>
}
