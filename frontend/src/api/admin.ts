import { fetchWithUser } from './client'

export async function triggerRecurringSync(): Promise<void> {
  const res = await fetchWithUser('/admin/recurring/sync', { method: 'POST' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}
