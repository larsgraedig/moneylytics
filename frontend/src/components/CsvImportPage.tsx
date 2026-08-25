import { useCallback, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
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

const COMMON_DATE_FORMATS = [
  'dd.MM.yyyy',
  'yyyy-MM-dd',
  'dd/MM/yyyy',
  'MM/dd/yyyy',
  'dd.MM.yy',
]

type Phase =
  | { step: 'upload' }
  | { step: 'detecting' }
  | { step: 'mapping'; detection: CsvDetectionResult; mapping: CsvMapping; file: File }
  | { step: 'previewing'; detection: CsvDetectionResult; mapping: CsvMapping; file: File }
  | { step: 'categorizing'; rows: GenericCsvPreviewRow[]; detection: CsvDetectionResult; mapping: CsvMapping; file: File }
  | { step: 'importing-rows'; rows: GenericCsvPreviewRow[]; detection: CsvDetectionResult; mapping: CsvMapping; file: File }
  | { step: 'success'; count: number }
  | { step: 'error'; message: string }

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

function MappingView({
  detection,
  mapping,
  file,
  onChange,
  onConfirm,
  onCancel,
  importing,
}: {
  detection: CsvDetectionResult
  mapping: CsvMapping
  file: File
  onChange: (m: CsvMapping) => void
  onConfirm: () => void
  onCancel: () => void
  importing: boolean
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
          </div>
          <div className="gcv-subtitle">{file.name} · {t('csvImport.mapping.delimiter')}: <code>{mapping.delimiter === '\t' ? 'Tab' : mapping.delimiter}</code></div>
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

export default function CsvImportPage() {
  const { t } = useTranslation()
  const [phase, setPhase] = useState<Phase>({ step: 'upload' })
  const [isDragging, setIsDragging] = useState(false)
  const [decisions, setDecisions] = useState<Record<number, ImportDecision>>({})
  const [filter, setFilter] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleFile = useCallback(async (file: File) => {
    setPhase({ step: 'detecting' })
    try {
      const detection = await detectCsvFormat(file)
      const mapping = buildInitialMapping(detection)
      setPhase({ step: 'mapping', detection, mapping, file })
    } catch (e) {
      setPhase({ step: 'error', message: e instanceof Error ? e.message : 'Detection failed' })
    }
  }, [])

  async function handleConfirm(detection: CsvDetectionResult, mapping: CsvMapping, file: File) {
    setPhase({ step: 'previewing', detection, mapping, file })
    try {
      const rows = await previewGenericCsv(file, mapping)
      const initialDecisions: Record<number, ImportDecision> = {}
      rows.forEach(r => {
        initialDecisions[r.rowIndex] = r.status === 'DUPLICATE' ? { action: 'ignore' } : { action: 'import' }
      })
      setDecisions(initialDecisions)
      setPhase({ step: 'categorizing', rows, detection, mapping, file })
    } catch (e) {
      setPhase({ step: 'error', message: e instanceof Error ? e.message : 'Preview failed' })
    }
  }

  async function handleImportRows(rows: GenericCsvPreviewRow[], detection: CsvDetectionResult, mapping: CsvMapping, file: File) {
    const toImport: GenericRowToImport[] = rows
      .filter(r => {
        if (r.status === 'DUPLICATE' || r.unknownAccount) return false
        const d = decisions[r.rowIndex]
        return d?.action === 'import'
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

    setPhase({ step: 'importing-rows', rows, detection, mapping, file })
    try {
      const count = await importGenericRows(toImport, [])
      setPhase({ step: 'success', count })
    } catch (e) {
      setPhase({ step: 'error', message: e instanceof Error ? e.message : 'Import failed' })
    }
  }

  if (phase.step === 'success') {
    return (
      <div className="flex flex-col h-full items-center justify-center gap-4 text-center p-8">
        <p className="text-green-500 font-medium text-lg">
          {phase.count > 0
            ? t('csvImport.success.imported', { count: phase.count })
            : t('csvImport.success.none')}
        </p>
        <button className="rounded-lg border border-input bg-input/30 px-4 py-2 text-sm hover:bg-input/50" onClick={() => setPhase({ step: 'upload' })}>{t('csvImport.success.importMore')}</button>
      </div>
    )
  }

  if (phase.step === 'mapping' || phase.step === 'previewing') {
    const { detection, mapping, file } = phase
    return (
      <MappingView
        detection={detection}
        mapping={mapping}
        file={file}
        onChange={m => setPhase({ step: 'mapping', detection, mapping: m, file })}
        onConfirm={() => handleConfirm(detection, mapping, file)}
        onCancel={() => setPhase({ step: 'upload' })}
        importing={phase.step === 'previewing'}
      />
    )
  }

  if (phase.step === 'categorizing' || phase.step === 'importing-rows') {
    const { rows, detection, mapping, file } = phase
    const importing = phase.step === 'importing-rows'

    const duplicateCount = rows.filter(r => r.status === 'DUPLICATE').length
    const unknownAccountCount = rows.filter(r => r.status !== 'DUPLICATE' && r.unknownAccount).length
    const readyCount = rows.filter(r => {
      if (r.status === 'DUPLICATE' || r.unknownAccount) return false
      return decisions[r.rowIndex]?.action === 'import'
    }).length
    const ignoredCount = rows.filter(r => r.status !== 'DUPLICATE' && !r.unknownAccount && decisions[r.rowIndex]?.action === 'ignore').length

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

    const filteredPreviewRows = filter == null ? previewRows
      : filter === 'DUPLICATE' ? previewRows.filter(r => r.status === 'DUPLICATE')
      : filter === 'UNKNOWN_ACCOUNT' ? previewRows.filter(r => r.unknownAccount && r.status !== 'DUPLICATE')
      : filter === 'IGNORED' ? previewRows.filter(r => decisions[r.key]?.action === 'ignore')
      : previewRows.filter(r => r.status !== 'DUPLICATE' && !r.unknownAccount)

    const toggleFilter = (key: string) => setFilter(f => f === key ? null : key)

    return (
      <div className="ri-page">
        <div className="ri-preview">
          <div className="ri-summary-bar">
            <button
              className={`ri-chip ri-chip--new${filter === 'NEW' ? ' ri-chip--active' : ''}`}
              onClick={() => toggleFilter('NEW')}
            >
              {t('csvImport.categorizing.new', { count: rows.length - duplicateCount - unknownAccountCount })}
            </button>
            {duplicateCount > 0 && (
              <button
                className={`ri-chip ri-chip--dup${filter === 'DUPLICATE' ? ' ri-chip--active' : ''}`}
                onClick={() => toggleFilter('DUPLICATE')}
              >
                {t('csvImport.categorizing.duplicate', { count: duplicateCount })}
              </button>
            )}
            {unknownAccountCount > 0 && (
              <button
                className={`ri-chip ri-chip--inv${filter === 'UNKNOWN_ACCOUNT' ? ' ri-chip--active' : ''}`}
                onClick={() => toggleFilter('UNKNOWN_ACCOUNT')}
              >
                {t('csvImport.categorizing.excluded', { count: unknownAccountCount })}
              </button>
            )}
            {ignoredCount > 0 && (
              <button
                className={`ri-chip ri-chip--prev-ignored${filter === 'IGNORED' ? ' ri-chip--active' : ''}`}
                onClick={() => toggleFilter('IGNORED')}
              >
                {t('csvImport.categorizing.skipped', { count: ignoredCount })}
              </button>
            )}
            <span className="ri-summary-spacer" />
            <button className="load-btn" onClick={() => setPhase({ step: 'mapping', detection, mapping, file })} disabled={importing}>{t('csvImport.categorizing.back')}</button>
            <button
              className="load-btn ri-import-btn"
              disabled={readyCount === 0 || importing}
              onClick={() => handleImportRows(rows, detection, mapping, file)}
            >
              {importing ? '…' : t('csvImport.categorizing.importCount', { count: readyCount })}
            </button>
          </div>
          <ImportPreviewTable
            rows={filteredPreviewRows}
            decisions={decisions}
            onDecide={(key, d) => setDecisions(prev => ({ ...prev, [key]: d }))}
          />
        </div>
      </div>
    )
  }

  const isLoading = phase.step === 'detecting'

  return (
    <div className="flex flex-col h-full items-center justify-center p-8">
      <div
        className={`flex flex-col items-center justify-center gap-3 rounded-2xl border-2 border-dashed p-16 text-center cursor-pointer transition-colors w-full max-w-lg ${isDragging ? 'border-primary bg-primary/5' : 'border-border hover:border-primary/50'}`}
        onDragOver={e => { e.preventDefault(); setIsDragging(true) }}
        onDragLeave={() => setIsDragging(false)}
        onDrop={e => { e.preventDefault(); setIsDragging(false); const f = e.dataTransfer.files[0]; if (f) handleFile(f) }}
        onClick={() => !isLoading && fileInputRef.current?.click()}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept=".csv,.txt"
          style={{ display: 'none' }}
          onChange={e => { const f = e.target.files?.[0]; if (f) handleFile(f); e.target.value = '' }}
        />
        <span className="text-4xl">↑</span>
        <span className="text-base font-medium">
          {isLoading ? t('csvImport.dropzone.analyzing') : t('csvImport.dropzone.label')}
        </span>
        <span className="text-sm text-muted-foreground">
          {isLoading ? t('csvImport.dropzone.analyzingHint') : t('csvImport.dropzone.hint')}
        </span>
        {phase.step === 'error' && (
          <span className="text-sm text-destructive">{phase.message}</span>
        )}
      </div>
    </div>
  )
}
