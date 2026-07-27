import { useState, useEffect, useMemo } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import type { LucideIcon } from 'lucide-react'
import {
  Workflow, TrendingUp, TrendingDown, PieChart, BarChart2,
  List, Landmark, Wallet, Gauge,
  FileSpreadsheet, FileCode, Link2, FolderOpen, Repeat, Wrench, Building2,
} from 'lucide-react'
import { getPresetRange, detectPreset, PRESETS, type Preset } from './utils/datePresets'
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
import BurnRatePage from './components/BurnRatePage'
import AccountsPage from './components/AccountsPage'
import LinkedTransactionsPage from './components/LinkedTransactionsPage'
import CollectionsPage from './components/CollectionsPage'
import RecurringPage from './components/RecurringPage'
import AdminPage from './components/AdminPage'
import OrgsPage from './components/OrgsPage'
import LoginPage from './components/LoginPage'
import SettingsPanel from './components/SettingsPanel'
import InvitePage from './components/InvitePage'
import OnboardingModal from './components/OnboardingModal'
import OrgSelectModal from './components/OrgSelectModal'
import { fetchSankeyData, type SankeyResponse } from './api/transactions'
import { fetchAccounts, type Account } from './api/accounts'
import { fetchCategories, type CategoryGroup } from './api/rawImport'
import { fetchUserSettings } from './api/settings'
import { useAuth } from './context/AuthContext'
import { useTranslation, Trans } from 'react-i18next'

function isoDate(d: Date) {
  return d.toISOString().slice(0, 10)
}

const today = isoDate(new Date())
const firstOfYear = isoDate(new Date(new Date().getFullYear(), 0, 1))

type Tab = 'sankey' | 'trends' | 'breakdown' | 'cashflow' | 'burnrate' | 'kontoauszug' | 'verknuepfungen' | 'sammlungen' | 'konten' | 'budgets' | 'limits' | 'csv' | 'camt' | 'wiederkehrer' | 'admin' | 'orgs'

const VALID_TABS = new Set<string>(['sankey', 'trends', 'breakdown', 'cashflow', 'burnrate', 'kontoauszug', 'verknuepfungen', 'sammlungen', 'konten', 'budgets', 'limits', 'csv', 'camt', 'wiederkehrer', 'admin', 'orgs'])

type ViewState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'ready'; data: SankeyResponse }

type NavSection = { sectionKey: string; items: [Tab, string, LucideIcon][] }

const BASE_NAV: NavSection[] = [
  {
    sectionKey: 'analytics',
    items: [
      ['sankey', 'nav.sankey', Workflow],
      ['trends', 'nav.trends', TrendingUp],
      ['breakdown', 'nav.breakdown', PieChart],
      ['cashflow', 'nav.cashflow', BarChart2],
      ['burnrate', 'nav.burnrate', TrendingDown],
    ],
  },
  {
    sectionKey: 'accounts',
    items: [
      ['kontoauszug', 'nav.kontoauszug', List],
      ['verknuepfungen', 'nav.verknuepfungen', Link2],
      ['sammlungen', 'nav.sammlungen', FolderOpen],
      ['konten', 'nav.konten', Landmark],
      ['budgets', 'nav.budgets', Wallet],
      ['limits', 'nav.limits', Gauge],
      ['wiederkehrer', 'nav.wiederkehrer', Repeat],
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
  const { username, isSystemAdmin, impersonating, deimpersonate, isLoading, logout, activeOrganization, organizations, activateOrganization, refreshAuth } = useAuth()
  const isOrgAdminOrOwner = activeOrganization?.role === 'ADMIN' || activeOrganization?.role === 'OWNER'
  const NAV = useMemo((): NavSection[] => {
    const sections = [...BASE_NAV]
    if (isOrgAdminOrOwner) {
      sections.push({ sectionKey: 'admin', items: [['orgs', 'nav.orgs', Building2]] })
    }
    if (isSystemAdmin) {
      const last = sections[sections.length - 1]
      if (last.sectionKey === 'admin') {
        last.items.push(['admin', 'nav.admin', Wrench])
      } else {
        sections.push({ sectionKey: 'admin', items: [['admin', 'nav.admin', Wrench]] })
      }
    }
    return sections
  }, [isSystemAdmin, isOrgAdminOrOwner])
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()

  const searchParams = new URLSearchParams(location.search)
  const rawPath = location.pathname.slice(1)
  const tab: Tab = (VALID_TABS.has(rawPath) ? rawPath : 'sankey') as Tab
  const from = searchParams.get('from') ?? firstOfYear
  const to = searchParams.get('to') ?? today
  const selectedIban = searchParams.get('iban') ?? ''
  const activePreset = detectPreset(from, to)

  const [settingsOpen, setSettingsOpen] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [accounts, setAccounts] = useState<Account[]>([])
  const [categories, setCategories] = useState<CategoryGroup[]>([])
  const [txColumnOrder, setTxColumnOrder] = useState<string[] | null>(null)
  const [view, setView] = useState<ViewState>({ phase: 'idle' })
  const [activeNode, setActiveNode] = useState<string | null>(null)

  function updateSearch(updates: Record<string, string>) {
    const p = new URLSearchParams(location.search)
    Object.entries(updates).forEach(([k, v]) => {
      if (v) p.set(k, v)
      else p.delete(k)
    })
    navigate({ search: p.toString() }, { replace: true })
  }

  function navigateToTab(newTab: Tab) {
    navigate({ pathname: `/${newTab}`, search: location.search })
  }

  useEffect(() => {
    if (!username) return
    setAccounts([])
    setCategories([])
    setTxColumnOrder(null)
    setView({ phase: 'idle' })
    setActiveNode(null)
    Promise.all([fetchAccounts(), fetchUserSettings(), fetchCategories()]).then(([accs, settings, cats]) => {
      setAccounts(accs)
      setCategories(cats.categories)
      if (settings.language) {
        i18n.changeLanguage(settings.language)
        localStorage.setItem('lang', settings.language)
      }
      const defaultIban = settings.defaultAccountIban ?? localStorage.getItem('defaultIban') ?? ''
      if (defaultIban && accs.some(a => a.iban === defaultIban)) {
        const current = new URLSearchParams(window.location.search)
        if (!current.get('iban')) {
          current.set('iban', defaultIban)
          navigate({ search: current.toString() }, { replace: true })
        }
      }
      setTxColumnOrder(settings.transactionsColumnOrder ?? null)
    }).catch(() => {
      Promise.all([fetchAccounts(), fetchCategories()]).then(([accs, cats]) => {
        setAccounts(accs)
        setCategories(cats.categories)
        const defaultIban = localStorage.getItem('defaultIban') ?? ''
        if (defaultIban && accs.some(a => a.iban === defaultIban)) {
          const current = new URLSearchParams(window.location.search)
          if (!current.get('iban')) {
            current.set('iban', defaultIban)
            navigate({ search: current.toString() }, { replace: true })
          }
        }
      }).catch(() => {})
    })
  }, [username])

  if (location.pathname.startsWith('/invite/')) {
    const token = location.pathname.split('/')[2]
    return <InvitePage token={token} />
  }

  if (isLoading) return null
  if (!username) return <LoginPage />
  if (organizations.length === 0) return <OnboardingModal onComplete={refreshAuth} />
  if (organizations.length > 1 && !activeOrganization) return <OrgSelectModal organizations={organizations} onSelect={activateOrganization} />

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
                  onClick={() => navigateToTab(id)}
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
        <header className={`bar${impersonating ? ' bar--impersonating' : ''}`}>
          <span className="wordmark">moneylytics</span>
          <div className="session">
            {impersonating && (
              <>
                <span className="impersonate-badge">
                  {t('admin.impersonation.active', { username: impersonating })}
                </span>
                <button className="deimpersonate-btn" onClick={deimpersonate}>
                  {t('admin.impersonation.stop')}
                </button>
              </>
            )}
            {organizations.length > 1 && (
              <select
                className="org-select"
                value={activeOrganization?.id ?? ''}
                onChange={e => activateOrganization(Number(e.target.value))}
              >
                {organizations.map(org => (
                  <option key={org.id} value={org.id}>{org.name}</option>
                ))}
              </select>
            )}
            {organizations.length === 1 && activeOrganization && (
              <span className="org-name">{activeOrganization.name}</span>
            )}
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
              onChange={e => updateSearch({ iban: e.target.value })}
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
              updateSearch({ from: range.from, to: range.to })
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
              <input type="date" value={from} max={to} onChange={e => updateSearch({ from: e.target.value })} />
            </label>
            <div className="range-sep" />
            <label className="range-field">
              <span className="range-label">{t('common.to')}</span>
              <input type="date" value={to} min={from} max={today} onChange={e => updateSearch({ to: e.target.value })} />
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
          {tab === 'burnrate' && <BurnRatePage key={username} from={from} to={to} iban={iban} />}
          {tab === 'trends' && <TrendsPage key={username} from={from} to={to} iban={iban} categories={categories} />}
          {tab === 'breakdown' && <PiePage key={username} from={from} to={to} iban={iban} />}
          {tab === 'kontoauszug' && <TransactionsPage key={username} from={from} to={to} iban={iban} accounts={accounts} categories={categories} columnOrder={txColumnOrder ?? undefined} onColumnOrderChange={order => setTxColumnOrder(order)} />}
          {tab === 'verknuepfungen' && <LinkedTransactionsPage key={username} />}
          {tab === 'sammlungen' && <CollectionsPage key={username} accounts={accounts} categories={categories} />}
          {tab === 'budgets' && <BudgetsPage key={username} from={from} to={to} iban={iban} accounts={accounts} categories={categories} />}
          {tab === 'limits' && <ThresholdsPage key={username} from={from} to={to} iban={iban} categories={categories} />}
          {tab === 'konten' && <AccountsPage key={username} />}
          {tab === 'csv' && <CsvImportPage key={username} categories={categories} />}
          {tab === 'camt' && <CamtImportPage key={username} categories={categories} />}
          {tab === 'wiederkehrer' && <RecurringPage key={username} />}
          {tab === 'orgs' && isOrgAdminOrOwner && <OrgsPage key={activeOrganization?.id} />}
          {tab === 'admin' && isSystemAdmin && <AdminPage key={username} />}
        </main>
      </div>
      {settingsOpen && <SettingsPanel accounts={accounts} defaultAccountIban={selectedIban} onClose={() => setSettingsOpen(false)} />}
    </div>
  )
}
