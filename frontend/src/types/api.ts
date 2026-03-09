// ---------------------------------------------------------------------------
// API request / response types mirroring the backend OpenAPI spec
// ---------------------------------------------------------------------------

export interface CreateUrlRequest {
  longUrl: string
  domain?: string
  alias?: string
}

export interface CreateUrlResponse {
  id: number
  shortCode: string
  shortUrl: string
  longUrl: string
  domain: string
  aliasType: 'CUSTOM' | 'GENERATED'
  createdAt: string
}

export interface ShortUrlSummary {
  alias: string
  longUrl: string
  shortUrl: string
  clickCount: number
}

export interface UrlPageResponse {
  data: ShortUrlSummary[]
  page: number
  pageSize: number
  totalRecords: number
  totalPages: number
}

export interface ApiError {
  code: number
  message: string
}

export interface AnalyticsSummary {
  currentPeriodClicks: number
  previousPeriodClicks: number
  percentChange: number
}

export interface TimeseriesPoint {
  date: string
  clicks: number
}

export interface AnalyticsPagination {
  currentPage: number
  totalPages: number
}

export interface AnalyticsResponse {
  summary: AnalyticsSummary
  timeseries: TimeseriesPoint[]
  pagination: AnalyticsPagination
}

export interface DeviceTypeStat {
  deviceType: string
  clicks: number
}

export interface DeviceDistributionResponse {
  data: DeviceTypeStat[]
}
