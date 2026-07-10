import { fetchWithUser } from './client'

export interface SankeyNode {
  name: string
  value: number
  nodeKey: string
}

export interface SankeyLink {
  source: number
  target: number
  value: number
}

export interface SankeyResponse {
  nodes: SankeyNode[]
  links: SankeyLink[]
}

export interface Account {
  iban: string
  name: string
}

export async function fetchAccounts(): Promise<Account[]> {
  const res = await fetchWithUser('/accounts')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const data = await res.json() as { accounts: Account[] }
  return data.accounts
}

export interface GroupSummary {
  id: number
  name: string | null
}

export interface OffsetLinkItem {
  id: number
  linkedTransactionId: number
  linkedTransactionAmount: number
  amountA: number | null
  amountB: number | null
  committedAmount: number
  comment: string | null
  groupId: number | null
}

export interface AllocationError {
  transactionId: number
  maxRemainingAmount: number
  existingLinks: Array<{ linkId: number; linkedTransactionId: number; committedAmount: number }>
}

export class AllocationExceededError extends Error {
  constructor(public readonly data: AllocationError) {
    super('Allocation exceeded')
  }
}

export interface CollectionSummary {
  id: number
  name: string
}

export interface TransactionItem {
  id: number
  bookingDate: string
  accountingDate: string
  accountIban: string
  category: string
  subcategory: string
  categoryGroup: string | null
  amount: number
  effectiveAmount: number
  currency: string
  offsetLinks: OffsetLinkItem[]
  groups: GroupSummary[]
  collections: CollectionSummary[]
  comment: string | null
  purpose: string | null
  counterpartyName: string | null
  counterpartyIban: string | null
}

export interface TransactionListResponse {
  transactions: TransactionItem[]
  total: number
}

export function computeEffectiveAmount(amount: number, offsetLinks: OffsetLinkItem[]): number {
  if (offsetLinks.length === 0) return amount
  const totalOffset = offsetLinks.reduce((acc, link) => {
    const offset = (link.amountA !== null && link.amountB !== null)
      ? Math.min(Math.abs(link.amountA), Math.abs(link.amountB))
      : (link.amountA === null && link.amountB === null)
        ? 0
        : Math.min(Math.abs(link.committedAmount), Math.abs(link.linkedTransactionAmount))
    return acc + offset
  }, 0)
  return amount >= 0 ? amount - totalOffset : amount + totalOffset
}

export async function fetchTransactionList(
  from: string,
  to: string,
  category?: string,
  subcategory?: string,
  iban?: string,
): Promise<TransactionListResponse> {
  const params = new URLSearchParams({ from, to, type: 'EXPENSES' })
  if (category) params.set('category', category)
  if (subcategory) params.set('subcategory', subcategory)
  if (iban) params.set('iban', iban)
  const res = await fetchWithUser(`/transactions/list?${params}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<TransactionListResponse>
}

export async function fetchAllTransactions(
  from: string,
  to: string,
  iban?: string,
  category?: string,
  subcategory?: string,
  uncategorized?: boolean,
  categoryGroup?: string,
  type?: 'ALL' | 'INCOME' | 'EXPENSES',
  excludeCollectionId?: number,
  excludeBudgetId?: number,
): Promise<TransactionListResponse> {
  const params = new URLSearchParams({ from, to })
  if (iban) params.set('iban', iban)
  if (category) params.set('category', category)
  if (subcategory) params.set('subcategory', subcategory)
  if (categoryGroup) params.set('categoryGroup', categoryGroup)
  if (uncategorized) params.set('uncategorized', 'true')
  if (type) params.set('type', type)
  if (excludeCollectionId != null) params.set('excludeCollectionId', String(excludeCollectionId))
  if (excludeBudgetId != null) params.set('excludeBudgetId', String(excludeBudgetId))
  const res = await fetchWithUser(`/transactions/list?${params}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<TransactionListResponse>
}

export async function updateTransactionCategory(
  id: number,
  category: string,
  subcategory: string,
  categoryGroup?: string | null,
): Promise<TransactionItem> {
  const res = await fetchWithUser(`/transactions/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ category, subcategory, categoryGroup: categoryGroup ?? null }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<TransactionItem>
}

export interface OffsetLinkResult {
  id: number | null
  transactionAId: number
  transactionBId: number
  amountA: number | null
  amountB: number | null
  groupId: number
}

export async function linkTransactions(
  transactionId: number,
  otherTransactionId: number,
  myAmount?: number,
  otherAmount?: number,
  targetGroupId?: number,
  forceNewGroup?: boolean,
): Promise<OffsetLinkResult> {
  const res = await fetchWithUser(`/transactions/${transactionId}/offsets`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      otherTransactionId,
      myAmount: myAmount ?? null,
      otherAmount: otherAmount ?? null,
      targetGroupId: targetGroupId ?? null,
      forceNewGroup: forceNewGroup ?? false,
    }),
  })
  if (res.status === 422) {
    const data = await res.json() as AllocationError
    throw new AllocationExceededError(data)
  }
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<OffsetLinkResult>
}

export async function updateTransactionComment(
  id: number,
  comment: string | null,
): Promise<TransactionItem> {
  const res = await fetchWithUser(`/transactions/${id}/comment`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ comment }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<TransactionItem>
}

export interface LinkedGroupItem {
  groupId: number
  name: string | null
  comment: string | null
  transactions: TransactionItem[]
}

export interface LinkedGroupsResponse {
  groups: LinkedGroupItem[]
}

export async function fetchLinkedGroups(): Promise<LinkedGroupsResponse> {
  const res = await fetchWithUser('/transactions/linked')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<LinkedGroupsResponse>
}

export async function fetchLinkedGroup(groupId: number): Promise<LinkedGroupItem> {
  const res = await fetchWithUser(`/transactions/linked/${groupId}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<LinkedGroupItem>
}

export async function updateLinkedGroupMeta(groupId: number, name: string | null, comment: string | null): Promise<void> {
  const res = await fetchWithUser(`/transactions/linked/${groupId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, comment }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export async function updateTransactionAccountingDate(
  id: number,
  accountingDate: string,
): Promise<TransactionItem> {
  const res = await fetchWithUser(`/transactions/${id}/accounting-date`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ accountingDate }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<TransactionItem>
}

export async function unlinkTransaction(linkId: number): Promise<void> {
  const res = await fetchWithUser(`/transactions/offsets/${linkId}`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export async function removeTransactionFromGroup(txId: number, groupId: number): Promise<void> {
  const res = await fetchWithUser(`/transactions/${txId}/groups/${groupId}`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export async function updateOffsetLinkComment(linkId: number, comment: string | null): Promise<void> {
  const res = await fetchWithUser(`/transactions/offsets/${linkId}/comment`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ comment }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export async function fetchSankeyData(from: string, to: string, iban?: string): Promise<SankeyResponse> {
  const params = new URLSearchParams({ from, to })
  if (iban) params.set('iban', iban)
  const res = await fetchWithUser(`/transactions/sankey?${params}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<SankeyResponse>
}
