import NavBar from './components/layout/NavBar'
import Hero from './components/hero/Hero'
import Features from './components/sections/Features'
import Config from './components/config/Config'
import FAQ from './components/sections/FAQ'
import Footer from './components/layout/Footer'
import SectionDivider from './components/layout/SectionDivider'

export default function App() {
  return (
    <div className="app">
      <NavBar />
      <main>
        <Hero />
        <SectionDivider />
        <Features />
        <SectionDivider />
        <Config />
        <SectionDivider />
        <FAQ />
      </main>
      <Footer />
    </div>
  )
}
