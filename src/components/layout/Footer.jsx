import { useTranslation } from '../../hooks/useTranslation'

export default function Footer() {
  const { t } = useTranslation()
  return (
    <footer className="footer">
      <div className="container">
        <div className="footer__inner">
          <div className="footer__logo"><span className="footer__logo-icon">🧬</span> LethalBreed</div>
          <div className="footer__links">
            <a href="#hero">{t('nav.hero')}</a>
            <a href="#features">{t('nav.features')}</a>
            <a href="#config">{t('nav.config')}</a>
            <a href="#faq">{t('nav.faq')}</a>
          </div>
          <p className="footer__copy">© 2024 LethalBreed Mod. Open Source.</p>
        </div>
      </div>
    </footer>
  )
}
