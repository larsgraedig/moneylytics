import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { CategoryNode } from '../api/rawImport'
import { createVirtualTransaction, type Account } from '../api/transactions'
import { CategoryPathInput } from './CategoryPathInput'

const inputStyle: React.CSSProperties = {
  flex: 1,
  padding: '6px 8px',
  borderRadius: 6,
  border: '1px solid #3a3d4a',
  background: '#1e2028',
  color: '#e2e8f0',
  fontSize: 13,
  minWidth: 0,
}

export function CreateVirtualTransactionModal({
  accounts,
  categories,
  defaultDate,
  onClose,
  onCreate,
}: {
  accounts: Account[]
  categories: CategoryNode[]
  defaultDate: string
  onClose: () => void
  onCreate: () => void
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
    <div
      style={{
        position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
      }}
      onClick={e => { if (e.target === e.currentTarget) onClose() }}
    >
      <div style={{ background: '#1e2028', borderRadius: 10, padding: 24, width: 480, maxWidth: '95vw' }}>
        <h3 style={{ margin: '0 0 16px', color: '#e2e8f0' }}>{t('virtualTransaction.title')}</h3>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div style={{ display: 'flex', gap: 8 }}>
            <input
              type="date"
              value={accountingDate}
              onChange={e => setAccountingDate(e.target.value)}
              style={{ ...inputStyle, flex: '0 0 auto', width: 140 }}
            />
            <input
              type="number"
              step="0.01"
              placeholder={t('virtualTransaction.amount')}
              value={amount}
              onChange={e => setAmount(e.target.value)}
              style={{ ...inputStyle, flex: '0 0 auto', width: 120 }}
              autoFocus
            />
            <select
              value={accountIban}
              onChange={e => setAccountIban(e.target.value)}
              style={{ ...inputStyle, cursor: 'pointer' }}
            >
              {accounts.map(a => (
                <option key={a.iban} value={a.iban}>{a.name}</option>
              ))}
            </select>
          </div>

          <div style={{ display: 'flex', gap: 8 }}>
            <input
              type="text"
              placeholder={t('virtualTransaction.counterpartyName')}
              value={counterpartyName}
              onChange={e => setCounterpartyName(e.target.value)}
              style={inputStyle}
            />
            <input
              type="text"
              placeholder={t('virtualTransaction.purpose')}
              value={purpose}
              onChange={e => setPurpose(e.target.value)}
              style={inputStyle}
            />
          </div>

          <CategoryPathInput
            value={categoryId}
            onChange={id => setCategoryId(id)}
            tree={categories}
            placeholder={t('virtualTransaction.category')}
            className="ri-cat-input"
          />
        </div>

        {error && <p style={{ color: '#f87171', fontSize: 13, margin: '12px 0 0' }}>{error}</p>}

        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 20 }}>
          <button
            onClick={onClose}
            style={{ padding: '8px 16px', borderRadius: 6, border: '1px solid #3a3d4a', background: 'none', color: '#94a3b8', cursor: 'pointer' }}
          >
            {t('virtualTransaction.cancel')}
          </button>
          <button
            onClick={handleConfirm}
            disabled={saving}
            style={{ padding: '8px 16px', borderRadius: 6, border: 'none', background: '#3b82f6', color: '#fff', cursor: saving ? 'not-allowed' : 'pointer', opacity: saving ? 0.7 : 1 }}
          >
            {saving ? '…' : t('virtualTransaction.confirm')}
          </button>
        </div>
      </div>
    </div>
  )
}
