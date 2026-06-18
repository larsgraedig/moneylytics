import { fetchWithUser } from './client'

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

export async function createAccount(iban: string, name: string): Promise<Account> {
  const res = await fetchWithUser('/accounts', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ iban, name }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<Account>
}

export async function updateAccount(iban: string, name: string): Promise<Account> {
  const res = await fetchWithUser(`/accounts/${encodeURIComponent(iban)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<Account>
}

export async function deleteAccount(iban: string): Promise<void> {
  const res = await fetchWithUser(`/accounts/${encodeURIComponent(iban)}`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}
