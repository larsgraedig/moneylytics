import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  assignTransaction,
  createBudget,
  deleteBudget,
  fetchBudgets,
  removeTransactionLink,
  updateBudget,
  type Budget,
  type BudgetTransactionLink,
} from '../api/budgets'
import BudgetDetail from './BudgetDetail'
import { fetchCategories, type CategoryGroup } from '../api/rawImport'
import {
  fetchAccounts,
  fetchAllTransactions,
  type Account,
  type TransactionItem,
} from '../api/transactions'

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

function parseAmt(s: string): number | null {
  const n = parseFloat(s.replace(',', '.'))
  return isNaN(n) ? null : n
}

import { getPresetRange, PRESETS, type Preset } from '../utils/datePresets'

function effectiveContrib(amount: number | null, transactionAmount: number): number {
  if (amount === null) return transactionAmount
  return transactionAmount < 0 ? -Math.abs(amount) : Math.abs(amount)
}

interface FormState {
  name: string
  targetAmount: string
  note: string
}

const EMPTY_FORM: FormState = { name: '', targetAmount: '', note: '' }

function budgetToForm(b: Budget): FormState {
  return {
    name: b.name,
    targetAmount: b.targetAmount?.toString() ?? '',
    note: b.note ?? '',
  }
}

export default function BudgetsPage({ from, to, iban }: { from: string; to: string; iban?: string }) {
  const { t } = useTranslation()
  const [budgets, setBudgets] = useState<Budget[]>([])
  const [loading, setLoading] = useState(true)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [creatingNew, setCreatingNew] = useState(false)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [drilldownId, setDrilldownId] = useState<number | null>(null)
  const [detailBudgetId, setDetailBudgetId] = useState<number | null>(null)
  const [assigningBudget, setAssigningBudget] = useState<Budget | null>(null)

  useEffect(() => {
    setLoading(true)
    fetchBudgets()
      .then(setBudgets)
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  const drilldownBudget = budgets.find(b => b.id === drilldownId) ?? null
  const detailBudget = budgets.find(b => b.id === detailBudgetId) ?? null

  function startCreate() {
    setCreatingNew(true)
    setEditingId(null)
    setForm(EMPTY_FORM)
    setFormError(null)
  }

  function startEdit(budget: Budget) {
    setEditingId(budget.id)
    setCreatingNew(false)
    setForm(budgetToForm(budget))
    setFormError(null)
  }

  function cancelForm() {
    setEditingId(null)
    setCreatingNew(false)
    setForm(EMPTY_FORM)
    setFormError(null)
  }

  async function handleSave() {
    const name = form.name.trim()
    if (!name) { setFormError(t('budgets.nameRequired')); return }
    const targetAmount = form.targetAmount !== '' ? parseAmt(form.targetAmount) : null
    const note = form.note.trim() || null
    setSaving(true)
    setFormError(null)
    try {
      if (creatingNew) {
        const created = await createBudget(name, targetAmount, note)
        setBudgets(prev => [...prev, created])
      } else if (editingId != null) {
        const updated = await updateBudget(editingId, name, targetAmount, note)
        setBudgets(prev => prev.map(b => b.id === updated.id ? { ...updated, transactionLinks: b.transactionLinks } : b))
      }
      cancelForm()
    } catch {
      setFormError(t('budgets.saveFailed'))
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(id: number) {
    if (!window.confirm(t('budgets.deleteConfirm'))) return
    try {
      await deleteBudget(id)
      setBudgets(prev => prev.filter(b => b.id !== id))
      if (editingId === id) cancelForm()
      if (drilldownId === id) setDrilldownId(null)
      if (detailBudgetId === id) setDetailBudgetId(null)
    } catch {
      setFormError(t('budgets.deleteFailed'))
    }
  }

  async function handleRemoveLink(budgetId: number, linkId: number) {
    try {
      await removeTransactionLink(linkId)
      setBudgets(prev =>
        prev.map(b => {
          if (b.id !== budgetId) return b
          const removed = b.transactionLinks.find(l => l.id === linkId)
          return {
            ...b,
            transactionLinks: b.transactionLinks.filter(l => l.id !== linkId),
            balance: b.balance - effectiveContrib(removed?.amount ?? null, removed?.transactionAmount ?? 0),
          }
        }),
      )
    } catch { /* silent */ }
  }

  function handleAssigned(budgetId: number, link: BudgetTransactionLink) {
    setBudgets(prev =>
      prev.map(b => {
        if (b.id !== budgetId) return b
        return {
          ...b,
          transactionLinks: [...b.transactionLinks, link],
          balance: b.balance + effectiveContrib(link.amount, link.transactionAmount),
        }
      }),
    )
  }

  if (loading) return <div className="bdg-page"><p className="hint">{t('common.fetching')}</p></div>

  if (detailBudget != null) {
    return (
      <BudgetDetail
        budget={detailBudget}
        onBack={() => setDetailBudgetId(null)}
        onRemoveLink={linkId => handleRemoveLink(detailBudget.id, linkId)}
        onAssign={() => setAssigningBudget(detailBudget)}
      />
    )
  }

  return (
    <div className="bdg-page">
      <div className="bdg-controls">
        {!creatingNew && editingId == null && (
          <button className="bdg-create-btn" onClick={startCreate}>
            + {t('budgets.createBudget')}
          </button>
        )}
        {(creatingNew || editingId != null) && (
          <div className="bdg-form">
            <input
              className="bdg-form-input"
              placeholder={t('budgets.namePlaceholder')}
              value={form.name}
              onChange={e => setForm(p => ({ ...p, name: e.target.value }))}
              autoFocus
            />
            <input
              className="bdg-form-input bdg-form-input--sm"
              type="number"
              min="0"
              step="1"
              placeholder={t('budgets.targetAmountPlaceholder')}
              value={form.targetAmount}
              onChange={e => setForm(p => ({ ...p, targetAmount: e.target.value }))}
            />
            <input
              className="bdg-form-input"
              placeholder={t('budgets.notePlaceholder')}
              value={form.note}
              onChange={e => setForm(p => ({ ...p, note: e.target.value }))}
            />
            <button className="bdg-btn bdg-btn--save" onClick={handleSave} disabled={saving}>
              {saving ? '…' : t('common.save')}
            </button>
            <button className="bdg-btn bdg-btn--cancel" onClick={cancelForm}>✕</button>
            {formError && <span className="bdg-form-error">{formError}</span>}
          </div>
        )}
      </div>

      <div className="bdg-body">
        {budgets.length === 0 && !creatingNew ? (
          <p className="hint">{t('budgets.noBudgets')}</p>
        ) : (
          <table className="bdg-table">
            <thead>
              <tr>
                <th>{t('budgets.columns.name')}</th>
                <th className="bdg-th-right">{t('budgets.columns.balance')}</th>
                <th className="bdg-th-right">{t('budgets.columns.target')}</th>
                <th>{t('budgets.columns.progress')}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {budgets.map(budget => {
                const isEditing = editingId === budget.id
                const hasTarget = budget.targetAmount != null && budget.targetAmount !== 0
                const isNegativeTarget = hasTarget && budget.targetAmount! < 0
                const pct = hasTarget ? Math.abs(budget.balance) / Math.abs(budget.targetAmount!) : null
                return (
                  <tr
                    key={budget.id}
                    className={`bdg-row${isEditing ? ' bdg-row--editing' : ''}`}
                    onClick={() => !isEditing && setDrilldownId(budget.id)}
                  >
                    <td
                      className="bdg-cell-name bdg-cell-name--link"
                      onClick={e => { e.stopPropagation(); setDetailBudgetId(budget.id) }}
                    >
                      {budget.name}
                      {budget.note && <span className="bdg-cell-note" title={budget.note}>  {budget.note}</span>}
                    </td>
                    <td className={`bdg-cell-amount ${budget.balance >= 0 ? 'positive' : 'negative'}`}>
                      {EUR.format(budget.balance)}
                    </td>
                    <td className="bdg-cell-target">
                      {budget.targetAmount != null
                        ? EUR.format(budget.targetAmount)
                        : <span className="bdg-muted">—</span>}
                    </td>
                    <td className="bdg-cell-progress">
                      {pct != null ? (
                        <div className="bdg-bar">
                          <div className="bdg-bar-track">
                            <div
                              className={`bdg-bar-fill${isNegativeTarget ? (pct >= 1 ? ' bdg-bar-fill--negative-done' : ' bdg-bar-fill--negative') : pct >= 1 ? ' bdg-bar-fill--done' : ''}`}
                              style={{ width: `${Math.min(pct * 100, 100)}%` }}
                            />
                          </div>
                          <span className={`bdg-bar-pct${isNegativeTarget ? (pct >= 1 ? ' bdg-bar-pct--negative-done' : '') : pct >= 1 ? ' bdg-bar-pct--done' : ''}`}>
                            {Math.round(pct * 100)}%
                          </span>
                        </div>
                      ) : <span className="bdg-muted">—</span>}
                    </td>
                    <td className="bdg-cell-actions" onClick={e => e.stopPropagation()}>
                      <button
                        className="bdg-icon-btn"
                        title={t('common.edit')}
                        onClick={() => isEditing ? cancelForm() : startEdit(budget)}
                      >
                        {isEditing ? '✕' : '✎'}
                      </button>
                      <button
                        className="bdg-icon-btn bdg-icon-btn--danger"
                        title={t('common.delete')}
                        onClick={() => handleDelete(budget.id)}
                      >
                        ␡
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>

      {drilldownBudget != null && (
        <BudgetPanel
          budget={drilldownBudget}
          onClose={() => setDrilldownId(null)}
          onRemoveLink={linkId => handleRemoveLink(drilldownBudget.id, linkId)}
          onAssign={() => setAssigningBudget(drilldownBudget)}
          t={t}
        />
      )}

      {assigningBudget != null && (
        <AssignTransactionModal
          budget={assigningBudget}
          defaultFrom={from}
          defaultTo={to}
          defaultIban={iban}
          onClose={() => setAssigningBudget(null)}
          onAssigned={link => handleAssigned(assigningBudget.id, link)}
        />
      )}
    </div>
  )
}

// ── Budget panel (slide-in) ───────────────────────────────────────────────

function BudgetPanel({
  budget, onClose, onRemoveLink, onAssign, t,
}: {
  budget: Budget
  onClose: () => void
  onRemoveLink: (linkId: number) => void
  onAssign: () => void
  t: (key: string, opts?: Record<string, unknown>) => string
}) {
  const links = [...budget.transactionLinks].sort((a, b) => b.transactionDate.localeCompare(a.transactionDate))
  const total = links.reduce((s, l) => s + effectiveContrib(l.amount, l.transactionAmount), 0)

  return (
    <>
      <div className="txn-backdrop" onClick={onClose} />
      <div className="txn-panel">
        <div className="txn-panel-header">
          <div className="txn-panel-title">
            <span className="txn-panel-name">{budget.name}</span>
          </div>
          <div className="bdg-panel-actions">
            <button className="bdg-assign-btn" onClick={onAssign}>
              + {t('budgets.assign')}
            </button>
            <button className="txn-panel-close" onClick={onClose} title={t('common.cancel')}>✕</button>
          </div>
        </div>
        <div className="txn-panel-body">
          {links.length === 0 && <p className="txn-hint">{t('budgets.noTransactions')}</p>}
          {links.length > 0 && (
            <>
              <table className="txn-list-table">
                <thead>
                  <tr>
                    <th>{t('transactions.panel.date')}</th>
                    <th>{t('transactions.panel.subcategory')}</th>
                    <th className="txn-col-amount">{t('transactions.panel.amount')}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {links.map(link => (
                    <tr key={link.id}>
                      <td className="txn-cell-date">{formatDate(link.transactionDate)}</td>
                      <td className="txn-cell-sub">{link.transactionCategory} / {link.transactionSubcategory}</td>
                      <td className={`txn-cell-amount${effectiveContrib(link.amount, link.transactionAmount) < 0 ? ' negative' : ''}`}>
                        {EUR.format(effectiveContrib(link.amount, link.transactionAmount))}
                      </td>
                      <td>
                        <button className="bdg-remove-btn" title={t('budgets.remove')} onClick={() => onRemoveLink(link.id)}>
                          ×
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <div className="txn-total-row">
                <span>{t('transactions.panel.total')}</span>
                <span className={`txn-total-value${total < 0 ? ' negative' : ''}`}>{EUR.format(total)}</span>
              </div>
            </>
          )}
        </div>
      </div>
    </>
  )
}

// ── Assign transaction modal ──────────────────────────────────────────────

function AssignTransactionModal({
  budget,
  defaultFrom,
  defaultTo,
  defaultIban,
  onClose,
  onAssigned,
}: {
  budget: Budget
  defaultFrom: string
  defaultTo: string
  defaultIban?: string
  onClose: () => void
  onAssigned: (link: BudgetTransactionLink) => void
}) {
  const { t } = useTranslation()
  const [accounts, setAccounts] = useState<Account[]>([])
  const [categories, setCategories] = useState<CategoryGroup[]>([])

  const [filterFrom, setFilterFrom] = useState(defaultFrom)
  const [filterTo, setFilterTo] = useState(defaultTo)
  const [filterIban, setFilterIban] = useState(defaultIban ?? '')
  const [filterCategory, setFilterCategory] = useState('')
  const [filterSubcategory, setFilterSubcategory] = useState('')
  const [activePreset, setActivePreset] = useState<Preset | ''>('')

  const [transactions, setTransactions] = useState<TransactionItem[] | null>(null)
  const [loadingTx, setLoadingTx] = useState(false)

  const [assigningId, setAssigningId] = useState<number | null>(null)
  const [partialAmount, setPartialAmount] = useState('')
  const [assigning, setAssigning] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchAccounts().then(setAccounts).catch(() => {})
    fetchCategories().then(r => setCategories(r.categories)).catch(() => {})
  }, [])

  const subcategoriesFor = (cat: string) => categories.find(c => c.name === cat)?.subcategories ?? []

  async function loadWith(from: string, to: string) {
    setLoadingTx(true)
    setTransactions(null)
    setAssigningId(null)
    setError(null)
    try {
      const resp = await fetchAllTransactions(
        from,
        to,
        filterIban || undefined,
        filterCategory || undefined,
        filterSubcategory || undefined,
        undefined,
        undefined,
        undefined,
        undefined,
        budget.id,
      )
      setTransactions(resp.transactions)
    } catch {
      setTransactions([])
    } finally {
      setLoadingTx(false)
    }
  }

  function load() { loadWith(filterFrom, filterTo) }

  function applyPreset(preset: Preset) {
    const { from, to } = getPresetRange(preset)
    setActivePreset(preset)
    setFilterFrom(from)
    setFilterTo(to)
    loadWith(from, to)
  }

  async function doAssign(txId: number) {
    const amount = partialAmount !== '' ? parseAmt(partialAmount) : null
    setAssigning(true)
    setError(null)
    try {
      const link = await assignTransaction(budget.id, txId, amount)
      onAssigned(link)
      setTransactions(prev => prev ? prev.filter(tx => tx.id !== txId) : null)
      setAssigningId(null)
      setPartialAmount('')
    } catch {
      setError(t('budgets.assignFailed'))
    } finally {
      setAssigning(false)
    }
  }

  return (
    <div className="bdg-assign-backdrop" onClick={onClose}>
      <div className="bdg-assign-modal bdg-assign-modal--lg" onClick={e => e.stopPropagation()}>

        <div className="bdg-modal-header">
          <span className="bdg-modal-title">{t('budgets.assignTitle', { name: budget.name })}</span>
          <button className="bdg-modal-close" onClick={onClose}>✕</button>
        </div>

        <div className="bdg-assign-filters">
          <select
            className="account-select"
            value={activePreset}
            onChange={e => e.target.value && applyPreset(e.target.value as Preset)}
          >
            <option value="">{t('budgets.presets.placeholder')}</option>
            {PRESETS.map(p => (
              <option key={p} value={p}>{t(`budgets.presets.${p}`)}</option>
            ))}
          </select>

          <div className="bdg-filter-sep" />

          <label className="range-field">
            <span className="range-label">{t('common.from')}</span>
            <input
              type="date"
              value={filterFrom}
              max={filterTo}
              onChange={e => { setFilterFrom(e.target.value); setActivePreset('') }}
            />
          </label>
          <div className="range-sep" />
          <label className="range-field">
            <span className="range-label">{t('common.to')}</span>
            <input
              type="date"
              value={filterTo}
              min={filterFrom}
              onChange={e => { setFilterTo(e.target.value); setActivePreset('') }}
            />
          </label>

          {accounts.length > 0 && (
            <select
              className="account-select"
              value={filterIban}
              onChange={e => setFilterIban(e.target.value)}
            >
              <option value="">{t('common.allAccounts')}</option>
              {accounts.map(a => <option key={a.iban} value={a.iban}>{a.name || a.iban}</option>)}
            </select>
          )}

          <select
            className="account-select"
            value={filterCategory}
            onChange={e => { setFilterCategory(e.target.value); setFilterSubcategory('') }}
          >
            <option value="">{t('transactions.allCategories')}</option>
            {categories.map(c => <option key={c.name} value={c.name}>{c.name}</option>)}
          </select>

          {filterCategory && subcategoriesFor(filterCategory).length > 0 && (
            <select
              className="account-select"
              value={filterSubcategory}
              onChange={e => setFilterSubcategory(e.target.value)}
            >
              <option value="">{t('transactions.allSubcategories')}</option>
              {subcategoriesFor(filterCategory).map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          )}

          <button className="load-btn" onClick={load} disabled={loadingTx}>
            {loadingTx ? '…' : t('common.load')}
          </button>
        </div>

        <div className="bdg-assign-results">
          {transactions === null && !loadingTx && (
            <p className="txn-hint">{t('common.selectDateAndLoad', { defaultValue: 'Filter setzen und Laden drücken.' })}</p>
          )}
          {loadingTx && <p className="txn-hint loading">{t('common.fetching')}</p>}
          {transactions != null && transactions.length === 0 && (
            <p className="txn-hint">{t('common.noTransactions')}</p>
          )}
          {error && <p className="txn-hint" style={{ color: 'var(--error)' }}>{error}</p>}

          {transactions != null && transactions.length > 0 && (
            <table className="txn-list-table">
              <thead>
                <tr>
                  <th>{t('transactions.columns.date')}</th>
                  <th>{t('transactions.columns.category')}</th>
                  <th>{t('transactions.columns.subcategory')}</th>
                  <th className="txn-col-amount">{t('transactions.columns.amount')}</th>
                  <th className="bdg-assign-col-action" />
                </tr>
              </thead>
              <tbody>
                {transactions.map(tx => {
                  const isActive = assigningId === tx.id
                  return (
                    <tr key={tx.id} className={isActive ? 'bdg-assign-row--active' : undefined}>
                      <td className="txn-cell-date">{formatDate(tx.accountingDate)}</td>
                      <td className="txn-cell-sub">{tx.category}</td>
                      <td className="txn-cell-sub">{tx.subcategory}</td>
                      <td className={`txn-cell-amount${tx.amount < 0 ? ' negative' : ''}`}>
                        {EUR.format(tx.amount)}
                      </td>
                      <td className="bdg-assign-col-action">
                        {isActive ? (
                          <div className="bdg-assign-inline">
                            <input
                              className="txnv-partial-input"
                              type="number"
                              step="0.01"
                              placeholder={t('budgets.partialAmount')}
                              value={partialAmount}
                              onChange={e => setPartialAmount(e.target.value)}
                              autoFocus
                            />
                            <button
                              className="txnv-link-confirm-btn"
                              onClick={() => doAssign(tx.id)}
                              disabled={assigning}
                            >
                              {assigning ? '…' : '✓'}
                            </button>
                            <button
                              className="txnv-link-back-btn"
                              onClick={() => { setAssigningId(null); setPartialAmount('') }}
                            >
                              ×
                            </button>
                          </div>
                        ) : (
                          <button
                            className="bdg-assign-row-btn"
                            onClick={() => { setAssigningId(tx.id); setPartialAmount('') }}
                          >
                            +
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  )
}
