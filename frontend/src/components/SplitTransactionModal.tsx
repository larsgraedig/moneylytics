import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { CategoryNode } from '../api/rawImport'
import { splitTransaction, type SplitItemRequest, type TransactionItem } from '../api/transactions'
import { CategoryPathInput } from './CategoryPathInput'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface SplitEntry {
  amount: string
  categoryId: number | null
  comment: string
}

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

export function SplitTransactionModal({
  transaction,
  categories,
  onClose,
  onSplit,
  onCategoryCreated,
}: {
  transaction: TransactionItem
  categories: CategoryNode[]
  onClose: () => void
  onSplit: () => void
  onCategoryCreated?: (node: CategoryNode) => void
}) {
  const { t } = useTranslation()
  const [entries, setEntries] = useState<SplitEntry[]>([
    { amount: '', categoryId: transaction.categoryId ?? null, comment: '' },
    { amount: '', categoryId: transaction.categoryId ?? null, comment: '' },
  ])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const totalAmount = transaction.amount
  const enteredSum = entries.reduce((acc, e) => {
    const v = parseFloat(e.amount.replace(',', '.'))
    return isNaN(v) ? acc : acc + v
  }, 0)
  const remaining = totalAmount - enteredSum

  function normalizeAmountSign(value: string): string {
    const num = parseFloat(value.replace(',', '.'))
    if (isNaN(num) || num === 0) return value
    const shouldBeNegative = totalAmount < 0
    if (shouldBeNegative && num > 0) return String(-num)
    if (!shouldBeNegative && num < 0) return String(-num)
    return value
  }

  function updateEntry(idx: number, field: keyof SplitEntry, value: string | number | null) {
    const normalized = field === 'amount' ? normalizeAmountSign(value as string) : value
    setEntries(prev => prev.map((e, i) => (i === idx ? { ...e, [field]: normalized } : e)))
  }

  function addEntry() {
    setEntries(prev => [...prev, { amount: '', categoryId: null, comment: '' }])
  }

  function removeEntry(idx: number) {
    setEntries(prev => prev.filter((_, i) => i !== idx))
  }

  async function handleConfirm() {
    setError(null)
    if (entries.length < 2) {
      setError(t('transactions.split.minSplits'))
      return
    }
    const splits: SplitItemRequest[] = entries.map(e => ({
      amount: parseFloat(e.amount.replace(',', '.')),
      categoryId: e.categoryId,
      comment: e.comment || null,
    }))
    const sum = splits.reduce((a, s) => a + s.amount, 0)
    if (Math.abs(sum - totalAmount) > 0.001) {
      setError(t('transactions.split.sumMismatch'))
      return
    }
    setSaving(true)
    try {
      await splitTransaction(transaction.id, splits)
      onSplit()
    } catch {
      setError('Fehler beim Aufteilen.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open onOpenChange={open => { if (!open) onClose() }}>
      <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t('transactions.split.title')}</DialogTitle>
        </DialogHeader>

        <p className="text-sm text-muted-foreground">
          {t('common.total')}: <strong className="text-foreground">{EUR.format(totalAmount)}</strong>
          {' · '}
          {t('transactions.split.remaining')}:{' '}
          <strong className={cn(remaining < -0.001 ? 'text-destructive' : 'text-green-500')}>
            {EUR.format(remaining)}
          </strong>
        </p>

        <div className="flex flex-col gap-2">
          {entries.map((entry, idx) => (
            <div key={idx} className="flex flex-col gap-2 rounded-lg border p-3">
              <div className="flex gap-2">
                <Input
                  type="number"
                  step="0.01"
                  placeholder={t('transactions.split.amount')}
                  value={entry.amount}
                  onChange={e => updateEntry(idx, 'amount', e.target.value)}
                  className="w-28"
                />
                <CategoryPathInput
                  value={entry.categoryId}
                  onChange={id => updateEntry(idx, 'categoryId', id)}
                  tree={categories}
                  onCategoryCreated={onCategoryCreated}
                  placeholder={t('transactions.split.category')}
                  className="ri-cat-input flex-1"
                />
                {entries.length > 2 && (
                  <Button variant="ghost" size="sm" onClick={() => removeEntry(idx)}>
                    {t('transactions.split.removeSplit')}
                  </Button>
                )}
              </div>
              <Input
                type="text"
                placeholder={t('transactions.split.comment')}
                value={entry.comment}
                onChange={e => updateEntry(idx, 'comment', e.target.value)}
              />
            </div>
          ))}
        </div>

        <Button variant="outline" onClick={addEntry} className="w-full border-dashed">
          + {t('transactions.split.addSplit')}
        </Button>

        {error && <p className="text-sm text-destructive">{error}</p>}

        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose}>{t('transactions.split.cancel')}</Button>
          <Button onClick={handleConfirm} disabled={saving}>
            {saving ? '…' : t('transactions.split.confirm')}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
