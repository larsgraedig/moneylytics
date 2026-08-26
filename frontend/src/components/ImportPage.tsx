import { useCallback, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Upload } from 'lucide-react'
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
  type MappingToSave,
} from '../api/genericCsvImport'
import { ImportPreviewTable, type ImportDecision, type ImportPreviewRow } from './ImportPreviewTable'

// ── Types ──────────────────────────────────────────────────────────────────────

type CombinedSource =
  | { type: 'csv'; csvRow: GenericCsvPreviewRow }
  | { type: 'camt'; camtRow: RawPreviewRow }

type CombinedPreviewRow = ImportPreviewRow & {
  source: CombinedSource
  sourceFilename: string | null
}

type MappedFile = { file: File; mapping: CsvMapping; detection: CsvDetectionResult }
type PendingFile = { file: File; detection: CsvDetectionResult }

type CamtState = {
  accounts: CamtAccountInfo[]
  accountBalances: Record<string, CamtAccountBalance>
}

type PageState =
  | { mode: 'idle' }
  | { mode: 'detecting' }
  | { mode: 'csv-mapping'; detection: CsvDetectionResult; mapping: CsvMapping; file: File; mappedFiles: MappedFile[]; pendingFiles: PendingFile[]; camtFiles: File[] }
  | { mode: 'combined-previewing' }
  | { mode: 'combined-preview'; rows: CombinedPreviewRow[]; camtState: CamtState | null; mappedFiles: MappedFile[] }
  | { mode: 'combined-success'; importedCount: number }

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

// ── Combined preview helpers ───────────────────────────────────────────────────

function markCrossFileDuplicates(rows: CombinedPreviewRow[]): CombinedPreviewRow[] {
  const seen = new Set<string>()
  return rows.map(row => {
    if (!row.fingerprint || row.status !== 'NEW') return row
    if (seen.has(row.fingerprint)) return { ...row, status: 'CROSS_FILE_DUPLICATE' as const }
    seen.add(row.fingerprint)
    return row
  })
}

function defaultCombinedFilters(rows: CombinedPreviewRow[]): Set<string> {
  if (rows.some(r => r.status === 'NEW' && !r.unknownAccount)) return new Set(['NEW'])
  if (rows.some(r => r.status === 'PREVIOUSLY_IGNORED')) return new Set(['PREVIOUSLY_IGNORED'])
  if (rows.some(r => r.status === 'DUPLICATE')) return new Set(['DUPLICATE'])
  return new Set(['UNKNOWN_ACCOUNT'])
}

function rowMatchesFilter(
  row: CombinedPreviewRow,
  filters: Set<string>,
  decisions: Record<number, ImportDecision>,
): boolean {
  if (row.unknownAccount && row.status !== 'DUPLICATE') return filters.has('UNKNOWN_ACCOUNT')
  if (row.status === 'INVALID') return filters.has('INVALID')
  if (row.status === 'DUPLICATE') return filters.has('DUPLICATE')
  if (row.status === 'CROSS_FILE_DUPLICATE') return filters.has('CROSS_FILE_DUPLICATE')
  if (row.status === 'PREVIOUSLY_IGNORED') return filters.has('PREVIOUSLY_IGNORED')
  if (decisions[row.key]?.action === 'ignore') return filters.has('IGNORED')
  return filters.has('NEW')
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
  const [savedMappingDecided, setSavedMappingDecided] = useState(false)
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
            {detection.savedMapping && !savedMappingDecided && (
              <span className="gcv-saved-badge">{t('csvImport.mapping.savedBadge')}</span>
            )}
            {sessionSuggested && !detection.savedMapping && savedMappingDecided && (
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

      {(detection.savedMapping != null || sessionSuggested === true) && !savedMappingDecided ? (
        <div className="gcv-body" style={{ alignItems: 'center', justifyContent: 'center' }}>
          <div style={{ maxWidth: 480, textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 16 }}>
            <p style={{ margin: 0 }}>
              {detection.savedMapping
                ? t('csvImport.mapping.savedPrompt')
                : t('csvImport.mapping.sessionPrompt')}
            </p>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'center' }}>
              <button className="gcv-confirm-btn" onClick={onConfirm}>{t('csvImport.mapping.savedPromptAccept')}</button>
              <button className="gcv-cancel-btn" onClick={() => {
                onChange(buildInitialMapping({ ...detection, savedMapping: null }))
                setSavedMappingDecided(true)
              }}>{t('csvImport.mapping.savedPromptReject')}</button>
            </div>
          </div>
        </div>
      ) : (
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
      )}
    </div>
  )
}

// ── Main component ─────────────────────────────────────────────────────────────

export default function ImportPage() {
  const { t } = useTranslation()
  const [state, setState] = useState<PageState>({ mode: 'idle' })

  const [dragging, setDragging] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [decisions, setDecisions] = useState<Record<number, ImportDecision>>({})
  const [filters, setFilters] = useState<Set<string>>(
    () => new Set(['NEW', 'PREVIOUSLY_IGNORED', 'DUPLICATE', 'CROSS_FILE_DUPLICATE', 'INVALID', 'UNKNOWN_ACCOUNT', 'IGNORED']),
  )
  const [accountNames, setAccountNames] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  const inputRef = useRef<HTMLInputElement>(null)
  const sessionMappings = useRef(new Map<string, CsvMapping>())

  const resetToIdle = () => {
    setState({ mode: 'idle' })
    setError(null)
    setSubmitting(false)
  }

  // ── Combined preview assembly ─────────────────────────────────────────────────

  const startCombinedPreview = useCallback(async (mappedFiles: MappedFile[], camtFiles: File[]) => {
    setState({ mode: 'combined-previewing' })
    try {
      const groups: { files: File[]; mapping: CsvMapping }[] = []
      const mappingKeyToGroupIdx = new Map<string, number>()
      for (const { file, mapping } of mappedFiles) {
        const key = JSON.stringify(mapping)
        let idx = mappingKeyToGroupIdx.get(key)
        if (idx === undefined) {
          idx = groups.length
          mappingKeyToGroupIdx.set(key, idx)
          groups.push({ files: [], mapping })
        }
        groups[idx].files.push(file)
      }

      const [camtResult, ...csvResults] = await Promise.all([
        camtFiles.length > 0 ? previewCamtImport(camtFiles) : Promise.resolve(null),
        ...groups.map(g => previewGenericCsv(g.files, g.mapping)),
      ])

      const combinedRows: CombinedPreviewRow[] = []
      let nextKey = 0

      if (camtResult) {
        const names: Record<string, string> = {}
        for (const acc of camtResult.accounts) {
          names[acc.iban] = acc.suggestedName
        }
        setAccountNames(names)
        for (const camtRow of camtResult.rows) {
          combinedRows.push({
            ...adaptCamtRow(camtRow, names),
            key: nextKey++,
            source: { type: 'camt', camtRow },
            sourceFilename: camtRow.sourceFilename,
          })
        }
      }

      for (const csvRows of csvResults) {
        for (const csvRow of csvRows) {
          combinedRows.push({
            key: nextKey++,
            status: csvRow.status === 'DUPLICATE' ? 'DUPLICATE' : 'NEW',
            unknownAccount: csvRow.unknownAccount,
            date: csvRow.date,
            accountDisplay: csvRow.accountIban,
            accountIban: csvRow.accountIban,
            counterparty: csvRow.counterpartyName || null,
            purpose: csvRow.purpose || null,
            amount: csvRow.amount,
            currency: csvRow.currency,
            errors: [],
            fingerprint: csvRow.fingerprint,
            source: { type: 'csv', csvRow },
            sourceFilename: csvRow.sourceFilename,
          })
        }
      }

      const markedRows = markCrossFileDuplicates(combinedRows)

      const initialDecisions: Record<number, ImportDecision> = {}
      for (const row of markedRows) {
        if (row.unknownAccount || row.status === 'INVALID' || row.status === 'DUPLICATE' || row.status === 'CROSS_FILE_DUPLICATE') continue
        if (row.status === 'NEW') initialDecisions[row.key] = { action: 'import' }
        else if (row.status === 'PREVIOUSLY_IGNORED') initialDecisions[row.key] = { action: 'ignore' }
      }
      setDecisions(initialDecisions)
      setFilters(defaultCombinedFilters(markedRows))
      setState({
        mode: 'combined-preview',
        rows: markedRows,
        camtState: camtResult
          ? { accounts: camtResult.accounts, accountBalances: camtResult.accountBalances }
          : null,
        mappedFiles,
      })
    } catch (e) {
      setState({ mode: 'idle' })
      setError(e instanceof Error ? e.message : 'Preview failed')
    }
  }, [])

  // ── File dispatch ─────────────────────────────────────────────────────────────

  const handleFiles = useCallback(async (files: File[]) => {
    if (files.length === 0) return
    setError(null)

    const invalid = files.filter(f => {
      const n = f.name.toLowerCase()
      return !n.endsWith('.xml') && !n.endsWith('.csv') && !n.endsWith('.txt')
    })
    if (invalid.length > 0) {
      setError(t('import.dropzone.errorType'))
      return
    }

    const camtFiles = files.filter(f => f.name.toLowerCase().endsWith('.xml'))
    const csvFiles = files.filter(f => !f.name.toLowerCase().endsWith('.xml'))

    if (csvFiles.length === 0) {
      await startCombinedPreview([], camtFiles)
      return
    }

    setState({ mode: 'detecting' })
    try {
      const detections = await Promise.all(csvFiles.map(f => detectCsvFormat(f)))
      const pendingFiles: PendingFile[] = detections.map((detection, i) => ({ file: csvFiles[i], detection }))
      const [first, ...rest] = pendingFiles
      setState({
        mode: 'csv-mapping',
        detection: first.detection,
        mapping: buildInitialMapping(first.detection),
        file: first.file,
        mappedFiles: [],
        pendingFiles: rest,
        camtFiles,
      })
    } catch (e) {
      setState({ mode: 'idle' })
      setError(e instanceof Error ? e.message : 'Detection failed')
    }
  }, [t, startCombinedPreview])

  const handleCsvConfirm = async (
    detection: CsvDetectionResult,
    mapping: CsvMapping,
    file: File,
    mappedFiles: MappedFile[],
    pendingFiles: PendingFile[],
    camtFiles: File[],
  ) => {
    sessionMappings.current.set(detection.fingerprint, mapping)
    const newMappedFiles = [...mappedFiles, { file, mapping, detection }]
    if (pendingFiles.length === 0) {
      await startCombinedPreview(newMappedFiles, camtFiles)
    } else {
      const [first, ...rest] = pendingFiles
      const sessionMapping = sessionMappings.current.get(first.detection.fingerprint)
      setState({
        mode: 'csv-mapping',
        detection: first.detection,
        mapping: sessionMapping ?? buildInitialMapping(first.detection),
        file: first.file,
        mappedFiles: newMappedFiles,
        pendingFiles: rest,
        camtFiles,
      })
    }
  }

  // ── Import ────────────────────────────────────────────────────────────────────

  const handleCombinedImport = async () => {
    if (state.mode !== 'combined-preview') return
    const { rows, camtState, mappedFiles } = state

    setSubmitting(true)
    try {
      let totalImported = 0

      const csvRowsToImport: GenericRowToImport[] = rows
        .filter(r =>
          r.source.type === 'csv' &&
          !r.unknownAccount &&
          r.status !== 'DUPLICATE' &&
          r.status !== 'CROSS_FILE_DUPLICATE' &&
          decisions[r.key]?.action === 'import',
        )
        .map(r => {
          const { csvRow } = r.source as Extract<CombinedSource, { type: 'csv' }>
          return {
            date: csvRow.date,
            amount: csvRow.amount,
            currency: csvRow.currency,
            accountIban: csvRow.accountIban,
            purpose: csvRow.purpose,
            category: '',
            subcategory: null,
            group: '',
            counterpartyName: csvRow.counterpartyName,
            counterpartyIban: csvRow.counterpartyIban,
          }
        })

      if (csvRowsToImport.length > 0 || mappedFiles.length > 0) {
        const mappingsToSave: MappingToSave[] = mappedFiles.map(({ mapping, detection }) => ({
          fingerprint: detection.fingerprint,
          mapping,
        }))
        const csvCount = await importGenericRows(csvRowsToImport, [], mappingsToSave)
        totalImported += csvCount
      }

      if (camtState) {
        const camtRows = rows.filter(r => r.source.type === 'camt')

        const toImport = camtRows
          .filter(r => {
            const { camtRow } = r.source as Extract<CombinedSource, { type: 'camt' }>
            return (
              !r.unknownAccount &&
              (r.status === 'NEW' || r.status === 'PREVIOUSLY_IGNORED') &&
              decisions[r.key]?.action === 'import' &&
              (camtRow.status === 'NEW' || camtRow.status === 'PREVIOUSLY_IGNORED')
            )
          })
          .map(r => {
            const { camtRow } = r.source as Extract<CombinedSource, { type: 'camt' }>
            return {
              fingerprint: camtRow.fingerprint!,
              bookingDate: camtRow.bookingDate!,
              valueDate: camtRow.valueDate!,
              amount: camtRow.amount!,
              currency: camtRow.currency,
              category: '',
              subcategory: null,
              group: '',
              accountIban: camtRow.accountIban,
              purpose: camtRow.purpose || null,
              counterpartyName: camtRow.counterparty ?? null,
              counterpartyIban: camtRow.counterpartyIban ?? null,
              sourceFilename: camtRow.sourceFilename ?? null,
            }
          })

        const toIgnore = camtRows
          .filter(r => {
            const { camtRow } = r.source as Extract<CombinedSource, { type: 'camt' }>
            return (
              r.status === 'NEW' &&
              decisions[r.key]?.action === 'ignore' &&
              camtRow.status === 'NEW' &&
              !r.unknownAccount
            )
          })
          .map(r => (r.source as Extract<CombinedSource, { type: 'camt' }>).camtRow.fingerprint!)

        if (toImport.length > 0 || toIgnore.length > 0) {
          const camtResult = await importCamt({
            accountNames,
            toImport,
            toIgnore,
            toEnrich: [],
            accountBalances: camtState.accountBalances,
          })
          totalImported += camtResult.importedCount
        }
      }

      setState({ mode: 'combined-success', importedCount: totalImported })
    } catch (e) {
      setState({ mode: 'idle' })
      setError(e instanceof Error ? e.message : 'Import failed')
    } finally {
      setSubmitting(false)
    }
  }

  const toggleFilter = (key: string) => {
    setFilters(prev => {
      if (prev.has(key) && prev.size === 1) return prev
      const next = new Set(prev)
      if (next.has(key)) next.delete(key); else next.add(key)
      return next
    })
  }

  // ── Success screen ────────────────────────────────────────────────────────────

  if (state.mode === 'combined-success') {
    return (
      <div className="flex flex-col h-full items-center justify-center gap-4 text-center p-8">
        <p className="text-green-500 font-medium text-lg">
          {state.importedCount > 0
            ? t('import.success.imported', { count: state.importedCount })
            : t('csvImport.success.none')}
        </p>
        <button
          className="rounded-lg border border-input bg-input/30 px-4 py-2 text-sm hover:bg-input/50"
          onClick={resetToIdle}
        >
          {t('import.success.importMore')}
        </button>
      </div>
    )
  }

  // ── CSV mapping wizard ────────────────────────────────────────────────────────

  if (state.mode === 'csv-mapping') {
    const { detection, mapping, file, mappedFiles, pendingFiles, camtFiles } = state
    const totalFiles = mappedFiles.length + pendingFiles.length + 1
    return (
      <MappingView
        detection={detection}
        mapping={mapping}
        file={file}
        onChange={m => setState({ mode: 'csv-mapping', detection, mapping: m, file, mappedFiles, pendingFiles, camtFiles })}
        onConfirm={() => void handleCsvConfirm(detection, mapping, file, mappedFiles, pendingFiles, camtFiles)}
        onCancel={resetToIdle}
        importing={false}
        fileProgress={totalFiles > 1 ? { current: mappedFiles.length + 1, total: totalFiles } : undefined}
        sessionSuggested={sessionMappings.current.has(detection.fingerprint)}
      />
    )
  }

  // ── Loading ───────────────────────────────────────────────────────────────────

  if (state.mode === 'detecting' || state.mode === 'combined-previewing') {
    return (
      <div className="flex h-full items-center justify-center p-8">
        <div className="flex flex-col items-center justify-center gap-3 rounded-2xl border-2 border-dashed border-border p-16 text-center w-full max-w-lg">
          <Upload className="h-10 w-10 text-muted-foreground" />
          <span className="text-base font-medium">{t('camtImport.parsing')}</span>
        </div>
      </div>
    )
  }

  // ── Combined preview ──────────────────────────────────────────────────────────

  if (state.mode === 'combined-preview') {
    const { rows } = state
    const imp = submitting

    const nNew = rows.filter(r => r.status === 'NEW' && !r.unknownAccount).length
    const nPrevIgnored = rows.filter(r => r.status === 'PREVIOUSLY_IGNORED').length
    const nDup = rows.filter(r => r.status === 'DUPLICATE').length
    const nCrossFileDup = rows.filter(r => r.status === 'CROSS_FILE_DUPLICATE').length
    const nInvalid = rows.filter(r => r.status === 'INVALID').length
    const nUnknown = rows.filter(r => r.unknownAccount && r.status !== 'DUPLICATE').length
    const nIgnored = rows.filter(r =>
      r.status === 'NEW' && !r.unknownAccount && decisions[r.key]?.action === 'ignore',
    ).length

    const readyCount = rows.filter(r =>
      !r.unknownAccount &&
      (r.status === 'NEW' || r.status === 'PREVIOUSLY_IGNORED') &&
      decisions[r.key]?.action === 'import',
    ).length

    const camtHasIgnores = rows.some(r =>
      r.source.type === 'camt' &&
      r.status === 'NEW' &&
      decisions[r.key]?.action === 'ignore' &&
      !r.unknownAccount,
    )

    const filteredRows = rows.filter(r => rowMatchesFilter(r, filters, decisions))

    return (
      <div className="ri-page">
        <div className="ri-preview">
          <div className="ri-summary-bar">
            {nNew > 0 && (
              <button
                className={`ri-chip ri-chip--new${filters.has('NEW') ? ' ri-chip--active' : ''}`}
                onClick={() => toggleFilter('NEW')}
              >
                {t('import.chips.new', { count: nNew })}
              </button>
            )}
            {nPrevIgnored > 0 && (
              <button
                className={`ri-chip ri-chip--prev-ignored${filters.has('PREVIOUSLY_IGNORED') ? ' ri-chip--active' : ''}`}
                onClick={() => toggleFilter('PREVIOUSLY_IGNORED')}
              >
                {t('camtImport.chips.previouslyIgnored', { count: nPrevIgnored })}
              </button>
            )}
            {nDup > 0 && (
              <button
                className={`ri-chip ri-chip--dup${filters.has('DUPLICATE') ? ' ri-chip--active' : ''}`}
                onClick={() => toggleFilter('DUPLICATE')}
              >
                {t('import.chips.duplicate', { count: nDup })}
              </button>
            )}
            {nCrossFileDup > 0 && (
              <button
                className={`ri-chip ri-chip--dup${filters.has('CROSS_FILE_DUPLICATE') ? ' ri-chip--active' : ''}`}
                onClick={() => toggleFilter('CROSS_FILE_DUPLICATE')}
              >
                {t('import.chips.crossFileDuplicate', { count: nCrossFileDup })}
              </button>
            )}
            {nInvalid > 0 && (
              <button
                className={`ri-chip ri-chip--inv${filters.has('INVALID') ? ' ri-chip--active' : ''}`}
                onClick={() => toggleFilter('INVALID')}
              >
                {t('camtImport.chips.invalid', { count: nInvalid })}
              </button>
            )}
            {nUnknown > 0 && (
              <button
                className={`ri-chip ri-chip--inv${filters.has('UNKNOWN_ACCOUNT') ? ' ri-chip--active' : ''}`}
                onClick={() => toggleFilter('UNKNOWN_ACCOUNT')}
              >
                {t('import.chips.excluded', { count: nUnknown })}
              </button>
            )}
            {nIgnored > 0 && (
              <button
                className={`ri-chip ri-chip--prev-ignored${filters.has('IGNORED') ? ' ri-chip--active' : ''}`}
                onClick={() => toggleFilter('IGNORED')}
              >
                {t('csvImport.categorizing.skipped', { count: nIgnored })}
              </button>
            )}
            <span className="ri-summary-spacer" />
            <button className="load-btn" onClick={resetToIdle} disabled={imp}>
              {t('camtImport.back')}
            </button>
            <button
              className="load-btn ri-import-btn"
              disabled={readyCount === 0 && !camtHasIgnores || imp}
              onClick={() => void handleCombinedImport()}
            >
              {imp ? '…' : t('csvImport.categorizing.importCount', { count: readyCount })}
            </button>
          </div>
          <ImportPreviewTable
            rows={filteredRows}
            decisions={decisions}
            onDecide={(key, d) => setDecisions(prev => ({ ...prev, [key]: d }))}
          />
        </div>
      </div>
    )
  }

  // ── Idle: unified dropzone ────────────────────────────────────────────────────

  return (
    <div className="flex h-full items-center justify-center p-8">
      <div className="w-full max-w-lg">
        <div
          className={`flex flex-col items-center justify-center gap-3 rounded-2xl border-2 border-dashed p-16 text-center cursor-pointer transition-colors ${dragging ? 'border-primary bg-primary/5' : 'border-border hover:border-primary/50'}`}
          onDragOver={e => { e.preventDefault(); setDragging(true) }}
          onDragLeave={() => setDragging(false)}
          onDrop={e => {
            e.preventDefault()
            setDragging(false)
            const files = Array.from(e.dataTransfer.files)
            if (files.length > 0) void handleFiles(files)
          }}
          onClick={() => inputRef.current?.click()}
        >
          <input
            ref={inputRef}
            type="file"
            accept=".xml,.csv,.txt"
            multiple
            style={{ display: 'none' }}
            onChange={e => {
              const files = Array.from(e.target.files ?? [])
              if (files.length > 0) void handleFiles(files)
              e.target.value = ''
            }}
          />
          <Upload className="h-10 w-10 text-muted-foreground" />
          <span className="text-base font-medium">{t('import.dropzone.label')}</span>
          <span className="text-sm text-muted-foreground">{t('import.dropzone.hint')}</span>
          {error && <span className="text-sm text-destructive">{error}</span>}
        </div>
      </div>
    </div>
  )
}
