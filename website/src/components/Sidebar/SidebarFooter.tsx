/**
 * Project: Lethal Breed
 * Responsibility: Sidebar Footer with Social Links
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
import { Github } from 'lucide-react'

export const SidebarFooter = () => (
  <div className="pt-6 border-t-2 border-slate-100 flex justify-center gap-6">
    <a 
      href="https://github.com" 
      target="_blank" 
      rel="noreferrer"
      className="text-slate-400 hover:text-slate-900 transition-colors"
    >
      <Github size={20} />
    </a>
  </div>
)
