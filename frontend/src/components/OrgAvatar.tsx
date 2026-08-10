import type { Organization } from '../context/AuthContext'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'

interface Props {
  organizations: Organization[]
  activeOrganization: Organization | null
  onSwitch: (orgId: number) => void
}

function getInitials(name: string): string {
  const words = name.trim().split(/\s+/)
  if (words.length === 1) return words[0].charAt(0).toUpperCase()
  return (words[0].charAt(0) + words[1].charAt(0)).toUpperCase()
}

export default function OrgAvatar({ organizations, activeOrganization, onSwitch }: Props) {
  if (!activeOrganization) return null

  const initials = getInitials(activeOrganization.name)
  const isMulti = organizations.length > 1

  const avatar = (
    <Avatar title={activeOrganization.name}>
      {activeOrganization.logoUrl && <AvatarImage src={activeOrganization.logoUrl} alt={activeOrganization.name} />}
      <AvatarFallback>{initials}</AvatarFallback>
    </Avatar>
  )

  if (!isMulti) return <div className="flex items-center">{avatar}</div>

  return (
    <DropdownMenu>
      <DropdownMenuTrigger className="rounded-full focus:outline-none focus-visible:ring-2 focus-visible:ring-ring">
        {avatar}
      </DropdownMenuTrigger>
      <DropdownMenuContent side="bottom" align="end">
        {organizations.map(org => (
          <DropdownMenuItem
            key={org.id}
            className={org.id === activeOrganization.id ? 'font-medium' : ''}
            onClick={() => { if (org.id !== activeOrganization.id) onSwitch(org.id) }}
          >
            {org.name}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
