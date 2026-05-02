import { useState, useRef, useCallback, useEffect } from 'react'
import api from '../api/axios'
import styles from './AiPanel.module.css'

const LANG_OPTIONS = [
  { value: 'java',       label: 'Java' },
  { value: 'python',     label: 'Python' },
  { value: 'javascript', label: 'JavaScript' },
  { value: 'typescript', label: 'TypeScript' },
  { value: 'html',       label: 'HTML' },
  { value: 'css',        label: 'CSS' },
  { value: 'sql',        label: 'SQL' },
  { value: 'go',         label: 'Go' },
  { value: 'cpp',        label: 'C++' },
]

const DEBOUNCE_MS = 2000

function detectLang(file) {
  if (!file) return 'java'
  const ext = file.name?.split('.').pop()?.toLowerCase()
  const map = { java: 'java', py: 'python', js: 'javascript', ts: 'typescript',
                html: 'html', css: 'css', sql: 'sql', go: 'go', cpp: 'cpp', c: 'cpp' }
  return map[ext] || 'java'
}

// Animated dots component for loading state
function LoadingDots() {
  return (
    <span className={styles.dots}>
      <span>.</span><span>.</span><span>.</span>
    </span>
  )
}

// Skeleton block shown while AI is generating
function SkeletonBlock() {
  return (
    <div className={styles.skeleton}>
      <div className={styles.skeletonLine} style={{ width: '90%' }} />
      <div className={styles.skeletonLine} style={{ width: '75%' }} />
      <div className={styles.skeletonLine} style={{ width: '85%' }} />
      <div className={styles.skeletonLine} style={{ width: '60%' }} />
      <div className={styles.skeletonLine} style={{ width: '80%' }} />
    </div>
  )
}

export default function AiPanel({ selectedFile, editorRef, onInsert, canEdit }) {
  const [mode, setMode]         = useState('generate')
  const [prompt, setPrompt]     = useState('')
  const [language, setLanguage] = useState(detectLang(selectedFile))
  const [loading, setLoading]   = useState(false)
  const [error, setError]       = useState('')
  const [cooldown, setCooldown] = useState(0)
  const [genResult, setGenResult]         = useState('')
  const [copied, setCopied]               = useState(false)
  const [improveSuccess, setImproveSuccess] = useState(false)
  const [insertedSuccess, setInsertedSuccess] = useState(false)

  const lastRequest  = useRef(0)
  const cooldownRef  = useRef(null)
  const resultRef    = useRef(null)  // for auto-scroll
  const inFlight     = useRef(false) // hard lock — prevents any duplicate in-flight call

  const hasFile = !!selectedFile

  useEffect(() => {
    setLanguage(detectLang(selectedFile))
    setGenResult('')
    setImproveSuccess(false)
    setError('')
  }, [selectedFile?.id])

  // Auto-scroll to result when it appears
  useEffect(() => {
    if (genResult && resultRef.current) {
      resultRef.current.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    }
  }, [genResult])

  const switchMode = (m) => {
    setMode(m)
    setGenResult('')
    setImproveSuccess(false)
    setInsertedSuccess(false)
    setError('')
    setPrompt('')
  }

  const startCooldown = (secs) => {
    setCooldown(secs)
    if (cooldownRef.current) clearInterval(cooldownRef.current)
    cooldownRef.current = setInterval(() => {
      setCooldown(prev => {
        if (prev <= 1) { clearInterval(cooldownRef.current); return 0 }
        return prev - 1
      })
    }, 1000)
  }

  const handleError = (err) => {
    const status = err.response?.status
    const msg    = err.response?.data?.error || err.response?.data?.message || err.message || 'Failed'
    if (status === 429) { setError('Rate limit hit. Please wait 15 seconds.'); startCooldown(15) }
    else if (status === 401) { setError('OpenAI API key invalid. Contact admin.') }
    else { setError(msg) }
  }

  // ── GENERATE MODE ─────────────────────────────────────────────────────────
  const handleGenerate = useCallback(async () => {
    // Triple guard: loading state + debounce + in-flight ref
    if (loading || cooldown > 0 || !prompt.trim()) return
    if (inFlight.current) { console.warn('[AI] Blocked duplicate call'); return }
    if (Date.now() - lastRequest.current < DEBOUNCE_MS) return
    lastRequest.current = Date.now()
    inFlight.current = true

    console.log('[AI] Generate called — prompt:', prompt.trim().substring(0, 60))

    setLoading(true)
    setError('')
    setGenResult('')
    setCopied(false)
    setInsertedSuccess(false)

    try {
      const res = await api.post('/ai/generate', {
        prompt: prompt.trim(),
        language,
        fileName: null,
        existingCode: null,
      })
      console.log('[AI] Response received:', res.data)
      const code = res.data.code || res.data.generatedCode || ''
      if (!code) throw new Error('AI returned empty response')
      setGenResult(code)
    } catch (err) {
      console.error('[AI] Error:', err.response?.status, err.response?.data || err.message)
      handleError(err)
    } finally {
      setLoading(false)
      inFlight.current = false
    }
  }, [loading, cooldown, prompt, language])

  // ── IMPROVE MODE ──────────────────────────────────────────────────────────
  const handleImprove = useCallback(async () => {
    if (loading || cooldown > 0 || !prompt.trim()) return
    if (!hasFile) { setError('Please select a file first.'); return }
    if (inFlight.current) { console.warn('[AI] Blocked duplicate improve call'); return }

    const currentCode = editorRef?.current?.getValue() || selectedFile?.content || ''
    if (!currentCode.trim()) { setError('The selected file is empty.'); return }

    if (Date.now() - lastRequest.current < DEBOUNCE_MS) return
    lastRequest.current = Date.now()
    inFlight.current = true

    console.log('[AI] Improve called — file:', selectedFile.name)

    setLoading(true)
    setError('')
    setImproveSuccess(false)

    try {
      const res = await api.post('/ai/improve', {
        prompt:       prompt.trim(),
        language,
        fileName:     selectedFile.name,
        existingCode: currentCode,
      })
      console.log('[AI] Improve response received')
      const code = res.data.code || res.data.generatedCode || ''
      if (!code) throw new Error('AI returned empty response')

      if (editorRef?.current) {
        editorRef.current.setValue(code)
        editorRef.current.focus()
      }
      if (onInsert) onInsert(code, 'replace')

      setImproveSuccess(true)
      setTimeout(() => setImproveSuccess(false), 3000)
    } catch (err) {
      console.error('[AI] Improve error:', err.response?.status, err.response?.data || err.message)
      handleError(err)
    } finally {
      setLoading(false)
      inFlight.current = false
    }
  }, [loading, cooldown, prompt, language, selectedFile, editorRef, onInsert, hasFile])

  const handleKeyDown = (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault()
      mode === 'generate' ? handleGenerate() : handleImprove()
    }
  }

  const copyToClipboard = () => {
    navigator.clipboard.writeText(genResult)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const handleInsert = () => {
    if (!genResult) return
    if (editorRef?.current) {
      editorRef.current.setValue(genResult)
      editorRef.current.focus()
      if (onInsert) onInsert(genResult, 'replace')
    } else if (onInsert) {
      onInsert(genResult, 'replace')
    }
    setInsertedSuccess(true)
    setTimeout(() => setInsertedSuccess(false), 2500)
  }

  const isDisabled = loading || cooldown > 0 || !prompt.trim()

  return (
    <div className={styles.panel}>

      {/* ── Header ── */}
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <span className={styles.title}>✨ AI Code Assistant</span>
          <span className={styles.model}>gpt-4o-mini</span>
        </div>
        {selectedFile && <span className={styles.fileCtx}>📄 {selectedFile.name}</span>}
      </div>

      <div className={styles.body}>

        {/* ── Mode toggle ── */}
        <div className={styles.modeRow}>
          <button
            className={mode === 'generate' ? styles.modeActive : styles.modeBtn}
            onClick={() => switchMode('generate')}
            disabled={loading}
          >
            ✦ Generate Code
          </button>
          <button
            className={mode === 'improve' ? styles.modeActive : styles.modeBtn}
            onClick={() => switchMode('improve')}
            disabled={!hasFile || loading}
            title={!hasFile ? 'Select a file first' : 'Improve the open file'}
          >
            ⟳ Improve File
          </button>
        </div>

        {/* ── Mode description ── */}
        {mode === 'generate' && !loading && (
          <p className={styles.modeDesc}>
            AI generates code and shows it below. Use "Insert" to put it in the editor.
          </p>
        )}
        {mode === 'improve' && !loading && (
          <p className={hasFile ? styles.modeDescGreen : styles.modeDescWarn}>
            {hasFile
              ? `✓ Will modify ${selectedFile.name} directly in the editor.`
              : '⚠ Select a file from the explorer first.'}
          </p>
        )}

        {/* ── Language selector ── */}
        <div className={styles.row}>
          <label className={styles.label}>Language</label>
          <select
            value={language}
            onChange={(e) => setLanguage(e.target.value)}
            className={styles.select}
            disabled={loading}
          >
            {LANG_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
        </div>

        {/* ── Prompt input ── */}
        <label className={styles.label}>
          Prompt <span className={styles.shortcut}>(Ctrl+Enter to generate)</span>
        </label>
        <textarea
          className={`${styles.promptInput} ${loading ? styles.promptDisabled : ''}`}
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={
            mode === 'generate'
              ? 'e.g. Write a Java program to add two numbers'
              : 'e.g. Add error handling and input validation'
          }
          rows={4}
          disabled={loading}
        />

        {/* ── Generate / Improve button ── */}
        <button
          onClick={mode === 'generate' ? handleGenerate : handleImprove}
          disabled={isDisabled || (mode === 'improve' && !hasFile)}
          className={`${styles.generateBtn} ${loading ? styles.generateBtnLoading : ''}`}
        >
          {loading ? (
            <span className={styles.btnInner}>
              <span className={styles.spinner} />
              Generating<LoadingDots />
            </span>
          ) : cooldown > 0 ? (
            `⏱ Wait ${cooldown}s...`
          ) : mode === 'generate' ? (
            '✨ Generate Code'
          ) : (
            '⟳ Improve File'
          )}
        </button>

        {/* ── Cooldown bar ── */}
        {cooldown > 0 && (
          <div className={styles.cooldownBar}>
            <div className={styles.cooldownFill} style={{ width: `${(cooldown / 15) * 100}%` }} />
          </div>
        )}

        {/* ── Loading status text ── */}
        {loading && (
          <div className={styles.loadingStatus}>
            <span className={styles.loadingDot} />
            <span>
              {mode === 'generate'
                ? 'Generating code with AI'
                : 'Improving your file with AI'}
              <LoadingDots />
            </span>
          </div>
        )}

        {/* ── Skeleton block while loading ── */}
        {loading && mode === 'generate' && <SkeletonBlock />}

        {/* ── Error ── */}
        {error && !loading && (
          <div className={styles.error}>
            <span>⚠ {error}</span>
            <button onClick={() => setError('')} className={styles.dismissBtn}>✕</button>
          </div>
        )}

        {/* ── Improve success banner ── */}
        {improveSuccess && (
          <div className={styles.successBanner}>
            ✓ File updated in editor and saved!
          </div>
        )}

        {/* ── GENERATE MODE: result panel ── */}
        {mode === 'generate' && genResult && !loading && (
          <div className={styles.resultArea} ref={resultRef}>
            <div className={styles.resultHeader}>
              <div className={styles.resultTitle}>
                <span className={styles.resultDot} />
                Generated Code
              </div>
              <div className={styles.resultActions}>
                {canEdit && (
                  <button
                    onClick={handleInsert}
                    className={insertedSuccess ? styles.insertedBtn : styles.insertBtn}
                    disabled={!hasFile && !onInsert}
                    title={!hasFile ? 'Select a file first' : 'Insert into editor'}
                  >
                    {insertedSuccess ? '✓ Inserted!' : '→ Insert into Editor'}
                  </button>
                )}
                <button onClick={copyToClipboard} className={styles.copyBtn}>
                  {copied ? '✓ Copied!' : '📋 Copy'}
                </button>
              </div>
            </div>
            <pre className={styles.code}>{genResult}</pre>
          </div>
        )}

      </div>
    </div>
  )
}
