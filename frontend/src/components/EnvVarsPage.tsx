import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { fetchBackendEnvironment } from '../api/environment'

const frontendEnv: Record<string, string> = Object.fromEntries(
  Object.entries(import.meta.env).map(([key, value]) => [key, String(value)]),
)

function EnvTable({ entries }: { entries: Record<string, string> }) {
  const sortedKeys = Object.keys(entries).sort((a, b) => a.localeCompare(b))
  return (
    <div className="overflow-auto rounded-lg border">
      <table className="w-full text-sm">
        <tbody>
          {sortedKeys.map(key => (
            <tr key={key} className="border-b last:border-b-0">
              <td className="whitespace-nowrap px-3 py-1.5 font-mono text-xs text-muted-foreground">{key}</td>
              <td className="break-all px-3 py-1.5 font-mono text-xs">{entries[key]}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default function EnvVarsPage() {
  const { t } = useTranslation()
  const [backendEnv, setBackendEnv] = useState<Record<string, string> | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchBackendEnvironment()
      .then(setBackendEnv)
      .catch(e => setError(e instanceof Error ? e.message : t('common.requestFailed')))
  }, [])

  return (
    <div className="flex flex-col gap-6">
      <section className="flex flex-col gap-3">
        <h2 className="text-sm font-medium">{t('envVars.backend')}</h2>
        {error && <p className="text-sm text-destructive">{error}</p>}
        {!error && !backendEnv && <p className="text-sm text-muted-foreground">{t('common.loading')}</p>}
        {backendEnv && <EnvTable entries={backendEnv} />}
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="text-sm font-medium">{t('envVars.frontend')}</h2>
        <EnvTable entries={frontendEnv} />
      </section>
    </div>
  )
}
