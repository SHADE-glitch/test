import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '../api/auth'
import { clearTokens, getToken, setTokens } from '../api/client'
import type { LoginRequest, RegisterRequest, User } from '../types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const user = ref<User | null>(null)

  const isAuthed = computed(() => !!token.value)

  function applyAuth(accessToken: string, refreshToken: string, u: User) {
    setTokens(accessToken, refreshToken)
    token.value = accessToken
    user.value = u
  }

  async function login(data: LoginRequest) {
    const res = await authApi.login(data)
    applyAuth(res.accessToken, res.refreshToken, res.user)
  }

  async function register(data: RegisterRequest) {
    const res = await authApi.register(data)
    applyAuth(res.accessToken, res.refreshToken, res.user)
  }

  async function fetchMe() {
    if (!token.value) return
    user.value = await authApi.me()
  }

  function logout() {
    clearTokens()
    token.value = null
    user.value = null
  }

  return { token, user, isAuthed, login, register, fetchMe, logout }
})