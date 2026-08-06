import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Account } from '../api/accounts'
import { updateUserSettings } from '../api/settings'
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface Props {
  accounts: Account[]
  defaultAccountIban: string
  onClose: () => void
}

export default function SettingsPanel({ accounts, defaultAccountIban: initialIban, onClose }: Props) {
  const { t, i18n } = useTranslation()
  const [defaultIban, setDefaultIban] = useState(initialIban)

  function setLang(lang: string) {
    i18n.changeLanguage(lang)
    localStorage.setItem('lang', lang)
    updateUserSettings({ language: lang, defaultAccountIban: defaultIban || null }).catch(() => {})
  }

  function updateDefaultAccount(iban: string) {
    setDefaultIban(iban)
    if (iban) {
      localStorage.setItem('defaultIban', iban)
    } else {
      localStorage.removeItem('defaultIban')
    }
    updateUserSettings({ defaultAccountIban: iban || null, language: i18n.language }).catch(() => {})
  }

  return (
    <Sheet open onOpenChange={open => { if (!open) onClose() }}>
      <SheetContent side="right" className="w-72 p-0">
        <SheetHeader className="border-b px-5 py-4">
          <SheetTitle>{t('settings.title')}</SheetTitle>
        </SheetHeader>

        <div className="flex flex-col gap-6 px-5 py-6">
          <section className="flex flex-col gap-2">
            <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
              {t('settings.language')}
            </p>
            <div className="flex gap-2">
              {(['de', 'en'] as const).map(lang => (
                <Button
                  key={lang}
                  variant={i18n.language === lang ? 'default' : 'outline'}
                  size="sm"
                  onClick={() => setLang(lang)}
                  className={cn(i18n.language === lang && 'pointer-events-none')}
                >
                  {lang.toUpperCase()}
                </Button>
              ))}
            </div>
          </section>

          {accounts.length > 0 && (
            <section className="flex flex-col gap-2">
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
                {t('settings.defaultAccount')}
              </p>
              <select
                className="w-full rounded-lg border border-input bg-input/30 px-3 py-2 text-sm outline-none focus:border-ring focus:ring-2 focus:ring-ring/50"
                value={defaultIban}
                onChange={e => updateDefaultAccount(e.target.value)}
              >
                <option value="">{t('settings.noDefaultAccount')}</option>
                {accounts.map(a => (
                  <option key={a.iban} value={a.iban}>{a.name}</option>
                ))}
              </select>
            </section>
          )}
        </div>
      </SheetContent>
    </Sheet>
  )
}
