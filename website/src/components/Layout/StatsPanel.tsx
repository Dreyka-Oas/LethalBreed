/**
 * Project: Lethal Breed
 * Responsibility: Mod Requirements and Technical Details Panel
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
import { Cpu, Box, Code } from 'lucide-react'

const TechDetail = ({ icon: Icon, l, v }: { icon: any, l: string, v: string }) => (
  <div className="flex items-center gap-3 p-3 bg-white border border-slate-100 rounded-xl shadow-soft-sm hover:shadow-soft-md hover:-translate-y-0.5 transition-all group">
    <div className="text-slate-400 bg-slate-50 p-2 rounded-lg group-hover:bg-emerald-50 group-hover:text-mc-green transition-colors"><Icon size={18} /></div>
    <div>
      <p className="text-[10px] font-semibold text-slate-500 uppercase tracking-widest leading-none mb-1">{l}</p>
      <p className="text-sm font-bold text-slate-900">{v}</p>
    </div>
  </div>
)

export const StatsPanel = () => (
  <div className="bg-white border border-slate-100 p-6 shadow-soft-md rounded-2xl">
    <h4 className="text-[10px] font-semibold text-slate-400 uppercase tracking-widest mb-4">Requirements</h4>
    <div className="space-y-3">
      <TechDetail icon={Code} l="Java Runtime" v="Java 21" />
      <TechDetail icon={Box} l="Mod Loader" v="Fabric" />
      <TechDetail icon={Cpu} l="Environment" v="Client/Server" />
    </div>
  </div>
)
