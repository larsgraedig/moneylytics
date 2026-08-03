import type { ReactNode } from 'react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { Trans, useTranslation } from 'react-i18next'
import type { CategoryNode } from '../api/rawImport'
import { CategoryPathInput } from './CategoryPathInput'
import {
  AllocationExceededError,
  bulkUpdateTransactionCategory,
  fetchAllTransactions,
  fetchLinkedGroup,
  fetchSubTransactionGroup,
  linkTransactions,
  unsplitTransaction,
  unmergeTransactions,
  updateTransactionAccountingDate,
  updateTransactionCategory,
  updateTransactionComment,
  type Account,
  type AllocationError,
  type GroupSummary,
  type LinkedGroupItem,
  type TransactionItem,
} from '../api/transactions'
import { CreateVirtualTransactionModal } from './CreateVirtualTransactionModal'
import { GroupCard } from './GroupCard'
import { SplitTransactionModal } from './SplitTransactionModal'
import { MergeTransactionModal } from './MergeTransactionModal'
import {
  assignTransaction as assignToBudget,
  fetchBudgets,
  removeTransactionLink as removeBudgetLink,
  type Budget,
} from '../api/budgets'
import {
  addTransactionToCollection,
  createCollection,
  fetchCollections,
  removeTransactionFromCollection,
} from '../api/collections'
import type { CollectionSummary } from '../api/transactions'
import { updateUserSettings } from '../api/settings'

const LINK_COLORS = ['#f59e0b', '#10b981', '#60a5fa', '#f472b6', '#a78bfa', '#fb923c']
const BUDGET_COLORS = ['#34d399', '#818cf8', '#fb7185', '#fbbf24', '#38bdf8', '#a3e635']

const DEFAULT_COLUMN_ORDER = ['date', 'account', 'amount', 'category', 'offsets', 'budget', 'collection', 'counterparty', 'purpose', 'comment'] as const
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
  subcategory: string
  group: string
  comment: string
  accountingDate: string
  selected: boolean
  saving: boolean
  savingComment: boolean
  savingAccountingDate: boolean
  error: string | null
  budgetAssignments: BudgetAssignment[]
  addingBudget: { budgetId: string; amount: string } | null
  collections: CollectionSummary[]
  addingCollection: { collectionId: string; newName: string } | null
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
  categories,
  onCategoryCreated,
  columnOrder,
  onColumnOrderChange,
}: {
  from: string
  to: string
  iban?: string
  accounts: Account[]
  categories: CategoryNode[]
  onCategoryCreated?: (node: CategoryNode) => void
  columnOrder?: string[]
  onColumnOrderChange?: (order: string[]) => void
}) {
  const { t } = useTranslation()
  const location = useLocation()
  const [rows, setRows] = useState<RowState[]>([])
  const [page, setPage] = useState<PageState>({ phase: 'idle' })
  const [budgets, setBudgets] = useState<Budget[]>([])
  const [allCollections, setAllCollections] = useState<CollectionSummary[]>([])
  const [linkingState, setLinkingState] = useState<LinkingState>(null)
  const [linkError, setLinkError] = useState<string | AllocationError | null>(null)
  const [groupModal, setGroupModal] = useState<{ groupId: number; group: LinkedGroupItem | null } | null>(null)
  const [highlightedId] = useState<number | null>(null)
  const [filterCategoryId, setFilterCategoryId] = useState<number | null>(null)
  const [filterUncategorized, setFilterUncategorized] = useState(false)
  const [filterType, setFilterType] = useState<'all' | 'income' | 'expenses'>('all')
  const [bulkCategoryId, setBulkCategoryId] = useState<number | null>(null)
  const [bulkApplying, setBulkApplying] = useState(false)
  const [dragCol, setDragCol] = useState<ColumnKey | null>(null)
  const [dragOverCol, setDragOverCol] = useState<ColumnKey | null>(null)
  const [splitModalTx, setSplitModalTx] = useState<TransactionItem | null>(null)
  const [mergeModalTxs, setMergeModalTxs] = useState<TransactionItem[] | null>(null)
  const [parentTxMap, setParentTxMap] = useState<Map<number, TransactionItem>>(new Map())
  const [mergeChildrenMap, setMergeChildrenMap] = useState<Map<number, TransactionItem[]>>(new Map())
  const [createVirtualOpen, setCreateVirtualOpen] = useState(false)

  useEffect(() => {
    fetchBudgets().then(setBudgets).catch(() => {})
    fetchCollections().then(cols => setAllCollections(cols.map(c => ({ id: c.id, name: c.name })))).catch(() => {})
  }, [])

  const accountMap = useMemo(
    () => new Map(accounts.map(a => [a.iban, a.name])),
    [accounts],
  )

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

  function toApiType(t: 'all' | 'income' | 'expenses'): 'ALL' | 'INCOME' | 'EXPENSES' {
    if (t === 'income') return 'INCOME'
    if (t === 'expenses') return 'EXPENSES'
    return 'ALL'
  }

  async function doLoad(categoryId?: number | null, uncategorized?: boolean, type?: 'ALL' | 'INCOME' | 'EXPENSES') {
    setPage({ phase: 'loading' })
    setLinkingState(null)
    try {
      const data = await fetchAllTransactions(from, to, iban, undefined, undefined, uncategorized, undefined, type ?? toApiType(filterType), undefined, undefined, categoryId ?? undefined)
      setRows(
        data.transactions.map(tx => ({
          original: tx,
          category: tx.category ?? '',
          subcategory: tx.subcategory ?? '',
          group: tx.group ?? '',
          comment: tx.comment ?? '',
          accountingDate: tx.accountingDate,
          selected: false,
          saving: false,
          savingComment: false,
          savingAccountingDate: false,
          error: null,
          budgetAssignments: (tx.budgetLinks ?? []).map(l => ({ linkId: l.linkId, budgetId: l.budgetId, budgetName: l.budgetName, amount: l.amount })),
          addingBudget: null,
          collections: tx.collections ?? [],
          addingCollection: null,
        })),
      )
      setPage({ phase: 'ready' })

      // Fetch parent transactions for virtual split children so they can be shown as ghost rows
      const parentIds = new Set<number>()
      data.transactions.forEach(tx => {
        if (tx.isVirtual && tx.parentId != null) parentIds.add(tx.parentId)
      })
      if (parentIds.size > 0) {
        const results = await Promise.allSettled(
          [...parentIds].map(id => fetchSubTransactionGroup(id).then(g => g ? ({ id, tx: g.parent }) : null)),
        )
        const map = new Map<number, TransactionItem>()
        results.forEach(r => { if (r.status === 'fulfilled' && r.value) map.set(r.value.id, r.value.tx) })
        setParentTxMap(map)
      } else {
        setParentTxMap(new Map())
      }

      // Fetch children for merged virtual transactions so they can be shown as ghost rows
      const mergeVirtualIds = data.transactions
        .filter(tx => tx.isVirtual && tx.parentId == null)
        .map(tx => tx.id)
      if (mergeVirtualIds.length > 0) {
        const results = await Promise.allSettled(
          mergeVirtualIds.map(id => fetchSubTransactionGroup(id).then(g => g ? ({ id, children: g.children }) : null)),
        )
        const map = new Map<number, TransactionItem[]>()
        results.forEach(r => { if (r.status === 'fulfilled' && r.value) map.set(r.value.id, r.value.children) })
        setMergeChildrenMap(map)
      } else {
        setMergeChildrenMap(new Map())
      }
    } catch (e) {
      setPage({ phase: 'error', message: e instanceof Error ? e.message : t('common.requestFailed') })
    }
  }

  function load() {
    setFilterCategoryId(null)
    setFilterUncategorized(false)
    doLoad()
  }

  function updateRow(index: number, field: 'category' | 'subcategory' | 'group' | 'comment' | 'accountingDate', value: string) {
    setRows(prev => {
      const next = [...prev]
      next[index] = { ...next[index], [field]: value }
      return next
    })
  }

  async function handleCategoryChange(index: number, categoryId: number | null) {
    const row = rows[index]
    setRows(prev => {
      const next = [...prev]
      next[index] = { ...next[index], saving: true, error: null }
      return next
    })
    try {
      const updated = await updateTransactionCategory(row.original.id, categoryId)
      setRows(prev => {
        const next = [...prev]
        next[index] = {
          ...next[index],
          original: updated,
          category: updated.category ?? '',
          subcategory: updated.subcategory ?? '',
          group: updated.group ?? '',
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
      const { sourceTransaction, otherTransaction } = await linkTransactions(
        sourceRow.original.id, targetRow.original.id, parsedMy, parsedOther, targetGroupId, forceNewGroup,
      )
      setRows(prev => {
        const next = [...prev]
        next[sourceIndex] = { ...next[sourceIndex], original: sourceTransaction }
        next[targetIndex] = { ...next[targetIndex], original: otherTransaction }
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

  async function removeBudgetAssign(rowIndex: number, linkId: number) {
    try {
      await removeBudgetLink(linkId)
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
    setBulkCategoryId(null)
  }

  async function applyBulk() {
    if (selectedCount === 0) return
    setBulkApplying(true)
    const categoryId = bulkCategoryId
    const indices = rows.map((r, i) => ({ r, i })).filter(({ r }) => r.selected).map(({ i }) => i)
    try {
      const updated = await bulkUpdateTransactionCategory(
        indices.map(i => ({ id: rows[i].original.id, categoryId })),
      )
      const updatedById = new Map(updated.map(tx => [tx.id, tx]))
      setRows(prev => {
        const next = [...prev]
        indices.forEach(i => {
          const tx = updatedById.get(next[i].original.id)
          if (tx) {
            next[i] = {
              ...next[i],
              original: tx,
              category: tx.category ?? '',
              subcategory: tx.subcategory ?? '',
              group: tx.group ?? '',
              selected: false,
            }
          }
        })
        return next
      })
    } catch {
      // silent — user can retry
    }
    setBulkApplying(false)
    setBulkCategoryId(null)
  }

  const filteredRows = useMemo(() => rows.map((row, i) => ({ row, i })), [rows])

  type DisplayItem =
    | { type: 'ghost'; parentTx: TransactionItem; parentId: number }
    | { type: 'merge-child-ghost'; childTx: TransactionItem; parentVirtualId: number }
    | { type: 'row'; row: RowState; i: number }

  const displayItems = useMemo((): DisplayItem[] => {
    const seenParentIds = new Set<number>()
    const seenIds = new Set<number>()
    const result: DisplayItem[] = []

    for (const { row, i } of filteredRows) {
      if (seenIds.has(row.original.id)) continue

      if (row.original.isVirtual && row.original.parentId != null) {
        const parentId = row.original.parentId
        if (!seenParentIds.has(parentId)) {
          seenParentIds.add(parentId)
          const parentTx = parentTxMap.get(parentId)
          if (parentTx) result.push({ type: 'ghost', parentTx, parentId })
          // Collect ALL siblings together regardless of their position in filteredRows
          for (const sibling of filteredRows) {
            if (sibling.row.original.isVirtual && sibling.row.original.parentId === parentId) {
              seenIds.add(sibling.row.original.id)
              result.push({ type: 'row', row: sibling.row, i: sibling.i })
            }
          }
        }
      } else {
        seenIds.add(row.original.id)
        result.push({ type: 'row', row, i })
        // For merged virtual transactions, show their original children as ghost rows below
        if (row.original.isVirtual && row.original.parentId == null) {
          const children = mergeChildrenMap.get(row.original.id)
          if (children) {
            children.forEach(child => {
              result.push({ type: 'merge-child-ghost', childTx: child, parentVirtualId: row.original.id })
            })
          }
        }
      }
    }

    return result
  }, [filteredRows, parentTxMap, mergeChildrenMap])

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
    if (commentDirty || accountingDateDirty)
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
              onClick={() => removeBudgetAssign(i, a.linkId)}
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

  async function addToCollection(rowIndex: number, collectionId: number, collectionName: string) {
    const row = rows[rowIndex]
    try {
      await addTransactionToCollection(collectionId, row.original.id)
      setRows(prev => {
        const next = [...prev]
        next[rowIndex] = {
          ...next[rowIndex],
          collections: [...next[rowIndex].collections, { id: collectionId, name: collectionName }],
          addingCollection: null,
        }
        return next
      })
    } catch {
      // silent — user can retry
    }
  }

  async function createAndAddToCollection(rowIndex: number, name: string) {
    if (!name.trim()) return
    const row = rows[rowIndex]
    try {
      const created = await createCollection(name.trim(), null)
      setAllCollections(prev => [...prev, { id: created.id, name: created.name }])
      await addTransactionToCollection(created.id, row.original.id)
      setRows(prev => {
        const next = [...prev]
        next[rowIndex] = {
          ...next[rowIndex],
          collections: [...next[rowIndex].collections, { id: created.id, name: created.name }],
          addingCollection: null,
        }
        return next
      })
    } catch {
      // silent — user can retry
    }
  }

  async function removeFromCollection(rowIndex: number, collectionId: number) {
    const row = rows[rowIndex]
    try {
      await removeTransactionFromCollection(collectionId, row.original.id)
      setRows(prev => {
        const next = [...prev]
        next[rowIndex] = {
          ...next[rowIndex],
          collections: next[rowIndex].collections.filter(c => c.id !== collectionId),
        }
        return next
      })
    } catch {
      // silent
    }
  }

  function renderCollectionCell(row: RowState, i: number) {
    const alreadyIn = new Set(row.collections.map(c => c.id))
    const available = allCollections.filter(c => !alreadyIn.has(c.id))
    const adding = row.addingCollection

    return (
      <div className="txnv-budget-cell">
        {row.collections.map((c, ci) => (
          <span
            key={c.id}
            className="txnv-budget-chip"
            style={{ borderColor: BUDGET_COLORS[ci % BUDGET_COLORS.length] }}
          >
            <span className="txnv-budget-chip-name">{c.name}</span>
            <button
              className="txnv-link-chip-remove"
              onClick={() => removeFromCollection(i, c.id)}
              title={t('collections.removeTransaction')}
            >
              ×
            </button>
          </span>
        ))}
        {adding == null && (
          <button
            className="txnv-add-link-btn"
            onClick={() => setRows(prev => {
              const next = [...prev]
              next[i] = { ...next[i], addingCollection: { collectionId: '', newName: '' } }
              return next
            })}
            title={t('collections.addTransaction')}
          >
            {t('collections.collection')}
          </button>
        )}
        {adding != null && (
          <div className="txnv-budget-assign">
            <select
              className="txnv-budget-select"
              value={adding.collectionId}
              onChange={e => setRows(prev => {
                const next = [...prev]
                next[i] = { ...next[i], addingCollection: { ...next[i].addingCollection!, collectionId: e.target.value } }
                return next
              })}
              autoFocus
            >
              <option value="">—</option>
              {available.map(c => (
                <option key={c.id} value={String(c.id)}>{c.name}</option>
              ))}
              <option value="__new__">+ {t('collections.createCollection')}</option>
            </select>
            {adding.collectionId === '__new__' && (
              <input
                className="txnv-partial-input"
                type="text"
                placeholder={t('collections.namePlaceholder')}
                value={adding.newName}
                onChange={e => setRows(prev => {
                  const next = [...prev]
                  next[i] = { ...next[i], addingCollection: { ...next[i].addingCollection!, newName: e.target.value } }
                  return next
                })}
              />
            )}
            <button
              className="txnv-link-confirm-btn"
              disabled={!adding.collectionId || (adding.collectionId === '__new__' && !adding.newName.trim())}
              onClick={() => {
                if (adding.collectionId === '__new__') {
                  createAndAddToCollection(i, adding.newName)
                } else {
                  const col = allCollections.find(c => c.id === Number(adding.collectionId))
                  if (col) addToCollection(i, col.id, col.name)
                }
              }}
            >
              ✓
            </button>
            <button
              className="txnv-link-back-btn"
              onClick={() => setRows(prev => {
                const next = [...prev]
                next[i] = { ...next[i], addingCollection: null }
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

  function renderGhostCell(col: ColumnKey, tx: TransactionItem): ReactNode {
    switch (col) {
      case 'date':
        return <td key={col} className="txn-cell-date">{formatDate(tx.accountingDate)}</td>
      case 'account':
        return <td key={col} className="txnv-cell-account">{accountMap.get(tx.accountIban) ?? tx.accountIban}</td>
      case 'amount':
        return (
          <td key={col} className={`txn-cell-amount txnv-col-amount${tx.amount < 0 ? ' negative' : ' positive'}`}>
            {EUR.format(tx.amount)}
          </td>
        )
      case 'category':
        return <td key={col}><span className="ri-cat-input" style={{ display: 'inline-block' }}>{tx.category ?? ''}</span></td>
      case 'counterparty':
        return <td key={col} className="txnv-cell-counterparty"><span className="txnv-counterparty-name">{tx.counterpartyName ?? ''}</span></td>
      case 'purpose':
        return <td key={col} className="txnv-cell-purpose"><span className="txnv-purpose-text">{tx.purpose ?? ''}</span></td>
      case 'comment':
        return <td key={col} className="txnv-cell-comment"><span style={{ color: 'inherit', fontSize: 12 }}>{tx.comment ?? ''}</span></td>
      default:
        return <td key={col} />
    }
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
      case 'offsets': label = t('transactions.columns.offsets'); break
      case 'budget': label = t('budgets.columns.budget'); break
      case 'collection': label = t('collections.columns.name'); break
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
            <CategoryPathInput
              className="ri-cat-input"
              value={row.original.categoryId ?? null}
              onChange={id => { void handleCategoryChange(i, id) }}
              tree={categories}
              onCategoryCreated={onCategoryCreated}
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
      case 'collection':
        return (
          <td key={col} className="txnv-cell-budget">
            {renderCollectionCell(row, i)}
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
        <button
          className="load-btn"
          onClick={() => setCreateVirtualOpen(true)}
        >
          + {t('virtualTransaction.button')}
        </button>
        {categories.length > 0 && (
          <CategoryPathInput
            value={filterCategoryId}
            onChange={id => {
              setFilterCategoryId(id)
              setLinkingState(null)
              if (page.phase === 'ready') doLoad(id)
            }}
            tree={categories}
            allowCreate={false}
            placeholder={t('transactions.allCategories')}
            className="account-select"
          />
        )}
        <button
          className={`load-btn${filterUncategorized ? ' load-btn--active' : ''}`}
          onClick={() => {
            const next = !filterUncategorized
            setFilterUncategorized(next)
            setFilterCategoryId(null)
            setLinkingState(null)
            if (page.phase === 'ready') doLoad(null, next || undefined)
          }}
        >
          {t('transactions.filterUncategorized')}
        </button>
        <div className="txnv-type-filter">
          {(['all', 'income', 'expenses'] as const).map(type => (
            <button
              key={type}
              className={`txnv-type-btn${filterType === type ? ' txnv-type-btn--active' : ''}`}
              onClick={() => {
                setFilterType(type)
                doLoad(filterCategoryId, filterUncategorized || undefined, toApiType(type))
              }}
            >
              {t(`transactions.filter${type.charAt(0).toUpperCase() + type.slice(1)}`)}
            </button>
          ))}
        </div>
        {page.phase === 'ready' && (
          <span className="txnv-count">{t('transactions.count', { count: filteredRows.length })}</span>
        )}
      </div>


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
        {page.phase === 'ready' && filteredRows.length === 0 && (
          <p className="hint">{t('common.noTransactions')}</p>
        )}
        {page.phase === 'ready' && filteredRows.length > 0 && (
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
              {displayItems.map(item => {
                if (item.type === 'ghost') {
                  return (
                    <tr key={`ghost-${item.parentId}`} className="txnv-row--parent-ghost">
                      <td className="txnv-col-check" />
                      {colOrder.map(col => renderGhostCell(col, item.parentTx))}
                      <td className="txnv-cell-actions">
                        <span className="txnv-sub-badge txnv-sub-badge--split">{t('transactions.split.splitBadge')}</span>
                      </td>
                    </tr>
                  )
                }
                if (item.type === 'merge-child-ghost') {
                  return (
                    <tr key={`merge-child-${item.childTx.id}`} className="txnv-row--merge-child-ghost">
                      <td className="txnv-col-check" />
                      {colOrder.map(col => renderGhostCell(col, item.childTx))}
                      <td className="txnv-cell-actions">
                        <span className="txnv-sub-badge txnv-sub-badge--merged-child">{t('transactions.merge.mergedBadge')}</span>
                      </td>
                    </tr>
                  )
                }
                const { row, i } = item
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
                      {row.saving && <span className="txnv-save-btn">…</span>}
                      {row.original.isVirtual && row.original.parentId == null && (() => {
                        const children = mergeChildrenMap.get(row.original.id)
                        const isStandalone = children != null && children.length === 0
                        if (isStandalone) {
                          return (
                            <span
                              className="txnv-sub-badge txnv-sub-badge--merge"
                              title={t('virtualTransaction.deleteConfirm')}
                              onClick={() => {
                                if (confirm(t('virtualTransaction.deleteConfirm'))) {
                                  unmergeTransactions(row.original.id).then(() => doLoad()).catch(() => {})
                                }
                              }}
                              style={{ cursor: 'pointer' }}
                            >
                              {t('virtualTransaction.badge')} ×
                            </span>
                          )
                        }
                        return (
                          <span
                            className="txnv-sub-badge txnv-sub-badge--merge"
                            title={t('transactions.merge.undoConfirm')}
                            onClick={() => {
                              if (confirm(t('transactions.merge.undoConfirm'))) {
                                unmergeTransactions(row.original.id).then(() => doLoad()).catch(() => {})
                              }
                            }}
                            style={{ cursor: 'pointer' }}
                          >
                            {t('transactions.merge.virtualBadge')} ×
                          </span>
                        )
                      })()}
                      {row.original.isVirtual && row.original.parentId != null && (
                        <span className="txnv-sub-badge txnv-sub-badge--split-child">
                          {t('transactions.split.virtualBadge')}
                        </span>
                      )}
                      {!row.original.isVirtual && row.original.excluded && row.original.parentId != null && (
                        <span className="txnv-sub-badge txnv-sub-badge--merged-child">
                          {t('transactions.merge.mergedBadge')}
                        </span>
                      )}
                      {!row.original.isVirtual && row.original.excluded && row.original.parentId == null && (
                        <span
                          className="txnv-sub-badge txnv-sub-badge--split"
                          title={t('transactions.split.undoConfirm')}
                          onClick={() => {
                            if (confirm(t('transactions.split.undoConfirm'))) {
                              unsplitTransaction(row.original.id).then(() => doLoad()).catch(() => {})
                            }
                          }}
                          style={{ cursor: 'pointer' }}
                        >
                          {t('transactions.split.splitBadge')} ×
                        </span>
                      )}
                      {!row.original.isVirtual && !row.original.excluded && row.original.parentId == null && (
                        <button
                          className="txnv-sub-split-btn"
                          onClick={() => setSplitModalTx(row.original)}
                          title={t('transactions.split.button')}
                        >
                          ÷
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
      {createVirtualOpen && (
        <CreateVirtualTransactionModal
          accounts={accounts}
          categories={categories}
          defaultDate={to}
          onClose={() => setCreateVirtualOpen(false)}
          onCreate={() => { setCreateVirtualOpen(false); doLoad() }}
          onCategoryCreated={onCategoryCreated}
        />
      )}
      {splitModalTx && (
        <SplitTransactionModal
          transaction={splitModalTx}
          categories={categories}
          onClose={() => setSplitModalTx(null)}
          onSplit={() => { setSplitModalTx(null); doLoad() }}
          onCategoryCreated={onCategoryCreated}
        />
      )}
      {mergeModalTxs && (
        <MergeTransactionModal
          transactions={mergeModalTxs}
          onClose={() => setMergeModalTxs(null)}
          onMerge={() => { setMergeModalTxs(null); doLoad() }}
        />
      )}

      {selectedCount > 0 && (
        <div className="txnv-bulk-bar">
          <span className="txnv-bulk-count">{t('transactions.bulkSelected', { count: selectedCount })}</span>
          <div className="txnv-bulk-inputs">
            <CategoryPathInput
              className="ri-cat-input txnv-bulk-cat"
              value={bulkCategoryId}
              onChange={id => setBulkCategoryId(id)}
              tree={categories}
              onCategoryCreated={onCategoryCreated}
              placeholder={t('common.category')}
            />
          </div>
          <button
            className="txnv-bulk-apply-btn"
            onClick={applyBulk}
            disabled={bulkApplying}
          >
            {bulkApplying ? '…' : t('transactions.applyBulk')}
          </button>
          {selectedCount >= 2 && (
            <button
              className="txnv-bulk-apply-btn"
              style={{ background: '#10b981' }}
              onClick={() => {
                const selected = rows.filter(r => r.selected).map(r => r.original)
                setMergeModalTxs(selected)
              }}
              disabled={bulkApplying}
            >
              {t('transactions.merge.button')}
            </button>
          )}
          <button className="txnv-bulk-cancel-btn" onClick={clearSelection} disabled={bulkApplying}>
            ✕
          </button>
        </div>
      )}
    </div>
  )
}
