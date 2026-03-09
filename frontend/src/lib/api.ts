import type {
  AnalyticsResponse,
  CreateUrlRequest,
  CreateUrlResponse,
  DeviceDistributionResponse,
  TopUrlsResponse,
  UrlPageResponse,
} from '@/types/api'

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'

export class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

function normalizeShortUrl(url: string): string {
  return url.replace(/^https:\/\/(localhost[:/])/i, 'http://$1')
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let message = `Request failed with status ${res.status}`
    try {
      const body = await res.json()
      if (body.errors && Array.isArray(body.errors)) {
        message = body.errors.join(', ')
      } else if (body.message) {
        message = body.message
      }
    } catch {
      try {
        const text = await res.text()
        if (text) message = text
      } catch { /* ignore */ }
    }
    throw new ApiError(res.status, message)
  }
  return res.json() as Promise<T>
}

/**
 * Creates a new short URL via the backend API.
 */
export async function createShortUrl(
  payload: CreateUrlRequest,
): Promise<CreateUrlResponse> {
  const res = await fetch(`${API_BASE}/v1/url/create`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  const data = await handleResponse<CreateUrlResponse>(res)
  data.shortUrl = normalizeShortUrl(data.shortUrl)
  return data
}

/**
 * Returns a paginated page of short URLs.
 */
export async function listUrls(page: number): Promise<UrlPageResponse> {
  const res = await fetch(`${API_BASE}/v1/url?page=${page}`)
  const data = await handleResponse<UrlPageResponse>(res)
  data.data.forEach(item => { item.shortUrl = normalizeShortUrl(item.shortUrl) })
  return data
}

/**
 * Returns click analytics for a 7-day window (page 0 = last 7 days).
 */
export async function getAnalytics(page: number): Promise<AnalyticsResponse> {
  const res = await fetch(`${API_BASE}/v1/analytics?page=${page}`)
  return handleResponse<AnalyticsResponse>(res)
}

/**
 * Returns device-type click distribution for the last 30 days.
 */
export async function getDeviceDistribution(): Promise<DeviceDistributionResponse> {
  const res = await fetch(`${API_BASE}/v1/analytics/devices`)
  return handleResponse<DeviceDistributionResponse>(res)
}

/**
 * Returns the top 10 URLs by click count for today (UTC).
 */
export async function getTopUrlsToday(): Promise<TopUrlsResponse> {
  const res = await fetch(`${API_BASE}/v1/analytics/top-urls`)
  return handleResponse<TopUrlsResponse>(res)
}
