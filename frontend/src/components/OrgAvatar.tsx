import { useEffect, useRef, useState } from 'react'
import type { Organization } from '../context/AuthContext'

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
  const [open, setOpen] = useState(false)
  const wrapRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    function handleMouseDown(e: MouseEvent) {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleMouseDown)
    return () => document.removeEventListener('mousedown', handleMouseDown)
  }, [open])

  if (!activeOrganization) return null

  const initials = getInitials(activeOrganization.name)
  const isMulti = organizations.length > 1

  const avatarContent = activeOrganization.logoUrl ? (
    <img
      className="org-avatar__img"
      src={activeOrganization.logoUrl}
      alt={activeOrganization.name}
    />
  ) : (
    <span className="org-avatar__initials">{initials}</span>
  )

  if (!isMulti) {
    return (
      <div className="org-avatar-wrap">
        <div className="org-avatar" title={activeOrganization.name}>
          {avatarContent}
        </div>
      </div>
    )
  }

  return (
    <div className="org-avatar-wrap" ref={wrapRef}>
      <button
        className="org-avatar org-avatar--clickable"
        onClick={() => setOpen(o => !o)}
        title={activeOrganization.name}
        type="button"
      >
        {avatarContent}
      </button>
      {open && (
        <div className="org-avatar-menu">
          {organizations.map(org => (
            <button
              key={org.id}
              type="button"
              className={`org-avatar-menu-item${org.id === activeOrganization.id ? ' org-avatar-menu-item--active' : ''}`}
              onClick={() => {
                setOpen(false)
                if (org.id !== activeOrganization.id) onSwitch(org.id)
              }}
            >
              {org.name}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
