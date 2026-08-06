import { Fragment, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { removeTransactionFromGroup, updateLinkedGroupMeta, updateOffsetLinkComment, type LinkedGroupItem, type TransactionItem } from '../api/transactions'
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

function committedOffset(tx: TransactionItem): number {
  return tx.offsetLinks.reduce((acc, link) => {
    if ((tx.amount >= 0) === (link.linkedTransactionAmount >= 0)) return acc
    const offset = (link.amountA !== null && link.amountB !== null)
      ? Math.min(Math.abs(link.amountA), Math.abs(link.amountB))
      : (link.amountA === null && link.amountB === null)
        ? 0
        : Math.min(Math.abs(link.committedAmount), Math.abs(link.linkedTransactionAmount))
    return acc + offset
  }, 0)
}

function effectiveAmount(tx: TransactionItem): number {
  if (tx.offsetLinks.length === 0) return tx.amount
  const totalOffset = committedOffset(tx)
  if (totalOffset === 0) return tx.amount
  if (tx.amount >= 0) return tx.amount - totalOffset
  return totalOffset >= Math.abs(tx.amount) ? 0 : -totalOffset
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

export function GroupCard({ group, onMetaChange, onOffsetCommentChange, onRemoveTransaction }: {
  group: LinkedGroupItem
  onMetaChange: (groupId: number, name: string | null, comment: string | null) => void
  onOffsetCommentChange: (groupId: number, txId: number, linkId: number, comment: string | null) => void
  onRemoveTransaction: (txId: number) => void
}) {
  const { t } = useTranslation()
  const [saving, setSaving] = useState(false)
  const incomeEff = group.transactions.filter(tx => tx.amount >= 0).reduce((sum, tx) => sum + effectiveAmount(tx), 0)
  const netSum = Math.abs(incomeEff) > 0.005
    ? incomeEff
    : group.transactions.filter(tx => tx.amount < 0).reduce((sum, tx) => sum + tx.amount + committedOffset(tx), 0)
  const txById = Object.fromEntries(group.transactions.map(tx => [tx.id, tx]))
  const rawSum = group.transactions.reduce((sum, tx) => sum + tx.amount, 0)
  const isBalanced = Math.abs(rawSum) < 0.005

  function save(name: string | null, comment: string | null) {
    setSaving(true)
    updateLinkedGroupMeta(group.groupId, name, comment)
      .then(() => { onMetaChange(group.groupId, name, comment); setSaving(false) })
      .catch(() => setSaving(false))
  }

  function saveOffsetComment(txId: number, linkId: number, comment: string | null) {
    updateOffsetLinkComment(linkId, comment)
      .then(() => onOffsetCommentChange(group.groupId, txId, linkId, comment))
      .catch(() => {})
  }

  function removeTransaction(txId: number) {
    removeTransactionFromGroup(txId, group.groupId)
      .then(() => onRemoveTransaction(txId))
      .catch(() => {})
  }

  return (
    <Card className="overflow-hidden">
      <div className="flex items-start justify-between gap-4 border-b px-4 py-3">
        <div className="flex items-center gap-2 min-w-0">
          <Badge variant="secondary" className="shrink-0 text-xs">#{group.groupId}</Badge>
          <InlineEdit value={group.name} placeholder={t('linked.namePlaceholder')} onSave={name => save(name, group.comment)} />
          {saving && <span className="text-xs text-muted-foreground">…</span>}
        </div>
        <span className={cn('shrink-0 font-medium tabular-nums text-sm', isBalanced ? 'text-muted-foreground' : netSum > 0 ? 'text-green-500' : 'text-destructive')}>
          {isBalanced ? t('linked.balanced') : EUR.format(netSum)}
        </span>
      </div>
      <div className="px-4 py-2 border-b text-sm">
        <InlineEdit value={group.comment} placeholder={t('linked.commentPlaceholder')} multiline onSave={comment => save(group.name, comment)} />
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
            <TableHead className="text-right">{t('transactions.columns.effectiveAmount')}</TableHead>
            <TableHead className="w-8" />
          </TableRow>
        </TableHeader>
        <TableBody>
          {group.transactions.map(tx => {
            const eff = effectiveAmount(tx)
            const reduced = Math.abs(eff) < Math.abs(tx.amount) - 0.005
            return (
              <Fragment key={tx.id}>
                <TableRow>
                  <TableCell className="text-xs text-muted-foreground tabular-nums">{formatDate(tx.accountingDate)}</TableCell>
                  <TableCell className="max-w-36 truncate text-sm" title={tx.counterpartyIban ?? undefined}>{tx.counterpartyName ?? ''}</TableCell>
                  <TableCell className="max-w-48 truncate text-sm text-muted-foreground" title={tx.purpose ?? undefined}>{tx.purpose ?? ''}</TableCell>
                  <TableCell className="text-xs">
                    {tx.category && <span className="text-muted-foreground">{tx.category}{tx.subcategory ? ` / ${tx.subcategory}` : ''}</span>}
                  </TableCell>
                  <TableCell className={cn('text-right tabular-nums text-sm', tx.amount >= 0 ? 'text-green-500' : 'text-destructive')}>
                    {EUR.format(tx.amount)}
                  </TableCell>
                  <TableCell className={cn('text-right tabular-nums text-sm', reduced ? 'text-muted-foreground line-through' : eff >= 0 ? 'text-green-500' : 'text-destructive')}>
                    {EUR.format(eff)}
                  </TableCell>
                  <TableCell className="p-1">
                    <Button variant="ghost" size="icon-xs" onClick={() => removeTransaction(tx.id)} title={t('linked.removeTransaction')}>×</Button>
                  </TableCell>
                </TableRow>
                {tx.offsetLinks.filter(link => link.committedAmount !== tx.amount).map(link => {
                  const linkedTx = txById[link.linkedTransactionId]
                  const isPositive = link.committedAmount >= 0
                  return (
                    <TableRow key={`offset-${link.id}`} className="bg-muted/20">
                      <TableCell className="text-xs text-muted-foreground tabular-nums">{linkedTx ? formatDate(linkedTx.accountingDate) : ''}</TableCell>
                      <TableCell className="text-sm">
                        <span className={isPositive ? 'text-green-500' : 'text-destructive'}>{isPositive ? '↑' : '↓'}</span>
                        {' '}
                        {linkedTx ? (linkedTx.counterpartyName ?? linkedTx.purpose ?? t('linked.transaction')) : t('linked.transaction')}
                      </TableCell>
                      <TableCell className="text-sm">
                        <InlineEdit value={link.comment} placeholder={t('linked.offsetCommentPlaceholder')} onSave={comment => saveOffsetComment(tx.id, link.id, comment)} />
                      </TableCell>
                      <TableCell className="text-xs text-muted-foreground">
                        {(link.amountA !== null || link.amountB !== null) && t('linked.partial')}
                      </TableCell>
                      <TableCell className={cn('text-right tabular-nums text-sm', isPositive ? 'text-green-500' : 'text-destructive')}>
                        {EUR.format(link.committedAmount)}
                      </TableCell>
                      <TableCell /><TableCell />
                    </TableRow>
                  )
                })}
              </Fragment>
            )
          })}
        </TableBody>
      </Table>
      </div>
    </Card>
  )
}
