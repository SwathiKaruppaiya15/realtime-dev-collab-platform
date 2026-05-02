import { useState, useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import api from '../api/axios'
import styles from './ChatPanel.module.css'

export default function ChatPanel({ roomId }) {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [connected, setConnected] = useState(false)
  const clientRef = useRef(null)
  const messagesEndRef = useRef(null)

  useEffect(() => {
    // Load history
    api.get(`/chat/${roomId}`).then(res => setMessages(res.data || [])).catch(() => {})

    // Connect WebSocket
    const token = localStorage.getItem('token')
    const client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true)
        client.subscribe(`/topic/room/${roomId}`, (msg) => {
          const data = JSON.parse(msg.body)
          setMessages(prev => [...prev, data])
        })
      },
      onDisconnect: () => setConnected(false),
      onStompError: (frame) => console.error('Chat STOMP error', frame),
    })

    client.activate()
    clientRef.current = client

    return () => client.deactivate()
  }, [roomId])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const send = (e) => {
    e.preventDefault()
    if (!input.trim() || !clientRef.current?.connected) return

    clientRef.current.publish({
      destination: '/app/chat.send',
      body: JSON.stringify({ roomId: parseInt(roomId), content: input.trim() }),
    })

    setInput('')
  }

  const me = JSON.parse(localStorage.getItem('user') || '{}')

  return (
    <div className={styles.chat}>
      <div className={styles.header}>
        <span>💬 Chat</span>
        <span className={connected ? styles.connectedDot : styles.disconnectedDot}>
          {connected ? '●' : '○'}
        </span>
      </div>

      <div className={styles.messages}>
        {messages.length === 0 && <p className={styles.empty}>No messages yet</p>}
        {messages.map((msg, i) => {
          const isMe = msg.senderName === me.name || msg.senderEmail === me.email
          return (
            <div key={i} className={isMe ? styles.msgMe : styles.msgOther}>
              {!isMe && <span className={styles.sender}>{msg.senderName}</span>}
              <div className={styles.bubble}>{msg.content}</div>
              <span className={styles.time}>{new Date(msg.createdAt).toLocaleTimeString()}</span>
            </div>
          )
        })}
        <div ref={messagesEndRef} />
      </div>

      <form onSubmit={send} className={styles.inputForm}>
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Type a message..."
          disabled={!connected}
        />
        <button type="submit" disabled={!connected || !input.trim()}>Send</button>
      </form>
    </div>
  )
}
