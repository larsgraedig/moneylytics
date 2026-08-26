import { fetchWithUser } from './client'

export type ImportStatus = 'ACTIVE' | 'REJECTED' | 'PARTIALLY_REJECTED'

export interface ImportFileDto {
  id: number
  filename: string
  checksum: string
  fileType: string
  transactionCount: number
  status: ImportStatus
}

export interface TransactionImportDto {
  id: number
  importedAt: string
  transactionCount: number
  status: ImportStatus
  files: ImportFileDto[]
}

export interface ImportTransactionDto {
  id: number
  bookingDate: string
  counterpartyName: string | null
  purpose: string | null
  amount: number
  currency: string
  status: 'ACCEPTED' | 'REJECTED'
  collections: string[]
  budgets: string[]
  offsetGroups: (string | null)[]
  parentId: number | null
  isVirtual: boolean
  categoryPath: string | null
}

export interface BlockedTransaction {
  transactionId: number
  reasons: string[]
}

export interface RejectImportSuccess {
  rejectedCount: number
}

export interface RejectImportFailure {
  blocked: BlockedTransaction[]
}

export async function fetchImports(): Promise<TransactionImportDto[]> {
  const res = await fetchWithUser('/imports')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<TransactionImportDto[]>
}

export async function rejectImport(
  importId: number,
  force = false,
): Promise<RejectImportSuccess | { error: RejectImportFailure }> {
  const res = await fetchWithUser(`/imports/${importId}/reject`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ force }),
  })
  const body = (await res.json()) as Record<string, unknown>
  if (res.status === 422) return { error: body as unknown as RejectImportFailure }
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return body as unknown as RejectImportSuccess
}

export async function fetchImportTransactions(importId: number): Promise<ImportTransactionDto[]> {
  const res = await fetchWithUser(`/imports/${importId}/transactions`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<ImportTransactionDto[]>
}

export async function fetchImportFileTransactions(importId: number, fileId: number): Promise<ImportTransactionDto[]> {
  const res = await fetchWithUser(`/imports/${importId}/files/${fileId}/transactions`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<ImportTransactionDto[]>
}

export async function rejectImportFile(
  importId: number,
  fileId: number,
  force = false,
): Promise<RejectImportSuccess | { error: RejectImportFailure }> {
  const res = await fetchWithUser(`/imports/${importId}/files/${fileId}/reject`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ force }),
  })
  const body = (await res.json()) as Record<string, unknown>
  if (res.status === 422) return { error: body as unknown as RejectImportFailure }
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return body as unknown as RejectImportSuccess
}
