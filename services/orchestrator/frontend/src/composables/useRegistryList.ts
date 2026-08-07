import { onMounted, shallowRef, type Ref } from 'vue'

export interface UseRegistryListOptions<T> {
  fetch: () => Promise<T[]>
  idOf: (item: T) => string
  errorText: string
  /** Re-select the current item by id after reload (default: true). */
  reselectOnReload?: boolean
}

export interface RegistryList<T> {
  items: Ref<T[]>
  selected: Ref<T | null>
  loading: Ref<boolean>
  errorMessage: Ref<string | null>
  reload: () => Promise<void>
  select: (item: T | null) => void
}

/**
 * Shared registry-page state: loading/error/reload/select plus the default
 * "select first item on first load, keep selection by id on reload" behavior
 * used by the Skills, MCP Plugins, Agent Market and Projects pages.
 */
export function useRegistryList<T>(options: UseRegistryListOptions<T>): RegistryList<T> {
  const items = shallowRef<T[]>([])
  const selected = shallowRef<T | null>(null)
  const loading = shallowRef(true)
  const errorMessage = shallowRef<string | null>(null)
  const reselectOnReload = options.reselectOnReload ?? true

  async function reload(): Promise<void> {
    loading.value = true
    errorMessage.value = null
    try {
      items.value = await options.fetch()
      syncSelection()
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : options.errorText
    } finally {
      loading.value = false
    }
  }

  function syncSelection(): void {
    if (!selected.value && items.value.length > 0) {
      selected.value = items.value[0]
      return
    }
    if (selected.value && reselectOnReload) {
      const refreshed = items.value.find(
        (item) => options.idOf(item) === options.idOf(selected.value as T),
      )
      if (refreshed) {
        selected.value = refreshed
      }
    }
  }

  function select(item: T | null): void {
    selected.value = item
  }

  onMounted(reload)

  return { items, selected, loading, errorMessage, reload, select }
}
