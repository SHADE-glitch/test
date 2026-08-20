import { http } from './client'
import type { AuthResponse, LoginRequest, RegisterRequest, User } from '../types'

export const authApi = {
  register: (data: RegisterRequest) => http.post<AuthResponse>('/auth/register', data),
  login: (data: LoginRequest) => http.post<AuthResponse>('/auth/login', data),
  refresh: (refreshToken: string) =>
    http.post<AuthResponse>('/auth/refresh', null, { Authorization: `Bearer ${refreshToken}` }),
  me: () => http.get<User>('/auth/me'),
}