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

function parseDateForSort(date: string | null): string {
  if (!date) return ''
  const [d, m, y] = date.split('.')
  return `${y}${m}${d}`
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

  const sortedRows = [...rows].sort((a, b) =>
    parseDateForSort(b.date).localeCompare(parseDateForSort(a.date)),
  )

  return (
    <div className="ri-table-wrap">
      <div className="ri-cards">
        {sortedRows.map(row => {
          const d = decisions[row.key]
          const isImporting = d?.action === 'import'
          const isUnknown = row.unknownAccount && row.status !== 'DUPLICATE'
          const isActionable = !isUnknown && row.status !== 'INVALID' && row.status !== 'DUPLICATE'

          const cardStatusClass = (() => {
            if (isUnknown) return 'ri-card--duplicate'
            if (row.status === 'INVALID') return 'ri-card--invalid'
            if (row.status === 'DUPLICATE') return 'ri-card--duplicate'
            if (row.status === 'PREVIOUSLY_IGNORED') {
              return isImporting ? 'ri-card--prev-ignored-importing' : 'ri-card--prev-ignored'
            }
            return isImporting ? 'ri-card--new' : 'ri-card--will-ignore'
          })()

          const ghostClass = (row.status === 'DUPLICATE' || isUnknown) ? ' txn-card--ghost' : ''

          return (
            <div key={row.key} className={`txn-card ${cardStatusClass}${ghostClass}`}>
              <div className="txn-card-header">
                <div className="txn-card-header-left">
                  <StatusBadge row={row} decision={d} />
                  <span className="txn-card-date">{row.date ?? '—'}</span>
                </div>
                <span className={`txn-card-amount${row.amount != null && row.amount < 0 ? ' negative' : ' positive'}`}>
                  {formatAmount(row.amount, row.currency, row.amountRaw)}
                </span>
              </div>

              <div className="txn-card-body">
                <span className="txn-card-counterparty">
                  {row.counterparty || '—'}
                </span>
                {row.purpose && (
                  <span className="txn-card-purpose">{row.purpose}</span>
                )}
                <span
                  className={`ri-card-account${isUnknown ? ' gcv-unknown-iban' : ''}`}
                  title={row.accountIban}
                >
                  {isUnknown ? t('camtImport.unknownAccount') : row.accountDisplay}
                </span>
              </div>

              <ErrorTags row={row} />

              {isActionable && (
                <div className="txn-card-footer">
                  <ActionToggle row={row} decision={d} onDecide={d => onDecide(row.key, d)} />
                </div>
              )}
            </div>
          )
        })}
      </div>
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

function ErrorTags({ row }: { row: ImportPreviewRow }) {
  if (row.status !== 'INVALID' || row.errors.length === 0) return null
  return (
    <div className="ri-card-errors">
      {row.errors.map((err, i) => (
        <span key={i} className="ri-error-tag" title={err.message}>
          {err.column}: <em>{err.value || '∅'}</em>
        </span>
      ))}
    </div>
  )
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
        {t('camtImport.ignoreAgain')}
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
      {t('camtImport.ignore')}
    </button>
  )
}
