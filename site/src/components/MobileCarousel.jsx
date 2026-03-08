import { useEffect, useState, useRef } from 'react'

export default function MobileCarousel({ className = '', trackClassName = '', compact = false, children }) {
  const trackRef = useRef(null)
  const [canScrollPrev, setCanScrollPrev] = useState(false)
  const [canScrollNext, setCanScrollNext] = useState(false)

  useEffect(() => {
    const track = trackRef.current
    if (!track) return

    const updateScrollState = () => {
      const maxScrollLeft = track.scrollWidth - track.clientWidth
      setCanScrollPrev(track.scrollLeft > 4)
      setCanScrollNext(maxScrollLeft - track.scrollLeft > 4)
    }

    updateScrollState()
    track.addEventListener('scroll', updateScrollState, { passive: true })
    window.addEventListener('resize', updateScrollState)

    const resizeObserver = typeof ResizeObserver !== 'undefined'
      ? new ResizeObserver(updateScrollState)
      : null

    resizeObserver?.observe(track)
    Array.from(track.children).forEach((child) => resizeObserver?.observe(child))

    return () => {
      track.removeEventListener('scroll', updateScrollState)
      window.removeEventListener('resize', updateScrollState)
      resizeObserver?.disconnect()
    }
  }, [children])

  const scrollByCard = (direction) => {
    const track = trackRef.current
    if (!track) return

    const firstChild = track.firstElementChild
    const gap = parseFloat(getComputedStyle(track).gap || getComputedStyle(track).columnGap || '0')
    const cardWidth = firstChild ? firstChild.getBoundingClientRect().width : track.getBoundingClientRect().width
    track.scrollBy({
      left: direction * (cardWidth + gap),
      behavior: 'smooth'
    })
  }

  return (
    <div className={`nm-mobile-carousel ${compact ? 'is-compact' : ''} ${className}`.trim()}>
      {canScrollPrev ? (
        <button
          type="button"
          className="nm-mobile-carousel-nav nm-mobile-carousel-prev"
          aria-label="Previous"
          onClick={() => scrollByCard(-1)}
        >
          &lt;
        </button>
      ) : null}
      <div ref={trackRef} className={`nm-mobile-carousel-track ${trackClassName}`.trim()}>
        {children}
      </div>
      {canScrollNext ? (
        <button
          type="button"
          className="nm-mobile-carousel-nav nm-mobile-carousel-next"
          aria-label="Next"
          onClick={() => scrollByCard(1)}
        >
          &gt;
        </button>
      ) : null}
    </div>
  )
}
