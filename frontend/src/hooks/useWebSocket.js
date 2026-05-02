import { useEffect, useRef, useCallback } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

export function useWebSocket({ roomId, fileId, onCodeChange, onCursorMove, onFileOpen }) {
  const clientRef = useRef(null)
  const isRemoteRef = useRef(false)

  useEffect(() => {
    const token = localStorage.getItem('token')

    const client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      onConnect: () => {
        // CODE_CHANGE
        client.subscribe(`/topic/code/${roomId}`, (msg) => {
          const data = JSON.parse(msg.body)
          if (onCodeChange) {
            isRemoteRef.current = true
            onCodeChange(data)
          }
        })

        // CURSOR_MOVE
        client.subscribe(`/topic/cursor/${roomId}`, (msg) => {
          const data = JSON.parse(msg.body)
          if (onCursorMove) onCursorMove(data)
        })

        // FILE_OPEN
        client.subscribe(`/topic/file/${roomId}`, (msg) => {
          const data = JSON.parse(msg.body)
          if (onFileOpen) onFileOpen(data)
        })
      },
      onStompError: (frame) => console.error('STOMP error', frame),
    })

    client.activate()
    clientRef.current = client

    return () => client.deactivate()
  }, [roomId])

  const sendCodeChange = useCallback((content) => {
    if (isRemoteRef.current) { isRemoteRef.current = false; return }
    if (clientRef.current?.connected) {
      clientRef.current.publish({
        destination: '/app/code.change',
        body: JSON.stringify({ roomId: parseInt(roomId), fileId, content }),
      })
    }
  }, [roomId, fileId])

  const sendCursorMove = useCallback((line, column) => {
    if (clientRef.current?.connected) {
      clientRef.current.publish({
        destination: '/app/cursor.move',
        body: JSON.stringify({ roomId: parseInt(roomId), fileId, line, column }),
      })
    }
  }, [roomId, fileId])

  const sendFileOpen = useCallback((fileId, fileName) => {
    if (clientRef.current?.connected) {
      clientRef.current.publish({
        destination: '/app/file.open',
        body: JSON.stringify({ roomId: parseInt(roomId), fileId, fileName }),
      })
    }
  }, [roomId])

  return { sendCodeChange, sendCursorMove, sendFileOpen, isRemoteRef }
}
