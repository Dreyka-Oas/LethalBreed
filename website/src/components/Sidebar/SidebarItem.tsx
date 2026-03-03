/**
 * Project: Lethal Breed
 * Responsibility: Navigation Item Component
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
import { LucideIcon } from 'lucide-react'

interface Props {
  icon: LucideIcon;
  label: string;
  active?: boolean;
  onClick?: () => void;
}

export const SidebarItem = ({ icon: Icon, label, active, onClick }: Props) => (
  <button
    onClick={onClick}
    className={`w-full flex items-center gap-3 px-4 py-2.5 rounded-xl transition-all font-sans ${active
      ? 'bg-emerald-50 text-mc-dark-green font-bold shadow-soft-sm'
      : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
      }`}>
    <Icon size={18} className={active ? 'text-mc-green' : 'text-slate-400'} />
    <span className="text-sm tracking-wide">{label}</span>
  </button>
)
