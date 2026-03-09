import type { NextConfig } from 'next'

const nextConfig: NextConfig = {
  // Allow Next.js to proxy API calls to the backend in development.
  // In production (Docker), requests should go directly to the backend service.
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'}/:path*`,
      },
    ]
  },
}

export default nextConfig
