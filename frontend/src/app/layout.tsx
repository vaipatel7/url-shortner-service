import type { Metadata } from 'next'
import { ColorModeScript } from '@chakra-ui/react'
import { Providers } from './providers'

export const metadata: Metadata = {
  title: 'URL Shortener',
  description: 'Generate and manage short URLs with redirection and analytics',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <ColorModeScript initialColorMode="dark" />
      </head>
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  )
}
