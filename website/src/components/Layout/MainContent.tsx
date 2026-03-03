/**
 * Project: Lethal Breed
 * Responsibility: Main Documentation Content Display
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
import ReactMarkdown from 'react-markdown'
import { Menu } from 'lucide-react'
import { StatusHeader } from './StatusHeader'
import { StatsPanel } from './StatsPanel'
import { DownloadSection } from './DownloadSection'

interface Props {
  content: string;
  onMenuOpen: () => void;
}

export const MainContent = ({ content, onMenuOpen }: Props) => (
  <main className="flex-1 min-w-0 bg-ui-bg relative overflow-y-auto">
    <div className="max-w-[1400px] mx-auto p-8 lg:p-16">
      {/* Mobile Header */}
      <button 
        onClick={onMenuOpen}
        className="lg:hidden mb-12 flex items-center gap-2 text-ui-muted font-bold uppercase tracking-widest text-xs"
      >
        <Menu size={20} /> Menu
      </button>

      <StatusHeader />

      <div className="grid grid-cols-1 xl:grid-cols-[1fr_400px] gap-20">
        <article className="prose prose-slate max-w-none">
          <ReactMarkdown>{content}</ReactMarkdown>
        </article>

        <aside className="space-y-12">
          <StatsPanel />
          <DownloadSection />
        </aside>
      </div>
    </div>
  </main>
)
