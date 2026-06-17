import { useState } from 'react'
import { ResponsiveBar } from '@nivo/bar'
import { fetchAllTransactions, type TransactionItem } from '../api/transactions'

type Granularity = 'monthly' | 'yearly'

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 })
const EUR2 = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

function isoDate(d: Date) { return d.toISOString().slice(0, 10) }

const today = isoDate(new Date())
const threeYearsAgo = isoDate(new Date(new Date().getFullYear() - 2, 0, 1))

interface CashflowBucket {
  [key: string]: string | number
  period: string
  periodKey: string
  income: number
  expenses: number
  net: number
}

interface DrilldownState {
  period: string
  type: 'income' | 'expenses'
  from: string
  to: string
  transactions: TransactionItem[] | null
  loading: boolean
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

const NIVO_THEME = {
  background: 'transparent',
  text: { fill: '#6b6b78', fontSize: 11, fontFamily: "ui-monospace, 'SF Mono', Consolas, monospace" },
  grid: { line: { stroke: '#222228', strokeWidth: 1 } },
  tooltip: { container: { display: 'none' } },
}

export default function CashflowPage() {
  const [from, setFrom] = useState(threeYearsAgo)
  const [to, setTo] = useState(today)
  const [granularity, setGranularity] = useState<Granularity>('monthly')
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState<CashflowBucket[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [drilldown, setDrilldown] = useState<DrilldownState | null>(null)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      const resp = await fetchAllTransactions(from, to)
      const map = new Map<string, { income: number; expenses: number }>()

      for (const tx of resp.transactions) {
        const key = bucketKey(tx.accountingDate, granularity)
        if (!map.has(key)) map.set(key, { income: 0, expenses: 0 })
        const entry = map.get(key)!
        if (tx.effectiveAmount >= 0) {
          entry.income += tx.effectiveAmount
        } else {
          entry.expenses += Math.abs(tx.effectiveAmount)
        }
      }

      const buckets: CashflowBucket[] = [...map.entries()]
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([key, v]) => ({
          period: bucketLabel(key, granularity),
          periodKey: key,
          income: Math.round(v.income),
          expenses: Math.round(v.expenses),
          net: Math.round(v.income - v.expenses),
        }))

      setData(buckets)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'request failed')
    } finally {
      setLoading(false)
    }
  }

  async function openDrilldown(periodKey: string, period: string, type: 'income' | 'expenses') {
    const range = periodRange(periodKey, granularity, from, to)
    setDrilldown({ period, type, from: range.from, to: range.to, transactions: null, loading: true })
    try {
      const resp = await fetchAllTransactions(range.from, range.to)
      const txs = resp.transactions
        .filter(tx => type === 'income' ? tx.effectiveAmount >= 0 : tx.effectiveAmount < 0)
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

  return (
    <div className="cf-page">
      <div className="cf-controls">
        <fieldset className="range-group">
          <label className="range-field">
            <span className="range-label">from</span>
            <input type="date" value={from} max={to} onChange={e => setFrom(e.target.value)} />
          </label>
          <div className="range-sep" />
          <label className="range-field">
            <span className="range-label">to</span>
            <input type="date" value={to} min={from} max={today} onChange={e => setTo(e.target.value)} />
          </label>
        </fieldset>
        <div className="cf-gran-toggle">
          {(['monthly', 'yearly'] as const).map(g => (
            <button
              key={g}
              className={`cf-gran-btn${granularity === g ? ' active' : ''}`}
              onClick={() => setGranularity(g)}
            >
              {g === 'monthly' ? 'monatlich' : 'jährlich'}
            </button>
          ))}
        </div>
        <button className="load-btn" onClick={load} disabled={loading}>
          {loading ? '…' : 'load'}
        </button>
        {totals && (
          <div className="cf-summary">
            <span className="cf-summary-item cf-summary-income">
              <span className="cf-summary-label">einnahmen</span>
              <span className="cf-summary-val">{EUR.format(totals.income)}</span>
            </span>
            <span className="cf-summary-sep">·</span>
            <span className="cf-summary-item cf-summary-expenses">
              <span className="cf-summary-label">ausgaben</span>
              <span className="cf-summary-val">{EUR.format(totals.expenses)}</span>
            </span>
            <span className="cf-summary-sep">·</span>
            <span className={`cf-summary-item cf-summary-net${totals.income - totals.expenses >= 0 ? ' positive' : ' negative'}`}>
              <span className="cf-summary-label">netto</span>
              <span className="cf-summary-val">{EUR.format(totals.income - totals.expenses)}</span>
            </span>
          </div>
        )}
      </div>

      <div className="cf-body">
        {error && <p className="hint error">{error}</p>}
        {!error && data === null && !loading && (
          <p className="hint">Zeitraum wählen und <kbd>load</kbd> drücken</p>
        )}
        {loading && <p className="hint loading">fetching…</p>}
        {data !== null && data.length === 0 && (
          <p className="hint">Keine Transaktionen im Zeitraum.</p>
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
            layers={['grid', 'axes', 'bars', NetLayer, 'legends']}
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
                  <span>einnahmen</span>
                  <span className="cf-tooltip-amt">{EUR.format(d.income)}</span>
                </div>
                <div className="cf-tooltip-row">
                  <span className="cf-dot cf-dot--expenses" />
                  <span>ausgaben</span>
                  <span className="cf-tooltip-amt">{EUR.format(d.expenses)}</span>
                </div>
                <div className={`cf-tooltip-row cf-tooltip-net${d.net >= 0 ? ' positive' : ' negative'}`}>
                  <span className="cf-dot cf-dot--net" style={{ background: d.net >= 0 ? '#4ade80' : '#f87171' }} />
                  <span>netto</span>
                  <span className="cf-tooltip-amt">{EUR.format(d.net)}</span>
                </div>
              </div>
            )}
            theme={NIVO_THEME}
          />
        )}
      </div>

      {drilldown && (
        <DrilldownModal state={drilldown} onClose={() => setDrilldown(null)} />
      )}
    </div>
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
}: {
  state: DrilldownState
  onClose: () => void
}) {
  const title = state.type === 'income' ? 'Einnahmen' : 'Ausgaben'
  const total = state.transactions?.reduce((s, tx) => s + Math.abs(tx.effectiveAmount), 0) ?? 0

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

        {state.loading && <p className="bgt-dd-hint">Lade Transaktionen…</p>}

        {!state.loading && state.transactions != null && state.transactions.length === 0 && (
          <p className="bgt-dd-hint">Keine Transaktionen im Zeitraum.</p>
        )}

        {!state.loading && state.transactions != null && state.transactions.length > 0 && (
          <>
            <div className="bgt-dd-scroll">
              <table className="bgt-dd-table">
                <thead>
                  <tr>
                    <th>datum</th>
                    <th>kategorie</th>
                    <th>verwendungszweck</th>
                    <th style={{ textAlign: 'right' }}>betrag</th>
                  </tr>
                </thead>
                <tbody>
                  {state.transactions.map(tx => (
                    <tr key={tx.id}>
                      <td className="bgt-dd-cell-date">{tx.accountingDate}</td>
                      <td className="bgt-dd-cell-cat">
                        {tx.category}{tx.subcategory ? ` / ${tx.subcategory}` : ''}
                      </td>
                      <td className="bgt-dd-cell-purpose" title={tx.purpose ?? undefined}>
                        {tx.purpose ?? <span className="bgt-cell-muted">—</span>}
                      </td>
                      <td className="bgt-dd-cell-amount">
                        {EUR2.format(Math.abs(tx.effectiveAmount))}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="bgt-dd-footer">
              <span className="bgt-dd-total-label">gesamt</span>
              <span className="bgt-dd-total">{EUR2.format(total)}</span>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
