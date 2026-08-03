import { fetchWithUser } from './client'

export interface SankeyNode {
  name: string
  value: number
  nodeKey: string
  categoryId: number
  namePath: string[]
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

export interface BudgetLinkSummary {
  linkId: number
  budgetId: number
  budgetName: string
  amount: number | null
}

export interface BulkCategoryUpdate {
  id: number
  categoryId: number | null
}

export interface TransactionItem {
  id: number
  bookingDate: string
  accountingDate: string
  accountIban: string
  categoryId: number | null
  category: string | null
  subcategory: string | null
  group: string | null
  amount: number
  effectiveAmount: number
  currency: string
  offsetLinks: OffsetLinkItem[]
  groups: GroupSummary[]
  collections: CollectionSummary[]
  budgetLinks: BudgetLinkSummary[]
  comment: string | null
  purpose: string | null
  counterpartyName: string | null
  counterpartyIban: string | null
  parentId: number | null
  isVirtual: boolean
  excluded: boolean
}

export interface SplitItemRequest {
  amount: number
  categoryId?: number | null
  comment?: string | null
}

export interface SubTransactionGroupResponse {
  parent: TransactionItem
  children: TransactionItem[]
}

export interface TransactionListResponse {
  transactions: TransactionItem[]
  total: number
}

export async function fetchTransactionList(
  from: string,
  to: string,
  category?: string,
  subcategory?: string,
  iban?: string,
  group?: string,
  categoryId?: number,
): Promise<TransactionListResponse> {
  const params = new URLSearchParams({ from, to, type: 'EXPENSES' })
  if (categoryId != null) params.set('categoryId', String(categoryId))
  if (category) params.set('category', category)
  if (subcategory) params.set('subcategory', subcategory)
  if (group) params.set('group', group)
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
  group?: string,
  type?: 'ALL' | 'INCOME' | 'EXPENSES',
  excludeCollectionId?: number,
  excludeBudgetId?: number,
  categoryId?: number,
): Promise<TransactionListResponse> {
  const params = new URLSearchParams({ from, to })
  if (iban) params.set('iban', iban)
  if (category) params.set('category', category)
  if (subcategory) params.set('subcategory', subcategory)
  if (group) params.set('group', group)
  if (uncategorized) params.set('uncategorized', 'true')
  if (type) params.set('type', type)
  if (excludeCollectionId != null) params.set('excludeCollectionId', String(excludeCollectionId))
  if (excludeBudgetId != null) params.set('excludeBudgetId', String(excludeBudgetId))
  if (categoryId != null) params.set('categoryId', String(categoryId))
  const res = await fetchWithUser(`/transactions/list?${params}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<TransactionListResponse>
}

export async function updateTransactionCategory(
  id: number,
  categoryId: number | null,
): Promise<TransactionItem> {
  const res = await fetchWithUser(`/transactions/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ categoryId }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<TransactionItem>
}

export interface LinkTransactionResult {
  groupId: number
  sourceTransaction: TransactionItem
  otherTransaction: TransactionItem
}

export async function linkTransactions(
  transactionId: number,
  otherTransactionId: number,
  myAmount?: number,
  otherAmount?: number,
  targetGroupId?: number,
  forceNewGroup?: boolean,
): Promise<LinkTransactionResult> {
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
  return res.json() as Promise<LinkTransactionResult>
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

export async function bulkUpdateTransactionCategory(updates: BulkCategoryUpdate[]): Promise<TransactionItem[]> {
  const res = await fetchWithUser('/transactions/bulk', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ updates }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<TransactionItem[]>
}

export async function updateOffsetLinkComment(linkId: number, comment: string | null): Promise<void> {
  const res = await fetchWithUser(`/transactions/offsets/${linkId}/comment`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ comment }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export interface CashflowBucketDto {
  key: string
  incomeGross: number
  incomeNet: number
  expensesGross: number
  expensesNet: number
  net: number
}

export interface CashflowResponseDto {
  granularity: string
  buckets: CashflowBucketDto[]
}

export async function fetchCashflow(
  from: string,
  to: string,
  granularity: 'monthly' | 'yearly',
  iban?: string,
): Promise<CashflowResponseDto> {
  const params = new URLSearchParams({ from, to, granularity: granularity.toUpperCase() })
  if (iban) params.set('iban', iban)
  const res = await fetchWithUser(`/transactions/cashflow?${params}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<CashflowResponseDto>
}

export interface BurnRatePointDto {
  date: string
  expenses: number
  rollingAvg: number
  cumulative: number
  cumulativeIncome: number
}

export interface BurnRateResponseDto {
  points: BurnRatePointDto[]
  totalExpenses: number
  totalIncome: number
  avgPerDay: number
}

export async function fetchBurnRate(
  from: string,
  to: string,
  rollingWindow: number,
  iban?: string,
): Promise<BurnRateResponseDto> {
  const params = new URLSearchParams({ from, to, rollingWindow: String(rollingWindow) })
  if (iban) params.set('iban', iban)
  const res = await fetchWithUser(`/transactions/burnrate?${params}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<BurnRateResponseDto>
}

export interface CategoryTotalItem {
  name: string
  value: number
  categoryId?: number
}

export interface CategoryTotalsResponseDto {
  items: CategoryTotalItem[]
}

export async function fetchCategoryTotals(
  from: string,
  to: string,
  iban?: string,
  category?: string,
  categoryId?: number,
): Promise<CategoryTotalsResponseDto> {
  const params = new URLSearchParams({ from, to })
  if (iban) params.set('iban', iban)
  if (categoryId != null) params.set('categoryId', String(categoryId))
  else if (category) params.set('category', category)
  const res = await fetchWithUser(`/transactions/category-totals?${params}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<CategoryTotalsResponseDto>
}

export async function fetchSankeyData(from: string, to: string, iban?: string): Promise<SankeyResponse> {
  const params = new URLSearchParams({ from, to })
  if (iban) params.set('iban', iban)
  const res = await fetchWithUser(`/transactions/sankey?${params}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<SankeyResponse>
}

export async function splitTransaction(
  transactionId: number,
  splits: SplitItemRequest[],
): Promise<SubTransactionGroupResponse> {
  const res = await fetchWithUser(`/transactions/${transactionId}/split`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ splits }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<SubTransactionGroupResponse>
}

export async function unsplitTransaction(transactionId: number): Promise<void> {
  const res = await fetchWithUser(`/transactions/${transactionId}/split`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export async function mergeTransactions(
  transactionIds: number[],
  accountingDate: string,
  name?: string | null,
  comment?: string | null,
): Promise<SubTransactionGroupResponse> {
  const res = await fetchWithUser('/transactions/merge', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ transactionIds, accountingDate, name: name ?? null, comment: comment ?? null }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<SubTransactionGroupResponse>
}

export async function addToMerge(parentId: number, transactionId: number): Promise<SubTransactionGroupResponse> {
  const res = await fetchWithUser(`/transactions/${parentId}/merge/${transactionId}`, { method: 'POST' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<SubTransactionGroupResponse>
}

export async function removeFromMerge(parentId: number, transactionId: number): Promise<SubTransactionGroupResponse> {
  const res = await fetchWithUser(`/transactions/${parentId}/merge/${transactionId}`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<SubTransactionGroupResponse>
}

export async function unmergeTransactions(parentId: number): Promise<void> {
  const res = await fetchWithUser(`/transactions/${parentId}/merge`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export async function createVirtualTransaction(data: {
  amount: number
  currency?: string
  accountIban: string
  accountingDate: string
  categoryId?: number | null
  counterpartyName?: string | null
  purpose?: string | null
}): Promise<TransactionItem> {
  const res = await fetchWithUser('/transactions/virtual', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ currency: 'EUR', ...data }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<TransactionItem>
}

export async function fetchSubTransactionGroup(transactionId: number): Promise<SubTransactionGroupResponse | null> {
  const res = await fetchWithUser(`/transactions/${transactionId}/sub-group`)
  if (res.status === 404) return null
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<SubTransactionGroupResponse>
}
