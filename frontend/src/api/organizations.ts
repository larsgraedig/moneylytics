import { fetchWithUser } from './client'

export interface OrgMember {
  userId: number
  email: string
  role: string
}

export async function createOrganization(name: string): Promise<{ id: number; name: string; role: string }> {
  const res = await fetchWithUser('/admin/organizations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
  if (!res.ok) throw new Error('Failed to create organization')
  return res.json()
}

export async function onboardOrganization(name: string): Promise<{ id: number; name: string; role: string }> {
  const res = await fetchWithUser('/organizations/onboard', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
  if (!res.ok) throw new Error('Failed to create organization')
  return res.json()
}

export async function getMembers(orgId: number): Promise<OrgMember[]> {
  const res = await fetchWithUser(`/organizations/${orgId}/members`)
  if (!res.ok) throw new Error('Failed to fetch members')
  return res.json()
}


export async function removeMember(orgId: number, userId: number): Promise<void> {
  const res = await fetchWithUser(`/organizations/${orgId}/members/${userId}`, { method: 'DELETE' })
  if (!res.ok) throw new Error('Failed to remove member')
}

export async function updateMemberRole(orgId: number, userId: number, role: string): Promise<void> {
  const res = await fetchWithUser(`/organizations/${orgId}/members/${userId}/role`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ role }),
  })
  if (!res.ok) throw new Error('Failed to update member role')
}

export async function uploadOrgLogo(orgId: number, file: File): Promise<void> {
  const form = new FormData()
  form.append('file', file)
  const res = await fetchWithUser(`/organizations/${orgId}/logo`, { method: 'POST', body: form })
  if (!res.ok) throw new Error('Failed to upload logo')
}

export async function deleteOrgLogo(orgId: number): Promise<void> {
  const res = await fetchWithUser(`/organizations/${orgId}/logo`, { method: 'DELETE' })
  if (!res.ok) throw new Error('Failed to delete logo')
}
