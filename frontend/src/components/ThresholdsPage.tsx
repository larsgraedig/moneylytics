import { useEffect, useState } from 'react'
import { fetchCamtCategories, type CategoryGroup } from '../api/camtImport'
import {
  fetchThresholds,
  saveThreshold,
  deleteThreshold,
  type Threshold,
  type ThresholdPeriod,
  type SaveThresholdRequest,
} from '../api/thresholds'

const PERIODS: ThresholdPeriod[] = ['WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY']
const PERIOD_LABELS: Record<ThresholdPeriod, string> = {
  WEEKLY: 'weekly',
  MONTHLY: 'monthly',
  QUARTERLY: 'quarterly',
  YEARLY: 'yearly',
}

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 })

function formatAmount(v: number | null) {
  return v == null ? '—' : EUR.format(v)
}

interface FormState {
  category: string
  subcategory: string
  period: ThresholdPeriod
  notice: string
  warning: string
  critical: string
}

const EMPTY_FORM: FormState = {
  category: '',
  subcategory: '',
  period: 'MONTHLY',
  notice: '',
  warning: '',
  critical: '',
}

function thresholdToForm(t: Threshold): FormState {
  return {
    category: t.category,
    subcategory: t.subcategory ?? '',
    period: t.period,
    notice: t.notice?.toString() ?? '',
    warning: t.warning?.toString() ?? '',
    critical: t.critical?.toString() ?? '',
  }
}

function parseAmount(s: string): number | null {
  const n = parseFloat(s.replace(',', '.'))
  return isNaN(n) || n < 0 ? null : n
}

export default function ThresholdsPage() {
  const [thresholds, setThresholds] = useState<Threshold[]>([])
  const [categories, setCategories] = useState<CategoryGroup[]>([])
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchThresholds().then(setThresholds).catch(() => {})
    fetchCamtCategories().then(r => setCategories(r.categories)).catch(() => {})
  }, [])

  const subcatOptions = categories.find(c => c.name === form.category)?.subcategories ?? []

  const setField = (field: keyof FormState, value: string) => {
    setForm(prev => ({
      ...prev,
      [field]: value,
      ...(field === 'category' ? { subcategory: '' } : {}),
    }))
  }

  const startEdit = (t: Threshold) => {
    setEditingId(t.id)
    setForm(thresholdToForm(t))
    setError(null)
  }

  const cancelEdit = () => {
    setEditingId(null)
    setForm(EMPTY_FORM)
    setError(null)
  }

  const handleSave = async () => {
    if (!form.category.trim()) { setError('Category is required.'); return }
    const request: SaveThresholdRequest = {
      category: form.category.trim(),
      subcategory: form.subcategory.trim() || null,
      period: form.period,
      notice: parseAmount(form.notice),
      warning: parseAmount(form.warning),
      critical: parseAmount(form.critical),
    }
    if (request.notice == null && request.warning == null && request.critical == null) {
      setError('At least one threshold amount is required.')
      return
    }
    setSaving(true)
    setError(null)
    try {
      const saved = await saveThreshold(request)
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
      setError('Failed to save threshold.')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (id: number) => {
    try {
      await deleteThreshold(id)
      setThresholds(prev => prev.filter(t => t.id !== id))
      if (editingId === id) cancelEdit()
    } catch {
      setError('Failed to delete threshold.')
    }
  }

  return (
    <div className="th-page">
      <div className="th-form-panel">
        <h2 className="th-section-title">{editingId != null ? 'edit threshold' : 'add threshold'}</h2>

        <div className="th-form">
          <div className="th-form-row">
            <label className="th-label">category</label>
            <input
              className="th-input"
              list="th-cat-list"
              placeholder="category"
              value={form.category}
              onChange={e => setField('category', e.target.value)}
            />
            <datalist id="th-cat-list">
              {categories.map(c => <option key={c.name} value={c.name} />)}
            </datalist>

            <label className="th-label">subcategory</label>
            <input
              className="th-input"
              list="th-sub-list"
              placeholder="all subcategories"
              value={form.subcategory}
              onChange={e => setField('subcategory', e.target.value)}
            />
            <datalist id="th-sub-list">
              {subcatOptions.map(s => <option key={s} value={s} />)}
            </datalist>

            <label className="th-label">period</label>
            <select
              className="th-select"
              value={form.period}
              onChange={e => setField('period', e.target.value)}
            >
              {PERIODS.map(p => <option key={p} value={p}>{PERIOD_LABELS[p]}</option>)}
            </select>
          </div>

          <div className="th-form-row">
            <label className="th-label th-label--notice">notice</label>
            <input
              className="th-input th-input--amount"
              type="number"
              min="0"
              step="1"
              placeholder="—"
              value={form.notice}
              onChange={e => setField('notice', e.target.value)}
            />
            <label className="th-label th-label--warning">warning</label>
            <input
              className="th-input th-input--amount"
              type="number"
              min="0"
              step="1"
              placeholder="—"
              value={form.warning}
              onChange={e => setField('warning', e.target.value)}
            />
            <label className="th-label th-label--critical">critical</label>
            <input
              className="th-input th-input--amount"
              type="number"
              min="0"
              step="1"
              placeholder="—"
              value={form.critical}
              onChange={e => setField('critical', e.target.value)}
            />
          </div>

          {error && <p className="th-error">{error}</p>}

          <div className="th-form-actions">
            <button className="load-btn" onClick={handleSave} disabled={saving}>
              {saving ? '…' : 'save'}
            </button>
            {editingId != null && (
              <button className="load-btn th-cancel-btn" onClick={cancelEdit}>cancel</button>
            )}
          </div>
        </div>
      </div>

      <div className="th-list-panel">
        <h2 className="th-section-title">defined thresholds</h2>
        {thresholds.length === 0 ? (
          <p className="hint">no thresholds defined yet</p>
        ) : (
          <table className="th-table">
            <thead>
              <tr>
                <th>category</th>
                <th>subcategory</th>
                <th>period</th>
                <th className="th-col-sev th-col-sev--notice">notice</th>
                <th className="th-col-sev th-col-sev--warning">warning</th>
                <th className="th-col-sev th-col-sev--critical">critical</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {thresholds.map(t => (
                <tr
                  key={t.id}
                  className={`th-row${editingId === t.id ? ' th-row--active' : ''}`}
                  onClick={() => startEdit(t)}
                >
                  <td>{t.category}</td>
                  <td className="th-cell-muted">{t.subcategory ?? 'all'}</td>
                  <td className="th-cell-muted">{PERIOD_LABELS[t.period]}</td>
                  <td className="th-col-sev th-col-sev--notice">{formatAmount(t.notice)}</td>
                  <td className="th-col-sev th-col-sev--warning">{formatAmount(t.warning)}</td>
                  <td className="th-col-sev th-col-sev--critical">{formatAmount(t.critical)}</td>
                  <td>
                    <button
                      className="th-delete-btn"
                      onClick={e => { e.stopPropagation(); handleDelete(t.id) }}
                      title="Delete"
                    >
                      ✕
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
