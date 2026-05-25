import { fetchWithUser } from './client'

export interface CsvValidationError {
  row: number
  column: string
  value: string
  message: string
}

export async function importCsv(
  file: File,
): Promise<{ importedCount: number } | { errors: CsvValidationError[] }> {
  const formData = new FormData()
  formData.append('file', file)
  const res = await fetchWithUser('/transactions/import', { method: 'POST', body: formData })
  const body = await res.json() as Record<string, unknown>
  if (!res.ok) {
    if (Array.isArray(body.errors)) return { errors: body.errors as CsvValidationError[] }
    throw new Error(`HTTP ${res.status}`)
  }
  return { importedCount: body.importedCount as number }
}
