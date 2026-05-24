export type RowStatus = 'NEW' | 'DUPLICATE' | 'INVALID'

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
}

export interface RawPreviewResponse {
  rows: RawPreviewRow[]
}

export interface CategoryGroup {
  name: string
  subcategories: string[]
}

export interface CategoriesResponse {
  categories: CategoryGroup[]
}

export interface RawTransactionImport {
  bookingDate: string
  valueDate: string
  amount: number
  currency: string
  category: string
  subcategory: string
}

export interface ImportRawRequest {
  accountIban: string
  accountName: string
  transactions: RawTransactionImport[]
}

export interface ImportRawResponse {
  importedCount: number
}

export async function previewRawImport(file: File): Promise<RawPreviewResponse> {
  const formData = new FormData()
  formData.append('file', file)
  const res = await fetch('/transactions/raw/preview', { method: 'POST', body: formData })
  if (!res.ok) {
    const body = await res.json().catch(() => ({})) as Record<string, unknown>
    throw new Error((body.error as string | undefined) ?? `HTTP ${res.status}`)
  }
  return res.json() as Promise<RawPreviewResponse>
}

export async function fetchCategories(): Promise<CategoriesResponse> {
  const res = await fetch('/categories')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<CategoriesResponse>
}

export async function importRaw(request: ImportRawRequest): Promise<ImportRawResponse> {
  const res = await fetch('/transactions/raw/import', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<ImportRawResponse>
}
