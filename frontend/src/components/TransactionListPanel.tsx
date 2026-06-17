import { useEffect, useState } from 'react'
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

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

function parseNodeKey(nodeKey: string): { isCat: boolean; category: string; subcategory?: string; label: string } {
  if (nodeKey.startsWith('cat:')) {
    const category = nodeKey.slice(4)
    return { isCat: true, category, label: category }
  }
  // sub:Category:Subcategory
  const rest = nodeKey.slice(4)
  const colon = rest.indexOf(':')
  const category = rest.slice(0, colon)
  const subcategory = rest.slice(colon + 1)
  return { isCat: false, category, subcategory, label: subcategory }
}

function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

export default function TransactionListPanel({ nodeKey, from, to, iban, onClose }: Props) {
  const [state, setState] = useState<State>({ phase: 'loading' })
  const { isCat, category, subcategory, label } = parseNodeKey(nodeKey)

  useEffect(() => {
    setState({ phase: 'loading' })
    fetchTransactionList(from, to, category, subcategory, iban)
      .then(r => setState({ phase: 'ready', transactions: r.transactions, total: r.total }))
      .catch(e => setState({ phase: 'error', message: e instanceof Error ? e.message : 'Failed to load' }))
  }, [nodeKey, from, to, iban, category, subcategory])

  return (
    <>
      <div className="txn-backdrop" onClick={onClose} />
      <div className="txn-panel">
        <div className="txn-panel-header">
          <div className="txn-panel-title">
            {isCat ? (
              <span className="txn-panel-name">{label}</span>
            ) : (
              <>
                <span className="txn-panel-breadcrumb">{category}</span>
                <span className="txn-panel-sep">›</span>
                <span className="txn-panel-name">{label}</span>
              </>
            )}
          </div>
          <button className="txn-panel-close" onClick={onClose} title="Close">✕</button>
        </div>

        <div className="txn-panel-body">
          {state.phase === 'loading' && (
            <p className="txn-hint loading">loading…</p>
          )}

          {state.phase === 'error' && (
            <p className="txn-hint error">{state.message}</p>
          )}

          {state.phase === 'ready' && (
            <>
              <table className="txn-list-table">
                <thead>
                  <tr>
                    <th>date</th>
                    {isCat && <th>subcategory</th>}
                    <th className="txn-col-amount">amount</th>
                  </tr>
                </thead>
                <tbody>
                  {state.transactions.map((tx, i) => (
                    <tr key={i}>
                      <td className="txn-cell-date">{formatDate(tx.accountingDate)}</td>
                      {isCat && <td className="txn-cell-sub">{tx.subcategory}</td>}
                      <td className={`txn-cell-amount${tx.amount < 0 ? ' negative' : ''}`}>
                        {EUR.format(tx.amount)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {state.transactions.length === 0 && (
                <p className="txn-hint">no transactions found</p>
              )}

              {state.transactions.length > 0 && (
                <div className="txn-total-row">
                  <span>total</span>
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
