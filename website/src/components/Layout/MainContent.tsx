/**
 * Project: Lethal Breed
 * Responsibility: Main Content Layout and Markdown Rendering
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
import ReactMarkdown from 'react-markdown'
import { InfoBox } from './InfoBox'
import { StatsPanel } from './StatsPanel'
import { StatusHeader } from './StatusHeader'

interface Props { content: string; onMenuOpen: () => void }

export const MainContent = ({ content, onMenuOpen }: Props) => (
  <main className="flex-1 overflow-x-hidden p-6 md:p-10 lg:p-16 h-screen overflow-y-auto">
    <div className="w-full">
      <StatusHeader onMenuOpen={onMenuOpen} />
      <div className="content-grid items-start">
        <div className="bg-white border border-slate-100 p-8 md:p-12 lg:p-16 shadow-soft-xl rounded-2xl transition-all hover:shadow-soft-md">
          <article className="prose prose-slate max-w-none">
            <ReactMarkdown>{content}</ReactMarkdown>
          </article>
        </div>
        <div className="space-y-8 pb-10">
          <InfoBox />
          <StatsPanel />
        </div>
      </div>
    </div>
  </main>
)
