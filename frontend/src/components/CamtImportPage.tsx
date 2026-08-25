import { useCallback, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  importCamt,
  previewCamtImport,
  type CamtAccountBalance,
  type CamtAccountInfo,
  type RawPreviewRow,
} from '../api/camtImport'
import { ImportPreviewTable, type ImportDecision, type ImportPreviewRow } from './ImportPreviewTable'

type PageState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'preview'; rows: RawPreviewRow[]; accounts: CamtAccountInfo[]; accountBalances: Record<string, CamtAccountBalance> }
  | { phase: 'imported'; importedCount: number; ignoredCount: number }

function formatDate(iso: string | null): string {
  if (!iso) return '—'
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

function adaptRow(row: RawPreviewRow, accountNames: Record<string, string>): ImportPreviewRow {
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

function initDecisions(rows: RawPreviewRow[]): Record<number, ImportDecision> {
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

export default function CamtImportPage() {
  const { t } = useTranslation()
  const [state, setState] = useState<PageState>({ phase: 'idle' })
  const [decisions, setDecisions] = useState<Record<number, ImportDecision>>({})
  const [accountNames, setAccountNames] = useState<Record<string, string>>({})
  const [importing, setImporting] = useState(false)
  const [isDragging, setIsDragging] = useState(false)
  const [activeFilters, setActiveFilters] = useState<Set<string>>(
    () => new Set(['NEW', 'PREVIOUSLY_IGNORED', 'DUPLICATE', 'INVALID', 'UNKNOWN_ACCOUNT']),
  )
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleFiles = useCallback(async (files: File[]) => {
    if (files.length === 0) return
    setState({ phase: 'loading' })
    try {
      const result = await previewCamtImport(files)
      const { rows, accounts, accountBalances } = result
      if (rows.length === 0) {
        setState({ phase: 'error', message: t('camtImport.noRows') })
        return
      }
      const names: Record<string, string> = {}
      for (const acc of accounts) {
        names[acc.iban] = acc.suggestedName
      }
      setState({ phase: 'preview', rows, accounts, accountBalances })
      setDecisions(initDecisions(rows))
      setAccountNames(names)
      setActiveFilters(defaultCamtFilters(rows))
    } catch (e) {
      setState({ phase: 'error', message: e instanceof Error ? e.message : 'Preview failed' })
    }
  }, [])

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setIsDragging(false)
    const files = Array.from(e.dataTransfer.files).filter(f => f.name.endsWith('.xml'))
    if (files.length > 0) handleFiles(files)
  }, [handleFiles])

  const handleImport = async () => {
    if (state.phase !== 'preview') return
    const { rows, accountBalances } = state

    const toImport = rows
      .filter(r => r.status === 'NEW' || r.status === 'PREVIOUSLY_IGNORED')
      .flatMap(r => {
        const d = decisions[r.rowNumber]
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
      .filter(r => r.status === 'NEW' && decisions[r.rowNumber]?.action === 'ignore')
      .map(r => r.fingerprint!)

    setImporting(true)
    try {
      const result = await importCamt({ accountNames, toImport, toIgnore, toEnrich: [], accountBalances })
      setState({ phase: 'imported', importedCount: result.importedCount, ignoredCount: toIgnore.length })
    } catch (e) {
      setState({ phase: 'error', message: e instanceof Error ? e.message : 'Import failed' })
    } finally {
      setImporting(false)
    }
  }

  const canImport = (rows: RawPreviewRow[]) => {
    const readyToImport = rows.filter(r =>
      (r.status === 'NEW' || r.status === 'PREVIOUSLY_IGNORED') && decisions[r.rowNumber]?.action === 'import',
    )
    const toIgnore = rows.filter(r => r.status === 'NEW' && decisions[r.rowNumber]?.action === 'ignore')
    return readyToImport.length > 0 || toIgnore.length > 0
  }

  // ── result screen ──────────────────────────────────────────────────────────

  if (state.phase === 'imported') {
    const parts: string[] = []
    if (state.importedCount > 0) parts.push(t('camtImport.success.imported', { count: state.importedCount }))
    if (state.ignoredCount > 0) parts.push(t('camtImport.success.ignored', { count: state.ignoredCount }))
    return (
      <div className="flex flex-col h-full items-center justify-center gap-4 text-center p-8">
        <p className="text-green-500 font-medium text-lg">{parts.join(' · ')}</p>
        <button className="rounded-lg border border-input bg-input/30 px-4 py-2 text-sm hover:bg-input/50" onClick={() => setState({ phase: 'idle' })}>{t('camtImport.success.importMore')}</button>
      </div>
    )
  }

  // ── drop zone ──────────────────────────────────────────────────────────────

  if (state.phase === 'idle' || state.phase === 'error') {
    return (
      <div className="flex flex-col h-full items-center justify-center p-8">
        <div
          className={`flex flex-col items-center justify-center gap-3 rounded-2xl border-2 border-dashed p-16 text-center cursor-pointer transition-colors w-full max-w-lg ${isDragging ? 'border-primary bg-primary/5' : 'border-border hover:border-primary/50'}`}
          onDragOver={e => { e.preventDefault(); setIsDragging(true) }}
          onDragLeave={() => setIsDragging(false)}
          onDrop={handleDrop}
          onClick={() => fileInputRef.current?.click()}
        >
          <input
            ref={fileInputRef}
            type="file"
            accept=".xml"
            multiple
            style={{ display: 'none' }}
            onChange={e => {
              const files = Array.from(e.target.files ?? [])
              if (files.length > 0) handleFiles(files)
              e.target.value = ''
            }}
          />
          <span className="text-4xl">↑</span>
          <span className="text-base font-medium">{t('camtImport.dropzone.label')}</span>
          <span className="text-sm text-muted-foreground">{t('camtImport.dropzone.hint')}</span>
          {state.phase === 'error' && (
            <span className="text-sm text-destructive">{state.message}</span>
          )}
        </div>
      </div>
    )
  }

  if (state.phase === 'loading') {
    return <p className="hint loading">{t('camtImport.parsing')}</p>
  }

  // ── preview ────────────────────────────────────────────────────────────────

  const { rows } = state
  const nNew = rows.filter(r => r.status === 'NEW' && !r.unknownAccount).length
  const nDup = rows.filter(r => r.status === 'DUPLICATE').length
  const nInv = rows.filter(r => r.status === 'INVALID').length
  const nIgn = rows.filter(r => r.status === 'PREVIOUSLY_IGNORED').length
  const nUnknown = rows.filter(r => r.unknownAccount && r.status !== 'DUPLICATE').length

  const adaptedRows = rows.map(r => adaptRow(r, accountNames))
  const filteredRows = adaptedRows.filter(r => {
    if (r.unknownAccount && r.status !== 'DUPLICATE') return activeFilters.has('UNKNOWN_ACCOUNT')
    if (r.status === 'NEW') return activeFilters.has('NEW')
    return activeFilters.has(r.status)
  })

  const toggleFilter = (key: string) => {
    setActiveFilters(prev => {
      if (prev.has(key) && prev.size === 1) return prev
      const next = new Set(prev)
      if (next.has(key)) next.delete(key); else next.add(key)
      return next
    })
  }

  return (
    <div className="ri-page">
      <div className="ri-preview">
        <div className="ri-summary-bar">
          {nNew > 0 && (
            <button className={`ri-chip ri-chip--new${activeFilters.has('NEW') ? ' ri-chip--active' : ''}`} onClick={() => toggleFilter('NEW')}>
              {t('import.chips.new', { count: nNew })}
            </button>
          )}
          {nIgn > 0 && (
            <button className={`ri-chip ri-chip--prev-ignored${activeFilters.has('PREVIOUSLY_IGNORED') ? ' ri-chip--active' : ''}`} onClick={() => toggleFilter('PREVIOUSLY_IGNORED')}>
              {t('camtImport.chips.previouslyIgnored', { count: nIgn })}
            </button>
          )}
          {nDup > 0 && (
            <button className={`ri-chip ri-chip--dup${activeFilters.has('DUPLICATE') ? ' ri-chip--active' : ''}`} onClick={() => toggleFilter('DUPLICATE')}>
              {t('import.chips.duplicate', { count: nDup })}
            </button>
          )}
          {nInv > 0 && (
            <button className={`ri-chip ri-chip--inv${activeFilters.has('INVALID') ? ' ri-chip--active' : ''}`} onClick={() => toggleFilter('INVALID')}>
              {t('camtImport.chips.invalid', { count: nInv })}
            </button>
          )}
          {nUnknown > 0 && (
            <button className={`ri-chip ri-chip--inv${activeFilters.has('UNKNOWN_ACCOUNT') ? ' ri-chip--active' : ''}`} onClick={() => toggleFilter('UNKNOWN_ACCOUNT')}>
              {t('import.chips.excluded', { count: nUnknown })}
            </button>
          )}
          <span className="ri-summary-spacer" />
          <button className="load-btn" onClick={() => setState({ phase: 'idle' })}>{t('camtImport.back')}</button>
          <button
            className="load-btn ri-import-btn"
            onClick={handleImport}
            disabled={!canImport(rows) || importing}
          >
            {importing ? '…' : t('camtImport.confirm')}
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
