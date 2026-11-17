import { Link } from 'react-router-dom'
import { Card } from '../components/ui/Card/Card'
import styles from './Pages.module.css'

export function FeaturePlaceholder({ title, description }: { title: string; description: string }) {
  return <div className={styles.page}><h1>{title}</h1><Card className={styles.section}>
    <h2>Coming soon</h2><p>{description}</p><Link className={styles.textLink} to="/quests">Back to Explore</Link>
  </Card></div>
}
