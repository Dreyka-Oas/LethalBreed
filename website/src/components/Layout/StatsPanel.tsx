/**
 * Project: Lethal Breed
 * Responsibility: Side Stats Visualization Panel (Light Mode)
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
import { Activity, Zap, Shield, Target } from 'lucide-react'

export const StatsPanel = () => (
  <div className="space-y-6">
    <h4 className="text-[10px] font-black uppercase tracking-[0.3em] text-zinc-400 mb-8">
      Entity Analytics
    </h4>
    
    <div className="grid grid-cols-1 gap-4">
      <StatItem icon={Activity} label="AI Intelligence" value="Tier 4" color="text-brand" />
      <StatItem icon={Zap} label="Response Time" value="< 50ms" color="text-amber-600" />
      <StatItem icon={Shield} label="Adaptability" value="High" color="text-blue-600" />
      <StatItem icon={Target} label="Threat Level" value="Lethal" color="text-red-600" />
    </div>
  </div>
)

const StatItem = ({ icon: Icon, label, value, color }: any) => (
  <div className="bg-zinc-50 p-5 flex items-center justify-between border border-zinc-100 rounded hover:border-zinc-300 transition-all group">
    <div className="flex items-center gap-4">
      <div className="p-2 bg-white border border-zinc-100 rounded shadow-sm group-hover:border-zinc-900 transition-colors">
        <Icon size={16} className="text-zinc-900" />
      </div>
      <span className="text-[10px] font-black text-zinc-400 uppercase tracking-widest">{label}</span>
    </div>
    <span className={`text-[10px] font-black uppercase tracking-wider ${color}`}>{value}</span>
  </div>
)
