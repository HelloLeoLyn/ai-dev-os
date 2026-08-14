export type QueryValue = string | number | boolean | null | undefined
export type QueryParameters = Record<string, QueryValue | QueryValue[]>

export interface ApiRequestOptions extends Omit<RequestInit, 'body'> {
  query?: QueryParameters
  body?: unknown
}

export class ApiError extends Error {
  readonly status: number
  readonly details: unknown

  constructor(status: number, message: string, details?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
  }
}

function requestUrl(path: string, query?: QueryParameters): string {
  const url = new URL(path, window.location.origin)

  for (const [key, rawValue] of Object.entries(query ?? {})) {
    const values = Array.isArray(rawValue) ? rawValue : [rawValue]
    for (const value of values) {
      if (value !== null && value !== undefined) {
        url.searchParams.append(key, String(value))
      }
    }
  }

  return `${url.pathname}${url.search}`
}

async function responseBody(response: Response): Promise<unknown> {
  if (response.status === 204) {
    return undefined
  }

  const contentType = response.headers.get('content-type') ?? ''
  if (contentType.includes('application/json')) {
    return response.json()
  }

  const text = await response.text()
  return text || undefined
}

export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const { query, body, headers, ...requestOptions } = options
  const requestHeaders = new Headers(headers)

  if (body !== undefined && !requestHeaders.has('Content-Type')) {
    requestHeaders.set('Content-Type', 'application/json')
  }

  const response = await fetch(requestUrl(path, query), {
    ...requestOptions,
    headers: requestHeaders,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const payload = await responseBody(response)

  if (!response.ok) {
    const message =
      typeof payload === 'string' && payload.length > 0
        ? payload
        : `Request failed with status ${response.status}`
    throw new ApiError(response.status, message, payload)
  }

  return payload as T
}

export const apiClient = {
  get<T>(path: string, query?: QueryParameters, options?: RequestInit) {
    return apiRequest<T>(path, { ...options, method: 'GET', query })
  },
  post<T>(path: string, body?: unknown, options?: RequestInit) {
    return apiRequest<T>(path, { ...options, method: 'POST', body })
  },
  put<T>(path: string, body?: unknown, options?: RequestInit) {
    return apiRequest<T>(path, { ...options, method: 'PUT', body })
  },
  delete<T>(path: string, options?: RequestInit) {
    return apiRequest<T>(path, { ...options, method: 'DELETE' })
  },
}
