import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { CategoryNode } from '../api/rawImport'
import { createVirtualTransaction, type Account } from '../api/transactions'
import { CategoryPathInput } from './CategoryPathInput'
import { DatePicker } from '@/components/ui/date-picker'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'

function parseIso(s: string): Date | null {
  if (!s) return null
  const [y, m, d] = s.split('-').map(Number)
  return new Date(y, m - 1, d)
}

function isoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function CreateVirtualTransactionModal({
  accounts,
  categories,
  defaultDate,
  onClose,
  onCreate,
  onCategoryCreated,
}: {
  accounts: Account[]
  categories: CategoryNode[]
  defaultDate: string
  onClose: () => void
  onCreate: () => void
  onCategoryCreated?: (node: CategoryNode) => void
}) {
  const { t } = useTranslation()
  const [accountIban, setAccountIban] = useState(accounts[0]?.iban ?? '')
  const [accountingDate, setAccountingDate] = useState(defaultDate)
  const [amount, setAmount] = useState('')
  const [categoryId, setCategoryId] = useState<number | null>(null)
  const [counterpartyName, setCounterpartyName] = useState('')
  const [purpose, setPurpose] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleConfirm() {
    setError(null)
    const parsedAmount = parseFloat(amount.replace(',', '.'))
    if (!accountIban) {
      setError(t('virtualTransaction.errorNoAccount'))
      return
    }
    if (!accountingDate) {
      setError(t('virtualTransaction.errorNoDate'))
      return
    }
    if (isNaN(parsedAmount) || parsedAmount === 0) {
      setError(t('virtualTransaction.errorNoAmount'))
      return
    }
    setSaving(true)
    try {
      await createVirtualTransaction({
        amount: parsedAmount,
        accountIban,
        accountingDate,
        categoryId,
        counterpartyName: counterpartyName || null,
        purpose: purpose || null,
      })
      onCreate()
    } catch {
      setError(t('virtualTransaction.errorSave'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open onOpenChange={open => { if (!open) onClose() }}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{t('virtualTransaction.title')}</DialogTitle>
        </DialogHeader>

        <div className="flex flex-col gap-3">
          <div className="flex gap-2">
            <DatePicker
              value={parseIso(accountingDate)}
              onChange={d => setAccountingDate(d ? isoDate(d) : '')}
              max={new Date()}
              className="w-36 shrink-0"
            />
            <Input
              type="number"
              step="0.01"
              placeholder={t('virtualTransaction.amount')}
              value={amount}
              onChange={e => setAmount(e.target.value)}
              className="w-28 shrink-0"
              autoFocus
            />
            <select
              value={accountIban}
              onChange={e => setAccountIban(e.target.value)}
              className="flex-1 rounded-lg border border-input bg-input/30 px-3 py-2 text-sm outline-none focus:border-ring focus:ring-2 focus:ring-ring/50"
            >
              {accounts.map(a => (
                <option key={a.iban} value={a.iban}>{a.name}</option>
              ))}
            </select>
          </div>

          <div className="flex gap-2">
            <Input
              type="text"
              placeholder={t('virtualTransaction.counterpartyName')}
              value={counterpartyName}
              onChange={e => setCounterpartyName(e.target.value)}
            />
            <Input
              type="text"
              placeholder={t('virtualTransaction.purpose')}
              value={purpose}
              onChange={e => setPurpose(e.target.value)}
            />
          </div>

          <CategoryPathInput
            value={categoryId}
            onChange={id => setCategoryId(id)}
            tree={categories}
            onCategoryCreated={onCategoryCreated}
            placeholder={t('virtualTransaction.category')}
            className="ri-cat-input w-full"
          />
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}

        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose}>{t('virtualTransaction.cancel')}</Button>
          <Button onClick={handleConfirm} disabled={saving}>
            {saving ? '…' : t('virtualTransaction.confirm')}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
