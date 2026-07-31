import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { fetchTransactionList, type TransactionItem } from '../api/transactions'

interface Props {
  nodeKey: string
  from: string
  to: string
  iban?: string
  onClose: () => void
}

type State =
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'ready'; transactions: TransactionItem[]; total: number }

type NodeInfo =
  | { type: 'cat'; category: string }
  | { type: 'grp'; category: string; group: string }
  | { type: 'leaf'; category: string; group: string; subcategory: string }
  | { type: 'sub'; category: string; subcategory: string }

function parseNodeKey(nodeKey: string): NodeInfo {
  if (nodeKey.startsWith('cat:')) {
    return { type: 'cat', category: nodeKey.slice(4) }
  }
  if (nodeKey.startsWith('grp:')) {
    const rest = nodeKey.slice(4)
    const colon = rest.indexOf(':')
    return { type: 'grp', category: rest.slice(0, colon), group: rest.slice(colon + 1) }
  }
  if (nodeKey.startsWith('leaf:')) {
    const rest = nodeKey.slice(5)
    const first = rest.indexOf(':')
    const second = rest.indexOf(':', first + 1)
    return {
      type: 'leaf',
      category: rest.slice(0, first),
      group: rest.slice(first + 1, second),
      subcategory: rest.slice(second + 1),
    }
  }
  // sub: — PiePage format: sub:{category}:{subcategory}
  const rest = nodeKey.slice(4)
  const colon = rest.indexOf(':')
  return { type: 'sub', category: rest.slice(0, colon), subcategory: rest.slice(colon + 1) }
}

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

export default function TransactionListPanel({ nodeKey, from, to, iban, onClose }: Props) {
  const { t } = useTranslation()
  const [state, setState] = useState<State>({ phase: 'loading' })
  const info = parseNodeKey(nodeKey)

  useEffect(() => {
    setState({ phase: 'loading' })
    const category = info.category
    const subcategory =
      info.type === 'grp' || info.type === 'leaf'
        ? info.group
        : info.type === 'sub'
        ? info.subcategory
        : undefined
    const group = info.type === 'leaf' ? info.subcategory : undefined
    fetchTransactionList(from, to, category, subcategory, iban, group)
      .then(r => setState({ phase: 'ready', transactions: r.transactions, total: r.total }))
      .catch(e => setState({ phase: 'error', message: e instanceof Error ? e.message : 'Failed to load' }))
  }, [nodeKey, from, to, iban])

  const showGroupCol = info.type === 'cat'
  const showSubcategoryCol = info.type === 'grp'

  return (
    <>
      <div className="txn-backdrop" onClick={onClose} />
      <div className="txn-panel">
        <div className="txn-panel-header">
          <div className="txn-panel-title">
            {info.type === 'cat' && (
              <span className="txn-panel-name">{info.category}</span>
            )}
            {info.type === 'grp' && (
              <>
                <span className="txn-panel-breadcrumb">{info.category}</span>
                <span className="txn-panel-sep">›</span>
                <span className="txn-panel-name">{info.group}</span>
              </>
            )}
            {info.type === 'leaf' && (
              <>
                <span className="txn-panel-breadcrumb">{info.category}</span>
                <span className="txn-panel-sep">›</span>
                <span className="txn-panel-breadcrumb">{info.group}</span>
                <span className="txn-panel-sep">›</span>
                <span className="txn-panel-name">{info.subcategory}</span>
              </>
            )}
            {info.type === 'sub' && (
              <>
                <span className="txn-panel-breadcrumb">{info.category}</span>
                <span className="txn-panel-sep">›</span>
                <span className="txn-panel-name">{info.subcategory}</span>
              </>
            )}
          </div>
          <button className="txn-panel-close" onClick={onClose} title="Close">✕</button>
        </div>

        <div className="txn-panel-body">
          {state.phase === 'loading' && (
            <p className="txn-hint loading">{t('common.loading')}</p>
          )}

          {state.phase === 'error' && (
            <p className="txn-hint error">{state.message}</p>
          )}

          {state.phase === 'ready' && (
            <>
              <table className="txn-list-table">
                <thead>
                  <tr>
                    <th>{t('transactions.panel.date')}</th>
                    {showGroupCol && <th>{t('transactions.columns.group')}</th>}
                    {showSubcategoryCol && <th>{t('transactions.columns.subcategory')}</th>}
                    <th className="txn-col-amount">{t('transactions.panel.amount')}</th>
                  </tr>
                </thead>
                <tbody>
                  {state.transactions.map((tx, i) => (
                    <tr key={i}>
                      <td className="txn-cell-date">{formatDate(tx.accountingDate)}</td>
                      {showGroupCol && <td className="txn-cell-sub">{tx.subcategory}</td>}
                      {showSubcategoryCol && <td className="txn-cell-sub">{tx.group}</td>}
                      <td className={`txn-cell-amount${tx.amount < 0 ? ' negative' : ''}`}>
                        {EUR.format(tx.amount)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {state.transactions.length === 0 && (
                <p className="txn-hint">{t('transactions.panel.noTransactions')}</p>
              )}

              {state.transactions.length > 0 && (
                <div className="txn-total-row">
                  <span>{t('transactions.panel.total')}</span>
                  <span className={`txn-total-value${state.total < 0 ? ' negative' : ''}`}>
                    {EUR.format(state.total)}
                  </span>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </>
  )
}
