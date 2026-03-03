/**
 * Project: Lethal Breed
 * Responsibility: Mod Specification Information Box
 * License: O.A.S - MS-RSL (Microsoft Reference Source License)
 * Copyright (c) 2026 O.A.S (Optimization & Quality). All rights reserved.
 */
import { Shield } from 'lucide-react'
import { DownloadSection } from './DownloadSection'

const Row = ({ l, v }: { l: string, v: string }) => (
  <div className="flex border-b border-slate-100 last:border-0 hover:bg-slate-50 transition-colors group">
    <div className="w-1/3 p-3 text-[10px] font-semibold text-slate-500 uppercase tracking-wider">{l}</div>
    <div className="w-2/3 p-3 text-sm font-medium text-slate-900 bg-white transition-colors group-hover:bg-slate-50">{v}</div>
  </div>
)

export const InfoBox = () => (
  <div className="w-full bg-white border border-slate-100 shadow-soft-md rounded-2xl overflow-hidden">
    <div className="bg-slate-50 p-4 text-slate-900 flex justify-between border-b border-slate-100 items-center">
      <span className="font-bold tracking-tight text-sm">Spec Sheet</span>
      <Shield size={18} className="text-mc-green" />
    </div>
    <Row l="Version" v="1.0.0 (beta)" />
    <Row l="Loader" v="Fabric" />
    <Row l="MC" v="1.21.11" />
    <DownloadSection />
  </div>
)
