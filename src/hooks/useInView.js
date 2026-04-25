import { useEffect, useRef, useState } from 'react'

/**
 * useInView — triggers when an element enters the viewport
 * @param {number} threshold  — percentage visible (0–1)
 * @param {string} rootMargin — CSS margin string
 */
export function useInView(threshold = 0.15, rootMargin = '0px') {
  const ref = useRef(null)
  const [isVisible, setIsVisible] = useState(false)

  useEffect(() => {
    const el = ref.current
    if (!el) return

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsVisible(true)
          observer.disconnect()
        }
      },
      { threshold, rootMargin }
    )

    observer.observe(el)
    return () => observer.disconnect()
  }, [threshold, rootMargin])

  return [ref, isVisible]
}
