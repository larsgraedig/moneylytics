import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { splitTransaction, type SplitItemRequest, type TransactionItem } from '../api/transactions'

interface SplitEntry {
  amount: string
  category: string
  subcategory: string
  comment: string
}

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

export function SplitTransactionModal({
  transaction,
  onClose,
  onSplit,
}: {
  transaction: TransactionItem
  onClose: () => void
  onSplit: () => void
}) {
  const { t } = useTranslation()
  const [entries, setEntries] = useState<SplitEntry[]>([
    { amount: '', category: transaction.category ?? '', subcategory: transaction.subcategory ?? '', comment: '' },
    { amount: '', category: transaction.category ?? '', subcategory: transaction.subcategory ?? '', comment: '' },
  ])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const totalAmount = transaction.amount
  const enteredSum = entries.reduce((acc, e) => {
    const v = parseFloat(e.amount.replace(',', '.'))
    return isNaN(v) ? acc : acc + v
  }, 0)
  const remaining = totalAmount - enteredSum

  function updateEntry(idx: number, field: keyof SplitEntry, value: string) {
    setEntries(prev => prev.map((e, i) => (i === idx ? { ...e, [field]: value } : e)))
  }

  function addEntry() {
    setEntries(prev => [...prev, { amount: '', category: '', subcategory: '', comment: '' }])
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
      category: e.category || null,
      subcategory: e.subcategory || null,
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
    <div
      style={{
        position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
      }}
      onClick={e => { if (e.target === e.currentTarget) onClose() }}
    >
      <div style={{ background: '#1e2028', borderRadius: 10, padding: 24, width: 560, maxWidth: '95vw', maxHeight: '90vh', overflowY: 'auto' }}>
        <h3 style={{ margin: '0 0 12px', color: '#e2e8f0' }}>{t('transactions.split.title')}</h3>
        <p style={{ margin: '0 0 16px', color: '#94a3b8', fontSize: 13 }}>
          {t('common.total')}: <strong style={{ color: '#e2e8f0' }}>{EUR.format(totalAmount)}</strong>
          {' · '}
          {t('transactions.split.remaining')}: <strong style={{ color: Math.abs(remaining) < 0.001 ? '#34d399' : '#f87171' }}>{EUR.format(remaining)}</strong>
        </p>

        {entries.map((entry, idx) => (
          <div key={idx} style={{ background: '#2a2d3a', borderRadius: 8, padding: 12, marginBottom: 10 }}>
            <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
              <input
                type="number"
                step="0.01"
                placeholder={t('transactions.split.amount')}
                value={entry.amount}
                onChange={e => updateEntry(idx, 'amount', e.target.value)}
                style={{ width: 110, padding: '6px 8px', borderRadius: 6, border: '1px solid #3a3d4a', background: '#1e2028', color: '#e2e8f0', fontSize: 13 }}
              />
              <input
                type="text"
                placeholder={t('transactions.split.category')}
                value={entry.category}
                onChange={e => updateEntry(idx, 'category', e.target.value)}
                style={{ flex: 1, padding: '6px 8px', borderRadius: 6, border: '1px solid #3a3d4a', background: '#1e2028', color: '#e2e8f0', fontSize: 13 }}
              />
              <input
                type="text"
                placeholder={t('transactions.split.subcategory')}
                value={entry.subcategory}
                onChange={e => updateEntry(idx, 'subcategory', e.target.value)}
                style={{ flex: 1, padding: '6px 8px', borderRadius: 6, border: '1px solid #3a3d4a', background: '#1e2028', color: '#e2e8f0', fontSize: 13 }}
              />
              {entries.length > 2 && (
                <button
                  onClick={() => removeEntry(idx)}
                  style={{ padding: '4px 8px', borderRadius: 6, border: '1px solid #4a4d5a', background: 'none', color: '#94a3b8', cursor: 'pointer', fontSize: 12 }}
                >
                  {t('transactions.split.removeSplit')}
                </button>
              )}
            </div>
            <input
              type="text"
              placeholder={t('transactions.split.comment')}
              value={entry.comment}
              onChange={e => updateEntry(idx, 'comment', e.target.value)}
              style={{ width: '100%', padding: '6px 8px', borderRadius: 6, border: '1px solid #3a3d4a', background: '#1e2028', color: '#e2e8f0', fontSize: 13, boxSizing: 'border-box' }}
            />
          </div>
        ))}

        <button
          onClick={addEntry}
          style={{ padding: '6px 12px', borderRadius: 6, border: '1px dashed #4a4d5a', background: 'none', color: '#60a5fa', cursor: 'pointer', fontSize: 13, marginBottom: 16 }}
        >
          + {t('transactions.split.addSplit')}
        </button>

        {error && <p style={{ color: '#f87171', fontSize: 13, margin: '0 0 12px' }}>{error}</p>}

        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <button
            onClick={onClose}
            style={{ padding: '8px 16px', borderRadius: 6, border: '1px solid #3a3d4a', background: 'none', color: '#94a3b8', cursor: 'pointer' }}
          >
            {t('transactions.split.cancel')}
          </button>
          <button
            onClick={handleConfirm}
            disabled={saving}
            style={{ padding: '8px 16px', borderRadius: 6, border: 'none', background: '#3b82f6', color: '#fff', cursor: saving ? 'not-allowed' : 'pointer', opacity: saving ? 0.7 : 1 }}
          >
            {saving ? '…' : t('transactions.split.confirm')}
          </button>
        </div>
      </div>
    </div>
  )
}
