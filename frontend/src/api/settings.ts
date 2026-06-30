import { fetchWithUser } from './client'

export interface UserSettings {
  defaultAccountIban: string | null
  language: string | null
  transactionsColumnOrder: string[] | null
}

export async function fetchUserSettings(): Promise<UserSettings> {
  const res = await fetchWithUser('/users/settings')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<UserSettings>
}

export async function updateUserSettings(patch: Partial<UserSettings>): Promise<UserSettings> {
  const res = await fetchWithUser('/users/settings', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ defaultAccountIban: patch.defaultAccountIban ?? null, language: patch.language ?? null, transactionsColumnOrder: patch.transactionsColumnOrder ?? null }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<UserSettings>
}
