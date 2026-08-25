import { useTranslation } from 'react-i18next'

export type ImportPreviewRow = {
  key: number
  status: 'NEW' | 'DUPLICATE' | 'INVALID' | 'PREVIOUSLY_IGNORED'
  unknownAccount: boolean
  date: string | null
  accountDisplay: string
  accountIban: string
  counterparty: string | null
  purpose: string | null
  amount: number | null
  amountRaw?: string
  currency: string
  errors: Array<{ column: string; message: string; value: string }>
  fingerprint: string | null
}

export type ImportDecision =
  | { action: 'import' }
  | { action: 'ignore' }

interface ImportPreviewTableProps {
  rows: ImportPreviewRow[]
  decisions: Record<number, ImportDecision>
  onDecide: (key: number, d: ImportDecision) => void
}

function formatAmount(amount: number | null, currency: string, raw?: string): string {
  if (amount == null) return raw ?? '—'
  try {
    return new Intl.NumberFormat('de-DE', { style: 'currency', currency }).format(amount)
  } catch {
    return `${amount} ${currency}`
  }
}

export function ImportPreviewTable({ rows, decisions, onDecide }: ImportPreviewTableProps) {
  const { t } = useTranslation()

  return (
    <div className="ri-table-wrap">
      <table className="ri-table">
        <thead>
          <tr>
            <th>{t('camtImport.columns.status')}</th>
            <th>{t('camtImport.columns.date')}</th>
            <th>{t('camtImport.columns.account')}</th>
            <th>{t('camtImport.columns.counterparty')}</th>
            <th>{t('camtImport.columns.purpose')}</th>
            <th>{t('camtImport.columns.amount')}</th>
            <th></th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {rows.map(row => {
            const d = decisions[row.key]
            const isImporting = d?.action === 'import'
            const isUnknown = row.unknownAccount && row.status !== 'DUPLICATE'

            const rowClass = (() => {
              if (isUnknown) return 'ri-row ri-row--duplicate'
              if (row.status === 'INVALID') return 'ri-row ri-row--invalid'
              if (row.status === 'DUPLICATE') return 'ri-row ri-row--duplicate'
              if (row.status === 'PREVIOUSLY_IGNORED') {
                return isImporting ? 'ri-row ri-row--prev-ignored-importing' : 'ri-row ri-row--prev-ignored'
              }
              return isImporting ? 'ri-row ri-row--new' : 'ri-row ri-row--will-ignore'
            })()

            return (
              <tr key={row.key} className={rowClass}>
                <td>
                  <StatusBadge row={row} decision={d} />
                </td>
                <td className="ri-cell-date">{row.date ?? '—'}</td>
                <td className={`ri-cell-date${isUnknown ? ' gcv-unknown-iban' : ''}`} title={row.accountIban}>
                  {isUnknown ? t('camtImport.unknownAccount') : row.accountDisplay}
                </td>
                <td className="ri-cell-party" title={row.counterparty ?? ''}>{row.counterparty || '—'}</td>
                <td className="ri-cell-purpose" title={row.purpose ?? ''}>{row.purpose || '—'}</td>
                <td className={`ri-cell-amount${row.amount != null && row.amount < 0 ? ' negative' : ''}`}>
                  {formatAmount(row.amount, row.currency, row.amountRaw)}
                </td>
                <DetailCell row={row} />
                <td className="ri-cell-action">
                  <ActionToggle row={row} decision={d} onDecide={d => onDecide(row.key, d)} />
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function StatusBadge({ row, decision }: { row: ImportPreviewRow; decision: ImportDecision | undefined }) {
  const { t } = useTranslation()
  const isUnknown = row.unknownAccount && row.status !== 'DUPLICATE'
  if (isUnknown) return <span className="ri-badge ri-badge--invalid">{t('camtImport.status.excluded')}</span>
  if (row.status === 'INVALID') return <span className="ri-badge ri-badge--invalid">{t('camtImport.status.invalid')}</span>
  if (row.status === 'DUPLICATE') return <span className="ri-badge ri-badge--duplicate">{t('camtImport.status.duplicate')}</span>
  if (row.status === 'PREVIOUSLY_IGNORED') {
    return decision?.action === 'import'
      ? <span className="ri-badge ri-badge--new">{t('camtImport.status.importing')}</span>
      : <span className="ri-badge ri-badge--prev-ignored">{t('camtImport.status.previouslyIgnored')}</span>
  }
  return decision?.action === 'ignore'
    ? <span className="ri-badge ri-badge--will-ignore">{t('camtImport.status.willIgnore')}</span>
    : <span className="ri-badge ri-badge--new">{t('camtImport.status.new')}</span>
}

function DetailCell({ row }: { row: ImportPreviewRow }) {
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
  return <td className="ri-cell-muted">—</td>
}

function ActionToggle({
  row, decision, onDecide,
}: {
  row: ImportPreviewRow
  decision: ImportDecision | undefined
  onDecide: (d: ImportDecision) => void
}) {
  const { t } = useTranslation()
  const isUnknown = row.unknownAccount && row.status !== 'DUPLICATE'
  if (isUnknown || row.status === 'INVALID' || row.status === 'DUPLICATE') return null

  if (row.status === 'PREVIOUSLY_IGNORED') {
    return decision?.action === 'import' ? (
      <button className="ri-action-btn ri-action-btn--ignore" onClick={() => onDecide({ action: 'ignore' })}>
        {t('camtImport.skipAgain')}
      </button>
    ) : (
      <button className="ri-action-btn ri-action-btn--import" onClick={() => onDecide({ action: 'import' })}>
        {t('camtImport.importAnyway')}
      </button>
    )
  }

  return decision?.action === 'ignore' ? (
    <button className="ri-action-btn ri-action-btn--import" onClick={() => onDecide({ action: 'import' })}>
      {t('common.undo')}
    </button>
  ) : (
    <button className="ri-action-btn ri-action-btn--ignore" onClick={() => onDecide({ action: 'ignore' })}>
      {t('common.skip')}
    </button>
  )
}
