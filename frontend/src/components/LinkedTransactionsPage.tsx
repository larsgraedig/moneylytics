import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { fetchLinkedGroups, type LinkedGroupItem, type TransactionItem } from '../api/transactions'

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

function GroupCard({ group, index }: { group: LinkedGroupItem; index: number }) {
  const { t } = useTranslation()
  const netSum = group.transactions.reduce((sum, tx) => sum + effectiveAmount(tx), 0)
  const isBalanced = Math.abs(netSum) < 0.005

  return (
    <div className="ltx-group">
      <div className="ltx-group-header">
        <span className="ltx-group-label">{t('linked.group')} {index + 1}</span>
        <span className={`ltx-group-net ${isBalanced ? 'ltx-group-net--zero' : netSum > 0 ? 'ltx-group-net--pos' : 'ltx-group-net--neg'}`}>
          {isBalanced ? t('linked.balanced') : EUR.format(netSum)}
        </span>
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
              <tr key={tx.id} className="ltx-row">
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
        : groups.map((g, i) => <GroupCard key={i} group={g} index={i} />)
      }
    </div>
  )
}
