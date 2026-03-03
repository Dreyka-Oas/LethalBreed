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
  lang: 'fr' | 'en';
  onClose: () => void;
}

export const SidebarNav = ({ currentPage, setPage, lang, onClose }: Props) => {
  const navigate = (page: string) => {
    setPage(page);
    onClose();
  };

  const labels = {
    fr: {
      main: "Principal",
      presentation: "Présentation",
      technical: "Technique",
      features: "Fonctionnalités",
      server: "Serveur",
      install: "Installation",
      system: "Système",
      config: "Configuration"
    },
    en: {
      main: "Main",
      presentation: "Presentation",
      technical: "Technical",
      features: "Features",
      server: "Server",
      install: "Install",
      system: "System",
      config: "Config"
    }
  }[lang];

  return (
    <nav className="flex-1 space-y-8">
      <div className="space-y-1">
        <p className="px-4 text-[10px] font-black text-zinc-400 uppercase tracking-[0.2em] mb-4">{labels.main}</p>
        <SidebarItem icon={Home} label={labels.presentation} active={currentPage === 'presentation'} onClick={() => navigate('presentation')} />
        <SidebarItem icon={Book} label={labels.technical} active={currentPage === 'overview'} onClick={() => navigate('overview')} />
        <SidebarItem icon={Layers} label={labels.features} active={currentPage === 'features'} onClick={() => navigate('features')} />
        <SidebarItem icon={Server} label={labels.server} active={currentPage === 'server'} onClick={() => navigate('server')} />
      </div>
      <div className="pt-6 border-t border-zinc-100 space-y-1">
        <p className="px-4 text-[10px] font-black text-zinc-400 uppercase tracking-[0.2em] mb-4">{labels.system}</p>
        <SidebarItem icon={Settings} label={labels.config} active={currentPage === 'config'} onClick={() => navigate('config')} />
      </div>
    </nav>
  );
};
