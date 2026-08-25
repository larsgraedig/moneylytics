import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  fetchImports,
  rejectImport,
  type BlockedTransaction,
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

export default function ImportsPage() {
  const { t } = useTranslation()
  const [imports, setImports] = useState<TransactionImportDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [confirmTarget, setConfirmTarget] = useState<TransactionImportDto | null>(null)
  const [rejecting, setRejecting] = useState(false)
  const [blockedInfo, setBlockedInfo] = useState<BlockedTransaction[] | null>(null)
  const [blockedTarget, setBlockedTarget] = useState<TransactionImportDto | null>(null)

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

  async function handleConfirmReject() {
    if (!confirmTarget) return
    setRejecting(true)
    try {
      const result = await rejectImport(confirmTarget.id)
      if ('error' in result) {
        setBlockedTarget(confirmTarget)
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

  async function handleForceReject() {
    if (!blockedTarget) return
    setRejecting(true)
    try {
      await rejectImport(blockedTarget.id, true)
      setBlockedInfo(null)
      setBlockedTarget(null)
      await load()
    } catch {
      setBlockedInfo(null)
      setBlockedTarget(null)
    } finally {
      setRejecting(false)
    }
  }

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
              <TableHead>{t('imports.columns.date')}</TableHead>
              <TableHead>{t('imports.columns.filename')}</TableHead>
              <TableHead>{t('imports.columns.fileType')}</TableHead>
              <TableHead>{t('imports.columns.checksum')}</TableHead>
              <TableHead className="text-right">{t('imports.columns.count')}</TableHead>
              <TableHead>{t('imports.columns.status')}</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {imports.map((imp) => (
              <TableRow key={imp.id}>
                <TableCell className="whitespace-nowrap text-sm">
                  {formatImportedAt(imp.importedAt)}
                </TableCell>
                <TableCell className="max-w-48 truncate text-sm" title={imp.filename}>
                  {imp.filename}
                </TableCell>
                <TableCell className="text-sm">{imp.fileType}</TableCell>
                <TableCell className="font-mono text-xs" title={imp.checksum}>
                  {imp.checksum.slice(0, 8)}…
                </TableCell>
                <TableCell className="text-right text-sm">{imp.transactionCount}</TableCell>
                <TableCell>
                  <Badge variant={imp.status === 'ACTIVE' ? 'default' : 'secondary'}>
                    {t(`imports.status.${imp.status}`)}
                  </Badge>
                </TableCell>
                <TableCell className="text-right">
                  {imp.status === 'ACTIVE' && (
                    <Button
                      variant="destructive"
                      size="sm"
                      onClick={() => setConfirmTarget(imp)}
                    >
                      {t('imports.reject.button')}
                    </Button>
                  )}
                </TableCell>
              </TableRow>
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
              count: confirmTarget?.transactionCount ?? 0,
              filename: confirmTarget?.filename ?? '',
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
    </div>
  )
}
