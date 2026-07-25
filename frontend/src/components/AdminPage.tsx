import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Play } from 'lucide-react'
import { triggerRecurringSync } from '../api/admin'

export default function AdminPage() {
  const { t } = useTranslation()
  const [running, setRunning] = useState(false)
  const [success, setSuccess] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleTrigger() {
    setRunning(true)
    setSuccess(false)
    setError(null)
    try {
      await triggerRecurringSync()
      setSuccess(true)
    } catch (e) {
      setError(e instanceof Error ? e.message : t('common.requestFailed'))
    } finally {
      setRunning(false)
    }
  }

  return (
    <div className="adm-page">
      <section className="adm-section">
        <h2 className="adm-section-title">{t('admin.recurring.title')}</h2>
        <p className="adm-description">{t('admin.recurring.description')}</p>
        <div className="adm-action-row">
          <button className="adm-trigger-btn" onClick={handleTrigger} disabled={running}>
            <Play size={14} />
            {running ? t('admin.recurring.triggering') : t('admin.recurring.triggerSync')}
          </button>
          {success && <span className="adm-feedback adm-feedback--ok">{t('admin.recurring.success')}</span>}
          {error && <span className="adm-feedback adm-feedback--error">{error}</span>}
        </div>
      </section>
    </div>
  )
}
