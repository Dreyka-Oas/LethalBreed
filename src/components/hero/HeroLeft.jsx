import { useTranslation } from '../../hooks/useTranslation'

export default function HeroLeft() {
  const { t } = useTranslation()
  return (
    <div className="hero__left">
      <h1 className="hero__title"><span className="hero__title-accent">Lethal</span>Breed</h1>
      <p className="hero__tagline">{t('hero.tagline_1')}<br /><em>{t('hero.tagline_2')}</em></p>
      <div className="hero__actions">
        <a href="https://modrinth.com/mod/lethalbreed" className="btn btn--modrinth btn--lg" target="_blank" rel="noreferrer">
          <img 
            src="https://dl.flathub.org/media/com/modrinth/ModrinthApp/4aeed8cf001818b98023e0507cf6ed00/icons/128x128@2/com.modrinth.ModrinthApp.png" 
            width="20" height="20" alt="" 
            style={{ objectFit: 'contain' }} 
          />
          {t('hero.modrinth')}
        </a>
        <a href="https://www.curseforge.com/minecraft/mc-mods/lethal-breed" className="btn btn--curseforge btn--lg" target="_blank" rel="noreferrer">
          <img 
            src="https://assets.streamlinehq.com/image/private/w_300,h_300,ar_1/f_auto/v1/icons/logos/curseforge-1ggrlxplc9gjkajypnzcdh.png/curseforge-vqyvsa5do1rsh8njqkqpam.png?_a=DATAiZAAZAA0" 
            width="20" height="20" alt="" 
            style={{ objectFit: 'contain' }} 
          />
          {t('hero.curseforge')}
        </a>
      </div>
      <div className="hero__stats">
        <div className="hero__stat"><span className="hero__stat-num">25+</span><span className="hero__stat-label">{t('hero.stats.versions')}</span></div>
        <div className="hero__stat-divider" />
        <div className="hero__stat"><span className="hero__stat-num">7</span><span className="hero__stat-label">{t('hero.stats.config')}</span></div>
        <div className="hero__stat-divider" />
        <div className="hero__stat"><span className="hero__stat-num">100%</span><span className="hero__stat-label">{t('hero.stats.multiplayer')}</span></div>
      </div>
    </div>
  )
}
