/**
 * Project: Lethal Breed
 * Responsibility: Mod Branding and Logo Component
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
export const Logo = () => (
  <div className="flex items-center gap-3 mb-10">
    <div className="w-10 h-10 bg-white flex items-center justify-center shadow-[4px_4px_0px_0px_rgba(0,0,0,1)] rounded-lg border-2 border-slate-900 overflow-hidden">
      <img src="/logo.png" alt="Lethal Breed Logo" className="w-full h-full object-contain" />
    </div>
    <div>
      <h1 className="text-xl font-black tracking-tighter italic text-slate-900 leading-none">LETHAL BREED</h1>
      <p className="text-[9px] font-bold text-mc-green tracking-[0.4em] uppercase mt-1">Oas Work</p>
    </div>
  </div>
)
