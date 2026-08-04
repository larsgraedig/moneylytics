import { fetchWithUser } from './client'

export type RowStatus = 'NEW' | 'DUPLICATE' | 'INVALID' | 'PREVIOUSLY_IGNORED'

export interface RawPreviewError {
  column: string
  value: string
  message: string
}

export interface RawPreviewRow {
  rowNumber: number
  status: RowStatus
  bookingDate: string | null
  valueDate: string | null
  counterparty: string
  purpose: string
  amount: number | null
  amountRaw: string
  currency: string
  accountIban: string
  accountName: string
  fingerprint: string | null
  errors: RawPreviewError[]
  unknownAccount: boolean
  counterpartyIban: string | null
}

export interface CategoryNode {
  id: number
  name: string
  children: CategoryNode[]
}

export interface CategoriesResponse {
  categories: CategoryNode[]
}

export async function fetchCategories(): Promise<CategoriesResponse> {
  const res = await fetchWithUser('/categories')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<CategoriesResponse>
}

export async function findOrCreateCategory(path: string[]): Promise<CategoryNode> {
  const res = await fetchWithUser('/categories', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ path }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<CategoryNode>
}

export interface CategoryStatItem {
  categoryId: number
  totalCount: number
  periodCount: number
}

export interface CategoryStatsResponse {
  items: CategoryStatItem[]
}

export async function deleteCategory(id: number): Promise<void> {
  const res = await fetchWithUser(`/categories/${id}`, { method: 'DELETE' })
  if (res.status === 409) {
    const body = await res.json().catch(() => ({})) as { reason?: string }
    throw new Error(body.reason ?? 'UNKNOWN')
  }
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export async function fetchCategoryStats(
  from: string,
  to: string,
  iban?: string,
): Promise<CategoryStatsResponse> {
  const params = new URLSearchParams({ from, to })
  if (iban) params.set('iban', iban)
  const res = await fetchWithUser(`/categories/stats?${params}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<CategoryStatsResponse>
}
