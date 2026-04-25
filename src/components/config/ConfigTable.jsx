import { useState, useRef, useEffect } from 'react'
import { usePretext } from '../../hooks/usePretext'
import { useTranslation } from '../../hooks/useTranslation'

export default function ConfigTable({ categoryId, options }) {
  const { t } = useTranslation()
  const tableRef = useRef(null)
  const [containerWidth, setContainerWidth] = useState(300)

  useEffect(() => {
    if (!tableRef.current) return
    const ro = new ResizeObserver(([entry]) => {
      setContainerWidth(Math.floor(entry.contentRect.width * 0.38))
    })
    ro.observe(tableRef.current)
    return () => ro.disconnect()
  }, [])

  const descTexts = options.map((o) => t(`config.categories.${categoryId}.opt.${o.key.replace('.', '_')}`))
  const { heights } = usePretext(descTexts, containerWidth)

  return (
    <div ref={tableRef} className="win-table-wrap" style={{ marginTop: 16 }}>
      <table className="win-table">
        <thead>
          <tr>
            <th>{t('config.table.option')}</th>
            <th>{t('config.table.type')}</th>
            <th>{t('config.table.default')}</th>
            <th>{t('config.table.range')}</th>
            <th>{t('config.table.description')}</th>
          </tr>
        </thead>
        <tbody>
          {options.map((opt, i) => (
            <tr key={opt.key}>
              <td><code>{opt.key}</code></td>
              <td>
                <span className={`badge badge--${
                  opt.type === 'Double' || opt.type === 'Float' ? 'accent'
                  : opt.type === 'Int' ? 'success'
                  : 'neutral'
                }`} style={{ fontSize: 11 }}>{opt.type}</span>
              </td>
              <td><code>{opt.def}</code></td>
              <td style={{ whiteSpace: 'nowrap', color: 'var(--c-text-3)' }}>
                {opt.min} – {opt.max}
              </td>
              <td style={{ minHeight: heights[i] ? `${heights[i]}px` : 'auto', transition: 'min-height 0.2s var(--ease-fluent)' }}>
                {descTexts[i]}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
