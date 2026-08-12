import { ref } from 'vue'
import { getTimeline } from '../api/timeline'
import type { UnifiedTimeline } from '../types/timeline'

export function useTimeline() {
  const timeline = ref<UnifiedTimeline | null>(null)
  const loading = ref(false)
  const errorMessage = ref<string | null>(null)

  async function load(id: string): Promise<void> {
    loading.value = true
    errorMessage.value = null
    try {
      timeline.value = await getTimeline(id)
    } catch (error) {
      timeline.value = null
      errorMessage.value = error instanceof Error ? error.message : 'Unable to load timeline.'
    } finally {
      loading.value = false
    }
  }

  return { timeline, loading, errorMessage, load }
}
