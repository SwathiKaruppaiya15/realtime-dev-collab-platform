import { useState, useEffect } from 'react'
import api from '../api/axios'
import styles from './InvitePanel.module.css'

export default function InvitePanel({ roomId, onClose }) {
  const [email, setEmail] = useState('')
  const [role, setRole] = useState('EDITOR')
  const [members, setMembers] = useState([])
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const loadMembers = async () => {
    try {
      const res = await api.get(`/rooms/${roomId}/members`)
      setMembers(res.data)
    } catch {
      // ignore
    }
  }

  useEffect(() => { loadMembers() }, [roomId])

  const handleInvite = async (e) => {
    e.preventDefault()
    setError('')
    setSuccess('')
    try {
      await api.post('/rooms/invite', { roomId, email, role })
      setSuccess(`${email} invited as ${role}`)
      setEmail('')
      loadMembers()
    } catch (err) {
      setError(err.response?.data?.error || 'Invite failed')
    }
  }

  const roleColor = { ADMIN: '#f38ba8', EDITOR: '#fab387', VIEWER: '#a6e3a1' }

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div className={styles.panel} onClick={(e) => e.stopPropagation()}>
        <div className={styles.header}>
          <h3>Manage Members</h3>
          <button onClick={onClose} className={styles.close}>✕</button>
        </div>

        <form onSubmit={handleInvite} className={styles.form}>
          <input
            type="email"
            placeholder="User email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
          <select value={role} onChange={(e) => setRole(e.target.value)}>
            <option value="EDITOR">EDITOR</option>
            <option value="VIEWER">VIEWER</option>
            <option value="ADMIN">ADMIN</option>
          </select>
          <button type="submit">Invite</button>
        </form>

        {error && <p className={styles.error}>{error}</p>}
        {success && <p className={styles.success}>{success}</p>}

        <div className={styles.memberList}>
          <p className={styles.listTitle}>Current Members ({members.length})</p>
          {members.map((m) => (
            <div key={m.userId} className={styles.member}>
              <div>
                <span className={styles.memberName}>{m.name}</span>
                <span className={styles.memberEmail}>{m.email}</span>
              </div>
              <span className={styles.memberRole} style={{ background: roleColor[m.role] }}>
                {m.role}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
