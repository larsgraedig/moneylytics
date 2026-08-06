import { useState, useEffect, useMemo, useRef, Fragment } from 'react'
import { useTranslation } from 'react-i18next'
import type { CategoryNode, CategoryStatItem, CategoryMergeItem } from '../api/rawImport'
import {
  deleteCategory, fetchCategoryStats, moveCategory, renameCategory,
  mergeCategories, fetchCategoryMerges, revertMerge, findOrCreateCategory,
} from '../api/rawImport'
import { CategoryPathInput } from './CategoryPathInput'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'

interface Props {
  categories: CategoryNode[]
  from: string
  to: string
  accountId?: number
  onCategoryDeleted: () => void
  onCategoryMoved: () => void
  onCategoryRenamed: () => void
  onCategoryMerged: () => void
  onCategoryCreated: () => void
}

type DragTarget =
  | { kind: 'row'; id: number }
  | { kind: 'gap'; parentId: number | null; gapKey: string }
  | null

interface DropGapProps {
  parentId: number | null
  gapKey: string
  depth: number
  dragTarget: DragTarget
  draggedId: React.MutableRefObject<number | null>
  setDragTarget: (t: DragTarget) => void
  onDrop: (parentId: number | null) => void
}

function DropGap({ parentId, gapKey, depth, dragTarget, draggedId, setDragTarget, onDrop }: DropGapProps) {
  const isOver = dragTarget?.kind === 'gap' && dragTarget.gapKey === gapKey
  return (
    <div
      className={`cat-drop-gap${isOver ? ' cat-drop-gap--active' : ''}`}
      style={{ marginLeft: `${20 + depth * 20}px` }}
      onDragOver={e => {
        e.preventDefault()
        e.stopPropagation()
        setDragTarget({ kind: 'gap', parentId, gapKey })
      }}
      onDragLeave={e => {
        e.stopPropagation()
        setDragTarget(null)
      }}
      onDrop={e => {
        e.preventDefault()
        e.stopPropagation()
        setDragTarget(null)
        if (draggedId.current !== null) onDrop(parentId)
      }}
    />
  )
}

interface RowProps {
  node: CategoryNode
  depth: number
  expanded: Set<number>
  onToggle: (id: number) => void
  subtreeTotals: Map<number, number>
  subtreePeriod: Map<number, number>
  search: string
  visibleIds: Set<number>
  isSearchActive: boolean
  deletingId: number | null
  onDelete: (id: number) => void
  editingId: number | null
  onStartEdit: (id: number, currentName: string) => void
  onStartMerge: (node: CategoryNode) => void
  creatingParentId: number | null | 'root'
  onStartCreate: (parentId: number | null, depth: number) => void
  onCreateConfirm: (name: string) => void
  onCreateCancel: () => void
  draggedId: React.MutableRefObject<number | null>
  dragTarget: DragTarget
  setDragTarget: (t: DragTarget) => void
  onDrop: (targetParentId: number | null) => void
}

function buildSubtreeCounts(
  nodes: CategoryNode[],
  flat: Map<number, CategoryStatItem>,
  totalMap: Map<number, number>,
  periodMap: Map<number, number>,
) {
  for (const node of nodes) {
    buildSubtreeCounts(node.children, flat, totalMap, periodMap)
    const own = flat.get(node.id)
    const childTotal = node.children.reduce((s, c) => s + (totalMap.get(c.id) ?? 0), 0)
    const childPeriod = node.children.reduce((s, c) => s + (periodMap.get(c.id) ?? 0), 0)
    totalMap.set(node.id, (own?.totalCount ?? 0) + childTotal)
    periodMap.set(node.id, (own?.periodCount ?? 0) + childPeriod)
  }
}

function collectMatchingIds(nodes: CategoryNode[], query: string, result: Set<number>) {
  const q = query.toLowerCase()
  for (const node of nodes) {
    if (node.name.toLowerCase().includes(q)) result.add(node.id)
    collectMatchingIds(node.children, query, result)
  }
}

function collectVisibleIds(nodes: CategoryNode[], matchingIds: Set<number>, result: Set<number>): boolean {
  let anyVisible = false
  for (const node of nodes) {
    const childVisible = collectVisibleIds(node.children, matchingIds, result)
    if (matchingIds.has(node.id) || childVisible) {
      result.add(node.id)
      anyVisible = true
    }
  }
  return anyVisible
}

function highlightName(name: string, search: string) {
  if (!search) return <>{name}</>
  const idx = name.toLowerCase().indexOf(search.toLowerCase())
  if (idx === -1) return <>{name}</>
  return (
    <>
      {name.slice(0, idx)}
      <mark className="cat-highlight">{name.slice(idx, idx + search.length)}</mark>
      {name.slice(idx + search.length)}
    </>
  )
}

function buildParentPath(parentId: number | null, nodes: CategoryNode[], path: string[] = []): string[] {
  if (parentId === null) return []
  for (const node of nodes) {
    const current = [...path, node.name]
    if (node.id === parentId) return current
    const found = buildParentPath(parentId, node.children, current)
    if (found.length > 0) return found
  }
  return []
}

interface CreateInputProps {
  depth: number
  onConfirm: (name: string) => void
  onCancel: () => void
}

function CreateCategoryInput({ depth, onConfirm, onCancel }: CreateInputProps) {
  const [value, setValue] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)
  useEffect(() => { inputRef.current?.focus() }, [])
  return (
    <div className="cat-create-row" style={{ paddingLeft: `${20 + depth * 20}px` }}>
      <span className="cat-chevron" />
      <input
        ref={inputRef}
        className="cat-create-input"
        value={value}
        onChange={e => setValue(e.target.value)}
        onKeyDown={e => {
          if (e.key === 'Enter' && value.trim()) onConfirm(value.trim())
          if (e.key === 'Escape') onCancel()
        }}
        onBlur={onCancel}
        placeholder="Name…"
      />
    </div>
  )
}

function formatRelativeDate(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime()
  const days = Math.floor(diff / 86400000)
  if (days === 0) return 'heute'
  if (days === 1) return 'gestern'
  if (days < 7) return `vor ${days} Tagen`
  if (days < 30) return `vor ${Math.floor(days / 7)} Woche${Math.floor(days / 7) > 1 ? 'n' : ''}`
  if (days < 365) return `vor ${Math.floor(days / 30)} Monat${Math.floor(days / 30) > 1 ? 'en' : ''}`
  return `vor ${Math.floor(days / 365)} Jahr${Math.floor(days / 365) > 1 ? 'en' : ''}`
}

function CategoryRow({
  node, depth, expanded, onToggle,
  subtreeTotals, subtreePeriod,
  search, visibleIds, isSearchActive,
  deletingId, onDelete,
  editingId, onStartEdit, onStartMerge,
  creatingParentId, onStartCreate, onCreateConfirm, onCreateCancel,
  draggedId, dragTarget, setDragTarget, onDrop,
}: RowProps) {
  const visibleChildren = isSearchActive
    ? node.children.filter(c => visibleIds.has(c.id))
    : node.children

  const isExpanded = isSearchActive
    ? visibleChildren.length > 0
    : expanded.has(node.id)

  const hasChildren = node.children.length > 0
  const total = subtreeTotals.get(node.id) ?? 0
  const period = subtreePeriod.get(node.id) ?? 0
  const isDeletable = !hasChildren && total === 0
  const isDragging = draggedId.current === node.id
  const isDragOver = dragTarget?.kind === 'row' && dragTarget.id === node.id
  const isEditing = editingId === node.id

  const classNames = [
    'cat-row',
    hasChildren && !isSearchActive ? 'cat-row--clickable' : '',
    isDragging ? 'cat-row--dragging' : '',
    isDragOver ? 'cat-row--drag-over' : '',
  ].filter(Boolean).join(' ')

  const gapProps = { dragTarget, draggedId, setDragTarget, onDrop }

  return (
    <>
      <div
        className={classNames}
        style={{ paddingLeft: `${20 + depth * 20}px` }}
        onClick={() => !isSearchActive && hasChildren && onToggle(node.id)}
        draggable={!isSearchActive}
        onDragStart={e => {
          draggedId.current = node.id
          e.dataTransfer.effectAllowed = 'move'
        }}
        onDragEnd={() => {
          draggedId.current = null
          setDragTarget(null)
        }}
        onDragOver={e => {
          e.preventDefault()
          e.stopPropagation()
          if (draggedId.current !== node.id) setDragTarget({ kind: 'row', id: node.id })
        }}
        onDragLeave={e => {
          e.stopPropagation()
          setDragTarget(null)
        }}
        onDrop={e => {
          e.preventDefault()
          e.stopPropagation()
          setDragTarget(null)
          if (draggedId.current !== null && draggedId.current !== node.id) {
            onDrop(node.id)
          }
        }}
      >
        <span className="cat-drag-handle" aria-hidden>⠿</span>
        <span className="cat-chevron">
          {hasChildren ? (isExpanded ? '▾' : '▸') : ''}
        </span>
        <span className="cat-name">{isEditing ? null : highlightName(node.name, search)}</span>
        {(total > 0 || period > 0) && !isEditing && (
          <span className="cat-counts">
            <span className="cat-count-period" title="Im Zeitraum">{period}</span>
            <span className="cat-count-sep">/</span>
            <span className="cat-count-total" title="Gesamt">{total}</span>
          </span>
        )}
        {!isEditing && (
          <button
            className="cat-edit-btn"
            onClick={e => { e.stopPropagation(); onStartEdit(node.id, node.name) }}
            title="Umbenennen"
          >
            ✎
          </button>
        )}
        {!isEditing && (
          <button
            className="cat-merge-btn"
            onClick={e => { e.stopPropagation(); onStartMerge(node) }}
            title="Verschmelzen"
          >
            ⇌
          </button>
        )}
        {!isEditing && !isSearchActive && (
          <button
            className="cat-add-child-btn"
            onClick={e => { e.stopPropagation(); onStartCreate(node.id, depth + 1); if (!expanded.has(node.id)) onToggle(node.id) }}
            title="Kindkategorie anlegen"
          >
            +
          </button>
        )}
        {isDeletable && !isEditing && (
          <button
            className="cat-delete-btn"
            disabled={deletingId === node.id}
            onClick={e => { e.stopPropagation(); onDelete(node.id) }}
          >
            {deletingId === node.id ? '…' : '×'}
          </button>
        )}
      </div>
      {isExpanded && (
        <>
          <DropGap parentId={node.id} gapKey={`gap-${node.id}-start`} depth={depth + 1} {...gapProps} />
          {visibleChildren.map(child => (
            <Fragment key={child.id}>
              <CategoryRow
                node={child}
                depth={depth + 1}
                expanded={expanded}
                onToggle={onToggle}
                subtreeTotals={subtreeTotals}
                subtreePeriod={subtreePeriod}
                search={search}
                visibleIds={visibleIds}
                isSearchActive={isSearchActive}
                deletingId={deletingId}
                onDelete={onDelete}
                editingId={editingId}
                onStartEdit={onStartEdit}
                onStartMerge={onStartMerge}
                creatingParentId={creatingParentId}
                onStartCreate={onStartCreate}
                onCreateConfirm={onCreateConfirm}
                onCreateCancel={onCreateCancel}
                draggedId={draggedId}
                dragTarget={dragTarget}
                setDragTarget={setDragTarget}
                onDrop={onDrop}
              />
              <DropGap parentId={node.id} gapKey={`gap-${node.id}-after-${child.id}`} depth={depth + 1} {...gapProps} />
            </Fragment>
          ))}
          {creatingParentId === node.id && (
            <CreateCategoryInput depth={depth + 1} onConfirm={onCreateConfirm} onCancel={onCreateCancel} />
          )}
        </>
      )}
    </>
  )
}

export default function CategoriesPage({
  categories, from, to, accountId,
  onCategoryDeleted, onCategoryMoved, onCategoryRenamed, onCategoryMerged, onCategoryCreated,
}: Props) {
  const { t } = useTranslation()
  const [expanded, setExpanded] = useState<Set<number>>(new Set())
  const [stats, setStats] = useState<CategoryStatItem[]>([])
  const [search, setSearch] = useState('')
  const [deletingId, setDeletingId] = useState<number | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [moveError, setMoveError] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editValue, setEditValue] = useState('')
  const [renameError, setRenameError] = useState<string | null>(null)
  const [dragTarget, setDragTarget] = useState<DragTarget>(null)
  const draggedId = useRef<number | null>(null)
  const editInputRef = useRef<HTMLInputElement>(null)

  const [mergingNode, setMergingNode] = useState<CategoryNode | null>(null)
  const [mergeTargetId, setMergeTargetId] = useState<number | null>(null)
  const [mergeError, setMergeError] = useState<string | null>(null)
  const [merging, setMerging] = useState(false)

  const [creatingParentId, setCreatingParentId] = useState<number | null | 'root'>(null)
  const [createError, setCreateError] = useState<string | null>(null)

  const [merges, setMerges] = useState<CategoryMergeItem[]>([])
  const [historyOpen, setHistoryOpen] = useState(false)
  const [revertingId, setRevertingId] = useState<number | null>(null)
  const [revertError, setRevertError] = useState<string | null>(null)

  useEffect(() => {
    fetchCategoryStats(from, to, accountId).then(r => setStats(r.items)).catch(() => {})
  }, [from, to, accountId])

  useEffect(() => {
    fetchCategoryMerges().then(setMerges).catch(() => {})
  }, [])

  const { subtreeTotals, subtreePeriod } = useMemo(() => {
    const flat = new Map(stats.map(s => [s.categoryId, s]))
    const totalMap = new Map<number, number>()
    const periodMap = new Map<number, number>()
    buildSubtreeCounts(categories, flat, totalMap, periodMap)
    return { subtreeTotals: totalMap, subtreePeriod: periodMap }
  }, [stats, categories])

  const { visibleIds, isSearchActive } = useMemo(() => {
    const q = search.trim()
    if (!q) return { visibleIds: new Set<number>(), isSearchActive: false }
    const matching = new Set<number>()
    collectMatchingIds(categories, q, matching)
    const visible = new Set<number>()
    collectVisibleIds(categories, matching, visible)
    return { visibleIds: visible, isSearchActive: true }
  }, [search, categories])

  function toggle(id: number) {
    setExpanded(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  async function handleDelete(id: number) {
    setDeletingId(id)
    setDeleteError(null)
    try {
      await deleteCategory(id)
      onCategoryDeleted()
    } catch (e) {
      const reason = e instanceof Error ? e.message : 'UNKNOWN'
      const key = `kategorien.deleteError.${reason}`
      const msg = t(key, { defaultValue: t('kategorien.deleteError.UNKNOWN') })
      setDeleteError(msg)
    } finally {
      setDeletingId(null)
    }
  }

  function handleStartEdit(id: number, currentName: string) {
    setEditingId(id)
    setEditValue(currentName)
    setRenameError(null)
    setTimeout(() => editInputRef.current?.select(), 0)
  }

  function handleCancelEdit() {
    setEditingId(null)
    setEditValue('')
    setRenameError(null)
  }

  async function handleConfirmRename() {
    if (editingId === null) return
    const trimmed = editValue.trim()
    if (!trimmed) {
      setRenameError(t('kategorien.renameError.EMPTY_NAME'))
      return
    }
    const id = editingId
    setEditingId(null)
    setRenameError(null)
    try {
      await renameCategory(id, trimmed)
      onCategoryRenamed()
    } catch (e) {
      const reason = e instanceof Error ? e.message : 'UNKNOWN'
      const key = `kategorien.renameError.${reason}`
      const msg = t(key, { defaultValue: t('kategorien.renameError.UNKNOWN') })
      setRenameError(msg)
    }
  }

  async function handleDrop(targetParentId: number | null) {
    const id = draggedId.current
    if (id === null) return
    setMoveError(null)
    try {
      await moveCategory(id, targetParentId)
      onCategoryMoved()
    } catch (e) {
      const reason = e instanceof Error ? e.message : 'UNKNOWN'
      const key = `kategorien.moveError.${reason}`
      const msg = t(key, { defaultValue: t('kategorien.moveError.UNKNOWN') })
      setMoveError(msg)
    }
  }

  function handleStartCreate(parentId: number | null, _depth: number) {
    setCreatingParentId(parentId === null ? 'root' : parentId)
  }

  async function handleCreateConfirm(name: string) {
    setCreateError(null)
    const parentId = creatingParentId === 'root' ? null : (creatingParentId as number | null)
    const parentPath = buildParentPath(parentId, categories)
    const fullPath = [...parentPath, name]
    setCreatingParentId(null)
    try {
      await findOrCreateCategory(fullPath)
      onCategoryCreated()
    } catch {
      setCreateError(`Kategorie "${name}" konnte nicht angelegt werden.`)
    }
  }

  function handleCreateCancel() {
    setCreatingParentId(null)
    setCreateError(null)
  }

  async function handleMergeConfirm() {
    if (!mergingNode || mergeTargetId === null) return
    setMerging(true)
    setMergeError(null)
    try {
      const result = await mergeCategories(mergingNode.id, mergeTargetId)
      setMerges(prev => [result, ...prev])
      setHistoryOpen(true)
      setMergingNode(null)
      setMergeTargetId(null)
      onCategoryMerged()
    } catch (e) {
      const reason = e instanceof Error ? e.message : 'UNKNOWN'
      const key = `kategorien.mergeError.${reason}`
      const msg = t(key, { defaultValue: t('kategorien.mergeError.UNKNOWN') })
      setMergeError(msg)
    } finally {
      setMerging(false)
    }
  }

  async function handleRevert(mergeId: number) {
    setRevertingId(mergeId)
    setRevertError(null)
    try {
      await revertMerge(mergeId)
      setMerges(prev => prev.filter(m => m.id !== mergeId))
      onCategoryMerged()
    } catch (e) {
      const reason = e instanceof Error ? e.message : 'UNKNOWN'
      const key = `kategorien.revertError.${reason}`
      const msg = t(key, { defaultValue: t('kategorien.revertError.UNKNOWN') })
      setRevertError(msg)
    } finally {
      setRevertingId(null)
    }
  }

  const rootNodes = isSearchActive
    ? categories.filter(n => visibleIds.has(n.id))
    : categories

  const gapProps = { dragTarget, draggedId, setDragTarget, onDrop: handleDrop }

  const mergeTreeWithoutSource = useMemo(() => {
    if (!mergingNode) return categories
    function filter(nodes: CategoryNode[]): CategoryNode[] {
      return nodes
        .filter(n => n.id !== mergingNode!.id)
        .map(n => ({ ...n, children: filter(n.children) }))
    }
    return filter(categories)
  }, [categories, mergingNode])

  return (
    <div className="flex flex-col gap-3 p-6">
      <div className="flex items-center gap-2">
        <span className="text-base font-medium">{t('kategorien.title')}</span>
        <span className="inline-flex items-center justify-center rounded-full bg-muted px-2 py-0.5 text-xs">{categories.length}</span>
        <input
          className="ml-2 rounded-lg border border-input bg-input/30 px-3 py-1.5 text-sm outline-none focus:border-ring w-56"
          type="search"
          placeholder={t('kategorien.search')}
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
        {!isSearchActive && (
          <button className="ml-1 flex h-7 w-7 items-center justify-center rounded-lg border border-dashed border-input hover:border-foreground text-muted-foreground hover:text-foreground transition-colors" onClick={() => handleStartCreate(null, 0)} title="Neue Root-Kategorie">
            +
          </button>
        )}
      </div>
      {(deleteError || moveError || renameError || createError || revertError) && (
        <div className="flex flex-col gap-1">
          {deleteError && <p className="text-sm text-destructive">{deleteError}</p>}
          {moveError && <p className="text-sm text-destructive">{moveError}</p>}
          {renameError && <p className="text-sm text-destructive">{renameError}</p>}
          {createError && <p className="text-sm text-destructive">{createError}</p>}
          {revertError && <p className="text-sm text-destructive">{revertError}</p>}
        </div>
      )}

      {editingId !== null && (
        <Dialog open onOpenChange={open => { if (!open) handleCancelEdit() }}>
          <DialogContent className="max-w-sm">
            <DialogHeader><DialogTitle>{t('kategorien.rename')}</DialogTitle></DialogHeader>
            <div className="flex items-center gap-2">
              <Input
                ref={editInputRef}
                value={editValue}
                onChange={e => setEditValue(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') handleConfirmRename(); if (e.key === 'Escape') handleCancelEdit() }}
                autoFocus
              />
              <Button size="sm" onClick={handleConfirmRename}>✓</Button>
              <Button size="sm" variant="ghost" onClick={handleCancelEdit}>✕</Button>
            </div>
          </DialogContent>
        </Dialog>
      )}

      {mergingNode !== null && (
        <Dialog open onOpenChange={open => { if (!open) { setMergingNode(null); setMergeTargetId(null); setMergeError(null) } }}>
          <DialogContent className="max-w-sm">
            <DialogHeader><DialogTitle>{t('kategorien.mergeTitle', { name: mergingNode.name })}</DialogTitle></DialogHeader>
            <CategoryPathInput
              value={mergeTargetId}
              onChange={setMergeTargetId}
              tree={mergeTreeWithoutSource}
              allowCreate={false}
              placeholder="Zielkategorie wählen…"
            />
            <p className="text-sm text-muted-foreground">{t('kategorien.mergeHint')}</p>
            {mergeError && <p className="text-sm text-destructive">{mergeError}</p>}
            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={() => { setMergingNode(null); setMergeTargetId(null); setMergeError(null) }}>✕</Button>
              <Button disabled={mergeTargetId === null || merging} onClick={handleMergeConfirm}>
                {merging ? '…' : t('kategorien.mergeConfirm')}
              </Button>
            </div>
          </DialogContent>
        </Dialog>
      )}

      <div className="flex flex-col">
        {categories.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('kategorien.empty')}</p>
        ) : rootNodes.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('kategorien.noResults')}</p>
        ) : (
          <>
            <DropGap parentId={null} gapKey="gap-root-start" depth={0} {...gapProps} />
            {rootNodes.map(node => (
              <Fragment key={node.id}>
                <CategoryRow
                  node={node}
                  depth={0}
                  expanded={expanded}
                  onToggle={toggle}
                  subtreeTotals={subtreeTotals}
                  subtreePeriod={subtreePeriod}
                  search={search.trim()}
                  visibleIds={visibleIds}
                  isSearchActive={isSearchActive}
                  deletingId={deletingId}
                  onDelete={handleDelete}
                  editingId={editingId}
                  onStartEdit={handleStartEdit}
                  onStartMerge={setMergingNode}
                  creatingParentId={creatingParentId}
                  onStartCreate={handleStartCreate}
                  onCreateConfirm={handleCreateConfirm}
                  onCreateCancel={handleCreateCancel}
                  draggedId={draggedId}
                  dragTarget={dragTarget}
                  setDragTarget={setDragTarget}
                  onDrop={handleDrop}
                />
                <DropGap parentId={null} gapKey={`gap-root-after-${node.id}`} depth={0} {...gapProps} />
              </Fragment>
            ))}
            {creatingParentId === 'root' && (
              <CreateCategoryInput depth={0} onConfirm={handleCreateConfirm} onCancel={handleCreateCancel} />
            )}
          </>
        )}
      </div>

      {merges.length > 0 && (
        <div className="mt-4 border-t pt-4">
          <button className="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors" onClick={() => setHistoryOpen(o => !o)}>
            <span>{historyOpen ? '▾' : '▸'}</span>
            <span>{t('kategorien.mergeHistory')} ({merges.length})</span>
          </button>
          {historyOpen && (
            <div className="mt-2 flex flex-col gap-1.5">
              {merges.map(m => (
                <div key={m.id} className="flex items-center gap-3 text-sm">
                  <span className="flex items-center gap-1 text-muted-foreground">
                    <span>{m.sourceName}</span>
                    <span> → </span>
                    <span className="font-medium text-foreground">{m.targetName}</span>
                  </span>
                  <span className="flex items-center gap-2 text-xs text-muted-foreground">
                    {m.transactionCount > 0 && <span>{m.transactionCount} Tx</span>}
                    {m.childCount > 0 && <span>{m.childCount} Kinder</span>}
                    <span>{formatRelativeDate(m.mergedAt)}</span>
                  </span>
                  <button
                    className="ml-auto text-xs text-muted-foreground hover:text-foreground underline underline-offset-2 disabled:opacity-50"
                    disabled={revertingId === m.id}
                    onClick={() => handleRevert(m.id)}
                  >
                    {revertingId === m.id ? '…' : t('kategorien.mergeRevert')}
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
