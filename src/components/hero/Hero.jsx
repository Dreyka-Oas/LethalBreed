import HeroLeft from './HeroLeft'
import HeroRight from './HeroRight'

export default function Hero() {
  return (
    <section className="hero" id="hero">
      <div className="hero__blobs" aria-hidden="true">
        <div className="hero__blob hero__blob--1" />
        <div className="hero__blob hero__blob--2" />
        <div className="hero__blob hero__blob--3" />
      </div>
      <div className="hero__grid-overlay" aria-hidden="true" />
      <div className="hero__content">
        <HeroLeft />
        <HeroRight />
      </div>
    </section>
  )
}
