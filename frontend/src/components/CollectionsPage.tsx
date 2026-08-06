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
  fetchAllTransactions,
  type Account,
  type TransactionItem,
} from '../api/transactions'
import type { CategoryNode } from '../api/rawImport'
import { getPresetRange, PRESETS, type Preset } from '../utils/datePresets'
import { CollectionCard } from './CollectionCard'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { cn } from '@/lib/utils'

function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

export default function CollectionsPage({ accounts, categories }: { accounts: Account[]; categories: CategoryNode[] }) {
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
    fetchCollections().then(setCollections).catch(() => {}).finally(() => setLoading(false))
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
    } catch { /* silent */ }
  }

  function handleRemoveTransaction(collectionId: number, txId: number) {
    setCollections(prev => prev.map(c =>
      c.id === collectionId ? { ...c, transactions: c.transactions.filter(tx => tx.id !== txId) } : c,
    ))
  }

  function handleTransactionAdded(collectionId: number, tx: TransactionItem) {
    setCollections(prev => prev.map(c =>
      c.id === collectionId ? { ...c, transactions: [...c.transactions, tx] } : c,
    ))
  }

  if (loading) return <div className="flex flex-col gap-4 p-6"><p className="text-sm text-muted-foreground">{t('common.loading')}</p></div>

  return (
    <div className="flex flex-col gap-4 p-6">
      <div className="flex items-center gap-2">
        <h2 className="text-base font-medium">{t('collections.title')}</h2>
        <Badge variant="secondary">{t('collections.count', { count: collections.length })}</Badge>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {!creatingNew && (
          <Button variant="outline" size="sm" onClick={() => { setCreatingNew(true); setFormError(null) }}>
            + {t('collections.createCollection')}
          </Button>
        )}
        {creatingNew && (
          <div className="flex flex-wrap items-center gap-2">
            <Input
              className="w-48"
              placeholder={t('collections.namePlaceholder')}
              value={newName}
              onChange={e => setNewName(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter') handleCreate() }}
              autoFocus
            />
            <Input
              className="w-48"
              placeholder={t('collections.notePlaceholder')}
              value={newNote}
              onChange={e => setNewNote(e.target.value)}
            />
            <Button size="sm" onClick={handleCreate} disabled={saving}>{saving ? '…' : t('common.save')}</Button>
            <Button size="sm" variant="ghost" onClick={() => { setCreatingNew(false); setNewName(''); setNewNote(''); setFormError(null) }}>✕</Button>
            {formError && <span className="text-sm text-destructive">{formError}</span>}
          </div>
        )}
      </div>

      {collections.length === 0 && !creatingNew
        ? <p className="text-sm text-muted-foreground">{t('collections.empty')}</p>
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
          accounts={accounts}
          categories={categories}
          onClose={() => setAssigningCollection(null)}
          onAdded={tx => { handleTransactionAdded(assigningCollection.id, tx); setAssigningCollection(null) }}
        />
      )}
    </div>
  )
}

function AddToCollectionModal({ collection, accounts, categories, onClose, onAdded }: {
  collection: CollectionDto
  accounts: Account[]
  categories: CategoryNode[]
  onClose: () => void
  onAdded: (tx: TransactionItem) => void
}) {
  const { t } = useTranslation()
  const today = new Date().toISOString().slice(0, 10)
  const firstOfYear = new Date(new Date().getFullYear(), 0, 1).toISOString().slice(0, 10)

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

  const subcategoriesFor = (cat: string) => categories.find(c => c.name === cat)?.children.map(s => s.name) ?? []

  async function loadWith(from: string, to: string) {
    setLoadingTx(true)
    setTransactions(null)
    setError(null)
    try {
      const resp = await fetchAllTransactions(from, to, accounts.find(a => a.iban === filterIban)?.id, filterCategory || undefined, filterSubcategory || undefined, undefined, undefined, undefined, collection.id ?? undefined)
      setTransactions(resp.transactions)
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

  const selectCls = 'rounded-lg border border-input bg-input/30 px-3 py-1.5 text-sm outline-none focus:border-ring'

  return (
    <Dialog open onOpenChange={open => { if (!open) onClose() }}>
      <DialogContent className="max-w-3xl max-h-[85vh] flex flex-col p-0 gap-0">
        <DialogHeader className="px-5 py-4 border-b shrink-0">
          <DialogTitle>{t('collections.assignTitle', { name: collection.name })}</DialogTitle>
        </DialogHeader>

        <div className="flex flex-wrap items-center gap-2 border-b px-5 py-3 shrink-0">
          <select className={selectCls} value={activePreset} onChange={e => e.target.value && applyPreset(e.target.value as Preset)}>
            <option value="">{t('budgets.presets.placeholder')}</option>
            {PRESETS.map(p => <option key={p} value={p}>{t(`budgets.presets.${p}`)}</option>)}
          </select>
          <div className="flex items-center gap-1">
            <span className="text-xs text-muted-foreground">{t('common.from')}</span>
            <input type="date" value={filterFrom} max={filterTo} onChange={e => { setFilterFrom(e.target.value); setActivePreset('') }} className={selectCls} />
          </div>
          <div className="flex items-center gap-1">
            <span className="text-xs text-muted-foreground">{t('common.to')}</span>
            <input type="date" value={filterTo} min={filterFrom} onChange={e => { setFilterTo(e.target.value); setActivePreset('') }} className={selectCls} />
          </div>
          {accounts.length > 0 && (
            <select className={selectCls} value={filterIban} onChange={e => setFilterIban(e.target.value)}>
              <option value="">{t('common.allAccounts')}</option>
              {accounts.map(a => <option key={a.iban} value={a.iban}>{a.name || a.iban}</option>)}
            </select>
          )}
          <select className={selectCls} value={filterCategory} onChange={e => { setFilterCategory(e.target.value); setFilterSubcategory('') }}>
            <option value="">{t('transactions.allCategories')}</option>
            {categories.map(c => <option key={c.name} value={c.name}>{c.name}</option>)}
          </select>
          {filterCategory && subcategoriesFor(filterCategory).length > 0 && (
            <select className={selectCls} value={filterSubcategory} onChange={e => setFilterSubcategory(e.target.value)}>
              <option value="">{t('transactions.allSubcategories')}</option>
              {subcategoriesFor(filterCategory).map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          )}
          <Button size="sm" onClick={() => loadWith(filterFrom, filterTo)} disabled={loadingTx}>
            {loadingTx ? '…' : t('common.load')}
          </Button>
        </div>

        <div className="flex-1 overflow-auto">
          {transactions === null && !loadingTx && <p className="px-5 py-4 text-sm text-muted-foreground">{t('common.selectDateAndLoad', { defaultValue: 'Filter setzen und Laden drücken.' })}</p>}
          {loadingTx && <p className="px-5 py-4 text-sm text-muted-foreground">{t('common.fetching')}</p>}
          {transactions != null && transactions.length === 0 && <p className="px-5 py-4 text-sm text-muted-foreground">{t('common.noTransactions')}</p>}
          {error && <p className="px-5 py-4 text-sm text-destructive">{error}</p>}
          {transactions != null && transactions.length > 0 && (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('transactions.columns.date')}</TableHead>
                  <TableHead>{t('transactions.columns.counterpartyName')}</TableHead>
                  <TableHead>{t('transactions.columns.category')}</TableHead>
                  <TableHead className="text-right">{t('transactions.columns.amount')}</TableHead>
                  <TableHead className="w-12" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {transactions.map(tx => (
                  <TableRow key={tx.id}>
                    <TableCell className="text-xs text-muted-foreground tabular-nums">{formatDate(tx.accountingDate)}</TableCell>
                    <TableCell className="text-sm">{tx.counterpartyName ?? tx.purpose ?? '—'}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">{tx.category}{tx.subcategory ? ` / ${tx.subcategory}` : ''}</TableCell>
                    <TableCell className={cn('text-right tabular-nums text-sm', tx.amount < 0 ? 'text-destructive' : '')}>{EUR.format(tx.amount)}</TableCell>
                    <TableCell>
                      <Button size="xs" variant="outline" onClick={() => doAdd(tx)} disabled={adding === tx.id}>
                        {adding === tx.id ? '…' : '+'}
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
