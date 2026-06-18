import { useState, useEffect } from 'react'
import SankeyChart from './components/SankeyChart'
import CamtImportPage from './components/CamtImportPage'
import CsvImportPage from './components/CsvImportPage'
import TrendsPage from './components/TrendsPage'
import ThresholdsPage from './components/ThresholdsPage'
import PiePage from './components/PiePage'
import TransactionsPage from './components/TransactionsPage'
import TransactionListPanel from './components/TransactionListPanel'
import CashflowPage from './components/CashflowPage'
import AccountsPage from './components/AccountsPage'
import LoginPage from './components/LoginPage'
import { fetchSankeyData, fetchAccounts, type SankeyResponse, type Account } from './api/transactions'
import { useAuth } from './context/AuthContext'

function isoDate(d: Date) {
  return d.toISOString().slice(0, 10)
}

const today = isoDate(new Date())
const firstOfYear = isoDate(new Date(new Date().getFullYear(), 0, 1))

type Tab = 'sankey' | 'trends' | 'breakdown' | 'cashflow' | 'kontoauszug' | 'konten' | 'budgets' | 'csv' | 'camt'

type ViewState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'ready'; data: SankeyResponse }

const NAV: { section: string; items: [Tab, string][] }[] = [
  {
    section: 'Analytics',
    items: [
      ['sankey', 'Sankey'],
      ['trends', 'Trends'],
      ['breakdown', 'Breakdown'],
      ['cashflow', 'Cashflow'],
    ],
  },
  {
    section: 'Accounts',
    items: [
      ['kontoauszug', 'Kontoauszug'],
      ['konten', 'Konten'],
      ['budgets', 'Budgets'],
    ],
  },
  {
    section: 'Importe',
    items: [
      ['csv', 'CSV'],
      ['camt', 'CAMT'],
    ],
  },
]

export default function App() {
  const { username, isLoading, logout } = useAuth()
  const [tab, setTab] = useState<Tab>('sankey')
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [from, setFrom] = useState(firstOfYear)
  const [to, setTo] = useState(today)
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
    fetchAccounts().then(setAccounts).catch(() => {})
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
          title={sidebarOpen ? 'Menü einklappen' : 'Menü ausklappen'}
        >
          <span className="sidebar-toggle-icon">{sidebarOpen ? '‹' : '›'}</span>
        </button>

        <nav className="sidebar-nav">
          {NAV.map(({ section, items }) => (
            <div key={section} className="nav-section">
              <span className="nav-section-title">{section}</span>
              {items.map(([id, label]) => (
                <button
                  key={id}
                  className={`nav-item${tab === id ? ' active' : ''}`}
                  onClick={() => setTab(id)}
                  title={label}
                >
                  <span className="nav-item-label">{label}</span>
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
            <span className="session-user">{username}</span>
            <button className="logout-btn" onClick={logout}>sign out</button>
          </div>
        </header>

        <div className="subbar">
          {accounts.length > 0 && (
            <select
              className="account-select"
              value={selectedIban}
              onChange={e => setSelectedIban(e.target.value)}
            >
              <option value="">all accounts</option>
              {accounts.map(a => (
                <option key={a.iban} value={a.iban}>{a.name}</option>
              ))}
            </select>
          )}

          <fieldset className="range-group">
            <label className="range-field">
              <span className="range-label">from</span>
              <input type="date" value={from} max={to} onChange={e => setFrom(e.target.value)} />
            </label>
            <div className="range-sep" />
            <label className="range-field">
              <span className="range-label">to</span>
              <input type="date" value={to} min={from} max={today} onChange={e => setTo(e.target.value)} />
            </label>
          </fieldset>

          {tab === 'sankey' && (
            <button className="load-btn" onClick={load} disabled={view.phase === 'loading'}>
              {view.phase === 'loading' ? '…' : 'load'}
            </button>
          )}
        </div>

        <main className="stage">
          {tab === 'sankey' && (
            <>
              {view.phase === 'idle' && (
                <p className="hint">select a date range and press <kbd>load</kbd></p>
              )}
              {view.phase === 'loading' && (
                <p className="hint loading">fetching…</p>
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
          {tab === 'budgets' && <ThresholdsPage key={username} from={from} to={to} iban={iban} />}
          {tab === 'konten' && <AccountsPage key={username} />}
          {tab === 'csv' && <CsvImportPage key={username} />}
          {tab === 'camt' && <CamtImportPage key={username} />}
        </main>
      </div>
    </div>
  )
}
