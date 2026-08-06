export type SkillType = 'CODING' | 'TESTING' | 'BROWSER' | 'ANALYSIS' | 'DEPLOYMENT'

export interface Skill {
  skillId: string
  name: string
  description: string | null
  type: SkillType
  version: string | null
  enabled: boolean
  tools: string[]
  instructions: string | null
}
