const TOKEN_KEY = 'aiview_access_token'
const REFRESH_KEY = 'aiview_refresh_token'

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export class ApiError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY)
}

export function setTokens(access: string, refresh: string) {
  localStorage.setItem(TOKEN_KEY, access)
  localStorage.setItem(REFRESH_KEY, refresh)
}

export function clearTokens() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
}

let refreshPromise: Promise<boolean> | null = null

async function tryRefresh(): Promise<boolean> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return false
  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const res = await fetch('/api/auth/refresh', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${refreshToken}`,
          },
        })
        const body = (await res.json()) as ApiResult<{ accessToken: string; refreshToken: string }>
        if (body.code === 0) {
          setTokens(body.data.accessToken, body.data.refreshToken)
          return true
        }
        return false
      } catch {
        return false
      } finally {
        setTimeout(() => (refreshPromise = null), 0)
      }
    })()
  }
  return refreshPromise
}

export async function request<T>(
  path: string,
  options: RequestInit = {},
  retryOn401 = true,
): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(`/api${path}`, { ...options, headers })
  const body = (await res.json()) as ApiResult<T>
  if (body.code === 401) {
    if (retryOn401 && (await tryRefresh())) {
      return request<T>(path, options, false)
    }
    clearTokens()
    if (window.location.pathname !== '/login') window.location.href = '/login'
    throw new ApiError(401, body.message)
  }
  if (body.code !== 0) {
    throw new ApiError(body.code, body.message)
  }
  return body.data
}

export const http = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, data?: unknown, extraHeaders?: Record<string, string>) =>
    request<T>(path, {
      method: 'POST',
      body: JSON.stringify(data ?? {}),
      headers: extraHeaders,
    }),
  put: <T>(path: string, data?: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(data ?? {}) }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}