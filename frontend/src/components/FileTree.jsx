import { useState } from 'react'
import styles from './FileTree.module.css'

// Icon based on file extension
function getFileIcon(name, type) {
  if (type === 'FOLDER' || type === 'folder') return null // handled by expand state
  const ext = name?.split('.').pop()?.toLowerCase()
  const icons = {
    js: '🟨', jsx: '🟨', ts: '🔷', tsx: '🔷',
    py: '🐍', java: '☕', html: '🌐', css: '🎨',
    json: '📋', md: '📝', txt: '📄', sql: '🗄️',
  }
  return icons[ext] || '📄'
}

function FileNodeItem({ node, onSelect, onCreate, onDelete, onRename, canEdit, selectedId, depth = 0 }) {
  const [expanded, setExpanded] = useState(true)
  const [adding, setAdding] = useState(null)   // 'FILE' | 'FOLDER' | null
  const [newName, setNewName] = useState('')
  const [renaming, setRenaming] = useState(false)
  const [renameName, setRenameName] = useState(node.name)

  const isFolder = node.type === 'FOLDER' || node.type === 'folder'
  const isSelected = node.id === selectedId

  const handleAdd = (e) => {
    e.preventDefault()
    if (!newName.trim()) return
    onCreate(newName.trim(), adding, node.id)
    setNewName('')
    setAdding(null)
  }

  const handleRenameSubmit = (e) => {
    e.preventDefault()
    if (!renameName.trim() || renameName === node.name) { setRenaming(false); return }
    onRename(node.id, renameName.trim())
    setRenaming(false)
  }

  return (
    <div>
      <div
        className={`${styles.node} ${isSelected ? styles.selected : ''}`}
        style={{ paddingLeft: depth * 14 + 8 + 'px' }}
        onClick={() => {
          if (isFolder) setExpanded(!expanded)
          else onSelect(node)
        }}
      >
        <span className={styles.icon}>
          {isFolder ? (expanded ? '📂' : '📁') : getFileIcon(node.name, node.type)}
        </span>

        {renaming ? (
          <form onSubmit={handleRenameSubmit} onClick={(e) => e.stopPropagation()} className={styles.renameForm}>
            <input
              autoFocus
              value={renameName}
              onChange={(e) => setRenameName(e.target.value)}
              onBlur={handleRenameSubmit}
            />
          </form>
        ) : (
          <span className={styles.name}>{node.name}</span>
        )}

        {canEdit && !renaming && (
          <span className={styles.actions} onClick={(e) => e.stopPropagation()}>
            {isFolder && (
              <>
                <button title="New file" onClick={() => setAdding('FILE')}>+f</button>
                <button title="New folder" onClick={() => setAdding('FOLDER')}>+d</button>
              </>
            )}
            <button title="Rename" onClick={() => { setRenaming(true); setRenameName(node.name) }}>✏</button>
            <button title="Delete" onClick={() => onDelete(node.id)}>✕</button>
          </span>
        )}
      </div>

      {adding && (
        <form
          onSubmit={handleAdd}
          className={styles.addForm}
          style={{ paddingLeft: (depth + 1) * 14 + 8 + 'px' }}
        >
          <input
            autoFocus
            placeholder={adding === 'FILE' ? 'filename.js' : 'folder name'}
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
          />
          <button type="submit">✓</button>
          <button type="button" onClick={() => setAdding(null)}>✕</button>
        </form>
      )}

      {isFolder && expanded && node.children?.map((child) => (
        <FileNodeItem
          key={child.id}
          node={child}
          onSelect={onSelect}
          onCreate={onCreate}
          onDelete={onDelete}
          onRename={onRename}
          canEdit={canEdit}
          selectedId={selectedId}
          depth={depth + 1}
        />
      ))}
    </div>
  )
}

export default function FileTree({ tree, onSelect, onCreate, onDelete, onRename, canEdit, selectedId }) {
  const [adding, setAdding] = useState(null)
  const [newName, setNewName] = useState('')

  const handleRootAdd = (e) => {
    e.preventDefault()
    if (!newName.trim()) return
    onCreate(newName.trim(), adding, null)
    setNewName('')
    setAdding(null)
  }

  return (
    <div className={styles.tree}>
      <div className={styles.treeHeader}>
        <span>EXPLORER</span>
        {canEdit && (
          <span className={styles.rootActions}>
            <button title="New File" onClick={() => setAdding('FILE')}>📄+</button>
            <button title="New Folder" onClick={() => setAdding('FOLDER')}>📁+</button>
          </span>
        )}
      </div>

      {adding && (
        <form onSubmit={handleRootAdd} className={styles.addForm} style={{ paddingLeft: '8px' }}>
          <input
            autoFocus
            placeholder={adding === 'FILE' ? 'filename.js' : 'folder name'}
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
          />
          <button type="submit">✓</button>
          <button type="button" onClick={() => setAdding(null)}>✕</button>
        </form>
      )}

      {tree.length === 0 && <p className={styles.empty}>No files yet</p>}

      {tree.map((node) => (
        <FileNodeItem
          key={node.id}
          node={node}
          onSelect={onSelect}
          onCreate={onCreate}
          onDelete={onDelete}
          onRename={onRename}
          canEdit={canEdit}
          selectedId={selectedId}
          depth={0}
        />
      ))}
    </div>
  )
}
