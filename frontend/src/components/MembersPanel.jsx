import { useState, useEffect } from 'react'
import api from '../api/axios'
import styles from './MembersPanel.module.css'

const ROLE_COLORS = { ADMIN: '#f38ba8', EDITOR: '#fab387', VIEWER: '#a6e3a1' }

export default function MembersPanel({ roomId, myRole, onClose }) {
  const [members, setMembers] = useState([])
  const [error, setError] = useState('')
  const isAdmin = myRole === 'ADMIN'
  const me = JSON.parse(localStorage.getItem('user') || '{}')

  const load = async () => {
    try {
      const res = await api.get(`/rooms/${roomId}/members`)
      setMembers(res.data || [])
    } catch {
      setError('Failed to load members')
    }
  }

  useEffect(() => { load() }, [roomId])

  const changeRole = async (userId, role) => {
    try {
      await api.put(`/rooms/${roomId}/role`, { userId, role })
      load()
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to change role')
    }
  }

  const removeMember = async (userId, name) => {
    if (!window.confirm(`Remove ${name} from this room?`)) return
    try {
      await api.delete(`/rooms/${roomId}/member/${userId}`)
      load()
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to remove member')
    }
  }

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div className={styles.panel} onClick={(e) => e.stopPropagation()}>
        <div className={styles.header}>
          <h3>👥 Members ({members.length})</h3>
          <button onClick={onClose} className={styles.close}>✕</button>
        </div>

        {error && <p className={styles.error}>{error}</p>}

        <div className={styles.list}>
          {members.map((m) => {
            const isMe = m.email === me.email
            return (
              <div key={m.userId} className={styles.member}>
                <div className={styles.info}>
                  <span className={styles.name}>{m.name} {isMe && <span className={styles.youTag}>(you)</span>}</span>
                  <span className={styles.email}>{m.email}</span>
                </div>

                <div className={styles.controls}>
                  {/* Role badge or dropdown for ADMIN */}
                  {isAdmin && !isMe ? (
                    <select
                      value={m.role}
                      onChange={(e) => changeRole(m.userId, e.target.value)}
                      className={styles.roleSelect}
                      style={{ borderColor: ROLE_COLORS[m.role] }}
                    >
                      <option value="ADMIN">ADMIN</option>
                      <option value="EDITOR">EDITOR</option>
                      <option value="VIEWER">VIEWER</option>
                    </select>
                  ) : (
                    <span className={styles.roleBadge} style={{ background: ROLE_COLORS[m.role] }}>
                      {m.role}
                    </span>
                  )}

                  {/* Remove button — ADMIN only, not for self */}
                  {isAdmin && !isMe && (
                    <button
                      onClick={() => removeMember(m.userId, m.name)}
                      className={styles.removeBtn}
                      title="Remove from room"
                    >
                      ✕
                    </button>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
