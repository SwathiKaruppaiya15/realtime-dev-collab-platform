import { useState, useEffect } from 'react'
import api from '../api/axios'
import styles from './NotificationBell.module.css'

export default function NotificationBell() {
  const [count, setCount] = useState(0)
  const [notifications, setNotifications] = useState([])
  const [showPanel, setShowPanel] = useState(false)
  const [loading, setLoading] = useState(false)

  const loadCount = async () => {
    try {
      const res = await api.get('/notifications/count')
      setCount(res.data.count || 0)
    } catch {
      // ignore
    }
  }

  const loadNotifications = async () => {
    setLoading(true)
    try {
      const res = await api.get('/notifications')
      setNotifications(res.data || [])
    } catch {
      // ignore
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadCount()
    const interval = setInterval(loadCount, 10000) // poll every 10s
    return () => clearInterval(interval)
  }, [])

  const handleOpen = () => {
    setShowPanel(!showPanel)
    if (!showPanel) loadNotifications()
  }

  const respond = async (notificationId, action) => {
    try {
      await api.post('/invite/respond', { notificationId, action })
      loadNotifications()
      loadCount()
    } catch (err) {
      alert(err.response?.data?.error || 'Failed to respond')
    }
  }

  const pending = notifications.filter(n => n.status === 'PENDING')

  return (
    <div className={styles.container}>
      <button className={styles.bell} onClick={handleOpen}>
        🔔
        {count > 0 && <span className={styles.badge}>{count}</span>}
      </button>

      {showPanel && (
        <>
          <div className={styles.overlay} onClick={() => setShowPanel(false)} />
          <div className={styles.panel}>
            <div className={styles.header}>
              <span>Notifications</span>
              <button onClick={() => setShowPanel(false)} className={styles.close}>✕</button>
            </div>

            {loading && <p className={styles.loading}>Loading...</p>}

            {!loading && pending.length === 0 && (
              <p className={styles.empty}>No pending invites</p>
            )}

            {pending.map(n => (
              <div key={n.id} className={styles.item}>
                <div className={styles.itemContent}>
                  <strong>{n.senderName}</strong> invited you to <strong>{n.roomName}</strong>
                  <span className={styles.time}>{new Date(n.createdAt).toLocaleString()}</span>
                </div>
                <div className={styles.actions}>
                  <button onClick={() => respond(n.id, 'ACCEPT')} className={styles.accept}>Accept</button>
                  <button onClick={() => respond(n.id, 'REJECT')} className={styles.reject}>Reject</button>
                </div>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
