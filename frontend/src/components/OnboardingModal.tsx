import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { onboardOrganization } from '../api/organizations'

interface Props {
  onComplete: () => Promise<void>
}

export default function OnboardingModal({ onComplete }: Props) {
  const { t } = useTranslation()
  const [name, setName] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleCreate() {
    if (!name.trim()) return
    setLoading(true)
    setError(null)
    try {
      await onboardOrganization(name.trim())
      await onComplete()
    } catch {
      setError(t('onboarding.error'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="onboarding-backdrop">
      <div className="onboarding-modal">
        <h1 className="onboarding-title">{t('onboarding.title')}</h1>
        <p className="onboarding-subtitle">{t('onboarding.subtitle')}</p>
        <div className="onboarding-form">
          <input
            className="acc-input onboarding-input"
            type="text"
            placeholder={t('onboarding.placeholder')}
            value={name}
            onChange={e => setName(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleCreate()}
            autoFocus
          />
          <button
            className="onboarding-btn"
            onClick={handleCreate}
            disabled={!name.trim() || loading}
          >
            {loading ? '…' : t('onboarding.button')}
          </button>
        </div>
        {error && <p className="onboarding-error">{error}</p>}
      </div>
    </div>
  )
}
