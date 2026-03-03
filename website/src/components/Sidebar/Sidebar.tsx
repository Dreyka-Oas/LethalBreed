/**
 * Project: Lethal Breed
 * Responsibility: Navigation Sidebar Layout
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
import { X } from 'lucide-react'
import { Logo } from './Logo'
import { SidebarNav } from './SidebarNav'
import { SidebarFooter } from './SidebarFooter'

interface Props {
  currentPage: string;
  setPage: (page: string) => void;
  isOpen: boolean;
  onClose: () => void;
}

export const Sidebar = ({ currentPage, setPage, isOpen, onClose }: Props) => (
  <>
    {isOpen && (
      <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-sm z-50 lg:hidden" onClick={onClose} />
    )}
    <aside className={`
      fixed inset-y-0 left-0 w-72 bg-white border-r border-slate-100 p-6 shrink-0 h-screen z-[60] 
      flex flex-col transform transition-transform duration-300 ease-in-out lg:translate-x-0 lg:sticky lg:top-0 lg:w-64
      ${isOpen ? 'translate-x-0' : '-translate-x-full'}
    `}>
      <div className="flex justify-between items-start lg:block">
        <Logo />
        <button onClick={onClose} className="lg:hidden p-2 text-slate-400 hover:text-slate-800 hover:bg-slate-50 rounded-xl transition-colors">
          <X size={24} />
        </button>
      </div>
      <SidebarNav currentPage={currentPage} setPage={setPage} onClose={onClose} />
      <SidebarFooter />
    </aside>
  </>
)
