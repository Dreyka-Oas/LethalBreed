/**
 * Project: Lethal Breed
 * Responsibility: Navigation Menu Structure
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
import { Book, Settings, Layers, Download, Home, Server } from 'lucide-react'
import { SidebarItem } from './SidebarItem'

interface Props {
  currentPage: string;
  setPage: (page: string) => void;
  onClose: () => void;
}

export const SidebarNav = ({ currentPage, setPage, onClose }: Props) => {
  const navigate = (page: string) => {
    setPage(page);
    onClose();
  };

  return (
    <nav className="flex-1 space-y-8">
      <div className="space-y-1">
        <p className="px-4 text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Main</p>
        <SidebarItem icon={Home} label="Presentation" active={currentPage === 'presentation'} onClick={() => navigate('presentation')} />
        <SidebarItem icon={Book} label="Technical" active={currentPage === 'overview'} onClick={() => navigate('overview')} />
        <SidebarItem icon={Layers} label="Features" active={currentPage === 'features'} onClick={() => navigate('features')} />
        <SidebarItem icon={Server} label="Server" active={currentPage === 'server'} onClick={() => navigate('server')} />
        <SidebarItem icon={Download} label="Install" active={currentPage === 'install'} onClick={() => navigate('install')} />
      </div>
      <div className="pt-6 border-t border-slate-100 space-y-1">
        <p className="px-4 text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">System</p>
        <SidebarItem icon={Settings} label="Config" active={currentPage === 'config'} onClick={() => navigate('config')} />
      </div>
    </nav>
  );
};
