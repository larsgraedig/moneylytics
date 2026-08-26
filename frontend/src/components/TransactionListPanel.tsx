import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { fetchTransactionList, type SankeyNode, type TransactionItem } from '../api/transactions'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ScrollArea } from '@/components/ui/scroll-area'
import { cn } from '@/lib/utils'

const LINK_COLORS = ['#f59e0b', '#10b981', '#60a5fa', '#f472b6', '#a78bfa', '#fb923c']
const BUDGET_COLORS = ['#34d399', '#818cf8', '#fb7185', '#fbbf24', '#38bdf8', '#a3e635']

type NodeInfo =
  | { type: 'cat'; category: string }
  | { type: 'grp'; category: string; group: string }
  | { type: 'leaf'; category: string; group: string; subcategory: string }
  | { type: 'sub'; category: string; subcategory: string }

function parseNodeKey(nodeKey: string): NodeInfo {
  if (nodeKey.startsWith('cat:')) {
    return { type: 'cat', category: nodeKey.slice(4) }
  }
  if (nodeKey.startsWith('grp:')) {
    const rest = nodeKey.slice(4)
    const colon = rest.indexOf(':')
    return { type: 'grp', category: rest.slice(0, colon), group: rest.slice(colon + 1) }
  }
  if (nodeKey.startsWith('leaf:')) {
    const rest = nodeKey.slice(5)
    const first = rest.indexOf(':')
    const second = rest.indexOf(':', first + 1)
    return {
      type: 'leaf',
      category: rest.slice(0, first),
      group: rest.slice(first + 1, second),
      subcategory: rest.slice(second + 1),
    }
  }
  const rest = nodeKey.slice(4)
  const colon = rest.indexOf(':')
  return { type: 'sub', category: rest.slice(0, colon), subcategory: rest.slice(colon + 1) }
}

type BaseProps = {
  from: string
  to: string
  accountId?: number
  onClose: () => void
}

type Props = BaseProps & ({ node: SankeyNode; nodeKey?: never } | { nodeKey: string; node?: never })

type State =
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'ready'; transactions: TransactionItem[]; total: number }

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

function PanelTitle({ node, nodeKey }: { node: SankeyNode | null; nodeKey: string | null }) {
  const info = nodeKey != null ? parseNodeKey(nodeKey) : null

  if (node != null) {
    return (
      <span className="flex items-center gap-1 flex-wrap">
        {node.namePath.map((segment, idx) => (
          <span key={idx} className="flex items-center gap-1">
            {idx > 0 && <span className="text-muted-foreground">›</span>}
            <span className={idx === node.namePath.length - 1 ? '' : 'text-muted-foreground text-sm'}>
              {segment}
            </span>
          </span>
        ))}
      </span>
    )
  }

  if (info != null) {
    if (info.type === 'cat') return <span>{info.category}</span>
    if (info.type === 'grp') return (
      <span className="flex items-center gap-1">
        <span className="text-muted-foreground text-sm">{info.category}</span>
        <span className="text-muted-foreground">›</span>
        <span>{info.group}</span>
      </span>
    )
    if (info.type === 'leaf') return (
      <span className="flex items-center gap-1 flex-wrap">
        <span className="text-muted-foreground text-sm">{info.category}</span>
        <span className="text-muted-foreground">›</span>
        <span className="text-muted-foreground text-sm">{info.group}</span>
        <span className="text-muted-foreground">›</span>
        <span>{info.subcategory}</span>
      </span>
    )
    return (
      <span className="flex items-center gap-1">
        <span className="text-muted-foreground text-sm">{info.category}</span>
        <span className="text-muted-foreground">›</span>
        <span>{info.subcategory}</span>
      </span>
    )
  }

  return null
}

export default function TransactionListPanel({ from, to, accountId, onClose, ...rest }: Props) {
  const { t } = useTranslation()
  const [state, setState] = useState<State>({ phase: 'loading' })

  const node: SankeyNode | null = 'node' in rest && rest.node != null ? rest.node : null
  const nodeKey: string | null = 'nodeKey' in rest && rest.nodeKey != null ? rest.nodeKey : null

  useEffect(() => {
    setState({ phase: 'loading' })
    let req: Promise<{ transactions: TransactionItem[]; total: number }>
    if (node != null) {
      req = fetchTransactionList(from, to, undefined, undefined, accountId, undefined, node.categoryId)
    } else {
      const info = parseNodeKey(nodeKey!)
      const category = info.category
      const subcategory =
        info.type === 'grp' || info.type === 'leaf'
          ? info.group
          : info.type === 'sub'
          ? info.subcategory
          : undefined
      const group = info.type === 'leaf' ? info.subcategory : undefined
      req = fetchTransactionList(from, to, category, subcategory, accountId, group)
    }
    req
      .then(r => setState({ phase: 'ready', transactions: r.transactions, total: r.total }))
      .catch(e => setState({ phase: 'error', message: e instanceof Error ? e.message : 'Failed to load' }))
  }, [node?.categoryId, nodeKey, from, to, accountId])

  const info = nodeKey != null ? parseNodeKey(nodeKey) : null
  const showGroupCol = info?.type === 'cat'
  const showSubcategoryCol = info?.type === 'grp'

  return (
    <Dialog open onOpenChange={open => { if (!open) onClose() }}>
      <DialogContent className="flex flex-col max-w-4xl max-h-[85vh] p-0 gap-0">
        <DialogHeader className="border-b px-5 py-4 shrink-0">
          <DialogTitle>
            <PanelTitle node={node} nodeKey={nodeKey} />
          </DialogTitle>
        </DialogHeader>

        <ScrollArea className="flex-1">
          {state.phase === 'loading' && (
            <p className="px-5 py-8 text-sm text-muted-foreground">{t('common.loading')}</p>
          )}

          {state.phase === 'error' && (
            <p className="px-5 py-8 text-sm text-destructive">{state.message}</p>
          )}

          {state.phase === 'ready' && (
            <div className="flex flex-col">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('transactions.panel.date')}</TableHead>
                    {showGroupCol && <TableHead>{t('transactions.columns.group')}</TableHead>}
                    {showSubcategoryCol && <TableHead>{t('transactions.columns.subcategory')}</TableHead>}
                    <TableHead>{t('transactions.panel.details')}</TableHead>
                    <TableHead className="text-right">{t('transactions.panel.amount')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {state.transactions.map((tx, i) => (
                    <TableRow key={i}>
                      <TableCell className="text-muted-foreground tabular-nums text-xs whitespace-nowrap">
                        <div>{formatDate(tx.accountingDate)}</div>
                        <div className="text-[10px] opacity-40">#{tx.id}</div>
                      </TableCell>
                      {showGroupCol && <TableCell className="text-muted-foreground text-xs">{tx.subcategory}</TableCell>}
                      {showSubcategoryCol && <TableCell className="text-muted-foreground text-xs">{tx.group}</TableCell>}
                      <TableCell>
                        <div className="flex flex-wrap items-center gap-1">
                          <span className="text-xs text-muted-foreground">
                            {[tx.category, tx.subcategory, tx.group].filter(Boolean).join(' › ')}
                          </span>
                          {tx.groups.map((g, gi) => (
                            <span
                              key={g.id}
                              className="txnv-link-chip"
                              style={{ borderColor: LINK_COLORS[gi % LINK_COLORS.length] }}
                            >
                              {g.name ?? `#${g.id}`}
                            </span>
                          ))}
                          {tx.budgetLinks.map((bl, bi) => (
                            <span
                              key={bl.linkId}
                              className="txnv-budget-chip"
                              style={{ borderColor: BUDGET_COLORS[bi % BUDGET_COLORS.length] }}
                            >
                              <span className="txnv-budget-chip-name">{bl.budgetName}</span>
                              {bl.amount != null && (
                                <span className="txnv-budget-chip-amt">{EUR.format(bl.amount)}</span>
                              )}
                            </span>
                          ))}
                          {tx.collections.map((c, ci) => (
                            <span
                              key={c.id}
                              className="txnv-budget-chip"
                              style={{ borderColor: BUDGET_COLORS[ci % BUDGET_COLORS.length] }}
                            >
                              <span className="txnv-budget-chip-name">{c.name}</span>
                            </span>
                          ))}
                        </div>
                      </TableCell>
                      <TableCell className={cn('text-right tabular-nums font-medium text-sm whitespace-nowrap', tx.amount < 0 ? 'text-destructive' : 'text-foreground')}>
                        {EUR.format(tx.amount)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>

              {state.transactions.length === 0 && (
                <p className="px-5 py-8 text-sm text-muted-foreground">{t('transactions.panel.noTransactions')}</p>
              )}

              {state.transactions.length > 0 && (
                <div className="flex items-center justify-between border-t px-5 py-3 mt-auto">
                  <span className="text-sm text-muted-foreground">{t('transactions.panel.total')}</span>
                  <span className={cn('font-medium tabular-nums', state.total < 0 ? 'text-destructive' : 'text-foreground')}>
                    {EUR.format(state.total)}
                  </span>
                </div>
              )}
            </div>
          )}
        </ScrollArea>
      </DialogContent>
    </Dialog>
  )
}
