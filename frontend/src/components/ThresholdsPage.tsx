import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { fetchCategories, type CategoryGroup } from '../api/rawImport'
import {
  deleteThreshold,
  fetchThresholds,
  fetchThresholdStatus,
  saveThreshold,
  type SaveThresholdRequest,
  type Threshold,
  type ThresholdPeriod,
  type ThresholdStatusItem,
} from '../api/thresholds'
import { fetchTransactionList, type TransactionItem } from '../api/transactions'

const PERIODS: ThresholdPeriod[] = ['WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY']
const PERIOD_IDEAL_DAYS: Record<ThresholdPeriod, number> = {
  WEEKLY: 7,
  MONTHLY: 30,
  QUARTERLY: 91,
  YEARLY: 365,
}

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 })
const EUR2 = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

// ── helpers ──────────────────────────────────────────────────────────────

function rowKey(category: string, subcategory: string | null): string {
  return `${category}\0${subcategory ?? ''}`
}

function pickBest(thresholds: Threshold[], from: string, to: string): Threshold | null {
  if (thresholds.length === 0) return null
  if (thresholds.length === 1) return thresholds[0]
  const days = (new Date(to).getTime() - new Date(from).getTime()) / 86_400_000 + 1
  return thresholds.reduce((best, cur) => {
    const score = (p: ThresholdPeriod) => Math.abs(Math.log2(days / PERIOD_IDEAL_DAYS[p]))
    return score(cur.period) < score(best.period) ? cur : best
  })
}

type Status = 'ok' | 'notice' | 'warning' | 'critical'

interface Progress {
  pct: number
  status: Status
  tickNotice: number | null
  tickWarning: number | null
}

function progressFromItem(item: ThresholdStatusItem): Progress {
  return {
    pct: item.pct,
    status: item.status.toLowerCase() as Status,
    tickNotice: item.tickNotice,
    tickWarning: item.tickWarning,
  }
}

// ── form state ────────────────────────────────────────────────────────────

interface FormState {
  period: ThresholdPeriod
  notice: string
  warning: string
  critical: string
}

const EMPTY_FORM: FormState = { period: 'MONTHLY', notice: '', warning: '', critical: '' }

function thresholdToForm(t: Threshold): FormState {
  return {
    period: t.period,
    notice: t.notice?.toString() ?? '',
    warning: t.warning?.toString() ?? '',
    critical: t.critical?.toString() ?? '',
  }
}

function parseAmt(s: string): number | null {
  const n = parseFloat(s.replace(',', '.'))
  return isNaN(n) || n < 0 ? null : n
}

// ── drilldown ─────────────────────────────────────────────────────────────

interface DrilldownState {
  key: string
  category: string
  subcategory: string | null
  transactions: TransactionItem[] | null
  loading: boolean
}

// ── row type ──────────────────────────────────────────────────────────────

interface BudgetRow {
  category: string
  subcategory: string | null
  key: string
  thresholds: Threshold[]
  isFirst: boolean
}

// ── component ─────────────────────────────────────────────────────────────

export default function ThresholdsPage({ from, to, iban }: { from: string; to: string; iban?: string }) {
  const { t } = useTranslation()
  const [categories, setCategories] = useState<CategoryGroup[]>([])
  const [thresholds, setThresholds] = useState<Threshold[]>([])
  const [statusMap, setStatusMap] = useState<Map<string, ThresholdStatusItem>>(new Map())
  const [statusLoaded, setStatusLoaded] = useState(false)
  const [loading, setLoading] = useState(false)
  const [editingKey, setEditingKey] = useState<string | null>(null)
  const [editThresholdId, setEditThresholdId] = useState<number | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [drilldown, setDrilldown] = useState<DrilldownState | null>(null)

  useEffect(() => {
    fetchCategories().then(r => setCategories(r.categories)).catch(() => {})
    fetchThresholds().then(setThresholds).catch(() => {})
  }, [])

  useEffect(() => {
    setStatusLoaded(false)
    setStatusMap(new Map())
  }, [from, to, iban])

  async function loadStatus() {
    setLoading(true)
    try {
      const items = await fetchThresholdStatus(from, to, iban)
      setStatusMap(new Map(items.map(item => [rowKey(item.category, item.subcategory), item])))
      setStatusLoaded(true)
    } catch {
      // silent — user can retry
    } finally {
      setLoading(false)
    }
  }

  // ── derive rows ───────────────────────────────────────────────────────

  const rows = useMemo((): BudgetRow[] => {
    const catSubcats = new Map<string, Set<string>>()
    const hasCategoryRow = new Set<string>()

    for (const cg of categories) {
      if (!catSubcats.has(cg.name)) catSubcats.set(cg.name, new Set())
      hasCategoryRow.add(cg.name)
      for (const s of cg.subcategories) catSubcats.get(cg.name)!.add(s)
    }

    for (const t of thresholds) {
      if (!catSubcats.has(t.category)) catSubcats.set(t.category, new Set())
      if (t.subcategory != null) {
        catSubcats.get(t.category)!.add(t.subcategory)
      } else {
        hasCategoryRow.add(t.category)
      }
    }

    const result: BudgetRow[] = []
    for (const cat of [...catSubcats.keys()].sort()) {
      const subs = [...catSubcats.get(cat)!].sort()
      let isFirst = true

      if (hasCategoryRow.has(cat)) {
        const key = rowKey(cat, null)
        result.push({
          category: cat,
          subcategory: null,
          key,
          thresholds: thresholds.filter(t => t.category === cat && t.subcategory == null),
          isFirst,
        })
        isFirst = false
      }

      for (const sub of subs) {
        const key = rowKey(cat, sub)
        result.push({
          category: cat,
          subcategory: sub,
          key,
          thresholds: thresholds.filter(t => t.category === cat && t.subcategory === sub),
          isFirst,
        })
        isFirst = false
      }
    }
    return result
  }, [categories, thresholds])

  // ── edit handlers ─────────────────────────────────────────────────────

  function startEdit(row: BudgetRow) {
    const best = pickBest(row.thresholds, from, to)
    setEditingKey(row.key)
    setEditThresholdId(best?.id ?? null)
    setForm(best != null ? thresholdToForm(best) : EMPTY_FORM)
    setFormError(null)
  }

  function cancelEdit() {
    setEditingKey(null)
    setEditThresholdId(null)
    setForm(EMPTY_FORM)
    setFormError(null)
  }

  async function handleSave(row: BudgetRow) {
    const req: SaveThresholdRequest = {
      category: row.category,
      subcategory: row.subcategory,
      period: form.period,
      notice: parseAmt(form.notice),
      warning: parseAmt(form.warning),
      critical: parseAmt(form.critical),
    }
    if (req.notice == null && req.warning == null && req.critical == null) {
      setFormError(t('limits.minAmount'))
      return
    }
    setSaving(true)
    setFormError(null)
    try {
      const saved = await saveThreshold(req)
      setThresholds(prev => {
        const idx = prev.findIndex(t => t.id === saved.id)
        if (idx >= 0) {
          const next = [...prev]
          next[idx] = saved
          return next
        }
        return [...prev, saved]
      })
      cancelEdit()
    } catch {
      setFormError(t('limits.saveFailed'))
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete() {
    if (editThresholdId == null) return
    try {
      await deleteThreshold(editThresholdId)
      setThresholds(prev => prev.filter(t => t.id !== editThresholdId))
      cancelEdit()
    } catch {
      setFormError(t('limits.deleteFailed'))
    }
  }

  async function openDrilldown(row: BudgetRow) {
    setDrilldown({ key: row.key, category: row.category, subcategory: row.subcategory, transactions: null, loading: true })
    try {
      const resp = await fetchTransactionList(from, to, row.category, row.subcategory ?? undefined, iban)
      const txs = resp.transactions.filter(tx => tx.effectiveAmount < 0)
      txs.sort((a, b) => b.bookingDate.localeCompare(a.bookingDate))
      setDrilldown(prev => prev?.key === row.key ? { ...prev, transactions: txs, loading: false } : prev)
    } catch {
      setDrilldown(prev => prev?.key === row.key ? { ...prev, transactions: [], loading: false } : prev)
    }
  }

  // ── render ────────────────────────────────────────────────────────────

  return (
    <div className="bgt-page">
      <div className="bgt-controls">
        <button className="load-btn" onClick={loadStatus} disabled={loading}>
          {loading ? '…' : t('limits.load')}
        </button>
        {statusLoaded && (
          <span className="bgt-period-badge">
            {t('limits.statusLoaded', { from, to })}
          </span>
        )}
      </div>

      <div className="bgt-body">
        {rows.length === 0 ? (
          <p className="hint">{t('limits.noCategories')}</p>
        ) : (
          <table className="bgt-table">
            <thead>
              <tr>
                <th className="bgt-th-cat">{t('limits.columns.category')}</th>
                <th className="bgt-th-sub">{t('limits.columns.subcategory')}</th>
                {statusLoaded && <th className="bgt-th-spent">{t('limits.columns.spending')}</th>}
                <th className="bgt-th-period">{t('limits.columns.period')}</th>
                <th className="bgt-th-sev bgt-sev--notice">{t('limits.columns.notice')}</th>
                <th className="bgt-th-sev bgt-sev--warning">{t('limits.columns.warning')}</th>
                <th className="bgt-th-sev bgt-sev--critical">{t('limits.columns.critical')}</th>
                {statusLoaded && <th className="bgt-th-bar">{t('limits.columns.progress')}</th>}
                <th className="bgt-th-actions" />
              </tr>
            </thead>
            <tbody>
              {rows.map(row => {
                const isEditing = editingKey === row.key
                const item = statusLoaded ? statusMap.get(row.key) : undefined
                const best = item ?? pickBest(row.thresholds, from, to)
                const spending = item?.spending ?? 0
                const progress = item != null ? progressFromItem(item) : null

                const rowClass = [
                  'bgt-row',
                  row.isFirst ? 'bgt-row--group-start' : '',
                  isEditing ? 'bgt-row--editing' : '',
                  !isEditing && row.thresholds.length === 0 ? 'bgt-row--no-budget' : '',
                ]
                  .filter(Boolean)
                  .join(' ')

                if (isEditing) {
                  return (
                    <tr key={row.key} className={rowClass}>
                      <td className="bgt-td-cat bgt-cell-cat">{row.category}</td>
                      <td className="bgt-td-sub bgt-cell-muted">{row.subcategory ?? t('limits.totalSubcat')}</td>
                      {statusLoaded && (
                        <td className="bgt-td-spent">
                          {spending > 0 ? EUR2.format(spending) : '—'}
                        </td>
                      )}
                      <td className="bgt-td-period">
                        <select
                          className="bgt-select"
                          value={form.period}
                          onChange={e =>
                            setForm(p => ({ ...p, period: e.target.value as ThresholdPeriod }))
                          }
                        >
                          {PERIODS.map(p => (
                            <option key={p} value={p}>
                              {t(`budgets.period.${p.toLowerCase()}`)}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td className="bgt-td-sev">
                        <input
                          className="bgt-sev-input"
                          type="number"
                          min="0"
                          step="1"
                          placeholder="—"
                          value={form.notice}
                          onChange={e => setForm(p => ({ ...p, notice: e.target.value }))}
                        />
                      </td>
                      <td className="bgt-td-sev">
                        <input
                          className="bgt-sev-input"
                          type="number"
                          min="0"
                          step="1"
                          placeholder="—"
                          value={form.warning}
                          onChange={e => setForm(p => ({ ...p, warning: e.target.value }))}
                        />
                      </td>
                      <td className="bgt-td-sev">
                        <input
                          className="bgt-sev-input"
                          type="number"
                          min="0"
                          step="1"
                          placeholder="—"
                          value={form.critical}
                          onChange={e => setForm(p => ({ ...p, critical: e.target.value }))}
                        />
                      </td>
                      {statusLoaded && <td />}
                      <td className="bgt-td-edit-actions">
                        <button
                          className="bgt-btn bgt-btn--save"
                          onClick={() => handleSave(row)}
                          disabled={saving}
                        >
                          {saving ? '…' : t('common.save')}
                        </button>
                        {editThresholdId != null && (
                          <button className="bgt-btn bgt-btn--delete" onClick={handleDelete}>
                            {t('common.delete')}
                          </button>
                        )}
                        <button className="bgt-btn bgt-btn--cancel" onClick={cancelEdit}>
                          ✕
                        </button>
                        {formError != null && (
                          <span className="bgt-form-error">{formError}</span>
                        )}
                      </td>
                    </tr>
                  )
                }

                return (
                  <tr key={row.key} className={rowClass} onClick={() => startEdit(row)}>
                    <td className="bgt-td-cat bgt-cell-cat">
                      {row.isFirst ? row.category : ''}
                    </td>
                    <td
                      className={`bgt-td-sub ${row.subcategory == null ? 'bgt-cell-all' : 'bgt-cell-sub'}`}
                    >
                      {row.subcategory ?? t('limits.totalSubcat')}
                    </td>
                    {statusLoaded && (
                      <td className={`bgt-td-spent${spending === 0 ? ' bgt-cell-muted' : ''}`}>
                        {spending > 0 ? (
                          <button
                            className="bgt-spent-btn"
                            onClick={e => { e.stopPropagation(); openDrilldown(row) }}
                          >
                            {EUR2.format(spending)}
                          </button>
                        ) : '—'}
                      </td>
                    )}
                    <td className="bgt-td-period bgt-cell-muted">
                      {best ? t(`budgets.period.${best.period.toLowerCase()}`) : '—'}
                    </td>
                    <td className="bgt-td-sev bgt-sev--notice">
                      {best?.notice != null ? (
                        EUR.format(best.notice)
                      ) : (
                        <span className="bgt-cell-muted">—</span>
                      )}
                    </td>
                    <td className="bgt-td-sev bgt-sev--warning">
                      {best?.warning != null ? (
                        EUR.format(best.warning)
                      ) : (
                        <span className="bgt-cell-muted">—</span>
                      )}
                    </td>
                    <td className="bgt-td-sev bgt-sev--critical">
                      {best?.critical != null ? (
                        EUR.format(best.critical)
                      ) : (
                        <span className="bgt-cell-muted">—</span>
                      )}
                    </td>

                    {statusLoaded && (
                      <td className="bgt-td-bar">
                        {progress != null ? (
                          <ProgressBar progress={progress} />
                        ) : (
                          <span className="bgt-cell-muted">—</span>
                        )}
                      </td>
                    )}
                    <td className="bgt-td-actions">
                      <button
                        className={`bgt-icon-btn${best == null ? ' bgt-icon-btn--add' : ''}`}
                        onClick={e => {
                          e.stopPropagation()
                          startEdit(row)
                        }}
                        title={best != null ? t('limits.editThreshold') : t('limits.addThreshold')}
                      >
                        {best != null ? '✎' : '+'}
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>
      {drilldown != null && (
        <DrilldownModal
          state={drilldown}
          from={from}
          to={to}
          onClose={() => setDrilldown(null)}
        />
      )}
    </div>
  )
}

function ProgressBar({ progress }: { progress: Progress }) {
  const fillPct = Math.min(progress.pct * 100, 100)
  const overBudget = progress.pct > 1

  return (
    <div className="bgt-bar">
      <div className="bgt-bar-track">
        <div
          className={`bgt-bar-fill bgt-bar-fill--${progress.status}`}
          style={{ width: `${fillPct}%` }}
        />
        {progress.tickNotice != null && (
          <div
            className="bgt-bar-tick"
            style={{ left: `${progress.tickNotice * 100}%` }}
          />
        )}
        {progress.tickWarning != null && (
          <div
            className="bgt-bar-tick bgt-bar-tick--warn"
            style={{ left: `${progress.tickWarning * 100}%` }}
          />
        )}
      </div>
      <span className={`bgt-bar-pct bgt-bar-pct--${progress.status}${overBudget ? ' bgt-bar-pct--over' : ''}`}>
        {overBudget && '▲ '}
        {Math.round(progress.pct * 100)}%
      </span>
    </div>
  )
}

function DrilldownModal({
  state,
  from,
  to,
  onClose,
}: {
  state: DrilldownState
  from: string
  to: string
  onClose: () => void
}) {
  const { t } = useTranslation()
  const title = state.subcategory != null
    ? `${state.category} / ${state.subcategory}`
    : state.category

  const total = state.transactions?.reduce((s, tx) => s + Math.abs(tx.effectiveAmount), 0) ?? 0

  return (
    <div className="bgt-dd-backdrop" onClick={onClose}>
      <div className="bgt-dd-panel" onClick={e => e.stopPropagation()}>
        <div className="bgt-dd-header">
          <div className="bgt-dd-title">
            <span className="bgt-dd-name">{title}</span>
            <span className="bgt-dd-range">{from} → {to}</span>
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
                    <th>{t('common.amount')}</th>
                  </tr>
                </thead>
                <tbody>
                  {state.transactions.map(tx => (
                    <tr key={tx.id}>
                      <td className="bgt-dd-cell-date">{tx.accountingDate}</td>
                      <td className="bgt-dd-cell-cat">{tx.category} / {tx.subcategory}</td>
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
              <span className="bgt-dd-total-label">{t('common.total')}</span>
              <span className="bgt-dd-total">{EUR2.format(total)}</span>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
