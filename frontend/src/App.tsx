import { useState, useEffect } from 'react'
import type { LucideIcon } from 'lucide-react'
import {
  Workflow, TrendingUp, PieChart, BarChart2,
  List, Landmark, Wallet, Gauge,
  FileSpreadsheet, FileCode,
} from 'lucide-react'
import { getPresetRange, PRESETS, type Preset } from './utils/datePresets'
import SankeyChart from './components/SankeyChart'
import CamtImportPage from './components/CamtImportPage'
import CsvImportPage from './components/CsvImportPage'
import TrendsPage from './components/TrendsPage'
import ThresholdsPage from './components/ThresholdsPage'
import BudgetsPage from './components/BudgetsPage'
import PiePage from './components/PiePage'
import TransactionsPage from './components/TransactionsPage'
import TransactionListPanel from './components/TransactionListPanel'
import CashflowPage from './components/CashflowPage'
import AccountsPage from './components/AccountsPage'
import LoginPage from './components/LoginPage'
import SettingsPanel from './components/SettingsPanel'
import { fetchSankeyData, fetchAccounts, type SankeyResponse, type Account } from './api/transactions'
import { fetchUserSettings } from './api/settings'
import { useAuth } from './context/AuthContext'
import { useTranslation, Trans } from 'react-i18next'

function isoDate(d: Date) {
  return d.toISOString().slice(0, 10)
}

const today = isoDate(new Date())
const firstOfYear = isoDate(new Date(new Date().getFullYear(), 0, 1))

type Tab = 'sankey' | 'trends' | 'breakdown' | 'cashflow' | 'kontoauszug' | 'konten' | 'budgets' | 'limits' | 'csv' | 'camt'

type ViewState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'ready'; data: SankeyResponse }

type NavSection = { sectionKey: string; items: [Tab, string, LucideIcon][] }

const NAV: NavSection[] = [
  {
    sectionKey: 'analytics',
    items: [
      ['sankey', 'nav.sankey', Workflow],
      ['trends', 'nav.trends', TrendingUp],
      ['breakdown', 'nav.breakdown', PieChart],
      ['cashflow', 'nav.cashflow', BarChart2],
    ],
  },
  {
    sectionKey: 'accounts',
    items: [
      ['kontoauszug', 'nav.kontoauszug', List],
      ['konten', 'nav.konten', Landmark],
      ['budgets', 'nav.budgets', Wallet],
      ['limits', 'nav.limits', Gauge],
    ],
  },
  {
    sectionKey: 'imports',
    items: [
      ['csv', 'nav.csv', FileSpreadsheet],
      ['camt', 'nav.camt', FileCode],
    ],
  },
]

export default function App() {
  const { username, isLoading, logout } = useAuth()
  const { t, i18n } = useTranslation()
  const [tab, setTab] = useState<Tab>('sankey')
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [from, setFrom] = useState(firstOfYear)
  const [to, setTo] = useState(today)
  const [activePreset, setActivePreset] = useState<Preset | ''>('')
  const [accounts, setAccounts] = useState<Account[]>([])
  const [selectedIban, setSelectedIban] = useState<string>('')
  const [view, setView] = useState<ViewState>({ phase: 'idle' })
  const [activeNode, setActiveNode] = useState<string | null>(null)

  useEffect(() => {
    if (!username) return
    setAccounts([])
    setSelectedIban('')
    setView({ phase: 'idle' })
    setActiveNode(null)
    Promise.all([fetchAccounts(), fetchUserSettings()]).then(([accs, settings]) => {
      setAccounts(accs)
      if (settings.language) {
        i18n.changeLanguage(settings.language)
        localStorage.setItem('lang', settings.language)
      }
      const defaultIban = settings.defaultAccountIban ?? localStorage.getItem('defaultIban') ?? ''
      if (defaultIban && accs.some(a => a.iban === defaultIban)) {
        setSelectedIban(defaultIban)
      }
    }).catch(() => {
      fetchAccounts().then(accs => {
        setAccounts(accs)
        const defaultIban = localStorage.getItem('defaultIban') ?? ''
        if (defaultIban && accs.some(a => a.iban === defaultIban)) {
          setSelectedIban(defaultIban)
        }
      }).catch(() => {})
    })
  }, [username])

  if (isLoading) return null
  if (!username) return <LoginPage />

  const iban = selectedIban || undefined

  async function load() {
    setView({ phase: 'loading' })
    try {
      const data = await fetchSankeyData(from, to, iban)
      setView(data.nodes.length === 0 ? { phase: 'idle' } : { phase: 'ready', data })
    } catch (e) {
      setView({ phase: 'error', message: e instanceof Error ? e.message : 'request failed' })
    }
  }

  return (
    <div className="shell">
      {/* ── sidebar ── */}
      <aside className={`sidebar${sidebarOpen ? '' : ' sidebar--collapsed'}`}>
        <button
          className="sidebar-toggle"
          onClick={() => setSidebarOpen(o => !o)}
          title={sidebarOpen ? t('nav.collapse') : t('nav.expand')}
        >
          <span className="sidebar-toggle-icon">{sidebarOpen ? '‹' : '›'}</span>
        </button>

        <nav className="sidebar-nav">
          {NAV.map(({ sectionKey, items }) => (
            <div key={sectionKey} className="nav-section">
              <span className="nav-section-title">{t(`nav.sections.${sectionKey}`)}</span>
              {items.map(([id, labelKey, Icon]) => (
                <button
                  key={id}
                  className={`nav-item${tab === id ? ' active' : ''}`}
                  onClick={() => setTab(id)}
                  title={t(labelKey)}
                >
                  <span className="nav-item-icon"><Icon size={15} strokeWidth={1.6} /></span>
                  <span className="nav-item-label">{t(labelKey)}</span>
                </button>
              ))}
            </div>
          ))}
        </nav>
      </aside>

      {/* ── main column ── */}
      <div className="main-col">
        <header className="bar">
          <span className="wordmark">moneylytics</span>
          <div className="session">
            <button className="session-user-btn" onClick={() => setSettingsOpen(true)}>
              {username}
            </button>
            <button className="logout-btn" onClick={logout}>{t('common.signOut')}</button>
          </div>
        </header>

        <div className="subbar">
          {accounts.length > 0 && (
            <select
              className="account-select"
              value={selectedIban}
              onChange={e => setSelectedIban(e.target.value)}
            >
              <option value="">{t('common.allAccounts')}</option>
              {accounts.map(a => (
                <option key={a.iban} value={a.iban}>{a.name}</option>
              ))}
            </select>
          )}

          <select
            className="account-select"
            value={activePreset}
            onChange={e => {
              const p = e.target.value as Preset
              if (!p) return
              const range = getPresetRange(p)
              setActivePreset(p)
              setFrom(range.from)
              setTo(range.to)
            }}
          >
            <option value="">{t('budgets.presets.placeholder')}</option>
            {PRESETS.map(p => (
              <option key={p} value={p}>{t(`budgets.presets.${p}`)}</option>
            ))}
          </select>

          <fieldset className="range-group">
            <label className="range-field">
              <span className="range-label">{t('common.from')}</span>
              <input type="date" value={from} max={to} onChange={e => { setFrom(e.target.value); setActivePreset('') }} />
            </label>
            <div className="range-sep" />
            <label className="range-field">
              <span className="range-label">{t('common.to')}</span>
              <input type="date" value={to} min={from} max={today} onChange={e => { setTo(e.target.value); setActivePreset('') }} />
            </label>
          </fieldset>

          {tab === 'sankey' && (
            <button className="load-btn" onClick={load} disabled={view.phase === 'loading'}>
              {view.phase === 'loading' ? '…' : t('common.load')}
            </button>
          )}
        </div>

        <main className="stage">
          {tab === 'sankey' && (
            <>
              {view.phase === 'idle' && (
                <p className="hint">
                  <Trans i18nKey="sankey.hint"><span /><kbd /></Trans>
                </p>
              )}
              {view.phase === 'loading' && (
                <p className="hint loading">{t('common.fetching')}</p>
              )}
              {view.phase === 'error' && (
                <p className="hint error">{view.message}</p>
              )}
              {view.phase === 'ready' && (
                <div className="chart" key={`${iban}/${from}/${to}`}>
                  <SankeyChart
                    data={view.data}
                    onNodeClick={nodeKey => setActiveNode(nodeKey)}
                  />
                  {activeNode && (
                    <TransactionListPanel
                      nodeKey={activeNode}
                      from={from}
                      to={to}
                      iban={iban}
                      onClose={() => setActiveNode(null)}
                    />
                  )}
                </div>
              )}
            </>
          )}

          {tab === 'cashflow' && <CashflowPage key={username} from={from} to={to} iban={iban} />}
          {tab === 'trends' && <TrendsPage key={username} from={from} to={to} iban={iban} />}
          {tab === 'breakdown' && <PiePage key={username} from={from} to={to} iban={iban} />}
          {tab === 'kontoauszug' && <TransactionsPage key={username} from={from} to={to} iban={iban} accounts={accounts} />}
          {tab === 'budgets' && <BudgetsPage key={username} from={from} to={to} iban={iban} />}
          {tab === 'limits' && <ThresholdsPage key={username} from={from} to={to} iban={iban} />}
          {tab === 'konten' && <AccountsPage key={username} />}
          {tab === 'csv' && <CsvImportPage key={username} />}
          {tab === 'camt' && <CamtImportPage key={username} />}
        </main>
      </div>
      {settingsOpen && <SettingsPanel accounts={accounts} defaultAccountIban={selectedIban} onClose={() => setSettingsOpen(false)} />}
    </div>
  )
}
