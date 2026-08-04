import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { CategoryNode } from '../api/rawImport'
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
import { CategoryPathInput } from './CategoryPathInput'

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
  thresholdId: number
  categoryPath: string[]
  transactions: TransactionItem[] | null
  loading: boolean
}

// ── component ─────────────────────────────────────────────────────────────

export default function ThresholdsPage({ from, to, iban, categories }: { from: string; to: string; iban?: string; categories: CategoryNode[] }) {
  const { t } = useTranslation()
  const [thresholds, setThresholds] = useState<Threshold[]>([])
  const [statusMap, setStatusMap] = useState<Map<number, ThresholdStatusItem>>(new Map())
  const [statusLoaded, setStatusLoaded] = useState(false)
  const [loading, setLoading] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [drilldown, setDrilldown] = useState<DrilldownState | null>(null)
  const [isAddingNew, setIsAddingNew] = useState(false)
  const [newCategoryId, setNewCategoryId] = useState<number | null>(null)
  const [newForm, setNewForm] = useState<FormState>(EMPTY_FORM)
  const [newSaving, setNewSaving] = useState(false)
  const [newFormError, setNewFormError] = useState<string | null>(null)

  useEffect(() => {
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
      setStatusMap(new Map(items.map(item => [item.thresholdId, item])))
      setStatusLoaded(true)
    } catch {
      // silent — user can retry
    } finally {
      setLoading(false)
    }
  }

  // ── edit handlers ─────────────────────────────────────────────────────

  function startEdit(threshold: Threshold) {
    setEditingId(threshold.id)
    setForm(thresholdToForm(threshold))
    setFormError(null)
  }

  function cancelEdit() {
    setEditingId(null)
    setForm(EMPTY_FORM)
    setFormError(null)
  }

  async function handleSave(threshold: Threshold) {
    const req: SaveThresholdRequest = {
      categoryId: threshold.categoryId,
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

  async function handleDelete(id: number) {
    try {
      await deleteThreshold(id)
      setThresholds(prev => prev.filter(t => t.id !== id))
      cancelEdit()
    } catch {
      setFormError(t('limits.deleteFailed'))
    }
  }

  // ── add new handlers ──────────────────────────────────────────────────

  function startAddNew() {
    setIsAddingNew(true)
    setNewCategoryId(null)
    setNewForm(EMPTY_FORM)
    setNewFormError(null)
    cancelEdit()
  }

  function cancelAddNew() {
    setIsAddingNew(false)
    setNewCategoryId(null)
    setNewForm(EMPTY_FORM)
    setNewFormError(null)
  }

  async function handleSaveNew() {
    if (newCategoryId == null) {
      setNewFormError(t('limits.categoryRequired'))
      return
    }
    const req: SaveThresholdRequest = {
      categoryId: newCategoryId,
      period: newForm.period,
      notice: parseAmt(newForm.notice),
      warning: parseAmt(newForm.warning),
      critical: parseAmt(newForm.critical),
    }
    if (req.notice == null && req.warning == null && req.critical == null) {
      setNewFormError(t('limits.minAmount'))
      return
    }
    setNewSaving(true)
    setNewFormError(null)
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
      cancelAddNew()
    } catch {
      setNewFormError(t('limits.saveFailed'))
    } finally {
      setNewSaving(false)
    }
  }

  async function openDrilldown(threshold: Threshold) {
    const path = threshold.categoryPath
    setDrilldown({ thresholdId: threshold.id, categoryPath: path, transactions: null, loading: true })
    try {
      const resp = await fetchTransactionList(from, to, path[0], path[1], iban)
      const txs = resp.transactions.filter(tx => tx.effectiveAmount < 0)
      txs.sort((a, b) => b.bookingDate.localeCompare(a.bookingDate))
      setDrilldown(prev => prev?.thresholdId === threshold.id ? { ...prev, transactions: txs, loading: false } : prev)
    } catch {
      setDrilldown(prev => prev?.thresholdId === threshold.id ? { ...prev, transactions: [], loading: false } : prev)
    }
  }

  const sortedThresholds = [...thresholds].sort((a, b) =>
    a.categoryPath.filter(Boolean).join(' > ').localeCompare(b.categoryPath.filter(Boolean).join(' > ')),
  )

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
        {sortedThresholds.length === 0 && !isAddingNew ? (
          <p className="hint">{t('limits.noCategories')}</p>
        ) : (
          <table className="bgt-table">
            <thead>
              <tr>
                <th className="bgt-th-path">{t('limits.columns.category')}</th>
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
              {sortedThresholds.map(threshold => {
                const isEditing = editingId === threshold.id
                const item = statusLoaded ? statusMap.get(threshold.id) : undefined
                const spending = item?.spending ?? 0
                const progress = item != null ? progressFromItem(item) : null

                const rowClass = [
                  'bgt-row',
                  isEditing ? 'bgt-row--editing' : '',
                ]
                  .filter(Boolean)
                  .join(' ')

                if (isEditing) {
                  return (
                    <tr key={threshold.id} className={rowClass}>
                      <td className="bgt-td-path bgt-cell-cat">
                        {threshold.categoryPath.filter(Boolean).join(' > ')}
                      </td>
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
                          onClick={() => handleSave(threshold)}
                          disabled={saving}
                        >
                          {saving ? '…' : t('common.save')}
                        </button>
                        <button className="bgt-btn bgt-btn--delete" onClick={() => handleDelete(threshold.id)}>
                          {t('common.delete')}
                        </button>
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

                const best = item ?? pickBest([threshold], from, to)

                return (
                  <tr key={threshold.id} className={rowClass} onClick={() => startEdit(threshold)}>
                    <td className="bgt-td-path bgt-cell-cat">
                      {threshold.categoryPath.filter(Boolean).join(' > ')}
                    </td>
                    {statusLoaded && (
                      <td className={`bgt-td-spent${spending === 0 ? ' bgt-cell-muted' : ''}`}>
                        {spending > 0 ? (
                          <button
                            className="bgt-spent-btn"
                            onClick={e => { e.stopPropagation(); openDrilldown(threshold) }}
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
                        className="bgt-icon-btn"
                        onClick={e => {
                          e.stopPropagation()
                          startEdit(threshold)
                        }}
                        title={t('limits.editThreshold')}
                      >
                        ✎
                      </button>
                    </td>
                  </tr>
                )
              })}

              {isAddingNew && (
                <tr className="bgt-row bgt-row--editing bgt-row--new">
                  <td className="bgt-td-path">
                    <CategoryPathInput
                      value={newCategoryId}
                      onChange={setNewCategoryId}
                      tree={categories}
                      placeholder={t('limits.selectCategory')}
                      className="bgt-category-input"
                    />
                  </td>
                  {statusLoaded && <td />}
                  <td className="bgt-td-period">
                    <select
                      className="bgt-select"
                      value={newForm.period}
                      onChange={e =>
                        setNewForm(p => ({ ...p, period: e.target.value as ThresholdPeriod }))
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
                      value={newForm.notice}
                      onChange={e => setNewForm(p => ({ ...p, notice: e.target.value }))}
                    />
                  </td>
                  <td className="bgt-td-sev">
                    <input
                      className="bgt-sev-input"
                      type="number"
                      min="0"
                      step="1"
                      placeholder="—"
                      value={newForm.warning}
                      onChange={e => setNewForm(p => ({ ...p, warning: e.target.value }))}
                    />
                  </td>
                  <td className="bgt-td-sev">
                    <input
                      className="bgt-sev-input"
                      type="number"
                      min="0"
                      step="1"
                      placeholder="—"
                      value={newForm.critical}
                      onChange={e => setNewForm(p => ({ ...p, critical: e.target.value }))}
                    />
                  </td>
                  {statusLoaded && <td />}
                  <td className="bgt-td-edit-actions">
                    <button
                      className="bgt-btn bgt-btn--save"
                      onClick={handleSaveNew}
                      disabled={newSaving}
                    >
                      {newSaving ? '…' : t('common.save')}
                    </button>
                    <button className="bgt-btn bgt-btn--cancel" onClick={cancelAddNew}>
                      ✕
                    </button>
                    {newFormError != null && (
                      <span className="bgt-form-error">{newFormError}</span>
                    )}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}

        {!isAddingNew && (
          <button className="bgt-add-btn" onClick={startAddNew}>
            + {t('limits.addThreshold')}
          </button>
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
  const title = state.categoryPath.filter(Boolean).join(' > ')

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
                      <td className="bgt-dd-cell-cat">{tx.category}{tx.subcategory ? ` / ${tx.subcategory}` : ''}</td>
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
