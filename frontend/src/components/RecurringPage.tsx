import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { RefreshCw, X, RotateCcw, Plus, Trash2 } from 'lucide-react'
import {
  fetchRecurringSeries,
  refreshRecurringSeries,
  confirmRecurringSeries,
  createRecurringSeries,
  deleteRecurringSeries,
  correctRecurringSeriesType,
  type RecurringSeriesItem,
  type RecurrenceDirection,
  type RecurrenceCadence,
  type RecurringType,
  type CreateRecurringSeriesBody,
} from '../api/recurring'
import { fetchAccounts, type Account } from '../api/accounts'

type PageState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'ready'; series: RecurringSeriesItem[] }
  | { phase: 'pending'; series: RecurringSeriesItem[]; dismissed: Set<string> }

interface AddForm {
  label: string
  type: RecurringType
  direction: RecurrenceDirection
  cadence: RecurrenceCadence
  expectedAmount: string
  currency: string
  accountIban: string
  lastBookingDate: string
}

const DEFAULT_FORM: AddForm = {
  label: '',
  type: 'OTHER',
  direction: 'EXPENSE',
  cadence: 'MONTHLY',
  expectedAmount: '',
  currency: 'EUR',
  accountIban: '',
  lastBookingDate: '',
}

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

const ALL_TYPES: RecurringType[] = [
  'SALARY', 'RENT', 'INSURANCE', 'SUBSCRIPTION', 'UTILITY', 'LOAN', 'MEMBERSHIP', 'OTHER',
]

const ALL_CADENCES: RecurrenceCadence[] = ['WEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'YEARLY']

function formatAmount(amount: number, currency: string): string {
  return new Intl.NumberFormat('de-DE', { style: 'currency', currency }).format(Math.abs(amount))
}

export default function RecurringPage() {
  const { t } = useTranslation()
  const [state, setState] = useState<PageState>({ phase: 'idle' })
  const [filterDirection, setFilterDirection] = useState<RecurrenceDirection | undefined>()
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const [refreshing, setRefreshing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editingType, setEditingType] = useState<number | null>(null)
  const [showAddModal, setShowAddModal] = useState(false)
  const [accounts, setAccounts] = useState<Account[]>([])
  const [addForm, setAddForm] = useState<AddForm>(DEFAULT_FORM)
  const [addSubmitting, setAddSubmitting] = useState(false)
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null)

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
      const initialDismissed = new Set(series.filter(s => s.isFalsePositive).map(s => s.fingerprint))
      setState({ phase: 'pending', series, dismissed: initialDismissed })
    } catch (e) {
      setState({ phase: 'error', message: e instanceof Error ? e.message : t('common.requestFailed') })
    } finally {
      setRefreshing(false)
    }
  }

  async function save() {
    if (state.phase !== 'pending') return
    setSaving(true)
    try {
      const confirmed = state.series
        .filter(s => !state.dismissed.has(s.fingerprint))
        .map(s => s.fingerprint)
      const falsePositives = [...state.dismissed]
      const saved = await confirmRecurringSeries(confirmed, falsePositives)
      // Reload to include any manual series that aren't in the confirm response
      const all = await fetchRecurringSeries()
      setState({ phase: 'ready', series: all })
      void saved
    } catch (e) {
      setState({ phase: 'error', message: e instanceof Error ? e.message : t('common.requestFailed') })
    } finally {
      setSaving(false)
    }
  }

  function cancelPending() {
    load()
  }

  function toggleDismiss(fingerprint: string) {
    if (state.phase !== 'pending') return
    setState(prev => {
      if (prev.phase !== 'pending') return prev
      const next = new Set(prev.dismissed)
      if (next.has(fingerprint)) next.delete(fingerprint)
      else next.add(fingerprint)
      return { ...prev, dismissed: next }
    })
  }

  function toggleExpand(key: string) {
    setExpanded(prev => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  async function handleTypeChange(id: number, newType: RecurringType) {
    setEditingType(null)
    if (state.phase !== 'ready') return
    const prev = state.series
    setState({ phase: 'ready', series: prev.map(s => s.id === id ? { ...s, type: newType } : s) })
    try {
      await correctRecurringSeriesType(id, newType)
    } catch {
      setState({ phase: 'ready', series: prev })
    }
  }

  async function openAddModal() {
    setAddForm(DEFAULT_FORM)
    setShowAddModal(true)
    try {
      const accs = await fetchAccounts()
      setAccounts(accs)
    } catch {
      setAccounts([])
    }
  }

  async function handleAddSubmit(e: React.FormEvent) {
    e.preventDefault()
    setAddSubmitting(true)
    try {
      const body: CreateRecurringSeriesBody = {
        label: addForm.label.trim(),
        type: addForm.type,
        direction: addForm.direction,
        cadence: addForm.cadence,
        expectedAmount: parseFloat(addForm.expectedAmount.replace(',', '.')),
        currency: addForm.currency || 'EUR',
        accountIban: addForm.accountIban,
        lastBookingDate: addForm.lastBookingDate || null,
      }
      const created = await createRecurringSeries(body)
      setShowAddModal(false)
      if (state.phase === 'ready') {
        setState({ phase: 'ready', series: [...state.series, created] })
      }
    } catch (e) {
      // keep modal open so user can retry
    } finally {
      setAddSubmitting(false)
    }
  }

  async function handleDelete(id: number) {
    try {
      await deleteRecurringSeries(id)
      setConfirmDeleteId(null)
      if (state.phase === 'ready') {
        setState({ phase: 'ready', series: state.series.filter(s => s.id !== id) })
      }
    } catch {
      setConfirmDeleteId(null)
    }
  }

  const isPending = state.phase === 'pending'
  const allSeries = (state.phase === 'ready' || state.phase === 'pending') ? state.series : []
  const displaySeries = filterDirection ? allSeries.filter(s => s.direction === filterDirection) : allSeries

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
        <div className="rcr-toolbar-actions">
          {isPending ? (
            <>
              <button className="rcr-cancel-btn" onClick={cancelPending} disabled={saving}>
                {t('recurring.cancelPending')}
              </button>
              <button className="rcr-save-btn" onClick={save} disabled={saving}>
                {saving ? t('recurring.saving') : t('recurring.save')}
              </button>
            </>
          ) : (
            <>
              <button className="rcr-add-btn" onClick={openAddModal}>
                <Plus size={14} />
                {t('recurring.addManual')}
              </button>
              <button className="rcr-refresh-btn" onClick={refresh} disabled={refreshing}>
                <RefreshCw size={14} className={refreshing ? 'rcr-spin' : ''} />
                {refreshing ? t('recurring.refreshing') : t('recurring.refresh')}
              </button>
            </>
          )}
        </div>
      </div>

      {isPending && (
        <div className="rcr-pending-banner">
          {t('recurring.pendingBanner', { count: allSeries.length })}
        </div>
      )}

      {state.phase === 'loading' && <p className="hint loading">{t('common.fetching')}</p>}
      {state.phase === 'error' && <p className="hint error">{state.message}</p>}
      {(state.phase === 'ready' || state.phase === 'pending') && displaySeries.length === 0 && (
        <p className="hint">{t('recurring.empty')}</p>
      )}

      {(state.phase === 'ready' || state.phase === 'pending') && displaySeries.length > 0 && (
        <table className="rcr-table">
          <thead>
            <tr>
              <th></th>
              <th>{t('common.amount')}</th>
              <th>{t('recurring.nextExpected')}</th>
              <th>{t('recurring.lastSeen')}</th>
              <th>{t('recurring.occurrences')}</th>
              <th>{t('recurring.account')}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {displaySeries.map(s => {
              const rowKey = s.fingerprint || String(s.id)
              const isDismissed = isPending && state.phase === 'pending' && state.dismissed.has(s.fingerprint)
              const isManual = s.status === 'MANUAL'
              const isExpanded = expanded.has(rowKey)
              const isEditingThisType = !isPending && s.id !== null && editingType === s.id
              const isConfirmingDelete = !isPending && s.id !== null && confirmDeleteId === s.id
              return (
                <>
                  <tr
                    key={rowKey}
                    className={`rcr-row${isDismissed ? ' rcr-row--dismissed' : ''}`}
                    onClick={() => { if (!isEditingThisType && !isConfirmingDelete) toggleExpand(rowKey) }}
                  >
                    <td className="rcr-cell-main">
                      <div className={`rcr-label${isDismissed ? ' rcr-label--dismissed' : ''}`}>{s.label}</div>
                      <div className="rcr-badges">
                        {isEditingThisType ? (
                          <select
                            className="rcr-type-select"
                            value={s.type}
                            autoFocus
                            onClick={e => e.stopPropagation()}
                            onBlur={() => setEditingType(null)}
                            onChange={e => s.id !== null && handleTypeChange(s.id, e.target.value as RecurringType)}
                          >
                            {ALL_TYPES.map(type => (
                              <option key={type} value={type}>{t(`recurring.type.${type}`)}</option>
                            ))}
                          </select>
                        ) : (
                          <span
                            className={`rcr-badge${!isPending && !isManual ? ' rcr-badge--clickable' : ''}`}
                            style={{ color: TYPE_COLORS[s.type] ?? '#6b7280', borderColor: TYPE_COLORS[s.type] ?? '#6b7280' }}
                            title={!isPending && !isManual ? t('recurring.correctType') : undefined}
                            onClick={e => { if (!isPending && !isManual && s.id !== null) { e.stopPropagation(); setEditingType(s.id) } }}
                          >
                            {t(`recurring.type.${s.type}`)}
                          </span>
                        )}
                        <span className="rcr-badge rcr-badge--cadence">
                          {t(`recurring.cadence.${s.cadence}`)}
                        </span>
                        {s.amountVariable && (
                          <span className="rcr-badge rcr-badge--variable">~</span>
                        )}
                        {isManual ? (
                          <span className="rcr-badge rcr-badge--manual">
                            {t('recurring.status.MANUAL')}
                          </span>
                        ) : !isPending ? (
                          <span
                            className="rcr-badge"
                            style={{ color: DEVIATION_COLORS[s.deviation] ?? '#6b7280', borderColor: DEVIATION_COLORS[s.deviation] ?? '#6b7280' }}
                          >
                            {t(`recurring.deviation.${s.deviation}`)}
                          </span>
                        ) : null}
                        {isDismissed && (
                          <span className="rcr-badge rcr-badge--false-positive">
                            {t('recurring.falsePositive')}
                          </span>
                        )}
                      </div>
                    </td>
                    <td className={`rcr-amount ${s.direction === 'EXPENSE' ? 'rcr-amount--expense' : 'rcr-amount--income'}`}>
                      {s.direction === 'EXPENSE' ? '−' : '+'}{formatAmount(s.expectedAmount, s.currency)}
                    </td>
                    <td className="rcr-date">{s.nextExpectedDate}</td>
                    <td className="rcr-date">{s.lastSeen}</td>
                    <td className="rcr-count">{s.occurrenceCount}×</td>
                    <td className="rcr-iban">{s.accountIban}</td>
                    <td className="rcr-cell-actions" onClick={e => e.stopPropagation()}>
                      {isPending ? (
                        <button
                          className={`rcr-dismiss-btn${isDismissed ? ' rcr-dismiss-btn--restore' : ''}`}
                          title={isDismissed ? t('recurring.restore') : t('recurring.dismiss')}
                          onClick={() => toggleDismiss(s.fingerprint)}
                        >
                          {isDismissed ? <RotateCcw size={14} /> : <X size={14} />}
                        </button>
                      ) : s.id !== null && (
                        isConfirmingDelete ? (
                          <div className="rcr-delete-confirm">
                            <span className="rcr-delete-confirm-label">{t('recurring.deleteConfirm')}</span>
                            <button className="rcr-delete-confirm-yes" onClick={() => handleDelete(s.id!)}>
                              {t('recurring.deleteConfirmYes')}
                            </button>
                            <button className="rcr-delete-confirm-no" onClick={() => setConfirmDeleteId(null)}>
                              {t('recurring.deleteConfirmNo')}
                            </button>
                          </div>
                        ) : (
                          <button
                            className="rcr-delete-btn"
                            title={t('recurring.deleteConfirm')}
                            onClick={() => setConfirmDeleteId(s.id!)}
                          >
                            <Trash2 size={14} />
                          </button>
                        )
                      )}
                    </td>
                  </tr>
                  {isExpanded && (
                    <tr key={`${rowKey}-history`} className="rcr-history-row">
                      <td colSpan={7}>
                        <table className="rcr-history-table">
                          <thead>
                            <tr>
                              <th>{t('recurring.history.date')}</th>
                              <th>{t('recurring.history.amount')}</th>
                              <th>{t('recurring.history.counterparty')}</th>
                              <th>{t('recurring.history.purpose')}</th>
                            </tr>
                          </thead>
                          <tbody>
                            {s.occurrences.length === 0 ? (
                              <tr>
                                <td colSpan={4} className="rcr-history-detail">—</td>
                              </tr>
                            ) : s.occurrences.map(o => (
                              <tr key={o.transactionId}>
                                <td>{o.date}</td>
                                <td className={o.amount < 0 ? 'rcr-amount--expense' : 'rcr-amount--income'}>
                                  {formatAmount(o.amount, s.currency)}
                                </td>
                                <td className="rcr-history-detail">
                                  {o.counterpartyName ?? o.counterpartyIban ?? '—'}
                                </td>
                                <td className="rcr-history-detail rcr-history-purpose">
                                  {o.purpose ?? '—'}
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

      {showAddModal && (
        <div className="rcr-modal-overlay" onClick={() => setShowAddModal(false)}>
          <div className="rcr-modal" onClick={e => e.stopPropagation()}>
            <div className="rcr-modal-header">
              <span className="rcr-modal-title">{t('recurring.modal.title')}</span>
              <button className="rcr-modal-close" onClick={() => setShowAddModal(false)}>
                <X size={16} />
              </button>
            </div>
            <form className="rcr-modal-body" onSubmit={handleAddSubmit}>
              <div className="rcr-form-row">
                <label className="rcr-form-label">{t('recurring.modal.label')}</label>
                <input
                  className="rcr-form-input"
                  type="text"
                  required
                  value={addForm.label}
                  onChange={e => setAddForm(f => ({ ...f, label: e.target.value }))}
                />
              </div>
              <div className="rcr-form-row rcr-form-row--split">
                <div className="rcr-form-group">
                  <label className="rcr-form-label">{t('recurring.modal.type')}</label>
                  <select
                    className="rcr-form-select"
                    value={addForm.type}
                    onChange={e => setAddForm(f => ({ ...f, type: e.target.value as RecurringType }))}
                  >
                    {ALL_TYPES.map(type => (
                      <option key={type} value={type}>{t(`recurring.type.${type}`)}</option>
                    ))}
                  </select>
                </div>
                <div className="rcr-form-group">
                  <label className="rcr-form-label">{t('recurring.modal.direction')}</label>
                  <div className="rcr-direction-toggle">
                    {(['EXPENSE', 'INCOME'] as RecurrenceDirection[]).map(d => (
                      <button
                        key={d}
                        type="button"
                        className={`rcr-direction-btn${addForm.direction === d ? ' rcr-direction-btn--active' : ''}`}
                        onClick={() => setAddForm(f => ({ ...f, direction: d }))}
                      >
                        {t(`recurring.filter${d === 'EXPENSE' ? 'Expenses' : 'Income'}`)}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
              <div className="rcr-form-row rcr-form-row--split">
                <div className="rcr-form-group">
                  <label className="rcr-form-label">{t('recurring.modal.cadence')}</label>
                  <select
                    className="rcr-form-select"
                    value={addForm.cadence}
                    onChange={e => setAddForm(f => ({ ...f, cadence: e.target.value as RecurrenceCadence }))}
                  >
                    {ALL_CADENCES.map(c => (
                      <option key={c} value={c}>{t(`recurring.cadence.${c}`)}</option>
                    ))}
                  </select>
                </div>
                <div className="rcr-form-group">
                  <label className="rcr-form-label">{t('recurring.modal.amount')}</label>
                  <div className="rcr-amount-input-row">
                    <input
                      className="rcr-form-input"
                      type="text"
                      inputMode="decimal"
                      required
                      placeholder="0,00"
                      value={addForm.expectedAmount}
                      onChange={e => setAddForm(f => ({ ...f, expectedAmount: e.target.value }))}
                    />
                    <input
                      className="rcr-form-input rcr-form-input--currency"
                      type="text"
                      value={addForm.currency}
                      maxLength={3}
                      onChange={e => setAddForm(f => ({ ...f, currency: e.target.value.toUpperCase() }))}
                    />
                  </div>
                </div>
              </div>
              <div className="rcr-form-row rcr-form-row--split">
                <div className="rcr-form-group">
                  <label className="rcr-form-label">{t('recurring.modal.lastBookingDate')}</label>
                  <input
                    className="rcr-form-input"
                    type="date"
                    value={addForm.lastBookingDate}
                    onChange={e => setAddForm(f => ({ ...f, lastBookingDate: e.target.value }))}
                  />
                </div>
                <div className="rcr-form-group">
                  <label className="rcr-form-label">{t('recurring.modal.account')}</label>
                  <select
                    className="rcr-form-select"
                    value={addForm.accountIban}
                    onChange={e => setAddForm(f => ({ ...f, accountIban: e.target.value }))}
                  >
                    <option value="">{t('recurring.modal.accountPlaceholder')}</option>
                    {accounts.map(a => (
                      <option key={a.iban} value={a.iban}>{a.name} ({a.iban})</option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="rcr-modal-footer">
                <button type="button" className="rcr-cancel-btn" onClick={() => setShowAddModal(false)}>
                  {t('recurring.modal.cancel')}
                </button>
                <button type="submit" className="rcr-save-btn" disabled={addSubmitting}>
                  {t('recurring.modal.submit')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
