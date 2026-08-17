import { useState } from 'react'
import { ChevronDown } from 'lucide-react'
import { useTranslation } from 'react-i18next'

export default function FAQ() {
  const [openIndex, setOpenIndex] = useState<number | null>(null)
  const { t } = useTranslation()

  const items = t('faq.items', { returnObjects: true }) as { q: string; a: string }[]

  return (
    <section
      id="faq"
      style={{ background: '#1a1c24', borderTop: '1px solid #2a2c38' }}
      className="py-28"
    >
      <div className="max-w-3xl mx-auto px-6">
        <div className="text-center mb-14 flex flex-col gap-3">
          <span
            style={{ fontFamily: "'Geist Mono', monospace" }}
            className="text-xs text-[#4ade80] tracking-widest uppercase"
          >
            {t('faq.badge')}
          </span>
          <h2
            style={{ fontFamily: "'Montserrat Variable', sans-serif" }}
            className="text-3xl lg:text-4xl font-bold text-[#e6e3dc]"
          >
            {t('faq.headline')}
          </h2>
        </div>

        <div className="flex flex-col">
          {items.map(({ q, a }, i) => {
            const isOpen = openIndex === i

            return (
              <div
                key={i}
                style={{ borderTop: '1px solid #2a2c38' }}
                className={i === items.length - 1 ? 'border-b border-[#2a2c38]' : ''}
              >
                <button
                  className="w-full flex items-center justify-between gap-4 py-5 text-left"
                  onClick={() => setOpenIndex(isOpen ? null : i)}
                  aria-expanded={isOpen}
                >
                  <span className="text-[15px] font-medium text-[#e6e3dc]">{q}</span>
                  <ChevronDown
                    size={18}
                    color="#7a7a8a"
                    className="shrink-0 transition-transform duration-200"
                    style={{ transform: isOpen ? 'rotate(180deg)' : 'rotate(0deg)' }}
                  />
                </button>

                {isOpen && (
                  <div className="pb-5">
                    <p className="text-sm text-[#7a7a8a] leading-relaxed">{a}</p>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      </div>
    </section>
  )
}
