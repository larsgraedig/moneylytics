import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  cancelSubscription,
  downloadInvoicePdf,
  fetchInvoices,
  fetchStripeConfig,
  fetchSubscriptionStatus,
  type Invoice,
  type SubscriptionStatus,
} from '../api/subscription'
import { Button } from '@/components/ui/button'
import UpgradeModal from './UpgradeModal'

export default function SubscriptionSection() {
  const { t } = useTranslation()
  const [status, setStatus] = useState<SubscriptionStatus | null>(null)
  const [invoices, setInvoices] = useState<Invoice[]>([])
  const [publishableKey, setPublishableKey] = useState<string | null>(null)
  const [showUpgrade, setShowUpgrade] = useState(false)
  const [showCancelConfirm, setShowCancelConfirm] = useState(false)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)
  const [canceling, setCanceling] = useState(false)

  useEffect(() => {
    void fetchSubscriptionStatus().then(setStatus).catch(() => {})
    void fetchInvoices().then(setInvoices).catch(() => {})
    void fetchStripeConfig().then(cfg => setPublishableKey(cfg.publishableKey)).catch(() => {})
  }, [])

  const isPro = status?.status === 'ACTIVE' || status?.status === 'TRIALING' || status?.status === 'CANCELING'
  const isCanceling = status?.status === 'CANCELING'

  function formatDate(epochSeconds: number) {
    return new Date(epochSeconds * 1000).toLocaleDateString()
  }

  async function handleCancel() {
    setCanceling(true)
    try {
      await cancelSubscription()
      setSuccessMsg(t('subscription.cancelSuccess'))
      const updated = await fetchSubscriptionStatus()
      setStatus(updated)
    } catch {
      // ignore
    } finally {
      setCanceling(false)
      setShowCancelConfirm(false)
    }
  }

  function handleUpgradeSuccess() {
    setShowUpgrade(false)
    setSuccessMsg(t('subscription.upgradeModal.success'))
    void fetchSubscriptionStatus().then(setStatus).catch(() => {})
    void fetchInvoices().then(setInvoices).catch(() => {})
  }

  const statusKey = status?.status ?? null
  const statusLabel = statusKey ? (t(`subscription.status.${statusKey}`, { defaultValue: statusKey }) as string) : null

  return (
    <section className="flex flex-col gap-3">
      <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
        {t('subscription.title')}
      </p>

      <div className="rounded-lg border bg-card px-4 py-3 flex flex-col gap-2">
        <div className="flex items-center justify-between">
          <span className="text-sm font-medium">
            {isPro ? t('subscription.pro') : t('subscription.free')}
          </span>
          {statusLabel && (
            <span className="text-xs text-muted-foreground">{statusLabel}</span>
          )}
        </div>

        {status?.currentPeriodEnd && (
          <p className="text-xs text-muted-foreground">
            {isCanceling ? t('subscription.expiresOn') : t('subscription.renewsOn')}{' '}
            {formatDate(status.currentPeriodEnd)}
            {status.interval && ` · ${t(`subscription.interval.${status.interval}`)}`}
          </p>
        )}

        {successMsg && (
          <p className="text-xs text-green-600">{successMsg}</p>
        )}

        <div className="flex gap-2 pt-1">
          {!isPro && publishableKey && (
            <Button size="sm" onClick={() => setShowUpgrade(true)} className="flex-1">
              {t('subscription.upgradeButton')}
            </Button>
          )}
          {isPro && !isCanceling && (
            <Button
              size="sm"
              variant="outline"
              onClick={() => setShowCancelConfirm(true)}
              className="flex-1"
            >
              {t('subscription.cancelButton')}
            </Button>
          )}
        </div>
      </div>

      {showCancelConfirm && (
        <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 flex flex-col gap-2">
          <p className="text-sm">{t('subscription.cancelConfirm')}</p>
          <div className="flex gap-2">
            <Button
              size="sm"
              variant="destructive"
              onClick={handleCancel}
              disabled={canceling}
              className="flex-1"
            >
              {t('subscription.cancelConfirmYes')}
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => setShowCancelConfirm(false)}
              className="flex-1"
            >
              {t('subscription.cancelConfirmNo')}
            </Button>
          </div>
        </div>
      )}

      {invoices.length > 0 && (
        <div className="flex flex-col gap-1">
          <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
            {t('subscription.invoices')}
          </p>
          <div className="rounded-lg border divide-y">
            {invoices.map(inv => (
              <div key={inv.id} className="flex items-center justify-between px-3 py-2 text-sm">
                <div className="flex flex-col">
                  <span>{inv.issuedAt}</span>
                  <span className="text-xs text-muted-foreground">
                    {(inv.amountCents / 100).toFixed(2)} {inv.currency.toUpperCase()}
                  </span>
                </div>
                {inv.hasPdf && (
                  <button
                    className="text-xs text-primary underline-offset-2 hover:underline"
                    onClick={() => void downloadInvoicePdf(inv.id).catch(() => {})}
                  >
                    {t('subscription.downloadPdf')}
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {invoices.length === 0 && isPro && (
        <p className="text-xs text-muted-foreground">{t('subscription.noInvoices')}</p>
      )}

      {showUpgrade && publishableKey && (
        <UpgradeModal
          publishableKey={publishableKey}
          onClose={() => setShowUpgrade(false)}
          onSuccess={handleUpgradeSuccess}
        />
      )}
    </section>
  )
}
