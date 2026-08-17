import { useState } from 'react'
import { Menu, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import i18n from '@/i18n'

const APP_URL = import.meta.env.VITE_APP_URL ?? 'http://localhost:5173'

function LangToggle({ mobile }: { mobile?: boolean }) {
  const { i18n: i18next } = useTranslation()
  const current = i18next.language

  const toggle = (lang: string) => {
    i18n.changeLanguage(lang)
    localStorage.setItem('marketing-lang', lang)
  }

  return (
    <div
      style={{ border: '1px solid #333645' }}
      className={`flex rounded-md overflow-hidden ${mobile ? 'self-start' : ''}`}
    >
      {(['de', 'en'] as const).map((lang) => (
        <button
          key={lang}
          onClick={() => toggle(lang)}
          style={{
            fontFamily: "'Geist Mono', monospace",
            background: current === lang ? '#333645' : 'transparent',
            color: current === lang ? '#e6e3dc' : '#7a7a8a',
          }}
          className="px-2.5 py-1 text-[11px] uppercase tracking-wider transition-colors hover:text-[#e6e3dc]"
        >
          {lang}
        </button>
      ))}
    </div>
  )
}

export default function Navbar() {
  const [open, setOpen] = useState(false)
  const { t } = useTranslation()

  const navLinks = [
    { label: t('nav.features'), href: '#features' },
    { label: t('nav.howItWorks'), href: '#how-it-works' },
    { label: t('nav.pricing'), href: '#pricing' },
    { label: t('nav.faq'), href: '#faq' },
  ]

  return (
    <header
      style={{ borderBottom: '1px solid #2a2c38' }}
      className="fixed top-0 left-0 right-0 z-50 bg-[#13141a]/90 backdrop-blur-md"
    >
      <div className="max-w-6xl mx-auto px-6 h-16 flex items-center justify-between">
        <a
          href="#"
          className="font-mono text-[15px] font-semibold tracking-tight text-[#e6e3dc]"
          style={{ fontFamily: "'Geist Mono', monospace" }}
        >
          moneylytics
        </a>

        <nav className="hidden md:flex items-center gap-8">
          {navLinks.map(({ label, href }) => (
            <a
              key={href}
              href={href}
              className="text-sm text-[#7a7a8a] hover:text-[#e6e3dc] transition-colors duration-150"
            >
              {label}
            </a>
          ))}
        </nav>

        <div className="hidden md:flex items-center gap-3">
          <LangToggle />
          <a
            href={`${APP_URL}/login`}
            className="text-sm text-[#7a7a8a] hover:text-[#e6e3dc] transition-colors duration-150 px-3 py-1.5"
          >
            {t('nav.login')}
          </a>
          <a
            href={`${APP_URL}/register`}
            className="text-sm font-medium px-4 py-2 rounded-md bg-[#4ade80] text-[#0d1117] hover:bg-[#22c55e] transition-colors duration-150"
          >
            {t('nav.cta')}
          </a>
        </div>

        <button
          className="md:hidden text-[#7a7a8a] hover:text-[#e6e3dc] transition-colors"
          onClick={() => setOpen(!open)}
          aria-label="Menu"
        >
          {open ? <X size={20} /> : <Menu size={20} />}
        </button>
      </div>

      {open && (
        <div
          style={{ borderTop: '1px solid #2a2c38' }}
          className="md:hidden bg-[#13141a] px-6 py-4 flex flex-col gap-4"
        >
          {navLinks.map(({ label, href }) => (
            <a
              key={href}
              href={href}
              className="text-sm text-[#7a7a8a] hover:text-[#e6e3dc] transition-colors"
              onClick={() => setOpen(false)}
            >
              {label}
            </a>
          ))}
          <div
            style={{ borderTop: '1px solid #2a2c38' }}
            className="pt-4 flex flex-col gap-3"
          >
            <LangToggle mobile />
            <a
              href={`${APP_URL}/login`}
              className="text-sm text-center text-[#7a7a8a] hover:text-[#e6e3dc] transition-colors py-2"
            >
              {t('nav.login')}
            </a>
            <a
              href={`${APP_URL}/register`}
              className="text-sm font-medium text-center px-4 py-2.5 rounded-md bg-[#4ade80] text-[#0d1117] hover:bg-[#22c55e] transition-colors"
            >
              {t('nav.cta')}
            </a>
          </div>
        </div>
      )}
    </header>
  )
}
