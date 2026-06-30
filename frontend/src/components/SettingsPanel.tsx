import { useState } from 'react'
import { X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { Account } from '../api/accounts'
import { updateUserSettings } from '../api/settings'

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
    <>
      <div className="settings-backdrop" onClick={onClose} />
      <div className="settings-panel">
        <div className="settings-header">
          <span className="settings-title">{t('settings.title')}</span>
          <button className="settings-close" onClick={onClose} title={t('common.close')}>
            <X size={15} strokeWidth={1.8} />
          </button>
        </div>

        <div className="settings-body">
          <section className="settings-section">
            <h3 className="settings-section-label">{t('settings.language')}</h3>
            <div className="settings-lang-group">
              {(['de', 'en'] as const).map(lang => (
                <button
                  key={lang}
                  className={`settings-lang-btn${i18n.language === lang ? ' active' : ''}`}
                  onClick={() => setLang(lang)}
                >
                  {lang.toUpperCase()}
                </button>
              ))}
            </div>
          </section>

          {accounts.length > 0 && (
            <section className="settings-section">
              <h3 className="settings-section-label">{t('settings.defaultAccount')}</h3>
              <select
                className="account-select"
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
      </div>
    </>
  )
}
