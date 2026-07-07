import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  addTransactionToCollection,
  createCollection,
  deleteCollection,
  fetchCollections,
  type CollectionDto,
} from '../api/collections'
import {
  fetchAccounts,
  fetchAllTransactions,
  type Account,
  type TransactionItem,
} from '../api/transactions'
import { fetchCategories, type CategoryGroup } from '../api/rawImport'
import { getPresetRange, PRESETS, type Preset } from '../utils/datePresets'
import { CollectionCard } from './CollectionCard'

function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

export default function CollectionsPage() {
  const { t } = useTranslation()
  const [collections, setCollections] = useState<CollectionDto[]>([])
  const [loading, setLoading] = useState(true)
  const [creatingNew, setCreatingNew] = useState(false)
  const [newName, setNewName] = useState('')
  const [newNote, setNewNote] = useState('')
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [assigningCollection, setAssigningCollection] = useState<CollectionDto | null>(null)

  useEffect(() => {
    fetchCollections()
      .then(setCollections)
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  async function handleCreate() {
    const name = newName.trim()
    if (!name) { setFormError(t('collections.nameRequired')); return }
    setSaving(true)
    setFormError(null)
    try {
      const created = await createCollection(name, newNote.trim() || null)
      setCollections(prev => [...prev, created])
      setCreatingNew(false)
      setNewName('')
      setNewNote('')
    } catch {
      setFormError(t('collections.saveFailed'))
    } finally {
      setSaving(false)
    }
  }

  function handleUpdate(id: number, name: string, note: string | null) {
    setCollections(prev => prev.map(c => c.id === id ? { ...c, name, note } : c))
  }

  async function handleDelete(id: number) {
    try {
      await deleteCollection(id)
      setCollections(prev => prev.filter(c => c.id !== id))
    } catch {
      // silent — user can retry
    }
  }

  function handleRemoveTransaction(collectionId: number, txId: number) {
    setCollections(prev =>
      prev.map(c =>
        c.id === collectionId
          ? { ...c, transactions: c.transactions.filter(tx => tx.id !== txId) }
          : c,
      ),
    )
  }

  function handleTransactionAdded(collectionId: number, tx: TransactionItem) {
    setCollections(prev =>
      prev.map(c =>
        c.id === collectionId
          ? { ...c, transactions: [...c.transactions, tx] }
          : c,
      ),
    )
  }

  if (loading) return <div className="ltx-page"><span className="ltx-status">{t('common.loading')}</span></div>

  return (
    <div className="ltx-page">
      <div className="ltx-header">
        <h2 className="ltx-title">{t('collections.title')}</h2>
        <span className="ltx-count">{t('collections.count', { count: collections.length })}</span>
      </div>

      <div className="bdg-controls">
        {!creatingNew && (
          <button className="bdg-create-btn" onClick={() => { setCreatingNew(true); setFormError(null) }}>
            + {t('collections.createCollection')}
          </button>
        )}
        {creatingNew && (
          <div className="bdg-form">
            <input
              className="bdg-form-input"
              placeholder={t('collections.namePlaceholder')}
              value={newName}
              onChange={e => setNewName(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter') handleCreate() }}
              autoFocus
            />
            <input
              className="bdg-form-input"
              placeholder={t('collections.notePlaceholder')}
              value={newNote}
              onChange={e => setNewNote(e.target.value)}
            />
            <button className="bdg-btn bdg-btn--save" onClick={handleCreate} disabled={saving}>
              {saving ? '…' : t('common.save')}
            </button>
            <button className="bdg-btn bdg-btn--cancel" onClick={() => { setCreatingNew(false); setNewName(''); setNewNote(''); setFormError(null) }}>
              ✕
            </button>
            {formError && <span className="bdg-form-error">{formError}</span>}
          </div>
        )}
      </div>

      {collections.length === 0 && !creatingNew
        ? <p className="ltx-status">{t('collections.empty')}</p>
        : collections.map(c => (
          <div key={c.id} id={`col-${c.id}`}>
            <CollectionCard
              collection={c}
              onUpdate={handleUpdate}
              onDelete={handleDelete}
              onRemoveTransaction={handleRemoveTransaction}
              onAddTransaction={setAssigningCollection}
            />
          </div>
        ))
      }

      {assigningCollection && (
        <AddToCollectionModal
          collection={assigningCollection}
          onClose={() => setAssigningCollection(null)}
          onAdded={tx => { handleTransactionAdded(assigningCollection.id, tx); setAssigningCollection(null) }}
        />
      )}
    </div>
  )
}

function AddToCollectionModal({
  collection,
  onClose,
  onAdded,
}: {
  collection: CollectionDto
  onClose: () => void
  onAdded: (tx: TransactionItem) => void
}) {
  const { t } = useTranslation()
  const today = new Date().toISOString().slice(0, 10)
  const firstOfYear = new Date(new Date().getFullYear(), 0, 1).toISOString().slice(0, 10)

  const [accounts, setAccounts] = useState<Account[]>([])
  const [categories, setCategories] = useState<CategoryGroup[]>([])
  const [filterFrom, setFilterFrom] = useState(firstOfYear)
  const [filterTo, setFilterTo] = useState(today)
  const [filterIban, setFilterIban] = useState('')
  const [filterCategory, setFilterCategory] = useState('')
  const [filterSubcategory, setFilterSubcategory] = useState('')
  const [activePreset, setActivePreset] = useState<Preset | ''>('')
  const [transactions, setTransactions] = useState<TransactionItem[] | null>(null)
  const [loadingTx, setLoadingTx] = useState(false)
  const [adding, setAdding] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  const assignedIds = new Set(collection.transactions.map(tx => tx.id))

  useEffect(() => {
    fetchAccounts().then(setAccounts).catch(() => {})
    fetchCategories().then(r => setCategories(r.categories)).catch(() => {})
  }, [])

  const subcategoriesFor = (cat: string) => categories.find(c => c.name === cat)?.subcategories ?? []

  async function loadWith(from: string, to: string) {
    setLoadingTx(true)
    setTransactions(null)
    setError(null)
    try {
      const resp = await fetchAllTransactions(from, to, filterIban || undefined, filterCategory || undefined, filterSubcategory || undefined)
      setTransactions(resp.transactions.filter(tx => !assignedIds.has(tx.id)))
    } catch {
      setTransactions([])
    } finally {
      setLoadingTx(false)
    }
  }

  function applyPreset(preset: Preset) {
    const { from, to } = getPresetRange(preset)
    setActivePreset(preset)
    setFilterFrom(from)
    setFilterTo(to)
    loadWith(from, to)
  }

  async function doAdd(tx: TransactionItem) {
    setAdding(tx.id)
    setError(null)
    try {
      await addTransactionToCollection(collection.id, tx.id)
      onAdded(tx)
    } catch {
      setError(t('collections.assignFailed'))
      setAdding(null)
    }
  }

  return (
    <div className="bdg-assign-backdrop" onClick={onClose}>
      <div className="bdg-assign-modal bdg-assign-modal--lg" onClick={e => e.stopPropagation()}>
        <div className="bdg-modal-header">
          <span className="bdg-modal-title">{t('collections.assignTitle', { name: collection.name })}</span>
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
            <input type="date" value={filterFrom} max={filterTo} onChange={e => { setFilterFrom(e.target.value); setActivePreset('') }} />
          </label>
          <div className="range-sep" />
          <label className="range-field">
            <span className="range-label">{t('common.to')}</span>
            <input type="date" value={filterTo} min={filterFrom} onChange={e => { setFilterTo(e.target.value); setActivePreset('') }} />
          </label>

          {accounts.length > 0 && (
            <select className="account-select" value={filterIban} onChange={e => setFilterIban(e.target.value)}>
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

          <button className="load-btn" onClick={() => loadWith(filterFrom, filterTo)} disabled={loadingTx}>
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
                  <th>{t('transactions.columns.counterpartyName')}</th>
                  <th>{t('transactions.columns.category')}</th>
                  <th className="txn-col-amount">{t('transactions.columns.amount')}</th>
                  <th className="bdg-assign-col-action" />
                </tr>
              </thead>
              <tbody>
                {transactions.map(tx => (
                  <tr key={tx.id}>
                    <td className="txn-cell-date">{formatDate(tx.accountingDate)}</td>
                    <td className="txn-cell-sub">{tx.counterpartyName ?? tx.purpose ?? '—'}</td>
                    <td className="txn-cell-sub">{tx.category}{tx.subcategory ? ` / ${tx.subcategory}` : ''}</td>
                    <td className={`txn-cell-amount${tx.amount < 0 ? ' negative' : ''}`}>
                      {EUR.format(tx.amount)}
                    </td>
                    <td className="bdg-assign-col-action">
                      <button
                        className="bdg-assign-row-btn"
                        onClick={() => doAdd(tx)}
                        disabled={adding === tx.id}
                      >
                        {adding === tx.id ? '…' : '+'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  )
}
