import { fetchWithUser } from './client'

export async function triggerRecurringSync(): Promise<void> {
  const res = await fetchWithUser('/admin/recurring/sync', { method: 'POST' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export async function listAdminUsers(): Promise<string[]> {
  const res = await fetchWithUser('/admin/users')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const data = await res.json() as { users: string[] }
  return data.users
}

export async function impersonateUser(externalId: string): Promise<void> {
  const res = await fetchWithUser(`/admin/impersonate/${encodeURIComponent(externalId)}`, { method: 'POST' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export async function deimpersonateUser(): Promise<void> {
  await fetchWithUser('/admin/impersonate', { method: 'DELETE' })
}
