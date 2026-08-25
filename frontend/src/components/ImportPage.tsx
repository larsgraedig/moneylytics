import { useCallback, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { FileCode, FileSpreadsheet } from 'lucide-react'
import {
  importCamt,
  previewCamtImport,
  type CamtAccountBalance,
  type CamtAccountInfo,
  type RawPreviewRow,
} from '../api/camtImport'
import {
  detectCsvFormat,
  importGenericRows,
  previewGenericCsv,
  type AmountFormat,
  type CsvDetectionResult,
  type CsvMapping,
  type GenericCsvPreviewRow,
  type GenericRowToImport,
} from '../api/genericCsvImport'
import { ImportPreviewTable, type ImportDecision, type ImportPreviewRow } from './ImportPreviewTable'

// ── CSV helpers ────────────────────────────────────────────────────────────────

const COMMON_DATE_FORMATS = ['dd.MM.yyyy', 'yyyy-MM-dd', 'dd/MM/yyyy', 'MM/dd/yyyy', 'dd.MM.yy']

function buildInitialMapping(d: CsvDetectionResult): CsvMapping {
  if (d.savedMapping) return d.savedMapping
  return {
    delimiter: d.delimiter,
    dateColumn: d.suggestions.date ?? d.headers[0] ?? '',
    dateFormat: d.detectedDateFormat ?? 'dd.MM.yyyy',
    amountColumn: d.suggestions.amount ?? '',
    amountFormat: d.detectedAmountFormat,
    purposeColumn: d.suggestions.purpose ?? null,
    categoryColumn: null,
    subcategoryColumn: null,
    categoryGroupColumn: null,
    accountIbanColumn: d.suggestions.accountIban ?? null,
    currencyColumn: d.suggestions.currency ?? null,
    fixedAccountIban: null,
    fixedCurrency: 'EUR',
    counterpartyNameColumn: d.suggestions.counterpartyName ?? null,
    counterpartyIbanColumn: d.suggestions.counterpartyIban ?? null,
  }
}

function parsePreviewAmount(raw: string, format: AmountFormat): string {
  if (!raw) return ''
  try {
    const normalized = format === 'GERMAN'
      ? raw.replace(/\./g, '').replace(',', '.')
      : raw.replace(/,/g, '')
    const n = parseFloat(normalized)
    return isNaN(n) ? raw : n.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  } catch { return raw }
}

function parsePreviewDate(raw: string, fmt: string): string {
  if (!raw) return ''
  try {
    const v = raw.trim().substring(0, 10)
    if (fmt === 'dd.MM.yyyy' && /^\d{2}\.\d{2}\.\d{4}$/.test(v)) return v
    if (fmt === 'yyyy-MM-dd' && /^\d{4}-\d{2}-\d{2}$/.test(v)) {
      const [y, m, d] = v.split('-')
      return `${d}.${m}.${y}`
    }
    if ((fmt === 'dd/MM/yyyy' || fmt === 'MM/dd/yyyy') && /^\d{2}\/\d{2}\/\d{4}$/.test(v)) return v
    return v
  } catch { return raw }
}

function colIdx(headers: string[], col: string | null) {
  if (!col) return -1
  return headers.indexOf(col)
}

// ── CAMT helpers ───────────────────────────────────────────────────────────────

function formatDate(iso: string | null): string {
  if (!iso) return '—'
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

function adaptCamtRow(row: RawPreviewRow, accountNames: Record<string, string>): ImportPreviewRow {
  return {
    key: row.rowNumber,
    status: row.status,
    unknownAccount: row.unknownAccount,
    date: formatDate(row.bookingDate),
    accountDisplay: accountNames[row.accountIban] || row.accountIban,
    accountIban: row.accountIban,
    counterparty: row.counterparty || null,
    purpose: row.purpose || null,
    amount: row.amount,
    amountRaw: row.amountRaw,
    currency: row.currency,
    errors: row.errors,
    fingerprint: row.fingerprint,
  }
}

function defaultCamtFilters(rows: RawPreviewRow[]): Set<string> {
  if (rows.some(r => r.status === 'NEW' && !r.unknownAccount)) return new Set(['NEW'])
  if (rows.some(r => r.status === 'DUPLICATE')) return new Set(['DUPLICATE'])
  return new Set(['UNKNOWN_ACCOUNT'])
}

function initCamtDecisions(rows: RawPreviewRow[]): Record<number, ImportDecision> {
  const out: Record<number, ImportDecision> = {}
  for (const row of rows) {
    if (row.unknownAccount) continue
    if (row.status === 'NEW') {
      out[row.rowNumber] = { action: 'import' }
    } else if (row.status === 'PREVIOUSLY_IGNORED') {
      out[row.rowNumber] = { action: 'ignore' }
    }
  }
  return out
}

function defaultCsvFilters(rows: GenericCsvPreviewRow[]): Set<string> {
  if (rows.some(r => r.status !== 'DUPLICATE' && !r.unknownAccount)) return new Set(['NEW'])
  if (rows.some(r => r.status === 'DUPLICATE')) return new Set(['DUPLICATE'])
  return new Set(['UNKNOWN_ACCOUNT'])
}

// ── MappingRow sub-component ───────────────────────────────────────────────────

function MappingRow({
  label,
  required,
  headers,
  value,
  onChange,
}: {
  label: string
  required?: boolean
  headers: string[]
  value: string | null
  onChange: (v: string | null) => void
}) {
  const { t } = useTranslation()
  return (
    <div className="gcv-map-row">
      <span className="gcv-map-label">
        {label}
        {required && <span className="gcv-map-required">*</span>}
      </span>
      <select
        className="gcv-map-select"
        value={value ?? ''}
        onChange={e => onChange(e.target.value || null)}
      >
        {!required && <option value="">{t('csvImport.mapping.notMapped')}</option>}
        {headers.map(h => <option key={h} value={h}>{h}</option>)}
      </select>
    </div>
  )
}

// ── MappingView sub-component ──────────────────────────────────────────────────

function MappingView({
  detection,
  mapping,
  file,
  onChange,
  onConfirm,
  onCancel,
  importing,
  fileProgress,
  sessionSuggested,
}: {
  detection: CsvDetectionResult
  mapping: CsvMapping
  file: File
  onChange: (m: CsvMapping) => void
  onConfirm: () => void
  onCancel: () => void
  importing: boolean
  fileProgress?: { current: number; total: number }
  sessionSuggested?: boolean
}) {
  const { t } = useTranslation()
  const { headers, sampleRows } = detection

  const dateIdx = colIdx(headers, mapping.dateColumn)
  const amtIdx = colIdx(headers, mapping.amountColumn)
  const purpIdx = colIdx(headers, mapping.purposeColumn)
  const ibanIdx = colIdx(headers, mapping.accountIbanColumn)
  const currIdx = colIdx(headers, mapping.currencyColumn)
  const cpNameIdx = colIdx(headers, mapping.counterpartyNameColumn)
  const cpIbanIdx = colIdx(headers, mapping.counterpartyIbanColumn)

  const canConfirm = mapping.dateColumn && mapping.amountColumn &&
    (mapping.accountIbanColumn || mapping.fixedAccountIban)

  function set<K extends keyof CsvMapping>(key: K, value: CsvMapping[K]) {
    onChange({ ...mapping, [key]: value })
  }

  return (
    <div className="gcv-page">
      <div className="gcv-header">
        <div>
          <div className="gcv-title">
            {t('csvImport.mapping.title')}
            {detection.savedMapping && (
              <span className="gcv-saved-badge">{t('csvImport.mapping.savedBadge')}</span>
            )}
            {sessionSuggested && !detection.savedMapping && (
              <span className="gcv-saved-badge">{t('csvImport.mapping.sessionBadge')}</span>
            )}
          </div>
          <div className="gcv-subtitle">
            {file.name} · {t('csvImport.mapping.delimiter')}: <code>{mapping.delimiter === '\t' ? 'Tab' : mapping.delimiter}</code>
            {fileProgress && fileProgress.total > 1 && (
              <span style={{ marginLeft: 8, opacity: 0.6 }}>{t('csvImport.mapping.fileProgress', fileProgress)}</span>
            )}
          </div>
        </div>
        <div className="gcv-header-actions">
          <button className="gcv-cancel-btn" onClick={onCancel} disabled={importing}>{t('csvImport.mapping.cancel')}</button>
          <button className="gcv-confirm-btn" onClick={onConfirm} disabled={!canConfirm || importing}>
            {importing ? t('csvImport.mapping.importing') : t('csvImport.mapping.import')}
          </button>
        </div>
      </div>

      <div className="gcv-body">
        <div className="gcv-mapping-panel">
          <div className="gcv-section-title">{t('csvImport.mapping.requiredFields')}</div>
          <MappingRow label={t('csvImport.mapping.date')} required headers={headers} value={mapping.dateColumn} onChange={v => set('dateColumn', v ?? '')} />
          <div className="gcv-map-row">
            <span className="gcv-map-label">{t('csvImport.mapping.dateFormat')}<span className="gcv-map-required">*</span></span>
            <select className="gcv-map-select" value={mapping.dateFormat} onChange={e => set('dateFormat', e.target.value)}>
              {COMMON_DATE_FORMATS.map(f => <option key={f} value={f}>{f}</option>)}
              {!COMMON_DATE_FORMATS.includes(mapping.dateFormat) && (
                <option value={mapping.dateFormat}>{mapping.dateFormat}</option>
              )}
            </select>
          </div>
          <MappingRow label={t('csvImport.mapping.amount')} required headers={headers} value={mapping.amountColumn} onChange={v => set('amountColumn', v ?? '')} />
          <div className="gcv-map-row">
            <span className="gcv-map-label">{t('csvImport.mapping.amountFormat')}<span className="gcv-map-required">*</span></span>
            <div className="gcv-radio-group">
              {(['GERMAN', 'ENGLISH'] as AmountFormat[]).map(f => (
                <label key={f} className="gcv-radio-label">
                  <input type="radio" checked={mapping.amountFormat === f} onChange={() => set('amountFormat', f)} />
                  {f === 'GERMAN' ? t('csvImport.mapping.amountGerman') : t('csvImport.mapping.amountEnglish')}
                </label>
              ))}
            </div>
          </div>

          <MappingRow label={`${t('csvImport.mapping.accountIban')} *`} headers={headers} value={mapping.accountIbanColumn} onChange={v => set('accountIbanColumn', v)} />
          {!mapping.accountIbanColumn && (
            <div className="gcv-map-row">
              <span className="gcv-map-label gcv-map-label--sub">{t('csvImport.mapping.fixedIban')}<span className="gcv-map-required">*</span></span>
              <input
                className="gcv-map-input"
                placeholder={t('csvImport.mapping.fixedIbanPlaceholder')}
                value={mapping.fixedAccountIban ?? ''}
                onChange={e => set('fixedAccountIban', e.target.value || null)}
              />
            </div>
          )}

          <div className="gcv-section-title" style={{ marginTop: 16 }}>{t('csvImport.mapping.optionalFields')}</div>
          <MappingRow label={t('csvImport.mapping.purpose')} headers={headers} value={mapping.purposeColumn} onChange={v => set('purposeColumn', v)} />
          <MappingRow label={t('csvImport.mapping.currency')} headers={headers} value={mapping.currencyColumn} onChange={v => set('currencyColumn', v)} />
          {!mapping.currencyColumn && (
            <div className="gcv-map-row">
              <span className="gcv-map-label gcv-map-label--sub">{t('csvImport.mapping.fixedCurrency')}</span>
              <input
                className="gcv-map-input"
                value={mapping.fixedCurrency}
                onChange={e => set('fixedCurrency', e.target.value)}
              />
            </div>
          )}
          <MappingRow label={t('csvImport.mapping.counterpartyName')} headers={headers} value={mapping.counterpartyNameColumn} onChange={v => set('counterpartyNameColumn', v)} />
          <MappingRow label={t('csvImport.mapping.counterpartyIban')} headers={headers} value={mapping.counterpartyIbanColumn} onChange={v => set('counterpartyIbanColumn', v)} />
        </div>

        <div className="gcv-preview-panel">
          <div className="gcv-section-title">{t('csvImport.mapping.previewTitle', { count: sampleRows.length })}</div>
          <div className="gcv-preview-wrap">
            <table className="gcv-preview-table">
              <thead>
                <tr>
                  <th>{t('csvImport.categorizing.columns.date')}</th>
                  <th>{t('csvImport.categorizing.columns.amount')}</th>
                  {currIdx >= 0 && <th>{t('csvImport.categorizing.columns.currency')}</th>}
                  {ibanIdx >= 0 && <th>{t('csvImport.categorizing.columns.account')}</th>}
                  {purpIdx >= 0 && <th>{t('csvImport.categorizing.columns.purpose')}</th>}
                  {cpNameIdx >= 0 && <th>{t('csvImport.categorizing.columns.counterpartyName')}</th>}
                  {cpIbanIdx >= 0 && <th>{t('csvImport.categorizing.columns.counterpartyIban')}</th>}
                </tr>
              </thead>
              <tbody>
                {sampleRows.map((row, i) => {
                  const amtRaw = row[amtIdx] ?? ''
                  const amtNum = parseFloat(
                    mapping.amountFormat === 'GERMAN'
                      ? amtRaw.replace(/\./g, '').replace(',', '.')
                      : amtRaw.replace(/,/g, ''),
                  )
                  return (
                    <tr key={i}>
                      <td>{parsePreviewDate(row[dateIdx] ?? '', mapping.dateFormat)}</td>
                      <td className={`gcv-amt ${!isNaN(amtNum) && amtNum < 0 ? 'negative' : 'positive'}`}>
                        {parsePreviewAmount(amtRaw, mapping.amountFormat)}
                      </td>
                      {currIdx >= 0 && <td className="gcv-currency">{row[currIdx] ?? ''}</td>}
                      {ibanIdx >= 0 && <td className="gcv-iban">{row[ibanIdx] ?? ''}</td>}
                      {purpIdx >= 0 && <td className="gcv-purpose">{row[purpIdx] ?? ''}</td>}
                      {cpNameIdx >= 0 && <td>{row[cpNameIdx] ?? ''}</td>}
                      {cpIbanIdx >= 0 && <td className="gcv-iban">{row[cpIbanIdx] ?? ''}</td>}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  )
}

// ── Page state ─────────────────────────────────────────────────────────────────

type MappedFile = { file: File; mapping: CsvMapping; detection: CsvDetectionResult }
type PendingFile = { file: File; detection: CsvDetectionResult }

type PageState =
  | { mode: 'idle' }
  | { mode: 'camt-loading' }
  | { mode: 'camt-preview'; rows: RawPreviewRow[]; accounts: CamtAccountInfo[]; accountBalances: Record<string, CamtAccountBalance> }
  | { mode: 'camt-imported'; importedCount: number; ignoredCount: number }
  | { mode: 'csv-detecting' }
  | { mode: 'csv-mapping'; detection: CsvDetectionResult; mapping: CsvMapping; file: File; mappedFiles: MappedFile[]; pendingFiles: PendingFile[] }
  | { mode: 'csv-previewing'; mappedFiles: MappedFile[] }
  | { mode: 'csv-categorizing'; rows: GenericCsvPreviewRow[]; mappedFiles: MappedFile[] }
  | { mode: 'csv-importing'; rows: GenericCsvPreviewRow[]; mappedFiles: MappedFile[] }
  | { mode: 'csv-success'; count: number }

export default function ImportPage() {
  const { t } = useTranslation()
  const [state, setState] = useState<PageState>({ mode: 'idle' })

  const [camtDragging, setCamtDragging] = useState(false)
  const [csvDragging, setCsvDragging] = useState(false)
  const [camtError, setCamtError] = useState<string | null>(null)
  const [csvError, setCsvError] = useState<string | null>(null)

  const [camtDecisions, setCamtDecisions] = useState<Record<number, ImportDecision>>({})
  const [accountNames, setAccountNames] = useState<Record<string, string>>({})
  const [camtImporting, setCamtImporting] = useState(false)
  const [camtFilters, setCamtFilters] = useState<Set<string>>(
    () => new Set(['NEW', 'PREVIOUSLY_IGNORED', 'DUPLICATE', 'INVALID', 'UNKNOWN_ACCOUNT']),
  )

  const [csvDecisions, setCsvDecisions] = useState<Record<number, ImportDecision>>({})
  const [csvFilters, setCsvFilters] = useState<Set<string>>(
    () => new Set(['NEW', 'DUPLICATE', 'UNKNOWN_ACCOUNT', 'IGNORED']),
  )

  const camtInputRef = useRef<HTMLInputElement>(null)
  const csvInputRef = useRef<HTMLInputElement>(null)
  const sessionMappings = useRef(new Map<string, CsvMapping>())

  const resetToIdle = () => {
    setState({ mode: 'idle' })
    setCamtError(null)
    setCsvError(null)
  }

  // ── CAMT handlers ────────────────────────────────────────────────────────────

  const handleCamtFiles = useCallback(async (files: File[]) => {
    if (files.length === 0) return
    setCamtError(null)
    setState({ mode: 'camt-loading' })
    try {
      const result = await previewCamtImport(files)
      const { rows, accounts, accountBalances } = result
      if (rows.length === 0) {
        setState({ mode: 'idle' })
        setCamtError(t('camtImport.noRows'))
        return
      }
      const names: Record<string, string> = {}
      for (const acc of accounts) {
        names[acc.iban] = acc.suggestedName
      }
      setState({ mode: 'camt-preview', rows, accounts, accountBalances })
      setCamtDecisions(initCamtDecisions(rows))
      setAccountNames(names)
      setCamtFilters(defaultCamtFilters(rows))
    } catch (e) {
      setState({ mode: 'idle' })
      setCamtError(e instanceof Error ? e.message : 'Preview failed')
    }
  }, [t])

  const handleCamtDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setCamtDragging(false)
    const all = Array.from(e.dataTransfer.files)
    if (all.some(f => !f.name.toLowerCase().endsWith('.xml'))) {
      setCamtError(t('import.errorCamtType'))
      return
    }
    setCamtError(null)
    if (all.length > 0) handleCamtFiles(all)
  }, [handleCamtFiles, t])

  const handleCamtImport = async () => {
    if (state.mode !== 'camt-preview') return
    const { rows, accountBalances } = state

    const toImport = rows
      .filter(r => r.status === 'NEW' || r.status === 'PREVIOUSLY_IGNORED')
      .flatMap(r => {
        const d = camtDecisions[r.rowNumber]
        if (d?.action !== 'import') return []
        return [{
          fingerprint: r.fingerprint!,
          bookingDate: r.bookingDate!,
          valueDate: r.valueDate!,
          amount: r.amount!,
          currency: r.currency,
          category: '',
          subcategory: null,
          group: '',
          accountIban: r.accountIban,
          purpose: r.purpose || null,
          counterpartyName: r.counterparty ?? null,
          counterpartyIban: r.counterpartyIban ?? null,
          sourceFilename: r.sourceFilename ?? null,
        }]
      })

    const toIgnore = rows
      .filter(r => r.status === 'NEW' && camtDecisions[r.rowNumber]?.action === 'ignore')
      .map(r => r.fingerprint!)

    setCamtImporting(true)
    try {
      const result = await importCamt({ accountNames, toImport, toIgnore, toEnrich: [], accountBalances })
      setState({ mode: 'camt-imported', importedCount: result.importedCount, ignoredCount: toIgnore.length })
    } catch (e) {
      setState({ mode: 'idle' })
      setCamtError(e instanceof Error ? e.message : 'Import failed')
    } finally {
      setCamtImporting(false)
    }
  }

  const camtCanImport = (rows: RawPreviewRow[]) => {
    const readyToImport = rows.filter(r =>
      (r.status === 'NEW' || r.status === 'PREVIOUSLY_IGNORED') && camtDecisions[r.rowNumber]?.action === 'import',
    )
    const toIgnore = rows.filter(r => r.status === 'NEW' && camtDecisions[r.rowNumber]?.action === 'ignore')
    return readyToImport.length > 0 || toIgnore.length > 0
  }

  const toggleCamtFilter = (key: string) => {
    setCamtFilters(prev => {
      if (prev.has(key) && prev.size === 1) return prev
      const next = new Set(prev)
      if (next.has(key)) next.delete(key); else next.add(key)
      return next
    })
  }

  // ── CSV handlers ─────────────────────────────────────────────────────────────

  const startCsvPreview = useCallback(async (mappedFiles: MappedFile[]) => {
    setState({ mode: 'csv-previewing', mappedFiles })
    try {
      const groups = new Map<string, { files: File[]; mapping: CsvMapping }>()
      for (const { file, mapping } of mappedFiles) {
        const key = JSON.stringify(mapping)
        if (!groups.has(key)) groups.set(key, { files: [], mapping })
        groups.get(key)!.files.push(file)
      }

      const allRows: GenericCsvPreviewRow[] = []
      let nextRowIndex = 0
      for (const { files, mapping } of groups.values()) {
        const rows = await previewGenericCsv(files, mapping)
        for (const row of rows) {
          allRows.push({ ...row, rowIndex: nextRowIndex++ })
        }
      }

      const initialDecisions: Record<number, ImportDecision> = {}
      allRows.forEach(r => {
        initialDecisions[r.rowIndex] = r.status === 'DUPLICATE' ? { action: 'ignore' } : { action: 'import' }
      })
      setCsvDecisions(initialDecisions)
      setState({ mode: 'csv-categorizing', rows: allRows, mappedFiles })
      setCsvFilters(defaultCsvFilters(allRows))
    } catch (e) {
      setState({ mode: 'idle' })
      setCsvError(e instanceof Error ? e.message : 'Preview failed')
    }
  }, [])

  const handleCsvFiles = useCallback(async (files: File[]) => {
    if (files.length === 0) return
    setCsvError(null)
    setState({ mode: 'csv-detecting' })
    try {
      const detections = await Promise.all(files.map(f => detectCsvFormat(f)))
      const mappedFiles: MappedFile[] = []
      const pendingFiles: PendingFile[] = []
      for (let i = 0; i < files.length; i++) {
        const detection = detections[i]
        if (detection.savedMapping) {
          mappedFiles.push({ file: files[i], mapping: detection.savedMapping, detection })
        } else {
          pendingFiles.push({ file: files[i], detection })
        }
      }
      if (pendingFiles.length === 0) {
        await startCsvPreview(mappedFiles)
      } else {
        const [first, ...rest] = pendingFiles
        setState({
          mode: 'csv-mapping',
          detection: first.detection,
          mapping: buildInitialMapping(first.detection),
          file: first.file,
          mappedFiles,
          pendingFiles: rest,
        })
      }
    } catch (e) {
      setState({ mode: 'idle' })
      setCsvError(e instanceof Error ? e.message : 'Detection failed')
    }
  }, [startCsvPreview])

  const handleCsvDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setCsvDragging(false)
    const all = Array.from(e.dataTransfer.files)
    if (all.length === 0) return
    if (all.some(f => !f.name.toLowerCase().endsWith('.csv') && !f.name.toLowerCase().endsWith('.txt'))) {
      setCsvError(t('import.errorCsvType'))
      return
    }
    setCsvError(null)
    void handleCsvFiles(all)
  }, [handleCsvFiles, t])

  const handleCsvConfirm = async (
    detection: CsvDetectionResult,
    mapping: CsvMapping,
    file: File,
    mappedFiles: MappedFile[],
    pendingFiles: PendingFile[],
  ) => {
    sessionMappings.current.set(detection.fingerprint, mapping)
    const newMappedFiles = [...mappedFiles, { file, mapping, detection }]
    if (pendingFiles.length === 0) {
      await startCsvPreview(newMappedFiles)
    } else {
      const [first, ...rest] = pendingFiles
      const sessionMapping = sessionMappings.current.get(first.detection.fingerprint)
      const initialMapping = sessionMapping ?? buildInitialMapping(first.detection)
      setState({
        mode: 'csv-mapping',
        detection: first.detection,
        mapping: initialMapping,
        file: first.file,
        mappedFiles: newMappedFiles,
        pendingFiles: rest,
      })
    }
  }

  const handleCsvImportRows = async (rows: GenericCsvPreviewRow[], mappedFiles: MappedFile[]) => {
    const toImport: GenericRowToImport[] = rows
      .filter(r => {
        if (r.status === 'DUPLICATE' || r.unknownAccount) return false
        return csvDecisions[r.rowIndex]?.action === 'import'
      })
      .map(r => ({
        date: r.date,
        amount: r.amount,
        currency: r.currency,
        accountIban: r.accountIban,
        purpose: r.purpose,
        category: '',
        subcategory: null,
        group: '',
        counterpartyName: r.counterpartyName,
        counterpartyIban: r.counterpartyIban,
      }))

    setState({ mode: 'csv-importing', rows, mappedFiles })
    try {
      const count = await importGenericRows(toImport, [])
      setState({ mode: 'csv-success', count })
    } catch (e) {
      setState({ mode: 'idle' })
      setCsvError(e instanceof Error ? e.message : 'Import failed')
    }
  }

  const toggleCsvFilter = (key: string) => {
    setCsvFilters(prev => {
      if (prev.has(key) && prev.size === 1) return prev
      const next = new Set(prev)
      if (next.has(key)) next.delete(key); else next.add(key)
      return next
    })
  }

  // ── Success screens ──────────────────────────────────────────────────────────

  if (state.mode === 'camt-imported') {
    const parts: string[] = []
    if (state.importedCount > 0) parts.push(t('camtImport.success.imported', { count: state.importedCount }))
    if (state.ignoredCount > 0) parts.push(t('camtImport.success.ignored', { count: state.ignoredCount }))
    return (
      <div className="flex flex-col h-full items-center justify-center gap-4 text-center p-8">
        <p className="text-green-500 font-medium text-lg">{parts.join(' · ')}</p>
        <button className="rounded-lg border border-input bg-input/30 px-4 py-2 text-sm hover:bg-input/50" onClick={resetToIdle}>{t('camtImport.success.importMore')}</button>
      </div>
    )
  }

  if (state.mode === 'csv-success') {
    return (
      <div className="flex flex-col h-full items-center justify-center gap-4 text-center p-8">
        <p className="text-green-500 font-medium text-lg">
          {state.count > 0
            ? t('csvImport.success.imported', { count: state.count })
            : t('csvImport.success.none')}
        </p>
        <button className="rounded-lg border border-input bg-input/30 px-4 py-2 text-sm hover:bg-input/50" onClick={resetToIdle}>{t('csvImport.success.importMore')}</button>
      </div>
    )
  }

  // ── CSV mapping ──────────────────────────────────────────────────────────────

  if (state.mode === 'csv-mapping' || state.mode === 'csv-previewing') {
    if (state.mode === 'csv-mapping') {
      const { detection, mapping, file, mappedFiles, pendingFiles } = state
      const totalFiles = mappedFiles.length + pendingFiles.length + 1
      return (
        <MappingView
          detection={detection}
          mapping={mapping}
          file={file}
          onChange={m => setState({ mode: 'csv-mapping', detection, mapping: m, file, mappedFiles, pendingFiles })}
          onConfirm={() => void handleCsvConfirm(detection, mapping, file, mappedFiles, pendingFiles)}
          onCancel={resetToIdle}
          importing={false}
          fileProgress={totalFiles > 1 ? { current: mappedFiles.length + 1, total: totalFiles } : undefined}
          sessionSuggested={sessionMappings.current.has(detection.fingerprint)}
        />
      )
    }
    // csv-previewing: loading state while fetching preview
    return (
      <div className="flex h-full items-center justify-center p-8">
        <div className="flex flex-col items-center justify-center gap-3 rounded-2xl border-2 border-dashed border-border p-16 text-center w-full max-w-lg">
          <FileSpreadsheet className="h-10 w-10 text-muted-foreground" />
          <span className="text-base font-medium">{t('csvImport.dropzone.analyzing')}</span>
          <span className="text-sm text-muted-foreground">{t('csvImport.dropzone.analyzingHint')}</span>
        </div>
      </div>
    )
  }

  // ── CSV categorizing ─────────────────────────────────────────────────────────

  if (state.mode === 'csv-categorizing' || state.mode === 'csv-importing') {
    const { rows, mappedFiles } = state
    const importing = state.mode === 'csv-importing'

    const duplicateCount = rows.filter(r => r.status === 'DUPLICATE').length
    const unknownAccountCount = rows.filter(r => r.status !== 'DUPLICATE' && r.unknownAccount).length
    const readyCount = rows.filter(r => {
      if (r.status === 'DUPLICATE' || r.unknownAccount) return false
      return csvDecisions[r.rowIndex]?.action === 'import'
    }).length
    const ignoredCount = rows.filter(r => r.status !== 'DUPLICATE' && !r.unknownAccount && csvDecisions[r.rowIndex]?.action === 'ignore').length

    const previewRows: ImportPreviewRow[] = rows.map(r => ({
      key: r.rowIndex,
      status: r.status === 'DUPLICATE' ? 'DUPLICATE' : 'NEW',
      unknownAccount: r.unknownAccount,
      date: r.date,
      accountDisplay: r.accountIban,
      accountIban: r.accountIban,
      counterparty: r.counterpartyName || null,
      purpose: r.purpose || null,
      amount: r.amount,
      currency: r.currency,
      errors: [],
      fingerprint: r.fingerprint,
    }))

    const filteredPreviewRows = previewRows.filter(r => {
      if (r.unknownAccount && r.status !== 'DUPLICATE') return csvFilters.has('UNKNOWN_ACCOUNT')
      if (r.status === 'DUPLICATE') return csvFilters.has('DUPLICATE')
      if (csvDecisions[r.key]?.action === 'ignore') return csvFilters.has('IGNORED')
      return csvFilters.has('NEW')
    })

    return (
      <div className="ri-page">
        <div className="ri-preview">
          <div className="ri-summary-bar">
            <button
              className={`ri-chip ri-chip--new${csvFilters.has('NEW') ? ' ri-chip--active' : ''}`}
              onClick={() => toggleCsvFilter('NEW')}
            >
              {t('import.chips.new', { count: rows.length - duplicateCount - unknownAccountCount })}
            </button>
            {duplicateCount > 0 && (
              <button
                className={`ri-chip ri-chip--dup${csvFilters.has('DUPLICATE') ? ' ri-chip--active' : ''}`}
                onClick={() => toggleCsvFilter('DUPLICATE')}
              >
                {t('import.chips.duplicate', { count: duplicateCount })}
              </button>
            )}
            {unknownAccountCount > 0 && (
              <button
                className={`ri-chip ri-chip--inv${csvFilters.has('UNKNOWN_ACCOUNT') ? ' ri-chip--active' : ''}`}
                onClick={() => toggleCsvFilter('UNKNOWN_ACCOUNT')}
              >
                {t('import.chips.excluded', { count: unknownAccountCount })}
              </button>
            )}
            {ignoredCount > 0 && (
              <button
                className={`ri-chip ri-chip--prev-ignored${csvFilters.has('IGNORED') ? ' ri-chip--active' : ''}`}
                onClick={() => toggleCsvFilter('IGNORED')}
              >
                {t('csvImport.categorizing.skipped', { count: ignoredCount })}
              </button>
            )}
            <span className="ri-summary-spacer" />
            <button className="load-btn" onClick={() => {
              if (mappedFiles.length === 1) {
                const { file, mapping, detection } = mappedFiles[0]
                setState({ mode: 'csv-mapping', detection, mapping, file, mappedFiles: [], pendingFiles: [] })
              } else {
                resetToIdle()
              }
            }} disabled={importing}>{t('csvImport.categorizing.back')}</button>
            <button
              className="load-btn ri-import-btn"
              disabled={readyCount === 0 || importing}
              onClick={() => void handleCsvImportRows(rows, mappedFiles)}
            >
              {importing ? '…' : t('csvImport.categorizing.importCount', { count: readyCount })}
            </button>
          </div>
          <ImportPreviewTable
            rows={filteredPreviewRows}
            decisions={csvDecisions}
            onDecide={(key, d) => setCsvDecisions(prev => ({ ...prev, [key]: d }))}
          />
        </div>
      </div>
    )
  }

  // ── CAMT preview ─────────────────────────────────────────────────────────────

  if (state.mode === 'camt-preview') {
    const { rows } = state
    const nNew = rows.filter(r => r.status === 'NEW' && !r.unknownAccount).length
    const nDup = rows.filter(r => r.status === 'DUPLICATE').length
    const nInv = rows.filter(r => r.status === 'INVALID').length
    const nIgn = rows.filter(r => r.status === 'PREVIOUSLY_IGNORED').length
    const nUnknown = rows.filter(r => r.unknownAccount && r.status !== 'DUPLICATE').length

    const adaptedRows = rows.map(r => adaptCamtRow(r, accountNames))
    const filteredRows = adaptedRows.filter(r => {
      if (r.unknownAccount && r.status !== 'DUPLICATE') return camtFilters.has('UNKNOWN_ACCOUNT')
      if (r.status === 'NEW') return camtFilters.has('NEW')
      return camtFilters.has(r.status)
    })

    return (
      <div className="ri-page">
        <div className="ri-preview">
          <div className="ri-summary-bar">
            {nNew > 0 && (
              <button className={`ri-chip ri-chip--new${camtFilters.has('NEW') ? ' ri-chip--active' : ''}`} onClick={() => toggleCamtFilter('NEW')}>
                {t('import.chips.new', { count: nNew })}
              </button>
            )}
            {nIgn > 0 && (
              <button className={`ri-chip ri-chip--prev-ignored${camtFilters.has('PREVIOUSLY_IGNORED') ? ' ri-chip--active' : ''}`} onClick={() => toggleCamtFilter('PREVIOUSLY_IGNORED')}>
                {t('camtImport.chips.previouslyIgnored', { count: nIgn })}
              </button>
            )}
            {nDup > 0 && (
              <button className={`ri-chip ri-chip--dup${camtFilters.has('DUPLICATE') ? ' ri-chip--active' : ''}`} onClick={() => toggleCamtFilter('DUPLICATE')}>
                {t('import.chips.duplicate', { count: nDup })}
              </button>
            )}
            {nInv > 0 && (
              <button className={`ri-chip ri-chip--inv${camtFilters.has('INVALID') ? ' ri-chip--active' : ''}`} onClick={() => toggleCamtFilter('INVALID')}>
                {t('camtImport.chips.invalid', { count: nInv })}
              </button>
            )}
            {nUnknown > 0 && (
              <button className={`ri-chip ri-chip--inv${camtFilters.has('UNKNOWN_ACCOUNT') ? ' ri-chip--active' : ''}`} onClick={() => toggleCamtFilter('UNKNOWN_ACCOUNT')}>
                {t('import.chips.excluded', { count: nUnknown })}
              </button>
            )}
            <span className="ri-summary-spacer" />
            <button className="load-btn" onClick={resetToIdle}>{t('camtImport.back')}</button>
            <button
              className="load-btn ri-import-btn"
              onClick={handleCamtImport}
              disabled={!camtCanImport(rows) || camtImporting}
            >
              {camtImporting ? '…' : t('camtImport.confirm')}
            </button>
          </div>
          <ImportPreviewTable
            rows={filteredRows}
            decisions={camtDecisions}
            onDecide={(key, d) => setCamtDecisions(prev => ({ ...prev, [key]: d }))}
          />
        </div>
      </div>
    )
  }

  // ── Loading states ───────────────────────────────────────────────────────────

  if (state.mode === 'camt-loading') {
    return <p className="hint loading">{t('camtImport.parsing')}</p>
  }

  if (state.mode === 'csv-detecting') {
    return (
      <div className="flex h-full items-center justify-center p-8">
        <div className="flex flex-col items-center justify-center gap-3 rounded-2xl border-2 border-dashed border-border p-16 text-center w-full max-w-lg">
          <FileSpreadsheet className="h-10 w-10 text-muted-foreground" />
          <span className="text-base font-medium">{t('csvImport.dropzone.analyzing')}</span>
          <span className="text-sm text-muted-foreground">{t('csvImport.dropzone.analyzingHint')}</span>
        </div>
      </div>
    )
  }


  // ── Idle: two drop zones ─────────────────────────────────────────────────────

  return (
    <div className="flex h-full items-center justify-center p-8 gap-6">
      <div className="flex-1 max-w-md">
        <div
          className={`flex flex-col items-center justify-center gap-3 rounded-2xl border-2 border-dashed p-16 text-center cursor-pointer transition-colors ${camtDragging ? 'border-primary bg-primary/5' : 'border-border hover:border-primary/50'}`}
          onDragOver={e => { e.preventDefault(); setCamtDragging(true) }}
          onDragLeave={() => setCamtDragging(false)}
          onDrop={handleCamtDrop}
          onClick={() => camtInputRef.current?.click()}
        >
          <input
            ref={camtInputRef}
            type="file"
            accept=".xml"
            multiple
            style={{ display: 'none' }}
            onChange={e => {
              const files = Array.from(e.target.files ?? [])
              if (files.length > 0) handleCamtFiles(files)
              e.target.value = ''
            }}
          />
          <FileCode className="h-10 w-10 text-muted-foreground" />
          <span className="text-base font-medium">{t('camtImport.dropzone.label')}</span>
          <span className="text-sm text-muted-foreground">{t('camtImport.dropzone.hint')}</span>
          {camtError && <span className="text-sm text-destructive">{camtError}</span>}
        </div>
      </div>

      <div className="self-stretch w-px bg-border" />

      <div className="flex-1 max-w-md">
        <div
          className={`flex flex-col items-center justify-center gap-3 rounded-2xl border-2 border-dashed p-16 text-center cursor-pointer transition-colors ${csvDragging ? 'border-primary bg-primary/5' : 'border-border hover:border-primary/50'}`}
          onDragOver={e => { e.preventDefault(); setCsvDragging(true) }}
          onDragLeave={() => setCsvDragging(false)}
          onDrop={handleCsvDrop}
          onClick={() => csvInputRef.current?.click()}
        >
          <input
            ref={csvInputRef}
            type="file"
            accept=".csv,.txt"
            multiple
            style={{ display: 'none' }}
            onChange={e => {
              const files = Array.from(e.target.files ?? [])
              if (files.length > 0) void handleCsvFiles(files)
              e.target.value = ''
            }}
          />
          <FileSpreadsheet className="h-10 w-10 text-muted-foreground" />
          <span className="text-base font-medium">{t('csvImport.dropzone.label')}</span>
          <span className="text-sm text-muted-foreground">{t('csvImport.dropzone.hint')}</span>
          {csvError && <span className="text-sm text-destructive">{csvError}</span>}
        </div>
      </div>
    </div>
  )
}
