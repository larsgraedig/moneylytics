import { useState, useEffect, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import type { CategoryNode, CategoryStatItem } from '../api/rawImport'
import { deleteCategory, fetchCategoryStats } from '../api/rawImport'

interface Props {
  categories: CategoryNode[]
  from: string
  to: string
  iban?: string
  onCategoryDeleted: () => void
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

  return (
    <>
      <div
        className={`cat-row${hasChildren && !isSearchActive ? ' cat-row--clickable' : ''}`}
        style={{ paddingLeft: `${20 + depth * 20}px` }}
        onClick={() => !isSearchActive && hasChildren && onToggle(node.id)}
      >
        <span className="cat-chevron">
          {hasChildren ? (isExpanded ? '▾' : '▸') : ''}
        </span>
        <span className="cat-name">{highlightName(node.name, search)}</span>
        {(total > 0 || period > 0) && (
          <span className="cat-counts">
            <span className="cat-count-period" title="Im Zeitraum">{period}</span>
            <span className="cat-count-sep">/</span>
            <span className="cat-count-total" title="Gesamt">{total}</span>
          </span>
        )}
        {isDeletable && (
          <button
            className="cat-delete-btn"
            disabled={deletingId === node.id}
            onClick={e => { e.stopPropagation(); onDelete(node.id) }}
          >
            {deletingId === node.id ? '…' : '×'}
          </button>
        )}
      </div>
      {isExpanded && visibleChildren.map(child => (
        <CategoryRow
          key={child.id}
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
        />
      ))}
    </>
  )
}

export default function CategoriesPage({ categories, from, to, iban, onCategoryDeleted }: Props) {
  const { t } = useTranslation()
  const [expanded, setExpanded] = useState<Set<number>>(new Set())
  const [stats, setStats] = useState<CategoryStatItem[]>([])
  const [search, setSearch] = useState('')
  const [deletingId, setDeletingId] = useState<number | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)

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
    } catch {
      setDeleteError(t('kategorien.deleteError'))
    } finally {
      setDeletingId(null)
    }
  }

  const rootNodes = isSearchActive
    ? categories.filter(n => visibleIds.has(n.id))
    : categories

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
      <div className="cat-tree">
        {categories.length === 0 ? (
          <p className="cat-empty">{t('kategorien.empty')}</p>
        ) : rootNodes.length === 0 ? (
          <p className="cat-empty">{t('kategorien.noResults')}</p>
        ) : (
          rootNodes.map(node => (
            <CategoryRow
              key={node.id}
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
            />
          ))
        )}
      </div>
    </div>
  )
}
