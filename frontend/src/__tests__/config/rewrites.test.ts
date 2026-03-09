/**
 * Unit tests for next.config.ts rewrite rules.
 *
 * Verifies that:
 *  - /api/:path* is rewritten to the backend base URL
 *  - The default destination is http://localhost:8080 when no env var is set
 *  - NEXT_PUBLIC_API_URL is used when provided
 */

import nextConfig from '../../../next.config'
import type { Rewrite } from 'next/dist/lib/load-custom-routes'

// next.config.ts's rewrites() reads process.env at call time, so we can
// control the env var per-test without reloading the module.

describe('next.config rewrites', () => {
  const originalEnv = process.env.NEXT_PUBLIC_API_URL

  afterEach(() => {
    if (originalEnv === undefined) {
      delete process.env.NEXT_PUBLIC_API_URL
    } else {
      process.env.NEXT_PUBLIC_API_URL = originalEnv
    }
  })

  it('returns exactly one rewrite rule', async () => {
    delete process.env.NEXT_PUBLIC_API_URL

    const rules = (await nextConfig.rewrites!()) as Rewrite[]

    expect(rules).toHaveLength(1)
  })

  it('rewrites source is /api/:path*', async () => {
    delete process.env.NEXT_PUBLIC_API_URL

    const rules = (await nextConfig.rewrites!()) as Rewrite[]

    expect(rules[0].source).toBe('/api/:path*')
  })

  it('default destination points to http://localhost:8080 when env var is absent', async () => {
    delete process.env.NEXT_PUBLIC_API_URL

    const rules = (await nextConfig.rewrites!()) as Rewrite[]

    expect(rules[0].destination).toBe('http://localhost:8080/:path*')
  })

  it('destination uses NEXT_PUBLIC_API_URL when set', async () => {
    process.env.NEXT_PUBLIC_API_URL = 'http://backend:8080'

    const rules = (await nextConfig.rewrites!()) as Rewrite[]

    expect(rules[0].destination).toBe('http://backend:8080/:path*')
  })

  it('destination preserves trailing port in custom API URL', async () => {
    process.env.NEXT_PUBLIC_API_URL = 'https://api.example.com:9090'

    const rules = (await nextConfig.rewrites!()) as Rewrite[]

    expect(rules[0].destination).toBe('https://api.example.com:9090/:path*')
  })
})
