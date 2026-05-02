import { useState, useEffect, useRef, useCallback } from 'react'
import Editor from '@monaco-editor/react'
import { useWebSocket } from '../hooks/useWebSocket'
import api from '../api/axios'
import styles from './CodeEditor.module.css'

const LANGUAGE_MAP = {
  js: 'javascript', jsx: 'javascript',
  ts: 'typescript', tsx: 'typescript',
  py: 'python', java: 'java',
  html: 'html', css: 'css', scss: 'scss',
  json: 'json', md: 'markdown',
  txt: 'plaintext', xml: 'xml',
  sql: 'sql', sh: 'shell', yaml: 'yaml', yml: 'yaml',
  c: 'c', cpp: 'cpp', cs: 'csharp', go: 'go', rs: 'rust',
  php: 'php', rb: 'ruby', kt: 'kotlin', swift: 'swift',
}

// Map extension → backend language string
const RUN_LANG_MAP = {
  java: 'java',
  py:   'python',
  js:   'javascript',
  cpp:  'cpp',
  cc:   'cpp',
  c:    'c',
  html: 'html',
  htm:  'html',
  css:  'css',
}

function getMonacoLang(filename, extension) {
  const ext = extension || filename?.split('.').pop()?.toLowerCase()
  return LANGUAGE_MAP[ext] || 'plaintext'
}

function getRunLang(filename, extension) {
  const ext = (extension || filename?.split('.').pop() || '').toLowerCase()
  return RUN_LANG_MAP[ext] || null
}

export default function CodeEditor({ file, roomId, onSave, readOnly, onFileOpen, editorRef: externalEditorRef }) {
  const [content, setContent]   = useState(file.content || '')
  const [language, setLanguage] = useState(getMonacoLang(file.name, file.extension))
  const [saved, setSaved]       = useState(true)
  const [cursors, setCursors]   = useState({})

  const [running, setRunning]       = useState(false)
  const [runResult, setRunResult]   = useState(null)
  const [showOutput, setShowOutput] = useState(false)
  const [previewHtml, setPreviewHtml] = useState(null) // for HTML preview

  const internalEditorRef = useRef(null)
  const editorRef         = externalEditorRef || internalEditorRef
  const decorationsRef    = useRef([])
  const isRemoteRef       = useRef(false)

  const runLang = getRunLang(file.name, file.extension)
  const isHtml  = runLang === 'html' || runLang === 'css'

  const handleRemoteCodeChange = useCallback((data) => {
    if (data.fileId === file.id) {
      isRemoteRef.current = true
      setContent(data.content)
    }
  }, [file.id])

  const handleCursorMove = useCallback((data) => {
    const me = JSON.parse(localStorage.getItem('user') || '{}')
    if (data.userId === String(me.id)) return
    if (data.fileId !== file.id) return
    setCursors(prev => ({
      ...prev,
      [data.userId]: { line: data.line, column: data.column, color: data.color, userName: data.userName }
    }))
  }, [file.id])

  const { sendCodeChange, sendCursorMove, sendFileOpen } = useWebSocket({
    roomId, fileId: file.id,
    onCodeChange: handleRemoteCodeChange,
    onCursorMove: handleCursorMove,
    onFileOpen,
  })

  useEffect(() => {
    setContent(file.content || '')
    setLanguage(getMonacoLang(file.name, file.extension))
    setSaved(true)
    setCursors({})
    setRunResult(null)
    setShowOutput(false)
    setPreviewHtml(null)
    sendFileOpen(file.id, file.name)
  }, [file.id])

  useEffect(() => {
    if (!editorRef.current || !window.monaco) return
    const monaco = window.monaco
    const newDecorations = Object.entries(cursors).map(([, cur]) => ({
      range: new monaco.Range(cur.line, cur.column, cur.line, cur.column + 1),
      options: {
        inlineClassName: 'remote-cursor',
        before: { content: cur.userName, inlineClassName: 'cursor-label' },
        stickiness: monaco.editor.TrackedRangeStickiness.NeverGrowsWhenTypingAtEdges,
      },
    }))
    decorationsRef.current = editorRef.current.deltaDecorations(decorationsRef.current, newDecorations)
  }, [cursors])

  const handleChange = (value) => {
    if (isRemoteRef.current) { isRemoteRef.current = false; return }
    setContent(value)
    setSaved(false)
    sendCodeChange(value)
  }

  const handleEditorDidMount = (editor) => {
    editorRef.current = editor
    editor.onDidChangeCursorPosition((e) => {
      sendCursorMove(e.position.lineNumber, e.position.column)
    })
  }

  const handleSave = async () => {
    await onSave(content)
    setSaved(true)
  }

  const handleKeyDown = (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 's') {
      e.preventDefault()
      if (!readOnly) handleSave()
    }
  }

  const handleRun = async () => {
    if (running || !runLang) return
    const code = editorRef.current?.getValue() || content
    if (!code.trim()) return

    // HTML/CSS — iframe preview, no backend call needed
    if (isHtml) {
      setPreviewHtml(code)
      setShowOutput(true)
      return
    }

    setRunning(true)
    setRunResult(null)
    setPreviewHtml(null)
    setShowOutput(true)

    try {
      const res = await api.post('/run', {
        fileName: file.name,
        code,
        language: runLang,
        roomId: roomId ? parseInt(roomId) : null,
      })
      setRunResult(res.data)
    } catch (err) {
      setRunResult({
        output: '',
        error: err.response?.data?.error || 'Execution failed',
        status: 'ERROR',
        exitCode: 1,
      })
    } finally {
      setRunning(false)
    }
  }

  const editorHeight = showOutput ? 'calc(100vh - 280px)' : 'calc(100vh - 105px)'

  return (
    <div className={styles.container} onKeyDown={handleKeyDown}>
      {/* Toolbar */}
      <div className={styles.toolbar}>
        <span className={styles.filename}>{file.name}</span>

        <select value={language} onChange={(e) => setLanguage(e.target.value)} className={styles.langSelect}>
          <option value="javascript">JavaScript</option>
          <option value="typescript">TypeScript</option>
          <option value="python">Python</option>
          <option value="java">Java</option>
          <option value="cpp">C++</option>
          <option value="c">C</option>
          <option value="html">HTML</option>
          <option value="css">CSS</option>
          <option value="json">JSON</option>
          <option value="markdown">Markdown</option>
          <option value="sql">SQL</option>
          <option value="shell">Shell</option>
          <option value="plaintext">Plain Text</option>
        </select>

        {Object.keys(cursors).length > 0 && (
          <div className={styles.activeCursors}>
            {Object.entries(cursors).map(([id, c]) => (
              <span key={id} className={styles.cursorBadge} style={{ background: c.color }}>
                {c.userName}
              </span>
            ))}
          </div>
        )}

        {runLang && !readOnly && (
          <button onClick={handleRun} disabled={running} className={styles.runBtn}>
            {running ? '⏳ Running...' : isHtml ? '👁 Preview' : '▶ Run'}
          </button>
        )}

        {readOnly ? (
          <span className={styles.readonlyBadge}>👁 Read Only</span>
        ) : (
          <button onClick={handleSave} className={`${styles.saveBtn} ${saved ? styles.saved : styles.unsaved}`}>
            {saved ? '✓ Saved' : '💾 Save (Ctrl+S)'}
          </button>
        )}
      </div>

      {/* Monaco Editor */}
      <Editor
        height={editorHeight}
        language={language}
        value={content}
        onChange={handleChange}
        onMount={handleEditorDidMount}
        theme="vs-dark"
        options={{
          fontSize: 14,
          lineNumbers: 'on',
          minimap: { enabled: false },
          readOnly,
          wordWrap: 'on',
          scrollBeyondLastLine: false,
          automaticLayout: true,
          tabSize: 2,
        }}
      />

      {/* Output / Preview panel */}
      {showOutput && (
        <div className={styles.outputPanel}>
          <div className={styles.outputHeader}>
            <span>
              {previewHtml ? '👁 Preview' : 'Output'}
              {runResult && (
                <span className={runResult.status === 'SUCCESS' ? styles.statusOk : styles.statusErr}>
                  {' '}{runResult.status} (exit {runResult.exitCode})
                </span>
              )}
            </span>
            <button onClick={() => { setShowOutput(false); setPreviewHtml(null) }} className={styles.closeOutput}>✕</button>
          </div>

          {/* HTML iframe preview */}
          {previewHtml && (
            <iframe
              className={styles.previewFrame}
              srcDoc={previewHtml}
              sandbox="allow-scripts"
              title="HTML Preview"
            />
          )}

          {/* Code execution output */}
          {!previewHtml && (
            <div className={styles.outputBody}>
              {running && <p className={styles.outputHint}>⏳ Executing in Docker...</p>}
              {!running && runResult === null && <p className={styles.outputHint}>No output yet</p>}
              {runResult && (
                <>
                  {runResult.output && <pre className={styles.stdout}>{runResult.output}</pre>}
                  {runResult.error  && <pre className={styles.stderr}>{runResult.error}</pre>}
                  {!runResult.output && !runResult.error && <pre className={styles.outputHint}>No output</pre>}
                </>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
