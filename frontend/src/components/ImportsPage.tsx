import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  fetchImports,
  fetchImportTransactions,
  rejectImport,
  rejectImportFile,
  type BlockedTransaction,
  type ImportFileDto,
  type ImportTransactionDto,
  type TransactionImportDto,
} from '../api/imports'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ChevronDown, ChevronRight, List, Package, Scissors } from 'lucide-react'

const DATE_FMT = new Intl.DateTimeFormat('de-DE', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

function formatImportedAt(iso: string): string {
  try {
    return DATE_FMT.format(new Date(iso))
  } catch {
    return iso
  }
}

const REASON_LABELS: Record<string, string> = {
  HAS_PARENT: 'Teil einer Transaktionsteilung',
  IS_PARENT: 'Hat Untertransaktionen',
  IN_COLLECTION: 'In einer Sammlung',
  IN_BUDGET: 'In einem Budget',
  HAS_OFFSET: 'Teil einer Verrechnung',
}

function labelReason(reason: string): string {
  return REASON_LABELS[reason] ?? reason
}

type RejectTarget =
  | { kind: 'import'; imp: TransactionImportDto }
  | { kind: 'file'; imp: TransactionImportDto; file: ImportFileDto }

export default function ImportsPage() {
  const { t } = useTranslation()
  const [imports, setImports] = useState<TransactionImportDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set())
  const [confirmTarget, setConfirmTarget] = useState<RejectTarget | null>(null)
  const [rejecting, setRejecting] = useState(false)
  const [blockedInfo, setBlockedInfo] = useState<BlockedTransaction[] | null>(null)
  const [blockedTarget, setBlockedTarget] = useState<RejectTarget | null>(null)
  const [txDialogImport, setTxDialogImport] = useState<TransactionImportDto | null>(null)
  const [txDialogItems, setTxDialogItems] = useState<ImportTransactionDto[]>([])
  const [txDialogLoading, setTxDialogLoading] = useState(false)
  const [txDialogError, setTxDialogError] = useState<string | null>(null)

  useEffect(() => {
    load()
  }, [])

  async function load() {
    setLoading(true)
    setError(null)
    try {
      setImports(await fetchImports())
    } catch {
      setError(t('imports.loadError'))
    } finally {
      setLoading(false)
    }
  }

  function toggleExpand(id: number) {
    setExpandedIds(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  async function doReject(target: RejectTarget, force = false) {
    setRejecting(true)
    try {
      const result =
        target.kind === 'import'
          ? await rejectImport(target.imp.id, force)
          : await rejectImportFile(target.imp.id, target.file.id, force)
      if ('error' in result) {
        setBlockedTarget(target)
        setBlockedInfo(result.error.blocked)
        setConfirmTarget(null)
      } else {
        setConfirmTarget(null)
        await load()
      }
    } catch {
      setConfirmTarget(null)
    } finally {
      setRejecting(false)
    }
  }

  async function handleConfirmReject() {
    if (!confirmTarget) return
    await doReject(confirmTarget)
  }

  async function handleForceReject() {
    if (!blockedTarget) return
    setRejecting(true)
    try {
      const result =
        blockedTarget.kind === 'import'
          ? await rejectImport(blockedTarget.imp.id, true)
          : await rejectImportFile(blockedTarget.imp.id, blockedTarget.file.id, true)
      if (!('error' in result)) {
        setBlockedInfo(null)
        setBlockedTarget(null)
        await load()
      }
    } catch {
      // ignore
    } finally {
      setRejecting(false)
    }
  }

  async function openTxDialog(imp: TransactionImportDto) {
    setTxDialogImport(imp)
    setTxDialogItems([])
    setTxDialogError(null)
    setTxDialogLoading(true)
    try {
      setTxDialogItems(await fetchImportTransactions(imp.id))
    } catch {
      setTxDialogError(t('imports.transactions.loadError'))
    } finally {
      setTxDialogLoading(false)
    }
  }

  function statusBadgeVariant(status: string): 'default' | 'secondary' | 'outline' {
    if (status === 'ACTIVE') return 'default'
    if (status === 'REJECTED') return 'secondary'
    return 'outline'
  }

  const confirmCount =
    confirmTarget == null
      ? 0
      : confirmTarget.kind === 'import'
        ? confirmTarget.imp.transactionCount
        : confirmTarget.file.transactionCount

  const confirmFilename =
    confirmTarget == null
      ? ''
      : confirmTarget.kind === 'import'
        ? `Import #${confirmTarget.imp.id}`
        : confirmTarget.file.filename

  if (loading) return <p className="p-6 text-sm text-muted-foreground">{t('common.loading')}</p>
  if (error) return <p className="p-6 text-sm text-destructive">{error}</p>

  return (
    <div className="flex flex-col gap-4 p-6">
      <h2 className="text-base font-medium">{t('imports.title')}</h2>

      {imports.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('imports.empty')}</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-6" />
              <TableHead>{t('imports.columns.date')}</TableHead>
              <TableHead className="text-right">{t('imports.columns.count')}</TableHead>
              <TableHead>{t('imports.columns.status')}</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {imports.map((imp) => (
              <>
                <TableRow key={imp.id}>
                  <TableCell className="py-1 pr-0">
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-6 w-6 p-0"
                      onClick={() => toggleExpand(imp.id)}
                    >
                      {expandedIds.has(imp.id)
                        ? <ChevronDown size={14} />
                        : <ChevronRight size={14} />}
                    </Button>
                  </TableCell>
                  <TableCell className="whitespace-nowrap text-sm">
                    {formatImportedAt(imp.importedAt)}
                  </TableCell>
                  <TableCell className="text-right text-sm">{imp.transactionCount}</TableCell>
                  <TableCell>
                    <Badge variant={statusBadgeVariant(imp.status)}>
                      {t(`imports.status.${imp.status}`)}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-2">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => openTxDialog(imp)}
                      >
                        <List size={14} className="mr-1" />
                        {t('imports.transactions.button')}
                      </Button>
                      {imp.status === 'ACTIVE' && (
                        <Button
                          variant="destructive"
                          size="sm"
                          onClick={() => setConfirmTarget({ kind: 'import', imp })}
                        >
                          {t('imports.reject.button')}
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>

                {expandedIds.has(imp.id) && imp.files.map((file) => (
                  <TableRow key={`file-${file.id}`} className="bg-muted/30">
                    <TableCell />
                    <TableCell className="py-1 text-xs text-muted-foreground max-w-48 truncate" title={file.filename}>
                      <span className="font-mono">{file.filename}</span>
                      <span className="ml-2 opacity-50 font-mono">{file.checksum.slice(0, 8)}…</span>
                    </TableCell>
                    <TableCell className="py-1 text-right text-xs text-muted-foreground">
                      {file.transactionCount}
                    </TableCell>
                    <TableCell className="py-1">
                      <Badge
                        variant={
                          file.status === 'ACTIVE'
                            ? 'outline'
                            : file.status === 'REJECTED'
                              ? 'secondary'
                              : 'outline'
                        }
                        className={`text-xs${file.status === 'PARTIALLY_REJECTED' ? ' text-orange-600' : ''}`}
                      >
                        {t(`imports.status.${file.status}`)}
                      </Badge>
                    </TableCell>
                    <TableCell className="py-1 text-right">
                      {file.status === 'ACTIVE' && (
                        <Button
                          variant="destructive"
                          size="sm"
                          className="h-7 text-xs"
                          onClick={() => setConfirmTarget({ kind: 'file', imp, file })}
                        >
                          {t('imports.reject.button')}
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </>
            ))}
          </TableBody>
        </Table>
      )}

      <Dialog open={!!confirmTarget} onOpenChange={(open) => { if (!open) setConfirmTarget(null) }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('imports.reject.confirmTitle')}</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            {t('imports.reject.confirmBody', {
              count: confirmCount,
              filename: confirmFilename,
            })}
          </p>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmTarget(null)}>
              {t('imports.reject.cancel')}
            </Button>
            <Button variant="destructive" disabled={rejecting} onClick={handleConfirmReject}>
              {t('imports.reject.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!blockedInfo} onOpenChange={(open) => { if (!open) { setBlockedInfo(null); setBlockedTarget(null) } }}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{t('imports.reject.blockedTitle')}</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">{t('imports.reject.blockedBody')}</p>
          {blockedInfo && (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('imports.reject.blockedTransaction')}</TableHead>
                  <TableHead>{t('imports.reject.blockedReasons')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {blockedInfo.map((bt) => (
                  <TableRow key={bt.transactionId}>
                    <TableCell className="font-mono text-sm">#{bt.transactionId}</TableCell>
                    <TableCell className="text-sm">
                      {bt.reasons.map(labelReason).join(', ')}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => { setBlockedInfo(null); setBlockedTarget(null) }}>
              {t('imports.reject.close')}
            </Button>
            <Button variant="destructive" disabled={rejecting} onClick={handleForceReject}>
              {t('imports.reject.blockedConfirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!txDialogImport} onOpenChange={(open) => { if (!open) setTxDialogImport(null) }}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>{t('imports.transactions.dialogTitle')}</DialogTitle>
          </DialogHeader>
          {txDialogLoading && (
            <p className="text-sm text-muted-foreground">{t('common.loading')}</p>
          )}
          {txDialogError && (
            <p className="text-sm text-destructive">{txDialogError}</p>
          )}
          {!txDialogLoading && !txDialogError && (
            txDialogItems.length === 0
              ? <p className="text-sm text-muted-foreground">{t('imports.transactions.empty')}</p>
              : (
                <div className="max-h-[60vh] overflow-y-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>{t('imports.transactions.columns.date')}</TableHead>
                        <TableHead>{t('imports.transactions.columns.counterparty')}</TableHead>
                        <TableHead className="text-right">{t('imports.transactions.columns.amount')}</TableHead>
                        <TableHead>{t('imports.transactions.columns.status')}</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {txDialogItems.map((tx) => (
                        <TableRow key={tx.id}>
                          <TableCell className="whitespace-nowrap text-sm align-top">{tx.bookingDate}</TableCell>
                          <TableCell className="text-sm align-top">
                            <div className="max-w-48 truncate" title={tx.counterpartyName ?? ''}>
                              {tx.counterpartyName ?? <span className="text-muted-foreground">—</span>}
                            </div>
                            {(tx.collections.length > 0 || tx.budgets.length > 0 || tx.offsetGroups.length > 0 || tx.parentId != null || tx.isVirtual) && (
                              <div className="flex flex-wrap gap-1 mt-1">
                                {tx.collections.map((name) => (
                                  <Badge key={`col-${name}`} variant="outline" className="text-xs font-normal">
                                    {name}
                                  </Badge>
                                ))}
                                {tx.budgets.map((name) => (
                                  <Badge key={`bud-${name}`} variant="outline" className="text-xs font-normal">
                                    {name}
                                  </Badge>
                                ))}
                                {tx.offsetGroups.map((name, i) => (
                                  <Badge key={`grp-${i}`} variant="outline" className="text-xs font-normal font-mono">
                                    {name ?? '—'}
                                  </Badge>
                                ))}
                                {tx.parentId != null && (
                                  <span className="inline-flex items-center text-blue-500" title="Teil einer Transaktionsteilung">
                                    <Scissors size={11} />
                                  </span>
                                )}
                                {tx.isVirtual && tx.parentId == null && (
                                  <span className="inline-flex items-center text-green-500" title="Verrechnung">
                                    <Package size={11} />
                                  </span>
                                )}
                              </div>
                            )}
                          </TableCell>
                          <TableCell className="text-right text-sm font-mono align-top">
                            {tx.amount.toLocaleString('de-DE', { minimumFractionDigits: 2 })} {tx.currency}
                          </TableCell>
                          <TableCell className="align-top">
                            <Badge
                              variant={tx.status === 'ACCEPTED' ? 'default' : 'secondary'}
                              className="text-xs"
                            >
                              {t(`imports.transactions.status.${tx.status}`)}
                            </Badge>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              )
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setTxDialogImport(null)}>
              {t('imports.reject.close')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
