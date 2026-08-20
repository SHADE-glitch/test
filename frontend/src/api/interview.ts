import { http } from './client'

export interface MessageVO {
  id: number
  role: string
  kind: string
  content: string
  knowledgePointId?: number | null
  createdAt: string
}

export interface InterviewSessionVO {
  id: number
  topic: string
  level: string
  status: string
  questionCount: number
  totalScore?: number | null
  startedAt: string
  finishedAt?: string | null
  currentQuestion?: MessageVO | null
  messages: MessageVO[]
}

export interface TopicVO {
  topic: string
  points: { id: number; name: string; difficulty: number }[]
}

export const listTopics = () => http.get<TopicVO[]>('/topics')

export const createInterview = (topic: string, level: string) =>
  http.post<InterviewSessionVO>('/interviews', { topic, level })

export const listInterviews = () => http.get<InterviewSessionVO[]>('/interviews')

export const getInterview = (id: number | string) => http.get<InterviewSessionVO>(`/interviews/${id}`)

export const answerInterview = (id: number | string, content: string) =>
  http.post<MessageVO>(`/interviews/${id}/answer`, { content })