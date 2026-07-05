import { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { ResponsiveBar } from '@nivo/bar'
import { fetchAllTransactions, type TransactionItem } from '../api/transactions'

type Granularity = 'monthly' | 'yearly'
type IncomeMode = 'all' | 'unnetted'
type ExpenseMode = 'all' | 'unnetted'

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 })
const EUR2 = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })


interface RawBucket {
  period: string
  periodKey: string
  incomeAll: number
  incomeUnnetted: number
  expensesAll: number
  expensesUnnetted: number
}

interface CashflowBucket {
  [key: string]: string | number
  period: string
  periodKey: string
  income: number
  incomeOffset: number
  expenses: number
  expensesOffset: number
  net: number
}

interface DrilldownState {
  period: string
  type: 'income' | 'expenses'
  from: string
  to: string
  transactions: TransactionItem[] | null
  loading: boolean
  incomeMode: IncomeMode
  expenseMode: ExpenseMode
}

function bucketKey(date: string, gran: Granularity): string {
  return gran === 'monthly' ? date.slice(0, 7) : date.slice(0, 4)
}

function bucketLabel(key: string, gran: Granularity): string {
  if (gran === 'yearly') return key
  const [y, m] = key.split('-').map(Number)
  return new Intl.DateTimeFormat('de-DE', { month: 'short', year: '2-digit' }).format(new Date(y, m - 1, 1))
}

function periodRange(
  key: string,
  gran: Granularity,
  selectedFrom: string,
  selectedTo: string,
): { from: string; to: string } {
  let start: string
  let end: string
  if (gran === 'yearly') {
    start = `${key}-01-01`
    end = `${key}-12-31`
  } else {
    const [y, m] = key.split('-').map(Number)
    start = `${key}-01`
    const lastDay = new Date(y, m, 0).getDate()
    end = `${key}-${String(lastDay).padStart(2, '0')}`
  }
  return {
    from: start > selectedFrom ? start : selectedFrom,
    to: end < selectedTo ? end : selectedTo,
  }
}

function toDisplayBuckets(raw: RawBucket[], incomeMode: IncomeMode, expenseMode: ExpenseMode): CashflowBucket[] {
  return raw.map(b => {
    const income = incomeMode === 'all' ? b.incomeAll : b.incomeUnnetted
    const incomeOffset = incomeMode === 'all' ? 0 : b.incomeAll - b.incomeUnnetted
    const expenses = expenseMode === 'all' ? b.expensesAll : b.expensesUnnetted
    const expensesOffset = expenseMode === 'all' ? 0 : b.expensesAll - b.expensesUnnetted
    return {
      period: b.period,
      periodKey: b.periodKey,
      income,
      incomeOffset,
      expenses,
      expensesOffset,
      net: income - expenses,
    }
  })
}

const NIVO_THEME = {
  background: 'transparent',
  text: { fill: '#6b6b78', fontSize: 11, fontFamily: "ui-monospace, 'SF Mono', Consolas, monospace" },
  grid: { line: { stroke: '#222228', strokeWidth: 1 } },
  tooltip: { container: { display: 'none' } },
}

export default function CashflowPage({ from, to, iban, onNavigateToGroup }: { from: string; to: string; iban?: string; onNavigateToGroup?: (groupId: number) => void }) {
  const { t } = useTranslation()
  const [granularity, setGranularity] = useState<Granularity>('monthly')
  const [incomeMode, setIncomeMode] = useState<IncomeMode>('all')
  const [expenseMode, setExpenseMode] = useState<ExpenseMode>('all')
  const [loading, setLoading] = useState(false)
  const [rawData, setRawData] = useState<RawBucket[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [drilldown, setDrilldown] = useState<DrilldownState | null>(null)

  const data: CashflowBucket[] | null = rawData ? toDisplayBuckets(rawData, incomeMode, expenseMode) : null

  async function load() {
    setLoading(true)
    setError(null)
    try {
      const resp = await fetchAllTransactions(from, to, iban)
      const map = new Map<string, { incomeAll: number; incomeUnnetted: number; expensesAll: number; expensesUnnetted: number }>()

      for (const tx of resp.transactions) {
        const key = bucketKey(tx.accountingDate, granularity)
        if (!map.has(key)) map.set(key, { incomeAll: 0, incomeUnnetted: 0, expensesAll: 0, expensesUnnetted: 0 })
        const entry = map.get(key)!
        if (tx.amount >= 0) {
          entry.incomeAll += tx.amount
          entry.incomeUnnetted += Math.max(0, tx.effectiveAmount)
        } else {
          entry.expensesAll += Math.abs(tx.amount)
          entry.expensesUnnetted += Math.abs(Math.min(0, tx.effectiveAmount))
        }
      }

      const buckets: RawBucket[] = [...map.entries()]
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([key, v]) => ({
          period: bucketLabel(key, granularity),
          periodKey: key,
          incomeAll: Math.round(v.incomeAll),
          incomeUnnetted: Math.round(v.incomeUnnetted),
          expensesAll: Math.round(v.expensesAll),
          expensesUnnetted: Math.round(v.expensesUnnetted),
        }))

      setRawData(buckets)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'request failed')
    } finally {
      setLoading(false)
    }
  }

  async function openDrilldown(periodKey: string, period: string, type: 'income' | 'expenses') {
    const range = periodRange(periodKey, granularity, from, to)
    setDrilldown({ period, type, from: range.from, to: range.to, transactions: null, loading: true, incomeMode, expenseMode })
    try {
      const resp = await fetchAllTransactions(range.from, range.to, iban)
      const txs = resp.transactions
        .filter(tx => type === 'income' ? tx.amount >= 0 : tx.amount < 0)
        .sort((a, b) => b.accountingDate.localeCompare(a.accountingDate))
      setDrilldown(prev => prev ? { ...prev, transactions: txs, loading: false } : prev)
    } catch {
      setDrilldown(prev => prev ? { ...prev, transactions: [], loading: false } : prev)
    }
  }

  const totals = data
    ? data.reduce(
        (acc, b) => ({ income: acc.income + b.income, expenses: acc.expenses + b.expenses }),
        { income: 0, expenses: 0 },
      )
    : null

  const yMax = data
    ? Math.max(0, ...data.map(d => Math.max(d.income + d.incomeOffset, d.expenses + d.expensesOffset)))
    : 0
  const yMin = data ? Math.min(0, ...data.map(d => d.net)) : 0

  return (
    <div className="cf-page">
      <div className="cf-controls">
        <div className="cf-gran-toggle">
          {(['monthly', 'yearly'] as const).map(g => (
            <button
              key={g}
              className={`cf-gran-btn${granularity === g ? ' active' : ''}`}
              onClick={() => setGranularity(g)}
            >
              {g === 'monthly' ? t('cashflow.monthly') : t('cashflow.yearly')}
            </button>
          ))}
        </div>
        <div className="cf-gran-toggle">
          {(['all', 'unnetted'] as const).map(mode => (
            <button
              key={mode}
              className={`cf-gran-btn${incomeMode === mode ? ' active' : ''}`}
              onClick={() => setIncomeMode(mode)}
            >
              {mode === 'all' ? t('cashflow.incomeModeAll') : t('cashflow.incomeModeUnnetted')}
            </button>
          ))}
        </div>
        <div className="cf-gran-toggle">
          {(['all', 'unnetted'] as const).map(mode => (
            <button
              key={mode}
              className={`cf-gran-btn${expenseMode === mode ? ' active' : ''}`}
              onClick={() => setExpenseMode(mode)}
            >
              {mode === 'all' ? t('cashflow.expenseModeAll') : t('cashflow.expenseModeUnnetted')}
            </button>
          ))}
        </div>
        <button className="load-btn" onClick={load} disabled={loading}>
          {loading ? '…' : t('common.load')}
        </button>
        {totals && (
          <div className="cf-summary">
            <span className="cf-summary-item cf-summary-income">
              <span className="cf-summary-label">{t('cashflow.income')}</span>
              <span className="cf-summary-val">{EUR.format(totals.income)}</span>
            </span>
            <span className="cf-summary-sep">·</span>
            <span className="cf-summary-item cf-summary-expenses">
              <span className="cf-summary-label">{t('cashflow.expenses')}</span>
              <span className="cf-summary-val">{EUR.format(totals.expenses)}</span>
            </span>
            <span className="cf-summary-sep">·</span>
            <span className={`cf-summary-item cf-summary-net${totals.income - totals.expenses >= 0 ? ' positive' : ' negative'}`}>
              <span className="cf-summary-label">{t('cashflow.net')}</span>
              <span className="cf-summary-val">{EUR.format(totals.income - totals.expenses)}</span>
            </span>
          </div>
        )}
      </div>

      <div className="cf-body">
        {error && <p className="hint error">{error}</p>}
        {!error && data === null && !loading && (
          <p className="hint"><Trans i18nKey="common.selectDateAndLoad"><span /><kbd /></Trans></p>
        )}
        {loading && <p className="hint loading">{t('common.fetching')}</p>}
        {data !== null && data.length === 0 && (
          <p className="hint">{t('cashflow.noTransactions')}</p>
        )}
        {data !== null && data.length > 0 && (
          <ResponsiveBar<CashflowBucket>
            data={data}
            keys={['income', 'expenses']}
            indexBy="period"
            groupMode="grouped"
            colors={({ id }) => id === 'income' ? '#4ade80' : '#f87171'}
            borderRadius={2}
            padding={0.25}
            innerPadding={3}
            valueScale={{ type: 'linear', min: yMin, max: yMax }}
            margin={{ top: 24, right: 24, bottom: data.length > 20 ? 72 : 48, left: 88 }}
            axisBottom={{
              tickSize: 0,
              tickPadding: 10,
              tickRotation: data.length > 18 ? -45 : 0,
            }}
            axisLeft={{
              tickSize: 0,
              tickPadding: 10,
              tickValues: 5,
              format: (v) => EUR.format(v),
            }}
            enableLabel={false}
            enableGridX={false}
            gridYValues={5}
            layers={['grid', 'axes', 'bars', IncomeOffsetLayer, ExpenseOffsetLayer, NetLayer, 'legends']}
            onClick={({ id, data: d }) => {
              if (id === 'income' || id === 'expenses') {
                openDrilldown(d.periodKey, d.period, id)
              }
            }}
            tooltip={({ indexValue, data: d }) => (
              <div className="cf-tooltip">
                <span className="cf-tooltip-period">{indexValue}</span>
                <div className="cf-tooltip-row">
                  <span className="cf-dot cf-dot--income" />
                  <span>{t('cashflow.tooltipIncome')}</span>
                  <span className="cf-tooltip-amt">{EUR.format(d.income)}</span>
                </div>
                {incomeMode === 'unnetted' && d.incomeOffset > 0 && (
                  <div className="cf-tooltip-row" style={{ color: '#6b6b78' }}>
                    <span className="cf-dot" style={{ background: 'rgba(120,120,130,0.5)' }} />
                    <span>{t('cashflow.netted')}</span>
                    <span className="cf-tooltip-amt">{EUR.format(d.incomeOffset)}</span>
                  </div>
                )}
                <div className="cf-tooltip-row">
                  <span className="cf-dot cf-dot--expenses" />
                  <span>{t('cashflow.tooltipExpenses')}</span>
                  <span className="cf-tooltip-amt">{EUR.format(d.expenses)}</span>
                </div>
                {expenseMode === 'unnetted' && d.expensesOffset > 0 && (
                  <div className="cf-tooltip-row" style={{ color: '#6b6b78' }}>
                    <span className="cf-dot" style={{ background: 'rgba(120,120,130,0.5)' }} />
                    <span>{t('cashflow.netted')}</span>
                    <span className="cf-tooltip-amt">{EUR.format(d.expensesOffset)}</span>
                  </div>
                )}
                <div className={`cf-tooltip-row cf-tooltip-net${d.net >= 0 ? ' positive' : ' negative'}`}>
                  <span className="cf-dot cf-dot--net" style={{ background: d.net >= 0 ? '#4ade80' : '#f87171' }} />
                  <span>{t('cashflow.tooltipNet')}</span>
                  <span className="cf-tooltip-amt">{EUR.format(d.net)}</span>
                </div>
              </div>
            )}
            theme={NIVO_THEME}
          />
        )}
      </div>

      {drilldown && (
        <DrilldownModal
          state={drilldown}
          onClose={() => setDrilldown(null)}
          onNavigateToGroup={onNavigateToGroup ? (groupId) => { setDrilldown(null); onNavigateToGroup(groupId) } : undefined}
        />
      )}
    </div>
  )
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function IncomeOffsetLayer({ bars, yScale }: any) {
  if (!bars || bars.length === 0) return null
  return (
    <g>
      {bars
        .filter((bar: any) => bar.data.id === 'income' && (bar.data.data.incomeOffset as number) > 0)
        .map((bar: any, i: number) => {
          const offsetHeight = yScale(0) - yScale(bar.data.data.incomeOffset as number)
          return (
            <rect
              key={i}
              x={bar.x}
              y={bar.y - offsetHeight}
              width={bar.width}
              height={offsetHeight}
              fill="rgba(120,120,130,0.35)"
              rx={2}
            />
          )
        })}
    </g>
  )
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function ExpenseOffsetLayer({ bars, yScale }: any) {
  if (!bars || bars.length === 0) return null
  return (
    <g>
      {bars
        .filter((bar: any) => bar.data.id === 'expenses' && (bar.data.data.expensesOffset as number) > 0)
        .map((bar: any, i: number) => {
          const offsetHeight = yScale(0) - yScale(bar.data.data.expensesOffset as number)
          return (
            <rect
              key={i}
              x={bar.x}
              y={bar.y - offsetHeight}
              width={bar.width}
              height={offsetHeight}
              fill="rgba(120,120,130,0.35)"
              rx={2}
            />
          )
        })}
    </g>
  )
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function NetLayer({ bars, yScale, innerWidth }: any) {
  if (!bars || bars.length === 0) return null

  const grouped = new Map<string, { cx: number; net: number }>()
  for (const bar of bars) {
    const key = bar.data.indexValue as string
    if (!grouped.has(key)) {
      grouped.set(key, {
        cx: bar.x + bar.width,
        net: bar.data.data.net as number,
      })
    } else {
      const first = grouped.get(key)!
      grouped.set(key, {
        cx: (first.cx - bars[0].width + bar.x + bar.width) / 2,
        net: first.net,
      })
    }
  }

  const points = [...grouped.values()]
  if (points.length < 2) return null

  const linePoints = points.map(p => `${p.cx},${yScale(p.net)}`).join(' ')

  return (
    <g>
      <polyline
        points={linePoints}
        fill="none"
        stroke="rgba(255,255,255,0.18)"
        strokeWidth={1.5}
        strokeDasharray="4 3"
        strokeLinejoin="round"
      />
      {points.map((p, i) => (
        <circle
          key={i}
          cx={p.cx}
          cy={yScale(p.net)}
          r={3}
          fill={p.net >= 0 ? '#4ade80' : '#f87171'}
          stroke="var(--bg)"
          strokeWidth={1.5}
        />
      ))}
      <line
        x1={0}
        x2={innerWidth}
        y1={yScale(0)}
        y2={yScale(0)}
        stroke="rgba(255,255,255,0.12)"
        strokeWidth={1}
        strokeDasharray="2 4"
      />
    </g>
  )
}

function DrilldownModal({
  state,
  onClose,
  onNavigateToGroup,
}: {
  state: DrilldownState
  onClose: () => void
  onNavigateToGroup?: (groupId: number) => void
}) {
  const { t } = useTranslation()
  const title = state.type === 'income' ? t('cashflow.income') : t('cashflow.expenses')

  const transactions = state.transactions ?? []
  const total = transactions.reduce((s, tx) => {
    if (state.type === 'income') {
      return state.incomeMode === 'all' ? s + tx.amount : s + Math.max(0, tx.effectiveAmount)
    } else {
      return state.expenseMode === 'all' ? s + Math.abs(tx.amount) : s + Math.abs(Math.min(0, tx.effectiveAmount))
    }
  }, 0)

  return (
    <div className="bgt-dd-backdrop" onClick={onClose}>
      <div className="bgt-dd-panel" onClick={e => e.stopPropagation()}>
        <div className="bgt-dd-header">
          <div className="bgt-dd-title">
            <span
              className="bgt-dd-name"
              style={{ color: state.type === 'income' ? '#4ade80' : '#f87171' }}
            >
              {title}
            </span>
            <span className="bgt-dd-range">{state.period} · {state.from} → {state.to}</span>
          </div>
          <button className="bgt-dd-close" onClick={onClose}>✕</button>
        </div>

        {state.loading && <p className="bgt-dd-hint">{t('common.loading')}</p>}

        {!state.loading && state.transactions != null && state.transactions.length === 0 && (
          <p className="bgt-dd-hint">{t('cashflow.noTransactions')}</p>
        )}

        {!state.loading && state.transactions != null && state.transactions.length > 0 && (
          <>
            <div className="bgt-dd-scroll">
              <table className="bgt-dd-table">
                <thead>
                  <tr>
                    <th>{t('common.date')}</th>
                    <th>{t('common.category')}</th>
                    <th>{t('common.purpose')}</th>
                    <th style={{ textAlign: 'right' }}>{t('common.amount')}</th>
                  </tr>
                </thead>
                <tbody>
                  {transactions.map(tx => {
                    const isIncome = state.type === 'income'
                    const showNetted = isIncome
                      ? state.incomeMode === 'unnetted'
                      : state.expenseMode === 'unnetted'

                    let displayAmount: number
                    let nettedAmount: number
                    let fullyNetted: boolean

                    if (isIncome) {
                      fullyNetted = showNetted && tx.effectiveAmount <= 0
                      displayAmount = showNetted ? Math.max(0, tx.effectiveAmount) : tx.amount
                      nettedAmount = showNetted ? Math.max(0, tx.amount - tx.effectiveAmount) : 0
                    } else {
                      fullyNetted = showNetted && tx.effectiveAmount >= 0
                      displayAmount = showNetted ? Math.abs(Math.min(0, tx.effectiveAmount)) : Math.abs(tx.amount)
                      nettedAmount = showNetted ? Math.abs(tx.amount) - Math.abs(Math.min(0, tx.effectiveAmount)) : 0
                    }

                    return (
                      <tr key={tx.id} style={fullyNetted ? { color: '#6b6b78' } : undefined}>
                        <td className="bgt-dd-cell-date">{tx.accountingDate}</td>
                        <td className="bgt-dd-cell-cat">
                          {tx.category}{tx.subcategory ? ` / ${tx.subcategory}` : ''}
                        </td>
                        <td className="bgt-dd-cell-purpose" title={tx.purpose ?? undefined}>
                          {tx.purpose ?? <span className="bgt-cell-muted">—</span>}
                        </td>
                        <td className="bgt-dd-cell-amount">
                          {!fullyNetted && displayAmount > 0 && (
                            <span>{EUR2.format(displayAmount)}</span>
                          )}
                          {nettedAmount > 0 && (
                            <span style={{ color: '#6b6b78', marginLeft: fullyNetted ? 0 : '0.4em' }}>
                              {fullyNetted
                                ? EUR2.format(Math.abs(tx.amount))
                                : `(${EUR2.format(nettedAmount)} ${t('cashflow.netted')})`}
                            </span>
                          )}
                          {nettedAmount > 0 && tx.groups.length > 0 && onNavigateToGroup && (
                            <div className="cf-group-chips">
                              {tx.groups.map(g => (
                                <button
                                  key={g.id}
                                  className="cf-group-chip"
                                  onClick={() => onNavigateToGroup(g.id)}
                                >
                                  {g.name ?? `#${g.id}`}
                                </button>
                              ))}
                            </div>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
            <div className="bgt-dd-footer">
              <span className="bgt-dd-total-label">{t('common.total')}</span>
              <span className="bgt-dd-total">{EUR2.format(total)}</span>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
