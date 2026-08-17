import { useTranslation } from 'react-i18next'

const APP_URL = import.meta.env.VITE_APP_URL ?? 'http://localhost:5173'

export default function Footer() {
  const { t } = useTranslation()
  const year = new Date().getFullYear()

  return (
    <footer style={{ borderTop: '1px solid #2a2c38' }} className="py-12 bg-[#13141a]">
      <div className="max-w-6xl mx-auto px-6">
        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-8">
          <div className="flex flex-col gap-2">
            <a
              href="#"
              style={{ fontFamily: "'Geist Mono', monospace" }}
              className="text-[15px] font-semibold tracking-tight text-[#e6e3dc]"
            >
              moneylytics
            </a>
            <p className="text-sm text-[#7a7a8a] max-w-xs">
              {t('footer.tagline')}
            </p>
          </div>

          <nav className="flex flex-wrap gap-x-8 gap-y-3">
            <a href="#features" className="text-sm text-[#7a7a8a] hover:text-[#e6e3dc] transition-colors">
              {t('nav.features')}
            </a>
            <a href="#pricing" className="text-sm text-[#7a7a8a] hover:text-[#e6e3dc] transition-colors">
              {t('nav.pricing')}
            </a>
            <a href="#faq" className="text-sm text-[#7a7a8a] hover:text-[#e6e3dc] transition-colors">
              {t('nav.faq')}
            </a>
            <a href={`${APP_URL}/login`} className="text-sm text-[#7a7a8a] hover:text-[#e6e3dc] transition-colors">
              {t('nav.login')}
            </a>
            <a href="/datenschutz" className="text-sm text-[#7a7a8a] hover:text-[#e6e3dc] transition-colors">
              {t('footer.privacy')}
            </a>
            <a href="/impressum" className="text-sm text-[#7a7a8a] hover:text-[#e6e3dc] transition-colors">
              {t('footer.legal')}
            </a>
          </nav>
        </div>

        <div style={{ borderTop: '1px solid #2a2c38' }} className="mt-8 pt-8">
          <p style={{ fontFamily: "'Geist Mono', monospace" }} className="text-xs text-[#7a7a8a]">
            © {year} Moneylytics. {t('footer.copyright')}
          </p>
        </div>
      </div>
    </footer>
  )
}
