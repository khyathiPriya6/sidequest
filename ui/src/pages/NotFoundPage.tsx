import { Link } from 'react-router-dom'
import styles from './Pages.module.css'

export function NotFoundPage() {
  return <div className={styles.page}><p className={styles.eyebrow}>404 · Off the map</p><h1>That page isn’t here.</h1>
    <p>Head back to Explore to find your way.</p><Link className={styles.textLink} to="/quests">Back to Explore</Link></div>
}
