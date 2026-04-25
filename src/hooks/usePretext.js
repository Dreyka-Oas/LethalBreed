import { useMemo, useCallback } from 'react'
import { prepare, layout } from '@chenglou/pretext'

const FONT = '14px "Plus Jakarta Sans", "Segoe UI Variable Display", system-ui'
const LINE_HEIGHT = 22 // px

/**
 * usePretext — wraps @chenglou/pretext to pre-compute text layout metrics.
 *
 * Benefits vs. DOM measurement:
 *  - Zero layout reflow (no getBoundingClientRect)
 *  - Exact pixel heights before mount → enables smooth CSS height transitions
 *  - ~500× faster for large lists
 *
 * @param {string[]} texts        — array of description strings to measure
 * @param {number}   containerWidth — available width in px (e.g. from ResizeObserver)
 * @returns {{ heights: number[], measureOne: function }}
 */
export function usePretext(texts, containerWidth) {
  // Pre-compute heights for all provided texts in one pass
  const heights = useMemo(() => {
    if (!containerWidth || !texts?.length) return []

    return texts.map((text) => {
      if (!text) return LINE_HEIGHT
      try {
        const prepared = prepare(text, FONT)
        const result = layout(prepared, containerWidth, LINE_HEIGHT)
        return result.height + 10 // +10px padding buffer
      } catch {
        // Graceful fallback: approximate 1 line
        return LINE_HEIGHT + 10
      }
    })
  }, [texts, containerWidth])

  // On-demand measurement for a single string
  const measureOne = useCallback(
    (text, width = containerWidth) => {
      if (!text || !width) return LINE_HEIGHT
      try {
        const prepared = prepare(text, FONT)
        const result = layout(prepared, width, LINE_HEIGHT)
        return result.height
      } catch {
        return LINE_HEIGHT
      }
    },
    [containerWidth]
  )

  return { heights, measureOne }
}
