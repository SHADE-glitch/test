import { request } from './client'

export interface DashboardStats {
  totalInterviews: number
  avgScore: number
  maxScore: number
  totalQuestions: number
}

export interface RadarDim {
  name: string
  value: number
}

export interface TrendPoint {
  date: string
  score: number
}

export interface DashboardVO {
  stats: DashboardStats
  dimensions: RadarDim[]
  trend: TrendPoint[]
  weakPoints: string[]
  suggestions: string[]
}

export const getDashboard = () => request<DashboardVO>('/dashboard', { method: 'GET' })