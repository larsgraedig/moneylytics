import { useState, useEffect } from 'react'
import SankeyChart from './components/SankeyChart'
import RawImportPage from './components/RawImportPage'
import CamtImportPage from './components/CamtImportPage'
import CsvImportPage from './components/CsvImportPage'
import TrendsPage from './components/TrendsPage'
import ThresholdsPage from './components/ThresholdsPage'
import PiePage from './components/PiePage'
import TransactionListPanel from './components/TransactionListPanel'
import { fetchSankeyData, fetchAccounts, type SankeyResponse, type Account } from './api/transactions'
import { useUser } from './context/UserContext'

function isoDate(d: Date) {
  return d.toISOString().slice(0, 10)
}

const today = isoDate(new Date())
const firstOfYear = isoDate(new Date(new Date().getFullYear(), 0, 1))

type Tab = 'analytics' | 'trends' | 'breakdown' | 'thresholds' | 'csv' | 'import' | 'camt'

type ViewState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'ready'; data: SankeyResponse }

export default function App() {
  const { userId, users, setUserId } = useUser()
  const [tab, setTab] = useState<Tab>('analytics')
  const [from, setFrom] = useState(firstOfYear)
  const [to, setTo] = useState(today)
  const [accounts, setAccounts] = useState<Account[]>([])
  const [selectedIban, setSelectedIban] = useState<string>('')
  const [view, setView] = useState<ViewState>({ phase: 'idle' })
  const [activeNode, setActiveNode] = useState<string | null>(null)

  useEffect(() => {
    setAccounts([])
    setSelectedIban('')
    setView({ phase: 'idle' })
    setActiveNode(null)
    fetchAccounts().then(setAccounts).catch(() => {})
  }, [userId])

  async function load() {
    setView({ phase: 'loading' })
    try {
      const data = await fetchSankeyData(from, to, selectedIban || undefined)
      setView(data.nodes.length === 0 ? { phase: 'idle' } : { phase: 'ready', data })
    } catch (e) {
      setView({ phase: 'error', message: e instanceof Error ? e.message : 'request failed' })
    }
  }

  return (
    <div className="shell">
      <header className="bar">
        <span className="wordmark">moneylytics</span>

        <nav className="tab-nav">
          <button
            className={`tab-btn${tab === 'analytics' ? ' active' : ''}`}
            onClick={() => setTab('analytics')}
          >
            analytics
          </button>
          <button
            className={`tab-btn${tab === 'trends' ? ' active' : ''}`}
            onClick={() => setTab('trends')}
          >
            trends
          </button>
          <button
            className={`tab-btn${tab === 'breakdown' ? ' active' : ''}`}
            onClick={() => setTab('breakdown')}
          >
            breakdown
          </button>
          <button
            className={`tab-btn${tab === 'thresholds' ? ' active' : ''}`}
            onClick={() => setTab('thresholds')}
          >
            thresholds
          </button>
          <button
            className={`tab-btn${tab === 'csv' ? ' active' : ''}`}
            onClick={() => setTab('csv')}
          >
            CSV import
          </button>
          <button
            className={`tab-btn${tab === 'import' ? ' active' : ''}`}
            onClick={() => setTab('import')}
          >
            MLP import
          </button>
          <button
            className={`tab-btn${tab === 'camt' ? ' active' : ''}`}
            onClick={() => setTab('camt')}
          >
            CAMT import
          </button>
        </nav>

        {tab === 'analytics' && (
          <div className="controls">
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
                <input
                  type="date"
                  value={from}
                  max={to}
                  onChange={e => setFrom(e.target.value)}
                />
              </label>
              <div className="range-sep" />
              <label className="range-field">
                <span className="range-label">to</span>
                <input
                  type="date"
                  value={to}
                  min={from}
                  max={today}
                  onChange={e => setTo(e.target.value)}
                />
              </label>
            </fieldset>

            <button
              className="load-btn"
              onClick={load}
              disabled={view.phase === 'loading'}
            >
              {view.phase === 'loading' ? '…' : 'load'}
            </button>
          </div>
        )}

        {(tab === 'csv' || tab === 'import' || tab === 'camt' || tab === 'trends' || tab === 'thresholds' || tab === 'breakdown') && <div className="controls" />}

        <select
          className="user-select"
          value={userId}
          onChange={e => setUserId(e.target.value)}
        >
          {[...new Set([userId, ...users])].map(u => (
            <option key={u} value={u}>{u}</option>
          ))}
        </select>
      </header>

      <main className="stage">
        {tab === 'analytics' && (
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
              <div className="chart" key={`${selectedIban}/${from}/${to}`}>
                <SankeyChart
                  data={view.data}
                  onNodeClick={nodeKey => setActiveNode(nodeKey)}
                />
                {activeNode && (
                  <TransactionListPanel
                    nodeKey={activeNode}
                    from={from}
                    to={to}
                    iban={selectedIban || undefined}
                    onClose={() => setActiveNode(null)}
                  />
                )}
              </div>
            )}
          </>
        )}

        {tab === 'trends' && <TrendsPage key={userId} />}
        {tab === 'breakdown' && <PiePage key={userId} />}
        {tab === 'thresholds' && <ThresholdsPage key={userId} />}
        {tab === 'csv' && <CsvImportPage key={userId} />}
        {tab === 'import' && <RawImportPage key={userId} />}
        {tab === 'camt' && <CamtImportPage key={userId} />}
      </main>
    </div>
  )
}
