import { useState, useEffect } from 'react'
import SankeyChart from './components/SankeyChart'
import RawImportPage from './components/RawImportPage'
import CamtImportPage from './components/CamtImportPage'
import CsvImportPage from './components/CsvImportPage'
import TrendsPage from './components/TrendsPage'
import TransactionListPanel from './components/TransactionListPanel'
import { fetchSankeyData, fetchAccounts, type SankeyResponse, type Account } from './api/transactions'

function isoDate(d: Date) {
  return d.toISOString().slice(0, 10)
}

const today = isoDate(new Date())
const firstOfYear = isoDate(new Date(new Date().getFullYear(), 0, 1))

type Tab = 'analytics' | 'trends' | 'csv' | 'import' | 'camt'

type ViewState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'ready'; data: SankeyResponse }

export default function App() {
  const [tab, setTab] = useState<Tab>('analytics')
  const [from, setFrom] = useState(firstOfYear)
  const [to, setTo] = useState(today)
  const [accounts, setAccounts] = useState<Account[]>([])
  const [selectedIban, setSelectedIban] = useState<string>('')
  const [view, setView] = useState<ViewState>({ phase: 'idle' })
  const [activeNode, setActiveNode] = useState<string | null>(null)

  useEffect(() => {
    fetchAccounts().then(setAccounts).catch(() => {/* accounts unavailable, dropdown stays empty */})
  }, [])

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

        {(tab === 'csv' || tab === 'import' || tab === 'camt' || tab === 'trends') && <div className="controls" />}
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

        {tab === 'trends' && <TrendsPage />}
        {tab === 'csv' && <CsvImportPage />}
        {tab === 'import' && <RawImportPage />}
        {tab === 'camt' && <CamtImportPage />}
      </main>
    </div>
  )
}
