export interface TimelineEventDTO {
  eventId: string
  eventType: string
  sourceType: string
  sourceId: string
  status: string | null
  message: string | null
  timestamp: string | null
}

export interface UnifiedTimeline {
  scopeType: string
  scopeId: string
  events: TimelineEventDTO[]
}
