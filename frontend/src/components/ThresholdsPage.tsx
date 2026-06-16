import { useEffect, useMemo, useState } from 'react'
import { fetchCategories, type CategoryGroup } from '../api/rawImport'
import {
  deleteThreshold,
  fetchThresholds,
  saveThreshold,
  type SaveThresholdRequest,
  type Threshold,
  type ThresholdPeriod,
} from '../api/thresholds'
import { fetchTransactionList } from '../api/transactions'

const PERIODS: ThresholdPeriod[] = ['WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY']
const PERIOD_LABELS: Record<ThresholdPeriod, string> = {
  WEEKLY: 'weekly',
  MONTHLY: 'monthly',
  QUARTERLY: 'quarterly',
  YEARLY: 'yearly',
}
const PERIOD_IDEAL_DAYS: Record<ThresholdPeriod, number> = {
  WEEKLY: 7,
  MONTHLY: 30,
  QUARTERLY: 91,
  YEARLY: 365,
}

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 })
const EUR2 = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

function isoDate(d: Date) {
  return d.toISOString().slice(0, 10)
}
const today = isoDate(new Date())
const firstOfMonth = isoDate(new Date(new Date().getFullYear(), new Date().getMonth(), 1))

// ── helpers ──────────────────────────────────────────────────────────────

function rowKey(category: string, subcategory: string | null): string {
  return `${category}\0${subcategory ?? ''}`
}

function periodsInRange(from: string, to: string, period: ThresholdPeriod): number {
  const f = new Date(from)
  const t = new Date(to)
  const months = (t.getFullYear() - f.getFullYear()) * 12 + (t.getMonth() - f.getMonth()) + 1
  const days = (t.getTime() - f.getTime()) / 86_400_000 + 1
  switch (period) {
    case 'WEEKLY': return days / 7
    case 'MONTHLY': return months
    case 'QUARTERLY': return months / 3
    case 'YEARLY': return months / 12
  }
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

function computeProgress(spending: number, t: Threshold, from: string, to: string): Progress | null {
  const p = periodsInRange(from, to, t.period)
  const sN = t.notice != null ? t.notice * p : null
  const sW = t.warning != null ? t.warning * p : null
  const sC = t.critical != null ? t.critical * p : null
  const max = sC ?? sW ?? sN
  if (!max || max <= 0) return null
  const pct = spending / max
  let status: Status = 'ok'
  if (sC != null && spending >= sC) status = 'critical'
  else if (sW != null && spending >= sW) status = 'warning'
  else if (sN != null && spending >= sN) status = 'notice'
  return {
    pct,
    status,
    tickNotice: sN != null ? Math.min(sN / max, 1) : null,
    tickWarning: sW != null ? Math.min(sW / max, 1) : null,
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

// ── row type ──────────────────────────────────────────────────────────────

interface BudgetRow {
  category: string
  subcategory: string | null
  key: string
  thresholds: Threshold[]
  isFirst: boolean
}

// ── component ─────────────────────────────────────────────────────────────

export default function ThresholdsPage() {
  const [categories, setCategories] = useState<CategoryGroup[]>([])
  const [thresholds, setThresholds] = useState<Threshold[]>([])
  const [spendingMap, setSpendingMap] = useState<Map<string, number>>(new Map())
  const [spendingLoaded, setSpendingLoaded] = useState(false)
  const [from, setFrom] = useState(firstOfMonth)
  const [to, setTo] = useState(today)
  const [loading, setLoading] = useState(false)
  const [editingKey, setEditingKey] = useState<string | null>(null)
  const [editThresholdId, setEditThresholdId] = useState<number | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  useEffect(() => {
    fetchCategories().then(r => setCategories(r.categories)).catch(() => {})
    fetchThresholds().then(setThresholds).catch(() => {})
  }, [])

  // Reset spending when date range changes
  useEffect(() => {
    setSpendingLoaded(false)
    setSpendingMap(new Map())
  }, [from, to])

  async function loadSpending() {
    setLoading(true)
    try {
      const resp = await fetchTransactionList(from, to)
      const map = new Map<string, number>()
      for (const tx of resp.transactions) {
        if (tx.effectiveAmount >= 0) continue
        const amt = Math.abs(tx.effectiveAmount)
        const ck = rowKey(tx.category, null)
        const sk = rowKey(tx.category, tx.subcategory)
        map.set(ck, (map.get(ck) ?? 0) + amt)
        map.set(sk, (map.get(sk) ?? 0) + amt)
      }
      setSpendingMap(map)
      setSpendingLoaded(true)
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
      setFormError('Mindestens ein Betrag erforderlich.')
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
      setFormError('Speichern fehlgeschlagen.')
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
      setFormError('Löschen fehlgeschlagen.')
    }
  }

  // ── render ────────────────────────────────────────────────────────────

  return (
    <div className="bgt-page">
      <div className="bgt-controls">
        <fieldset className="range-group">
          <label className="range-field">
            <span className="range-label">from</span>
            <input
              type="date"
              value={from}
              max={to}
              onChange={e => setFrom(e.target.value)}
            />
          </label>
          <div className="range-sep" />
          <label className="range-field">
            <span className="range-label">to</span>
            <input
              type="date"
              value={to}
              min={from}
              max={today}
              onChange={e => setTo(e.target.value)}
            />
          </label>
        </fieldset>
        <button className="load-btn" onClick={loadSpending} disabled={loading}>
          {loading ? '…' : 'load'}
        </button>
        {spendingLoaded && (
          <span className="bgt-period-badge">
            Ausgaben geladen · {from} → {to}
          </span>
        )}
      </div>

      <div className="bgt-body">
        {rows.length === 0 ? (
          <p className="hint">Keine Kategorien gefunden — zuerst Transaktionen importieren</p>
        ) : (
          <table className="bgt-table">
            <thead>
              <tr>
                <th className="bgt-th-cat">kategorie</th>
                <th className="bgt-th-sub">unterkategorie</th>
                {spendingLoaded && <th className="bgt-th-spent">ausgaben</th>}
                <th className="bgt-th-period">zeitraum</th>
                <th className="bgt-th-sev bgt-sev--notice">notice</th>
                <th className="bgt-th-sev bgt-sev--warning">warning</th>
                <th className="bgt-th-sev bgt-sev--critical">critical</th>
                {spendingLoaded && <th className="bgt-th-bar">fortschritt</th>}
                <th className="bgt-th-actions" />
              </tr>
            </thead>
            <tbody>
              {rows.map(row => {
                const isEditing = editingKey === row.key
                const best = pickBest(row.thresholds, from, to)
                const spending = spendingMap.get(row.key) ?? 0
                const progress =
                  best != null && spendingLoaded
                    ? computeProgress(spending, best, from, to)
                    : null

                const rowClass = [
                  'bgt-row',
                  row.isFirst ? 'bgt-row--group-start' : '',
                  isEditing ? 'bgt-row--editing' : '',
                  !isEditing && best == null ? 'bgt-row--no-budget' : '',
                ]
                  .filter(Boolean)
                  .join(' ')

                if (isEditing) {
                  return (
                    <tr key={row.key} className={rowClass}>
                      <td className="bgt-td-cat bgt-cell-cat">{row.category}</td>
                      <td className="bgt-td-sub bgt-cell-muted">{row.subcategory ?? 'gesamt'}</td>
                      {spendingLoaded && (
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
                              {PERIOD_LABELS[p]}
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
                      {spendingLoaded && <td />}
                      <td className="bgt-td-edit-actions">
                        <button
                          className="bgt-btn bgt-btn--save"
                          onClick={() => handleSave(row)}
                          disabled={saving}
                        >
                          {saving ? '…' : 'save'}
                        </button>
                        {editThresholdId != null && (
                          <button className="bgt-btn bgt-btn--delete" onClick={handleDelete}>
                            del
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
                      {row.subcategory ?? 'gesamt'}
                    </td>
                    {spendingLoaded && (
                      <td className={`bgt-td-spent${spending === 0 ? ' bgt-cell-muted' : ''}`}>
                        {spending > 0 ? EUR2.format(spending) : '—'}
                      </td>
                    )}
                    <td className="bgt-td-period bgt-cell-muted">
                      {best ? PERIOD_LABELS[best.period] : '—'}
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
                    {spendingLoaded && (
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
                        title={best != null ? 'threshold bearbeiten' : 'threshold hinzufügen'}
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
