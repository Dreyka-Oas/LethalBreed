/**
 * Project: Lethal Breed
 * Responsibility: Navigation Breadcrumbs and Status Header
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
import { ChevronRight, Menu } from 'lucide-react'

interface Props {
  onMenuOpen: () => void;
}

export const StatusHeader = ({ onMenuOpen }: Props) => (
  <div className="flex items-center justify-between mb-8 bg-white p-4 border border-slate-100 shadow-soft-sm rounded-2xl">
    <div className="flex items-center gap-4">
      <button
        onClick={onMenuOpen}
        className="lg:hidden p-2 text-slate-500 hover:text-slate-900 hover:bg-slate-50 rounded-xl transition-colors"
      >
        <Menu size={20} />
      </button>
      <div className="flex items-center gap-2 text-xs font-semibold text-slate-500 uppercase tracking-wider bg-slate-50 border border-slate-100 px-3 py-1.5 rounded-lg">
        <img src="/logo.png" alt="" className="w-5 h-5 rounded" />
        <span className="text-mc-dark-green bg-emerald-50 px-2 py-0.5 rounded-md">Core</span>
        <span className="text-slate-300">/</span>
        <span className="text-slate-700">Menu</span>
      </div>
    </div>
    <div className="flex items-center gap-3 border border-slate-100 px-4 py-2 rounded-xl bg-slate-50">
      <span className="text-xs font-bold text-slate-700 uppercase tracking-wide flex items-center gap-2">SYS_OK
        <div className="h-2 w-2 bg-mc-green rounded-full shadow-[0_0_8px_rgba(16,185,129,0.5)] animate-pulse" />
      </span>
    </div>
  </div>
)
