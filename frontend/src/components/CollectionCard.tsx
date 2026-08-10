import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { updateCollection, removeTransactionFromCollection, type CollectionDto } from '../api/collections'
import type { TransactionItem } from '../api/transactions'
import { Card } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

function InlineEdit({ value, placeholder, multiline, onSave }: {
  value: string | null
  placeholder: string
  multiline?: boolean
  onSave: (v: string | null) => void
}) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(value ?? '')
  const ref = useRef<HTMLInputElement & HTMLTextAreaElement>(null)

  useEffect(() => { if (editing) ref.current?.focus() }, [editing])

  function commit() {
    setEditing(false)
    const trimmed = draft.trim() || null
    if (trimmed !== value) onSave(trimmed)
  }

  if (!editing) {
    return (
      <span
        className={cn('cursor-text hover:underline underline-offset-2', !value && 'text-muted-foreground italic text-sm')}
        onClick={() => { setDraft(value ?? ''); setEditing(true) }}
        title={placeholder}
      >
        {value ?? placeholder}
      </span>
    )
  }

  const sharedProps = {
    ref,
    value: draft,
    onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => setDraft(e.target.value),
    onBlur: commit,
    onKeyDown: (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' && !multiline) { e.preventDefault(); commit() }
      if (e.key === 'Escape') { setEditing(false); setDraft(value ?? '') }
    },
    className: 'w-full rounded border border-input bg-background px-2 py-1 text-sm outline-none focus:ring-1 focus:ring-ring',
  }

  return multiline
    ? <textarea {...sharedProps} rows={2} />
    : <input {...sharedProps} type="text" />
}

export function CollectionCard({ collection, onUpdate, onDelete, onRemoveTransaction, onAddTransaction }: {
  collection: CollectionDto
  onUpdate: (id: number, name: string, note: string | null) => void
  onDelete: (id: number) => void
  onRemoveTransaction: (collectionId: number, txId: number) => void
  onAddTransaction: (collection: CollectionDto) => void
}) {
  const { t } = useTranslation()
  const [saving, setSaving] = useState(false)
  const sorted = [...collection.transactions].sort((a, b) => b.accountingDate.localeCompare(a.accountingDate))
  const total = collection.transactions.reduce((sum, tx) => sum + tx.amount, 0)

  function save(name: string | null, note: string | null) {
    if (!name?.trim()) return
    setSaving(true)
    updateCollection(collection.id, name.trim(), note)
      .then(() => { onUpdate(collection.id, name.trim(), note); setSaving(false) })
      .catch(() => setSaving(false))
  }

  function removeTx(tx: TransactionItem) {
    removeTransactionFromCollection(collection.id, tx.id)
      .then(() => onRemoveTransaction(collection.id, tx.id))
      .catch(() => {})
  }

  return (
    <Card className="overflow-hidden">
      <div className="flex items-start justify-between gap-4 border-b px-4 py-3">
        <div className="flex items-center gap-2 min-w-0">
          <Badge variant="secondary" className="shrink-0 text-xs">#{collection.id}</Badge>
          <InlineEdit
            value={collection.name}
            placeholder={t('collections.namePlaceholderInline')}
            onSave={name => save(name ?? collection.name, collection.note)}
          />
          {saving && <span className="text-xs text-muted-foreground">…</span>}
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <span className={cn('font-medium tabular-nums text-sm', total < 0 ? 'text-destructive' : total > 0 ? 'text-green-500' : 'text-muted-foreground')}>
            {EUR.format(total)}
          </span>
          <Button
            variant="destructive"
            size="icon-xs"
            title={t('common.delete')}
            onClick={() => { if (window.confirm(t('collections.deleteConfirm'))) onDelete(collection.id) }}
          >
            ␡
          </Button>
        </div>
      </div>
      <div className="px-4 py-2 border-b text-sm">
        <InlineEdit value={collection.note} placeholder={t('collections.commentPlaceholder')} multiline onSave={note => save(collection.name, note)} />
      </div>
      <div className="overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-24">{t('transactions.columns.date')}</TableHead>
            <TableHead>{t('transactions.columns.counterpartyName')}</TableHead>
            <TableHead>{t('transactions.columns.purpose')}</TableHead>
            <TableHead>{t('transactions.columns.category')}</TableHead>
            <TableHead className="text-right">{t('transactions.columns.amount')}</TableHead>
            <TableHead className="w-8" />
          </TableRow>
        </TableHeader>
        <TableBody>
          {sorted.map(tx => (
            <TableRow key={tx.id}>
              <TableCell className="text-xs text-muted-foreground tabular-nums">{formatDate(tx.accountingDate)}</TableCell>
              <TableCell className="max-w-36 truncate text-sm" title={tx.counterpartyIban ?? undefined}>{tx.counterpartyName ?? ''}</TableCell>
              <TableCell className="max-w-48 truncate text-sm text-muted-foreground" title={tx.purpose ?? undefined}>{tx.purpose ?? ''}</TableCell>
              <TableCell className="text-xs text-muted-foreground">
                {tx.category && <span>{tx.category}{tx.subcategory ? ` / ${tx.subcategory}` : ''}</span>}
              </TableCell>
              <TableCell className={cn('text-right tabular-nums text-sm', tx.amount >= 0 ? 'text-green-500' : 'text-destructive')}>
                {EUR.format(tx.amount)}
              </TableCell>
              <TableCell className="p-1">
                <Button variant="ghost" size="icon-xs" onClick={() => removeTx(tx)} title={t('collections.removeTransaction')}>×</Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      </div>
      <div className="flex px-4 py-3 border-t">
        <Button variant="outline" size="sm" onClick={() => onAddTransaction(collection)}>
          + {t('collections.addTransaction')}
        </Button>
      </div>
    </Card>
  )
}
