import { ServiceTabs } from './components/ServiceTabs'

function App() {
  return (
    <div className="app">
      <header className="app-header">
        <h1 className="app-title">ProcMan</h1>
        <span className="app-subtitle">Process Manager</span>
      </header>
      <main className="app-main">
        <ServiceTabs />
      </main>
    </div>
  )
}

export default App
