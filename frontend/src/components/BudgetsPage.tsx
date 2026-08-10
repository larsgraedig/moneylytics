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
import BudgetDetail from './BudgetDetail'
import type { CategoryNode } from '../api/rawImport'
import { fetchAllTransactions, type Account, type TransactionItem } from '../api/transactions'
import { getPresetRange, PRESETS, type Preset } from '../utils/datePresets'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { ScrollArea } from '@/components/ui/scroll-area'
import { cn } from '@/lib/utils'

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

function parseAmt(s: string): number | null {
  const n = parseFloat(s.replace(',', '.'))
  return isNaN(n) ? null : n
}

interface FormState { name: string; targetAmount: string; note: string }
const EMPTY_FORM: FormState = { name: '', targetAmount: '', note: '' }

function budgetToForm(b: Budget): FormState {
  return { name: b.name, targetAmount: b.targetAmount?.toString() ?? '', note: b.note ?? '' }
}

function BudgetProgressBar({ pct, negative }: { pct: number; negative: boolean }) {
  const fillPct = Math.min(pct * 100, 100)
  const done = pct >= 1
  return (
    <div className="flex items-center gap-2 min-w-24">
      <div className="relative flex-1 h-1.5 bg-muted rounded-full overflow-hidden">
        <div
          className={cn('absolute inset-y-0 left-0 rounded-full transition-all',
            negative ? (done ? 'bg-green-500' : 'bg-red-500') : (done ? 'bg-green-500' : 'bg-primary')
          )}
          style={{ width: `${fillPct}%` }}
        />
      </div>
      <span className={cn('text-xs tabular-nums', done ? 'text-green-500 font-medium' : 'text-muted-foreground')}>
        {Math.round(pct * 100)}%
      </span>
    </div>
  )
}

function BudgetPanel({ budget, onClose, onRemoveLink, onAssign }: {
  budget: Budget
  onClose: () => void
  onRemoveLink: (linkId: number) => void
  onAssign: () => void
}) {
  const { t } = useTranslation()
  const links = [...budget.transactionLinks].sort((a, b) => b.transactionDate.localeCompare(a.transactionDate))
  const total = budget.totalContributions

  return (
    <Sheet open onOpenChange={open => { if (!open) onClose() }}>
      <SheetContent side="right" className="flex flex-col w-full sm:w-[440px] p-0 gap-0">
        <SheetHeader className="border-b px-5 py-4 shrink-0">
          <SheetTitle>{budget.name}</SheetTitle>
        </SheetHeader>
        <div className="flex px-5 py-3 border-b shrink-0">
          <Button variant="outline" size="sm" onClick={onAssign}>+ {t('budgets.assign')}</Button>
        </div>
        <ScrollArea className="flex-1">
          {links.length === 0 && <p className="px-5 py-6 text-sm text-muted-foreground">{t('budgets.noTransactions')}</p>}
          {links.length > 0 && (
            <div className="flex flex-col">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('transactions.panel.date')}</TableHead>
                    <TableHead>{t('transactions.panel.subcategory')}</TableHead>
                    <TableHead className="text-right">{t('transactions.panel.amount')}</TableHead>
                    <TableHead className="w-8" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {links.map(link => (
                    <TableRow key={link.id}>
                      <TableCell className="text-xs text-muted-foreground tabular-nums">{formatDate(link.transactionDate)}</TableCell>
                      <TableCell className="text-sm">{link.transactionCategory} / {link.transactionSubcategory}</TableCell>
                      <TableCell className={cn('text-right tabular-nums text-sm', link.effectiveAmount < 0 ? 'text-destructive' : '')}>{EUR.format(link.effectiveAmount)}</TableCell>
                      <TableCell className="p-1">
                        <Button variant="ghost" size="icon-xs" title={t('budgets.remove')} onClick={() => onRemoveLink(link.id)}>×</Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <div className="flex items-center justify-between border-t px-5 py-3">
                <span className="text-sm text-muted-foreground">{t('transactions.panel.total')}</span>
                <span className={cn('font-medium tabular-nums', total < 0 ? 'text-destructive' : '')}>{EUR.format(total)}</span>
              </div>
            </div>
          )}
        </ScrollArea>
      </SheetContent>
    </Sheet>
  )
}

const selectCls = 'rounded-lg border border-input bg-input/30 px-3 py-1.5 text-sm outline-none focus:border-ring'

function AssignTransactionModal({ budget, defaultFrom, defaultTo, defaultIban, accounts, categories, onClose, onAssigned }: {
  budget: Budget
  defaultFrom: string
  defaultTo: string
  defaultIban?: string
  accounts: Account[]
  categories: CategoryNode[]
  onClose: () => void
  onAssigned: () => void
}) {
  const { t } = useTranslation()
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

  const subcategoriesFor = (cat: string) => categories.find(c => c.name === cat)?.children.map(s => s.name) ?? []

  async function loadWith(from: string, to: string) {
    setLoadingTx(true)
    setTransactions(null)
    setAssigningId(null)
    setError(null)
    try {
      const resp = await fetchAllTransactions(from, to, accounts.find(a => a.iban === filterIban)?.id, filterCategory || undefined, filterSubcategory || undefined, undefined, undefined, undefined, undefined, budget.id)
      setTransactions(resp.transactions)
    } catch { setTransactions([]) } finally { setLoadingTx(false) }
  }

  function applyPreset(preset: Preset) {
    const { from, to } = getPresetRange(preset)
    setActivePreset(preset); setFilterFrom(from); setFilterTo(to); loadWith(from, to)
  }

  async function doAssign(txId: number) {
    const amount = partialAmount !== '' ? parseAmt(partialAmount) : null
    setAssigning(true); setError(null)
    try {
      await assignTransaction(budget.id, txId, amount)
      onAssigned()
      setTransactions(prev => prev ? prev.filter(tx => tx.id !== txId) : null)
      setAssigningId(null); setPartialAmount('')
    } catch { setError(t('budgets.assignFailed')) } finally { setAssigning(false) }
  }

  return (
    <Dialog open onOpenChange={open => { if (!open) onClose() }}>
      <DialogContent className="max-w-3xl max-h-[85vh] flex flex-col p-0 gap-0">
        <DialogHeader className="px-5 py-4 border-b shrink-0">
          <DialogTitle>{t('budgets.assignTitle', { name: budget.name })}</DialogTitle>
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
          <Button size="sm" onClick={() => loadWith(filterFrom, filterTo)} disabled={loadingTx}>{loadingTx ? '…' : t('common.load')}</Button>
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
                  <TableHead>{t('transactions.columns.category')}</TableHead>
                  <TableHead>{t('transactions.columns.subcategory')}</TableHead>
                  <TableHead className="text-right">{t('transactions.columns.amount')}</TableHead>
                  <TableHead className="w-32" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {transactions.map(tx => {
                  const isActive = assigningId === tx.id
                  return (
                    <TableRow key={tx.id} className={isActive ? 'bg-accent/30' : undefined}>
                      <TableCell className="text-xs text-muted-foreground tabular-nums">{formatDate(tx.accountingDate)}</TableCell>
                      <TableCell className="text-sm">{tx.category}</TableCell>
                      <TableCell className="text-sm text-muted-foreground">{tx.subcategory}</TableCell>
                      <TableCell className={cn('text-right tabular-nums text-sm', tx.amount < 0 ? 'text-destructive' : '')}>{EUR.format(tx.amount)}</TableCell>
                      <TableCell>
                        {isActive ? (
                          <div className="flex items-center gap-1">
                            <Input type="number" step="0.01" placeholder={t('budgets.partialAmount')} value={partialAmount} onChange={e => setPartialAmount(e.target.value)} className="h-7 w-24" autoFocus />
                            <Button size="icon-xs" onClick={() => doAssign(tx.id)} disabled={assigning}>✓</Button>
                            <Button variant="ghost" size="icon-xs" onClick={() => { setAssigningId(null); setPartialAmount('') }}>×</Button>
                          </div>
                        ) : (
                          <Button size="xs" variant="outline" onClick={() => { setAssigningId(tx.id); setPartialAmount('') }}>+</Button>
                        )}
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}

export default function BudgetsPage({ from, to, accountId, accounts, categories }: { from: string; to: string; accountId?: number; accounts: Account[]; categories: CategoryNode[] }) {
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
    fetchBudgets().then(setBudgets).catch(() => {}).finally(() => setLoading(false))
  }, [])

  const drilldownBudget = budgets.find(b => b.id === drilldownId) ?? null
  const detailBudget = budgets.find(b => b.id === detailBudgetId) ?? null

  function startCreate() { setCreatingNew(true); setEditingId(null); setForm(EMPTY_FORM); setFormError(null) }
  function startEdit(budget: Budget) { setEditingId(budget.id); setCreatingNew(false); setForm(budgetToForm(budget)); setFormError(null) }
  function cancelForm() { setEditingId(null); setCreatingNew(false); setForm(EMPTY_FORM); setFormError(null) }

  async function handleSave() {
    const name = form.name.trim()
    if (!name) { setFormError(t('budgets.nameRequired')); return }
    const targetAmount = form.targetAmount !== '' ? parseAmt(form.targetAmount) : null
    const note = form.note.trim() || null
    setSaving(true); setFormError(null)
    try {
      if (creatingNew) { const created = await createBudget(name, targetAmount, note); setBudgets(prev => [...prev, created]) }
      else if (editingId != null) { const updated = await updateBudget(editingId, name, targetAmount, note); setBudgets(prev => prev.map(b => b.id === updated.id ? { ...updated, transactionLinks: b.transactionLinks } : b)) }
      cancelForm()
    } catch { setFormError(t('budgets.saveFailed')) } finally { setSaving(false) }
  }

  async function handleDelete(id: number) {
    if (!window.confirm(t('budgets.deleteConfirm'))) return
    try {
      await deleteBudget(id)
      setBudgets(prev => prev.filter(b => b.id !== id))
      if (editingId === id) cancelForm()
      if (drilldownId === id) setDrilldownId(null)
      if (detailBudgetId === id) setDetailBudgetId(null)
    } catch { setFormError(t('budgets.deleteFailed')) }
  }

  async function handleRemoveLink(linkId: number) {
    try { await removeTransactionLink(linkId); const fresh = await fetchBudgets(); setBudgets(fresh) } catch { /* silent */ }
  }

  function handleAssigned() { fetchBudgets().then(setBudgets).catch(() => {}) }

  if (loading) return <div className="flex flex-col p-6"><p className="text-sm text-muted-foreground">{t('common.fetching')}</p></div>

  if (detailBudget != null) {
    return <BudgetDetail budget={detailBudget} onBack={() => setDetailBudgetId(null)} onRemoveLink={handleRemoveLink} onAssign={() => setAssigningBudget(detailBudget)} />
  }

  return (
    <div className="flex flex-col gap-4 p-6">
      <div className="flex flex-wrap items-center gap-2">
        {!creatingNew && editingId == null && (
          <Button variant="outline" size="sm" onClick={startCreate}>+ {t('budgets.createBudget')}</Button>
        )}
        {(creatingNew || editingId != null) && (
          <div className="flex flex-wrap items-center gap-2">
            <Input className="w-40" placeholder={t('budgets.namePlaceholder')} value={form.name} onChange={e => setForm(p => ({ ...p, name: e.target.value }))} autoFocus />
            <Input className="w-28" type="number" min="0" step="1" placeholder={t('budgets.targetAmountPlaceholder')} value={form.targetAmount} onChange={e => setForm(p => ({ ...p, targetAmount: e.target.value }))} />
            <Input className="w-40" placeholder={t('budgets.notePlaceholder')} value={form.note} onChange={e => setForm(p => ({ ...p, note: e.target.value }))} />
            <Button size="sm" onClick={handleSave} disabled={saving}>{saving ? '…' : t('common.save')}</Button>
            <Button size="sm" variant="ghost" onClick={cancelForm}>✕</Button>
            {formError && <span className="text-sm text-destructive">{formError}</span>}
          </div>
        )}
      </div>

      {budgets.length === 0 && !creatingNew ? (
        <p className="text-sm text-muted-foreground">{t('budgets.noBudgets')}</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('budgets.columns.name')}</TableHead>
              <TableHead className="text-right">{t('budgets.columns.balance')}</TableHead>
              <TableHead className="text-right">{t('budgets.columns.target')}</TableHead>
              <TableHead>{t('budgets.columns.progress')}</TableHead>
              <TableHead className="w-20" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {budgets.map(budget => {
              const isEditing = editingId === budget.id
              const hasTarget = budget.targetAmount != null && budget.targetAmount !== 0
              const isNegativeTarget = hasTarget && budget.targetAmount! < 0
              const pct = hasTarget ? Math.abs(budget.balance) / Math.abs(budget.targetAmount!) : null
              return (
                <TableRow key={budget.id} className={cn('cursor-pointer', isEditing && 'bg-muted/30')} onClick={() => !isEditing && setDrilldownId(budget.id)}>
                  <TableCell>
                    <button
                      className="font-medium hover:underline underline-offset-2 text-left"
                      onClick={e => { e.stopPropagation(); setDetailBudgetId(budget.id) }}
                    >
                      {budget.name}
                    </button>
                    {budget.note && <span className="ml-2 text-xs text-muted-foreground">{budget.note}</span>}
                  </TableCell>
                  <TableCell className={cn('text-right tabular-nums', budget.balance >= 0 ? 'text-green-500' : 'text-destructive')}>
                    {EUR.format(budget.balance)}
                  </TableCell>
                  <TableCell className="text-right tabular-nums text-sm">
                    {budget.targetAmount != null ? EUR.format(budget.targetAmount) : <span className="text-muted-foreground">—</span>}
                  </TableCell>
                  <TableCell>
                    {pct != null ? <BudgetProgressBar pct={pct} negative={isNegativeTarget} /> : <span className="text-muted-foreground">—</span>}
                  </TableCell>
                  <TableCell onClick={e => e.stopPropagation()}>
                    <div className="flex gap-1">
                      <Button variant="ghost" size="icon-xs" title={t('common.edit')} onClick={() => isEditing ? cancelForm() : startEdit(budget)}>
                        {isEditing ? '✕' : '✎'}
                      </Button>
                      <Button variant="ghost" size="icon-xs" title={t('common.delete')} onClick={() => handleDelete(budget.id)}>␡</Button>
                    </div>
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      )}

      {drilldownBudget != null && (
        <BudgetPanel budget={drilldownBudget} onClose={() => setDrilldownId(null)} onRemoveLink={handleRemoveLink} onAssign={() => setAssigningBudget(drilldownBudget)} />
      )}

      {assigningBudget != null && (
        <AssignTransactionModal
          budget={assigningBudget}
          defaultFrom={from}
          defaultTo={to}
          defaultIban={accounts.find(a => a.id === accountId)?.iban}
          accounts={accounts}
          categories={categories}
          onClose={() => setAssigningBudget(null)}
          onAssigned={handleAssigned}
        />
      )}
    </div>
  )
}
