import type { AnalysisLevel, Finding, Recommendation } from '../types/analysis'

export const analysisLevels: AnalysisLevel[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
const rank = Object.fromEntries(analysisLevels.map((level, index) => [level, index])) as Record<AnalysisLevel, number>

export function levelSummary(items: Array<Finding | Recommendation>, field: 'severity' | 'priority'): Record<AnalysisLevel, number> {
  return Object.fromEntries(analysisLevels.map(level => [level, items.filter(item => field === 'severity'
    ? 'severity' in item && item.severity === level
    : 'priority' in item && item.priority === level).length])) as Record<AnalysisLevel, number>
}

export const sortFindings = (items: Finding[]) => [...items].sort((a, b) => rank[a.severity] - rank[b.severity])
export const sortRecommendations = <T extends Recommendation>(items: T[]) => [...items].sort((a, b) => rank[a.priority] - rank[b.priority])
