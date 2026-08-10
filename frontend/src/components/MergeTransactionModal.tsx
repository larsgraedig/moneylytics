import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { mergeTransactions, type TransactionItem } from '../api/transactions'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { cn } from '@/lib/utils'

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

export function MergeTransactionModal({
  transactions,
  onClose,
  onMerge,
}: {
  transactions: TransactionItem[]
  onClose: () => void
  onMerge: () => void
}) {
  const { t } = useTranslation()
  const total = transactions.reduce((acc, tx) => acc + tx.amount, 0)
  const today = new Date().toISOString().slice(0, 10)
  const [name, setName] = useState('')
  const [comment, setComment] = useState('')
  const [accountingDate, setAccountingDate] = useState(today)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleConfirm() {
    setError(null)
    setSaving(true)
    try {
      await mergeTransactions(
        transactions.map(tx => tx.id),
        accountingDate,
        name || null,
        comment || null,
      )
      onMerge()
    } catch {
      setError('Fehler beim Zusammenfassen.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open onOpenChange={open => { if (!open) onClose() }}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{t('transactions.merge.title')}</DialogTitle>
        </DialogHeader>

        <div className="rounded-lg border p-3 flex flex-col gap-1">
          {transactions.map(tx => (
            <div key={tx.id} className="flex justify-between text-sm">
              <span className="text-muted-foreground">{tx.accountingDate} – {tx.counterpartyName ?? tx.purpose ?? `#${tx.id}`}</span>
              <span className={cn('tabular-nums', tx.amount < 0 ? 'text-destructive' : 'text-green-500')}>
                {EUR.format(tx.amount)}
              </span>
            </div>
          ))}
          <Separator className="my-1" />
          <div className="flex justify-between text-sm font-medium">
            <span className="text-muted-foreground">{t('transactions.merge.total')}</span>
            <span className={cn('tabular-nums', total < 0 ? 'text-destructive' : 'text-green-500')}>
              {EUR.format(total)}
            </span>
          </div>
        </div>

        <div className="flex flex-col gap-3">
          <Input
            placeholder={t('transactions.merge.name')}
            value={name}
            onChange={e => setName(e.target.value)}
          />
          <Input
            placeholder={t('transactions.merge.comment')}
            value={comment}
            onChange={e => setComment(e.target.value)}
          />
          <div className="flex flex-col gap-1.5">
            <Label>{t('transactions.merge.accountingDate')}</Label>
            <Input
              type="date"
              value={accountingDate}
              onChange={e => setAccountingDate(e.target.value)}
            />
          </div>
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}

        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose}>{t('transactions.merge.cancel')}</Button>
          <Button onClick={handleConfirm} disabled={saving}>
            {saving ? '…' : t('transactions.merge.confirm')}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
