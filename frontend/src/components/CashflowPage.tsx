import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ResponsiveBar } from '@nivo/bar'
import { useIsMobile } from '@/hooks/use-mobile'
import { fetchAllTransactions, fetchCashflow, fetchLinkedGroup, type LinkedGroupItem, type TransactionItem } from '../api/transactions'
import { fetchCollection, type CollectionDto } from '../api/collections'
import { GroupCard } from './GroupCard'
import { CollectionCard } from './CollectionCard'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { TransactionModal } from './TransactionModal'

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

export default function CashflowPage({ from, to, accountId }: { from: string; to: string; accountId?: number }) {
  const { t } = useTranslation()
  const isMobile = useIsMobile()
  const location = useLocation()
  const [granularity, setGranularity] = useState<Granularity>('monthly')
  const [incomeMode, setIncomeMode] = useState<IncomeMode>('all')
  const [expenseMode, setExpenseMode] = useState<ExpenseMode>('all')
  const [loading, setLoading] = useState(false)
  const [rawData, setRawData] = useState<RawBucket[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [drilldown, setDrilldown] = useState<DrilldownState | null>(null)
  const [groupModal, setGroupModal] = useState<{ groupId: number; group: LinkedGroupItem | null } | null>(null)
  const [collectionModal, setCollectionModal] = useState<{ collectionId: number; collection: CollectionDto | null } | null>(null)

  const data: CashflowBucket[] | null = rawData ? toDisplayBuckets(rawData, incomeMode, expenseMode) : null

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void load() }, [from, to, accountId])

  async function load() {
    setLoading(true)
    setError(null)
    try {
      const resp = await fetchCashflow(from, to, granularity, accountId)
      const buckets: RawBucket[] = resp.buckets.map(b => ({
        period: bucketLabel(b.key, granularity),
        periodKey: b.key,
        incomeAll: Math.round(b.incomeGross),
        incomeUnnetted: Math.round(b.incomeNet),
        expensesAll: Math.round(b.expensesGross),
        expensesUnnetted: Math.round(b.expensesNet),
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
      const resp = await fetchAllTransactions(range.from, range.to, accountId, undefined, undefined, undefined, undefined, type === 'income' ? 'INCOME' : 'EXPENSES')
      const txs = resp.transactions
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

  const toggleBtn = (active: boolean) =>
    `rounded-md border px-3 py-1.5 text-sm transition-colors ${active ? 'bg-primary text-primary-foreground border-transparent' : 'border-input bg-input/30 hover:bg-input/50'}`

  return (
    <div className="flex flex-col h-full">
      <div className="flex flex-wrap items-center gap-3 border-b px-4 py-2 shrink-0">
        <div className="flex gap-1">
          {(['monthly', 'yearly'] as const).map(g => (
            <button
              key={g}
              className={toggleBtn(granularity === g)}
              onClick={() => setGranularity(g)}
            >
              {g === 'monthly' ? t('cashflow.monthly') : t('cashflow.yearly')}
            </button>
          ))}
        </div>
        <div className="flex gap-1">
          {(['all', 'unnetted'] as const).map(mode => (
            <button
              key={mode}
              className={toggleBtn(incomeMode === mode)}
              onClick={() => setIncomeMode(mode)}
            >
              {mode === 'all' ? t('cashflow.incomeModeAll') : t('cashflow.incomeModeUnnetted')}
            </button>
          ))}
        </div>
        <div className="flex gap-1">
          {(['all', 'unnetted'] as const).map(mode => (
            <button
              key={mode}
              className={toggleBtn(expenseMode === mode)}
              onClick={() => setExpenseMode(mode)}
            >
              {mode === 'all' ? t('cashflow.expenseModeAll') : t('cashflow.expenseModeUnnetted')}
            </button>
          ))}
        </div>
        {totals && (
          <div className="flex flex-wrap items-center gap-3 text-sm ml-auto">
            <span className="flex flex-col gap-0.5">
              <span className="text-xs text-muted-foreground">{t('cashflow.income')}</span>
              <span className="font-medium tabular-nums text-green-500">{EUR.format(totals.income)}</span>
            </span>
            <span className="text-muted-foreground">·</span>
            <span className="flex flex-col gap-0.5">
              <span className="text-xs text-muted-foreground">{t('cashflow.expenses')}</span>
              <span className="font-medium tabular-nums text-destructive">{EUR.format(totals.expenses)}</span>
            </span>
            <span className="text-muted-foreground">·</span>
            <span className="flex flex-col gap-0.5">
              <span className="text-xs text-muted-foreground">{t('cashflow.net')}</span>
              <span className={`font-medium tabular-nums ${totals.income - totals.expenses >= 0 ? 'text-green-500' : 'text-destructive'}`}>{EUR.format(totals.income - totals.expenses)}</span>
            </span>
          </div>
        )}
      </div>

      <div className="flex-1 relative">
        {error && <p className="hint error">{error}</p>}
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
            margin={{ top: 24, right: 24, bottom: data.length > 20 ? 72 : 48, left: isMobile ? 60 : 88 }}
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
          onOpenGroup={groupId => {
            setGroupModal({ groupId, group: null })
            fetchLinkedGroup(groupId).then(g => setGroupModal({ groupId, group: g }))
          }}
          onOpenCollection={collectionId => {
            setCollectionModal({ collectionId, collection: null })
            fetchCollection(collectionId).then(c => setCollectionModal({ collectionId, collection: c }))
          }}
        />
      )}

      {groupModal && (() => {
        const { groupId, group } = groupModal
        const close = () => setGroupModal(null)
        const deepLinkSearch = new URLSearchParams(location.search)
        deepLinkSearch.set('group', String(groupId))
        return (
          <Dialog open onOpenChange={open => { if (!open) close() }}>
            <DialogContent className="max-w-3xl max-h-[85vh] overflow-y-auto">
              <DialogHeader>
                <DialogTitle>
                  <Link to={{ pathname: '/verknuepfungen', search: deepLinkSearch.toString() }} onClick={close} className="hover:underline">
                    {t('linked.group')} #{groupId} ↗
                  </Link>
                </DialogTitle>
              </DialogHeader>
              {!group ? <p className="text-sm text-muted-foreground">{t('common.loading')}</p> : (
                <GroupCard
                  group={group}
                  onMetaChange={(_id, name, comment) => setGroupModal(prev => prev?.group ? { ...prev, group: { ...prev.group, name, comment } } : prev)}
                  onOffsetCommentChange={(_gid, txId, linkId, comment) => setGroupModal(prev => {
                    if (!prev?.group) return prev
                    return { ...prev, group: { ...prev.group, transactions: prev.group.transactions.map(tx => tx.id !== txId ? tx : { ...tx, offsetLinks: tx.offsetLinks.map(l => l.id === linkId ? { ...l, comment } : l) }) } }
                  })}
                  onRemoveTransaction={txId => {
                    const remaining = group.transactions.filter(tx => tx.id !== txId)
                    if (remaining.length >= 2) setGroupModal(prev => prev ? { ...prev, group: { ...group, transactions: remaining } } : null)
                    else setGroupModal(null)
                  }}
                />
              )}
            </DialogContent>
          </Dialog>
        )
      })()}

      {collectionModal && (() => {
        const { collectionId, collection } = collectionModal
        const close = () => setCollectionModal(null)
        const deepLinkSearch = new URLSearchParams(location.search)
        deepLinkSearch.set('collection', String(collectionId))
        return (
          <Dialog open onOpenChange={open => { if (!open) close() }}>
            <DialogContent className="max-w-3xl max-h-[85vh] overflow-y-auto">
              <DialogHeader>
                <DialogTitle>
                  <Link to={{ pathname: '/sammlungen', search: deepLinkSearch.toString() }} onClick={close} className="hover:underline">
                    {t('collections.collection')} #{collectionId} ↗
                  </Link>
                </DialogTitle>
              </DialogHeader>
              {!collection ? <p className="text-sm text-muted-foreground">{t('common.loading')}</p> : (
                <CollectionCard
                  collection={collection}
                  onUpdate={(_id, name, note) => setCollectionModal(prev => prev?.collection ? { ...prev, collection: { ...prev.collection, name, note } } : prev)}
                  onDelete={() => setCollectionModal(null)}
                  onRemoveTransaction={(_, txId) => setCollectionModal(prev => prev?.collection ? { ...prev, collection: { ...prev.collection, transactions: prev.collection.transactions.filter(tx => tx.id !== txId) } } : prev)}
                  onAddTransaction={() => {}}
                />
              )}
            </DialogContent>
          </Dialog>
        )
      })()}
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
  onOpenGroup,
  onOpenCollection,
}: {
  state: DrilldownState
  onClose: () => void
  onOpenGroup: (groupId: number) => void
  onOpenCollection: (collectionId: number) => void
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

  const modalTitle = (
    <>
      <span style={{ color: state.type === 'income' ? '#4ade80' : '#f87171' }}>{title}</span>
      <span className="text-sm font-normal text-muted-foreground ml-2">{state.period} · {state.from} → {state.to}</span>
    </>
  )

  const footer = !state.loading && transactions.length > 0
    ? (
      <>
        <span className="text-sm text-muted-foreground">{t('common.total')}</span>
        <span className="font-medium tabular-nums">{EUR2.format(total)}</span>
      </>
    )
    : undefined

  return (
    <TransactionModal onClose={onClose} title={modalTitle} footer={footer}>
      {state.loading && <p className="px-5 py-6 text-sm text-muted-foreground">{t('common.loading')}</p>}
      {!state.loading && state.transactions != null && state.transactions.length === 0 && (
        <p className="px-5 py-6 text-sm text-muted-foreground">{t('cashflow.noTransactions')}</p>
      )}
      {!state.loading && state.transactions != null && state.transactions.length > 0 && (
        <table className="w-full text-sm">
          <thead className="border-b">
            <tr>
              <th className="px-3 py-2 text-left font-medium whitespace-nowrap">{t('common.date')}</th>
              <th className="px-3 py-2 text-left font-medium">{t('common.category')}</th>
              <th className="px-3 py-2 text-left font-medium">{t('common.purpose')}</th>
              <th className="px-3 py-2 text-right font-medium whitespace-nowrap">{t('common.amount')}</th>
            </tr>
          </thead>
          <tbody>
            {transactions.map(tx => {
              const isIncome = state.type === 'income'
              const showNetted = isIncome ? state.incomeMode === 'unnetted' : state.expenseMode === 'unnetted'
              let displayAmount: number, nettedAmount: number, fullyNetted: boolean
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
                <tr key={tx.id} className="border-b hover:bg-muted/30" style={fullyNetted ? { color: '#6b6b78' } : undefined}>
                  <td className="px-3 py-2 text-xs tabular-nums whitespace-nowrap text-muted-foreground">{tx.accountingDate}</td>
                  <td className="px-3 py-2 text-xs">{tx.category}{tx.subcategory ? ` / ${tx.subcategory}` : ''}</td>
                  <td className="px-3 py-2 text-xs max-w-48 truncate text-muted-foreground" title={tx.purpose ?? undefined}>{tx.purpose ?? '—'}</td>
                  <td className="px-3 py-2 text-right tabular-nums">
                    <div className="flex flex-col items-end gap-0.5">
                      {!fullyNetted && displayAmount > 0 && <span>{EUR2.format(displayAmount)}</span>}
                      {nettedAmount > 0 && <span className="text-muted-foreground text-xs">{fullyNetted ? EUR2.format(Math.abs(tx.amount)) : `(${EUR2.format(nettedAmount)} ${t('cashflow.netted')})`}</span>}
                      {tx.groups.length > 0 && (
                        <div className="flex flex-wrap gap-1 justify-end">
                          {tx.groups.map(g => <button key={g.id} className="rounded-full border border-border px-2 py-0.5 text-xs text-muted-foreground hover:text-foreground" onClick={() => onOpenGroup(g.id)}>{g.name ?? `#${g.id}`}</button>)}
                        </div>
                      )}
                      {tx.collections.length > 0 && (
                        <div className="flex flex-wrap gap-1 justify-end">
                          {tx.collections.map(c => <button key={c.id} className="rounded-full border border-blue-500/30 bg-blue-500/10 px-2 py-0.5 text-xs text-blue-400 hover:text-blue-300" onClick={() => onOpenCollection(c.id)}>{c.name}</button>)}
                        </div>
                      )}
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}
    </TransactionModal>
  )
}
