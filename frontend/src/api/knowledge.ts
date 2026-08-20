import { request } from './client'

export interface KnowledgeNodeVO {
  id: string
  name: string
  category: 'topic' | 'point'
  mastery: number
  difficulty: number
}

export interface KnowledgeLinkVO {
  source: string
  target: string
}

export interface KnowledgeMapVO {
  nodes: KnowledgeNodeVO[]
  links: KnowledgeLinkVO[]
}

export const getKnowledgeMap = () => request<KnowledgeMapVO>('/knowledge/map', { method: 'GET' })