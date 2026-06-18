export function fetchWithUser(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  return fetch(input, init)
}
