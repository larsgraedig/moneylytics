import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  detectCsvFormat,
  importGenericCsv,
  importGenericRows,
  previewGenericCsv,
  type AmountFormat,
  type CsvDetectionResult,
  type CsvMapping,
  type GenericCsvPreviewRow,
  type GenericRowToImport,
} from '../api/genericCsvImport'
import { fetchCamtCategories, type CategoryGroup } from '../api/camtImport'

const COMMON_DATE_FORMATS = [
  'dd.MM.yyyy',
  'yyyy-MM-dd',
  'dd/MM/yyyy',
  'MM/dd/yyyy',
  'dd.MM.yy',
]

type RowDecision = { action: 'import'; category: string; subcategory: string } | { action: 'skip' }

type Phase =
  | { step: 'upload' }
  | { step: 'detecting' }
  | { step: 'mapping'; detection: CsvDetectionResult; mapping: CsvMapping; file: File }
  | { step: 'previewing'; detection: CsvDetectionResult; mapping: CsvMapping; file: File }
  | { step: 'categorizing'; rows: GenericCsvPreviewRow[]; detection: CsvDetectionResult; mapping: CsvMapping; file: File }
  | { step: 'importing'; detection: CsvDetectionResult; mapping: CsvMapping; file: File }
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
    categoryColumn: d.suggestions.category ?? null,
    subcategoryColumn: d.suggestions.subcategory ?? null,
    accountIbanColumn: d.suggestions.accountIban ?? null,
    currencyColumn: d.suggestions.currency ?? null,
    fixedAccountIban: null,
    fixedCurrency: 'EUR',
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
  const catIdx = colIdx(headers, mapping.categoryColumn)
  const subIdx = colIdx(headers, mapping.subcategoryColumn)
  const ibanIdx = colIdx(headers, mapping.accountIbanColumn)
  const currIdx = colIdx(headers, mapping.currencyColumn)

  const canConfirm = mapping.dateColumn && mapping.amountColumn

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

          <div className="gcv-section-title" style={{ marginTop: 16 }}>{t('csvImport.mapping.optionalFields')}</div>
          <MappingRow label={t('csvImport.mapping.purpose')} headers={headers} value={mapping.purposeColumn} onChange={v => set('purposeColumn', v)} />
          <MappingRow label={t('csvImport.mapping.category')} headers={headers} value={mapping.categoryColumn} onChange={v => set('categoryColumn', v)} />
          <MappingRow label={t('csvImport.mapping.subcategory')} headers={headers} value={mapping.subcategoryColumn} onChange={v => set('subcategoryColumn', v)} />
          <MappingRow label={t('csvImport.mapping.accountIban')} headers={headers} value={mapping.accountIbanColumn} onChange={v => set('accountIbanColumn', v)} />
          {!mapping.accountIbanColumn && (
            <div className="gcv-map-row">
              <span className="gcv-map-label gcv-map-label--sub">{t('csvImport.mapping.fixedIban')}</span>
              <input
                className="gcv-map-input"
                placeholder={t('csvImport.mapping.fixedIbanPlaceholder')}
                value={mapping.fixedAccountIban ?? ''}
                onChange={e => set('fixedAccountIban', e.target.value || null)}
              />
            </div>
          )}
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
                  {catIdx >= 0 && <th>{t('csvImport.categorizing.columns.category')}</th>}
                  {subIdx >= 0 && <th>{t('csvImport.categorizing.columns.subcategory')}</th>}
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
                      {catIdx >= 0 && <td>{row[catIdx] ?? ''}</td>}
                      {subIdx >= 0 && <td>{row[subIdx] ?? ''}</td>}
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

const needsPreview = (m: CsvMapping, rows: GenericCsvPreviewRow[]) =>
  !m.categoryColumn || !m.subcategoryColumn || rows.some(r => r.unknownAccount && r.status !== 'DUPLICATE')

export default function CsvImportPage() {
  const { t } = useTranslation()
  const [phase, setPhase] = useState<Phase>({ step: 'upload' })
  const [isDragging, setIsDragging] = useState(false)
  const [categories, setCategories] = useState<CategoryGroup[]>([])
  const [decisions, setDecisions] = useState<Record<number, RowDecision>>({})
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    fetchCamtCategories().then(r => setCategories(r.categories)).catch(() => {})
  }, [])

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
      if (needsPreview(mapping, rows)) {
        const initialDecisions: Record<number, RowDecision> = {}
        rows.forEach(r => {
          initialDecisions[r.rowIndex] = r.status === 'DUPLICATE'
            ? { action: 'skip' }
            : { action: 'import', category: r.mappedCategory ?? '', subcategory: r.mappedSubcategory ?? '' }
        })
        setDecisions(initialDecisions)
        setPhase({ step: 'categorizing', rows, detection, mapping, file })
      } else {
        setPhase({ step: 'importing', detection, mapping, file })
        const count = await importGenericCsv(file, mapping)
        setPhase({ step: 'success', count })
      }
    } catch (e) {
      setPhase({ step: 'error', message: e instanceof Error ? e.message : 'Preview failed' })
    }
  }

  async function handleImportRows(rows: GenericCsvPreviewRow[], detection: CsvDetectionResult, mapping: CsvMapping, file: File) {
    const toImport: GenericRowToImport[] = rows
      .filter(r => {
        if (r.status === 'DUPLICATE' || r.unknownAccount) return false
        const d = decisions[r.rowIndex]
        return d?.action === 'import' && d.category.trim() && d.subcategory.trim()
      })
      .map(r => {
        const d = decisions[r.rowIndex] as { action: 'import'; category: string; subcategory: string }
        return { ...r, category: d.category, subcategory: d.subcategory }
      })

    setPhase({ step: 'importing-rows', rows, detection, mapping, file })
    try {
      const count = await importGenericRows(toImport)
      setPhase({ step: 'success', count })
    } catch (e) {
      setPhase({ step: 'error', message: e instanceof Error ? e.message : 'Import failed' })
    }
  }

  if (phase.step === 'success') {
    return (
      <div className="ri-center">
        <p className="ri-success">
          {phase.count > 0
            ? t('csvImport.success.imported', { count: phase.count })
            : t('csvImport.success.none')}
        </p>
        <button className="load-btn" onClick={() => setPhase({ step: 'upload' })}>{t('csvImport.success.importMore')}</button>
      </div>
    )
  }

  if (phase.step === 'mapping' || phase.step === 'importing' || phase.step === 'previewing') {
    const { detection, mapping, file } = phase
    return (
      <MappingView
        detection={detection}
        mapping={mapping}
        file={file}
        onChange={m => setPhase({ step: 'mapping', detection, mapping: m, file })}
        onConfirm={() => handleConfirm(detection, mapping, file)}
        onCancel={() => setPhase({ step: 'upload' })}
        importing={phase.step === 'importing' || phase.step === 'previewing'}
      />
    )
  }

  if (phase.step === 'categorizing' || phase.step === 'importing-rows') {
    const { rows, detection, mapping, file } = phase
    const importing = phase.step === 'importing-rows'
    const allCategoryNames = categories.map(g => g.name)
    const subcategoriesFor = (cat: string) => categories.find(g => g.name === cat)?.subcategories ?? []

    const setDecision = (rowIndex: number, d: RowDecision) =>
      setDecisions(prev => ({ ...prev, [rowIndex]: d }))
    const setCategoryField = (rowIndex: number, field: 'category' | 'subcategory', value: string) =>
      setDecisions(prev => {
        const cur = prev[rowIndex]
        if (cur?.action !== 'import') return prev
        return { ...prev, [rowIndex]: { ...cur, [field]: value, ...(field === 'category' ? { subcategory: '' } : {}) } }
      })

    const duplicateCount = rows.filter(r => r.status === 'DUPLICATE').length
    const unknownAccountCount = rows.filter(r => r.status !== 'DUPLICATE' && r.unknownAccount).length
    const readyCount = rows.filter(r => {
      if (r.status === 'DUPLICATE' || r.unknownAccount) return false
      const d = decisions[r.rowIndex]
      return d?.action === 'import' && d.category.trim() && d.subcategory.trim()
    }).length
    const skippedCount = rows.filter(r => r.status !== 'DUPLICATE' && !r.unknownAccount && decisions[r.rowIndex]?.action === 'skip').length

    const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

    return (
      <div className="ri-page">
        <div className="ri-preview">
          <div className="ri-summary-bar">
            <span className="ri-chip ri-chip--new">{t('csvImport.categorizing.new', { count: rows.length - duplicateCount })}</span>
            {duplicateCount > 0 && <span className="ri-chip ri-chip--dup">{t('csvImport.categorizing.duplicate', { count: duplicateCount })}</span>}
            {unknownAccountCount > 0 && <span className="ri-chip ri-chip--inv">{t('csvImport.categorizing.unknownAccount', { count: unknownAccountCount })}</span>}
            {skippedCount > 0 && <span className="ri-chip ri-chip--prev-ignored">{t('csvImport.categorizing.skipped', { count: skippedCount })}</span>}
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
          <div className="ri-table-wrap">
            <table className="ri-table">
              <thead>
                <tr>
                  <th>{t('csvImport.categorizing.columns.date')}</th>
                  <th>{t('csvImport.categorizing.columns.amount')}</th>
                  <th>{t('csvImport.categorizing.columns.currency')}</th>
                  <th>{t('csvImport.categorizing.columns.account')}</th>
                  <th>{t('csvImport.categorizing.columns.purpose')}</th>
                  <th>{t('csvImport.categorizing.columns.category')}</th>
                  <th>{t('csvImport.categorizing.columns.subcategory')}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {rows.map(row => {
                  const isDuplicate = row.status === 'DUPLICATE'
                  const isUnknown = !isDuplicate && row.unknownAccount
                  const isExcluded = isDuplicate || isUnknown
                  const d = decisions[row.rowIndex]
                  const isImporting = !isExcluded && d?.action === 'import'
                  const catVal = isImporting ? d.category : ''
                  const subVal = isImporting ? d.subcategory : ''
                  const subcatOptions = subcategoriesFor(catVal)
                  const rowClass = isExcluded
                    ? 'ri-row ri-row--duplicate'
                    : isImporting ? 'ri-row ri-row--new' : 'ri-row ri-row--will-ignore'
                  return (
                    <tr key={row.rowIndex} className={rowClass}>
                      <td className="ri-cell-date">{row.date}</td>
                      <td className={`ri-cell-amount${row.amount < 0 ? ' negative' : ''}`}>{EUR.format(row.amount)}</td>
                      <td className="ri-cell-date">{row.currency}</td>
                      <td className={`ri-cell-date${isUnknown ? ' gcv-unknown-iban' : ''}`} title={row.accountIban}>
                        {isUnknown ? t('csvImport.categorizing.unknownAccountLabel') : row.accountIban}
                      </td>
                      <td className="ri-cell-purpose" title={row.purpose ?? ''}>{row.purpose || '—'}</td>
                      {isExcluded ? (
                        <td colSpan={2} className="ri-cell-muted">{isDuplicate ? t('csvImport.categorizing.alreadyImported') : t('csvImport.categorizing.excluded')}</td>
                      ) : isImporting ? (
                        <>
                          <td>
                            <input
                              className="ri-cat-input"
                              list="gcv-cat-list"
                              placeholder={t('common.category')}
                              value={catVal}
                              onChange={e => setCategoryField(row.rowIndex, 'category', e.target.value)}
                            />
                          </td>
                          <td>
                            <input
                              className="ri-cat-input"
                              list={`gcv-sub-list-${row.rowIndex}`}
                              placeholder={t('common.subcategory')}
                              value={subVal}
                              onChange={e => setCategoryField(row.rowIndex, 'subcategory', e.target.value)}
                            />
                            <datalist id={`gcv-sub-list-${row.rowIndex}`}>
                              {subcatOptions.map(s => <option key={s} value={s} />)}
                            </datalist>
                          </td>
                        </>
                      ) : (
                        <td colSpan={2} className="ri-cell-muted">—</td>
                      )}
                      <td className="ri-cell-action">
                        {!isExcluded && (isImporting ? (
                          <button className="ri-action-btn ri-action-btn--ignore" onClick={() => setDecision(row.rowIndex, { action: 'skip' })}>{t('common.skip')}</button>
                        ) : (
                          <button className="ri-action-btn ri-action-btn--import" onClick={() => setDecision(row.rowIndex, { action: 'import', category: '', subcategory: '' })}>{t('common.undo')}</button>
                        ))}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <datalist id="gcv-cat-list">
            {allCategoryNames.map(n => <option key={n} value={n} />)}
          </datalist>
        </div>
      </div>
    )
  }

  const isLoading = phase.step === 'detecting'

  return (
    <div className="ri-page">
      <div
        className={`ri-dropzone${isDragging ? ' dragging' : ''}`}
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
        <span className="ri-dropzone-icon">↑</span>
        <span className="ri-dropzone-label">
          {isLoading ? t('csvImport.dropzone.analyzing') : t('csvImport.dropzone.label')}
        </span>
        <span className="ri-dropzone-hint">
          {isLoading ? t('csvImport.dropzone.analyzingHint') : t('csvImport.dropzone.hint')}
        </span>
        {phase.step === 'error' && (
          <span className="ri-dropzone-error">{phase.message}</span>
        )}
      </div>
    </div>
  )
}
