import { fetchWithUser } from './client'

export async function fetchBackendEnvironment(): Promise<Record<string, string>> {
  const res = await fetchWithUser('/local/environment')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<Record<string, string>>
}
