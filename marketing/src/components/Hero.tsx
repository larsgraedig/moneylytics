import { ArrowRight, Play } from 'lucide-react'
import { useTranslation } from 'react-i18next'

const APP_URL = import.meta.env.VITE_APP_URL ?? 'http://localhost:5173'

function SankeyMock() {
  const { t } = useTranslation()
  const midX = 226

  const flows = [
    { key: 'rent',          color: '#ef4444', lY1: 50,  lY2: 116, rY1: 50,  rY2: 116, amount: '1.024' },
    { key: 'groceries',     color: '#f97316', lY1: 116, lY2: 156, rY1: 124, rY2: 164, amount: '640'   },
    { key: 'subscriptions', color: '#a78bfa', lY1: 156, lY2: 187, rY1: 172, rY2: 203, amount: '480'   },
    { key: 'leisure',       color: '#60a5fa', lY1: 187, lY2: 212, rY1: 211, rY2: 236, amount: '360'   },
    { key: 'savings',       color: '#4ade80', lY1: 212, lY2: 270, rY1: 244, rY2: 302, amount: '696'   },
  ]

  return (
    <div
      style={{ background: '#1a1c24', border: '1px solid #333645' }}
      className="rounded-xl overflow-hidden shadow-2xl w-full max-w-[560px]"
    >
      <div
        style={{ borderBottom: '1px solid #2a2c38' }}
        className="flex items-center gap-2 px-4 py-3"
      >
        <div className="flex gap-1.5">
          <div className="w-3 h-3 rounded-full bg-[#ef4444]/60" />
          <div className="w-3 h-3 rounded-full bg-[#f97316]/60" />
          <div className="w-3 h-3 rounded-full bg-[#4ade80]/60" />
        </div>
        <span
          style={{ fontFamily: "'Geist Mono', monospace" }}
          className="text-[11px] text-[#7a7a8a] ml-2 tracking-wide uppercase"
        >
          {t('hero.sankey.title')}
        </span>
      </div>

      <div className="p-4">
        <svg viewBox="0 0 500 320" className="w-full" aria-hidden="true">
          {flows.map((f) => {
            const path = [
              `M 32,${f.lY1}`,
              `C ${midX},${f.lY1} ${midX},${f.rY1} 420,${f.rY1}`,
              `L 420,${f.rY2}`,
              `C ${midX},${f.rY2} ${midX},${f.lY2} 32,${f.lY2}`,
              'Z',
            ].join(' ')
            return <path key={f.key} d={path} fill={f.color} opacity="0.22" />
          })}

          <rect x="20" y="50" width="12" height="220" rx="2" fill="#4ade80" />

          {flows.map((f) => (
            <rect key={f.key} x="420" y={f.rY1} width="12" height={f.rY2 - f.rY1} rx="2" fill={f.color} />
          ))}

          <text x="38" y="155" style={{ fontFamily: "'Geist Mono', monospace" }} fontSize="10" fill="#7a7a8a">
            {t('hero.sankey.income')}
          </text>
          <text x="38" y="170" style={{ fontFamily: "'Geist Mono', monospace" }} fontSize="12" fontWeight="500" fill="#4ade80">
            €3.200
          </text>

          {flows.map((f) => {
            const midY = f.rY1 + (f.rY2 - f.rY1) / 2
            return (
              <g key={f.key}>
                <text x="440" y={midY - 4} style={{ fontFamily: "'Geist Mono', monospace" }} fontSize="9" fill="#7a7a8a">
                  {t(`hero.sankey.${f.key}`)}
                </text>
                <text x="440" y={midY + 8} style={{ fontFamily: "'Geist Mono', monospace" }} fontSize="9" fontWeight="500" fill={f.color}>
                  €{f.amount}
                </text>
              </g>
            )
          })}
        </svg>
      </div>

      <div
        style={{ borderTop: '1px solid #2a2c38' }}
        className="flex items-center justify-between px-4 py-3"
      >
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-1.5">
            <div className="w-2 h-2 rounded-full bg-[#4ade80]" />
            <span style={{ fontFamily: "'Geist Mono', monospace" }} className="text-[10px] text-[#7a7a8a]">
              {t('hero.sankey.legendIncome')}
            </span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="w-2 h-2 rounded-full bg-[#ef4444]" />
            <span style={{ fontFamily: "'Geist Mono', monospace" }} className="text-[10px] text-[#7a7a8a]">
              {t('hero.sankey.legendExpenses')}
            </span>
          </div>
        </div>
        <span style={{ fontFamily: "'Geist Mono', monospace" }} className="text-[10px] text-[#4ade80]">
          {t('hero.sankey.saved')}
        </span>
      </div>
    </div>
  )
}

export default function Hero() {
  const { t } = useTranslation()

  return (
    <section className="min-h-screen flex items-center pt-16">
      <div className="max-w-6xl mx-auto px-6 py-20 w-full">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-16 items-center">
          <div className="flex flex-col gap-8">
            <div className="flex flex-col gap-5">
              <span
                style={{ fontFamily: "'Geist Mono', monospace", border: '1px solid #333645' }}
                className="self-start text-[11px] text-[#4ade80] tracking-widest uppercase px-3 py-1.5 rounded-full"
              >
                {t('hero.badge')}
              </span>

              <h1
                style={{ fontFamily: "'Montserrat Variable', sans-serif", lineHeight: 1.1 }}
                className="text-5xl lg:text-6xl font-bold text-[#e6e3dc]"
              >
                {t('hero.headline1')}{' '}
                <span className="text-[#4ade80]">{t('hero.headline2')}</span>
              </h1>

              <p className="text-lg text-[#7a7a8a] leading-relaxed max-w-md">
                {t('hero.subline')}
              </p>
            </div>

            <div className="flex flex-col sm:flex-row gap-3">
              <a
                href={`${APP_URL}/register`}
                className="inline-flex items-center justify-center gap-2 px-6 py-3 rounded-md bg-[#4ade80] text-[#0d1117] text-sm font-semibold hover:bg-[#22c55e] transition-colors duration-150"
              >
                {t('hero.cta')}
                <ArrowRight size={16} />
              </a>
              <a
                href="#features"
                style={{ border: '1px solid #333645' }}
                className="inline-flex items-center justify-center gap-2 px-6 py-3 rounded-md text-[#7a7a8a] text-sm font-medium hover:text-[#e6e3dc] hover:border-[#7a7a8a] transition-colors duration-150"
              >
                <Play size={14} />
                {t('hero.ctaSecondary')}
              </a>
            </div>

            <div className="flex items-center gap-6 pt-2">
              {[
                { value: t('hero.trust1value'), label: t('hero.trust1label') },
                { value: t('hero.trust2value'), label: t('hero.trust2label') },
                { value: t('hero.trust3value'), label: t('hero.trust3label') },
              ].map(({ value, label }) => (
                <div key={label} className="flex flex-col">
                  <span style={{ fontFamily: "'Geist Mono', monospace" }} className="text-sm font-semibold text-[#4ade80]">
                    {value}
                  </span>
                  <span className="text-xs text-[#7a7a8a]">{label}</span>
                </div>
              ))}
            </div>
          </div>

          <div className="flex justify-center lg:justify-end">
            <SankeyMock />
          </div>
        </div>
      </div>
    </section>
  )
}
