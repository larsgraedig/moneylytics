import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { CategoryNode } from '../api/rawImport'

interface Props {
  categories: CategoryNode[]
}

interface RowProps {
  node: CategoryNode
  depth: number
  expanded: Set<number>
  onToggle: (id: number) => void
}

function CategoryRow({ node, depth, expanded, onToggle }: RowProps) {
  const isExpanded = expanded.has(node.id)
  const hasChildren = node.children.length > 0

  return (
    <>
      <div
        className={`cat-row${hasChildren ? ' cat-row--clickable' : ''}`}
        style={{ paddingLeft: `${20 + depth * 20}px` }}
        onClick={() => hasChildren && onToggle(node.id)}
      >
        <span className="cat-chevron">
          {hasChildren ? (isExpanded ? '▾' : '▸') : ''}
        </span>
        <span className="cat-name">{node.name}</span>
      </div>
      {isExpanded && node.children.map(child => (
        <CategoryRow
          key={child.id}
          node={child}
          depth={depth + 1}
          expanded={expanded}
          onToggle={onToggle}
        />
      ))}
    </>
  )
}

export default function CategoriesPage({ categories }: Props) {
  const { t } = useTranslation()
  const [expanded, setExpanded] = useState<Set<number>>(new Set())

  function toggle(id: number) {
    setExpanded(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  return (
    <div className="cat-page">
      <div className="cat-header">
        <span className="cat-title">{t('kategorien.title')}</span>
        <span className="cat-count">{categories.length}</span>
      </div>
      <div className="cat-tree">
        {categories.length === 0 ? (
          <p className="cat-empty">{t('kategorien.empty')}</p>
        ) : (
          categories.map(node => (
            <CategoryRow
              key={node.id}
              node={node}
              depth={0}
              expanded={expanded}
              onToggle={toggle}
            />
          ))
        )}
      </div>
    </div>
  )
}
