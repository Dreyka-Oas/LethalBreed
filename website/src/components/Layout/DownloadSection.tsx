/**
 * Project: Lethal Breed
 * Responsibility: Download Links Section
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
import { Github } from 'lucide-react'

const DownloadBtn = ({ l, color }: { l: string, color: string }) => (
  <button className={`${color} text-white font-bold uppercase text-xs py-2.5 px-4 shadow-soft-sm hover:-translate-y-0.5 hover:shadow-soft-md active:translate-y-0 active:shadow-none transition-all w-full rounded-xl`}>
    {l}
  </button>
)

export const DownloadSection = () => (
  <div className="p-5 bg-slate-50 border-t border-slate-100 grid grid-cols-2 gap-3">
    <DownloadBtn l="CURSEFORGE" color="bg-[#f16436]" />
    <DownloadBtn l="MODRINTH" color="bg-[#1bd96a]" />
    <a href="https://github.com" className="col-span-2 bg-slate-800 text-white text-sm font-bold py-2.5 px-4 shadow-soft-sm hover:-translate-y-0.5 hover:shadow-soft-md active:translate-y-0 active:shadow-none transition-all rounded-xl flex items-center justify-center gap-2 uppercase">
      Source Code <Github size={18} />
    </a>
  </div>
)
