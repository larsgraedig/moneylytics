import { useTranslation } from 'react-i18next'
import type { Organization } from '../context/AuthContext'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'

interface Props {
  organizations: Organization[]
  onSelect: (orgId: number) => Promise<void>
}

export default function OrgSelectModal({ organizations, onSelect }: Props) {
  const { t } = useTranslation()

  return (
    <Dialog open>
      <DialogContent showCloseButton={false}>
        <DialogHeader>
          <DialogTitle>{t('orgSelect.title')}</DialogTitle>
          <DialogDescription>{t('orgSelect.subtitle')}</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-2">
          {organizations.map(org => (
            <Button
              key={org.id}
              variant="outline"
              className="flex h-auto flex-col items-start gap-0.5 px-4 py-3 text-left"
              onClick={() => onSelect(org.id)}
            >
              <span className="font-medium">{org.name}</span>
              <span className="text-xs text-muted-foreground">{org.role}</span>
            </Button>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  )
}
