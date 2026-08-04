import { useCallback, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  importCamt,
  previewCamtImport,
  type CamtAccountBalance,
  type CamtAccountInfo,
  type RawPreviewRow,
} from '../api/camtImport'
import type { CategoryNode } from '../api/rawImport'
import { CategoryPathInput } from './CategoryPathInput'

type PageState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'preview'; rows: RawPreviewRow[]; accounts: CamtAccountInfo[]; accountBalances: Record<string, CamtAccountBalance> }
  | { phase: 'imported'; importedCount: number; ignoredCount: number; skippedCount: number }

type RowDecision =
  | { action: 'import'; categoryId: number | null }
  | { action: 'ignore' }
  | { action: 'enrich' }

function formatAmount(amount: number | null, raw: string): string {
  if (amount == null) return raw
  return new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(amount)
}

function formatDate(iso: string | null): string {
  if (!iso) return '—'
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

function pathFromCategoryId(id: number, nodes: CategoryNode[], prefix: string[] = []): string[] {
  for (const n of nodes) {
    if (n.id === id) return [...prefix, n.name]
    const found = pathFromCategoryId(id, n.children, [...prefix, n.name])
    if (found.length > 0) return found
  }
  return []
}

function initDecisions(rows: RawPreviewRow[]): Record<number, RowDecision> {
  const out: Record<number, RowDecision> = {}
  for (const row of rows) {
    if (row.unknownAccount) continue
    if (row.status === 'NEW') {
      out[row.rowNumber] = { action: 'import', categoryId: null }
    } else if (row.status === 'PREVIOUSLY_IGNORED') {
      out[row.rowNumber] = { action: 'ignore' }
    }
  }
  return out
}

export default function CamtImportPage({ categories }: { categories: CategoryNode[] }) {
  const { t } = useTranslation()
  const [state, setState] = useState<PageState>({ phase: 'idle' })
  const [decisions, setDecisions] = useState<Record<number, RowDecision>>({})
  const [accountNames, setAccountNames] = useState<Record<string, string>>({})
  const [importing, setImporting] = useState(false)
  const [isDragging, setIsDragging] = useState(false)
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

  const setDecision = (rowNumber: number, decision: RowDecision) => {
    setDecisions(prev => ({ ...prev, [rowNumber]: decision }))
  }

  const handleImport = async () => {
    if (state.phase !== 'preview') return
    const { rows, accountBalances } = state

    const toImport = rows
      .filter(r => r.status === 'NEW' || r.status === 'PREVIOUSLY_IGNORED')
      .flatMap(r => {
        const d = decisions[r.rowNumber]
        if (d?.action !== 'import') return []
        const path = d.categoryId != null ? pathFromCategoryId(d.categoryId, categories) : []
        return [{
          fingerprint: r.fingerprint!,
          bookingDate: r.bookingDate!,
          valueDate: r.valueDate!,
          amount: r.amount!,
          currency: r.currency,
          category: path[0] ?? '',
          subcategory: path[1] ?? null,
          group: path[2] ?? '',
          accountIban: r.accountIban,
          purpose: r.purpose || null,
          counterpartyName: r.counterparty ?? null,
          counterpartyIban: r.counterpartyIban ?? null,
        }]
      })

    const toIgnore = rows
      .filter(r => r.status === 'NEW' && decisions[r.rowNumber]?.action === 'ignore')
      .map(r => r.fingerprint!)

    const toEnrich = rows
      .filter(r => r.status === 'DUPLICATE' && decisions[r.rowNumber]?.action === 'enrich')
      .map(r => ({
        fingerprint: r.fingerprint!,
        purpose: r.purpose || null,
        counterpartyName: r.counterparty || null,
        counterpartyIban: r.counterpartyIban || null,
      }))

    const skippedCount = rows
      .filter(r => r.status === 'NEW' || r.status === 'PREVIOUSLY_IGNORED')
      .filter(r => {
        const d = decisions[r.rowNumber]
        return d?.action === 'import' && d.categoryId == null
      })
      .length

    setImporting(true)
    try {
      const result = await importCamt({ accountNames, toImport, toIgnore, toEnrich, accountBalances })
      setState({ phase: 'imported', importedCount: result.importedCount, ignoredCount: toIgnore.length, skippedCount })
    } catch (e) {
      setState({ phase: 'error', message: e instanceof Error ? e.message : 'Import failed' })
    } finally {
      setImporting(false)
    }
  }

  const actionableRows = (rows: RawPreviewRow[]) =>
    rows.filter(r => r.status === 'NEW' || r.status === 'PREVIOUSLY_IGNORED')

  const toImportRows = (rows: RawPreviewRow[]) =>
    actionableRows(rows).filter(r => decisions[r.rowNumber]?.action === 'import')

  const toIgnoreRows = (rows: RawPreviewRow[]) =>
    rows.filter(r => r.status === 'NEW' && decisions[r.rowNumber]?.action === 'ignore')

  const canImport = (rows: RawPreviewRow[]) => {
    const readyToImport = toImportRows(rows).filter(r => decisions[r.rowNumber]?.action === 'import')
    const toEnrich = rows.filter(r => r.status === 'DUPLICATE' && decisions[r.rowNumber]?.action === 'enrich')
    return readyToImport.length > 0 || toIgnoreRows(rows).length > 0 || toEnrich.length > 0
  }

  // ── result screen ──────────────────────────────────────────────────────────

  if (state.phase === 'imported') {
    const parts: string[] = []
    if (state.importedCount > 0) parts.push(t('camtImport.success.imported', { count: state.importedCount }))
    if (state.ignoredCount > 0) parts.push(t('camtImport.success.ignored', { count: state.ignoredCount }))
    if (state.skippedCount > 0) parts.push(t('camtImport.success.skipped', { count: state.skippedCount }))
    return (
      <div className="ri-center">
        <p className="ri-success">{parts.join(' · ')}</p>
        <button className="load-btn" onClick={() => setState({ phase: 'idle' })}>{t('camtImport.success.importMore')}</button>
      </div>
    )
  }

  // ── drop zone ──────────────────────────────────────────────────────────────

  if (state.phase === 'idle' || state.phase === 'error') {
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
            accept=".xml"
            multiple
            style={{ display: 'none' }}
            onChange={e => {
              const files = Array.from(e.target.files ?? [])
              if (files.length > 0) handleFiles(files)
              e.target.value = ''
            }}
          />
          <span className="ri-dropzone-icon">↑</span>
          <span className="ri-dropzone-label">{t('camtImport.dropzone.label')}</span>
          <span className="ri-dropzone-hint">{t('camtImport.dropzone.hint')}</span>
          {state.phase === 'error' && (
            <span className="ri-dropzone-error">{state.message}</span>
          )}
        </div>
      </div>
    )
  }

  if (state.phase === 'loading') {
    return <p className="hint loading">{t('camtImport.parsing')}</p>
  }

  // ── preview ────────────────────────────────────────────────────────────────

  const { rows, accounts } = state
  const nNew = rows.filter(r => r.status === 'NEW').length
  const nDup = rows.filter(r => r.status === 'DUPLICATE').length
  const nInv = rows.filter(r => r.status === 'INVALID').length
  const nIgn = rows.filter(r => r.status === 'PREVIOUSLY_IGNORED').length
  const nUnknown = rows.filter(r => r.unknownAccount && r.status !== 'DUPLICATE').length

  return (
    <div className="ri-page">
      <div className="ri-preview">
        <div className="ri-summary-bar">
          {nNew > 0 && <span className="ri-chip ri-chip--new">{t('camtImport.chips.new', { count: nNew })}</span>}
          {nIgn > 0 && <span className="ri-chip ri-chip--prev-ignored">{t('camtImport.chips.previouslyIgnored', { count: nIgn })}</span>}
          {nDup > 0 && <span className="ri-chip ri-chip--dup">{t('camtImport.chips.duplicate', { count: nDup })}</span>}
          {nInv > 0 && <span className="ri-chip ri-chip--inv">{t('camtImport.chips.invalid', { count: nInv })}</span>}
          {nUnknown > 0 && <span className="ri-chip ri-chip--inv">{t('camtImport.chips.unknownAccount', { count: nUnknown })}</span>}
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

        {accounts.length > 0 && (
          <div className="ri-account-names">
            <span className="ri-account-names-label">{t('camtImport.accountNames')}</span>
            {accounts.map(acc => (
              <label key={acc.iban} className="ri-account-name-row">
                <span className="ri-account-iban">{acc.iban}</span>
                <input
                  className="ri-account-name-input"
                  placeholder={t('camtImport.accountNamePlaceholder')}
                  value={accountNames[acc.iban] ?? ''}
                  onChange={e => setAccountNames(prev => ({ ...prev, [acc.iban]: e.target.value }))}
                />
              </label>
            ))}
          </div>
        )}

        <div className="ri-table-wrap">
          <table className="ri-table">
            <thead>
              <tr>
                <th>{t('camtImport.columns.row')}</th>
                <th>{t('camtImport.columns.status')}</th>
                <th>{t('camtImport.columns.date')}</th>
                <th>{t('camtImport.columns.account')}</th>
                <th>{t('camtImport.columns.counterparty')}</th>
                <th>{t('camtImport.columns.purpose')}</th>
                <th>{t('camtImport.columns.amount')}</th>
                <th>{t('camtImport.columns.category')}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {rows.map(row => {
                const d = decisions[row.rowNumber]
                const isImporting = d?.action === 'import'
                const categoryId = isImporting && d.action === 'import' ? d.categoryId : null

                const rowClass = (() => {
                  if (row.unknownAccount) return 'ri-row ri-row--duplicate'
                  if (row.status === 'INVALID') return 'ri-row ri-row--invalid'
                  if (row.status === 'DUPLICATE') return 'ri-row ri-row--duplicate'
                  if (row.status === 'PREVIOUSLY_IGNORED') {
                    return isImporting ? 'ri-row ri-row--prev-ignored-importing' : 'ri-row ri-row--prev-ignored'
                  }
                  return isImporting ? 'ri-row ri-row--new' : 'ri-row ri-row--will-ignore'
                })()

                return (
                  <tr key={row.rowNumber} className={rowClass}>
                    <td className="ri-cell-num">{row.rowNumber}</td>

                    <td>
                      <StatusBadge row={row} decision={d} />
                    </td>

                    <td className="ri-cell-date">{formatDate(row.bookingDate)}</td>
                    <td className={`ri-cell-date${row.unknownAccount && row.status !== 'DUPLICATE' ? ' gcv-unknown-iban' : ''}`} title={row.accountIban}>
                      {row.unknownAccount && row.status !== 'DUPLICATE'
                        ? t('camtImport.unknownAccount')
                        : accountNames[row.accountIban] || row.accountIban}
                    </td>
                    <td className="ri-cell-party" title={row.counterparty}>{row.counterparty || '—'}</td>
                    <td className="ri-cell-purpose" title={row.purpose}>{row.purpose || '—'}</td>
                    <td className={`ri-cell-amount${row.amount != null && row.amount < 0 ? ' negative' : ''}`}>
                      {formatAmount(row.amount, row.amountRaw)}
                    </td>

                    <CategoryCell
                      row={row}
                      decision={d}
                      categoryId={categoryId}
                      categories={categories}
                      onCategoryChange={id => setDecision(row.rowNumber, { action: 'import', categoryId: id })}
                    />

                    <td className="ri-cell-action">
                      <ActionToggle row={row} decision={d} onDecide={dec => setDecision(row.rowNumber, dec)} />
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

// ── sub-components ─────────────────────────────────────────────────────────────

function StatusBadge({ row, decision }: { row: RawPreviewRow; decision: RowDecision | undefined }) {
  const { t } = useTranslation()
  if (row.status === 'INVALID') return <span className="ri-badge ri-badge--invalid">{t('camtImport.status.invalid')}</span>
  if (row.status === 'DUPLICATE') {
    return decision?.action === 'enrich'
      ? <span className="ri-badge ri-badge--new">{t('camtImport.status.enriching')}</span>
      : <span className="ri-badge ri-badge--duplicate">{t('camtImport.status.duplicate')}</span>
  }
  if (row.status === 'PREVIOUSLY_IGNORED') {
    return decision?.action === 'import'
      ? <span className="ri-badge ri-badge--new">{t('camtImport.status.importing')}</span>
      : <span className="ri-badge ri-badge--prev-ignored">{t('camtImport.status.previouslyIgnored')}</span>
  }
  return decision?.action === 'ignore'
    ? <span className="ri-badge ri-badge--will-ignore">{t('camtImport.status.willIgnore')}</span>
    : <span className="ri-badge ri-badge--new">{t('camtImport.status.new')}</span>
}

function CategoryCell({
  row, decision, categoryId, categories, onCategoryChange,
}: {
  row: RawPreviewRow
  decision: RowDecision | undefined
  categoryId: number | null
  categories: CategoryNode[]
  onCategoryChange: (id: number | null) => void
}) {
  const { t } = useTranslation()
  if (row.unknownAccount) {
    return <td className="ri-cell-muted">{t('camtImport.status.excluded')}</td>
  }
  if (row.status === 'INVALID') {
    return (
      <td className="ri-cell-errors">
        {row.errors.map((err, i) => (
          <span key={i} className="ri-error-tag" title={err.message}>
            {err.column}: <em>{err.value || '∅'}</em>
          </span>
        ))}
      </td>
    )
  }
  if (row.status === 'DUPLICATE') {
    return (
      <td className="ri-cell-muted">
        {decision?.action === 'enrich' ? t('camtImport.willEnrich') : t('camtImport.alreadyImported')}
      </td>
    )
  }
  if (decision?.action === 'ignore') {
    return <td className="ri-cell-muted">—</td>
  }
  return (
    <td className="ri-cell-category">
      <CategoryPathInput
        value={categoryId}
        onChange={onCategoryChange}
        tree={categories}
        placeholder={t('common.category')}
        className="ri-category-input"
      />
    </td>
  )
}

function ActionToggle({
  row, decision, onDecide,
}: {
  row: RawPreviewRow
  decision: RowDecision | undefined
  onDecide: (d: RowDecision) => void
}) {
  const { t } = useTranslation()
  if (row.unknownAccount || row.status === 'INVALID') return null

  if (row.status === 'DUPLICATE') {
    return decision?.action === 'enrich' ? (
      <button className="ri-action-btn ri-action-btn--ignore" onClick={() => onDecide({ action: 'ignore' })}>
        {t('camtImport.skip')}
      </button>
    ) : (
      <button className="ri-action-btn ri-action-btn--import" onClick={() => onDecide({ action: 'enrich' })}>
        {t('camtImport.enrich')}
      </button>
    )
  }

  if (row.status === 'PREVIOUSLY_IGNORED') {
    return decision?.action === 'import' ? (
      <button
        className="ri-action-btn ri-action-btn--ignore"
        onClick={() => onDecide({ action: 'ignore' })}
      >
        {t('camtImport.skipAgain')}
      </button>
    ) : (
      <button
        className="ri-action-btn ri-action-btn--import"
        onClick={() => onDecide({ action: 'import', categoryId: null })}
      >
        {t('camtImport.importAnyway')}
      </button>
    )
  }

  return decision?.action === 'ignore' ? (
    <button
      className="ri-action-btn ri-action-btn--import"
      onClick={() => onDecide({ action: 'import', categoryId: null })}
    >
      {t('camtImport.undo')}
    </button>
  ) : (
    <button
      className="ri-action-btn ri-action-btn--ignore"
      onClick={() => onDecide({ action: 'ignore' })}
    >
      {t('camtImport.skip')}
    </button>
  )
}
