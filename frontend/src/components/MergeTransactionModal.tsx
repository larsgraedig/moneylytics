import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { mergeTransactions, type TransactionItem } from '../api/transactions'

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
    <div
      style={{
        position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
      }}
      onClick={e => { if (e.target === e.currentTarget) onClose() }}
    >
      <div style={{ background: '#1e2028', borderRadius: 10, padding: 24, width: 500, maxWidth: '95vw' }}>
        <h3 style={{ margin: '0 0 12px', color: '#e2e8f0' }}>{t('transactions.merge.title')}</h3>

        <div style={{ background: '#2a2d3a', borderRadius: 8, padding: 12, marginBottom: 16 }}>
          {transactions.map(tx => (
            <div key={tx.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0', fontSize: 13, color: '#cbd5e1' }}>
              <span>{tx.accountingDate} – {tx.counterpartyName ?? tx.purpose ?? `#${tx.id}`}</span>
              <span style={{ color: tx.amount < 0 ? '#f87171' : '#34d399' }}>{EUR.format(tx.amount)}</span>
            </div>
          ))}
          <div style={{ borderTop: '1px solid #3a3d4a', marginTop: 8, paddingTop: 8, display: 'flex', justifyContent: 'space-between', fontSize: 13, fontWeight: 600 }}>
            <span style={{ color: '#94a3b8' }}>{t('transactions.merge.total')}</span>
            <span style={{ color: total < 0 ? '#f87171' : '#34d399' }}>{EUR.format(total)}</span>
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 16 }}>
          <input
            type="text"
            placeholder={t('transactions.merge.name')}
            value={name}
            onChange={e => setName(e.target.value)}
            style={{ padding: '8px 10px', borderRadius: 6, border: '1px solid #3a3d4a', background: '#2a2d3a', color: '#e2e8f0', fontSize: 13 }}
          />
          <input
            type="text"
            placeholder={t('transactions.merge.comment')}
            value={comment}
            onChange={e => setComment(e.target.value)}
            style={{ padding: '8px 10px', borderRadius: 6, border: '1px solid #3a3d4a', background: '#2a2d3a', color: '#e2e8f0', fontSize: 13 }}
          />
          <div>
            <label style={{ fontSize: 12, color: '#94a3b8', display: 'block', marginBottom: 4 }}>{t('transactions.merge.accountingDate')}</label>
            <input
              type="date"
              value={accountingDate}
              onChange={e => setAccountingDate(e.target.value)}
              style={{ padding: '8px 10px', borderRadius: 6, border: '1px solid #3a3d4a', background: '#2a2d3a', color: '#e2e8f0', fontSize: 13, width: '100%', boxSizing: 'border-box' }}
            />
          </div>
        </div>

        {error && <p style={{ color: '#f87171', fontSize: 13, margin: '0 0 12px' }}>{error}</p>}

        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <button
            onClick={onClose}
            style={{ padding: '8px 16px', borderRadius: 6, border: '1px solid #3a3d4a', background: 'none', color: '#94a3b8', cursor: 'pointer' }}
          >
            {t('transactions.merge.cancel')}
          </button>
          <button
            onClick={handleConfirm}
            disabled={saving}
            style={{ padding: '8px 16px', borderRadius: 6, border: 'none', background: '#10b981', color: '#fff', cursor: saving ? 'not-allowed' : 'pointer', opacity: saving ? 0.7 : 1 }}
          >
            {saving ? '…' : t('transactions.merge.confirm')}
          </button>
        </div>
      </div>
    </div>
  )
}
