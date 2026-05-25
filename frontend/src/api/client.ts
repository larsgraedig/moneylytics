const USER_ID = 'default'

export function fetchWithUser(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  return fetch(input, {
    ...init,
    headers: {
      ...init?.headers,
      'X-User-Id': USER_ID,
    },
  })
}
