import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { RefreshCw, X, RotateCcw } from 'lucide-react'
import {
  fetchRecurringSeries,
  refreshRecurringSeries,
  confirmRecurringSeries,
  correctRecurringSeriesType,
  type RecurringSeriesItem,
  type RecurrenceDirection,
  type RecurringType,
} from '../api/recurring'

type PageState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'ready'; series: RecurringSeriesItem[] }
  | { phase: 'pending'; series: RecurringSeriesItem[]; dismissed: Set<string> }

const TYPE_COLORS: Record<string, string> = {
  SALARY: '#4ade80',
  RENT: '#f59e0b',
  INSURANCE: '#60a5fa',
  SUBSCRIPTION: '#c084fc',
  UTILITY: '#fb923c',
  LOAN: '#f87171',
  MEMBERSHIP: '#34d399',
  OTHER: '#6b7280',
}

const DEVIATION_COLORS: Record<string, string> = {
  ON_TRACK: '#4ade80',
  AMOUNT_CHANGED: '#f59e0b',
  DATE_SHIFTED: '#60a5fa',
  OVERDUE: '#f87171',
}

const ALL_TYPES: RecurringType[] = [
  'SALARY', 'RENT', 'INSURANCE', 'SUBSCRIPTION', 'UTILITY', 'LOAN', 'MEMBERSHIP', 'OTHER',
]

function formatAmount(amount: number, currency: string): string {
  return new Intl.NumberFormat('de-DE', { style: 'currency', currency }).format(Math.abs(amount))
}

export default function RecurringPage() {
  const { t } = useTranslation()
  const [state, setState] = useState<PageState>({ phase: 'idle' })
  const [filterDirection, setFilterDirection] = useState<RecurrenceDirection | undefined>()
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const [refreshing, setRefreshing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editingType, setEditingType] = useState<number | null>(null)

  useEffect(() => {
    load()
  }, [])

  async function load() {
    setState({ phase: 'loading' })
    try {
      const series = await fetchRecurringSeries()
      setState({ phase: 'ready', series })
    } catch (e) {
      setState({ phase: 'error', message: e instanceof Error ? e.message : t('common.requestFailed') })
    }
  }

  async function refresh() {
    setRefreshing(true)
    try {
      const series = await refreshRecurringSeries()
      // Pre-dismiss backend-known false positives
      const initialDismissed = new Set(series.filter(s => s.isFalsePositive).map(s => s.fingerprint))
      setState({ phase: 'pending', series, dismissed: initialDismissed })
    } catch (e) {
      setState({ phase: 'error', message: e instanceof Error ? e.message : t('common.requestFailed') })
    } finally {
      setRefreshing(false)
    }
  }

  async function save() {
    if (state.phase !== 'pending') return
    setSaving(true)
    try {
      const confirmed = state.series
        .filter(s => !state.dismissed.has(s.fingerprint))
        .map(s => s.fingerprint)
      const falsePositives = [...state.dismissed]
      const saved = await confirmRecurringSeries(confirmed, falsePositives)
      setState({ phase: 'ready', series: saved })
    } catch (e) {
      setState({ phase: 'error', message: e instanceof Error ? e.message : t('common.requestFailed') })
    } finally {
      setSaving(false)
    }
  }

  function cancelPending() {
    load()
  }

  function toggleDismiss(fingerprint: string) {
    if (state.phase !== 'pending') return
    setState(prev => {
      if (prev.phase !== 'pending') return prev
      const next = new Set(prev.dismissed)
      if (next.has(fingerprint)) next.delete(fingerprint)
      else next.add(fingerprint)
      return { ...prev, dismissed: next }
    })
  }

  function toggleExpand(key: string) {
    setExpanded(prev => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  async function handleTypeChange(id: number, newType: RecurringType) {
    setEditingType(null)
    if (state.phase !== 'ready') return
    const prev = state.series
    setState({
      phase: 'ready',
      series: prev.map(s => s.id === id ? { ...s, type: newType } : s),
    })
    try {
      await correctRecurringSeriesType(id, newType)
    } catch {
      setState({ phase: 'ready', series: prev })
    }
  }

  const isPending = state.phase === 'pending'
  const allSeries = (state.phase === 'ready' || state.phase === 'pending') ? state.series : []
  const displaySeries = filterDirection
    ? allSeries.filter(s => s.direction === filterDirection)
    : allSeries

  return (
    <div className="rcr-page">
      <div className="rcr-toolbar">
        <div className="rcr-direction-filter">
          <button
            className={`rcr-filter-btn${!filterDirection ? ' rcr-filter-btn--active' : ''}`}
            onClick={() => setFilterDirection(undefined)}
          >
            {t('recurring.filterAll')}
          </button>
          <button
            className={`rcr-filter-btn${filterDirection === 'EXPENSE' ? ' rcr-filter-btn--active' : ''}`}
            onClick={() => setFilterDirection('EXPENSE')}
          >
            {t('recurring.filterExpenses')}
          </button>
          <button
            className={`rcr-filter-btn${filterDirection === 'INCOME' ? ' rcr-filter-btn--active' : ''}`}
            onClick={() => setFilterDirection('INCOME')}
          >
            {t('recurring.filterIncome')}
          </button>
        </div>
        <div className="rcr-toolbar-actions">
          {isPending ? (
            <>
              <button className="rcr-cancel-btn" onClick={cancelPending} disabled={saving}>
                {t('recurring.cancelPending')}
              </button>
              <button className="rcr-save-btn" onClick={save} disabled={saving}>
                {saving ? t('recurring.saving') : t('recurring.save')}
              </button>
            </>
          ) : (
            <button className="rcr-refresh-btn" onClick={refresh} disabled={refreshing}>
              <RefreshCw size={14} className={refreshing ? 'rcr-spin' : ''} />
              {refreshing ? t('recurring.refreshing') : t('recurring.refresh')}
            </button>
          )}
        </div>
      </div>

      {isPending && (
        <div className="rcr-pending-banner">
          {t('recurring.pendingBanner', { count: allSeries.length })}
        </div>
      )}

      {state.phase === 'loading' && <p className="hint loading">{t('common.fetching')}</p>}
      {state.phase === 'error' && <p className="hint error">{state.message}</p>}
      {(state.phase === 'ready' || state.phase === 'pending') && displaySeries.length === 0 && (
        <p className="hint">{t('recurring.empty')}</p>
      )}

      {(state.phase === 'ready' || state.phase === 'pending') && displaySeries.length > 0 && (
        <table className="rcr-table">
          <thead>
            <tr>
              <th></th>
              <th>{t('common.amount')}</th>
              <th>{t('recurring.nextExpected')}</th>
              <th>{t('recurring.lastSeen')}</th>
              <th>{t('recurring.occurrences')}</th>
              <th>{t('recurring.account')}</th>
              {isPending && <th></th>}
            </tr>
          </thead>
          <tbody>
            {displaySeries.map(s => {
              const rowKey = s.fingerprint || String(s.id)
              const isDismissed = isPending && state.phase === 'pending' && state.dismissed.has(s.fingerprint)
              const isExpanded = expanded.has(rowKey)
              const isEditingThisType = !isPending && s.id !== null && editingType === s.id
              return (
                <>
                  <tr
                    key={rowKey}
                    className={`rcr-row${isDismissed ? ' rcr-row--dismissed' : ''}`}
                    onClick={() => { if (!isEditingThisType) toggleExpand(rowKey) }}
                  >
                    <td className="rcr-cell-main">
                      <div className={`rcr-label${isDismissed ? ' rcr-label--dismissed' : ''}`}>{s.label}</div>
                      <div className="rcr-badges">
                        {isEditingThisType ? (
                          <select
                            className="rcr-type-select"
                            value={s.type}
                            autoFocus
                            onClick={e => e.stopPropagation()}
                            onBlur={() => setEditingType(null)}
                            onChange={e => s.id !== null && handleTypeChange(s.id, e.target.value as RecurringType)}
                          >
                            {ALL_TYPES.map(type => (
                              <option key={type} value={type}>{t(`recurring.type.${type}`)}</option>
                            ))}
                          </select>
                        ) : (
                          <span
                            className={`rcr-badge${!isPending ? ' rcr-badge--clickable' : ''}`}
                            style={{ color: TYPE_COLORS[s.type] ?? '#6b7280', borderColor: TYPE_COLORS[s.type] ?? '#6b7280' }}
                            title={!isPending ? t('recurring.correctType') : undefined}
                            onClick={e => { if (!isPending && s.id !== null) { e.stopPropagation(); setEditingType(s.id) } }}
                          >
                            {t(`recurring.type.${s.type}`)}
                          </span>
                        )}
                        <span className="rcr-badge rcr-badge--cadence">
                          {t(`recurring.cadence.${s.cadence}`)}
                        </span>
                        {s.amountVariable && (
                          <span className="rcr-badge rcr-badge--variable">~</span>
                        )}
                        {!isPending && (
                          <span
                            className="rcr-badge"
                            style={{ color: DEVIATION_COLORS[s.deviation] ?? '#6b7280', borderColor: DEVIATION_COLORS[s.deviation] ?? '#6b7280' }}
                          >
                            {t(`recurring.deviation.${s.deviation}`)}
                          </span>
                        )}
                        {isDismissed && (
                          <span className="rcr-badge rcr-badge--false-positive">
                            {t('recurring.falsePositive')}
                          </span>
                        )}
                      </div>
                    </td>
                    <td className={`rcr-amount ${s.direction === 'EXPENSE' ? 'rcr-amount--expense' : 'rcr-amount--income'}`}>
                      {s.direction === 'EXPENSE' ? '−' : '+'}{formatAmount(s.expectedAmount, s.currency)}
                    </td>
                    <td className="rcr-date">{s.nextExpectedDate}</td>
                    <td className="rcr-date">{s.lastSeen}</td>
                    <td className="rcr-count">{s.occurrenceCount}×</td>
                    <td className="rcr-iban">{s.accountIban}</td>
                    {isPending && (
                      <td className="rcr-cell-actions" onClick={e => e.stopPropagation()}>
                        <button
                          className={`rcr-dismiss-btn${isDismissed ? ' rcr-dismiss-btn--restore' : ''}`}
                          title={isDismissed ? t('recurring.restore') : t('recurring.dismiss')}
                          onClick={() => toggleDismiss(s.fingerprint)}
                        >
                          {isDismissed ? <RotateCcw size={14} /> : <X size={14} />}
                        </button>
                      </td>
                    )}
                  </tr>
                  {isExpanded && (
                    <tr key={`${rowKey}-history`} className="rcr-history-row">
                      <td colSpan={isPending ? 7 : 6}>
                        <table className="rcr-history-table">
                          <thead>
                            <tr>
                              <th>{t('recurring.history.date')}</th>
                              <th>{t('recurring.history.amount')}</th>
                              <th>{t('recurring.history.counterparty')}</th>
                              <th>{t('recurring.history.purpose')}</th>
                            </tr>
                          </thead>
                          <tbody>
                            {s.occurrences.map(o => (
                              <tr key={o.transactionId}>
                                <td>{o.date}</td>
                                <td className={o.amount < 0 ? 'rcr-amount--expense' : 'rcr-amount--income'}>
                                  {formatAmount(o.amount, s.currency)}
                                </td>
                                <td className="rcr-history-detail">
                                  {o.counterpartyName ?? o.counterpartyIban ?? '—'}
                                </td>
                                <td className="rcr-history-detail rcr-history-purpose">
                                  {o.purpose ?? '—'}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </td>
                    </tr>
                  )}
                </>
              )
            })}
          </tbody>
        </table>
      )}
    </div>
  )
}
