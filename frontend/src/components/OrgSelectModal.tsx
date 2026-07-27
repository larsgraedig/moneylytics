import { useTranslation } from 'react-i18next'
import type { Organization } from '../context/AuthContext'

interface Props {
  organizations: Organization[]
  onSelect: (orgId: number) => Promise<void>
}

export default function OrgSelectModal({ organizations, onSelect }: Props) {
  const { t } = useTranslation()

  return (
    <div className="onboarding-backdrop">
      <div className="onboarding-modal">
        <h1 className="onboarding-title">{t('orgSelect.title')}</h1>
        <p className="onboarding-subtitle">{t('orgSelect.subtitle')}</p>
        <div className="org-select-list">
          {organizations.map(org => (
            <button
              key={org.id}
              className="org-select-card"
              onClick={() => onSelect(org.id)}
            >
              <span className="org-select-name">{org.name}</span>
              <span className="org-select-role">{org.role}</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
