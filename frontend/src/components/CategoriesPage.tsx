import { useState, useEffect, useMemo, useRef, Fragment } from 'react'
import { useTranslation } from 'react-i18next'
import type { CategoryNode, CategoryStatItem } from '../api/rawImport'
import { deleteCategory, fetchCategoryStats, moveCategory, renameCategory } from '../api/rawImport'

interface Props {
  categories: CategoryNode[]
  from: string
  to: string
  iban?: string
  onCategoryDeleted: () => void
  onCategoryMoved: () => void
  onCategoryRenamed: () => void
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

function CategoryRow({
  node, depth, expanded, onToggle,
  subtreeTotals, subtreePeriod,
  search, visibleIds, isSearchActive,
  deletingId, onDelete,
  editingId, onStartEdit,
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
                draggedId={draggedId}
                dragTarget={dragTarget}
                setDragTarget={setDragTarget}
                onDrop={onDrop}
              />
              <DropGap parentId={node.id} gapKey={`gap-${node.id}-after-${child.id}`} depth={depth + 1} {...gapProps} />
            </Fragment>
          ))}
        </>
      )}
    </>
  )
}

export default function CategoriesPage({ categories, from, to, iban, onCategoryDeleted, onCategoryMoved, onCategoryRenamed }: Props) {
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

  useEffect(() => {
    fetchCategoryStats(from, to, iban).then(r => setStats(r.items)).catch(() => {})
  }, [from, to, iban])

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

  const rootNodes = isSearchActive
    ? categories.filter(n => visibleIds.has(n.id))
    : categories

  const gapProps = { dragTarget, draggedId, setDragTarget, onDrop: handleDrop }

  return (
    <div className="cat-page">
      <div className="cat-header">
        <span className="cat-title">{t('kategorien.title')}</span>
        <span className="cat-badge">{categories.length}</span>
        <input
          className="cat-search"
          type="search"
          placeholder={t('kategorien.search')}
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
      </div>
      {deleteError && <p className="cat-error">{deleteError}</p>}
      {moveError && <p className="cat-error">{moveError}</p>}
      {renameError && <p className="cat-error">{renameError}</p>}
      {editingId !== null && (
        <div className="cat-edit-overlay" onClick={handleCancelEdit}>
          <div className="cat-edit-dialog" onClick={e => e.stopPropagation()}>
            <input
              ref={editInputRef}
              className="cat-edit-input"
              value={editValue}
              onChange={e => setEditValue(e.target.value)}
              onKeyDown={e => {
                if (e.key === 'Enter') handleConfirmRename()
                if (e.key === 'Escape') handleCancelEdit()
              }}
              autoFocus
            />
            <div className="cat-edit-actions">
              <button className="cat-edit-confirm" onClick={handleConfirmRename}>✓</button>
              <button className="cat-edit-cancel" onClick={handleCancelEdit}>✕</button>
            </div>
          </div>
        </div>
      )}
      <div className="cat-tree">
        {categories.length === 0 ? (
          <p className="cat-empty">{t('kategorien.empty')}</p>
        ) : rootNodes.length === 0 ? (
          <p className="cat-empty">{t('kategorien.noResults')}</p>
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
                  draggedId={draggedId}
                  dragTarget={dragTarget}
                  setDragTarget={setDragTarget}
                  onDrop={handleDrop}
                />
                <DropGap parentId={null} gapKey={`gap-root-after-${node.id}`} depth={0} {...gapProps} />
              </Fragment>
            ))}
          </>
        )}
      </div>
    </div>
  )
}
