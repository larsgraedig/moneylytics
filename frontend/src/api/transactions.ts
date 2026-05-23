export interface SankeyNode {
  name: string
  value: number
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

export async function fetchSankeyData(from: string, to: string, iban?: string): Promise<SankeyResponse> {
  const params = new URLSearchParams({ from, to })
  if (iban) params.set('iban', iban)
  const res = await fetch(`/transactions/sankey?${params}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<SankeyResponse>
}
