import { useEffect, useState, useCallback, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import api from '../api/axios'
import FileTree from '../components/FileTree'
import CodeEditor from '../components/CodeEditor'
import Terminal from '../components/Terminal'
import AiPanel from '../components/AiPanel'
import InvitePanel from '../components/InvitePanel'
import MembersPanel from '../components/MembersPanel'
import ChatPanel from '../components/ChatPanel'
import styles from './Room.module.css'

export default function RoomPage() {
  const { roomId } = useParams()
  const navigate = useNavigate()

  const [room, setRoom] = useState(null)
  const [myRole, setMyRole] = useState('')
  const [fileTree, setFileTree] = useState([])
  const [selectedFile, setSelectedFile] = useState(null)
  const [error, setError] = useState('')
  const [activePanel, setActivePanel] = useState('editor')
  const [showInvite, setShowInvite] = useState(false)
  const [showMembers, setShowMembers] = useState(false)
  const [codeCopied, setCodeCopied] = useState(false)
  // who is viewing which file (from FILE_OPEN ws events)
  const [activeViewers, setActiveViewers] = useState({}) // userId → { userName, fileName }

  const canEdit = myRole === 'ADMIN' || myRole === 'EDITOR'
  const isAdmin = myRole === 'ADMIN'

  // Shared Monaco editor ref — passed to CodeEditor and AiPanel
  // so AiPanel can call editorRef.current.setValue(code) directly
  const editorRef = useRef(null)

  const loadRoom = async () => {
    try {
      const res = await api.get('/rooms/my')
      const found = res.data.find((r) => r.id === parseInt(roomId))
      if (!found) { navigate('/dashboard'); return }
      setRoom(found)
      setMyRole(found.myRole)
    } catch {
      navigate('/dashboard')
    }
  }

  const loadFiles = async () => {
    try {
      const res = await api.get(`/files/${roomId}`)
      setFileTree(res.data)
    } catch {
      setError('Failed to load files')
    }
  }

  useEffect(() => {
    loadRoom()
    loadFiles()
  }, [roomId])

  const handleSelectFile = (file) => {
    if (file.type === 'FILE' || file.type === 'file') {
      setSelectedFile(file)
      setActivePanel('editor')
    }
  }

  const handleSave = async (content) => {
    if (!selectedFile) return
    try {
      await api.put(`/files/update/${selectedFile.id}`, { content })
      setSelectedFile({ ...selectedFile, content })
      loadFiles()
    } catch (err) {
      setError(err.response?.data?.error || 'Save failed')
    }
  }

  const handleCreateFile = async (name, type, parentId) => {
    try {
      await api.post('/files/create', {
        name,
        type: type.toUpperCase(),
        parentId: parentId || null,
        roomId: parseInt(roomId)
      })
      loadFiles()
    } catch (err) {
      setError(err.response?.data?.error || 'Create failed')
    }
  }

  const handleDelete = async (id) => {
    if (!window.confirm('Delete this file/folder?')) return
    try {
      await api.delete(`/files/delete/${id}`)
      if (selectedFile?.id === id) setSelectedFile(null)
      loadFiles()
    } catch (err) {
      setError(err.response?.data?.error || 'Delete failed')
    }
  }

  const handleRename = async (id, newName) => {
    try {
      await api.put(`/files/update/${id}`, { name: newName })
      if (selectedFile?.id === id) {
        setSelectedFile({ ...selectedFile, name: newName })
      }
      loadFiles()
    } catch (err) {
      setError(err.response?.data?.error || 'Rename failed')
    }
  }

  const handleInsertAiCode = async (code, mode = 'replace') => {
    // editorRef.current.setValue() is already called by AiPanel directly
    // Here we just sync state and auto-save to backend
    if (selectedFile) {
      const newContent = mode === 'replace'
        ? code
        : (selectedFile.content || '') + '\n\n' + code

      const updated = { ...selectedFile, content: newContent }
      setSelectedFile(updated)

      // Auto-save to backend: PUT /api/files/update
      try {
        await api.put(`/files/update/${selectedFile.id}`, { content: newContent })
      } catch {
        // non-critical — user can still manually save
      }
    }
    setActivePanel('editor')
  }

  const handleLeave = async () => {
    if (!window.confirm('Leave this room? If you are the last ADMIN, the room will be deleted.')) return
    try {
      await api.delete(`/rooms/${roomId}/leave`)
      navigate('/dashboard')
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to leave room')
    }
  }

  const handleExport = async () => {
    try {
      const res = await api.get(`/export/${roomId}`, { responseType: 'blob' })
      const url = window.URL.createObjectURL(new Blob([res.data]))
      const a = document.createElement('a')
      a.href = url
      a.download = `room-${roomId}.zip`
      a.click()
      window.URL.revokeObjectURL(url)
    } catch {
      setError('Export failed')
    }
  }

  const copyRoomCode = () => {
    if (room?.code) {
      navigator.clipboard.writeText(room.code)
      setCodeCopied(true)
      setTimeout(() => setCodeCopied(false), 2000)
    }
  }

  const handleFileOpen = useCallback((data) => {
    setActiveViewers(prev => ({
      ...prev,
      [data.userId]: { userName: data.userName, fileName: data.fileName }
    }))
  }, [])

  const roleColor = { ADMIN: styles.admin, EDITOR: styles.editor, VIEWER: styles.viewer }

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <button onClick={() => navigate('/dashboard')} className={styles.back}>← Back</button>

        <span className={styles.roomName}>{room?.name}</span>

        {/* Room Code Badge */}
        <button className={styles.roomCode} onClick={copyRoomCode} title="Click to copy">
          {codeCopied ? '✓ Copied!' : `🔑 ${room?.code}`}
        </button>

        <div className={styles.headerRight}>
          <div className={styles.tabs}>
            <button className={activePanel === 'editor' ? styles.activeTab : styles.tab} onClick={() => setActivePanel('editor')}>Editor</button>
            <button className={activePanel === 'terminal' ? styles.activeTab : styles.tab} onClick={() => setActivePanel('terminal')}>Terminal</button>
            <button className={activePanel === 'ai' ? styles.activeTab : styles.tab} onClick={() => setActivePanel('ai')}>AI</button>
            <button className={activePanel === 'chat' ? styles.activeTab : styles.tab} onClick={() => setActivePanel('chat')}>Chat</button>
          </div>

          <button className={styles.membersBtn} onClick={() => setShowMembers(!showMembers)}>
            👥 Members
          </button>

          {isAdmin && (
            <button className={styles.inviteBtn} onClick={() => setShowInvite(!showInvite)}>
              ✉ Invite
            </button>
          )}

          <button className={styles.leaveBtn} onClick={handleLeave}>
            ← Leave
          </button>

          <button className={styles.exportBtn} onClick={handleExport}>⬇ ZIP</button>

          <span className={`${styles.roleBadge} ${roleColor[myRole] || ''}`}>{myRole}</span>
        </div>
      </header>

      {error && <div className={styles.error} onClick={() => setError('')}>{error} ✕</div>}

      {/* Invite Panel overlay */}
      {showInvite && (
        <InvitePanel
          roomId={parseInt(roomId)}
          onClose={() => setShowInvite(false)}
        />
      )}

      {/* Members Panel overlay */}
      {showMembers && (
        <MembersPanel
          roomId={parseInt(roomId)}
          myRole={myRole}
          onClose={() => setShowMembers(false)}
        />
      )}

      <div className={styles.workspace}>
        <aside className={styles.sidebar}>
          <FileTree
            tree={fileTree}
            onSelect={handleSelectFile}
            onCreate={handleCreateFile}
            onDelete={handleDelete}
            onRename={handleRename}
            canEdit={canEdit}
            selectedId={selectedFile?.id}
          />

          {/* Active viewers */}
          {Object.keys(activeViewers).length > 0 && (
            <div className={styles.viewers}>
              <p className={styles.viewersTitle}>Active</p>
              {Object.entries(activeViewers).map(([uid, v]) => (
                <div key={uid} className={styles.viewerItem}>
                  <span>👤 {v.userName}</span>
                  <span className={styles.viewerFile}>{v.fileName}</span>
                </div>
              ))}
            </div>
          )}
        </aside>

        <main className={styles.main}>
          {activePanel === 'editor' && (
            selectedFile ? (
              <CodeEditor
                key={selectedFile.id}
                file={selectedFile}
                roomId={roomId}
                onSave={handleSave}
                readOnly={!canEdit}
                onFileOpen={handleFileOpen}
                editorRef={editorRef}
              />
            ) : (
              <div className={styles.placeholder}>
                <div className={styles.placeholderIcon}>📁</div>
                <p>Select a file from the explorer to start editing</p>
                {!canEdit && <p className={styles.viewerNote}>You are a VIEWER — read only access</p>}
              </div>
            )
          )}

          {activePanel === 'terminal' && (
            <Terminal
              defaultCode={selectedFile?.content || ''}
              defaultLanguage={selectedFile?.name?.split('.').pop() || 'python'}
              selectedFile={selectedFile}
              roomId={roomId}
            />
          )}

          {activePanel === 'ai' && (
            <AiPanel
              selectedFile={selectedFile}
              editorRef={editorRef}
              onInsert={handleInsertAiCode}
              canEdit={canEdit}
            />
          )}

          {activePanel === 'chat' && (
            <ChatPanel roomId={roomId} />
          )}
        </main>
      </div>
    </div>
  )
}
