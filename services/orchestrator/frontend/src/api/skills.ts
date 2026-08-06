import { apiClient } from './client'
import type { Skill } from '../types/skill'

export function getSkills(): Promise<Skill[]> {
  return apiClient.get<Skill[]>('/api/skills')
}

export function getSkill(skillId: string): Promise<Skill> {
  return apiClient.get<Skill>(`/api/skills/${encodeURIComponent(skillId)}`)
}

export function getSkillsForAgent(agentName: string): Promise<Skill[]> {
  return apiClient.get<Skill[]>(
    `/api/skills/agents/${encodeURIComponent(agentName)}`,
  )
}

export function enableSkill(skillId: string): Promise<Skill> {
  return apiClient.post<Skill>(`/api/skills/${encodeURIComponent(skillId)}/enable`)
}

export function disableSkill(skillId: string): Promise<Skill> {
  return apiClient.post<Skill>(`/api/skills/${encodeURIComponent(skillId)}/disable`)
}
