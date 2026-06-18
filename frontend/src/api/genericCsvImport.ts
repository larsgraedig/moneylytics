export type AmountFormat = 'GERMAN' | 'ENGLISH'

export interface CsvColumnSuggestions {
  date: string | null
  amount: string | null
  currency: string | null
  purpose: string | null
  accountIban: string | null
  category: string | null
  subcategory: string | null
}

export interface CsvDetectionResult {
  fingerprint: string
  delimiter: string
  headers: string[]
  sampleRows: string[][]
  suggestions: CsvColumnSuggestions
  detectedDateFormat: string | null
  detectedAmountFormat: AmountFormat
  savedMapping: CsvMapping | null
}

export interface CsvMapping {
  delimiter: string
  dateColumn: string
  dateFormat: string
  amountColumn: string
  amountFormat: AmountFormat
  purposeColumn: string | null
  categoryColumn: string | null
  subcategoryColumn: string | null
  accountIbanColumn: string | null
  currencyColumn: string | null
  fixedAccountIban: string | null
  fixedCurrency: string
}

export async function detectCsvFormat(file: File): Promise<CsvDetectionResult> {
  const form = new FormData()
  form.append('file', file)
  const res = await fetch('/transactions/csv/detect', { method: 'POST', body: form })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<CsvDetectionResult>
}

export async function importGenericCsv(file: File, mapping: CsvMapping): Promise<number> {
  const form = new FormData()
  form.append('file', file)
  form.append('mapping', new Blob([JSON.stringify(mapping)], { type: 'application/json' }))
  const res = await fetch('/transactions/csv/import', { method: 'POST', body: form })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const data = await res.json() as { importedCount: number }
  return data.importedCount
}
