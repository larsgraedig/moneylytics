import { Fragment, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { fetchLinkedGroups, updateLinkedGroupMeta, type LinkedGroupItem, type TransactionItem } from '../api/transactions'

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

function effectiveAmount(tx: TransactionItem): number {
  return tx.offsetLinks.reduce((acc, link) => {
    const offset = link.partialAmount !== null ? link.partialAmount : Math.abs(link.linkedTransactionAmount)
    const contrib = link.linkedTransactionAmount >= 0 ? offset : -offset
    return acc + contrib
  }, tx.amount)
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

function GroupCard({
  group,
  onMetaChange,
}: {
  group: LinkedGroupItem
  onMetaChange: (groupId: number, name: string | null, comment: string | null) => void
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

  return (
    <div className="ltx-group">
      <div className="ltx-group-header">
        <div className="ltx-group-meta">
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
                </tr>
                {tx.offsetLinks.map(link => {
                  const linkedTx = txById[link.linkedTransactionId]
                  const offsetAmt = link.partialAmount !== null ? link.partialAmount : Math.abs(link.linkedTransactionAmount)
                  const contrib = link.linkedTransactionAmount >= 0 ? offsetAmt : -offsetAmt
                  const contribStr = (contrib >= 0 ? '+' : '') + EUR.format(contrib)
                  return (
                    <tr key={`offset-${link.id}`} className="ltx-offset-row">
                      <td className="ltx-offset-date">{linkedTx ? formatDate(linkedTx.accountingDate) : ''}</td>
                      <td className="ltx-offset-counterparty">
                        ↳ {linkedTx
                          ? (linkedTx.counterpartyName ?? linkedTx.purpose ?? t('linked.transaction'))
                          : t('linked.transaction')}
                      </td>
                      <td />
                      <td>
                        {link.partialAmount !== null && (
                          <span className="ltx-offset-partial">{t('linked.partial')}</span>
                        )}
                      </td>
                      <td className={`ltx-col-amount ${contrib >= 0 ? 'ltx-amount--pos' : 'ltx-amount--neg'}`}>
                        {contribStr}
                      </td>
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

export default function LinkedTransactionsPage() {
  const { t } = useTranslation()
  const [groups, setGroups] = useState<LinkedGroupItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchLinkedGroups()
      .then(res => { setGroups(res.groups); setLoading(false) })
      .catch(e => { setError(e instanceof Error ? e.message : t('common.requestFailed')); setLoading(false) })
  }, [t])

  function handleMetaChange(groupId: number, name: string | null, comment: string | null) {
    setGroups(prev => prev.map(g => g.groupId === groupId ? { ...g, name, comment } : g))
  }

  if (loading) return <div className="ltx-page"><span className="ltx-status">{t('common.loading')}</span></div>
  if (error) return <div className="ltx-page"><span className="ltx-status ltx-status--error">{error}</span></div>

  return (
    <div className="ltx-page">
      <div className="ltx-header">
        <h2 className="ltx-title">{t('linked.title')}</h2>
        <span className="ltx-count">{t('linked.count', { count: groups.length })}</span>
      </div>
      {groups.length === 0
        ? <p className="ltx-status">{t('linked.empty')}</p>
        : groups.map(g => <GroupCard key={g.groupId} group={g} onMetaChange={handleMetaChange} />)
      }
    </div>
  )
}
