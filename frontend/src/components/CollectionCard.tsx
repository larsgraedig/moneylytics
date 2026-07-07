import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { updateCollection, removeTransactionFromCollection, type CollectionDto } from '../api/collections'
import type { TransactionItem } from '../api/transactions'

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

function InlineEdit({
  value,
  placeholder,
  multiline,
  onSave,
}: {
  value: string | null
  placeholder: string
  multiline?: boolean
  onSave: (v: string | null) => void
}) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(value ?? '')
  const ref = useRef<HTMLInputElement & HTMLTextAreaElement>(null)

  useEffect(() => {
    if (editing) ref.current?.focus()
  }, [editing])

  function commit() {
    setEditing(false)
    const trimmed = draft.trim() || null
    if (trimmed !== value) onSave(trimmed)
  }

  if (!editing) {
    return (
      <span
        className={`ltx-inline-edit${value ? '' : ' ltx-inline-edit--empty'}`}
        onClick={() => { setDraft(value ?? ''); setEditing(true) }}
        title={placeholder}
      >
        {value ?? placeholder}
      </span>
    )
  }

  const sharedProps = {
    ref,
    value: draft,
    onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => setDraft(e.target.value),
    onBlur: commit,
    onKeyDown: (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' && !multiline) { e.preventDefault(); commit() }
      if (e.key === 'Escape') { setEditing(false); setDraft(value ?? '') }
    },
    className: 'ltx-inline-input',
  }

  return multiline
    ? <textarea {...sharedProps} rows={2} />
    : <input {...sharedProps} type="text" />
}

export function CollectionCard({
  collection,
  onUpdate,
  onDelete,
  onRemoveTransaction,
  onAddTransaction,
}: {
  collection: CollectionDto
  onUpdate: (id: number, name: string, note: string | null) => void
  onDelete: (id: number) => void
  onRemoveTransaction: (collectionId: number, txId: number) => void
  onAddTransaction: (collection: CollectionDto) => void
}) {
  const { t } = useTranslation()
  const [saving, setSaving] = useState(false)
  const sorted = [...collection.transactions].sort((a, b) =>
    b.accountingDate.localeCompare(a.accountingDate),
  )
  const total = collection.transactions.reduce((sum, tx) => sum + tx.amount, 0)

  function save(name: string | null, note: string | null) {
    if (!name?.trim()) return
    setSaving(true)
    updateCollection(collection.id, name.trim(), note)
      .then(() => { onUpdate(collection.id, name.trim(), note); setSaving(false) })
      .catch(() => setSaving(false))
  }

  function removeTx(tx: TransactionItem) {
    removeTransactionFromCollection(collection.id, tx.id)
      .then(() => onRemoveTransaction(collection.id, tx.id))
      .catch(() => {})
  }

  return (
    <div className="ltx-group">
      <div className="ltx-group-header">
        <div className="ltx-group-meta">
          <span className="ltx-group-id">#{collection.id}</span>
          <InlineEdit
            value={collection.name}
            placeholder={t('collections.namePlaceholderInline')}
            onSave={name => save(name ?? collection.name, collection.note)}
          />
          {saving && <span className="ltx-saving">…</span>}
        </div>
        <div className="col-card-actions">
          <span className={`ltx-group-net ${total < 0 ? 'ltx-group-net--neg' : total > 0 ? 'ltx-group-net--pos' : 'ltx-group-net--zero'}`}>
            {EUR.format(total)}
          </span>
          <button
            className="bdg-icon-btn bdg-icon-btn--danger"
            title={t('common.delete')}
            onClick={() => {
              if (window.confirm(t('collections.deleteConfirm'))) onDelete(collection.id)
            }}
          >
            ␡
          </button>
        </div>
      </div>
      <div className="ltx-group-comment">
        <InlineEdit
          value={collection.note}
          placeholder={t('collections.commentPlaceholder')}
          multiline
          onSave={note => save(collection.name, note)}
        />
      </div>
      <table className="ltx-table">
        <thead>
          <tr>
            <th>{t('transactions.columns.date')}</th>
            <th>{t('transactions.columns.counterpartyName')}</th>
            <th>{t('transactions.columns.purpose')}</th>
            <th>{t('transactions.columns.category')}</th>
            <th className="ltx-col-amount">{t('transactions.columns.amount')}</th>
            <th className="ltx-col-remove" />
          </tr>
        </thead>
        <tbody>
          {sorted.map(tx => (
            <tr key={tx.id} className="ltx-row">
              <td>{formatDate(tx.accountingDate)}</td>
              <td className="ltx-cell-counterparty" title={tx.counterpartyIban ?? undefined}>
                {tx.counterpartyName ?? ''}
              </td>
              <td className="ltx-cell-purpose" title={tx.purpose ?? undefined}>
                <span className="ltx-purpose-text">{tx.purpose ?? ''}</span>
              </td>
              <td>
                {tx.category && (
                  <span className="ltx-cat">
                    {tx.category}{tx.subcategory ? ` / ${tx.subcategory}` : ''}
                  </span>
                )}
              </td>
              <td className={`ltx-col-amount ${tx.amount >= 0 ? 'ltx-amount--pos' : 'ltx-amount--neg'}`}>
                {EUR.format(tx.amount)}
              </td>
              <td className="ltx-col-remove">
                <button
                  className="ltx-remove-btn"
                  onClick={() => removeTx(tx)}
                  title={t('collections.removeTransaction')}
                >
                  ×
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="col-card-footer">
        <button className="bdg-assign-btn" onClick={() => onAddTransaction(collection)}>
          + {t('collections.addTransaction')}
        </button>
      </div>
    </div>
  )
}
