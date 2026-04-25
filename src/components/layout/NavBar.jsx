import { useState, useEffect } from 'react'
import { useTranslation } from '../../hooks/useTranslation'

export default function NavBar() {
  const { t } = useTranslation()
  const [scrolled, setScrolled] = useState(false)

  useEffect(() => {
    const handleScroll = () => setScrolled(window.scrollY > 20)
    window.addEventListener('scroll', handleScroll)
    return () => window.removeEventListener('scroll', handleScroll)
  }, [])

  const links = [
    { id: 'hero', label: t('nav.hero') },
    { id: 'features', label: t('nav.features') },
    { id: 'config', label: t('nav.config') },
    { id: 'faq', label: t('nav.faq') },
  ]

  return (
    <header className={`navbar${scrolled ? ' navbar--scrolled' : ''}`}>
      <div className="navbar__inner">
        <a href="#hero" className="navbar__logo">
          <span className="navbar__logo-icon">🧬</span>
          <span><span className="navbar__logo-accent">Lethal</span>Breed</span>
        </a>
        <nav className="navbar__links">
          {links.map((link) => <a key={link.id} href={`#${link.id}`}>{link.label}</a>)}
        </nav>
      </div>
    </header>
  )
}
