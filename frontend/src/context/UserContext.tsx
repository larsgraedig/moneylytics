import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { setCurrentUser } from '../api/client'

async function fetchUsers(): Promise<string[]> {
  const res = await fetch('/users')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const data = await res.json() as { users: string[] }
  return data.users
}

interface UserContextValue {
  userId: string
  users: string[]
  setUserId: (id: string) => void
}

const UserContext = createContext<UserContextValue>({
  userId: 'default',
  users: [],
  setUserId: () => {},
})

export function UserProvider({ children }: { children: ReactNode }) {
  const [userId, setUserIdState] = useState('default')
  const [users, setUsers] = useState<string[]>([])

  useEffect(() => {
    setCurrentUser(userId)
    fetchUsers().then(setUsers).catch(() => {})
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  function setUserId(id: string) {
    setUserIdState(id)
    setCurrentUser(id)
  }

  return (
    <UserContext.Provider value={{ userId, users, setUserId }}>
      {children}
    </UserContext.Provider>
  )
}

export function useUser() {
  return useContext(UserContext)
}
