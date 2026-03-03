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
    className={`sidebar-item w-full ${active ? 'sidebar-item-active' : 'sidebar-item-inactive'}`}>
    <Icon size={20} className={active ? 'text-white' : 'text-ui-muted'} />
    <span className="text-sm font-semibold tracking-tight">{label}</span>
  </button>
)
