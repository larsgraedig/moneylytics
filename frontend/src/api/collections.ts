import { fetchWithUser } from './client'
import type { TransactionItem } from './transactions'

export interface CollectionDto {
  id: number
  name: string
  note: string | null
  transactions: TransactionItem[]
}

export async function fetchCollections(): Promise<CollectionDto[]> {
  const res = await fetchWithUser('/collections')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const data: { collections: CollectionDto[] } = await res.json()
  return data.collections
}

export async function createCollection(name: string, note: string | null): Promise<CollectionDto> {
  const res = await fetchWithUser('/collections', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, note }),
  })
  if (!res.ok) throw new Error('create failed')
  return res.json()
}

export async function updateCollection(id: number, name: string, note: string | null): Promise<CollectionDto> {
  const res = await fetchWithUser(`/collections/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, note }),
  })
  if (!res.ok) throw new Error('update failed')
  return res.json()
}

export async function deleteCollection(id: number): Promise<void> {
  const res = await fetchWithUser(`/collections/${id}`, { method: 'DELETE' })
  if (!res.ok) throw new Error('delete failed')
}

export async function addTransactionToCollection(collectionId: number, transactionId: number): Promise<void> {
  const res = await fetchWithUser(`/collections/${collectionId}/transactions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ transactionId }),
  })
  if (!res.ok) throw new Error('add failed')
}

export async function removeTransactionFromCollection(collectionId: number, transactionId: number): Promise<void> {
  const res = await fetchWithUser(`/collections/${collectionId}/transactions/${transactionId}`, { method: 'DELETE' })
  if (!res.ok) throw new Error('remove failed')
}
