import { fetchWithUser } from './client'

export interface RecurringOccurrenceItem {
  transactionId: number
  date: string
  amount: number
  purpose: string | null
  counterpartyName: string | null
  counterpartyIban: string | null
}

export type RecurrenceCadence = 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'SEMIANNUAL' | 'YEARLY'
export type RecurringType = 'SALARY' | 'RENT' | 'INSURANCE' | 'SUBSCRIPTION' | 'UTILITY' | 'LOAN' | 'MEMBERSHIP' | 'OTHER'
export type RecurrenceDirection = 'EXPENSE' | 'INCOME'
export type RecurrenceStatus = 'DETECTED' | 'MANUAL'
export type RecurrenceDeviation = 'ON_TRACK' | 'AMOUNT_CHANGED' | 'DATE_SHIFTED' | 'OVERDUE'

export interface ExpectedSlotItem {
  expectedDate: string
  matched: boolean
  transactionId: number | null
  date: string | null
  amount: number | null
  counterpartyName: string | null
  purpose: string | null
}

export interface RecurringSeriesItem {
  id: number | null
  label: string
  type: RecurringType
  direction: RecurrenceDirection
  cadence: RecurrenceCadence
  intervalDays: number
  expectedAmount: number
  amountVariable: boolean
  currency: string
  accountIban: string
  firstSeen: string
  lastSeen: string
  occurrenceCount: number
  nextExpectedDate: string
  status: RecurrenceStatus
  fingerprint: string
  isFalsePositive: boolean
  deviation: RecurrenceDeviation
  occurrences: RecurringOccurrenceItem[]
  expectedSlots: ExpectedSlotItem[]
}

export async function fetchRecurringSeries(
  direction?: RecurrenceDirection,
  type?: RecurringType,
): Promise<RecurringSeriesItem[]> {
  const params = new URLSearchParams()
  if (direction) params.set('direction', direction)
  if (type) params.set('type', type)
  const query = params.toString()
  const res = await fetchWithUser(`/transactions/recurring${query ? `?${query}` : ''}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<RecurringSeriesItem[]>
}

export async function refreshRecurringSeries(): Promise<RecurringSeriesItem[]> {
  const res = await fetchWithUser('/transactions/recurring/refresh', { method: 'POST' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<RecurringSeriesItem[]>
}

export async function confirmRecurringSeries(
  confirmedFingerprints: string[],
  falsePositiveFingerprints: string[],
): Promise<RecurringSeriesItem[]> {
  const res = await fetchWithUser('/transactions/recurring/confirm', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ confirmedFingerprints, falsePositiveFingerprints }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<RecurringSeriesItem[]>
}

export interface CreateRecurringSeriesBody {
  label: string
  type: RecurringType
  direction: RecurrenceDirection
  cadence: RecurrenceCadence
  expectedAmount: number
  currency: string
  accountIban: string
  lastBookingDate: string | null
}

export async function createRecurringSeries(body: CreateRecurringSeriesBody): Promise<RecurringSeriesItem> {
  const res = await fetchWithUser('/transactions/recurring', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<RecurringSeriesItem>
}

export async function deleteRecurringSeries(id: number): Promise<void> {
  const res = await fetchWithUser(`/transactions/recurring/${id}`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export async function correctRecurringSeriesType(id: number, type: RecurringType): Promise<void> {
  const res = await fetchWithUser(`/transactions/recurring/${id}/type`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ type }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export interface RecurringSyncLogEntry {
  seriesId: number | null
  seriesLabel: string
  transactionId: number
  bookingDate: string
  amount: number
  counterpartyName: string | null
}

export type RecurringSyncTrigger = 'SCHEDULED' | 'MANUAL'

export interface RecurringSyncLog {
  id: number | null
  ranAt: string
  triggeredBy: RecurringSyncTrigger
  seriesUpdatedCount: number
  transactionsLinkedCount: number
  entries: RecurringSyncLogEntry[]
}

export async function fetchRecurringSyncLogs(): Promise<RecurringSyncLog[]> {
  const res = await fetchWithUser('/transactions/recurring/sync-log')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<RecurringSyncLog[]>
}

export async function triggerRecurringSync(): Promise<void> {
  const res = await fetchWithUser('/transactions/recurring/sync', { method: 'POST' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}
