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

export interface OffsetLinkItem {
  id: number
  linkedTransactionId: number
  linkedTransactionAmount: number
  partialAmount: number | null
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
  return offsetLinks.reduce((acc, link) => {
    const offsetAmt = link.partialAmount !== null ? link.partialAmount : Math.abs(link.linkedTransactionAmount)
    const contribution = link.linkedTransactionAmount >= 0 ? offsetAmt : -offsetAmt
    return acc + contribution
  }, amount)
}

export async function fetchTransactionList(
  from: string,
  to: string,
  category?: string,
  subcategory?: string,
  iban?: string,
): Promise<TransactionListResponse> {
  const params = new URLSearchParams({ from, to })
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
): Promise<TransactionListResponse> {
  const params = new URLSearchParams({ from, to, onlyNegative: 'false' })
  if (iban) params.set('iban', iban)
  if (category) params.set('category', category)
  if (subcategory) params.set('subcategory', subcategory)
  if (categoryGroup) params.set('categoryGroup', categoryGroup)
  if (uncategorized) params.set('uncategorized', 'true')
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
  id: number
  transactionAId: number
  transactionBId: number
  partialAmount: number | null
}

export async function linkTransactions(
  transactionId: number,
  otherTransactionId: number,
  partialAmount?: number,
): Promise<OffsetLinkResult> {
  const res = await fetchWithUser(`/transactions/${transactionId}/offsets`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ otherTransactionId, partialAmount: partialAmount ?? null }),
  })
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
  groupKey: number
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

export async function updateLinkedGroupMeta(groupKey: number, name: string | null, comment: string | null): Promise<void> {
  const res = await fetchWithUser(`/transactions/linked/${groupKey}`, {
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

export async function fetchSankeyData(from: string, to: string, iban?: string): Promise<SankeyResponse> {
  const params = new URLSearchParams({ from, to })
  if (iban) params.set('iban', iban)
  const res = await fetchWithUser(`/transactions/sankey?${params}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<SankeyResponse>
}
