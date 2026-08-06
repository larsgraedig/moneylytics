import { useEffect, useMemo, useRef, useState } from 'react'
import { findOrCreateCategory, type CategoryNode } from '../api/rawImport'
import { cn } from '@/lib/utils'

interface FlatItem {
  id: number
  pathStr: string
}

type BrowseItem = { type: 'existing'; id: number; displayName: string; pathStr: string; depth: number }
type CreateItem = { type: 'create'; displayStr: string; path: string[] }
type DropdownItem = BrowseItem | CreateItem

function flattenTree(nodes: CategoryNode[], prefix: string[] = []): FlatItem[] {
  return nodes.flatMap(node => {
    const path = [...prefix, node.name]
    return [{ id: node.id, pathStr: path.filter(Boolean).join(' > ') }, ...flattenTree(node.children, path)]
  })
}

function flattenTreeWithDepth(nodes: CategoryNode[], depth = 0, prefix: string[] = []): BrowseItem[] {
  return nodes.flatMap(node => {
    const path = [...prefix, node.name]
    return [
      { type: 'existing', id: node.id, displayName: node.name, pathStr: path.filter(Boolean).join(' > '), depth },
      ...flattenTreeWithDepth(node.children, depth + 1, path),
    ]
  })
}

function pathStringForId(id: number | null, nodes: CategoryNode[], prefix: string[] = []): string {
  if (id === null) return ''
  for (const node of nodes) {
    const path = [...prefix, node.name]
    if (node.id === id) return path.filter(Boolean).join(' > ')
    const found = pathStringForId(id, node.children, path)
    if (found) return found
  }
  return ''
}

interface Props {
  value: number | null
  onChange: (id: number | null) => void
  tree: CategoryNode[]
  onCategoryCreated?: (node: CategoryNode) => void
  placeholder?: string
  className?: string
  allowCreate?: boolean
}

export function CategoryPathInput({ value, onChange, tree, onCategoryCreated, placeholder, className, allowCreate = true }: Props) {
  const flatItems = useMemo(() => flattenTree(tree), [tree])
  const committedId = useRef<number | null>(value)
  const [inputValue, setInputValue] = useState(() => pathStringForId(value, tree))
  const [open, setOpen] = useState(false)
  const [highlighted, setHighlighted] = useState(0)

  useEffect(() => {
    setInputValue(pathStringForId(value, tree))
    committedId.current = value
  }, [value, tree])

  const visibleItems = useMemo((): DropdownItem[] => {
    const q = inputValue.trim()
    if (!q) {
      return flattenTreeWithDepth(tree)
    }
    const ql = q.toLowerCase()
    const matches: BrowseItem[] = flatItems
      .filter(item => item.pathStr.toLowerCase().includes(ql))
      .map(item => ({ type: 'existing', id: item.id, displayName: item.pathStr, pathStr: item.pathStr, depth: 0 }))

    const exactMatch = flatItems.find(item => item.pathStr.toLowerCase() === ql)
    if (!exactMatch && allowCreate) {
      const path = q.split('>').map(s => s.trim()).filter(Boolean)
      if (path.length > 0) {
        return [...matches, { type: 'create', displayStr: path.join(' > '), path }]
      }
    }
    return matches
  }, [inputValue, flatItems, tree])

  useEffect(() => {
    setHighlighted(0)
  }, [visibleItems])

  async function selectItem(item: DropdownItem) {
    setOpen(false)
    if (item.type === 'existing') {
      committedId.current = item.id
      setInputValue(item.pathStr)
      onChange(item.id)
    } else {
      try {
        const created = await findOrCreateCategory(item.path)
        committedId.current = created.id
        setInputValue(item.displayStr)
        onCategoryCreated?.(created)
        onChange(created.id)
      } catch {
        setInputValue(pathStringForId(committedId.current, tree))
      }
    }
  }

  async function handleBlur() {
    setOpen(false)
    const q = inputValue.trim()

    if (!q) {
      committedId.current = null
      onChange(null)
      return
    }

    const match = flatItems.find(item => item.pathStr.toLowerCase() === q.toLowerCase())
    if (match) {
      committedId.current = match.id
      setInputValue(match.pathStr)
      onChange(match.id)
      return
    }

    if (allowCreate) {
      const path = q.split('>').map(s => s.trim()).filter(Boolean)
      if (path.length > 0) {
        try {
          const created = await findOrCreateCategory(path)
          committedId.current = created.id
          setInputValue(path.join(' > '))
          onCategoryCreated?.(created)
          onChange(created.id)
        } catch {
          setInputValue(pathStringForId(committedId.current, tree))
        }
      } else {
        setInputValue(pathStringForId(committedId.current, tree))
      }
    } else {
      setInputValue(pathStringForId(committedId.current, tree))
    }
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (!open) {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        setOpen(true)
        e.preventDefault()
      }
      return
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setHighlighted(h => Math.min(h + 1, visibleItems.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setHighlighted(h => Math.max(h - 1, 0))
    } else if (e.key === 'Enter') {
      e.preventDefault()
      const item = visibleItems[highlighted]
      if (item) void selectItem(item)
    } else if (e.key === 'Tab') {
      const item = visibleItems[highlighted]
      if (item?.type === 'existing') {
        e.preventDefault()
        setInputValue(item.pathStr + ' > ')
      }
    } else if (e.key === 'Escape') {
      setInputValue(pathStringForId(committedId.current, tree))
      setOpen(false)
    }
  }

  return (
    <div className="relative">
      <input
        type="text"
        value={inputValue}
        className={cn(
          'h-9 w-full rounded-lg border border-input bg-input/30 px-3 py-2 text-sm outline-none transition-colors placeholder:text-muted-foreground focus:border-ring focus:ring-2 focus:ring-ring/50',
          className,
        )}
        placeholder={placeholder}
        onChange={e => { setInputValue(e.target.value); setOpen(true) }}
        onFocus={() => setOpen(true)}
        onBlur={() => { void handleBlur() }}
        onKeyDown={handleKeyDown}
      />
      {open && visibleItems.length > 0 && (
        <ul className="absolute top-full left-0 z-50 mt-1 max-h-60 w-full overflow-auto rounded-lg border border-border bg-popover shadow-lg">
          {visibleItems.map((item, idx) => (
            <li
              key={item.type === 'existing' ? item.id : `create-${item.displayStr}`}
              className={cn(
                'cursor-pointer px-3 py-2 text-sm',
                idx === highlighted ? 'bg-accent text-accent-foreground' : 'hover:bg-accent hover:text-accent-foreground',
                item.type === 'create' && 'text-primary italic',
              )}
              style={item.type === 'existing' && item.depth > 0 ? { paddingLeft: `${item.depth * 12 + 12}px` } : undefined}
              onMouseDown={e => { e.preventDefault(); void selectItem(item) }}
            >
              {item.type === 'create' ? `+ Erstellen: "${item.displayStr}"` : item.displayName}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
