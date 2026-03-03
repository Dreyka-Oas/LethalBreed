/**
 * Project: Lethal Breed
 * Responsibility: Download and Support Links (Light Mode)
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
import { Download, Github, Heart } from 'lucide-react'

export const DownloadSection = () => (
  <div className="space-y-6 pt-12 border-t border-zinc-100">
    <button className="w-full bg-zinc-900 text-white p-6 flex items-center justify-between group hover:bg-brand transition-all shadow-[6px_6px_0px_0px_rgba(0,0,0,0.1)] active:translate-x-1 active:translate-y-1 active:shadow-none">
      <div className="flex items-center gap-4">
        <Download size={24} />
        <div className="text-left">
          <div className="text-[10px] font-black uppercase tracking-widest opacity-50">Latest Stable</div>
          <div className="text-sm font-black uppercase tracking-tight text-white">Download Artifact</div>
        </div>
      </div>
      <span className="text-[10px] font-black px-2 py-1 border border-white/20">v1.0.0</span>
    </button>

    <div className="grid grid-cols-2 gap-4">
      <a href="#" className="p-4 border-2 border-zinc-100 text-zinc-900 flex items-center justify-center gap-3 hover:border-zinc-900 hover:bg-zinc-50 transition-all font-black text-[10px] uppercase tracking-widest">
        <Github size={18} /> Source
      </a>
      <a href="#" className="p-4 border-2 border-zinc-100 text-zinc-900 flex items-center justify-center gap-3 hover:border-brand hover:text-brand hover:bg-brand/5 transition-all font-black text-[10px] uppercase tracking-widest">
        <Heart size={18} /> Support
      </a>
    </div>
  </div>
)
