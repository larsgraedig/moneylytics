import { useCallback, useRef, useState } from 'react'
import {
  detectCsvFormat,
  importGenericCsv,
  type AmountFormat,
  type CsvDetectionResult,
  type CsvMapping,
} from '../api/genericCsvImport'

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
  | { step: 'importing'; detection: CsvDetectionResult; mapping: CsvMapping; file: File }
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
        {!required && <option value="">— not mapped —</option>}
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
  const { headers, sampleRows } = detection

  const dateIdx = colIdx(headers, mapping.dateColumn)
  const amtIdx = colIdx(headers, mapping.amountColumn)
  const purpIdx = colIdx(headers, mapping.purposeColumn)
  const catIdx = colIdx(headers, mapping.categoryColumn)
  const subIdx = colIdx(headers, mapping.subcategoryColumn)

  const canConfirm = mapping.dateColumn && mapping.amountColumn

  function set<K extends keyof CsvMapping>(key: K, value: CsvMapping[K]) {
    onChange({ ...mapping, [key]: value })
  }

  return (
    <div className="gcv-page">
      <div className="gcv-header">
        <div>
          <div className="gcv-title">
            Spalten-Mapping
            {detection.savedMapping && (
              <span className="gcv-saved-badge">gespeicherte Konfiguration</span>
            )}
          </div>
          <div className="gcv-subtitle">{file.name} · Trennzeichen: <code>{mapping.delimiter === '\t' ? 'Tab' : mapping.delimiter}</code></div>
        </div>
        <div className="gcv-header-actions">
          <button className="gcv-cancel-btn" onClick={onCancel} disabled={importing}>abbrechen</button>
          <button className="gcv-confirm-btn" onClick={onConfirm} disabled={!canConfirm || importing}>
            {importing ? 'importiert…' : 'importieren'}
          </button>
        </div>
      </div>

      <div className="gcv-body">
        <div className="gcv-mapping-panel">
          <div className="gcv-section-title">Pflichtfelder</div>
          <MappingRow label="Datum" required headers={headers} value={mapping.dateColumn} onChange={v => set('dateColumn', v ?? '')} />
          <div className="gcv-map-row">
            <span className="gcv-map-label">Datumsformat<span className="gcv-map-required">*</span></span>
            <select className="gcv-map-select" value={mapping.dateFormat} onChange={e => set('dateFormat', e.target.value)}>
              {COMMON_DATE_FORMATS.map(f => <option key={f} value={f}>{f}</option>)}
              {!COMMON_DATE_FORMATS.includes(mapping.dateFormat) && (
                <option value={mapping.dateFormat}>{mapping.dateFormat}</option>
              )}
            </select>
          </div>
          <MappingRow label="Betrag" required headers={headers} value={mapping.amountColumn} onChange={v => set('amountColumn', v ?? '')} />
          <div className="gcv-map-row">
            <span className="gcv-map-label">Betragsformat<span className="gcv-map-required">*</span></span>
            <div className="gcv-radio-group">
              {(['GERMAN', 'ENGLISH'] as AmountFormat[]).map(f => (
                <label key={f} className="gcv-radio-label">
                  <input type="radio" checked={mapping.amountFormat === f} onChange={() => set('amountFormat', f)} />
                  {f === 'GERMAN' ? 'Deutsch (1.234,56)' : 'Englisch (1,234.56)'}
                </label>
              ))}
            </div>
          </div>

          <div className="gcv-section-title" style={{ marginTop: 16 }}>Optionale Felder</div>
          <MappingRow label="Verwendungszweck" headers={headers} value={mapping.purposeColumn} onChange={v => set('purposeColumn', v)} />
          <MappingRow label="Kategorie" headers={headers} value={mapping.categoryColumn} onChange={v => set('categoryColumn', v)} />
          <MappingRow label="Unterkategorie" headers={headers} value={mapping.subcategoryColumn} onChange={v => set('subcategoryColumn', v)} />
          <MappingRow label="Konto (IBAN)" headers={headers} value={mapping.accountIbanColumn} onChange={v => set('accountIbanColumn', v)} />
          {!mapping.accountIbanColumn && (
            <div className="gcv-map-row">
              <span className="gcv-map-label gcv-map-label--sub">fester IBAN-Wert</span>
              <input
                className="gcv-map-input"
                placeholder="z. B. DE00123456789"
                value={mapping.fixedAccountIban ?? ''}
                onChange={e => set('fixedAccountIban', e.target.value || null)}
              />
            </div>
          )}
          <MappingRow label="Währung" headers={headers} value={mapping.currencyColumn} onChange={v => set('currencyColumn', v)} />
          {!mapping.currencyColumn && (
            <div className="gcv-map-row">
              <span className="gcv-map-label gcv-map-label--sub">feste Währung</span>
              <input
                className="gcv-map-input"
                value={mapping.fixedCurrency}
                onChange={e => set('fixedCurrency', e.target.value)}
              />
            </div>
          )}
        </div>

        <div className="gcv-preview-panel">
          <div className="gcv-section-title">Vorschau (erste {sampleRows.length} Zeilen)</div>
          <div className="gcv-preview-wrap">
            <table className="gcv-preview-table">
              <thead>
                <tr>
                  <th>Datum</th>
                  <th>Betrag</th>
                  {purpIdx >= 0 && <th>Verwendungszweck</th>}
                  {catIdx >= 0 && <th>Kategorie</th>}
                  {subIdx >= 0 && <th>Unterkategorie</th>}
                </tr>
              </thead>
              <tbody>
                {sampleRows.map((row, i) => (
                  <tr key={i}>
                    <td>{parsePreviewDate(row[dateIdx] ?? '', mapping.dateFormat)}</td>
                    <td className={`gcv-amt ${parseFloat((mapping.amountFormat === 'GERMAN' ? (row[amtIdx] ?? '').replace(/\./g, '').replace(',', '.') : (row[amtIdx] ?? '').replace(/,/g, '')) || '0') < 0 ? 'negative' : 'positive'}`}>
                      {parsePreviewAmount(row[amtIdx] ?? '', mapping.amountFormat)}
                    </td>
                    {purpIdx >= 0 && <td className="gcv-purpose">{row[purpIdx] ?? ''}</td>}
                    {catIdx >= 0 && <td>{row[catIdx] ?? ''}</td>}
                    {subIdx >= 0 && <td>{row[subIdx] ?? ''}</td>}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  )
}

export default function CsvImportPage() {
  const [phase, setPhase] = useState<Phase>({ step: 'upload' })
  const [isDragging, setIsDragging] = useState(false)
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
    setPhase({ step: 'importing', detection, mapping, file })
    try {
      const count = await importGenericCsv(file, mapping)
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
            ? `${phase.count} Transaktion${phase.count !== 1 ? 'en' : ''} importiert`
            : 'Keine neuen Transaktionen (alle bereits importiert)'}
        </p>
        <button className="load-btn" onClick={() => setPhase({ step: 'upload' })}>weitere Datei importieren</button>
      </div>
    )
  }

  if (phase.step === 'mapping' || phase.step === 'importing') {
    const { detection, mapping, file } = phase
    return (
      <MappingView
        detection={detection}
        mapping={mapping}
        file={file}
        onChange={m => setPhase({ step: 'mapping', detection, mapping: m, file })}
        onConfirm={() => handleConfirm(detection, mapping, file)}
        onCancel={() => setPhase({ step: 'upload' })}
        importing={phase.step === 'importing'}
      />
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
          {isLoading ? 'analysiere Datei…' : 'beliebige CSV hochladen oder hierher ziehen'}
        </span>
        <span className="ri-dropzone-hint">
          {isLoading ? 'Trennzeichen, Spalten und Formate werden erkannt' : 'Trennzeichen und Formate werden automatisch erkannt'}
        </span>
        {phase.step === 'error' && (
          <span className="ri-dropzone-error">{phase.message}</span>
        )}
      </div>
    </div>
  )
}
