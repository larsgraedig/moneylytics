import { useState } from 'react'
import { Check } from 'lucide-react'
import { useTranslation } from 'react-i18next'

const APP_URL = import.meta.env.VITE_APP_URL ?? 'http://localhost:5173'

interface TierDef {
  key: string
  monthlyPrice: number | null
  yearlyPrice: number | null
  highlighted: boolean
}

const tierDefs: TierDef[] = [
  { key: 'free',    monthlyPrice: 0,     yearlyPrice: 0,     highlighted: false },
  { key: 'starter', monthlyPrice: 3.99,  yearlyPrice: 3.29,  highlighted: true  },
  { key: 'plus',    monthlyPrice: 7.99,  yearlyPrice: 6.59,  highlighted: false },
  { key: 'pro',     monthlyPrice: 14.99, yearlyPrice: 12.39, highlighted: false },
]

export default function Pricing() {
  const [yearly, setYearly] = useState(false)
  const { t } = useTranslation()

  return (
    <section id="pricing" className="py-28">
      <div className="max-w-6xl mx-auto px-6">
        <div className="text-center mb-12 flex flex-col gap-3">
          <span
            style={{ fontFamily: "'Geist Mono', monospace" }}
            className="text-xs text-[#4ade80] tracking-widest uppercase"
          >
            {t('pricing.badge')}
          </span>
          <h2
            style={{ fontFamily: "'Montserrat Variable', sans-serif" }}
            className="text-3xl lg:text-4xl font-bold text-[#e6e3dc]"
          >
            {t('pricing.headline')}
          </h2>
          <p className="text-[#7a7a8a]">{t('pricing.subline')}</p>
        </div>

        <div className="flex justify-center mb-10">
          <div style={{ background: '#1a1c24', border: '1px solid #2a2c38' }} className="flex rounded-lg p-1 gap-1">
            <button
              onClick={() => setYearly(false)}
              className={`px-4 py-2 text-sm rounded-md transition-colors duration-150 ${
                !yearly ? 'bg-[#4ade80] text-[#0d1117] font-semibold' : 'text-[#7a7a8a] hover:text-[#e6e3dc]'
              }`}
            >
              {t('pricing.monthly')}
            </button>
            <button
              onClick={() => setYearly(true)}
              className={`px-4 py-2 text-sm rounded-md transition-colors duration-150 flex items-center gap-2 ${
                yearly ? 'bg-[#4ade80] text-[#0d1117] font-semibold' : 'text-[#7a7a8a] hover:text-[#e6e3dc]'
              }`}
            >
              {t('pricing.yearly')}
              <span style={{ background: 'rgba(74,222,128,0.15)', color: '#4ade80' }} className="text-[10px] px-1.5 py-0.5 rounded font-medium">
                −17%
              </span>
            </button>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-5">
          {tierDefs.map(({ key, monthlyPrice, yearlyPrice, highlighted }) => {
            const price = yearly ? yearlyPrice : monthlyPrice
            const features = t(`pricing.tiers.${key}.features`, { returnObjects: true }) as string[]

            return (
              <div
                key={key}
                style={{
                  background: highlighted ? '#1f2b22' : '#1a1c24',
                  border: `1px solid ${highlighted ? 'rgba(74,222,128,0.4)' : '#2a2c38'}`,
                  boxShadow: highlighted ? '0 0 40px rgba(74,222,128,0.08)' : undefined,
                }}
                className="rounded-xl p-6 flex flex-col gap-6 relative"
              >
                {highlighted && (
                  <div className="absolute -top-3 left-1/2 -translate-x-1/2">
                    <span style={{ background: '#4ade80', color: '#0d1117' }} className="text-[11px] font-bold px-3 py-1 rounded-full whitespace-nowrap">
                      {t('pricing.popular')}
                    </span>
                  </div>
                )}

                <div className="flex flex-col gap-2">
                  <span style={{ fontFamily: "'Geist Mono', monospace" }} className="text-xs text-[#7a7a8a] tracking-wider uppercase">
                    {key.charAt(0).toUpperCase() + key.slice(1)}
                  </span>
                  <div className="flex items-end gap-1">
                    {price === 0 ? (
                      <span style={{ fontFamily: "'Montserrat Variable', sans-serif" }} className="text-3xl font-bold text-[#e6e3dc]">
                        {t('pricing.free')}
                      </span>
                    ) : (
                      <>
                        <span style={{ fontFamily: "'Montserrat Variable', sans-serif" }} className="text-3xl font-bold text-[#e6e3dc]">
                          €{price?.toFixed(2).replace('.', ',')}
                        </span>
                        <span className="text-sm text-[#7a7a8a] pb-1">{t('pricing.perMonth')}</span>
                      </>
                    )}
                  </div>
                  {yearly && price !== null && price > 0 && (
                    <span style={{ fontFamily: "'Geist Mono', monospace" }} className="text-xs text-[#4ade80]">
                      €{(price * 12).toFixed(2).replace('.', ',')} {t('pricing.perYear')}
                    </span>
                  )}
                  <p className="text-sm text-[#7a7a8a]">{t(`pricing.tiers.${key}.desc`)}</p>
                </div>

                <a
                  href={`${APP_URL}/register`}
                  style={
                    highlighted
                      ? { background: '#4ade80', color: '#0d1117' }
                      : { border: '1px solid #333645', color: '#e6e3dc' }
                  }
                  className="w-full text-center py-2.5 px-4 rounded-md text-sm font-semibold hover:opacity-90 transition-opacity duration-150"
                >
                  {t(`pricing.tiers.${key}.cta`)}
                </a>

                <ul className="flex flex-col gap-3">
                  {features.map((feature) => (
                    <li key={feature} className="flex items-start gap-2.5">
                      <Check size={14} color="#4ade80" className="mt-0.5 shrink-0" />
                      <span className="text-sm text-[#7a7a8a]">{feature}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )
          })}
        </div>
      </div>
    </section>
  )
}
