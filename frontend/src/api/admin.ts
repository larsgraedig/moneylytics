import { fetchWithUser } from './client'

export interface AdminOrgGroup {
  id: number
  name: string
  members: string[]
}

export interface AdminUsersResponse {
  organizations: AdminOrgGroup[]
  unorganized: string[]
}

export async function triggerRecurringSync(): Promise<void> {
  const res = await fetchWithUser('/admin/recurring/sync', { method: 'POST' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export async function listAdminUsers(): Promise<AdminUsersResponse> {
  const res = await fetchWithUser('/admin/users')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<AdminUsersResponse>
}

export async function impersonateUser(externalId: string): Promise<void> {
  const res = await fetchWithUser(`/admin/impersonate/${encodeURIComponent(externalId)}`, { method: 'POST' })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
}

export async function deimpersonateUser(): Promise<void> {
  await fetchWithUser('/admin/impersonate', { method: 'DELETE' })
}
