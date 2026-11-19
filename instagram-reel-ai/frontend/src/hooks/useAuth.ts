import { useState, useCallback } from 'react'
import { apiClient } from '../api/client'

export function useAuth() {
  const [isAuthenticated, setIsAuthenticated] = useState(() => !!apiClient.getToken())
  const [username, setUsername] = useState(() => localStorage.getItem('username') || '')
  const [role, setRole] = useState(() => localStorage.getItem('role') || '')

  const login = useCallback(async (user: string, password: string) => {
    const response = await apiClient.login(user, password)
    apiClient.setToken(response.token)
    localStorage.setItem('username', response.username)
    localStorage.setItem('role', response.role)
    setIsAuthenticated(true)
    setUsername(response.username)
    setRole(response.role)
  }, [])

  const register = useCallback(async (user: string, password: string, email?: string, displayName?: string) => {
    const response = await apiClient.register(user, password, email, displayName)
    apiClient.setToken(response.token)
    localStorage.setItem('username', response.username)
    localStorage.setItem('role', response.role)
    setIsAuthenticated(true)
    setUsername(response.username)
    setRole(response.role)
  }, [])

  const demoLogin = useCallback(async () => {
    const response = await apiClient.demoLogin()
    apiClient.setToken(response.token)
    localStorage.setItem('username', response.username)
    localStorage.setItem('role', response.role)
    setIsAuthenticated(true)
    setUsername(response.username)
    setRole(response.role)
  }, [])

  const logout = useCallback(() => {
    apiClient.setToken(null)
    localStorage.removeItem('username')
    localStorage.removeItem('role')
    setIsAuthenticated(false)
    setUsername('')
    setRole('')
  }, [])

  return { isAuthenticated, username, role, login, register, demoLogin, logout }
}
