import { fetchWithUser } from './client'

export interface ImportFileDto {
  id: number
  filename: string
  checksum: string
  fileType: string
  transactionCount: number
  status: 'ACTIVE' | 'REJECTED'
}

export interface TransactionImportDto {
  id: number
  importedAt: string
  transactionCount: number
  status: 'ACTIVE' | 'REJECTED'
  files: ImportFileDto[]
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
