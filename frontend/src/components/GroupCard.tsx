import { Fragment, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { removeTransactionFromGroup, updateLinkedGroupMeta, updateOffsetLinkComment, type LinkedGroupItem, type TransactionItem } from '../api/transactions'

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

function effectiveAmount(tx: TransactionItem): number {
  if (tx.offsetLinks.length === 0) return tx.amount
  if (tx.offsetLinks.some(link => link.committedAmount === tx.amount)) return tx.amount
  return tx.offsetLinks.reduce((acc, link) => acc + link.committedAmount, 0)
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

export function GroupCard({
  group,
  onMetaChange,
  onOffsetCommentChange,
  onRemoveTransaction,
}: {
  group: LinkedGroupItem
  onMetaChange: (groupId: number, name: string | null, comment: string | null) => void
  onOffsetCommentChange: (groupId: number, txId: number, linkId: number, comment: string | null) => void
  onRemoveTransaction: (txId: number) => void
}) {
  const { t } = useTranslation()
  const [saving, setSaving] = useState(false)
  const netSum = group.transactions.reduce((sum, tx) => sum + effectiveAmount(tx), 0)
  const txById = Object.fromEntries(group.transactions.map(tx => [tx.id, tx]))
  const isBalanced = Math.abs(netSum) < 0.005

  function save(name: string | null, comment: string | null) {
    setSaving(true)
    updateLinkedGroupMeta(group.groupId, name, comment)
      .then(() => { onMetaChange(group.groupId, name, comment); setSaving(false) })
      .catch(() => setSaving(false))
  }

  function saveOffsetComment(txId: number, linkId: number, comment: string | null) {
    updateOffsetLinkComment(linkId, comment)
      .then(() => onOffsetCommentChange(group.groupId, txId, linkId, comment))
      .catch(() => {})
  }

  function removeTransaction(txId: number) {
    removeTransactionFromGroup(txId, group.groupId)
      .then(() => onRemoveTransaction(txId))
      .catch(() => {})
  }

  return (
    <div className="ltx-group">
      <div className="ltx-group-header">
        <div className="ltx-group-meta">
          <span className="ltx-group-id">#{group.groupId}</span>
          <InlineEdit
            value={group.name}
            placeholder={t('linked.namePlaceholder')}
            onSave={name => save(name, group.comment)}
          />
          {saving && <span className="ltx-saving">…</span>}
        </div>
        <span className={`ltx-group-net ${isBalanced ? 'ltx-group-net--zero' : netSum > 0 ? 'ltx-group-net--pos' : 'ltx-group-net--neg'}`}>
          {isBalanced ? t('linked.balanced') : EUR.format(netSum)}
        </span>
      </div>
      <div className="ltx-group-comment">
        <InlineEdit
          value={group.comment}
          placeholder={t('linked.commentPlaceholder')}
          multiline
          onSave={comment => save(group.name, comment)}
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
            <th className="ltx-col-amount">{t('transactions.columns.effectiveAmount')}</th>
            <th className="ltx-col-remove" />
          </tr>
        </thead>
        <tbody>
          {group.transactions.map(tx => {
            const eff = effectiveAmount(tx)
            const reduced = Math.abs(eff) < Math.abs(tx.amount) - 0.005
            return (
              <Fragment key={tx.id}>
                <tr className="ltx-row">
                  <td>{formatDate(tx.accountingDate)}</td>
                  <td className="ltx-cell-counterparty" title={tx.counterpartyIban ?? undefined}>
                    {tx.counterpartyName ?? ''}
                  </td>
                  <td className="ltx-cell-purpose" title={tx.purpose ?? undefined}>
                    <span className="ltx-purpose-text">{tx.purpose ?? ''}</span>
                  </td>
                  <td>
                    {tx.category && <span className="ltx-cat">{tx.category}{tx.subcategory ? ` / ${tx.subcategory}` : ''}</span>}
                  </td>
                  <td className={`ltx-col-amount ${tx.amount >= 0 ? 'ltx-amount--pos' : 'ltx-amount--neg'}`}>
                    {EUR.format(tx.amount)}
                  </td>
                  <td className={`ltx-col-amount ${reduced ? 'ltx-amount--reduced' : eff >= 0 ? 'ltx-amount--pos' : 'ltx-amount--neg'}`}>
                    {EUR.format(eff)}
                  </td>
                  <td className="ltx-col-remove">
                    <button
                      className="ltx-remove-btn"
                      onClick={() => removeTransaction(tx.id)}
                      title={t('linked.removeTransaction')}
                    >
                      ×
                    </button>
                  </td>
                </tr>
                {tx.offsetLinks.filter(link => link.committedAmount !== tx.amount).map(link => {
                  const linkedTx = txById[link.linkedTransactionId]
                  const isPositive = link.committedAmount >= 0
                  return (
                    <tr key={`offset-${link.id}`} className="ltx-offset-row">
                      <td className="ltx-offset-date">{linkedTx ? formatDate(linkedTx.accountingDate) : ''}</td>
                      <td className="ltx-offset-counterparty">
                        <span className={isPositive ? 'ltx-offset-arrow ltx-offset-arrow--in' : 'ltx-offset-arrow ltx-offset-arrow--out'}>
                          {isPositive ? '↑' : '↓'}
                        </span>
                        {linkedTx
                          ? (linkedTx.counterpartyName ?? linkedTx.purpose ?? t('linked.transaction'))
                          : t('linked.transaction')}
                      </td>
                      <td className="ltx-cell-purpose">
                        <InlineEdit
                          value={link.comment}
                          placeholder={t('linked.offsetCommentPlaceholder')}
                          onSave={comment => saveOffsetComment(tx.id, link.id, comment)}
                        />
                      </td>
                      <td>
                        {(link.amountA !== null || link.amountB !== null) && (
                          <span className="ltx-offset-partial">{t('linked.partial')}</span>
                        )}
                      </td>
                      <td className={`ltx-col-amount ${isPositive ? 'ltx-amount--pos' : 'ltx-amount--neg'}`}>
                        {EUR.format(link.committedAmount)}
                      </td>
                      <td />
                      <td />
                    </tr>
                  )
                })}
              </Fragment>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
