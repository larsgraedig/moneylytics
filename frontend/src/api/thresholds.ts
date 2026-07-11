import { fetchWithUser } from './client'

export type ThresholdPeriod = 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY'
export type ThresholdStatus = 'OK' | 'NOTICE' | 'WARNING' | 'CRITICAL'

export interface ThresholdStatusItem {
  thresholdId: number
  category: string
  subcategory: string | null
  categoryGroup: string | null
  period: ThresholdPeriod
  notice: number | null
  warning: number | null
  critical: number | null
  spending: number
  periodsInRange: number
  pct: number
  tickNotice: number | null
  tickWarning: number | null
  status: ThresholdStatus
}

export interface Threshold {
  id: number
  category: string
  subcategory: string | null
  period: ThresholdPeriod
  notice: number | null
  warning: number | null
  critical: number | null
}

export interface SaveThresholdRequest {
  category: string
  subcategory: string | null
  period: ThresholdPeriod
  notice: number | null
  warning: number | null
  critical: number | null
}

export async function fetchThresholds(): Promise<Threshold[]> {
  const res = await fetchWithUser('/thresholds')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const data = await res.json() as { thresholds: Threshold[] }
  return data.thresholds
}

export async function saveThreshold(request: SaveThresholdRequest): Promise<Threshold> {
  const res = await fetchWithUser('/thresholds', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<Threshold>
}

export async function deleteThreshold(id: number): Promise<void> {
  const res = await fetchWithUser(`/thresholds/${id}`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export async function fetchThresholdStatus(
  from: string,
  to: string,
  iban?: string,
): Promise<ThresholdStatusItem[]> {
  const params = new URLSearchParams({ from, to })
  if (iban) params.set('iban', iban)
  const res = await fetchWithUser(`/thresholds/status?${params}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const data = await res.json() as { items: ThresholdStatusItem[] }
  return data.items
}
