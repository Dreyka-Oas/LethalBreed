import { useTranslation } from '../../hooks/useTranslation'

export default function HeroRight() {
  const { t } = useTranslation()
  const pills = ['ai', 'hearing', 'climbing', 'bosses', 'kamikazes', 'panic', 'loot', 'blocks']

  return (
    <div className="hero__right">
      <div className="hero__card">
        <div className="hero__card-tag"><span style={{ color: 'var(--c-success)', fontSize: 9 }}>●</span>{t('hero.active')}</div>
        
        <div className="hero__logo-display">
          <img src="/logo.png" alt="LethalBreed Logo" width="128" height="128" />
        </div>

        <div className="hero__card-title">{t('hero.card_title')}</div>
        <div className="hero__card-sub">{t('hero.card_sub')}</div>
        <div className="hero__pill-row">
          {pills.map((p) => <span key={p} className="hero__pill">{t(`hero.pills.${p}`)}</span>)}
        </div>
      </div>
    </div>
  )
}
