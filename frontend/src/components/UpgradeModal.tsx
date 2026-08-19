import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { loadStripe } from '@stripe/stripe-js'
import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js'
import { createSubscription } from '../api/subscription'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'

interface Props {
  publishableKey: string
  onClose: () => void
  onSuccess: () => void
}

export default function UpgradeModal({ publishableKey, onClose, onSuccess }: Props) {
  const { t } = useTranslation()
  const [interval, setInterval] = useState<'MONTHLY' | 'YEARLY'>('MONTHLY')
  const [clientSecret, setClientSecret] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const stripePromise = loadStripe(publishableKey)

  async function handleIntervalConfirm() {
    setLoading(true)
    setError(null)
    try {
      const setup = await createSubscription(interval)
      setClientSecret(setup.clientSecret)
    } catch {
      setError(t('subscription.upgradeModal.error'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open onOpenChange={open => { if (!open) onClose() }}>
      <DialogContent className="sm:max-w-md max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t('subscription.upgradeModal.title')}</DialogTitle>
        </DialogHeader>

        {!clientSecret ? (
          <div className="flex flex-col gap-4 py-2">
            <p className="text-sm text-muted-foreground">{t('subscription.upgradeModal.chooseInterval')}</p>
            <div className="flex gap-2">
              {(['MONTHLY', 'YEARLY'] as const).map(iv => (
                <button
                  key={iv}
                  onClick={() => setInterval(iv)}
                  className={`flex-1 rounded-lg border px-4 py-3 text-sm font-medium transition-colors ${
                    interval === iv
                      ? 'border-primary bg-primary text-primary-foreground'
                      : 'border-input bg-background hover:bg-accent'
                  }`}
                >
                  {t(`subscription.upgradeModal.${iv === 'MONTHLY' ? 'monthly' : 'yearly'}`)}
                </button>
              ))}
            </div>
            {error && <p className="text-sm text-destructive">{error}</p>}
            <div className="flex gap-2 pt-2">
              <Button variant="outline" onClick={onClose} className="flex-1">
                {t('subscription.upgradeModal.cancel')}
              </Button>
              <Button onClick={handleIntervalConfirm} disabled={loading} className="flex-1">
                {loading ? t('subscription.upgradeModal.processing') : t('subscription.upgradeModal.paymentDetails')}
              </Button>
            </div>
          </div>
        ) : (
          <Elements stripe={stripePromise} options={{ clientSecret }}>
            <PaymentForm onClose={onClose} onSuccess={onSuccess} />
          </Elements>
        )}
      </DialogContent>
    </Dialog>
  )
}

interface PaymentFormProps {
  onClose: () => void
  onSuccess: () => void
}

function PaymentForm({ onClose, onSuccess }: PaymentFormProps) {
  const { t } = useTranslation()
  const stripe = useStripe()
  const elements = useElements()
  const [processing, setProcessing] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!stripe || !elements) return

    setProcessing(true)
    setError(null)

    const { error: stripeError } = await stripe.confirmPayment({
      elements,
      redirect: 'if_required',
    })

    if (stripeError) {
      setError(stripeError.message ?? t('subscription.upgradeModal.error'))
      setProcessing(false)
    } else {
      onSuccess()
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4 py-2">
      <PaymentElement />
      {error && <p className="text-sm text-destructive">{error}</p>}
      <div className="flex gap-2 pt-2">
        <Button type="button" variant="outline" onClick={onClose} className="flex-1">
          {t('subscription.upgradeModal.cancel')}
        </Button>
        <Button type="submit" disabled={processing || !stripe} className="flex-1">
          {processing ? t('subscription.upgradeModal.processing') : t('subscription.upgradeModal.pay')}
        </Button>
      </div>
    </form>
  )
}
