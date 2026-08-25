import { useState, useEffect, useMemo } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import type { LucideIcon } from 'lucide-react'
import {
  Workflow, TrendingUp, TrendingDown, PieChart, BarChart2,
  List, Landmark, Wallet, Gauge,
  Upload, Link2, FolderOpen, Repeat, Wrench, Building2, Tags, History,
  LogOut, UserCog,
} from 'lucide-react'
import { getPresetRange, detectPreset, PRESETS, type Preset } from './utils/datePresets'
import { DatePicker } from '@/components/ui/date-picker'
import SankeyChart from './components/SankeyChart'
import ImportPage from './components/ImportPage'
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
import CategoriesPage from './components/CategoriesPage'
import CollectionsPage from './components/CollectionsPage'
import RecurringPage from './components/RecurringPage'
import ImportsPage from './components/ImportsPage'
import AdminPage from './components/AdminPage'
import OrgsPage from './components/OrgsPage'
import LoginPage from './components/LoginPage'
import SettingsPage from './components/SettingsPage'
import InvitePage from './components/InvitePage'
import OnboardingModal from './components/OnboardingModal'
import OrgSelectModal from './components/OrgSelectModal'
import OrgAvatar from './components/OrgAvatar'
import { fetchSankeyData, type SankeyNode, type SankeyResponse } from './api/transactions'
import { fetchAccounts, type Account } from './api/accounts'
import { fetchCategories, type CategoryNode } from './api/rawImport'
import { fetchUserSettings } from './api/settings'
import { useAuth } from './context/AuthContext'
import { useTranslation, Trans } from 'react-i18next'
import {
  SidebarProvider,
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuButton,
  SidebarTrigger,
  SidebarInset,
  SidebarSeparator,
  useSidebar,
} from '@/components/ui/sidebar'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

function isoDate(d: Date) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function parseIso(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}

const today = isoDate(new Date())
const firstOfYear = isoDate(new Date(new Date().getFullYear(), 0, 1))

type Tab = 'sankey' | 'trends' | 'breakdown' | 'cashflow' | 'burnrate' | 'kontoauszug' | 'verknuepfungen' | 'sammlungen' | 'konten' | 'budgets' | 'limits' | 'import' | 'importe' | 'wiederkehrer' | 'kategorien' | 'admin' | 'orgs' | 'einstellungen'

const VALID_TABS = new Set<string>(['sankey', 'trends', 'breakdown', 'cashflow', 'burnrate', 'kontoauszug', 'verknuepfungen', 'sammlungen', 'konten', 'budgets', 'limits', 'import', 'importe', 'wiederkehrer', 'kategorien', 'admin', 'orgs', 'einstellungen'])

type ViewState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'empty' }
  | { phase: 'error'; message: string }
  | { phase: 'ready'; data: SankeyResponse }

type NavSection = { sectionKey: string; items: [Tab, string, LucideIcon][] }

const BASE_NAV: NavSection[] = [
  {
    sectionKey: 'analytics',
    items: [
      ['kontoauszug', 'nav.kontoauszug', List],
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
      ['verknuepfungen', 'nav.verknuepfungen', Link2],
      ['sammlungen', 'nav.sammlungen', FolderOpen],
      ['konten', 'nav.konten', Landmark],
      ['budgets', 'nav.budgets', Wallet],
      ['limits', 'nav.limits', Gauge],
      ['wiederkehrer', 'nav.wiederkehrer', Repeat],
      ['kategorien', 'nav.kategorien', Tags],
    ],
  },
  {
    sectionKey: 'imports',
    items: [
      ['import', 'nav.import', Upload],
      ['importe', 'nav.importe', History],
    ],
  },
]

const ANALYTICS_TABS = new Set(
  BASE_NAV.find(s => s.sectionKey === 'analytics')?.items.map(([id]) => id) ?? [],
)

function SidebarNavLink(props: React.ComponentProps<typeof Link>) {
  const { setOpenMobile } = useSidebar()
  return <Link {...props} onClick={(e) => { setOpenMobile(false); props.onClick?.(e) }} />
}

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
  const selectedAccountId = searchParams.get('accountId') ?? ''
  const activePreset = detectPreset(from, to)

  const [accounts, setAccounts] = useState<Account[]>([])
  const [categories, setCategories] = useState<CategoryNode[]>([])
  const [txColumnOrder, setTxColumnOrder] = useState<string[] | null>(null)
  const [view, setView] = useState<ViewState>({ phase: 'idle' })
  const [activeNode, setActiveNode] = useState<SankeyNode | null>(null)

  function updateSearch(updates: Record<string, string>) {
    const p = new URLSearchParams(location.search)
    Object.entries(updates).forEach(([k, v]) => {
      if (v) p.set(k, v)
      else p.delete(k)
    })
    navigate({ search: p.toString() }, { replace: true })
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
      const defaultAccount = accs.find(a => a.iban === defaultIban)
      if (defaultAccount) {
        const current = new URLSearchParams(window.location.search)
        if (!current.get('accountId')) {
          current.set('accountId', String(defaultAccount.id))
          navigate({ search: current.toString() }, { replace: true })
        }
      }
      setTxColumnOrder(settings.transactionsColumnOrder ?? null)
    }).catch(() => {
      Promise.all([fetchAccounts(), fetchCategories()]).then(([accs, cats]) => {
        setAccounts(accs)
        setCategories(cats.categories)
        const defaultIban = localStorage.getItem('defaultIban') ?? ''
        const defaultAccount = accs.find(a => a.iban === defaultIban)
        if (defaultAccount) {
          const current = new URLSearchParams(window.location.search)
          if (!current.get('accountId')) {
            current.set('accountId', String(defaultAccount.id))
            navigate({ search: current.toString() }, { replace: true })
          }
        }
      }).catch(() => {})
    })
  }, [username, activeOrganization?.id])

  const accountId = selectedAccountId ? Number(selectedAccountId) : undefined

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { if (tab === 'sankey') void load() }, [tab, from, to, accountId])

  if (location.pathname.startsWith('/invite/')) {
    const token = location.pathname.split('/')[2]
    return <InvitePage token={token} />
  }

  if (isLoading) return null
  if (!username) return <LoginPage />
  if (organizations.length === 0) return <OnboardingModal onComplete={refreshAuth} />
  if (organizations.length > 1 && !activeOrganization) return <OrgSelectModal organizations={organizations} onSelect={activateOrganization} />

  async function load() {
    setView({ phase: 'loading' })
    try {
      const data = await fetchSankeyData(from, to, accountId)
      setView(data.nodes.length === 0 ? { phase: 'empty' } : { phase: 'ready', data })
    } catch (e) {
      setView({ phase: 'error', message: e instanceof Error ? e.message : 'request failed' })
    }
  }

  return (
    <SidebarProvider className="h-svh overflow-hidden">
      <Sidebar collapsible="icon">
        <SidebarContent className="px-3 group-data-[collapsible=icon]:px-0">
          {NAV.map(({ sectionKey, items }, idx) => (
            <SidebarGroup key={sectionKey}>
              {idx > 0 && <SidebarSeparator />}
              <SidebarGroupLabel>{t(`nav.sections.${sectionKey}`)}</SidebarGroupLabel>
              <SidebarMenu>
                {items.map(([id, labelKey, Icon]) => (
                  <SidebarMenuItem key={id}>
                    <SidebarMenuButton
                      isActive={tab === id}
                      tooltip={t(labelKey)}
                      render={
                        <SidebarNavLink to={{ pathname: `/${id}`, search: location.search }} />
                      }
                    >
                      <Icon />
                      <span>{t(labelKey)}</span>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                ))}
              </SidebarMenu>
            </SidebarGroup>
          ))}
        </SidebarContent>
        <SidebarFooter>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton
                isActive={tab === 'einstellungen'}
                tooltip={t('nav.einstellungen')}
                render={<SidebarNavLink to="/einstellungen" />}
              >
                <UserCog />
                <span>{t('nav.einstellungen')}</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarFooter>
      </Sidebar>

      <SidebarInset className="flex flex-col overflow-hidden">
        {/* Topbar */}
        <header className={cn(
          'flex h-12 shrink-0 items-center gap-2 border-b px-3',
          impersonating && 'bg-orange-500/10 border-orange-500/30',
        )}>
          <SidebarTrigger />
          <span className="font-semibold text-sm tracking-tight">moneylytics</span>
          <div className="ml-auto flex items-center gap-2">
            {impersonating && (
              <>
                <Badge variant="secondary" className="bg-orange-500/20 text-orange-600 border-orange-500/30 text-xs">
                  {t('admin.impersonation.active', { username: impersonating })}
                </Badge>
                <Button variant="outline" size="xs" onClick={deimpersonate}>
                  {t('admin.impersonation.stop')}
                </Button>
              </>
            )}
            <OrgAvatar
              organizations={organizations}
              activeOrganization={activeOrganization}
              onSwitch={activateOrganization}
            />
            <Button variant="ghost" size="sm" onClick={() => navigate('/einstellungen')} className="text-sm gap-1.5">
              <UserCog className="h-4 w-4 sm:hidden" />
              <span className="hidden sm:inline">{username}</span>
            </Button>
            <Button variant="ghost" size="sm" onClick={logout} className="gap-1.5">
              <LogOut className="h-4 w-4 sm:hidden" />
              <span className="hidden sm:inline">{t('common.signOut')}</span>
            </Button>
          </div>
        </header>

        {/* Filter subbar */}
        {ANALYTICS_TABS.has(tab) && <div className="flex shrink-0 flex-wrap items-center gap-2 border-b px-3 py-2 sm:px-4">
          {accounts.length > 0 && (
            <select
              className="flex-1 min-w-0 sm:flex-none rounded-lg border border-input bg-input/30 px-3 py-1.5 text-sm outline-none focus:border-ring"
              value={selectedAccountId}
              onChange={e => updateSearch({ accountId: e.target.value })}
            >
              <option value="">{t('common.allAccounts')}</option>
              {accounts.map(a => (
                <option key={a.iban} value={String(a.id)}>{a.name}</option>
              ))}
            </select>
          )}

          <select
            className="flex-1 min-w-0 sm:flex-none rounded-lg border border-input bg-input/30 px-3 py-1.5 text-sm outline-none focus:border-ring"
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

          <div className="flex w-full sm:w-auto items-center gap-1">
            <DatePicker
              value={parseIso(from)}
              onChange={d => d && updateSearch({ from: isoDate(d) })}
              max={parseIso(to)}
              placeholder={t('common.from')}
              className="flex-1 sm:flex-none"
            />
            <span className="text-muted-foreground px-1">–</span>
            <DatePicker
              value={parseIso(to)}
              onChange={d => d && updateSearch({ to: isoDate(d) })}
              min={parseIso(from)}
              max={parseIso(today)}
              placeholder={t('common.to')}
              className="flex-1 sm:flex-none"
            />
          </div>
        </div>}

        {/* Main content */}
        <div className="flex-1 overflow-auto">
          {tab === 'sankey' && (
            <>
              {view.phase === 'idle' && (
                <p className="flex items-center justify-center h-full text-sm text-muted-foreground">
                  <Trans i18nKey="sankey.hint"><span /><kbd /></Trans>
                </p>
              )}
              {view.phase === 'empty' && (
                <p className="flex items-center justify-center h-full text-sm text-muted-foreground">{t('sankey.noData')}</p>
              )}
              {view.phase === 'loading' && (
                <p className="flex items-center justify-center h-full text-sm text-muted-foreground">{t('common.fetching')}</p>
              )}
              {view.phase === 'error' && (
                <p className="flex items-center justify-center h-full text-sm text-destructive">{view.message}</p>
              )}
              {view.phase === 'ready' && (
                <div className="chart" key={`${accountId}/${from}/${to}`}>
                  <SankeyChart
                    data={view.data}
                    onNodeClick={node => setActiveNode(node)}
                  />
                  {activeNode && (
                    <TransactionListPanel
                      node={activeNode}
                      from={from}
                      to={to}
                      accountId={accountId}
                      onClose={() => setActiveNode(null)}
                    />
                  )}
                </div>
              )}
            </>
          )}

          {tab === 'cashflow' && <CashflowPage key={`${username}-${activeOrganization?.id}`} from={from} to={to} accountId={accountId} />}
          {tab === 'burnrate' && <BurnRatePage key={`${username}-${activeOrganization?.id}`} from={from} to={to} accountId={accountId} />}
          {tab === 'trends' && <TrendsPage key={`${username}-${activeOrganization?.id}`} from={from} to={to} accountId={accountId} categories={categories} />}
          {tab === 'breakdown' && <PiePage key={`${username}-${activeOrganization?.id}`} from={from} to={to} accountId={accountId} />}
          {tab === 'kontoauszug' && <TransactionsPage key={`${username}-${activeOrganization?.id}`} from={from} to={to} accountId={accountId} accounts={accounts} categories={categories} columnOrder={txColumnOrder ?? undefined} onColumnOrderChange={order => setTxColumnOrder(order)} onCategoryCreated={() => { fetchCategories().then(cats => setCategories(cats.categories)).catch(() => {}) }} />}
          {tab === 'verknuepfungen' && <LinkedTransactionsPage key={`${username}-${activeOrganization?.id}`} />}
          {tab === 'sammlungen' && <CollectionsPage key={`${username}-${activeOrganization?.id}`} accounts={accounts} categories={categories} />}
          {tab === 'budgets' && <BudgetsPage key={`${username}-${activeOrganization?.id}`} from={from} to={to} accountId={accountId} accounts={accounts} categories={categories} />}
          {tab === 'limits' && <ThresholdsPage key={`${username}-${activeOrganization?.id}`} accountId={accountId} categories={categories} />}
          {tab === 'konten' && <AccountsPage key={`${username}-${activeOrganization?.id}`} />}
          {tab === 'import' && <ImportPage key={`${username}-${activeOrganization?.id}`} />}
          {tab === 'importe' && <ImportsPage key={`${username}-${activeOrganization?.id}`} />}
          {tab === 'wiederkehrer' && <RecurringPage key={`${username}-${activeOrganization?.id}`} />}
          {tab === 'kategorien' && <CategoriesPage key={`${username}-${activeOrganization?.id}`} categories={categories} from={from} to={to} accountId={accountId} onCategoryDeleted={() => { fetchCategories().then(cats => setCategories(cats.categories)).catch(() => {}) }} onCategoryMoved={() => { fetchCategories().then(cats => setCategories(cats.categories)).catch(() => {}) }} onCategoryRenamed={() => { fetchCategories().then(cats => setCategories(cats.categories)).catch(() => {}) }} onCategoryMerged={() => { fetchCategories().then(cats => setCategories(cats.categories)).catch(() => {}) }} onCategoryCreated={() => { fetchCategories().then(cats => setCategories(cats.categories)).catch(() => {}) }} />}
          {tab === 'orgs' && isOrgAdminOrOwner && <OrgsPage key={activeOrganization?.id} />}
          {tab === 'admin' && isSystemAdmin && <AdminPage key={`${username}-${activeOrganization?.id}`} />}
          {tab === 'einstellungen' && <SettingsPage accounts={accounts} defaultAccountIban={accounts.find(a => String(a.id) === selectedAccountId)?.iban ?? ''} />}
        </div>
      </SidebarInset>

    </SidebarProvider>
  )
}
