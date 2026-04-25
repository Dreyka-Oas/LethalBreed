import { useState } from 'react'
import { useInView } from '../../hooks/useInView'
import { useTranslation } from '../../hooks/useTranslation'

function FAQItem({ question, answer }) {
  const [open, setOpen] = useState(false)
  return (
    <div className={`faq-item${open ? ' open' : ''}`}>
      <button className="faq-q" onClick={() => setOpen(!open)}>
        {question}
        <span className="faq-q__icon">+</span>
      </button>
      <div className="faq-a-wrap" style={{ height: open ? 'auto' : 0 }}>
        <div className="faq-a">{answer}</div>
      </div>
    </div>
  )
}

export default function FAQ() {
  const { t } = useTranslation()
  const [ref, visible] = useInView()
  const items = ['q1', 'q2', 'q3']

  return (
    <section className="section section--alt" id="faq">
      <div className="container">
        <div ref={ref} className={`section__header reveal${visible ? ' is-visible' : ''}`}>
          <div className="section__label">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 015.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
            {t('faq.label')}
          </div>
          <h2 className="section__title">{t('faq.title')}</h2>
          <div className="accent-line" />
        </div>
        <div className="faq-list">
          {items.map((id) => (
            <FAQItem key={id} question={t(`faq.items.${id}.q`)} answer={t(`faq.items.${id}.a`)} />
          ))}
        </div>
      </div>
    </section>
  )
}
