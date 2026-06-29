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
} from '../api/budgets'
import { fetchAllTransactions, type TransactionItem } from '../api/transactions'

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

function parseAmt(s: string): number | null {
  const n = parseFloat(s.replace(',', '.'))
  return isNaN(n) || n < 0 ? null : n
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

interface AssignState {
  budgetId: number
  transactions: TransactionItem[] | null
  loading: boolean
  selectedId: number | null
  amount: string
  assigning: boolean
  error: string | null
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
  const [assignState, setAssignState] = useState<AssignState | null>(null)

  useEffect(() => {
    setLoading(true)
    fetchBudgets()
      .then(setBudgets)
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  const drilldownBudget = budgets.find(b => b.id === drilldownId) ?? null

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
    if (!name) {
      setFormError(t('budgets.nameRequired'))
      return
    }
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
        setBudgets(prev => prev.map(b => (b.id === updated.id ? { ...updated, transactionLinks: b.transactionLinks } : b)))
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
          const newLinks = b.transactionLinks.filter(l => l.id !== linkId)
          const removedAmt = removed?.amount ?? removed?.transactionAmount ?? 0
          return { ...b, transactionLinks: newLinks, balance: b.balance - removedAmt }
        }),
      )
    } catch {
      // silent
    }
  }

  async function openAssign(budget: Budget) {
    setAssignState({
      budgetId: budget.id,
      transactions: null,
      loading: true,
      selectedId: null,
      amount: '',
      assigning: false,
      error: null,
    })
    try {
      const resp = await fetchAllTransactions(from, to, iban)
      const assignedIds = new Set(budget.transactionLinks.map(l => l.transactionId))
      const available = resp.transactions.filter(tx => !assignedIds.has(tx.id))
      setAssignState(prev => prev ? { ...prev, transactions: available, loading: false } : null)
    } catch {
      setAssignState(prev => prev ? { ...prev, transactions: [], loading: false } : null)
    }
  }

  async function confirmAssign() {
    if (!assignState || assignState.selectedId == null) return
    const amount = assignState.amount !== '' ? parseAmt(assignState.amount) : null
    setAssignState(prev => prev ? { ...prev, assigning: true, error: null } : null)
    try {
      const link = await assignTransaction(assignState.budgetId, assignState.selectedId, amount)
      const tx = assignState.transactions?.find(t => t.id === assignState.selectedId)
      setBudgets(prev =>
        prev.map(b => {
          if (b.id !== assignState.budgetId) return b
          return {
            ...b,
            transactionLinks: [...b.transactionLinks, link],
            balance: b.balance + (amount ?? (tx?.amount ?? 0)),
          }
        }),
      )
      setAssignState(null)
    } catch {
      setAssignState(prev => prev ? { ...prev, assigning: false, error: t('budgets.assignFailed') } : null)
    }
  }

  if (loading) {
    return <div className="bdg-page"><p className="hint">{t('common.fetching')}</p></div>
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
                const hasTarget = budget.targetAmount != null && budget.targetAmount > 0
                const pct = hasTarget ? Math.abs(budget.balance) / budget.targetAmount! : null

                return (
                  <tr
                    key={budget.id}
                    className={`bdg-row${isEditing ? ' bdg-row--editing' : ''}`}
                    onClick={() => !isEditing && setDrilldownId(budget.id)}
                  >
                    <td className="bdg-cell-name">
                      {budget.name}
                      {budget.note && <span className="bdg-cell-note" title={budget.note}>  {budget.note}</span>}
                    </td>
                    <td className={`bdg-cell-amount ${budget.balance >= 0 ? 'positive' : 'negative'}`}>
                      {EUR.format(budget.balance)}
                    </td>
                    <td className="bdg-cell-target">
                      {budget.targetAmount != null ? EUR.format(budget.targetAmount) : <span className="bdg-muted">—</span>}
                    </td>
                    <td className="bdg-cell-progress">
                      {pct != null ? (
                        <div className="bdg-bar">
                          <div className="bdg-bar-track">
                            <div
                              className={`bdg-bar-fill${pct >= 1 ? ' bdg-bar-fill--done' : ''}`}
                              style={{ width: `${Math.min(pct * 100, 100)}%` }}
                            />
                          </div>
                          <span className={`bdg-bar-pct${pct >= 1 ? ' bdg-bar-pct--done' : ''}`}>
                            {Math.round(pct * 100)}%
                          </span>
                        </div>
                      ) : (
                        <span className="bdg-muted">—</span>
                      )}
                    </td>
                    <td className="bdg-cell-actions" onClick={e => e.stopPropagation()}>
                      <button
                        className="bdg-icon-btn"
                        title={t('common.edit')}
                        onClick={() => (isEditing ? cancelForm() : startEdit(budget))}
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
          onAssign={() => openAssign(drilldownBudget)}
          t={t}
        />
      )}

      {assignState != null && (
        <div className="bdg-assign-backdrop" onClick={() => setAssignState(null)}>
          <div className="bdg-assign-modal" onClick={e => e.stopPropagation()}>
            <div className="bdg-modal-header">
              <span className="bdg-modal-title">
                {t('budgets.assignTitle', { name: drilldownBudget?.name ?? '' })}
              </span>
              <button className="bdg-modal-close" onClick={() => setAssignState(null)}>✕</button>
            </div>
            {assignState.loading && <p className="txn-hint loading">{t('common.fetching')}</p>}
            {!assignState.loading && assignState.transactions != null && assignState.transactions.length === 0 && (
              <p className="txn-hint">{t('common.noTransactions')}</p>
            )}
            {!assignState.loading && assignState.transactions != null && assignState.transactions.length > 0 && (
              <div className="bdg-modal-body">
                <select
                  className="bdg-modal-select"
                  value={assignState.selectedId ?? ''}
                  onChange={e => setAssignState(prev => prev ? { ...prev, selectedId: Number(e.target.value) || null } : null)}
                >
                  <option value="">{t('budgets.selectTransaction')}</option>
                  {assignState.transactions.map(tx => (
                    <option key={tx.id} value={tx.id}>
                      {tx.accountingDate}  {EUR.format(tx.amount)}  {tx.category} / {tx.subcategory}
                    </option>
                  ))}
                </select>
                <input
                  className="bdg-modal-amt"
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder={t('budgets.partialAmount')}
                  value={assignState.amount}
                  onChange={e => setAssignState(prev => prev ? { ...prev, amount: e.target.value } : null)}
                />
                <button
                  className="bdg-btn bdg-btn--save"
                  disabled={assignState.selectedId == null || assignState.assigning}
                  onClick={confirmAssign}
                >
                  {assignState.assigning ? '…' : t('budgets.confirm')}
                </button>
                {assignState.error && <span className="bdg-form-error">{assignState.error}</span>}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

function BudgetPanel({
  budget,
  onClose,
  onRemoveLink,
  onAssign,
  t,
}: {
  budget: Budget
  onClose: () => void
  onRemoveLink: (linkId: number) => void
  onAssign: () => void
  t: (key: string, opts?: Record<string, unknown>) => string
}) {
  const links = [...budget.transactionLinks].sort((a, b) =>
    b.transactionDate.localeCompare(a.transactionDate),
  )
  const total = links.reduce((s, l) => s + (l.amount ?? l.transactionAmount), 0)

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
          {links.length === 0 && (
            <p className="txn-hint">{t('budgets.noTransactions')}</p>
          )}

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
                      <td className="txn-cell-sub">
                        {link.transactionCategory} / {link.transactionSubcategory}
                      </td>
                      <td className={`txn-cell-amount${(link.amount ?? link.transactionAmount) < 0 ? ' negative' : ''}`}>
                        {EUR.format(link.amount ?? link.transactionAmount)}
                      </td>
                      <td>
                        <button
                          className="bdg-remove-btn"
                          title={t('budgets.remove')}
                          onClick={() => onRemoveLink(link.id)}
                        >
                          ×
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <div className="txn-total-row">
                <span>{t('transactions.panel.total')}</span>
                <span className={`txn-total-value${total < 0 ? ' negative' : ''}`}>
                  {EUR.format(total)}
                </span>
              </div>
            </>
          )}
        </div>
      </div>
    </>
  )
}
