import { fetchWithUser } from './client'

export interface BudgetTransactionLink {
  id: number
  transactionId: number
  amount: number | null
  transactionAmount: number
  transactionDate: string
  transactionCategory: string
  transactionSubcategory: string
  transactionPurpose: string | null
  transactionComment: string | null
}

export interface Budget {
  id: number
  name: string
  targetAmount: number | null
  note: string | null
  balance: number
  transactionLinks: BudgetTransactionLink[]
}

export async function fetchBudgets(): Promise<Budget[]> {
  const res = await fetchWithUser('/budgets')
  if (!res.ok) throw new Error('fetch failed')
  const data: { budgets: Budget[] } = await res.json()
  return data.budgets
}

export async function createBudget(
  name: string,
  targetAmount: number | null,
  note: string | null,
): Promise<Budget> {
  const res = await fetchWithUser('/budgets', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, targetAmount, note }),
  })
  if (!res.ok) throw new Error('create failed')
  return res.json()
}

export async function updateBudget(
  id: number,
  name: string,
  targetAmount: number | null,
  note: string | null,
): Promise<Budget> {
  const res = await fetchWithUser(`/budgets/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, targetAmount, note }),
  })
  if (!res.ok) throw new Error('update failed')
  return res.json()
}

export async function deleteBudget(id: number): Promise<void> {
  const res = await fetchWithUser(`/budgets/${id}`, { method: 'DELETE' })
  if (!res.ok) throw new Error('delete failed')
}

export async function assignTransaction(
  budgetId: number,
  transactionId: number,
  amount: number | null,
): Promise<BudgetTransactionLink> {
  const res = await fetchWithUser(`/budgets/${budgetId}/transactions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ transactionId, amount }),
  })
  if (!res.ok) throw new Error('assign failed')
  return res.json()
}

export async function removeTransactionLink(linkId: number): Promise<void> {
  const res = await fetchWithUser(`/budgets/transactions/${linkId}`, { method: 'DELETE' })
  if (!res.ok) throw new Error('remove failed')
}
