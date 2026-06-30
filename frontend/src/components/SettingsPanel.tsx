import { X } from 'lucide-react'
import { useTranslation } from 'react-i18next'

interface Props {
  onClose: () => void
}

export default function SettingsPanel({ onClose }: Props) {
  const { t, i18n } = useTranslation()

  function setLang(lang: string) {
    i18n.changeLanguage(lang)
    localStorage.setItem('lang', lang)
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
        </div>
      </div>
    </>
  )
}
