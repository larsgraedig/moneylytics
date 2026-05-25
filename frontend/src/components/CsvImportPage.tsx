import { useCallback, useRef, useState } from 'react'
import { importCsv, type CsvValidationError } from '../api/csvImport'

type PageState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'success'; importedCount: number }
  | { phase: 'validation'; errors: CsvValidationError[] }
  | { phase: 'error'; message: string }

export default function CsvImportPage() {
  const [state, setState] = useState<PageState>({ phase: 'idle' })
  const [isDragging, setIsDragging] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleFile = useCallback(async (file: File) => {
    setState({ phase: 'loading' })
    try {
      const result = await importCsv(file)
      if ('errors' in result) {
        setState({ phase: 'validation', errors: result.errors })
      } else {
        setState({ phase: 'success', importedCount: result.importedCount })
      }
    } catch (e) {
      setState({ phase: 'error', message: e instanceof Error ? e.message : 'Import failed' })
    }
  }, [])

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(false)
    const file = e.dataTransfer.files[0]
    if (file) handleFile(file)
  }, [handleFile])

  if (state.phase === 'success') {
    return (
      <div className="ri-center">
        <p className="ri-success">
          {state.importedCount > 0
            ? `${state.importedCount} transaction${state.importedCount !== 1 ? 's' : ''} imported`
            : 'No new transactions (all already imported)'}
        </p>
        <button className="load-btn" onClick={() => setState({ phase: 'idle' })}>import another file</button>
      </div>
    )
  }

  if (state.phase === 'validation') {
    return (
      <div className="ri-page">
        <div className="csv-error-header">
          <span className="csv-error-title">Format errors in uploaded file</span>
          <button className="load-btn" onClick={() => setState({ phase: 'idle' })}>← back</button>
        </div>
        <div className="ri-table-wrap">
          <table className="ri-table">
            <thead>
              <tr>
                <th>row</th>
                <th>column</th>
                <th>value</th>
                <th>error</th>
              </tr>
            </thead>
            <tbody>
              {state.errors.map((err, i) => (
                <tr key={i} className="ri-row ri-row--invalid">
                  <td className="ri-cell-num">{err.row || '—'}</td>
                  <td><code>{err.column}</code></td>
                  <td className="ri-cell-muted">{err.value || '∅'}</td>
                  <td>{err.message}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    )
  }

  return (
    <div className="ri-page">
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
          onChange={e => { const f = e.target.files?.[0]; if (f) handleFile(f); e.target.value = '' }}
        />
        <span className="ri-dropzone-icon">↑</span>
        <span className="ri-dropzone-label">drop enriched CSV here or click to browse</span>
        <span className="ri-dropzone-hint">MLP Banking export with Kategorie / Unterkategorie columns</span>
        {state.phase === 'error' && (
          <span className="ri-dropzone-error">{state.message}</span>
        )}
        {state.phase === 'loading' && (
          <span className="ri-dropzone-hint">importing…</span>
        )}
      </div>
    </div>
  )
}
