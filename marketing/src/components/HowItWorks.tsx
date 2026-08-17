import { UserPlus, Upload, BarChart2 } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'

const stepDefs: { key: string; icon: LucideIcon; number: string }[] = [
  { key: 'step1', icon: UserPlus,  number: '1' },
  { key: 'step2', icon: Upload,    number: '2' },
  { key: 'step3', icon: BarChart2, number: '3' },
]

export default function HowItWorks() {
  const { t } = useTranslation()

  return (
    <section
      id="how-it-works"
      style={{ background: '#1a1c24', borderTop: '1px solid #2a2c38', borderBottom: '1px solid #2a2c38' }}
      className="py-28"
    >
      <div className="max-w-6xl mx-auto px-6">
        <div className="text-center mb-16 flex flex-col gap-3">
          <span
            style={{ fontFamily: "'Geist Mono', monospace" }}
            className="text-xs text-[#4ade80] tracking-widest uppercase"
          >
            {t('howItWorks.badge')}
          </span>
          <h2
            style={{ fontFamily: "'Montserrat Variable', sans-serif" }}
            className="text-3xl lg:text-4xl font-bold text-[#e6e3dc]"
          >
            {t('howItWorks.headline')}
          </h2>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-10 relative">
          <div
            className="hidden md:block absolute top-10 left-[calc(16.667%+2rem)] right-[calc(16.667%+2rem)]"
            style={{ borderTop: '1px dashed #333645' }}
            aria-hidden="true"
          />

          {stepDefs.map(({ key, icon: Icon, number }) => (
            <div key={key} className="flex flex-col items-center text-center gap-5">
              <div className="relative">
                <div
                  style={{ background: '#13141a', border: '1px solid #333645' }}
                  className="w-20 h-20 rounded-full flex items-center justify-center"
                >
                  <Icon size={28} color="#4ade80" />
                </div>
                <span
                  style={{ fontFamily: "'Geist Mono', monospace", background: '#4ade80', color: '#0d1117' }}
                  className="absolute -top-1 -right-1 w-6 h-6 rounded-full text-[10px] font-bold flex items-center justify-center"
                >
                  {number}
                </span>
              </div>

              <div className="flex flex-col gap-2">
                <h3
                  style={{ fontFamily: "'Montserrat Variable', sans-serif" }}
                  className="text-[16px] font-semibold text-[#e6e3dc]"
                >
                  {t(`howItWorks.${key}.title`)}
                </h3>
                <p className="text-sm text-[#7a7a8a] leading-relaxed">
                  {t(`howItWorks.${key}.desc`)}
                </p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
