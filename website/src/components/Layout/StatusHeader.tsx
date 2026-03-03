/**
 * Project: Lethal Breed
 * Responsibility: Page Header with Version and Status (Light Mode)
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
export const StatusHeader = () => (
  <header className="mb-20">
    <div className="flex flex-wrap items-center gap-4 mb-8">
      <span className="px-4 py-1.5 bg-zinc-900 text-white text-[10px] font-black uppercase tracking-[0.2em]">
        Lethal Breed Project
      </span>
      <span className="px-4 py-1.5 border-2 border-zinc-900 text-zinc-900 text-[10px] font-black uppercase tracking-[0.2em]">
        v1.0.0-beta
      </span>
      <div className="flex items-center gap-2 ml-auto">
        <div className="w-2 h-2 rounded-full bg-brand animate-pulse" />
        <span className="text-[10px] font-black text-zinc-400 uppercase tracking-widest">
          Systems Online
        </span>
      </div>
    </div>
    <h1 className="text-7xl font-black tracking-tighter text-zinc-900 uppercase leading-[0.85]">
      Evolution <br/>
      <span className="text-brand">Protocol.</span>
    </h1>
  </header>
)
