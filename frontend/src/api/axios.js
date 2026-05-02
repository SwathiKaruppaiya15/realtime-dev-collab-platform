import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
})

// ── Request interceptor: attach JWT token to every request ──────────────────
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ── Response interceptor ─────────────────────────────────────────────────────
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const url    = error.config?.url || ''

    // ONLY logout when a 401 comes from an AUTH endpoint (login/register/token refresh).
    // AI errors, OpenAI key errors, and other 401s must NOT log the user out.
    const isAuthEndpoint = url.includes('/auth/') || url.includes('/auth')

    if (status === 401 && isAuthEndpoint) {
      console.warn('[axios] 401 on auth endpoint — clearing session')
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/login'
    }

    // For all other 401s (AI key errors, etc.) — just reject the promise
    // so the calling component can handle it and show an error message
    return Promise.reject(error)
  }
)

export default api
