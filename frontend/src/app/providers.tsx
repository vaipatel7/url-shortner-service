'use client'

import { ChakraProvider, extendTheme, type StorageManager } from '@chakra-ui/react'

const theme = extendTheme({
  config: {
    initialColorMode: 'dark',
    useSystemColorMode: false,
  },
})

// No localStorage — always dark on both server and client.
// Eliminates the SSR/hydration color-mode mismatch.
const darkModeManager: StorageManager = {
  get: () => 'dark',
  set: () => {},
  type: 'localStorage',
}

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <ChakraProvider theme={theme} colorModeManager={darkModeManager}>
      {children}
    </ChakraProvider>
  )
}
