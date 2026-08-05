import { fetchWithUser } from './client'

export interface CalendarDaySum {
  day: string
  value: number
}

export interface CalendarSumsResponse {
  data: CalendarDaySum[]
}

export async function fetchCalendarSums(
  from: string,
  to: string,
  iban?: string,
): Promise<CalendarSumsResponse> {
  const params = new URLSearchParams({ from, to })
  if (iban) params.set('iban', iban)
  const res = await fetchWithUser(`/transactions/calendar?${params}`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<CalendarSumsResponse>
}
