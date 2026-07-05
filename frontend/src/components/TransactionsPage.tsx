import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { Trans, useTranslation } from 'react-i18next'
import { fetchCategories, type CategoryGroup } from '../api/rawImport'
import {
  AllocationExceededError,
  computeEffectiveAmount,
  fetchAllTransactions,
  fetchLinkedGroup,
  linkTransactions,
  updateTransactionAccountingDate,
  updateTransactionCategory,
  updateTransactionComment,
  type Account,
  type AllocationError,
  type GroupSummary,
  type LinkedGroupItem,
  type OffsetLinkItem,
  type TransactionItem,
} from '../api/transactions'
import { GroupCard } from './GroupCard'
import {
  assignTransaction as assignToBudget,
  fetchBudgets,
  removeTransactionLink as removeBudgetLink,
  type Budget,
} from '../api/budgets'
import { updateUserSettings } from '../api/settings'

const LINK_COLORS = ['#f59e0b', '#10b981', '#60a5fa', '#f472b6', '#a78bfa', '#fb923c']
const BUDGET_COLORS = ['#34d399', '#818cf8', '#fb7185', '#fbbf24', '#38bdf8', '#a3e635']

const DEFAULT_COLUMN_ORDER = ['date', 'account', 'amount', 'category', 'group', 'subcategory', 'offsets', 'budget', 'counterparty', 'purpose', 'comment'] as const
type ColumnKey = typeof DEFAULT_COLUMN_ORDER[number]

function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

interface BudgetAssignment {
  linkId: number
  budgetId: number
  budgetName: string
  amount: number | null
}

interface RowState {
  original: TransactionItem
  category: string
  categoryGroup: string
  subcategory: string
  comment: string
  accountingDate: string
  selected: boolean
  saving: boolean
  savingComment: boolean
  savingAccountingDate: boolean
  error: string | null
  budgetAssignments: BudgetAssignment[]
  addingBudget: { budgetId: string; amount: string } | null
}

type PageState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'ready' }

type LinkingState =
  | { phase: 'selecting'; sourceIndex: number }
  | { phase: 'confirming'; sourceIndex: number; targetIndex: number; myAmount: string; otherAmount: string }
  | { phase: 'group-select'; sourceIndex: number; targetIndex: number; myAmount: string; otherAmount: string; availableGroups: GroupSummary[] }
  | null

export default function TransactionsPage({
  from,
  to,
  iban,
  accounts,
  columnOrder,
  onColumnOrderChange,
}: {
  from: string
  to: string
  iban?: string
  accounts: Account[]
  columnOrder?: string[]
  onColumnOrderChange?: (order: string[]) => void
}) {
  const { t } = useTranslation()
  const location = useLocation()
  const [rows, setRows] = useState<RowState[]>([])
  const [page, setPage] = useState<PageState>({ phase: 'idle' })
  const [categories, setCategories] = useState<CategoryGroup[]>([])
  const [budgets, setBudgets] = useState<Budget[]>([])
  const [linkingState, setLinkingState] = useState<LinkingState>(null)
  const [linkError, setLinkError] = useState<string | AllocationError | null>(null)
  const [groupModal, setGroupModal] = useState<{ groupId: number; group: LinkedGroupItem | null } | null>(null)
  const [highlightedId] = useState<number | null>(null)
  const [filterCategory, setFilterCategory] = useState('')
  const [filterCategoryGroup, setFilterCategoryGroup] = useState('')
  const [filterSubcategory, setFilterSubcategory] = useState('')
  const [filterUncategorized, setFilterUncategorized] = useState(false)
  const [bulkCategory, setBulkCategory] = useState('')
  const [bulkCategoryGroup, setBulkCategoryGroup] = useState('')
  const [bulkSubcategory, setBulkSubcategory] = useState('')
  const [bulkApplying, setBulkApplying] = useState(false)
  const [dragCol, setDragCol] = useState<ColumnKey | null>(null)
  const [dragOverCol, setDragOverCol] = useState<ColumnKey | null>(null)

  useEffect(() => {
    fetchCategories().then(r => setCategories(r.categories)).catch(() => {})
    fetchBudgets().then(setBudgets).catch(() => {})
  }, [])

  const accountMap = useMemo(
    () => new Map(accounts.map(a => [a.iban, a.name])),
    [accounts],
  )

  const txBudgetMap = useMemo(() => {
    const map = new Map<number, BudgetAssignment[]>()
    budgets.forEach(b => {
      b.transactionLinks.forEach(link => {
        const existing = map.get(link.transactionId) ?? []
        map.set(link.transactionId, [
          ...existing,
          { linkId: link.id, budgetId: b.id, budgetName: b.name, amount: link.amount },
        ])
      })
    })
    return map
  }, [budgets])

  const allCategoryNames = useMemo(() => categories.map(c => c.name), [categories])

  const allSubcategoryNames = useMemo(() => {
    const subs = new Set<string>()
    categories.forEach(c => c.subcategories.forEach(s => subs.add(s)))
    return [...subs].sort()
  }, [categories])

  const colOrder = useMemo<ColumnKey[]>(() => {
    const order = (columnOrder ?? DEFAULT_COLUMN_ORDER) as ColumnKey[]
    return [
      ...DEFAULT_COLUMN_ORDER.filter(c => order.includes(c as ColumnKey)).sort((a, b) => order.indexOf(a) - order.indexOf(b)),
      ...DEFAULT_COLUMN_ORDER.filter(c => !order.includes(c as ColumnKey)),
    ]
  }, [columnOrder])

  function saveColumnOrder(order: ColumnKey[]) {
    updateUserSettings({ transactionsColumnOrder: order }).catch(() => {})
    onColumnOrderChange?.(order)
  }

  function handleDrop(targetCol: ColumnKey) {
    if (!dragCol || dragCol === targetCol) {
      setDragCol(null)
      setDragOverCol(null)
      return
    }
    const newOrder = [...colOrder]
    const from = newOrder.indexOf(dragCol)
    const to = newOrder.indexOf(targetCol)
    newOrder.splice(from, 1)
    newOrder.splice(to, 0, dragCol)
    setDragCol(null)
    setDragOverCol(null)
    saveColumnOrder(newOrder)
  }

  function subcategoriesFor(category: string): string[] {
    const cat = categories.find(c => c.name === category)
    if (!cat) return []
    const fromGroups = cat.groups.flatMap(g => g.subcategories)
    return [...cat.subcategories, ...fromGroups].sort()
  }

  function groupsFor(category: string): string[] {
    return categories.find(c => c.name === category)?.groups.map(g => g.name) ?? []
  }

  async function doLoad(category?: string, subcategory?: string, uncategorized?: boolean, categoryGroup?: string) {
    setPage({ phase: 'loading' })
    setLinkingState(null)
    try {
      const data = await fetchAllTransactions(from, to, iban, category, subcategory, uncategorized, categoryGroup)
      setRows(
        data.transactions.map(tx => ({
          original: tx,
          category: tx.category,
          categoryGroup: tx.categoryGroup ?? '',
          subcategory: tx.subcategory,
          comment: tx.comment ?? '',
          accountingDate: tx.accountingDate,
          selected: false,
          saving: false,
          savingComment: false,
          savingAccountingDate: false,
          error: null,
          budgetAssignments: txBudgetMap.get(tx.id) ?? [],
          addingBudget: null,
        })),
      )
      setPage({ phase: 'ready' })
    } catch (e) {
      setPage({ phase: 'error', message: e instanceof Error ? e.message : t('common.requestFailed') })
    }
  }

  function load() {
    setFilterCategory('')
    setFilterCategoryGroup('')
    setFilterSubcategory('')
    setFilterUncategorized(false)
    doLoad()
  }

  function updateRow(index: number, field: 'category' | 'categoryGroup' | 'subcategory' | 'comment' | 'accountingDate', value: string) {
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
      const updated = await updateTransactionCategory(row.original.id, row.category, row.subcategory, row.categoryGroup || null)
      setRows(prev => {
        const next = [...prev]
        next[index] = {
          ...next[index],
          original: updated,
          category: updated.category,
          categoryGroup: updated.categoryGroup ?? '',
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

  async function saveAccountingDate(index: number) {
    const row = rows[index]
    if (!row.accountingDate || row.accountingDate === row.original.accountingDate) return
    setRows(prev => {
      const next = [...prev]
      next[index] = { ...next[index], savingAccountingDate: true }
      return next
    })
    try {
      const updated = await updateTransactionAccountingDate(row.original.id, row.accountingDate)
      setRows(prev => {
        const next = [...prev]
        next[index] = {
          ...next[index],
          original: updated,
          accountingDate: updated.accountingDate,
          savingAccountingDate: false,
        }
        return next
      })
    } catch {
      setRows(prev => {
        const next = [...prev]
        next[index] = { ...next[index], accountingDate: next[index].original.accountingDate, savingAccountingDate: false }
        return next
      })
    }
  }

  async function saveComment(index: number) {
    const row = rows[index]
    const newComment = row.comment.trim() || null
    if (newComment === (row.original.comment ?? null)) return
    setRows(prev => {
      const next = [...prev]
      next[index] = { ...next[index], savingComment: true }
      return next
    })
    try {
      const updated = await updateTransactionComment(row.original.id, newComment)
      setRows(prev => {
        const next = [...prev]
        next[index] = {
          ...next[index],
          original: updated,
          comment: updated.comment ?? '',
          savingComment: false,
        }
        return next
      })
    } catch {
      setRows(prev => {
        const next = [...prev]
        next[index] = { ...next[index], savingComment: false }
        return next
      })
    }
  }

  function resolveCommitted(myAmount: number | null, txAmount: number): number {
    return myAmount !== null ? myAmount : txAmount
  }

  function collectAvailableGroups(sourceIndex: number, targetIndex: number): GroupSummary[] {
    const seen = new Set<number>()
    const groups: GroupSummary[] = []
    for (const g of [...rows[sourceIndex].original.groups, ...rows[targetIndex].original.groups]) {
      if (!seen.has(g.id)) { seen.add(g.id); groups.push(g) }
    }
    return groups
  }

  async function confirmLink() {
    if (!linkingState || linkingState.phase !== 'confirming') return
    const { sourceIndex, targetIndex, myAmount, otherAmount } = linkingState
    const availableGroups = collectAvailableGroups(sourceIndex, targetIndex)
    if (availableGroups.length > 0) {
      setLinkingState({ phase: 'group-select', sourceIndex, targetIndex, myAmount, otherAmount, availableGroups })
      return
    }
    await doCreateLink(sourceIndex, targetIndex, myAmount, otherAmount)
  }

  async function confirmLinkWithGroup(targetGroupId?: number) {
    if (!linkingState || linkingState.phase !== 'group-select') return
    const { sourceIndex, targetIndex, myAmount, otherAmount } = linkingState
    await doCreateLink(sourceIndex, targetIndex, myAmount, otherAmount, targetGroupId, targetGroupId === undefined)
  }

  async function doCreateLink(
    sourceIndex: number,
    targetIndex: number,
    myAmount: string,
    otherAmount: string,
    targetGroupId?: number,
    forceNewGroup?: boolean,
  ) {
    const sourceRow = rows[sourceIndex]
    const targetRow = rows[targetIndex]
    const parsedMy = myAmount !== '' ? parseFloat(myAmount) : undefined
    const parsedOther = otherAmount !== '' ? parseFloat(otherAmount) : undefined
    setLinkError(null)
    try {
      const result = await linkTransactions(
        sourceRow.original.id, targetRow.original.id, parsedMy, parsedOther, targetGroupId, forceNewGroup,
      )
      const sourceIsA = sourceRow.original.id < targetRow.original.id
      const sourceCommitted = resolveCommitted(
        sourceIsA ? result.amountA : result.amountB,
        sourceRow.original.amount,
      )
      const targetCommitted = resolveCommitted(
        sourceIsA ? result.amountB : result.amountA,
        targetRow.original.amount,
      )
      setRows(prev => {
        const next = [...prev]
        if (result.id !== null) {
          const newForSource: OffsetLinkItem = {
            id: result.id,
            linkedTransactionId: targetRow.original.id,
            linkedTransactionAmount: targetRow.original.amount,
            amountA: result.amountA,
            amountB: result.amountB,
            committedAmount: sourceCommitted,
            comment: null,
            groupId: result.groupId,
          }
          const newForTarget: OffsetLinkItem = {
            id: result.id,
            linkedTransactionId: sourceRow.original.id,
            linkedTransactionAmount: sourceRow.original.amount,
            amountA: result.amountA,
            amountB: result.amountB,
            committedAmount: targetCommitted,
            comment: null,
            groupId: result.groupId,
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
        }
        return next
      })
      setLinkingState(null)
    } catch (e) {
      if (e instanceof AllocationExceededError) {
        setLinkError(e.data)
      } else {
        setLinkError(e instanceof Error ? e.message : 'link failed')
      }
    }
  }


  async function confirmBudgetAssign(rowIndex: number) {
    const row = rows[rowIndex]
    const adding = row.addingBudget
    if (!adding || !adding.budgetId) return
    const budgetId = Number(adding.budgetId)
    const amount = adding.amount !== '' ? parseFloat(adding.amount) || null : null
    try {
      const link = await assignToBudget(budgetId, row.original.id, amount)
      const budget = budgets.find(b => b.id === budgetId)
      if (!budget) return
      const newAssignment: BudgetAssignment = {
        linkId: link.id,
        budgetId,
        budgetName: budget.name,
        amount: link.amount,
      }
      setBudgets(prev =>
        prev.map(b =>
          b.id === budgetId
            ? { ...b, transactionLinks: [...b.transactionLinks, link], balance: b.balance + (link.amount ?? row.original.amount) }
            : b,
        ),
      )
      setRows(prev => {
        const next = [...prev]
        next[rowIndex] = {
          ...next[rowIndex],
          budgetAssignments: [...next[rowIndex].budgetAssignments, newAssignment],
          addingBudget: null,
        }
        return next
      })
    } catch {
      // silent — user can retry
    }
  }

  async function removeBudgetAssign(rowIndex: number, linkId: number, budgetId: number) {
    try {
      await removeBudgetLink(linkId)
      setBudgets(prev =>
        prev.map(b =>
          b.id === budgetId
            ? { ...b, transactionLinks: b.transactionLinks.filter(l => l.id !== linkId) }
            : b,
        ),
      )
      setRows(prev => {
        const next = [...prev]
        next[rowIndex] = {
          ...next[rowIndex],
          budgetAssignments: next[rowIndex].budgetAssignments.filter(a => a.linkId !== linkId),
        }
        return next
      })
    } catch {
      // silent
    }
  }

  const selectedCount = rows.filter(r => r.selected).length
  const allSelected = rows.length > 0 && selectedCount === rows.length

  function toggleSelect(index: number) {
    setRows(prev => {
      const next = [...prev]
      next[index] = { ...next[index], selected: !next[index].selected }
      return next
    })
  }

  function toggleSelectAll() {
    const next = !allSelected
    setRows(prev => prev.map(r => ({ ...r, selected: next })))
  }

  function clearSelection() {
    setRows(prev => prev.map(r => ({ ...r, selected: false })))
    setBulkCategory('')
    setBulkCategoryGroup('')
    setBulkSubcategory('')
  }

  async function applyBulk() {
    if (!bulkCategory.trim() || selectedCount === 0) return
    setBulkApplying(true)
    const cat = bulkCategory.trim()
    const grp = bulkCategoryGroup.trim() || null
    const sub = bulkSubcategory.trim()
    const indices = rows.map((r, i) => ({ r, i })).filter(({ r }) => r.selected).map(({ i }) => i)
    const results = await Promise.allSettled(
      indices.map(i => updateTransactionCategory(rows[i].original.id, cat, sub, grp)),
    )
    setRows(prev => {
      const next = [...prev]
      results.forEach((result, idx) => {
        const i = indices[idx]
        if (result.status === 'fulfilled') {
          const updated = result.value
          next[i] = {
            ...next[i],
            original: updated,
            category: updated.category,
            categoryGroup: updated.categoryGroup ?? '',
            subcategory: updated.subcategory,
            selected: false,
          }
        }
      })
      return next
    })
    setBulkApplying(false)
    setBulkCategory('')
    setBulkCategoryGroup('')
    setBulkSubcategory('')
  }

  const sublistId = (category: string) =>
    `txnv-sub-${category.replace(/\s+/g, '-').toLowerCase()}`

  const uniqueRowCategories = useMemo(() => {
    const seen = new Set<string>()
    rows.forEach(r => { if (r.category) seen.add(r.category) })
    return [...seen]
  }, [rows])

  const groupColorMap = useMemo(() => {
    const map = new Map<number, number>()
    let colorIdx = 0
    const seen = new Set<number>()
    for (const row of rows) {
      for (const group of row.original.groups) {
        if (seen.has(group.id)) continue
        seen.add(group.id)
        map.set(group.id, colorIdx % LINK_COLORS.length)
        colorIdx++
      }
    }
    return map
  }, [rows])

  function rowClassName(row: RowState, i: number): string {
    const classes: string[] = []
    const commentDirty = row.comment.trim() !== (row.original.comment ?? '')
    const accountingDateDirty = row.accountingDate !== row.original.accountingDate
    const groupDirty = row.categoryGroup !== (row.original.categoryGroup ?? '')
    if (row.category !== row.original.category || groupDirty || row.subcategory !== row.original.subcategory || commentDirty || accountingDateDirty)
      classes.push('txnv-row--dirty')
    if (linkingState?.sourceIndex === i)
      classes.push('txnv-row--linking-source')
    if (linkingState?.phase === 'confirming' && linkingState.targetIndex === i)
      classes.push('txnv-row--linking-target')
    if (highlightedId === row.original.id)
      classes.push('txnv-row--highlighted')
    if (row.selected)
      classes.push('txnv-row--selected')
    return classes.join(' ')
  }

  function renderOffsetCell(row: RowState, i: number) {
    const src = linkingState?.sourceIndex
    const isSource = src === i
    const isSelecting = linkingState?.phase === 'selecting'

    if (isSource) {
      return (
        <div className="txnv-linking-from">
          <span className="txnv-linking-badge">{t('transactions.linkingSelectingTarget')}</span>
          <button className="txnv-link-cancel-btn" onClick={() => { setLinkingState(null); setLinkError(null) }}>
            {t('common.cancel')}
          </button>
        </div>
      )
    }

    if (isSelecting) {
      return (
        <button
          className="txnv-connect-btn"
          onClick={() =>
            src !== undefined &&
            setLinkingState({ phase: 'confirming', sourceIndex: src, targetIndex: i, myAmount: '', otherAmount: '' })
          }
        >
          {t('transactions.linkHere')}
        </button>
      )
    }

    return (
      <div className="txnv-links-normal">
        {row.original.groups.map(group => {
          const colorIdx = groupColorMap.get(group.id)
          const chipColor = colorIdx !== undefined ? LINK_COLORS[colorIdx] : undefined
          const chipLabel = group.name ?? `#${group.id}`
          return (
            <span
              key={group.id}
              className="txnv-link-chip txnv-group-chip"
              style={chipColor ? { borderColor: chipColor } : undefined}
              onClick={() => {
                setGroupModal({ groupId: group.id, group: null })
                fetchLinkedGroup(group.id).then(g => setGroupModal({ groupId: group.id, group: g }))
              }}
              title={`#${group.id}`}
            >
              {chipLabel}
            </span>
          )
        })}
        <button
          className="txnv-add-link-btn"
          onClick={() => { setLinkError(null); setLinkingState({ phase: 'selecting', sourceIndex: i }) }}
          title={t('transactions.linkToTransaction')}
        >
          link
        </button>
      </div>
    )
  }

  function renderGroupModal() {
    if (!groupModal) return null
    const { group } = groupModal
    const close = () => setGroupModal(null)

    function handleMetaChange(_groupId: number, name: string | null, comment: string | null) {
      setGroupModal(prev => prev && prev.group ? { ...prev, group: { ...prev.group, name, comment } } : prev)
    }

    function handleOffsetCommentChange(_groupId: number, txId: number, linkId: number, comment: string | null) {
      setGroupModal(prev => {
        if (!prev?.group) return prev
        return {
          ...prev,
          group: {
            ...prev.group,
            transactions: prev.group.transactions.map(tx =>
              tx.id !== txId ? tx : {
                ...tx,
                offsetLinks: tx.offsetLinks.map(l => l.id === linkId ? { ...l, comment } : l),
              },
            ),
          },
        }
      })
    }

    const deepLinkSearch = new URLSearchParams(location.search)
    deepLinkSearch.set('group', String(groupModal.groupId))

    return (
      <div className="txnv-lm-backdrop" onClick={close}>
        <div className="txnv-lm-modal txnv-lm-modal--group" onClick={e => e.stopPropagation()}>
          <div className="txnv-lm-header">
            <Link
              className="txnv-lm-title"
              to={{ pathname: '/verknuepfungen', search: deepLinkSearch.toString() }}
              onClick={close}
            >
              {t('linked.group')} #{groupModal.groupId} ↗
            </Link>
            <button className="txnv-lm-close" onClick={close}>×</button>
          </div>
          <div className="txnv-lm-group-body">
            {!group
              ? <span className="txnv-lm-loading">{t('common.loading')}</span>
              : (
                <GroupCard
                  group={group}
                  onMetaChange={handleMetaChange}
                  onOffsetCommentChange={handleOffsetCommentChange}
                  onRemoveTransaction={txId => {
                    const remaining = group.transactions.filter(tx => tx.id !== txId)
                    if (remaining.length >= 2) {
                      setGroupModal(prev => prev ? { ...prev, group: { ...group, transactions: remaining } } : null)
                    } else {
                      setGroupModal(null)
                    }
                  }}
                />
              )
            }
          </div>
        </div>
      </div>
    )
  }

  function renderLinkModal() {
    if (linkingState?.phase !== 'confirming' && linkingState?.phase !== 'group-select') return null

    const sourceRow = rows[linkingState.sourceIndex]
    const targetRow = rows[linkingState.targetIndex]

    function txCard(tx: TransactionItem) {
      return (
        <div className="txnv-lm-tx-card">
          <span className="txnv-lm-tx-date">{formatDate(tx.accountingDate)}</span>
          <span className="txnv-lm-tx-name">{tx.counterpartyName ?? tx.purpose ?? '—'}</span>
          <span className={`txnv-lm-tx-amount ${tx.amount >= 0 ? 'positive' : 'negative'}`}>
            {EUR.format(tx.amount)}
          </span>
        </div>
      )
    }

    const cancel = () => { setLinkingState(null); setLinkError(null) }

    return (
      <div className="txnv-lm-backdrop" onClick={cancel}>
        <div className="txnv-lm-modal" onClick={e => e.stopPropagation()}>
          <div className="txnv-lm-header">
            <span className="txnv-lm-title">{t('transactions.linkModal.title')}</span>
            <button className="txnv-lm-close" onClick={cancel}>×</button>
          </div>

          <div className="txnv-lm-body">
            {txCard(sourceRow.original)}
            <div className="txnv-lm-divider">⇅</div>
            {txCard(targetRow.original)}

            {linkingState.phase === 'confirming' && (
              <>
                <div className="txnv-lm-amounts">
                  <div className="txnv-lm-amount-row">
                    <label className="txnv-lm-amount-label">
                      {sourceRow.original.counterpartyName ?? EUR.format(sourceRow.original.amount)}
                    </label>
                    <input
                      className="txnv-partial-input"
                      type="number"
                      step="0.01"
                      min="0"
                      placeholder={t('transactions.partialAmount')}
                      value={linkingState.myAmount}
                      autoFocus
                      onChange={e =>
                        setLinkingState(prev =>
                          prev?.phase === 'confirming' ? { ...prev, myAmount: e.target.value } : prev,
                        )
                      }
                    />
                  </div>
                  <div className="txnv-lm-amount-row">
                    <label className="txnv-lm-amount-label">
                      {targetRow.original.counterpartyName ?? EUR.format(targetRow.original.amount)}
                    </label>
                    <input
                      className="txnv-partial-input"
                      type="number"
                      step="0.01"
                      min="0"
                      placeholder={t('transactions.partialAmount')}
                      value={linkingState.otherAmount}
                      onChange={e =>
                        setLinkingState(prev =>
                          prev?.phase === 'confirming' ? { ...prev, otherAmount: e.target.value } : prev,
                        )
                      }
                    />
                  </div>
                </div>
                {linkError && (
                  typeof linkError === 'string'
                    ? <span className="txnv-link-error">{linkError}</span>
                    : (() => {
                      const txLookup = new Map(rows.map(r => [r.original.id, r.original]))
                      const overTx = txLookup.get(linkError.transactionId)
                      const alreadyCommitted = linkError.existingLinks.reduce((s, l) => s + Math.abs(l.committedAmount), 0)
                      const totalAbs = alreadyCommitted + linkError.maxRemainingAmount
                      return (
                        <div className="txnv-alloc-breakdown">
                          <div className="txnv-alloc-breakdown-header">
                            ⚠ {t('transactions.allocationTitle')}
                          </div>
                          <div className="txnv-alloc-breakdown-row txnv-alloc-breakdown-row--total">
                            <span>{overTx?.counterpartyName ?? `#${linkError.transactionId}`}</span>
                            <span>{EUR.format(totalAbs)}</span>
                          </div>
                          {linkError.existingLinks.map(l => {
                            const linkedTx = txLookup.get(l.linkedTransactionId)
                            return (
                              <div key={l.linkId} className="txnv-alloc-breakdown-row txnv-alloc-breakdown-row--link">
                                <span>↳ {linkedTx?.counterpartyName ?? `#${l.linkedTransactionId}`}</span>
                                <span>{EUR.format(Math.abs(l.committedAmount))}</span>
                              </div>
                            )
                          })}
                          <div className="txnv-alloc-breakdown-row txnv-alloc-breakdown-row--remaining">
                            <span>{t('transactions.allocationRemaining')}</span>
                            <span>{EUR.format(linkError.maxRemainingAmount)}</span>
                          </div>
                        </div>
                      )
                    })()
                )}
                <div className="txnv-lm-footer">
                  <button className="txnv-link-confirm-btn" onClick={confirmLink}>{t('transactions.link')}</button>
                  <button className="txnv-link-cancel-btn" onClick={cancel}>{t('common.cancel')}</button>
                </div>
              </>
            )}

            {linkingState.phase === 'group-select' && (
              <>
                <div className="txnv-lm-group-section">
                  <span className="txnv-group-select-label">{t('transactions.selectGroup')}</span>
                  <div className="txnv-group-select-options">
                    {linkingState.availableGroups.map(g => (
                      <button key={g.id} className="txnv-group-option-btn" onClick={() => confirmLinkWithGroup(g.id)}>
                        {g.name ?? `#${g.id}`}
                      </button>
                    ))}
                    <button className="txnv-group-option-btn txnv-group-option-btn--new" onClick={() => confirmLinkWithGroup(undefined)}>
                      {t('transactions.newGroup')}
                    </button>
                  </div>
                </div>
                <div className="txnv-lm-footer">
                  <button className="txnv-link-back-btn" onClick={() => {
                    if (linkingState.phase === 'group-select') {
                      setLinkingState({ phase: 'confirming', sourceIndex: linkingState.sourceIndex, targetIndex: linkingState.targetIndex, myAmount: linkingState.myAmount, otherAmount: linkingState.otherAmount })
                    }
                  }}>← {t('common.back')}</button>
                  <button className="txnv-link-cancel-btn" onClick={cancel}>{t('common.cancel')}</button>
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    )
  }

  function renderBudgetCell(row: RowState, i: number) {
    const availableBudgets = budgets.filter(
      b => !row.budgetAssignments.some(a => a.budgetId === b.id),
    )
    const adding = row.addingBudget

    return (
      <div className="txnv-budget-cell">
        {row.budgetAssignments.map((a, bi) => (
          <span
            key={a.linkId}
            className="txnv-budget-chip"
            style={{ borderColor: BUDGET_COLORS[bi % BUDGET_COLORS.length] }}
          >
            <span className="txnv-budget-chip-name">{a.budgetName}</span>
            {a.amount != null && (
              <span className="txnv-budget-chip-amt">{EUR.format(a.amount)}</span>
            )}
            <button
              className="txnv-link-chip-remove"
              onClick={() => removeBudgetAssign(i, a.linkId, a.budgetId)}
              title={t('budgets.remove')}
            >
              ×
            </button>
          </span>
        ))}
        {adding == null && availableBudgets.length > 0 && (
          <button
            className="txnv-add-link-btn"
            onClick={() => setRows(prev => {
              const next = [...prev]
              next[i] = { ...next[i], addingBudget: { budgetId: '', amount: '' } }
              return next
            })}
            title={t('budgets.assign')}
          >
            budget
          </button>
        )}
        {adding != null && (
          <div className="txnv-budget-assign">
            <select
              className="txnv-budget-select"
              value={adding.budgetId}
              onChange={e => setRows(prev => {
                const next = [...prev]
                next[i] = { ...next[i], addingBudget: { ...next[i].addingBudget!, budgetId: e.target.value } }
                return next
              })}
              autoFocus
            >
              <option value="">—</option>
              {availableBudgets.map(b => (
                <option key={b.id} value={b.id}>{b.name}</option>
              ))}
            </select>
            <input
              className="txnv-partial-input"
              type="number"
              step="0.01"
              min="0"
              placeholder={t('transactions.partialAmount')}
              value={adding.amount}
              onChange={e => setRows(prev => {
                const next = [...prev]
                next[i] = { ...next[i], addingBudget: { ...next[i].addingBudget!, amount: e.target.value } }
                return next
              })}
            />
            <button
              className="txnv-link-confirm-btn"
              onClick={() => confirmBudgetAssign(i)}
              disabled={!adding.budgetId}
            >
              ✓
            </button>
            <button
              className="txnv-link-back-btn"
              onClick={() => setRows(prev => {
                const next = [...prev]
                next[i] = { ...next[i], addingBudget: null }
                return next
              })}
            >
              ×
            </button>
          </div>
        )}
      </div>
    )
  }

  function renderColumnHeader(col: ColumnKey) {
    const isDragging = dragCol === col
    const isDragOver = dragOverCol === col
    const thClass = [
      'txnv-th-draggable',
      isDragging ? 'txnv-th-dragging' : '',
      isDragOver ? 'txnv-th-drag-over' : '',
    ].filter(Boolean).join(' ')

    let label: string
    switch (col) {
      case 'date': label = t('transactions.columns.date'); break
      case 'account': label = t('transactions.columns.account'); break
      case 'amount': label = t('transactions.columns.amount'); break
      case 'category': label = t('transactions.columns.category'); break
      case 'group': label = t('transactions.columns.group'); break
      case 'subcategory': label = t('transactions.columns.subcategory'); break
      case 'offsets': label = t('transactions.columns.offsets'); break
      case 'budget': label = t('budgets.columns.budget'); break
      case 'counterparty': label = t('transactions.columns.counterpartyName'); break
      case 'purpose': label = t('transactions.columns.purpose'); break
      case 'comment': label = t('transactions.columns.comment'); break
    }

    return (
      <th
        key={col}
        className={thClass}
        draggable={true}
        onDragStart={() => setDragCol(col)}
        onDragEnd={() => { setDragCol(null); setDragOverCol(null) }}
        onDragOver={e => { e.preventDefault(); setDragOverCol(col) }}
        onDragEnter={e => { e.preventDefault(); setDragOverCol(col) }}
        onDragLeave={() => setDragOverCol(null)}
        onDrop={() => handleDrop(col)}
      >
        {label}
      </th>
    )
  }

  function renderCell(col: ColumnKey, row: RowState, i: number, rowLinkColor: string | null) {
    switch (col) {
      case 'date':
        return (
          <td
            key={col}
            className="txn-cell-date"
            style={rowLinkColor ? { boxShadow: `inset 3px 0 0 0 ${rowLinkColor}`, paddingLeft: '9px' } : undefined}
          >
            <input
              className="txnv-accounting-date-input"
              type="date"
              value={row.accountingDate}
              disabled={row.savingAccountingDate}
              onChange={e => updateRow(i, 'accountingDate', e.target.value)}
              onBlur={() => saveAccountingDate(i)}
            />
            {row.original.accountingDate !== row.original.bookingDate && (
              <span className="txnv-booking-date-ref" title={t('transactions.bookingDateTitle')}>
                {formatDate(row.original.bookingDate)}
              </span>
            )}
          </td>
        )
      case 'account':
        return (
          <td key={col} className="txnv-cell-account">
            {accountMap.get(row.original.accountIban) ?? row.original.accountIban}
          </td>
        )
      case 'amount':
        return (
          <td key={col} className={`txn-cell-amount txnv-col-amount${row.original.amount < 0 ? ' negative' : ' positive'}`}>
            {EUR.format(row.original.amount)}
          </td>
        )
      case 'category':
        return (
          <td key={col}>
            <input
              className="ri-cat-input"
              value={row.category}
              list="txnv-cat-list"
              onChange={e => updateRow(i, 'category', e.target.value)}
            />
          </td>
        )
      case 'group':
        return (
          <td key={col}>
            <input
              className="ri-cat-input"
              value={row.categoryGroup}
              list={row.category ? `txnv-grp-${row.category.replace(/\s+/g, '-').toLowerCase()}` : undefined}
              onChange={e => updateRow(i, 'categoryGroup', e.target.value)}
            />
          </td>
        )
      case 'subcategory':
        return (
          <td key={col}>
            <input
              className="ri-cat-input"
              value={row.subcategory}
              list={row.category ? sublistId(row.category) : undefined}
              onChange={e => updateRow(i, 'subcategory', e.target.value)}
            />
          </td>
        )
      case 'offsets':
        return (
          <td key={col} className="txnv-cell-offsets">
            {renderOffsetCell(row, i)}
          </td>
        )
      case 'budget':
        return (
          <td key={col} className="txnv-cell-budget">
            {renderBudgetCell(row, i)}
          </td>
        )
      case 'counterparty':
        return (
          <td key={col} className="txnv-cell-counterparty">
            <span
              className="txnv-counterparty-name"
              title={row.original.counterpartyIban ?? undefined}
            >
              {row.original.counterpartyName ?? ''}
            </span>
          </td>
        )
      case 'purpose':
        return (
          <td key={col} className="txnv-cell-purpose" title={row.original.purpose ?? undefined}>
            <span className="txnv-purpose-text">{row.original.purpose ?? ''}</span>
          </td>
        )
      case 'comment':
        return (
          <td key={col} className="txnv-cell-comment">
            <input
              className="txnv-comment-input"
              type="text"
              value={row.comment}
              placeholder={t('transactions.addComment')}
              disabled={row.savingComment}
              onChange={e => updateRow(i, 'comment', e.target.value)}
              onBlur={() => saveComment(i)}
              onKeyDown={e => { if (e.key === 'Enter') { e.currentTarget.blur() } }}
            />
          </td>
        )
    }
  }

  return (
    <div className="txnv-page">
      <div className="tr-controls">
        <button
          className="load-btn"
          onClick={load}
          disabled={page.phase === 'loading'}
        >
          {page.phase === 'loading' ? '…' : t('common.load')}
        </button>
        {allCategoryNames.length > 0 && (
          <select
            className="account-select"
            value={filterCategory}
            disabled={filterUncategorized}
            onChange={e => {
              const cat = e.target.value
              setFilterCategory(cat)
              setFilterCategoryGroup('')
              setFilterSubcategory('')
              setLinkingState(null)
              if (page.phase === 'ready') doLoad(cat || undefined)
            }}
          >
            <option value="">{t('transactions.allCategories')}</option>
            {allCategoryNames.map(c => <option key={c} value={c}>{c}</option>)}
          </select>
        )}
        {filterCategory && groupsFor(filterCategory).length > 0 && (
          <select
            className="account-select"
            value={filterCategoryGroup}
            disabled={filterUncategorized}
            onChange={e => {
              const grp = e.target.value
              setFilterCategoryGroup(grp)
              setFilterSubcategory('')
              setLinkingState(null)
              if (page.phase === 'ready') doLoad(filterCategory, undefined, undefined, grp || undefined)
            }}
          >
            <option value="">{t('common.allGroups')}</option>
            {groupsFor(filterCategory).map(g => <option key={g} value={g}>{g}</option>)}
          </select>
        )}
        {filterCategory && subcategoriesFor(filterCategory).length > 0 && (
          <select
            className="account-select"
            value={filterSubcategory}
            onChange={e => {
              const sub = e.target.value
              setFilterSubcategory(sub)
              setLinkingState(null)
              if (page.phase === 'ready') doLoad(filterCategory, sub || undefined, undefined, filterCategoryGroup || undefined)
            }}
          >
            <option value="">{t('transactions.allSubcategories')}</option>
            {subcategoriesFor(filterCategory).map(s => <option key={s} value={s}>{s}</option>)}
          </select>
        )}
        <button
          className={`load-btn${filterUncategorized ? ' load-btn--active' : ''}`}
          onClick={() => {
            const next = !filterUncategorized
            setFilterUncategorized(next)
            setFilterCategory('')
            setFilterSubcategory('')
            setLinkingState(null)
            if (page.phase === 'ready') doLoad(undefined, undefined, next || undefined)
          }}
        >
          {t('transactions.filterUncategorized')}
        </button>
        {page.phase === 'ready' && (
          <span className="txnv-count">{t('transactions.count', { count: rows.length })}</span>
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
      {uniqueRowCategories.map(cat => (
        <datalist key={`grp-${cat}`} id={`txnv-grp-${cat.replace(/\s+/g, '-').toLowerCase()}`}>
          {groupsFor(cat).map(g => <option key={g} value={g} />)}
        </datalist>
      ))}

      <div className="txnv-body">
        {page.phase === 'idle' && (
          <p className="hint"><Trans i18nKey="common.selectDateAndLoad"><span /><kbd /></Trans></p>
        )}
        {page.phase === 'loading' && (
          <p className="hint loading">{t('common.fetching')}</p>
        )}
        {page.phase === 'error' && (
          <p className="hint error">{(page as { phase: 'error'; message: string }).message}</p>
        )}
        {page.phase === 'ready' && rows.length === 0 && (
          <p className="hint">{t('common.noTransactions')}</p>
        )}
        {page.phase === 'ready' && rows.length > 0 && (
          <table className="txnv-table">
            <thead>
              <tr>
                <th className="txnv-col-check">
                  <input
                    type="checkbox"
                    className="txnv-checkbox"
                    checked={allSelected}
                    ref={el => { if (el) el.indeterminate = selectedCount > 0 && !allSelected }}
                    onChange={toggleSelectAll}
                  />
                </th>
                {colOrder.map(col => renderColumnHeader(col))}
                <th></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row, i) => {
                const rowLinkColor = (() => {
                  for (const group of row.original.groups) {
                    const idx = groupColorMap.get(group.id)
                    if (idx !== undefined) return LINK_COLORS[idx]
                  }
                  return null
                })()
                return (
                  <tr key={row.original.id} className={rowClassName(row, i)} data-txid={row.original.id}>
                    <td className="txnv-col-check">
                      <input
                        type="checkbox"
                        className="txnv-checkbox"
                        checked={row.selected}
                        onChange={() => toggleSelect(i)}
                      />
                    </td>
                    {colOrder.map(col => renderCell(col, row, i, rowLinkColor))}
                    <td className="txnv-cell-actions">
                      {row.error && (
                        <span className="txnv-row-error">{row.error}</span>
                      )}
                      {(row.category !== row.original.category || row.categoryGroup !== (row.original.categoryGroup ?? '') || row.subcategory !== row.original.subcategory) && (
                        <button
                          className="txnv-save-btn"
                          onClick={() => saveRow(i)}
                          disabled={row.saving}
                        >
                          {row.saving ? '…' : t('common.save')}
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

      {renderLinkModal()}
      {renderGroupModal()}

      {selectedCount > 0 && (
        <div className="txnv-bulk-bar">
          <span className="txnv-bulk-count">{t('transactions.bulkSelected', { count: selectedCount })}</span>
          <div className="txnv-bulk-inputs">
            <input
              className="ri-cat-input txnv-bulk-cat"
              placeholder={t('common.category')}
              value={bulkCategory}
              list="txnv-cat-list"
              onChange={e => { setBulkCategory(e.target.value); setBulkCategoryGroup(''); setBulkSubcategory('') }}
            />
            <input
              className="ri-cat-input txnv-bulk-cat"
              placeholder={t('common.group')}
              value={bulkCategoryGroup}
              list={bulkCategory ? `txnv-grp-${bulkCategory.replace(/\s+/g, '-').toLowerCase()}` : undefined}
              onChange={e => setBulkCategoryGroup(e.target.value)}
            />
            <input
              className="ri-cat-input txnv-bulk-cat"
              placeholder={t('common.subcategory')}
              value={bulkSubcategory}
              list={bulkCategory ? sublistId(bulkCategory) : 'txnv-sub-all'}
              onChange={e => setBulkSubcategory(e.target.value)}
            />
          </div>
          <button
            className="txnv-bulk-apply-btn"
            onClick={applyBulk}
            disabled={bulkApplying || !bulkCategory.trim()}
          >
            {bulkApplying ? '…' : t('transactions.applyBulk')}
          </button>
          <button className="txnv-bulk-cancel-btn" onClick={clearSelection} disabled={bulkApplying}>
            ✕
          </button>
        </div>
      )}
    </div>
  )
}
