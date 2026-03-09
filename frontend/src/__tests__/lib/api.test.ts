/**
 * Unit tests for src/lib/api.ts
 *
 * node-mocks-http is used to model the server-side response. Each test builds
 * a MockResponse with the expected status code and body, then adapts it into a
 * browser-compatible fetch Response that is returned by the global.fetch mock.
 * This keeps the "what the server sends" concern separate from the "how the
 * client interprets it" concern under test.
 */

import { createResponse } from 'node-mocks-http'
import {
  ApiError,
  createShortUrl,
  getAnalytics,
  getDeviceDistribution,
  getTopUrlsToday,
  listUrls,
} from '@/lib/api'
import type {
  AnalyticsResponse,
  CreateUrlResponse,
  DeviceDistributionResponse,
  TopUrlsResponse,
  UrlPageResponse,
} from '@/types/api'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Converts a node-mocks-http MockResponse into a fetch-compatible Response.
 *
 * node-mocks-http models the server-side response (body serialisation, headers).
 * We call res.json(body) to record the server-side payload, then adapt the
 * result to the browser fetch Response API that lib/api.ts actually consumes.
 * The `status` parameter is used directly for `ok` / `status` rather than
 * reading `res.statusCode` back, because node-mocks-http does not guarantee
 * the initial statusCode option is preserved after send helpers are called.
 */
function asFetchResponse(status: number, body: unknown): Response {
  const res = createResponse({ statusCode: status })
  res.json(body) // serialises body into the mock response buffer

  return {
    ok: status >= 200 && status < 300,
    status,
    json: jest.fn().mockResolvedValue(body),
    text: jest.fn().mockResolvedValue(JSON.stringify(body)),
  } as unknown as Response
}

/**
 * Like asFetchResponse but the body is plain text (simulates non-JSON error
 * responses such as gateway errors).
 */
function asTextFetchResponse(status: number, text: string): Response {
  const res = createResponse({ statusCode: status })
  res.send(text)

  return {
    ok: false,
    status,
    json: jest.fn().mockRejectedValue(new SyntaxError('Unexpected token')),
    text: jest.fn().mockResolvedValue(text),
  } as unknown as Response
}

// ---------------------------------------------------------------------------
// Test data
// ---------------------------------------------------------------------------

const CREATE_RESPONSE: CreateUrlResponse = {
  id: 42,
  shortCode: 'abc123',
  shortUrl: 'http://localhost:8080/abc123',
  longUrl: 'https://example.com',
  domain: 'localhost:8080',
  aliasType: 'CUSTOM',
  createdAt: '2024-01-01T00:00:00Z',
}

const URL_PAGE_RESPONSE: UrlPageResponse = {
  data: [
    {
      alias: 'abc123',
      longUrl: 'https://example.com',
      shortUrl: 'http://localhost:8080/abc123',
      clickCount: 5,
    },
    {
      alias: 'xyz789',
      longUrl: 'https://other.com',
      shortUrl: 'http://localhost:8080/xyz789',
      clickCount: 2,
    },
  ],
  page: 0,
  pageSize: 10,
  totalRecords: 2,
  totalPages: 1,
}

const ANALYTICS_RESPONSE: AnalyticsResponse = {
  summary: { currentPeriodClicks: 100, previousPeriodClicks: 80, percentChange: 25 },
  timeseries: [{ date: '2024-01-01', clicks: 10 }],
  pagination: { currentPage: 0, totalPages: 3 },
}

const DEVICE_RESPONSE: DeviceDistributionResponse = {
  data: [
    { deviceType: 'DESKTOP', clicks: 60 },
    { deviceType: 'MOBILE', clicks: 40 },
  ],
}

const TOP_URLS_RESPONSE: TopUrlsResponse = {
  data: [
    { alias: 'abc123', longUrl: 'https://example.com', clicks: 42 },
    { alias: 'xyz789', longUrl: 'https://other.com', clicks: 17 },
    { alias: 'qrs456', longUrl: 'https://another.com', clicks: 5 },
  ],
}

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------

beforeEach(() => {
  global.fetch = jest.fn()
})

// ---------------------------------------------------------------------------
// ApiError
// ---------------------------------------------------------------------------

describe('ApiError', () => {
  it('sets name to "ApiError"', () => {
    const err = new ApiError(404, 'not found')
    expect(err.name).toBe('ApiError')
  })

  it('stores status and message', () => {
    const err = new ApiError(422, 'validation failed')
    expect(err.status).toBe(422)
    expect(err.message).toBe('validation failed')
  })

  it('is an instance of Error', () => {
    expect(new ApiError(500, 'oops')).toBeInstanceOf(Error)
  })
})

// ---------------------------------------------------------------------------
// createShortUrl
// ---------------------------------------------------------------------------

describe('createShortUrl', () => {
  it('returns parsed body on 201', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asFetchResponse(201, CREATE_RESPONSE),
    )

    const result = await createShortUrl({ longUrl: 'https://example.com', alias: 'abc123' })

    expect(result.id).toBe(42)
    expect(result.shortCode).toBe('abc123')
    expect(result.longUrl).toBe('https://example.com')
    expect(result.aliasType).toBe('CUSTOM')
    expect(result.createdAt).toBe('2024-01-01T00:00:00Z')
  })

  it('POSTs to /v1/url/create with JSON content-type', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asFetchResponse(201, CREATE_RESPONSE),
    )

    await createShortUrl({ longUrl: 'https://example.com' })

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/v1/url/create'),
      expect.objectContaining({
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      }),
    )
  })

  it('normalizes https://localhost shortUrl to http://localhost', async () => {
    const httpsResponse = {
      ...CREATE_RESPONSE,
      shortUrl: 'https://localhost:8080/abc123',
    }
    ;(global.fetch as jest.Mock).mockResolvedValue(asFetchResponse(201, httpsResponse))

    const result = await createShortUrl({ longUrl: 'https://example.com' })

    expect(result.shortUrl).toBe('http://localhost:8080/abc123')
  })

  it('normalizes https://localhost/ (path-style) shortUrl', async () => {
    const httpsResponse = {
      ...CREATE_RESPONSE,
      shortUrl: 'https://localhost/abc123',
    }
    ;(global.fetch as jest.Mock).mockResolvedValue(asFetchResponse(201, httpsResponse))

    const result = await createShortUrl({ longUrl: 'https://example.com' })

    expect(result.shortUrl).toBe('http://localhost/abc123')
  })

  it('leaves non-localhost shortUrl unchanged', async () => {
    const externalResponse = {
      ...CREATE_RESPONSE,
      shortUrl: 'https://short.example.com/abc123',
    }
    ;(global.fetch as jest.Mock).mockResolvedValue(asFetchResponse(201, externalResponse))

    const result = await createShortUrl({ longUrl: 'https://example.com' })

    expect(result.shortUrl).toBe('https://short.example.com/abc123')
  })

  it('throws ApiError with joined errors array on 422', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asFetchResponse(422, { errors: ['URL is invalid', 'Alias too short'] }),
    )

    await expect(createShortUrl({ longUrl: 'bad-url' })).rejects.toMatchObject({
      name: 'ApiError',
      status: 422,
      message: 'URL is invalid, Alias too short',
    })
  })

  it('throws ApiError with message field on 409', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asFetchResponse(409, { message: 'Alias already taken' }),
    )

    await expect(
      createShortUrl({ longUrl: 'https://example.com', alias: 'taken' }),
    ).rejects.toMatchObject({
      name: 'ApiError',
      status: 409,
      message: 'Alias already taken',
    })
  })

  it('throws ApiError with plain-text body on non-JSON error response', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asTextFetchResponse(500, 'Internal Server Error'),
    )

    await expect(createShortUrl({ longUrl: 'https://example.com' })).rejects.toMatchObject({
      name: 'ApiError',
      status: 500,
      message: 'Internal Server Error',
    })
  })

  it('falls back to generic message when body is empty on error', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asTextFetchResponse(503, ''),
    )

    await expect(createShortUrl({ longUrl: 'https://example.com' })).rejects.toMatchObject({
      name: 'ApiError',
      status: 503,
      message: 'Request failed with status 503',
    })
  })
})

// ---------------------------------------------------------------------------
// listUrls
// ---------------------------------------------------------------------------

describe('listUrls', () => {
  it('returns paginated page on 200', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asFetchResponse(200, URL_PAGE_RESPONSE),
    )

    const result = await listUrls(0)

    expect(result.page).toBe(0)
    expect(result.totalPages).toBe(1)
    expect(result.totalRecords).toBe(2)
    expect(result.data).toHaveLength(2)
  })

  it('GETs /v1/url with page query parameter', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asFetchResponse(200, URL_PAGE_RESPONSE),
    )

    await listUrls(3)

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/v1/url?page=3'),
    )
  })

  it('normalizes https://localhost shortUrl on each item in the list', async () => {
    const httpsPage: UrlPageResponse = {
      ...URL_PAGE_RESPONSE,
      data: [
        { ...URL_PAGE_RESPONSE.data[0], shortUrl: 'https://localhost:8080/abc123' },
        { ...URL_PAGE_RESPONSE.data[1], shortUrl: 'https://localhost:8080/xyz789' },
      ],
    }
    ;(global.fetch as jest.Mock).mockResolvedValue(asFetchResponse(200, httpsPage))

    const result = await listUrls(0)

    expect(result.data[0].shortUrl).toBe('http://localhost:8080/abc123')
    expect(result.data[1].shortUrl).toBe('http://localhost:8080/xyz789')
  })

  it('throws ApiError on 404', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asFetchResponse(404, { message: 'Not found' }),
    )

    await expect(listUrls(99)).rejects.toMatchObject({ name: 'ApiError', status: 404 })
  })
})

// ---------------------------------------------------------------------------
// getAnalytics
// ---------------------------------------------------------------------------

describe('getAnalytics', () => {
  it('returns analytics response on 200', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asFetchResponse(200, ANALYTICS_RESPONSE),
    )

    const result = await getAnalytics(0)

    expect(result.summary.currentPeriodClicks).toBe(100)
    expect(result.summary.previousPeriodClicks).toBe(80)
    expect(result.summary.percentChange).toBe(25)
    expect(result.timeseries).toHaveLength(1)
    expect(result.pagination.totalPages).toBe(3)
  })

  it('GETs /v1/analytics with page query parameter', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asFetchResponse(200, ANALYTICS_RESPONSE),
    )

    await getAnalytics(2)

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/v1/analytics?page=2'),
    )
  })

  it('throws ApiError on 500', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asFetchResponse(500, { message: 'Server error' }),
    )

    await expect(getAnalytics(0)).rejects.toMatchObject({ name: 'ApiError', status: 500 })
  })
})

// ---------------------------------------------------------------------------
// getDeviceDistribution
// ---------------------------------------------------------------------------

describe('getDeviceDistribution', () => {
  it('returns device distribution on 200', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asFetchResponse(200, DEVICE_RESPONSE),
    )

    const result = await getDeviceDistribution()

    expect(result.data).toHaveLength(2)
    expect(result.data[0]).toEqual({ deviceType: 'DESKTOP', clicks: 60 })
    expect(result.data[1]).toEqual({ deviceType: 'MOBILE', clicks: 40 })
  })

  it('GETs /v1/analytics/devices', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asFetchResponse(200, DEVICE_RESPONSE),
    )

    await getDeviceDistribution()

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/v1/analytics/devices'),
    )
  })

  it('throws ApiError on 503', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asFetchResponse(503, { message: 'Service unavailable' }),
    )

    await expect(getDeviceDistribution()).rejects.toMatchObject({
      name: 'ApiError',
      status: 503,
    })
  })
})

// ---------------------------------------------------------------------------
// getTopUrlsToday
// ---------------------------------------------------------------------------

describe('getTopUrlsToday', () => {
  it('returns top URLs response on 200', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asFetchResponse(200, TOP_URLS_RESPONSE),
    )

    const result = await getTopUrlsToday()

    expect(result.data).toHaveLength(3)
    expect(result.data[0]).toEqual({ alias: 'abc123', longUrl: 'https://example.com', clicks: 42 })
    expect(result.data[1]).toEqual({ alias: 'xyz789', longUrl: 'https://other.com', clicks: 17 })
    expect(result.data[2]).toEqual({ alias: 'qrs456', longUrl: 'https://another.com', clicks: 5 })
  })

  it('GETs /v1/analytics/top-urls', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asFetchResponse(200, TOP_URLS_RESPONSE),
    )

    await getTopUrlsToday()

    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/v1/analytics/top-urls'),
    )
  })

  it('returns empty data array when no clicks today', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asFetchResponse(200, { data: [] }),
    )

    const result = await getTopUrlsToday()

    expect(result.data).toHaveLength(0)
  })

  it('data is ordered by clicks descending', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asFetchResponse(200, TOP_URLS_RESPONSE),
    )

    const result = await getTopUrlsToday()

    for (let i = 1; i < result.data.length; i++) {
      expect(result.data[i - 1].clicks).toBeGreaterThanOrEqual(result.data[i].clicks)
    }
  })

  it('throws ApiError on 500', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asFetchResponse(500, { message: 'Server error' }),
    )

    await expect(getTopUrlsToday()).rejects.toMatchObject({ name: 'ApiError', status: 500 })
  })

  it('throws ApiError with message body on 503', async () => {
    ;(global.fetch as jest.Mock).mockResolvedValue(
      asFetchResponse(503, { message: 'Service unavailable' }),
    )

    await expect(getTopUrlsToday()).rejects.toMatchObject({
      name: 'ApiError',
      status: 503,
      message: 'Service unavailable',
    })
  })
})
