let currentUserId = 'default'

export function setCurrentUser(id: string) {
  currentUserId = id
}

export function fetchWithUser(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  return fetch(input, {
    ...init,
    headers: {
      ...init?.headers,
      'X-User-Id': currentUserId,
    },
  })
}
