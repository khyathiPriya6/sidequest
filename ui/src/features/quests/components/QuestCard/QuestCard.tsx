import { useId } from 'react'
import { Link } from 'react-router-dom'
import { ArrowUpRight } from 'lucide-react'
import { Badge } from '../../../../components/ui/Badge/Badge'
import { Card } from '../../../../components/ui/Card/Card'
import styles from './QuestCard.module.css'

export type QuestCardProps = {
  title: string
  description: string
  category: string
  difficulty: 'Easy' | 'Medium' | 'Hard'
  rewardXp: number
  href: string
  artwork?: { src: string; alt: string }
}

export function QuestCard({ title, description, category, difficulty, rewardXp, href, artwork }: QuestCardProps) {
  const headingId = useId()
  const validReward = Number.isFinite(rewardXp) && rewardXp >= 0
  return <article aria-labelledby={headingId}>
    <Card className={styles.card}>
      {artwork && <img className={styles.artwork} src={artwork.src} alt={artwork.alt} />}
      <div className={styles.badges}><Badge>{category || 'Uncategorized'}</Badge><Badge tone="accent">{difficulty}</Badge></div>
      <h3 id={headingId}><Link className={styles.link} to={href}>{title || 'Untitled quest'}<ArrowUpRight size={20} aria-hidden="true" /></Link></h3>
      <p>{description || 'No description available.'}</p>
      <div className={styles.reward}><Badge tone="reward">{validReward ? `${rewardXp} XP reward` : 'Reward unavailable'}</Badge></div>
    </Card>
  </article>
}
