import { useCallback, useEffect, useRef, useState } from 'react'
import {
  fetchCategories,
  importRaw,
  previewRawImport,
  type CategoryGroup,
  type RawPreviewRow,
} from '../api/rawImport'

type PageState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'preview'; rows: RawPreviewRow[]; accountIban: string; accountName: string }
  | { phase: 'imported'; count: number }

type CategoryAssignment = { category: string; subcategory: string }

function formatAmount(amount: number | null, raw: string): string {
  if (amount == null) return raw
  return new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(amount)
}

function formatDate(date: string | null, raw: string): string {
  if (!date) return raw
  const [y, m, d] = date.split('-')
  return `${d}.${m}.${y}`
}

export default function RawImportPage() {
  const [state, setState] = useState<PageState>({ phase: 'idle' })
  const [categories, setCategories] = useState<CategoryGroup[]>([])
  const [assignments, setAssignments] = useState<Record<number, CategoryAssignment>>({})
  const [importing, setImporting] = useState(false)
  const [isDragging, setIsDragging] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    fetchCategories().then(r => setCategories(r.categories)).catch(() => {})
  }, [])

  const handleFile = useCallback(async (file: File) => {
    setState({ phase: 'loading' })
    setAssignments({})
    try {
      const result = await previewRawImport(file)
      const rows = result.rows
      if (rows.length === 0) {
        setState({ phase: 'error', message: 'The file contains no rows.' })
        return
      }
      const firstValid = rows.find(r => r.status !== 'INVALID')
      setState({
        phase: 'preview',
        rows,
        accountIban: firstValid?.accountIban ?? '',
        accountName: firstValid?.accountName ?? '',
      })
    } catch (e) {
      setState({ phase: 'error', message: e instanceof Error ? e.message : 'Preview failed' })
    }
  }, [])

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(false)
    const file = e.dataTransfer.files[0]
    if (file) handleFile(file)
  }, [handleFile])

  const handleImport = async () => {
    if (state.phase !== 'preview') return
    const newRows = state.rows.filter(r => r.status === 'NEW')
    const transactions = newRows.map(row => {
      const a = assignments[row.rowNumber]
      return {
        bookingDate: row.bookingDate!,
        valueDate: row.valueDate!,
        amount: row.amount!,
        currency: row.currency,
        category: a?.category ?? '',
        subcategory: a?.subcategory ?? '',
      }
    })
    setImporting(true)
    try {
      const result = await importRaw({
        accountIban: state.accountIban,
        accountName: state.accountName,
        transactions,
      })
      setState({ phase: 'imported', count: result.importedCount })
    } catch (e) {
      setState({ phase: 'error', message: e instanceof Error ? e.message : 'Import failed' })
    } finally {
      setImporting(false)
    }
  }

  const setAssignment = (rowNumber: number, field: keyof CategoryAssignment, value: string) => {
    setAssignments(prev => ({
      ...prev,
      [rowNumber]: {
        ...(prev[rowNumber] ?? { category: '', subcategory: '' }),
        [field]: value,
        ...(field === 'category' ? { subcategory: '' } : {}),
      },
    }))
  }

  const newRows = state.phase === 'preview' ? state.rows.filter(r => r.status === 'NEW') : []
  const canImport =
    newRows.length > 0 &&
    newRows.every(r => {
      const a = assignments[r.rowNumber]
      return a?.category && a?.subcategory
    })

  const subcategoriesFor = (category: string): string[] =>
    categories.find(g => g.name === category)?.subcategories ?? []

  const allCategoryNames = categories.map(g => g.name)

  if (state.phase === 'imported') {
    return (
      <div className="ri-center">
        <p className="ri-success">
          Imported <strong>{state.count}</strong> new transaction{state.count !== 1 ? 's' : ''} successfully.
        </p>
        <button className="load-btn" onClick={() => setState({ phase: 'idle' })}>import another file</button>
      </div>
    )
  }

  return (
    <div className="ri-page">
      {/* Drop zone */}
      {(state.phase === 'idle' || state.phase === 'error') && (
        <div
          className={`ri-dropzone${isDragging ? ' dragging' : ''}`}
          onDragOver={e => { e.preventDefault(); setIsDragging(true) }}
          onDragLeave={() => setIsDragging(false)}
          onDrop={handleDrop}
          onClick={() => fileInputRef.current?.click()}
        >
          <input
            ref={fileInputRef}
            type="file"
            accept=".csv"
            style={{ display: 'none' }}
            onChange={e => { const f = e.target.files?.[0]; if (f) handleFile(f) }}
          />
          <span className="ri-dropzone-icon">↑</span>
          <span className="ri-dropzone-label">drop MLP CSV here or click to browse</span>
          {state.phase === 'error' && (
            <span className="ri-dropzone-error">{state.message}</span>
          )}
        </div>
      )}

      {state.phase === 'loading' && (
        <p className="hint loading">parsing file…</p>
      )}

      {state.phase === 'preview' && (() => {
        const { rows } = state
        const nNew = rows.filter(r => r.status === 'NEW').length
        const nDup = rows.filter(r => r.status === 'DUPLICATE').length
        const nInv = rows.filter(r => r.status === 'INVALID').length

        return (
          <div className="ri-preview">
            <div className="ri-summary-bar">
              <span className="ri-chip ri-chip--new">{nNew} new</span>
              <span className="ri-chip ri-chip--dup">{nDup} duplicate</span>
              <span className="ri-chip ri-chip--inv">{nInv} invalid</span>
              <span className="ri-summary-spacer" />
              <button
                className="load-btn"
                onClick={() => setState({ phase: 'idle' })}
              >
                ← back
              </button>
              <button
                className="load-btn ri-import-btn"
                onClick={handleImport}
                disabled={!canImport || importing}
                title={!canImport ? 'Assign category and subcategory to all new rows first' : ''}
              >
                {importing ? '…' : `import ${nNew} new`}
              </button>
            </div>

            <div className="ri-table-wrap">
              <table className="ri-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>status</th>
                    <th>date</th>
                    <th>counterparty</th>
                    <th>purpose</th>
                    <th>amount</th>
                    <th>category</th>
                    <th>subcategory</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map(row => {
                    const a = assignments[row.rowNumber]
                    const catVal = a?.category ?? ''
                    const subVal = a?.subcategory ?? ''
                    const subcatOptions = subcategoriesFor(catVal)

                    return (
                      <tr key={row.rowNumber} className={`ri-row ri-row--${row.status.toLowerCase()}`}>
                        <td className="ri-cell-num">{row.rowNumber}</td>
                        <td>
                          <span className={`ri-badge ri-badge--${row.status.toLowerCase()}`}>
                            {row.status.toLowerCase()}
                          </span>
                        </td>
                        <td className="ri-cell-date">{formatDate(row.bookingDate, row.bookingDate ?? '—')}</td>
                        <td className="ri-cell-party" title={row.counterparty}>{row.counterparty || '—'}</td>
                        <td className="ri-cell-purpose" title={row.purpose}>{row.purpose || '—'}</td>
                        <td className={`ri-cell-amount${row.amount != null && row.amount < 0 ? ' negative' : ''}`}>
                          {formatAmount(row.amount, row.amountRaw)}
                        </td>

                        {row.status === 'NEW' ? (
                          <>
                            <td>
                              <input
                                className="ri-cat-input"
                                list={`cat-list`}
                                placeholder="category"
                                value={catVal}
                                onChange={e => setAssignment(row.rowNumber, 'category', e.target.value)}
                              />
                            </td>
                            <td>
                              <input
                                className="ri-cat-input"
                                list={`sub-list-${row.rowNumber}`}
                                placeholder="subcategory"
                                value={subVal}
                                onChange={e => setAssignment(row.rowNumber, 'subcategory', e.target.value)}
                              />
                              <datalist id={`sub-list-${row.rowNumber}`}>
                                {subcatOptions.map(s => <option key={s} value={s} />)}
                              </datalist>
                            </td>
                          </>
                        ) : row.status === 'INVALID' ? (
                          <td colSpan={2} className="ri-cell-errors">
                            {row.errors.map((err, i) => (
                              <span key={i} className="ri-error-tag" title={err.message}>
                                {err.column}: <em>{err.value || '∅'}</em>
                              </span>
                            ))}
                          </td>
                        ) : (
                          <td colSpan={2} className="ri-cell-muted">already imported</td>
                        )}
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>

            {/* Shared category datalist (all categories) */}
            <datalist id="cat-list">
              {allCategoryNames.map(n => <option key={n} value={n} />)}
            </datalist>
          </div>
        )
      })()}
    </div>
  )
}
