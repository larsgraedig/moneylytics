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
  const res = await fetch('/accounts')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const data = await res.json() as { accounts: Account[] }
  return data.accounts
}

export interface TransactionItem {
  bookingDate: string
  category: string
  subcategory: string
  amount: number
  currency: string
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
): Promise<TransactionListResponse> {
  const params = new URLSearchParams({ from, to })
  if (category) params.set('category', category)
  if (subcategory) params.set('subcategory', subcategory)
  if (iban) params.set('iban', iban)
  const res = await fetch(`/transactions/list?${params}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<TransactionListResponse>
}

export async function fetchSankeyData(from: string, to: string, iban?: string): Promise<SankeyResponse> {
  const params = new URLSearchParams({ from, to })
  if (iban) params.set('iban', iban)
  const res = await fetch(`/transactions/sankey?${params}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<SankeyResponse>
}
