/**
 * Project: Lethal Breed
 * Responsibility: Root Application Component with I18n
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
import { useState, useEffect } from 'react'
import { Sidebar } from './components/Sidebar/Sidebar'
import { MainContent } from './components/Layout/MainContent'

export default function App() {
  const [content, setContent] = useState('')
  const [page, setPage] = useState('presentation')
  const [lang, setLang] = useState<'fr' | 'en'>('fr')
  const [isSidebarOpen, setIsSidebarOpen] = useState(false)

  useEffect(() => {
    const suffix = lang === 'en' ? '_en' : ''
    fetch(`/content/${page}${suffix}.md`)
      .then(res => res.text())
      .then(t => setContent(t))
      .catch(() => setContent('# Content not found'))
  }, [page, lang])

  return (
    <div className="flex min-h-screen bg-white">
      <Sidebar 
        currentPage={page} 
        setPage={setPage} 
        lang={lang}
        isOpen={isSidebarOpen} 
        onClose={() => setIsSidebarOpen(false)} 
      />
      <div className="flex-1 flex flex-col min-w-0">
        {/* Top Language Bar */}
        <div className="h-16 border-b border-zinc-100 flex items-center justify-end px-8 lg:px-16 gap-4 bg-white/50 backdrop-blur-md sticky top-0 z-40">
          <button 
            onClick={() => setLang(lang === 'fr' ? 'en' : 'fr')}
            className="px-4 py-1.5 border-2 border-zinc-900 text-zinc-900 text-[10px] font-black uppercase tracking-widest hover:bg-zinc-900 hover:text-white transition-all active:translate-y-0.5"
          >
            {lang === 'fr' ? 'Switch to English' : 'Passer en Français'}
          </button>
        </div>
        
        <MainContent 
          content={content} 
          onMenuOpen={() => setIsSidebarOpen(true)} 
        />
      </div>
    </div>
  )
}
