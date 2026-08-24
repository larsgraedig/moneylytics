import type { ReactNode } from 'react'
import { Link2, Wallet, Layers, Scissors, Package, MessageSquare, CalendarDays, CalendarClock } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import type { CategoryNode } from '../api/rawImport'
import { CategoryPathInput } from './CategoryPathInput'
import {
  AllocationExceededError,
  bulkUpdateTransactionCategory,
  fetchAllTransactions,
  fetchLinkedGroup,
  fetchSubTransactionGroup,
  linkTransactions,
  removeTransactionFromGroup,
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
import { TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Checkbox } from '@/components/ui/checkbox'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { DatePicker } from '@/components/ui/date-picker'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
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
import { useSidebar } from '@/components/ui/sidebar'

function parseIso(s: string): Date | null {
  if (!s) return null
  const [y, m, d] = s.split('-').map(Number)
  return new Date(y, m - 1, d)
}

function isoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const LINK_COLORS = ['#f59e0b', '#10b981', '#60a5fa', '#f472b6', '#a78bfa', '#fb923c']
const BUDGET_COLORS = ['#34d399', '#818cf8', '#fb7185', '#fbbf24', '#38bdf8', '#a3e635']

const DEFAULT_COLUMN_ORDER = ['date', 'account', 'amount', 'category', 'offsets', 'budget', 'collection', 'counterparty', 'comment'] as const
type ColumnKey = typeof DEFAULT_COLUMN_ORDER[number] | 'purpose'

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
  collections: CollectionSummary[]
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

function CommentInput({
  value,
  disabled,
  placeholder,
  className,
  style,
  onChange,
  onSave,
}: {
  value: string
  disabled: boolean
  placeholder: string
  className: string
  style?: React.CSSProperties
  onChange: (v: string) => void
  onSave: () => void
}) {
  const [open, setOpen] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const hasComment = value.trim() !== ''
  const showInput = hasComment || open

  useEffect(() => {
    if (open && inputRef.current) inputRef.current.focus()
  }, [open])

  if (!showInput) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="text-muted-foreground/30 hover:text-muted-foreground/70 transition-colors"
        title={placeholder}
      >
        <MessageSquare size={12} />
      </button>
    )
  }

  return (
    <Input
      ref={inputRef}
      className={className}
      style={style}
      type="text"
      value={value}
      placeholder={placeholder}
      disabled={disabled}
      onChange={e => onChange(e.target.value)}
      onBlur={() => { onSave(); if (!value.trim()) setOpen(false) }}
      onKeyDown={e => {
        if (e.key === 'Enter') e.currentTarget.blur()
        if (e.key === 'Escape') { e.currentTarget.blur(); if (!value.trim()) setOpen(false) }
      }}
    />
  )
}

export default function TransactionsPage({
  from,
  to,
  accountId,
  accounts,
  categories,
  onCategoryCreated,
  columnOrder,
  onColumnOrderChange,
}: {
  from: string
  to: string
  accountId?: number
  accounts: Account[]
  categories: CategoryNode[]
  onCategoryCreated?: (node: CategoryNode) => void
  columnOrder?: string[]
  onColumnOrderChange?: (order: string[]) => void
}) {
  const { t } = useTranslation()
  const { open: sidebarOpen, isMobile: sidebarIsMobile } = useSidebar()
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
  const [budgetModal, setBudgetModal] = useState<{ rowIndex: number; budgetId: string; amount: string } | null>(null)
  const [collectionModal, setCollectionModal] = useState<{ rowIndex: number; collectionId: string; newName: string } | null>(null)

  const CARD_BATCH = 50
  const [visibleCount, setVisibleCount] = useState(CARD_BATCH)
  const sentinelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    fetchBudgets().then(setBudgets).catch(() => {})
    fetchCollections().then(cols => setAllCollections(cols.map(c => ({ id: c.id, name: c.name })))).catch(() => {})
  }, [])

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void doLoad() }, [from, to, accountId])

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
    setVisibleCount(CARD_BATCH)
    setPage({ phase: 'loading' })
    setLinkingState(null)
    try {
      const data = await fetchAllTransactions(from, to, accountId, undefined, undefined, uncategorized, undefined, type ?? toApiType(filterType), undefined, undefined, categoryId ?? undefined)
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
          collections: tx.collections ?? [],
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

  async function saveAccountingDate(index: number, valueOverride?: string) {
    const row = rows[index]
    const dateToSave = valueOverride ?? row.accountingDate
    if (!dateToSave || dateToSave === row.original.accountingDate) return
    setRows(prev => {
      const next = [...prev]
      next[index] = { ...next[index], savingAccountingDate: true }
      return next
    })
    try {
      const updated = await updateTransactionAccountingDate(row.original.id, dateToSave)
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


  async function confirmBudgetAssign() {
    if (!budgetModal || !budgetModal.budgetId) return
    const { rowIndex } = budgetModal
    const row = rows[rowIndex]
    const budgetId = Number(budgetModal.budgetId)
    const amount = budgetModal.amount !== '' ? parseFloat(budgetModal.amount) || null : null
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
        }
        return next
      })
      setBudgetModal(null)
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
  const filteredSum = useMemo(() => filteredRows.reduce((sum, { row }) => sum + row.original.amount, 0), [filteredRows])

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

  useEffect(() => {
    const el = sentinelRef.current
    if (!el) return
    const obs = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && visibleCount < displayItems.length) {
          setVisibleCount(c => Math.min(c + CARD_BATCH, displayItems.length))
        }
      },
      { rootMargin: '300px' },
    )
    obs.observe(el)
    return () => obs.disconnect()
  }, [displayItems.length, visibleCount])

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
    if (row.original.isVirtual && row.original.parentId != null)
      classes.push('txnv-row--split-child')
    return classes.join(' ')
  }

  async function removeFromGroup(rowIndex: number, groupId: number) {
    const row = rows[rowIndex]
    try {
      await removeTransactionFromGroup(row.original.id, groupId)
      setRows(prev => {
        const next = [...prev]
        next[rowIndex] = {
          ...next[rowIndex],
          original: {
            ...next[rowIndex].original,
            groups: next[rowIndex].original.groups.filter(g => g.id !== groupId),
          },
        }
        return next
      })
    } catch {
      // silent — user can retry
    }
  }

  function renderOffsetCell(row: RowState, i: number) {
    const src = linkingState?.sourceIndex
    const isSource = src === i
    const isSelecting = linkingState?.phase === 'selecting'

    if (isSource) {
      return (
        <div className="txnv-linking-from">
          <span className="txnv-linking-badge">{t('transactions.linkingSelectingTarget')}</span>
          <button
            className="txnv-link-chip-remove"
            onClick={() => { setLinkingState(null); setLinkError(null) }}
            title={t('common.cancel')}
          >
            ×
          </button>
        </div>
      )
    }

    if (isSelecting) {
      return (
        <Button
          variant="outline"
          size="xs"
          className="rounded-sm font-mono text-[10px] border-green-500/30 text-green-400 hover:border-green-400 hover:text-green-300 hover:bg-green-400/10"
          onClick={() =>
            src !== undefined &&
            setLinkingState({ phase: 'confirming', sourceIndex: src, targetIndex: i, myAmount: '', otherAmount: '' })
          }
        >
          {t('transactions.linkHere')}
        </Button>
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
              title={`#${group.id}`}
            >
              <span
                onClick={() => {
                  setGroupModal({ groupId: group.id, group: null })
                  fetchLinkedGroup(group.id).then(g => setGroupModal({ groupId: group.id, group: g }))
                }}
              >
                {chipLabel}
              </span>
              <button
                className="txnv-link-chip-remove"
                onClick={e => { e.stopPropagation(); void removeFromGroup(i, group.id) }}
                title={t('transactions.removeFromGroup')}
              >
                ×
              </button>
            </span>
          )
        })}
        {row.original.groups.length === 0 && (
          <Button
            variant="ghost"
            size="icon-xs"
            className="rounded-sm border border-dashed border-border hover:border-foreground/40"
            onClick={() => { setLinkError(null); setLinkingState({ phase: 'selecting', sourceIndex: i }) }}
            title={t('transactions.linkToTransaction')}
          >
            <Link2 />
          </Button>
        )}
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
      <Dialog open onOpenChange={open => { if (!open) close() }}>
        <DialogContent className="max-w-3xl max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              <Link to={{ pathname: '/verknuepfungen', search: deepLinkSearch.toString() }} onClick={close} className="hover:underline">
                {t('linked.group')} #{groupModal.groupId} ↗
              </Link>
            </DialogTitle>
          </DialogHeader>
          {!group ? <p className="text-sm text-muted-foreground">{t('common.loading')}</p> : (
            <GroupCard
              group={group}
              onMetaChange={handleMetaChange}
              onOffsetCommentChange={handleOffsetCommentChange}
              onRemoveTransaction={txId => {
                const remaining = group.transactions.filter(tx => tx.id !== txId)
                if (remaining.length >= 2) setGroupModal(prev => prev ? { ...prev, group: { ...group, transactions: remaining } } : null)
                else setGroupModal(null)
              }}
            />
          )}
        </DialogContent>
      </Dialog>
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
      <Dialog open onOpenChange={open => { if (!open) cancel() }}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t('transactions.linkModal.title')}</DialogTitle>
          </DialogHeader>

          <div className="flex flex-col gap-4">
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
                    <Input
                      className="h-8 w-28 font-mono text-xs rounded-md"
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
                    <Input
                      className="h-8 w-28 font-mono text-xs rounded-md"
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
                  <Button variant="default" size="sm" className="rounded-md" onClick={confirmLink}>{t('transactions.link')}</Button>
                  <Button variant="ghost" size="sm" className="rounded-md" onClick={cancel}>{t('common.cancel')}</Button>
                </div>
              </>
            )}

            {linkingState.phase === 'group-select' && (
              <>
                <div className="txnv-lm-group-section">
                  <span className="txnv-group-select-label">{t('transactions.selectGroup')}</span>
                  <div className="flex flex-col gap-1">
                    {linkingState.availableGroups.map(g => (
                      <Button key={g.id} variant="outline" size="xs" className="rounded-md justify-start w-full font-mono text-[10px]" onClick={() => confirmLinkWithGroup(g.id)}>
                        {g.name ?? `#${g.id}`}
                      </Button>
                    ))}
                    <Button variant="outline" size="xs" className="rounded-md justify-start w-full font-mono text-[10px] border-green-500/30 text-green-400 hover:border-green-400 hover:bg-green-400/10" onClick={() => confirmLinkWithGroup(undefined)}>
                      {t('transactions.newGroup')}
                    </Button>
                  </div>
                </div>
                <div className="txnv-lm-footer">
                  <Button variant="ghost" size="sm" className="rounded-md" onClick={() => {
                    if (linkingState.phase === 'group-select') {
                      setLinkingState({ phase: 'confirming', sourceIndex: linkingState.sourceIndex, targetIndex: linkingState.targetIndex, myAmount: linkingState.myAmount, otherAmount: linkingState.otherAmount })
                    }
                  }}>← {t('common.back')}</Button>
                  <Button variant="ghost" size="sm" className="rounded-md" onClick={cancel}>{t('common.cancel')}</Button>
                </div>
              </>
            )}
          </div>
        </DialogContent>
      </Dialog>
    )
  }

  function renderBudgetCell(row: RowState, i: number) {
    const availableBudgets = budgets.filter(
      b => !row.budgetAssignments.some(a => a.budgetId === b.id),
    )

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
        {availableBudgets.length > 0 && (
          <Button
            variant="ghost"
            size="icon-xs"
            className="rounded-sm border border-dashed border-border hover:border-foreground/40"
            onClick={() => setBudgetModal({ rowIndex: i, budgetId: '', amount: '' })}
            title={t('budgets.assign')}
          >
            <Wallet />
          </Button>
        )}
      </div>
    )
  }

  function renderBudgetModal() {
    if (!budgetModal) return null
    const row = rows[budgetModal.rowIndex]
    const tx = row.original
    const availableBudgets = budgets.filter(
      b => !row.budgetAssignments.some(a => a.budgetId === b.id),
    )

    return (
      <Dialog open onOpenChange={open => { if (!open) setBudgetModal(null) }}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>{t('budgets.assign')}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex items-center justify-between text-sm border rounded-md px-3 py-2 bg-muted/30">
              <span className="text-muted-foreground truncate mr-4">{tx.counterpartyName ?? tx.purpose ?? '—'}</span>
              <span className={`font-mono shrink-0 ${tx.amount >= 0 ? 'text-green-400' : ''}`}>{EUR.format(tx.amount)}</span>
            </div>
            <Select
              value={budgetModal.budgetId}
              onValueChange={value => setBudgetModal(prev => prev ? { ...prev, budgetId: value ?? '' } : null)}
            >
              <SelectTrigger className="w-full rounded-md">
                <SelectValue placeholder="—">
                  {availableBudgets.find(b => String(b.id) === budgetModal.budgetId)?.name}
                </SelectValue>
              </SelectTrigger>
              <SelectContent>
                {availableBudgets.map(b => (
                  <SelectItem key={b.id} value={String(b.id)}>{b.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Input
              className="rounded-md font-mono text-sm"
              type="number"
              step="0.01"
              min="0"
              placeholder={t('budgets.partialAmount')}
              value={budgetModal.amount}
              onChange={e => setBudgetModal(prev => prev ? { ...prev, amount: e.target.value } : null)}
            />
            <div className="flex gap-2 justify-end">
              <Button variant="ghost" size="sm" className="rounded-md" onClick={() => setBudgetModal(null)}>
                {t('common.cancel')}
              </Button>
              <Button
                variant="default"
                size="sm"
                className="rounded-md"
                onClick={confirmBudgetAssign}
                disabled={!budgetModal.budgetId}
              >
                {t('common.confirm')}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
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
        }
        return next
      })
      setCollectionModal(null)
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
        }
        return next
      })
      setCollectionModal(null)
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
        <Button
          variant="ghost"
          size="icon-xs"
          className="rounded-sm border border-dashed border-border hover:border-foreground/40"
          onClick={() => setCollectionModal({ rowIndex: i, collectionId: '', newName: '' })}
          title={t('collections.addTransaction')}
        >
          <Layers />
        </Button>
      </div>
    )
  }

  function renderCollectionModal() {
    if (!collectionModal) return null
    const row = rows[collectionModal.rowIndex]
    const tx = row.original
    const alreadyIn = new Set(row.collections.map(c => c.id))
    const available = allCollections.filter(c => !alreadyIn.has(c.id))

    return (
      <Dialog open onOpenChange={open => { if (!open) setCollectionModal(null) }}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>{t('collections.addTransaction')}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex items-center justify-between text-sm border rounded-md px-3 py-2 bg-muted/30">
              <span className="text-muted-foreground truncate mr-4">{tx.counterpartyName ?? tx.purpose ?? '—'}</span>
              <span className={`font-mono shrink-0 ${tx.amount >= 0 ? 'text-green-400' : ''}`}>{EUR.format(tx.amount)}</span>
            </div>
            <Select
              value={collectionModal.collectionId}
              onValueChange={value => setCollectionModal(prev => prev ? { ...prev, collectionId: value ?? '', newName: '' } : null)}
            >
              <SelectTrigger className="w-full rounded-md">
                <SelectValue placeholder="—">
                  {collectionModal.collectionId === '__new__'
                    ? `+ ${t('collections.createCollection')}`
                    : available.find(c => String(c.id) === collectionModal.collectionId)?.name}
                </SelectValue>
              </SelectTrigger>
              <SelectContent>
                {available.map(c => (
                  <SelectItem key={c.id} value={String(c.id)}>{c.name}</SelectItem>
                ))}
                <SelectItem value="__new__">+ {t('collections.createCollection')}</SelectItem>
              </SelectContent>
            </Select>
            {collectionModal.collectionId === '__new__' && (
              <Input
                className="rounded-md text-sm"
                type="text"
                placeholder={t('collections.namePlaceholder')}
                value={collectionModal.newName}
                autoFocus
                onChange={e => setCollectionModal(prev => prev ? { ...prev, newName: e.target.value } : null)}
                onKeyDown={e => {
                  if (e.key === 'Enter' && collectionModal.newName.trim()) {
                    createAndAddToCollection(collectionModal.rowIndex, collectionModal.newName)
                  }
                }}
              />
            )}
            <div className="flex gap-2 justify-end">
              <Button variant="ghost" size="sm" className="rounded-md" onClick={() => setCollectionModal(null)}>
                {t('common.cancel')}
              </Button>
              <Button
                variant="default"
                size="sm"
                className="rounded-md"
                disabled={!collectionModal.collectionId || (collectionModal.collectionId === '__new__' && !collectionModal.newName.trim())}
                onClick={() => {
                  if (collectionModal.collectionId === '__new__') {
                    createAndAddToCollection(collectionModal.rowIndex, collectionModal.newName)
                  } else {
                    const col = allCollections.find(c => c.id === Number(collectionModal.collectionId))
                    if (col) addToCollection(collectionModal.rowIndex, col.id, col.name)
                  }
                }}
              >
                {t('common.confirm')}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    )
  }

  function renderRowBadge(row: RowState): ReactNode {
    const tx = row.original
    if (tx.isVirtual && tx.parentId == null)
      return <span title={t('transactions.merge.virtualBadge')}><Package size={11} style={{ color: '#34d399' }} /></span>
    if (tx.isVirtual && tx.parentId != null)
      return <span title={t('transactions.split.virtualBadge')}><Scissors size={11} style={{ color: '#60a5fa' }} /></span>
    if (!tx.isVirtual && tx.excluded && tx.parentId != null)
      return <span title={t('transactions.merge.mergedBadge')}><Package size={11} style={{ color: '#6ee7b7' }} /></span>
    if (!tx.isVirtual && tx.excluded && tx.parentId == null)
      return <span title={t('transactions.split.splitBadge')}><Scissors size={11} style={{ color: '#93c5fd' }} /></span>
    return null
  }

  function renderGhostCell(col: ColumnKey, tx: TransactionItem): ReactNode {
    switch (col) {
      case 'date':
        return <TableCell key={col} className="txn-cell-date px-3 py-1">{formatDate(tx.accountingDate)}</TableCell>
      case 'account':
        return <TableCell key={col} className="txnv-cell-account px-3 py-1">{accountMap.get(tx.accountIban) ?? tx.accountIban}</TableCell>
      case 'amount':
        return (
          <TableCell key={col} className={`txn-cell-amount txnv-col-amount px-3 py-1 text-right${tx.amount < 0 ? ' negative' : ' positive'}`}>
            {EUR.format(tx.amount)}
          </TableCell>
        )
      case 'category':
        return <TableCell key={col} className="px-3 py-1"><span className="ri-cat-input" style={{ display: 'inline-block' }}>{tx.category ?? ''}</span></TableCell>
      case 'counterparty':
        return (
          <TableCell key={col} className="txnv-cell-counterparty px-3 py-1">
            <span className="txnv-counterparty-name">{tx.counterpartyName ?? ''}</span>
            {tx.purpose && <span className="txnv-counterparty-purpose" title={tx.purpose}>{tx.purpose}</span>}
          </TableCell>
        )
      case 'purpose':
        return <TableCell key={col} className="txnv-cell-purpose px-3 py-1"><span className="txnv-purpose-text">{tx.purpose ?? ''}</span></TableCell>
      case 'comment':
        return <TableCell key={col} className="txnv-cell-comment px-3 py-1"><span style={{ color: 'inherit', fontSize: 12 }}>{tx.comment ?? ''}</span></TableCell>
      default:
        return <TableCell key={col} />
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
      <TableHead
        key={col}
        className={`${thClass} sticky top-0 bg-[var(--surface)] z-10 text-muted-foreground h-10 px-3`}
        draggable={true}
        onDragStart={() => setDragCol(col)}
        onDragEnd={() => { setDragCol(null); setDragOverCol(null) }}
        onDragOver={e => { e.preventDefault(); setDragOverCol(col) }}
        onDragEnter={e => { e.preventDefault(); setDragOverCol(col) }}
        onDragLeave={() => setDragOverCol(null)}
        onDrop={() => handleDrop(col)}
      >
        {label}
      </TableHead>
    )
  }

  function renderCell(col: ColumnKey, row: RowState, i: number, rowLinkColor: string | null) {
    switch (col) {
      case 'date':
        return (
          <TableCell
            key={col}
            className="txn-cell-date px-3 py-1"
            style={rowLinkColor ? { boxShadow: `inset 3px 0 0 0 ${rowLinkColor}`, paddingLeft: '9px' } : undefined}
          >
            <div className="flex items-center gap-2 flex-wrap">
              <div className="flex items-center gap-1">
                {row.original.accountingDate !== row.original.bookingDate && <CalendarDays size={10} className="shrink-0 text-muted-foreground/50" />}
                <DatePicker
                  value={parseIso(row.accountingDate)}
                  onChange={d => {
                    if (!d) return
                    const iso = isoDate(d)
                    updateRow(i, 'accountingDate', iso)
                    saveAccountingDate(i, iso)
                  }}
                  disabled={row.savingAccountingDate}
                  className="h-auto border-0 border-b border-transparent hover:border-foreground/30 rounded-none px-0 text-xs font-mono hover:bg-transparent gap-1 min-w-0 w-[100px]"
                  hideIcon
                />
              </div>
              {row.original.accountingDate !== row.original.bookingDate && (
                <div className="flex items-center gap-1 font-mono text-[10px] text-muted-foreground" title={t('transactions.bookingDateTitle')}>
                  <CalendarClock size={10} className="shrink-0 opacity-50" />
                  {formatDate(row.original.bookingDate)}
                </div>
              )}
            </div>
          </TableCell>
        )
      case 'account':
        return (
          <TableCell key={col} className="txnv-cell-account px-3 py-1">
            {accountMap.get(row.original.accountIban) ?? row.original.accountIban}
          </TableCell>
        )
      case 'amount':
        return (
          <TableCell key={col} className={`txn-cell-amount txnv-col-amount px-3 py-1 text-right${row.original.amount < 0 ? ' negative' : ' positive'}`}>
            {EUR.format(row.original.amount)}
          </TableCell>
        )
      case 'category':
        return (
          <TableCell key={col} className="px-1 py-1">
            <CategoryPathInput
              className="ri-cat-input"
              value={row.original.categoryId ?? null}
              onChange={id => { void handleCategoryChange(i, id) }}
              tree={categories}
              onCategoryCreated={onCategoryCreated}
            />
          </TableCell>
        )
      case 'offsets':
        return (
          <TableCell key={col} className="txnv-cell-offsets px-3 py-1">
            {renderOffsetCell(row, i)}
          </TableCell>
        )
      case 'budget':
        return (
          <TableCell key={col} className="txnv-cell-budget px-3 py-1">
            {renderBudgetCell(row, i)}
          </TableCell>
        )
      case 'collection':
        return (
          <TableCell key={col} className="txnv-cell-budget px-3 py-1">
            {renderCollectionCell(row, i)}
          </TableCell>
        )
      case 'counterparty':
        return (
          <TableCell key={col} className="txnv-cell-counterparty px-3 py-1">
            <span
              className="txnv-counterparty-name"
              title={row.original.counterpartyIban ?? undefined}
            >
              {row.original.counterpartyName ?? ''}
            </span>
            {row.original.purpose && (
              <span className="txnv-counterparty-purpose" title={row.original.purpose}>
                {row.original.purpose}
              </span>
            )}
          </TableCell>
        )
      case 'purpose':
        return (
          <TableCell key={col} className="txnv-cell-purpose px-3 py-1" title={row.original.purpose ?? undefined}>
            <span className="txnv-purpose-text">{row.original.purpose ?? ''}</span>
          </TableCell>
        )
      case 'comment':
        return (
          <TableCell key={col} className="txnv-cell-comment px-3 py-1">
            <CommentInput
              value={row.comment}
              placeholder={t('transactions.addComment')}
              disabled={row.savingComment}
              className="h-6 w-full border-0 border-b border-transparent rounded-none bg-transparent px-0 focus-visible:ring-0 hover:border-foreground/30 focus-visible:border-foreground/60 shadow-none placeholder:italic placeholder:text-muted-foreground"
              style={{ fontSize: 12 }}
              onChange={v => updateRow(i, 'comment', v)}
              onSave={() => saveComment(i)}
            />
          </TableCell>
        )
    }
  }

  function renderMobileCard(item: DisplayItem): ReactNode {
    if (item.type === 'ghost') {
      const tx = item.parentTx
      return (
        <div key={`ghost-${item.parentId}`} className="txn-card txn-card--ghost txn-card--split-ghost">
          <div className="txn-card-header">
            <div className="txn-card-header-left">
              <span title={t('transactions.split.splitBadge')}><Scissors size={11} style={{ color: '#60a5fa' }} /></span>
              <span className="txn-card-date">{formatDate(tx.accountingDate)}</span>
            </div>
            <span className={`txn-card-amount ${tx.amount < 0 ? 'negative' : 'positive'}`}>{EUR.format(tx.amount)}</span>
          </div>
          {(tx.counterpartyName ?? tx.purpose) && (
            <div className="txn-card-body">
              <div className="txn-card-counterparty">{tx.counterpartyName ?? tx.purpose}</div>
              {tx.category && <div className="txn-card-purpose">{tx.category}{tx.subcategory ? ` › ${tx.subcategory}` : ''}</div>}
            </div>
          )}
        </div>
      )
    }

    if (item.type === 'merge-child-ghost') {
      const tx = item.childTx
      return (
        <div key={`merge-child-${tx.id}`} className="txn-card txn-card--ghost txn-card--merge-ghost">
          <div className="txn-card-header">
            <div className="txn-card-header-left">
              <span title={t('transactions.merge.mergedBadge')}><Package size={11} style={{ color: '#6ee7b7' }} /></span>
              <span className="txn-card-date">{formatDate(tx.accountingDate)}</span>
            </div>
            <span className={`txn-card-amount ${tx.amount < 0 ? 'negative' : 'positive'}`}>{EUR.format(tx.amount)}</span>
          </div>
          {(tx.counterpartyName ?? tx.purpose) && (
            <div className="txn-card-body">
              <div className="txn-card-counterparty">{tx.counterpartyName ?? tx.purpose}</div>
            </div>
          )}
        </div>
      )
    }

    const { row, i } = item
    const hasChips = row.budgetAssignments.length > 0 || row.collections.length > 0 || row.original.groups.length > 0

    return (
      <div key={row.original.id} className={`txn-card ${rowClassName(row, i)}`}>
        <div className="txn-card-header">
          <div className="txn-card-header-left">
            <Checkbox checked={row.selected} onCheckedChange={() => toggleSelect(i)} />
            {renderRowBadge(row)}
            <div className="flex items-center gap-2 flex-wrap">
              <div className="flex items-center gap-1">
                {row.original.accountingDate !== row.original.bookingDate && <CalendarDays size={10} className="shrink-0 text-muted-foreground/50" />}
                <DatePicker
                  value={parseIso(row.accountingDate)}
                  onChange={d => {
                    if (!d) return
                    const iso = isoDate(d)
                    updateRow(i, 'accountingDate', iso)
                    saveAccountingDate(i, iso)
                  }}
                  disabled={row.savingAccountingDate}
                  className="h-auto border-0 border-b border-transparent hover:border-foreground/30 rounded-none px-0 text-xs font-mono hover:bg-transparent gap-1 min-w-0 w-[90px]"
                  hideIcon
                />
              </div>
              {row.original.accountingDate !== row.original.bookingDate && (
                <div className="flex items-center gap-1 font-mono text-[10px] text-muted-foreground" title={t('transactions.bookingDateTitle')}>
                  <CalendarClock size={10} className="shrink-0 opacity-50" />
                  {formatDate(row.original.bookingDate)}
                </div>
              )}
            </div>
          </div>
          <span className={`txn-card-amount font-mono ${row.original.amount < 0 ? 'negative' : 'positive'}`}>
            {EUR.format(row.original.amount)}
          </span>
        </div>
        <div className="txn-card-body">
          {row.original.counterpartyName && (
            <div className="txn-card-counterparty">{row.original.counterpartyName}</div>
          )}
          <CategoryPathInput
            className="ri-cat-input"
            value={row.original.categoryId ?? null}
            onChange={id => { void handleCategoryChange(i, id) }}
            tree={categories}
            onCategoryCreated={onCategoryCreated}
          />
          {row.original.purpose && (
            <div className="txn-card-purpose">{row.original.purpose}</div>
          )}
        </div>
        {hasChips && (
          <div className="txn-card-chips">
            {row.original.groups.map(group => {
              const colorIdx = groupColorMap.get(group.id)
              const chipColor = colorIdx !== undefined ? LINK_COLORS[colorIdx] : undefined
              return (
                <span
                  key={group.id}
                  className="txnv-link-chip txnv-group-chip"
                  style={chipColor ? { borderColor: chipColor } : undefined}
                  onClick={() => {
                    setGroupModal({ groupId: group.id, group: null })
                    fetchLinkedGroup(group.id).then(g => setGroupModal({ groupId: group.id, group: g }))
                  }}
                >
                  {group.name ?? `#${group.id}`}
                  <button
                    className="txnv-link-chip-remove"
                    onClick={e => { e.stopPropagation(); void removeFromGroup(i, group.id) }}
                    title={t('transactions.removeFromGroup')}
                  >×</button>
                </span>
              )
            })}
            {row.budgetAssignments.map((a, bi) => (
              <span
                key={a.linkId}
                className="txnv-budget-chip"
                style={{ borderColor: BUDGET_COLORS[bi % BUDGET_COLORS.length] }}
              >
                <span className="txnv-budget-chip-name">{a.budgetName}</span>
                {a.amount != null && <span className="txnv-budget-chip-amt">{EUR.format(a.amount)}</span>}
                <button className="txnv-link-chip-remove" onClick={() => removeBudgetAssign(i, a.linkId)} title={t('budgets.remove')}>×</button>
              </span>
            ))}
            {row.collections.map((c, ci) => (
              <span
                key={c.id}
                className="txnv-budget-chip"
                style={{ borderColor: BUDGET_COLORS[ci % BUDGET_COLORS.length] }}
              >
                <span className="txnv-budget-chip-name">{c.name}</span>
                <button className="txnv-link-chip-remove" onClick={() => removeFromCollection(i, c.id)} title={t('collections.removeTransaction')}>×</button>
              </span>
            ))}
          </div>
        )}
        <div className="txn-card-footer">
          <CommentInput
            value={row.comment}
            placeholder={t('transactions.addComment')}
            disabled={row.savingComment}
            className="h-6 flex-1 min-w-0 border-0 border-b border-transparent rounded-none bg-transparent px-0 focus-visible:ring-0 hover:border-foreground/30 focus-visible:border-foreground/60 shadow-none placeholder:italic placeholder:text-muted-foreground"
            style={{ fontSize: 12 }}
            onChange={v => updateRow(i, 'comment', v)}
            onSave={() => saveComment(i)}
          />
          {row.error && <span className="txnv-row-error">{row.error}</span>}
          {row.saving && <span className="text-xs text-muted-foreground">…</span>}
          {(() => {
            const src = linkingState?.sourceIndex
            const isSource = src === i
            const isSelecting = linkingState?.phase === 'selecting'
            if (isSource) {
              return (
                <>
                  <span className="txnv-linking-badge">{t('transactions.linkingSelectingTarget')}</span>
                  <button className="txnv-link-chip-remove" onClick={() => { setLinkingState(null); setLinkError(null) }}>×</button>
                </>
              )
            }
            if (isSelecting) {
              return (
                <Button
                  variant="outline"
                  size="xs"
                  className="rounded-sm font-mono text-[10px] border-green-500/30 text-green-400 hover:border-green-400 hover:text-green-300 hover:bg-green-400/10"
                  onClick={() => src !== undefined && setLinkingState({ phase: 'confirming', sourceIndex: src, targetIndex: i, myAmount: '', otherAmount: '' })}
                >
                  {t('transactions.linkHere')}
                </Button>
              )
            }
            return (
              <Button
                variant="ghost"
                size="icon-xs"
                className="rounded-sm border border-dashed border-border hover:border-foreground/40"
                onClick={() => { setLinkError(null); setLinkingState({ phase: 'selecting', sourceIndex: i }) }}
                title={t('transactions.linkToTransaction')}
              >
                <Link2 />
              </Button>
            )
          })()}
          {(() => {
            const availableBudgets = budgets.filter(b => !row.budgetAssignments.some(a => a.budgetId === b.id))
            return availableBudgets.length > 0 ? (
              <Button
                variant="ghost"
                size="icon-xs"
                className="rounded-sm border border-dashed border-border hover:border-foreground/40"
                onClick={() => setBudgetModal({ rowIndex: i, budgetId: '', amount: '' })}
                title={t('budgets.assign')}
              >
                <Wallet />
              </Button>
            ) : null
          })()}
          <Button
            variant="ghost"
            size="icon-xs"
            className="rounded-sm border border-dashed border-border hover:border-foreground/40"
            onClick={() => setCollectionModal({ rowIndex: i, collectionId: '', newName: '' })}
            title={t('collections.addTransaction')}
          >
            <Layers />
          </Button>
          {!row.original.isVirtual && !row.original.excluded && row.original.parentId == null && (
            <Button
              variant="ghost"
              size="icon-xs"
              className="rounded-sm text-blue-400 border border-blue-400/30 hover:bg-blue-400/10 hover:text-blue-300"
              onClick={() => setSplitModalTx(row.original)}
              title={t('transactions.split.button')}
            >
              ÷
            </Button>
          )}
        </div>
      </div>
    )
  }

  const filterBtn = (active: boolean) =>
    `rounded-md border px-3 py-1.5 text-sm transition-colors ${active ? 'bg-primary text-primary-foreground border-transparent' : 'border-input bg-input/30 hover:bg-input/50'}`

  return (
    <div className="flex flex-col h-full">
      <div className="flex flex-wrap items-center gap-2 px-4 py-2 shrink-0">
        <button
          className="rounded-lg border border-input bg-input/30 px-3 py-1.5 text-sm hover:bg-input/50 transition-colors"
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
            className="w-64"
          />
        )}
        <button
          className={filterBtn(filterUncategorized)}
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
        <div className="flex gap-1">
          {(['all', 'income', 'expenses'] as const).map(type => (
            <button
              key={type}
              className={filterBtn(filterType === type)}
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
          <span className="ml-auto text-sm text-muted-foreground">
            {t('transactions.count', { count: filteredRows.length })} · {EUR.format(filteredSum)}
          </span>
        )}
      </div>

      <div className="flex-1 flex flex-col overflow-hidden pl-4 pr-4 pb-4">
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
          <>
          <div className="txn-table-wrapper rounded-lg border border-border overflow-auto flex-1 min-h-0">
          <table className="min-w-full caption-bottom text-xs border-collapse">
            <TableHeader className="[&_tr]:border-b">
              <TableRow className="hover:bg-transparent border-b">
                <TableHead className="txnv-col-check h-10 px-2 sticky top-0 bg-[var(--surface)] z-10">
                  <Checkbox
                    checked={allSelected}
                    indeterminate={selectedCount > 0 && !allSelected}
                    onCheckedChange={toggleSelectAll}
                  />
                </TableHead>
                <TableHead className="txnv-col-badge sticky top-0 bg-[var(--surface)] z-10" />
                {colOrder.map(col => renderColumnHeader(col))}
                <TableHead className="sticky top-0 bg-[var(--surface)] z-10 px-3 h-10" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {displayItems.map(item => {
                if (item.type === 'ghost') {
                  return (
                    <TableRow key={`ghost-${item.parentId}`} className="txnv-row--parent-ghost">
                      <TableCell className="txnv-col-check p-0" />
                      <TableCell className="txnv-col-badge"><span title={t('transactions.split.splitBadge')}><Scissors size={11} style={{ color: '#60a5fa' }} /></span></TableCell>
                      {colOrder.map(col => renderGhostCell(col, item.parentTx))}
                      <TableCell className="txnv-cell-actions" />
                    </TableRow>
                  )
                }
                if (item.type === 'merge-child-ghost') {
                  return (
                    <TableRow key={`merge-child-${item.childTx.id}`} className="txnv-row--merge-child-ghost">
                      <TableCell className="txnv-col-check p-0" />
                      <TableCell className="txnv-col-badge"><span title={t('transactions.merge.mergedBadge')}><Package size={11} style={{ color: '#6ee7b7' }} /></span></TableCell>
                      {colOrder.map(col => renderGhostCell(col, item.childTx))}
                      <TableCell className="txnv-cell-actions" />
                    </TableRow>
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
                  <TableRow key={row.original.id} className={rowClassName(row, i)} data-txid={row.original.id}>
                    <TableCell className="txnv-col-check p-0">
                      <Checkbox
                        checked={row.selected}
                        onCheckedChange={() => toggleSelect(i)}
                      />
                    </TableCell>
                    <TableCell className="txnv-col-badge">{renderRowBadge(row)}</TableCell>
                    {colOrder.map(col => renderCell(col, row, i, rowLinkColor))}
                    <TableCell className="txnv-cell-actions">
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
                              ×
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
                            ×
                          </span>
                        )
                      })()}
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
                          ×
                        </span>
                      )}
                      {!row.original.isVirtual && !row.original.excluded && row.original.parentId == null && (
                        <Button
                          variant="ghost"
                          size="icon-xs"
                          className="rounded-sm text-blue-400 border border-blue-400/30 hover:bg-blue-400/10 hover:text-blue-300"
                          onClick={() => setSplitModalTx(row.original)}
                          title={t('transactions.split.button')}
                        >
                          ÷
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </table>
          </div>
          <div className="txn-cards-mobile">
            {displayItems.slice(0, visibleCount).map(item => renderMobileCard(item))}
            <div ref={sentinelRef} />
          </div>
          </>
        )}
      </div>

      {renderLinkModal()}
      {renderGroupModal()}
      {renderBudgetModal()}
      {renderCollectionModal()}
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
        <div
          className="fixed bottom-0 right-0 flex items-center gap-2 border-t bg-popover px-4 py-3 shadow-lg z-40 transition-[left] duration-200"
          style={{ left: sidebarIsMobile ? 0 : sidebarOpen ? 'var(--sidebar-width)' : 'var(--sidebar-width-icon)' }}
        >
          <span className="text-sm font-medium">{t('transactions.bulkSelected', { count: selectedCount })}</span>
          <CategoryPathInput
            className="ri-cat-input"
            value={bulkCategoryId}
            onChange={id => setBulkCategoryId(id)}
            tree={categories}
            onCategoryCreated={onCategoryCreated}
            placeholder={t('common.category')}
          />
          <button
            className="rounded-lg border border-input bg-primary text-primary-foreground px-3 py-1.5 text-sm disabled:opacity-50"
            onClick={applyBulk}
            disabled={bulkApplying}
          >
            {bulkApplying ? '…' : t('transactions.applyBulk')}
          </button>
          <button
            className="rounded-lg bg-green-600 text-white px-3 py-1.5 text-sm disabled:opacity-50"
            onClick={() => { const selected = rows.filter(r => r.selected).map(r => r.original); setMergeModalTxs(selected) }}
            disabled={bulkApplying || selectedCount < 2}
          >
            {t('transactions.merge.button')}
          </button>
          <button className="ml-auto rounded-lg border px-3 py-1.5 text-sm" onClick={clearSelection} disabled={bulkApplying}>✕</button>
        </div>
      )}
    </div>
  )
}
