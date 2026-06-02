import { fetchWithUser } from './client'

export type Granularity = 'MONTHLY' | 'WEEKLY' | 'DAILY' | 'QUARTERLY' | 'YEARLY' | 'BI_YEARLY'
export type SeriesRole = 'MAIN_SELECTED' | 'MAIN_CONTEXT' | 'SUB_SELECTED' | 'SUB_CONTEXT'

export interface TrendSeriesEntry {
  label: string
  data: number[]
  role: SeriesRole
}

export interface TrendSeriesGroup {
  main: TrendSeriesEntry
  subs: TrendSeriesEntry[]
}

export interface TrendsResponse {
  granularity: Granularity
  buckets: string[]
  groups: TrendSeriesGroup[]
}

export interface SeriesConfig {
  id: string
  category: string
  subcategory: string
}

export async function fetchTrends(
  from: string,
  to: string,
  series: SeriesConfig[],
  granularity: Granularity,
  iban?: string,
): Promise<TrendsResponse> {
  const params = new URLSearchParams()
  params.set('from', from)
  params.set('to', to)
  params.set('granularity', granularity)
  if (iban) params.set('iban', iban)
  for (const s of series) {
    if (!s.category.trim()) continue
    const spec = s.subcategory.trim() ? `${s.category}:${s.subcategory}` : s.category
    params.append('series', spec)
  }
  const res = await fetchWithUser(`/transactions/trends?${params}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<TrendsResponse>
}
