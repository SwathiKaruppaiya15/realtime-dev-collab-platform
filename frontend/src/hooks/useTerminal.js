import { useEffect, useRef, useCallback, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

/**
 * WebSocket-based terminal hook.
 * Connects to /ws, sends commands to /app/terminal.command,
 * receives streaming output from /topic/terminal/{sessionId}
 */
export function useTerminal(roomId) {
  const clientRef = useRef(null)
  const [connected, setConnected] = useState(false)
  const [sessionId, setSessionId] = useState(null)
  const outputCallbackRef = useRef(null)

  useEffect(() => {
    if (!roomId) return

    const token = localStorage.getItem('token')
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    const sid = `${roomId}:${user.email}`
    setSessionId(sid)

    const client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true)

        // Subscribe to this session's output stream
        client.subscribe(`/topic/terminal/${sid}`, (msg) => {
          if (outputCallbackRef.current) {
            outputCallbackRef.current(msg.body)
          }
        })

        // Send connect event to initialize session on backend
        client.publish({
          destination: '/app/terminal.connect',
          body: JSON.stringify({ roomId: String(roomId) }),
        })
      },
      onDisconnect: () => setConnected(false),
      onStompError: (frame) => console.error('Terminal STOMP error', frame),
    })

    client.activate()
    clientRef.current = client

    return () => {
      client.deactivate()
      setConnected(false)
    }
  }, [roomId])

  const sendCommand = useCallback((command) => {
    if (clientRef.current?.connected) {
      clientRef.current.publish({
        destination: '/app/terminal.command',
        body: JSON.stringify({ roomId: String(roomId), command }),
      })
    }
  }, [roomId])

  const onOutput = useCallback((callback) => {
    outputCallbackRef.current = callback
  }, [])

  return { sendCommand, onOutput, connected, sessionId }
}
