import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api/axios'
import NotificationBell from '../components/NotificationBell'
import styles from './Dashboard.module.css'

export default function DashboardPage() {
  const navigate = useNavigate()
  const user = JSON.parse(localStorage.getItem('user') || '{}')

  const [rooms, setRooms]       = useState([])
  const [stats, setStats]       = useState(null)
  const [roomName, setRoomName] = useState('')
  const [joinCode, setJoinCode] = useState('')
  const [error, setError]       = useState('')
  const [loading, setLoading]   = useState(true)
  const [deletingId, setDeletingId] = useState(null) // track which room is being deleted

  const loadDashboard = async () => {
    setLoading(true)
    setError('')
    try {
      const res = await api.get('/dashboard')
      setRooms(res.data.rooms  || [])
      setStats(res.data.stats  || null)
    } catch (err) {
      const msg = err.response?.data?.error || err.message || 'Failed to load dashboard'
      setError(msg)
      console.error('[Dashboard] load error:', err.response || err)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { loadDashboard() }, [])

  const createRoom = async (e) => {
    e.preventDefault()
    setError('')
    try {
      await api.post('/rooms/create', { name: roomName })
      setRoomName('')
      loadDashboard()
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to create room')
    }
  }

  const joinRoom = async (e) => {
    e.preventDefault()
    setError('')
    try {
      await api.post('/rooms/join', { code: joinCode.trim().toUpperCase() })
      setJoinCode('')
      loadDashboard()
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to join room')
    }
  }

  const deleteRoom = async (e, roomId) => {
    // Stop click from navigating into the room
    e.stopPropagation()

    if (!window.confirm('Delete this room? This will remove all files and members permanently.')) return

    setDeletingId(roomId)
    setError('')
    try {
      await api.delete(`/rooms/${roomId}`)
      loadDashboard()
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to delete room')
    } finally {
      setDeletingId(null)
    }
  }

  const logout = () => {
    localStorage.clear()
    navigate('/login')
  }

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1>CodeRoom</h1>
        <div className={styles.userInfo}>
          <span>{user.name || user.email}</span>
          <NotificationBell />
          <button onClick={logout}>Logout</button>
        </div>
      </header>

      <div className={styles.content}>

        {/* Error banner */}
        {error && (
          <div className={styles.errorBanner}>
            <span>⚠ {error}</span>
            <div className={styles.errorActions}>
              <button onClick={loadDashboard} className={styles.retryBtn}>Retry</button>
              <button onClick={() => setError('')} className={styles.dismissBtn}>✕</button>
            </div>
          </div>
        )}

        {loading && <p className={styles.loading}>Loading...</p>}

        {/* Stats */}
        {stats && (
          <div className={styles.stats}>
            <div className={styles.statCard}>
              <span>{stats.totalRooms}</span>
              <label>Total Rooms</label>
            </div>
            <div className={styles.statCard}>
              <span>{stats.ownedRooms}</span>
              <label>Owned</label>
            </div>
            <div className={styles.statCard}>
              <span>{stats.joinedRooms}</span>
              <label>Joined</label>
            </div>
          </div>
        )}

        {/* Create / Join */}
        <div className={styles.actions}>
          <form onSubmit={createRoom} className={styles.form}>
            <h3>Create Room</h3>
            <input
              placeholder="Room name"
              value={roomName}
              onChange={(e) => setRoomName(e.target.value)}
              required
            />
            <button type="submit">Create</button>
          </form>

          <form onSubmit={joinRoom} className={styles.form}>
            <h3>Join Room</h3>
            <input
              placeholder="Room code (e.g. A7X2P9)"
              value={joinCode}
              onChange={(e) => setJoinCode(e.target.value)}
              required
            />
            <button type="submit">Join</button>
          </form>
        </div>

        {/* Room list */}
        <div className={styles.rooms}>
          <h3>My Rooms</h3>
          {!loading && rooms.length === 0 && (
            <p className={styles.empty}>No rooms yet. Create or join one above.</p>
          )}
          {rooms.map((room) => (
            <div
              key={room.id}
              className={styles.roomCard}
              onClick={() => navigate(`/room/${room.id}`)}
            >
              {/* Room info */}
              <div className={styles.roomInfo}>
                <strong>{room.name}</strong>
                <span className={styles.code} title="Room code">🔑 {room.code}</span>
              </div>

              {/* Right side: role badge + delete button */}
              <div className={styles.roomActions}>
                <span className={`${styles.role} ${styles[room.myRole?.toLowerCase()]}`}>
                  {room.myRole}
                </span>

                {/* Only ADMIN sees delete button */}
                {room.myRole === 'ADMIN' && (
                  <button
                    className={styles.deleteRoomBtn}
                    onClick={(e) => deleteRoom(e, room.id)}
                    disabled={deletingId === room.id}
                    title="Delete room"
                  >
                    {deletingId === room.id ? '...' : '🗑'}
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>

      </div>
    </div>
  )
}
