import { GitFork, Upload, Sparkles, Target, RefreshCw, FolderTree } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'

interface FeatureDef {
  icon: LucideIcon
  key: string
  accent?: boolean
}

const featureDefs: FeatureDef[] = [
  { icon: GitFork,    key: 'sankey',     accent: true },
  { icon: Upload,     key: 'import' },
  { icon: Sparkles,   key: 'ai' },
  { icon: Target,     key: 'budgets' },
  { icon: RefreshCw,  key: 'recurring' },
  { icon: FolderTree, key: 'categories' },
]

export default function Features() {
  const { t } = useTranslation()

  return (
    <section id="features" className="py-28">
      <div className="max-w-6xl mx-auto px-6">
        <div className="text-center mb-16 flex flex-col gap-3">
          <span
            style={{ fontFamily: "'Geist Mono', monospace" }}
            className="text-xs text-[#4ade80] tracking-widest uppercase"
          >
            {t('features.badge')}
          </span>
          <h2
            style={{ fontFamily: "'Montserrat Variable', sans-serif" }}
            className="text-3xl lg:text-4xl font-bold text-[#e6e3dc]"
          >
            {t('features.headline')}
          </h2>
          <p className="text-[#7a7a8a] max-w-xl mx-auto">
            {t('features.subline')}
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
          {featureDefs.map(({ icon: Icon, key, accent }) => (
            <div
              key={key}
              style={{
                background: '#1a1c24',
                border: `1px solid ${accent ? 'rgba(74,222,128,0.3)' : '#2a2c38'}`,
                boxShadow: accent ? '0 0 24px rgba(74,222,128,0.06)' : undefined,
              }}
              className="rounded-xl p-6 flex flex-col gap-4 hover:border-[#333645] transition-colors duration-200"
            >
              <div
                style={{
                  background: accent ? 'rgba(74,222,128,0.1)' : '#13141a',
                  border: `1px solid ${accent ? 'rgba(74,222,128,0.2)' : '#2a2c38'}`,
                }}
                className="w-10 h-10 rounded-lg flex items-center justify-center"
              >
                <Icon size={18} color={accent ? '#4ade80' : '#7a7a8a'} />
              </div>
              <div className="flex flex-col gap-2">
                <h3
                  style={{ fontFamily: "'Montserrat Variable', sans-serif" }}
                  className="text-[15px] font-semibold text-[#e6e3dc]"
                >
                  {t(`features.${key}.title`)}
                </h3>
                <p className="text-sm text-[#7a7a8a] leading-relaxed">
                  {t(`features.${key}.desc`)}
                </p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
