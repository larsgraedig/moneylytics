import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { findOrCreateCategory, type CategoryNode } from '../api/rawImport'

interface FlatItem {
  id: number
  pathStr: string
}

function flattenTree(nodes: CategoryNode[], prefix: string[] = []): FlatItem[] {
  return nodes.flatMap(node => {
    const path = [...prefix, node.name]
    return [{ id: node.id, pathStr: path.join(' > ') }, ...flattenTree(node.children, path)]
  })
}

function pathStringForId(id: number | null, nodes: CategoryNode[], prefix: string[] = []): string {
  if (id === null) return ''
  for (const node of nodes) {
    const path = [...prefix, node.name]
    if (node.id === id) return path.join(' > ')
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
}

export function CategoryPathInput({ value, onChange, tree, onCategoryCreated, placeholder, className }: Props) {
  const flatItems = useMemo(() => flattenTree(tree), [tree])
  const datalistId = useId()
  const committedId = useRef<number | null>(value)

  const [inputValue, setInputValue] = useState(() => pathStringForId(value, tree))

  useEffect(() => {
    setInputValue(pathStringForId(value, tree))
    committedId.current = value
  }, [value, tree])

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    setInputValue(e.target.value)
  }

  async function commit() {
    const trimmed = inputValue.trim()

    if (!trimmed) {
      committedId.current = null
      onChange(null)
      return
    }

    const match = flatItems.find(item => item.pathStr.toLowerCase() === trimmed.toLowerCase())
    if (match) {
      committedId.current = match.id
      setInputValue(match.pathStr)
      onChange(match.id)
      return
    }

    const path = trimmed.split('>').map(s => s.trim()).filter(Boolean)
    if (path.length === 0) {
      setInputValue(pathStringForId(committedId.current, tree))
      return
    }

    try {
      const created = await findOrCreateCategory(path)
      committedId.current = created.id
      setInputValue(trimmed)
      onCategoryCreated?.(created)
      onChange(created.id)
    } catch {
      setInputValue(pathStringForId(committedId.current, tree))
    }
  }

  function handleBlur() {
    void commit()
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter') {
      e.currentTarget.blur()
    }
  }

  return (
    <>
      <input
        type="text"
        value={inputValue}
        onChange={handleChange}
        onBlur={handleBlur}
        onKeyDown={handleKeyDown}
        list={datalistId}
        placeholder={placeholder}
        className={className}
      />
      <datalist id={datalistId}>
        {flatItems.map(item => (
          <option key={item.id} value={item.pathStr} />
        ))}
      </datalist>
    </>
  )
}
