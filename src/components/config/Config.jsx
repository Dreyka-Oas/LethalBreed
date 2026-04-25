import { useState } from 'react'
import { useInView } from '../../hooks/useInView'
import { useTranslation } from '../../hooks/useTranslation'
import { CATEGORIES } from './categories'
import ConfigTable from './ConfigTable'
import JsonHighlight from './JsonHighlight'

export default function Config() {
  const { t } = useTranslation()
  const [active, setActive] = useState('attributes')
  const [tab, setTab] = useState('table')
  const [headerRef, headerVisible] = useInView()
  const current = CATEGORIES.find((c) => c.id === active)

  return (
    <section className="section section--alt" id="config">
      <div className="container">
        <div ref={headerRef} className={`section__header reveal${headerVisible ? ' is-visible' : ''}`}>
          <div className="section__label">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.07 4.93a10 10 0 010 14.14M4.93 4.93a10 10 0 000 14.14"/></svg>
            {t('config.label')}
          </div>
          <h2 className="section__title">{t('config.title')}</h2>
          <div className="accent-line" />
          <p className="section__subtitle">
            {t('config.subtitle_1')} <code style={{ background:'rgba(0,0,0,0.06)', padding:'1px 6px', borderRadius:3, fontFamily:'var(--mono)', fontSize:13 }}>config/o.a.s/lethalbreed.json</code>.
            {t('config.subtitle_2')} <code style={{ background:'rgba(0,0,0,0.06)', padding:'1px 6px', borderRadius:3, fontFamily:'var(--mono)', fontSize:13 }}>/lethalbreed reload</code>.
          </p>
          <div className="config-note config-note--warn" style={{ marginTop: 20, maxWidth: 600 }}>
            {t('config.compat_warning')}
          </div>
        </div>

        <div className="config-layout">
          <nav className="config-nav" aria-label="Config categories">
            {CATEGORIES.map((cat) => (
              <button key={cat.id} className={`config-nav__item${active === cat.id ? ' active' : ''}`} onClick={() => { setActive(cat.id); setTab('table') }}>
                <span className="config-nav__icon">{cat.icon}</span>
                {t(`config.categories.${cat.id}.label`)}
                <span className="config-nav__dot" style={{ marginLeft: 'auto' }} />
              </button>
            ))}
          </nav>

          <div className="config-panel">
            <div className="config-panel__header">
              <div className="config-panel__title"><span style={{ fontSize: 20 }}>{current.icon}</span> {t(`config.categories.${current.id}.label`)}</div>
              <div className="config-panel__tabs">
                <button className={`config-tab${tab === 'table' ? ' active' : ''}`} onClick={() => setTab('table')}>{t('config.tabs.options')}</button>
                <button className={`config-tab${tab === 'json' ? ' active' : ''}`} onClick={() => setTab('json')}>{t('config.tabs.json')}</button>
              </div>
            </div>
            <div className="config-panel__body">
              <p className="config-panel__desc">{t(`config.categories.${current.id}.desc`)}</p>
              {tab === 'table' ? <ConfigTable categoryId={current.id} options={current.options} /> : <JsonHighlight code={current.json} />}
              {active === 'ai' && <div className="config-note"><span>🔊</span><span><strong>{t('config.note_sounds')}</strong></span></div>}
              {active === 'mutant' && <div className="config-note config-note--warn"><span>⚠️</span><span><strong>{t('config.note_performance')}</strong></span></div>}
              {active === 'attributes' && <div className="config-note"><span>💡</span><span>{t('config.note_scale')}</span></div>}
            </div>
          </div>
        </div>

        <div style={{ marginTop: 40, display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
          <div>
            <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 6 }}>{t('config.reload_title')}</div>
            <div className="reload-cmd"><span style={{ color: '#6c7086' }}>/</span>lethalbreed reload</div>
          </div>
          <p style={{ fontSize: 13, color: 'var(--c-text-3)', maxWidth: 380, lineHeight: 1.6 }}>{t('config.reload_desc')}</p>
        </div>
      </div>
    </section>
  )
}
