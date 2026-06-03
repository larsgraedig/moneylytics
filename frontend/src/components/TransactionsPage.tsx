import { useEffect, useMemo, useState } from 'react'
import { fetchCategories, type CategoryGroup } from '../api/rawImport'
import {
  computeEffectiveAmount,
  fetchAccounts,
  fetchAllTransactions,
  linkTransactions,
  unlinkTransaction,
  updateTransactionCategory,
  type Account,
  type OffsetLinkItem,
  type TransactionItem,
} from '../api/transactions'

function isoDate(d: Date) {
  return d.toISOString().slice(0, 10)
}

function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

const today = isoDate(new Date())
const firstOfMonth = isoDate(new Date(new Date().getFullYear(), new Date().getMonth(), 1))
const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

interface RowState {
  original: TransactionItem
  category: string
  subcategory: string
  saving: boolean
  error: string | null
}

type PageState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'ready' }

type LinkingState =
  | { phase: 'selecting'; sourceIndex: number }
  | { phase: 'confirming'; sourceIndex: number; targetIndex: number; partialAmount: string }
  | null

export default function TransactionsPage() {
  const [from, setFrom] = useState(firstOfMonth)
  const [to, setTo] = useState(today)
  const [accounts, setAccounts] = useState<Account[]>([])
  const [selectedIban, setSelectedIban] = useState('')
  const [rows, setRows] = useState<RowState[]>([])
  const [page, setPage] = useState<PageState>({ phase: 'idle' })
  const [categories, setCategories] = useState<CategoryGroup[]>([])
  const [linkingState, setLinkingState] = useState<LinkingState>(null)
  const [linkError, setLinkError] = useState<string | null>(null)

  useEffect(() => {
    fetchAccounts().then(setAccounts).catch(() => {})
    fetchCategories().then(r => setCategories(r.categories)).catch(() => {})
  }, [])

  const accountMap = useMemo(
    () => new Map(accounts.map(a => [a.iban, a.name])),
    [accounts],
  )

  const allCategoryNames = useMemo(() => categories.map(c => c.name), [categories])

  const allSubcategoryNames = useMemo(() => {
    const subs = new Set<string>()
    categories.forEach(c => c.subcategories.forEach(s => subs.add(s)))
    return [...subs].sort()
  }, [categories])

  function subcategoriesFor(category: string): string[] {
    return categories.find(c => c.name === category)?.subcategories ?? []
  }

  async function load() {
    setPage({ phase: 'loading' })
    setLinkingState(null)
    try {
      const data = await fetchAllTransactions(from, to, selectedIban || undefined)
      setRows(
        data.transactions.map(tx => ({
          original: tx,
          category: tx.category,
          subcategory: tx.subcategory,
          saving: false,
          error: null,
        })),
      )
      setPage({ phase: 'ready' })
    } catch (e) {
      setPage({ phase: 'error', message: e instanceof Error ? e.message : 'request failed' })
    }
  }

  function updateRow(index: number, field: 'category' | 'subcategory', value: string) {
    setRows(prev => {
      const next = [...prev]
      next[index] = { ...next[index], [field]: value }
      return next
    })
  }

  async function saveRow(index: number) {
    const row = rows[index]
    setRows(prev => {
      const next = [...prev]
      next[index] = { ...next[index], saving: true, error: null }
      return next
    })
    try {
      const updated = await updateTransactionCategory(row.original.id, row.category, row.subcategory)
      setRows(prev => {
        const next = [...prev]
        next[index] = {
          original: updated,
          category: updated.category,
          subcategory: updated.subcategory,
          saving: false,
          error: null,
        }
        return next
      })
    } catch (e) {
      setRows(prev => {
        const next = [...prev]
        next[index] = {
          ...next[index],
          saving: false,
          error: e instanceof Error ? e.message : 'save failed',
        }
        return next
      })
    }
  }

  async function confirmLink() {
    if (!linkingState || linkingState.phase !== 'confirming') return
    const { sourceIndex, targetIndex, partialAmount } = linkingState
    const sourceRow = rows[sourceIndex]
    const targetRow = rows[targetIndex]
    const parsed = partialAmount !== '' ? parseFloat(partialAmount) : undefined
    setLinkError(null)
    try {
      const result = await linkTransactions(sourceRow.original.id, targetRow.original.id, parsed)
      setRows(prev => {
        const next = [...prev]
        const newForSource: OffsetLinkItem = {
          id: result.id,
          linkedTransactionId: targetRow.original.id,
          linkedTransactionAmount: targetRow.original.amount,
          partialAmount: result.partialAmount,
        }
        const newForTarget: OffsetLinkItem = {
          id: result.id,
          linkedTransactionId: sourceRow.original.id,
          linkedTransactionAmount: sourceRow.original.amount,
          partialAmount: result.partialAmount,
        }
        const srcLinks = [...next[sourceIndex].original.offsetLinks, newForSource]
        const tgtLinks = [...next[targetIndex].original.offsetLinks, newForTarget]
        next[sourceIndex] = {
          ...next[sourceIndex],
          original: {
            ...next[sourceIndex].original,
            offsetLinks: srcLinks,
            effectiveAmount: computeEffectiveAmount(next[sourceIndex].original.amount, srcLinks),
          },
        }
        next[targetIndex] = {
          ...next[targetIndex],
          original: {
            ...next[targetIndex].original,
            offsetLinks: tgtLinks,
            effectiveAmount: computeEffectiveAmount(next[targetIndex].original.amount, tgtLinks),
          },
        }
        return next
      })
      setLinkingState(null)
    } catch (e) {
      setLinkError(e instanceof Error ? e.message : 'link failed')
    }
  }

  async function removeLink(linkId: number) {
    try {
      await unlinkTransaction(linkId)
      setRows(prev =>
        prev.map(row => {
          if (!row.original.offsetLinks.some(l => l.id === linkId)) return row
          const newLinks = row.original.offsetLinks.filter(l => l.id !== linkId)
          return {
            ...row,
            original: {
              ...row.original,
              offsetLinks: newLinks,
              effectiveAmount: computeEffectiveAmount(row.original.amount, newLinks),
            },
          }
        }),
      )
    } catch {
      // silent — the chip stays if the request fails
    }
  }

  const sublistId = (category: string) =>
    `txnv-sub-${category.replace(/\s+/g, '-').toLowerCase()}`

  const uniqueRowCategories = useMemo(() => {
    const seen = new Set<string>()
    rows.forEach(r => seen.add(r.category))
    return [...seen]
  }, [rows])

  function rowClassName(row: RowState, i: number): string {
    const classes: string[] = []
    if (row.category !== row.original.category || row.subcategory !== row.original.subcategory)
      classes.push('txnv-row--dirty')
    if (linkingState?.sourceIndex === i)
      classes.push('txnv-row--linking-source')
    if (linkingState?.phase === 'confirming' && linkingState.targetIndex === i)
      classes.push('txnv-row--linking-target')
    return classes.join(' ')
  }

  function renderOffsetCell(row: RowState, i: number) {
    const src = linkingState?.sourceIndex
    const isSource = src === i
    const isConfirmTarget = linkingState?.phase === 'confirming' && linkingState.targetIndex === i
    const isLinking = linkingState !== null

    if (isSource) {
      return (
        <div className="txnv-linking-from">
          <span className="txnv-linking-badge">selecting target</span>
          <button className="txnv-link-cancel-btn" onClick={() => { setLinkingState(null); setLinkError(null) }}>
            cancel
          </button>
        </div>
      )
    }

    if (isConfirmTarget) {
      return (
        <div className="txnv-linking-confirm">
          <input
            className="txnv-partial-input"
            type="number"
            step="0.01"
            min="0"
            placeholder="partial amt"
            value={(linkingState as { partialAmount: string }).partialAmount}
            onChange={e =>
              setLinkingState(prev =>
                prev?.phase === 'confirming' ? { ...prev, partialAmount: e.target.value } : prev,
              )
            }
            autoFocus
          />
          <button className="txnv-link-confirm-btn" onClick={confirmLink}>link</button>
          <button
            className="txnv-link-back-btn"
            onClick={() => src !== undefined && setLinkingState({ phase: 'selecting', sourceIndex: src })}
          >
            ←
          </button>
          {linkError && <span className="txnv-link-error">{linkError}</span>}
        </div>
      )
    }

    if (isLinking) {
      return (
        <button
          className="txnv-connect-btn"
          onClick={() =>
            src !== undefined &&
            setLinkingState({ phase: 'confirming', sourceIndex: src, targetIndex: i, partialAmount: '' })
          }
        >
          link here
        </button>
      )
    }

    return (
      <div className="txnv-links-normal">
        {row.original.offsetLinks.map(link => (
          <span key={link.id} className="txnv-link-chip">
            <span className={`txnv-link-chip-amount ${link.linkedTransactionAmount >= 0 ? 'positive' : 'negative'}`}>
              {link.partialAmount !== null
                ? EUR.format(link.partialAmount)
                : EUR.format(Math.abs(link.linkedTransactionAmount))}
            </span>
            <button
              className="txnv-link-chip-remove"
              onClick={() => removeLink(link.id)}
              title="remove link"
            >
              ×
            </button>
          </span>
        ))}
        <button
          className="txnv-add-link-btn"
          onClick={() => { setLinkError(null); setLinkingState({ phase: 'selecting', sourceIndex: i }) }}
          title="link to another transaction"
        >
          link
        </button>
      </div>
    )
  }

  return (
    <div className="txnv-page">
      <div className="tr-controls">
        {accounts.length > 0 && (
          <select
            className="account-select"
            value={selectedIban}
            onChange={e => setSelectedIban(e.target.value)}
          >
            <option value="">all accounts</option>
            {accounts.map(a => (
              <option key={a.iban} value={a.iban}>{a.name}</option>
            ))}
          </select>
        )}
        <fieldset className="range-group">
          <label className="range-field">
            <span className="range-label">from</span>
            <input
              type="date"
              value={from}
              max={to}
              onChange={e => setFrom(e.target.value)}
            />
          </label>
          <div className="range-sep" />
          <label className="range-field">
            <span className="range-label">to</span>
            <input
              type="date"
              value={to}
              min={from}
              max={today}
              onChange={e => setTo(e.target.value)}
            />
          </label>
        </fieldset>
        <button
          className="load-btn"
          onClick={load}
          disabled={page.phase === 'loading'}
        >
          {page.phase === 'loading' ? '…' : 'load'}
        </button>
        {page.phase === 'ready' && (
          <span className="txnv-count">{rows.length} transactions</span>
        )}
      </div>

      <datalist id="txnv-cat-list">
        {allCategoryNames.map(c => <option key={c} value={c} />)}
      </datalist>
      <datalist id="txnv-sub-all">
        {allSubcategoryNames.map(s => <option key={s} value={s} />)}
      </datalist>
      {uniqueRowCategories.map(cat => (
        <datalist key={cat} id={sublistId(cat)}>
          {subcategoriesFor(cat).map(s => <option key={s} value={s} />)}
        </datalist>
      ))}

      <div className="txnv-body">
        {page.phase === 'idle' && (
          <p className="hint">select a date range and press <kbd>load</kbd></p>
        )}
        {page.phase === 'loading' && (
          <p className="hint loading">fetching…</p>
        )}
        {page.phase === 'error' && (
          <p className="hint error">{(page as { phase: 'error'; message: string }).message}</p>
        )}
        {page.phase === 'ready' && rows.length === 0 && (
          <p className="hint">no transactions in this range</p>
        )}
        {page.phase === 'ready' && rows.length > 0 && (
          <table className="txnv-table">
            <thead>
              <tr>
                <th>date</th>
                <th>account</th>
                <th className="txnv-col-amount">amount</th>
                <th>category</th>
                <th>subcategory</th>
                <th>offsets</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row, i) => {
                const effectiveDiffers = row.original.effectiveAmount !== row.original.amount
                return (
                  <tr key={row.original.id} className={rowClassName(row, i)}>
                    <td className="txn-cell-date">{formatDate(row.original.bookingDate)}</td>
                    <td className="txnv-cell-account">
                      {accountMap.get(row.original.accountIban) ?? row.original.accountIban}
                    </td>
                    <td className={`txn-cell-amount txnv-col-amount${row.original.amount < 0 ? ' negative' : ' positive'}`}>
                      <span className={effectiveDiffers ? 'txnv-amount-crossed' : ''}>
                        {EUR.format(row.original.amount)}
                      </span>
                      {effectiveDiffers && (
                        <span className="txnv-effective-amount">
                          {EUR.format(row.original.effectiveAmount)}
                        </span>
                      )}
                    </td>
                    <td>
                      <input
                        className="ri-cat-input"
                        value={row.category}
                        list="txnv-cat-list"
                        onChange={e => updateRow(i, 'category', e.target.value)}
                      />
                    </td>
                    <td>
                      <input
                        className="ri-cat-input"
                        value={row.subcategory}
                        list={sublistId(row.category)}
                        onChange={e => updateRow(i, 'subcategory', e.target.value)}
                      />
                    </td>
                    <td className="txnv-cell-offsets">
                      {renderOffsetCell(row, i)}
                    </td>
                    <td className="txnv-cell-actions">
                      {row.error && (
                        <span className="txnv-row-error">{row.error}</span>
                      )}
                      {(row.category !== row.original.category || row.subcategory !== row.original.subcategory) && (
                        <button
                          className="txnv-save-btn"
                          onClick={() => saveRow(i)}
                          disabled={row.saving}
                        >
                          {row.saving ? '…' : 'save'}
                        </button>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
