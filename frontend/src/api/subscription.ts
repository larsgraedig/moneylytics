import { fetchWithUser } from './client'

export interface StripeConfig {
  publishableKey: string
}

export interface SubscriptionSetup {
  clientSecret: string
  subscriptionId: string
}

export interface SubscriptionStatus {
  status: string | null
  currentPeriodEnd: number | null
  interval: string | null
}

export interface Invoice {
  id: number
  amountCents: number
  currency: string
  status: string
  periodStart: string
  periodEnd: string
  hasPdf: boolean
  issuedAt: string
}

export async function fetchStripeConfig(): Promise<StripeConfig> {
  const res = await fetchWithUser('/subscriptions/config')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<StripeConfig>
}

export async function createSubscription(interval: 'MONTHLY' | 'YEARLY'): Promise<SubscriptionSetup> {
  const res = await fetchWithUser('/subscriptions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ interval }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<SubscriptionSetup>
}

export async function cancelSubscription(): Promise<void> {
  const res = await fetchWithUser('/subscriptions', { method: 'DELETE' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export async function fetchSubscriptionStatus(): Promise<SubscriptionStatus> {
  const res = await fetchWithUser('/subscriptions/status')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<SubscriptionStatus>
}

export async function fetchInvoices(): Promise<Invoice[]> {
  const res = await fetchWithUser('/invoices')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<Invoice[]>
}

export async function downloadInvoicePdf(invoiceId: number): Promise<void> {
  const res = await fetchWithUser(`/invoices/${invoiceId}/pdf`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `invoice-${invoiceId}.pdf`
  a.click()
  URL.revokeObjectURL(url)
}
