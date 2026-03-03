/**
 * Project: Lethal Breed
 * Responsibility: Root Application Component
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
import { useState, useEffect } from 'react'
import { Sidebar } from './components/Sidebar/Sidebar'
import { MainContent } from './components/Layout/MainContent'

export default function App() {
  const [content, setContent] = useState('')
  const [page, setPage] = useState('presentation')
  const [isSidebarOpen, setIsSidebarOpen] = useState(false)

  useEffect(() => {
    fetch(`/content/${page}.md`)
      .then(res => res.text())
      .then(t => setContent(t))
  }, [page])

  return (
    <div className="flex min-h-screen">
      <Sidebar 
        currentPage={page} 
        setPage={setPage} 
        isOpen={isSidebarOpen} 
        onClose={() => setIsSidebarOpen(false)} 
      />
      <MainContent 
        content={content} 
        onMenuOpen={() => setIsSidebarOpen(true)} 
      />
    </div>
  )
}
