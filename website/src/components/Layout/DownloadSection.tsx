/**
 * Project: Lethal Breed
 * Responsibility: Platform Selection Links (Modrinth/CurseForge)
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
import { ExternalLink, Box } from 'lucide-react'

export const DownloadSection = () => (
  <div className="space-y-6 pt-12 border-t border-zinc-100">
    <h4 className="text-[10px] font-black uppercase tracking-[0.3em] text-zinc-400 mb-4">
      Availability
    </h4>
    
    <a 
      href="https://modrinth.com/mod/lethal-breed" 
      target="_blank" 
      rel="noreferrer"
      className="w-full bg-[#1bd96a] text-white p-6 flex items-center justify-between group transition-all shadow-[6px_6px_0px_0px_rgba(0,0,0,0.1)] active:translate-x-1 active:translate-y-1 active:shadow-none"
    >
      <div className="flex items-center gap-4">
        <Box size={24} />
        <div className="text-left">
          <div className="text-[10px] font-black uppercase tracking-widest opacity-70">Platform</div>
          <div className="text-sm font-black uppercase tracking-tight">Modrinth</div>
        </div>
      </div>
      <ExternalLink size={18} className="opacity-50" />
    </a>

    <a 
      href="https://www.curseforge.com/minecraft/mc-mods/lethal-breed" 
      target="_blank" 
      rel="noreferrer"
      className="w-full bg-[#f16436] text-white p-6 flex items-center justify-between group transition-all shadow-[6px_6px_0px_0px_rgba(0,0,0,0.1)] active:translate-x-1 active:translate-y-1 active:shadow-none"
    >
      <div className="flex items-center gap-4">
        <Box size={24} />
        <div className="text-left">
          <div className="text-[10px] font-black uppercase tracking-widest opacity-70">Platform</div>
          <div className="text-sm font-black uppercase tracking-tight">CurseForge</div>
        </div>
      </div>
      <ExternalLink size={18} className="opacity-50" />
    </a>
  </div>
)
