import { createContext, useContext, type ReactNode } from 'react'
import { useAuth } from './AuthContext'

interface UserContextValue {
  userId: string
}

const UserContext = createContext<UserContextValue>({ userId: '' })

export function UserProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  return (
    <UserContext.Provider value={{ userId: user?.sub ?? '' }}>
      {children}
    </UserContext.Provider>
  )
}

export function useUser() {
  return useContext(UserContext)
}
