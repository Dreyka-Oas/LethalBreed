import { useInView } from '../../hooks/useInView'
import { useTranslation } from '../../hooks/useTranslation'

export default function Features() {
  const { t } = useTranslation()
  const [ref, visible] = useInView()
  const items = [
    { id: 'ai', icon: '🧠', bg: 'rgba(0,120,212,0.08)', border: 'rgba(0,120,212,0.12)' },
    { id: 'mutant', icon: '👹', bg: 'rgba(196,43,28,0.08)', border: 'rgba(196,43,28,0.12)' },
    { id: 'movement', icon: '🧗', bg: 'rgba(16,124,16,0.08)', border: 'rgba(16,124,16,0.12)' },
    { id: 'kamikaze', icon: '💣', bg: 'rgba(202,80,16,0.08)', border: 'rgba(202,80,16,0.12)' },
    { id: 'panic', icon: '😱', bg: 'rgba(100,60,180,0.08)', border: 'rgba(100,60,180,0.12)' },
    { id: 'breaking', icon: '🧱', bg: 'rgba(120,80,40,0.08)', border: 'rgba(120,80,40,0.12)' },
  ]

  return (
    <section className="section" id="features">
      <div className="container">
        <div ref={ref} className={`section__header reveal${visible ? ' is-visible' : ''}`}>
          <div className="section__label">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>
            {t('features.label')}
          </div>
          <h2 className="section__title">{t('features.title')}</h2>
          <div className="accent-line" />
        </div>
        <div className="features-grid">
          {items.map((item) => (
            <div key={item.id} className="win-card feature-card">
              <div className="feature-card__icon" style={{ '--icon-bg': item.bg, '--icon-border': item.border }}>{item.icon}</div>
              <h3 className="feature-card__title">{t(`features.items.${item.id}.title`)}</h3>
              <p className="feature-card__desc">{t(`features.items.${item.id}.desc`)}</p>
              {t(`features.items.${item.id}.tag`) && <span className="feature-card__tag">{t(`features.items.${item.id}.tag`)}</span>}
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
