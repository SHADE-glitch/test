import { getToken, request } from './client'

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

export const listTopics = () => request<TopicVO[]>('/topics')

export const createInterview = (topic: string, level: string) =>
  request<InterviewSessionVO>('/interviews', {
    method: 'POST',
    body: JSON.stringify({ topic, level }),
  })

export const listInterviews = () => request<InterviewSessionVO[]>('/interviews')

export const getInterview = (id: number | string) => request<InterviewSessionVO>(`/interviews/${id}`)

export const answerInterview = (id: number | string, content: string) =>
  request<MessageVO>(`/interviews/${id}/answer`, {
    method: 'POST',
    body: JSON.stringify({ content }),
  })

export interface StreamCallbacks {
  onToken: (token: string) => void
  onDone: (messageId: number) => void
}

export async function streamAnswer(
  id: number | string,
  content: string,
  { onToken, onDone }: StreamCallbacks,
): Promise<void> {
  const res = await fetch(`/api/interviews/${id}/answer/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getToken() ?? ''}`,
    },
    body: JSON.stringify({ content }),
  })
  if (!res.ok || !res.body) {
    throw new Error(`流式请求失败: ${res.status}`)
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let finished = false

  while (!finished) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''

    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed.startsWith('data:')) continue
      const data = trimmed.slice(5).trim()
      if (!data) continue
      if (data.startsWith('{')) {
        try {
          onDone(JSON.parse(data).messageId as number)
          finished = true
          break
        } catch {
          /* ignore malformed */
        }
      }
      onToken(data)
    }
  }
  reader.cancel().catch(() => undefined)
}