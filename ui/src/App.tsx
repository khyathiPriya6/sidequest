import HealthStatus from './HealthStatus'

export default function App() {
  return (
    <main className="page-shell">
      <header className="topbar"><a className="brand" href="/" aria-label="SideQuest home"><span className="brand-mark">S</span> sidequest</a><span className="topbar-label">YOUR NEXT ADVENTURE STARTS HERE</span><button className="profile-button" aria-label="Profile">✦</button></header>
      <section className="hero" aria-labelledby="hero-title">
        <div className="hero-copy"><div className="eyebrow"><span className="eyebrow-dot" /> A LITTLE ADVENTURE GOES A LONG WAY</div><h1 id="hero-title">Make today<br />a <span>quest.</span></h1><p>Small challenges. Real momentum.<br />Find something fun and make it happen.</p><button className="primary-button" onClick={() => document.getElementById('discover')?.scrollIntoView({ behavior: 'smooth' })}>Explore quests <span aria-hidden="true">↗</span></button><div className="hero-footnote"><span>✳</span> YOUR STORY, ONE QUEST AT A TIME</div></div>
        <div className="hero-art" aria-hidden="true"><div className="sun-glow" /><div className="orbit orbit-one" /><div className="orbit orbit-two" /><div className="quest-token"><span>✦</span><small>GO<br />YOUR<br />OWN WAY</small></div><div className="spark spark-one">✳</div><div className="spark spark-two">✦</div><div className="art-caption">A NEW CHAPTER<br />IS WAITING</div></div>
      </section>
      <section className="discover" id="discover" aria-labelledby="discover-title"><div className="section-heading"><div><div className="section-kicker">PICK YOUR FIRST ADVENTURE</div><h2 id="discover-title">The world is your <span>playground.</span></h2></div><span className="coming-soon">QUESTS ARE ON THE WAY <span>↗</span></span></div><div className="empty-state"><div className="empty-icon">✳</div><div><h3>Your next great story starts soon.</h3><p>We’re getting the first quests ready. Check back shortly.</p></div><HealthStatus /></div></section>
      <footer><span>© SIDEQUEST</span><span>MADE FOR THE PLOT ✳</span></footer>
    </main>
  )
}
