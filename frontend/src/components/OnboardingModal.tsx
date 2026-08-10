import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { onboardOrganization } from '../api/organizations'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'

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
    <Dialog open>
      <DialogContent showCloseButton={false}>
        <DialogHeader>
          <DialogTitle>{t('onboarding.title')}</DialogTitle>
          <DialogDescription>{t('onboarding.subtitle')}</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-3">
          <Input
            type="text"
            placeholder={t('onboarding.placeholder')}
            value={name}
            onChange={e => setName(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleCreate()}
            autoFocus
          />
          <Button onClick={handleCreate} disabled={!name.trim() || loading}>
            {loading ? '…' : t('onboarding.button')}
          </Button>
          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>
      </DialogContent>
    </Dialog>
  )
}
