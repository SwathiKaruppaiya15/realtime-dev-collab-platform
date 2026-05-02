import { useState, useEffect, useRef } from 'react'
import api from '../api/axios'
import { useTerminal } from '../hooks/useTerminal'
import styles from './Terminal.module.css'

const LANG_MAP = {
  js: 'javascript', jsx: 'javascript', ts: 'javascript',
  py: 'python', java: 'java',
}

function detectLang(ext) {
  return LANG_MAP[ext?.toLowerCase()] || 'python'
}

// ─── Run Code Tab ─────────────────────────────────────────────────────────────

function RunCodeTab({ defaultCode, defaultLanguage, selectedFile }) {
  const [code, setCode] = useState(defaultCode || '')
  const [language, setLanguage] = useState(detectLang(defaultLanguage))
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [previewHtml, setPreviewHtml] = useState(null)

  // Sync code when selected file changes
  useEffect(() => {
    if (defaultCode !== undefined) setCode(defaultCode)
    if (defaultLanguage) setLanguage(detectLang(defaultLanguage))
    setResult(null)
    setPreviewHtml(null)
  }, [defaultCode, defaultLanguage])

  const isHtml = language === 'html' || language === 'css'

  const run = async () => {
    setResult(null)
    setPreviewHtml(null)

    // HTML/CSS — iframe preview, no backend needed
    if (isHtml) {
      setPreviewHtml(code)
      return
    }

    setLoading(true)
    try {
      const res = await api.post('/run', {
        fileName: selectedFile?.name || `main.${language === 'javascript' ? 'js' : language === 'python' ? 'py' : language === 'cpp' ? 'cpp' : language === 'c' ? 'c' : 'java'}`,
        code,
        language,
        roomId: null,
      })
      setResult(res.data)
    } catch (err) {
      setResult({
        output: '',
        error: err.response?.data?.error || 'Execution failed',
        status: 'ERROR',
        exitCode: 1,
      })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={styles.runTab}>
      <div className={styles.runToolbar}>
        <select value={language} onChange={(e) => { setLanguage(e.target.value); setResult(null); setPreviewHtml(null) }} className={styles.select}>
          <option value="python">Python</option>
          <option value="javascript">JavaScript (Node)</option>
          <option value="java">Java</option>
          <option value="cpp">C++</option>
          <option value="c">C</option>
          <option value="html">HTML (Preview)</option>
        </select>
        <button onClick={run} disabled={loading} className={styles.runBtn}>
          {loading ? '⏳ Running...' : isHtml ? '👁 Preview' : '▶ Run'}
        </button>
      </div>

      <textarea
        className={styles.codeInput}
        value={code}
        onChange={(e) => setCode(e.target.value)}
        placeholder={`Write ${language} code here...`}
        spellCheck={false}
      />

      {/* HTML iframe preview */}
      {previewHtml && (
        <iframe
          className={styles.previewFrame}
          srcDoc={previewHtml}
          sandbox="allow-scripts"
          title="HTML Preview"
        />
      )}

      {/* Code output */}
      {!previewHtml && (
        <div className={styles.outputArea}>
          <div className={styles.outputHeader}>
            Output
            {result && (
              <span className={result.status === 'SUCCESS' ? styles.statusOk : styles.statusErr}>
                {result.status} (exit {result.exitCode})
              </span>
            )}
          </div>

          {result === null && <p className={styles.hint}>Click ▶ Run to execute your code</p>}

          {result !== null && (
            <>
              {result.output && <pre className={styles.stdout}>{result.output}</pre>}
              {result.error && <pre className={styles.stderr}>{result.error}</pre>}
              {!result.output && !result.error && <pre className={styles.hint}>No output</pre>}
            </>
          )}
        </div>
      )}
    </div>
  )
}

// ─── Interactive Terminal Tab ─────────────────────────────────────────────────

function InteractiveTerminal({ roomId }) {
  const [history, setHistory] = useState([
    { type: 'info', text: 'Connecting to terminal...' }
  ])
  const [input, setInput] = useState('')
  const [cmdHistory, setCmdHistory] = useState([])
  const [historyIndex, setHistoryIndex] = useState(-1)
  const outputRef = useRef(null)
  const inputRef = useRef(null)

  const { sendCommand, onOutput, connected } = useTerminal(roomId)

  // Register output callback
  useEffect(() => {
    onOutput((text) => {
      setHistory(prev => [...prev, { type: 'output', text }])
    })
  }, [onOutput])

  useEffect(() => {
    if (connected) {
      setHistory(prev => [...prev, { type: 'info', text: '✓ Terminal connected\n' }])
    }
  }, [connected])

  // Auto-scroll to bottom
  useEffect(() => {
    if (outputRef.current) {
      outputRef.current.scrollTop = outputRef.current.scrollHeight
    }
  }, [history])

  const handleSubmit = (e) => {
    e.preventDefault()
    if (!input.trim()) return

    const cmd = input.trim()

    // Show command in terminal
    setHistory(prev => [...prev, { type: 'command', text: '$ ' + cmd + '\n' }])
    setCmdHistory(prev => [cmd, ...prev.slice(0, 49)])
    setHistoryIndex(-1)
    setInput('')

    sendCommand(cmd)
  }

  const handleKeyDown = (e) => {
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      const newIndex = Math.min(historyIndex + 1, cmdHistory.length - 1)
      setHistoryIndex(newIndex)
      setInput(cmdHistory[newIndex] || '')
    } else if (e.key === 'ArrowDown') {
      e.preventDefault()
      const newIndex = Math.max(historyIndex - 1, -1)
      setHistoryIndex(newIndex)
      setInput(newIndex === -1 ? '' : cmdHistory[newIndex])
    }
  }

  const clearTerminal = () => setHistory([])

  return (
    <div className={styles.interactiveTerminal} onClick={() => inputRef.current?.focus()}>
      <div className={styles.terminalHeader}>
        <span className={styles.terminalTitle}>⚡ Terminal</span>
        <span className={connected ? styles.connectedDot : styles.disconnectedDot}>
          {connected ? '● Connected' : '○ Disconnected'}
        </span>
        <button onClick={clearTerminal} className={styles.clearBtn}>Clear</button>
      </div>

      <div className={styles.terminalOutput} ref={outputRef}>
        {history.map((entry, i) => (
          <span
            key={i}
            className={
              entry.type === 'command' ? styles.termCmd :
              entry.type === 'info'    ? styles.termInfo :
              styles.termOut
            }
          >
            {entry.text}
          </span>
        ))}
      </div>

      <form onSubmit={handleSubmit} className={styles.terminalInput}>
        <span className={styles.prompt}>$</span>
        <input
          ref={inputRef}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Type a command..."
          autoComplete="off"
          spellCheck={false}
          disabled={!connected}
        />
      </form>
    </div>
  )
}

// ─── Main Terminal Component ──────────────────────────────────────────────────

export default function Terminal({ defaultCode, defaultLanguage, selectedFile, roomId }) {
  const [tab, setTab] = useState('run') // 'run' | 'terminal'

  return (
    <div className={styles.wrapper}>
      <div className={styles.tabBar}>
        <button
          className={tab === 'run' ? styles.activeTabBtn : styles.tabBtn}
          onClick={() => setTab('run')}
        >
          ▶ Run Code
        </button>
        <button
          className={tab === 'terminal' ? styles.activeTabBtn : styles.tabBtn}
          onClick={() => setTab('terminal')}
        >
          ⚡ Terminal
        </button>
      </div>

      {tab === 'run' && (
        <RunCodeTab
          defaultCode={defaultCode}
          defaultLanguage={defaultLanguage}
          selectedFile={selectedFile}
        />
      )}

      {tab === 'terminal' && (
        <InteractiveTerminal roomId={roomId} />
      )}
    </div>
  )
}
