import { fetchWithUser } from './client'
import type { RawPreviewRow } from './rawImport'

export type { RawPreviewRow }

export interface CamtAccountInfo {
  iban: string
  suggestedName: string
}

export interface CamtAccountBalance {
  amount: number
  date: string
}

export interface CamtPreviewResponse {
  rows: RawPreviewRow[]
  accounts: CamtAccountInfo[]
  accountBalances: Record<string, CamtAccountBalance>
}

export interface CamtTransactionImport {
  fingerprint: string
  bookingDate: string
  valueDate: string
  amount: number
  currency: string
  category: string
  subcategory: string
  accountIban: string
  purpose: string | null
  counterpartyName: string | null
  counterpartyIban: string | null
  categoryGroup: string | null
}

export interface TransactionEnrichRequest {
  fingerprint: string
  purpose: string | null
  counterpartyName: string | null
  counterpartyIban: string | null
}

export interface CamtImportRequest {
  accountNames: Record<string, string>
  toImport: CamtTransactionImport[]
  toIgnore: string[]
  toEnrich: TransactionEnrichRequest[]
  accountBalances: Record<string, CamtAccountBalance>
}

export interface CamtImportResponse {
  importedCount: number
}

export async function previewCamtImport(files: File[]): Promise<CamtPreviewResponse> {
  const formData = new FormData()
  for (const file of files) {
    formData.append('files', file)
  }
  const res = await fetchWithUser('/transactions/camt/preview', { method: 'POST', body: formData })
  if (!res.ok) {
    const body = await res.json().catch(() => ({})) as Record<string, unknown>
    throw new Error((body.error as string | undefined) ?? `HTTP ${res.status}`)
  }
  return res.json() as Promise<CamtPreviewResponse>
}

export async function importCamt(request: CamtImportRequest): Promise<CamtImportResponse> {
  const res = await fetchWithUser('/transactions/camt/import', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<CamtImportResponse>
}
