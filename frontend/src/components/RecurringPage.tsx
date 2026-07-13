import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { RefreshCw } from 'lucide-react'
import {
  fetchRecurringSeries,
  refreshRecurringSeries,
  type RecurringSeriesItem,
  type RecurrenceDirection,
} from '../api/recurring'

type PageState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'ready'; series: RecurringSeriesItem[] }

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

function formatAmount(amount: number, currency: string): string {
  return new Intl.NumberFormat('de-DE', { style: 'currency', currency }).format(Math.abs(amount))
}

export default function RecurringPage() {
  const { t } = useTranslation()
  const [state, setState] = useState<PageState>({ phase: 'idle' })
  const [filterDirection, setFilterDirection] = useState<RecurrenceDirection | undefined>()
  const [expanded, setExpanded] = useState<Set<number>>(new Set())
  const [refreshing, setRefreshing] = useState(false)

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
      setState({ phase: 'ready', series })
    } catch (e) {
      setState({ phase: 'error', message: e instanceof Error ? e.message : t('common.requestFailed') })
    } finally {
      setRefreshing(false)
    }
  }

  function toggleExpand(id: number) {
    setExpanded(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const allSeries = state.phase === 'ready' ? state.series : []
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
        <button className="rcr-refresh-btn" onClick={refresh} disabled={refreshing}>
          <RefreshCw size={14} className={refreshing ? 'rcr-spin' : ''} />
          {refreshing ? t('recurring.refreshing') : t('recurring.refresh')}
        </button>
      </div>

      {state.phase === 'loading' && <p className="hint loading">{t('common.fetching')}</p>}
      {state.phase === 'error' && <p className="hint error">{state.message}</p>}
      {state.phase === 'ready' && displaySeries.length === 0 && (
        <p className="hint">{t('recurring.empty')}</p>
      )}

      {state.phase === 'ready' && displaySeries.length > 0 && (
        <table className="rcr-table">
          <thead>
            <tr>
              <th></th>
              <th>{t('common.amount')}</th>
              <th>{t('recurring.nextExpected')}</th>
              <th>{t('recurring.lastSeen')}</th>
              <th>{t('recurring.occurrences')}</th>
              <th>{t('recurring.account')}</th>
            </tr>
          </thead>
          <tbody>
            {displaySeries.map(s => {
              const isExpanded = expanded.has(s.id)
              return (
                <>
                  <tr key={s.id} className="rcr-row" onClick={() => toggleExpand(s.id)}>
                    <td className="rcr-cell-main">
                      <div className="rcr-label">{s.label}</div>
                      <div className="rcr-badges">
                        <span
                          className="rcr-badge"
                          style={{ color: TYPE_COLORS[s.type] ?? '#6b7280', borderColor: TYPE_COLORS[s.type] ?? '#6b7280' }}
                        >
                          {t(`recurring.type.${s.type}`)}
                        </span>
                        <span className="rcr-badge rcr-badge--cadence">
                          {t(`recurring.cadence.${s.cadence}`)}
                        </span>
                        {s.amountVariable && (
                          <span className="rcr-badge rcr-badge--variable">~</span>
                        )}
                        <span
                          className="rcr-badge"
                          style={{ color: DEVIATION_COLORS[s.deviation] ?? '#6b7280', borderColor: DEVIATION_COLORS[s.deviation] ?? '#6b7280' }}
                        >
                          {t(`recurring.deviation.${s.deviation}`)}
                        </span>
                      </div>
                    </td>
                    <td className={`rcr-amount ${s.direction === 'EXPENSE' ? 'rcr-amount--expense' : 'rcr-amount--income'}`}>
                      {s.direction === 'EXPENSE' ? '−' : '+'}{formatAmount(s.expectedAmount, s.currency)}
                    </td>
                    <td className="rcr-date">{s.nextExpectedDate}</td>
                    <td className="rcr-date">{s.lastSeen}</td>
                    <td className="rcr-count">{s.occurrenceCount}×</td>
                    <td className="rcr-iban">{s.accountIban}</td>
                  </tr>
                  {isExpanded && (
                    <tr key={`${s.id}-history`} className="rcr-history-row">
                      <td colSpan={6}>
                        <table className="rcr-history-table">
                          <thead>
                            <tr>
                              <th>{t('recurring.history.date')}</th>
                              <th>{t('recurring.history.amount')}</th>
                            </tr>
                          </thead>
                          <tbody>
                            {s.occurrences.map(o => (
                              <tr key={o.transactionId}>
                                <td>{o.date}</td>
                                <td className={o.amount < 0 ? 'rcr-amount--expense' : 'rcr-amount--income'}>
                                  {formatAmount(o.amount, s.currency)}
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
