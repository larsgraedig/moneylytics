import { useEffect, useMemo, useState } from 'react'
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { cn } from '@/lib/utils'

const PERIODS: ThresholdPeriod[] = ['WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY']
const PERIOD_IDEAL_DAYS: Record<ThresholdPeriod, number> = {
  WEEKLY: 7,
  MONTHLY: 30,
  QUARTERLY: 91,
  YEARLY: 365,
}

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 })
const EUR2 = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

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

interface DrilldownState {
  thresholdId: number
  categoryPath: string[]
  transactions: TransactionItem[] | null
  loading: boolean
}

const STATUS_COLORS: Record<Status, string> = {
  ok: 'bg-green-500',
  notice: 'bg-yellow-400',
  warning: 'bg-orange-500',
  critical: 'bg-red-500',
}

function ProgressBar({ progress }: { progress: Progress }) {
  const fillPct = Math.min(progress.pct * 100, 100)
  const overBudget = progress.pct > 1

  return (
    <div className="flex items-center gap-2 min-w-24">
      <div className="relative flex-1 h-1.5 bg-muted rounded-full overflow-hidden">
        <div
          className={cn('absolute inset-y-0 left-0 rounded-full transition-all', STATUS_COLORS[progress.status])}
          style={{ width: `${fillPct}%` }}
        />
        {progress.tickNotice != null && (
          <div className="absolute inset-y-0 w-px bg-yellow-400/70" style={{ left: `${progress.tickNotice * 100}%` }} />
        )}
        {progress.tickWarning != null && (
          <div className="absolute inset-y-0 w-px bg-orange-500/70" style={{ left: `${progress.tickWarning * 100}%` }} />
        )}
      </div>
      <span className={cn('text-xs tabular-nums', overBudget ? 'text-destructive font-medium' : 'text-muted-foreground')}>
        {Math.round(progress.pct * 100)}%
      </span>
    </div>
  )
}

function DrilldownModal({ state, from, to, onClose }: { state: DrilldownState; from: string; to: string; onClose: () => void }) {
  const { t } = useTranslation()
  const title = state.categoryPath.filter(Boolean).join(' > ')
  const total = state.transactions?.reduce((s, tx) => s + Math.abs(tx.effectiveAmount), 0) ?? 0

  return (
    <Dialog open onOpenChange={open => { if (!open) onClose() }}>
      <DialogContent className="max-w-2xl max-h-[80vh] flex flex-col p-0 gap-0">
        <DialogHeader className="px-5 py-4 border-b shrink-0">
          <DialogTitle>{title}</DialogTitle>
          <p className="text-xs text-muted-foreground">{from} → {to}</p>
        </DialogHeader>

        {state.loading && <p className="px-5 py-6 text-sm text-muted-foreground">{t('common.loading')}</p>}

        {!state.loading && state.transactions?.length === 0 && (
          <p className="px-5 py-6 text-sm text-muted-foreground">{t('cashflow.noTransactions')}</p>
        )}

        {!state.loading && state.transactions != null && state.transactions.length > 0 && (
          <>
            <div className="flex-1 overflow-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('common.date')}</TableHead>
                    <TableHead>{t('common.category')}</TableHead>
                    <TableHead>{t('common.purpose')}</TableHead>
                    <TableHead className="text-right">{t('common.amount')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {state.transactions.map(tx => (
                    <TableRow key={tx.id}>
                      <TableCell className="text-xs text-muted-foreground tabular-nums whitespace-nowrap">{tx.accountingDate}</TableCell>
                      <TableCell className="text-sm">{tx.category}{tx.subcategory ? ` / ${tx.subcategory}` : ''}</TableCell>
                      <TableCell className="text-sm text-muted-foreground max-w-48 truncate">{tx.purpose ?? '—'}</TableCell>
                      <TableCell className="text-right tabular-nums">{EUR2.format(Math.abs(tx.effectiveAmount))}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            <div className="flex justify-between items-center border-t px-5 py-3 shrink-0">
              <span className="text-sm text-muted-foreground">{t('common.total')}</span>
              <span className="font-medium tabular-nums">{EUR2.format(total)}</span>
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}

const inputCls = 'h-7 w-20 rounded border border-input bg-background px-2 text-xs outline-none focus:ring-1 focus:ring-ring'
const selectCls = 'h-7 rounded border border-input bg-background px-2 text-xs outline-none focus:ring-1 focus:ring-ring'

export default function ThresholdsPage({ accountId, categories }: { accountId?: number; categories: CategoryNode[] }) {
  const { t } = useTranslation()
  const [years, setYears] = useState<1 | 2 | 3>(1)
  const localTo = useMemo(() => new Date().toISOString().slice(0, 10), [])
  const localFrom = useMemo(() => {
    const d = new Date()
    d.setFullYear(d.getFullYear() - years)
    return d.toISOString().slice(0, 10)
  }, [years])
  const [thresholds, setThresholds] = useState<Threshold[]>([])
  const [statusMap, setStatusMap] = useState<Map<number, ThresholdStatusItem>>(new Map())
  const [statusLoaded, setStatusLoaded] = useState(false)

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

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void loadStatus() }, [years, accountId])

  async function loadStatus() {
    setStatusLoaded(false)
    setStatusMap(new Map())
    try {
      const items = await fetchThresholdStatus(localFrom, localTo, accountId)
      setStatusMap(new Map(items.map(item => [item.thresholdId, item])))
      setStatusLoaded(true)
    } catch { /* silent */ }
  }

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
        if (idx >= 0) { const next = [...prev]; next[idx] = saved; return next }
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
    if (newCategoryId == null) { setNewFormError(t('limits.categoryRequired')); return }
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
        if (idx >= 0) { const next = [...prev]; next[idx] = saved; return next }
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
      const resp = await fetchTransactionList(localFrom, localTo, path[0], path[1], accountId)
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

  return (
    <div className="flex flex-col gap-4 p-6">
      <div className="flex flex-wrap items-center gap-2">
        {([1, 2, 3] as const).map(y => (
          <Button key={y} variant={years === y ? 'default' : 'outline'} size="sm" onClick={() => setYears(y)}>
            {y}J
          </Button>
        ))}
        {!isAddingNew && (
          <Button variant="outline" size="sm" onClick={startAddNew}>
            + {t('limits.addThreshold')}
          </Button>
        )}
        {statusLoaded && (
          <Badge variant="secondary" className="text-xs">
            {t('limits.statusLoaded', { count: years })}
          </Badge>
        )}
      </div>

      {sortedThresholds.length === 0 && !isAddingNew ? (
        <p className="text-sm text-muted-foreground">{t('limits.noCategories')}</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('limits.columns.category')}</TableHead>
              {statusLoaded && <TableHead>{t('limits.columns.spending')}</TableHead>}
              <TableHead>{t('limits.columns.period')}</TableHead>
              <TableHead className="text-yellow-500/80">{t('limits.columns.notice')}</TableHead>
              <TableHead className="text-orange-500/80">{t('limits.columns.warning')}</TableHead>
              <TableHead className="text-red-500/80">{t('limits.columns.critical')}</TableHead>
              {statusLoaded && <TableHead>{t('limits.columns.progress')}</TableHead>}
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {sortedThresholds.map(threshold => {
              const isEditing = editingId === threshold.id
              const item = statusLoaded ? statusMap.get(threshold.id) : undefined
              const spending = item?.spending ?? 0
              const progress = item != null ? progressFromItem(item) : null
              const best = item ?? pickBest([threshold], localFrom, localTo)

              if (isEditing) {
                return (
                  <TableRow key={threshold.id} className="bg-muted/30">
                    <TableCell className="font-medium text-sm">{threshold.categoryPath.filter(Boolean).join(' > ')}</TableCell>
                    {statusLoaded && <TableCell className="text-sm tabular-nums">{spending > 0 ? EUR2.format(spending) : '—'}</TableCell>}
                    <TableCell>
                      <select className={selectCls} value={form.period} onChange={e => setForm(p => ({ ...p, period: e.target.value as ThresholdPeriod }))}>
                        {PERIODS.map(p => <option key={p} value={p}>{t(`limits.period.${p.toLowerCase()}`)}</option>)}
                      </select>
                    </TableCell>
                    <TableCell><input className={inputCls} type="number" min="0" step="1" placeholder="—" value={form.notice} onChange={e => setForm(p => ({ ...p, notice: e.target.value }))} /></TableCell>
                    <TableCell><input className={inputCls} type="number" min="0" step="1" placeholder="—" value={form.warning} onChange={e => setForm(p => ({ ...p, warning: e.target.value }))} /></TableCell>
                    <TableCell><input className={inputCls} type="number" min="0" step="1" placeholder="—" value={form.critical} onChange={e => setForm(p => ({ ...p, critical: e.target.value }))} /></TableCell>
                    {statusLoaded && <TableCell />}
                    <TableCell>
                      <div className="flex items-center gap-1 flex-wrap">
                        <Button size="xs" onClick={() => handleSave(threshold)} disabled={saving}>{saving ? '…' : t('common.save')}</Button>
                        <Button size="xs" variant="destructive" onClick={() => handleDelete(threshold.id)}>{t('common.delete')}</Button>
                        <Button size="xs" variant="ghost" onClick={cancelEdit}>✕</Button>
                        {formError && <span className="text-xs text-destructive">{formError}</span>}
                      </div>
                    </TableCell>
                  </TableRow>
                )
              }

              return (
                <TableRow key={threshold.id} className="cursor-pointer" onClick={() => startEdit(threshold)}>
                  <TableCell className="font-medium text-sm">{threshold.categoryPath.filter(Boolean).join(' > ')}</TableCell>
                  {statusLoaded && (
                    <TableCell>
                      {spending > 0 ? (
                        <button
                          className="text-sm tabular-nums underline-offset-2 hover:underline"
                          onClick={e => { e.stopPropagation(); openDrilldown(threshold) }}
                        >
                          {EUR2.format(spending)}
                        </button>
                      ) : <span className="text-muted-foreground">—</span>}
                    </TableCell>
                  )}
                  <TableCell className="text-muted-foreground text-sm">{best ? t(`limits.period.${best.period.toLowerCase()}`) : '—'}</TableCell>
                  <TableCell className="text-sm tabular-nums">{best?.notice != null ? EUR.format(best.notice) : <span className="text-muted-foreground">—</span>}</TableCell>
                  <TableCell className="text-sm tabular-nums">{best?.warning != null ? EUR.format(best.warning) : <span className="text-muted-foreground">—</span>}</TableCell>
                  <TableCell className="text-sm tabular-nums">{best?.critical != null ? EUR.format(best.critical) : <span className="text-muted-foreground">—</span>}</TableCell>
                  {statusLoaded && <TableCell>{progress != null ? <ProgressBar progress={progress} /> : <span className="text-muted-foreground">—</span>}</TableCell>}
                  <TableCell />
                </TableRow>
              )
            })}

            {isAddingNew && (
              <TableRow className="bg-muted/30">
                <TableCell>
                  <CategoryPathInput value={newCategoryId} onChange={setNewCategoryId} tree={categories} placeholder={t('limits.selectCategory')} className="w-48" />
                </TableCell>
                {statusLoaded && <TableCell />}
                <TableCell>
                  <select className={selectCls} value={newForm.period} onChange={e => setNewForm(p => ({ ...p, period: e.target.value as ThresholdPeriod }))}>
                    {PERIODS.map(p => <option key={p} value={p}>{t(`limits.period.${p.toLowerCase()}`)}</option>)}
                  </select>
                </TableCell>
                <TableCell><input className={inputCls} type="number" min="0" step="1" placeholder="—" value={newForm.notice} onChange={e => setNewForm(p => ({ ...p, notice: e.target.value }))} /></TableCell>
                <TableCell><input className={inputCls} type="number" min="0" step="1" placeholder="—" value={newForm.warning} onChange={e => setNewForm(p => ({ ...p, warning: e.target.value }))} /></TableCell>
                <TableCell><input className={inputCls} type="number" min="0" step="1" placeholder="—" value={newForm.critical} onChange={e => setNewForm(p => ({ ...p, critical: e.target.value }))} /></TableCell>
                {statusLoaded && <TableCell />}
                <TableCell>
                  <div className="flex items-center gap-1 flex-wrap">
                    <Button size="xs" onClick={handleSaveNew} disabled={newSaving}>{newSaving ? '…' : t('common.save')}</Button>
                    <Button size="xs" variant="ghost" onClick={cancelAddNew}>✕</Button>
                    {newFormError && <span className="text-xs text-destructive">{newFormError}</span>}
                  </div>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      )}

      {drilldown != null && (
        <DrilldownModal state={drilldown} from={localFrom} to={localTo} onClose={() => setDrilldown(null)} />
      )}
    </div>
  )
}
