'use client'

import {
  Alert,
  AlertIcon,
  Badge,
  Box,
  Button,
  Container,
  Divider,
  Flex,
  FormControl,
  FormErrorMessage,
  FormHelperText,
  FormLabel,
  Heading,
  HStack,
  Input,
  Link,
  SimpleGrid,
  Spinner,
  Stat,
  StatArrow,
  StatHelpText,
  StatLabel,
  StatNumber,
  Tab,
  TableContainer,
  TabList,
  TabPanel,
  TabPanels,
  Tabs,
  Tag,
  Table,
  Tbody,
  Td,
  Text,
  Th,
  Thead,
  Tr,
  useClipboard,
  useColorModeValue,
  useToast,
  VStack,
} from '@chakra-ui/react'
import { useCallback, useEffect, useState } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { ApiError, createShortUrl, getAnalytics, getDeviceDistribution, getTopUrlsToday, listUrls } from '@/lib/api'
import type {
  AnalyticsResponse,
  CreateUrlResponse,
  DeviceTypeStat,
  TopUrlStat,
  UrlPageResponse,
} from '@/types/api'

const DEVICE_COLORS: Record<string, string> = {
  DESKTOP: '#3182CE',
  MOBILE:  '#38A169',
  TABLET:  '#D69E2E',
  BOT:     '#E53E3E',
  UNKNOWN: '#A0AEC0',
}

function formatAxisDate(dateStr: string): string {
  const d = new Date(dateStr + 'T00:00:00Z')
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

// ---------------------------------------------------------------------------
// URL Shortener Tab
// ---------------------------------------------------------------------------

function ShortenForm() {
  const [longUrl, setLongUrl] = useState('')
  const [alias, setAlias] = useState('')
  const [domain, setDomain] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<CreateUrlResponse | null>(null)
  const [errors, setErrors] = useState<{ longUrl?: string; alias?: string }>({})

  const toast = useToast()
  const { onCopy, hasCopied } = useClipboard(result?.shortUrl ?? '')

  const cardBg = useColorModeValue('gray.50', 'gray.700')
  const resultBg = useColorModeValue('green.50', 'green.900')

  function validate(): boolean {
    const e: { longUrl?: string; alias?: string } = {}
    const trimmedUrl = longUrl.trim()
    if (!trimmedUrl) {
      e.longUrl = 'Long URL is required'
    } else if (trimmedUrl.length > 2048) {
      e.longUrl = 'URL must not exceed 2048 characters'
    } else {
      try {
        const parsed = new URL(trimmedUrl)
        if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
          e.longUrl = 'URL must start with http:// or https://'
        }
      } catch {
        e.longUrl = 'Please enter a valid URL'
      }
    }
    const trimmedAlias = alias.trim()
    if (trimmedAlias && (trimmedAlias.length < 6 || trimmedAlias.length > 20)) {
      e.alias = 'Alias must be between 6 and 20 characters'
    }
    setErrors(e)
    return Object.keys(e).length === 0
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!validate()) return

    setLoading(true)
    setResult(null)
    try {
      const payload: { longUrl: string; alias?: string; domain?: string } = {
        longUrl: longUrl.trim(),
      }
      if (alias.trim()) payload.alias = alias.trim()
      if (domain.trim()) payload.domain = domain.trim()

      const data = await createShortUrl(payload)
      setResult(data)
    } catch (err: unknown) {
      if (err instanceof ApiError && (err.status === 422 || err.status === 409)) {
        setErrors(v => ({ ...v, alias: 'Alias is not available' }))
      } else {
        toast({
          title: 'Error',
          description: err instanceof Error ? err.message : 'Something went wrong',
          status: 'error',
          duration: 6000,
          isClosable: true,
        })
      }
    } finally {
      setLoading(false)
    }
  }

  function handleReset() {
    setLongUrl('')
    setAlias('')
    setDomain('')
    setErrors({})
    setResult(null)
  }

  return (
    <VStack spacing={6} align="stretch">
      <Box as="form" onSubmit={handleSubmit} bg={cardBg} p={6} borderRadius="lg">
        <VStack spacing={4} align="stretch">
          <FormControl isRequired isInvalid={!!errors.longUrl}>
            <FormLabel>Long URL</FormLabel>
            <Input
              placeholder="https://example.com/very/long/path"
              value={longUrl}
              onChange={e => {
                setLongUrl(e.target.value)
                setErrors(v => ({ ...v, longUrl: undefined }))
              }}
            />
            <FormErrorMessage>{errors.longUrl}</FormErrorMessage>
          </FormControl>

          <FormControl isInvalid={!!errors.alias}>
            <FormLabel>
              Custom Alias{' '}
              <Tag size="sm" colorScheme="gray" ml={1}>
                optional
              </Tag>
            </FormLabel>
            <Input
              placeholder="my-link (6–20 characters)"
              value={alias}
              onChange={e => {
                setAlias(e.target.value)
                setErrors(v => ({ ...v, alias: undefined }))
              }}
            />
            <FormHelperText>Leave blank to auto-generate an 8-character alias.</FormHelperText>
            <FormErrorMessage>{errors.alias}</FormErrorMessage>
          </FormControl>

          <FormControl>
            <FormLabel>
              Domain{' '}
              <Tag size="sm" colorScheme="gray" ml={1}>
                paid plan
              </Tag>
            </FormLabel>
            <Input
              placeholder="http://localhost:3000"
              value=""
              isDisabled
              _disabled={{ opacity: 0.6, cursor: 'not-allowed' }}
            />
            <FormHelperText>
              Custom domains are available in the paid plan.
            </FormHelperText>
          </FormControl>

          <HStack justify="flex-end" pt={2}>
            <Button variant="ghost" onClick={handleReset} isDisabled={loading}>
              Reset
            </Button>
            <Button
              type="submit"
              colorScheme="blue"
              isLoading={loading}
              loadingText="Creating…"
            >
              Shorten URL
            </Button>
          </HStack>
        </VStack>
      </Box>

      {result && (
        <Box
          bg={resultBg}
          p={5}
          borderRadius="lg"
          borderWidth={1}
          borderColor="green.300"
        >
          <VStack align="stretch" spacing={3}>
            <HStack justify="space-between">
              <Text fontWeight="bold" fontSize="lg">
                Short URL created!
              </Text>
              <Badge colorScheme={result.aliasType === 'CUSTOM' ? 'purple' : 'blue'}>
                {result.aliasType}
              </Badge>
            </HStack>

            <HStack spacing={2}>
              <Input value={result.shortUrl} isReadOnly fontFamily="mono" size="sm" />
              <Button size="sm" colorScheme="green" onClick={onCopy} flexShrink={0}>
                {hasCopied ? 'Copied!' : 'Copy'}
              </Button>
              <Button
                size="sm"
                colorScheme="blue"
                as="a"
                href={result.shortUrl}
                target="_blank"
                rel="noopener noreferrer"
              >
                Visit
              </Button>
            </HStack>
          </VStack>
        </Box>
      )}
    </VStack>
  )
}

// ---------------------------------------------------------------------------
// Short URLs Tab
// ---------------------------------------------------------------------------

function ShortUrlsPanel() {
  const [page, setPage] = useState(0)
  const [data, setData] = useState<UrlPageResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const tableBg = useColorModeValue('white', 'gray.800')

  const load = useCallback(async (p: number) => {
    setLoading(true)
    setError(null)
    try {
      const res = await listUrls(p)
      setData(res)
      setPage(p)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load URLs')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load(0) }, [load])

  return (
    <VStack spacing={4} align="stretch">
      {error && (
        <Alert status="error" borderRadius="md">
          <AlertIcon />
          {error}
        </Alert>
      )}

      {loading && (
        <Flex justify="center" py={8}>
          <Spinner size="lg" />
        </Flex>
      )}

      {data && !loading && (
        <>
          <Flex justify="space-between" align="center">
            <Text fontSize="sm" color="gray.500">
              {data.totalRecords} total URL{data.totalRecords !== 1 ? 's' : ''} · page{' '}
              {data.page + 1} of {Math.max(data.totalPages, 1)}
            </Text>
            <Button size="sm" variant="outline" onClick={() => load(page)}>
              Refresh
            </Button>
          </Flex>

          <TableContainer bg={tableBg} borderRadius="lg" borderWidth={1} overflowX="auto">
            <Table size="sm" variant="simple" style={{ tableLayout: 'fixed', width: '100%' }}>
              <Thead>
                <Tr>
                  <Th w="140px" whiteSpace="nowrap">Alias</Th>
                  <Th>Long URL</Th>
                  <Th w="80px" whiteSpace="nowrap" isNumeric>Clicks</Th>
                  <Th
                    w="72px"
                    position="sticky"
                    right={0}
                    bg={tableBg}
                    zIndex={1}
                    boxShadow="-2px 0 6px rgba(0,0,0,0.06)"
                  />
                </Tr>
              </Thead>
              <Tbody>
                {data.data.length === 0 ? (
                  <Tr>
                    <Td colSpan={4} textAlign="center" color="gray.500" py={6}>
                      No URLs found.
                    </Td>
                  </Tr>
                ) : (
                  data.data.map(row => (
                    <Tr key={row.alias}>
                      <Td fontFamily="mono" whiteSpace="nowrap" overflow="hidden" textOverflow="ellipsis">
                        {row.alias}
                      </Td>
                      <Td overflow="hidden">
                        <Link
                          href={row.longUrl}
                          isExternal
                          color="blue.400"
                          display="block"
                          overflow="hidden"
                          textOverflow="ellipsis"
                          whiteSpace="nowrap"
                        >
                          {row.longUrl}
                        </Link>
                      </Td>
                      <Td whiteSpace="nowrap" textAlign="right">
                        <Text fontSize="sm" color="gray.500">
                          {row.clickCount} click{row.clickCount !== 1 ? 's' : ''}
                        </Text>
                      </Td>
                      <Td
                        whiteSpace="nowrap"
                        position="sticky"
                        right={0}
                        bg={tableBg}
                        boxShadow="-2px 0 6px rgba(0,0,0,0.06)"
                      >
                        <Button
                          size="xs"
                          colorScheme="blue"
                          variant="outline"
                          as="a"
                          href={row.shortUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                        >
                          Click
                        </Button>
                      </Td>
                    </Tr>
                  ))
                )}
              </Tbody>
            </Table>
          </TableContainer>

          <HStack justify="center" spacing={2}>
            <Button
              size="sm"
              onClick={() => load(page - 1)}
              isDisabled={page <= 0}
              variant="outline"
            >
              ← Prev
            </Button>
            <Text fontSize="sm" px={2}>
              Page {page + 1}
            </Text>
            <Button
              size="sm"
              onClick={() => load(page + 1)}
              isDisabled={page >= data.totalPages - 1}
              variant="outline"
            >
              Next → 
            </Button>
          </HStack>
        </>
      )}
    </VStack>
  )
}

// ---------------------------------------------------------------------------
// Analytics Tab
// ---------------------------------------------------------------------------

function AnalyticsPanel() {
  const [page, setPage]         = useState(0)
  const [analytics, setAnalytics] = useState<AnalyticsResponse | null>(null)
  const [devices, setDevices]   = useState<DeviceTypeStat[] | null>(null)
  const [topUrls, setTopUrls]   = useState<TopUrlStat[] | null>(null)
  const [loading, setLoading]   = useState(false)
  const [error, setError]       = useState<string | null>(null)

  const cardBg    = useColorModeValue('white', 'gray.800')
  const gridColor = useColorModeValue('#E2E8F0', '#4A5568')
  const textMuted = useColorModeValue('gray.500', 'gray.400')

  const fetch = useCallback(async (p: number, withDevices = false) => {
    setLoading(true)
    setError(null)
    try {
      if (withDevices) {
        const [a, d, t] = await Promise.all([getAnalytics(p), getDeviceDistribution(), getTopUrlsToday()])
        setAnalytics(a)
        setDevices(d.data)
        setTopUrls(t.data)
      } else {
        setAnalytics(await getAnalytics(p))
      }
      setPage(p)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load analytics')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetch(0, true) }, [fetch])

  const { summary, timeseries, pagination } = analytics ?? {
    summary: { currentPeriodClicks: 0, previousPeriodClicks: 0, percentChange: 0 },
    timeseries: [],
    pagination: { currentPage: 0, totalPages: 1 },
  }

  const pctChange = summary.percentChange
  const hasChange = pctChange !== 0

  return (
    <VStack spacing={5} align="stretch">
      {error && (
        <Alert status="error" borderRadius="md">
          <AlertIcon />
          {error}
        </Alert>
      )}

      {loading && !analytics && (
        <Flex justify="center" py={8}>
          <Spinner size="lg" />
        </Flex>
      )}

      {analytics && (
        <>
          {/* ── Metric cards ─────────────────────────────────────── */}
          <SimpleGrid columns={2} spacing={4}>
            <Box bg={cardBg} p={5} borderRadius="lg" borderWidth={1}>
              <Stat>
                <StatLabel color={textMuted}>Current 7 days</StatLabel>
                <StatNumber fontSize="2xl">
                  {summary.currentPeriodClicks.toLocaleString()}
                </StatNumber>
                <StatHelpText mb={0}>clicks</StatHelpText>
              </Stat>
            </Box>

            <Box bg={cardBg} p={5} borderRadius="lg" borderWidth={1}>
              <Stat>
                <StatLabel color={textMuted}>Previous 7 days</StatLabel>
                <StatNumber fontSize="2xl">
                  {summary.previousPeriodClicks.toLocaleString()}
                </StatNumber>
                <StatHelpText mb={0}>
                  {hasChange ? (
                    <>
                      <StatArrow type={pctChange > 0 ? 'increase' : 'decrease'} />
                      {Math.abs(pctChange)}%
                    </>
                  ) : (
                    'no change'
                  )}
                </StatHelpText>
              </Stat>
            </Box>
          </SimpleGrid>

          {/* ── Line chart ───────────────────────────────────────── */}
          <Box bg={cardBg} p={5} borderRadius="lg" borderWidth={1}>
            <Flex justify="space-between" align="center" mb={4}>
              <Text fontWeight="semibold">Daily Clicks</Text>
              <HStack spacing={1}>
                <Button
                  size="xs"
                  variant="ghost"
                  onClick={() => fetch(page + 1)}
                  isDisabled={loading || page >= pagination.totalPages - 1}
                  aria-label="Previous page"
                >
                  &lt;
                </Button>
                <Button
                  size="xs"
                  variant="ghost"
                  onClick={() => fetch(page - 1)}
                  isDisabled={loading || page <= 0}
                  aria-label="Next page"
                >
                  &gt;
                </Button>
              </HStack>
            </Flex>

            <ResponsiveContainer width="100%" height={200}>
              <LineChart data={timeseries} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={gridColor} />
                <XAxis
                  dataKey="date"
                  tickFormatter={formatAxisDate}
                  tick={{ fontSize: 11 }}
                  tickLine={false}
                />
                <YAxis allowDecimals={false} tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
                <Tooltip
                  labelFormatter={(label) => formatAxisDate(String(label))}
                  formatter={(v) => [(v as number).toLocaleString(), 'Clicks']}
                />
                <Line
                  type="monotone"
                  dataKey="clicks"
                  stroke="#3182CE"
                  strokeWidth={2}
                  dot={{ r: 3, fill: '#3182CE' }}
                  activeDot={{ r: 5 }}
                />
              </LineChart>
            </ResponsiveContainer>
          </Box>

          {/* ── Top URLs today ───────────────────────────────────── */}
          {topUrls !== null && (
            <>
              <Divider />
              <Box bg={cardBg} p={5} borderRadius="lg" borderWidth={1}>
                <Flex justify="space-between" align="center" mb={4}>
                  <Text fontWeight="semibold">Top 10 URLs Today</Text>
                </Flex>

                {topUrls.length === 0 ? (
                  <Text fontSize="sm" color={textMuted} textAlign="center" py={4}>
                    No clicks recorded today yet.
                  </Text>
                ) : (
                  <ResponsiveContainer width="100%" height={Math.max(topUrls.length * 36, 120)}>
                    <BarChart
                      data={topUrls}
                      layout="vertical"
                      margin={{ top: 0, right: 24, left: 8, bottom: 0 }}
                    >
                      <CartesianGrid strokeDasharray="3 3" stroke={gridColor} horizontal={false} />
                      <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
                      <YAxis
                        type="category"
                        dataKey="alias"
                        width={90}
                        tick={{ fontSize: 11 }}
                        tickLine={false}
                        axisLine={false}
                      />
                      <Tooltip
                        formatter={(v, _name, props) => [
                          `${(v as number).toLocaleString()} clicks`,
                          props.payload?.longUrl ?? '',
                        ]}
                        labelFormatter={(label) => `/${label}`}
                      />
                      <Bar dataKey="clicks" fill="#3182CE" radius={[0, 4, 4, 0]} maxBarSize={24} />
                    </BarChart>
                  </ResponsiveContainer>
                )}
              </Box>
            </>
          )}

          {/* ── Device distribution ──────────────────────────────── */}
          {devices && devices.length > 0 && (
            <>
              <Divider />
              <Box bg={cardBg} p={5} borderRadius="lg" borderWidth={1}>
                <Flex justify="space-between" align="center" mb={2}>
                  <Text fontWeight="semibold">Device Distribution</Text>
                  <Text fontSize="xs" color={textMuted}>Last 30 days</Text>
                </Flex>

                <ResponsiveContainer width="100%" height={240}>
                  <PieChart>
                    <Pie
                      data={devices}
                      dataKey="clicks"
                      nameKey="deviceType"
                      innerRadius={64}
                      outerRadius={96}
                      paddingAngle={2}
                    >
                      {devices.map(entry => (
                        <Cell
                          key={entry.deviceType}
                          fill={DEVICE_COLORS[entry.deviceType] ?? '#A0AEC0'}
                        />
                      ))}
                    </Pie>
                    <Tooltip formatter={(v) => [(v as number).toLocaleString(), 'Clicks']} />
                    <Legend
                      iconType="circle"
                      iconSize={10}
                      formatter={(value: string) =>
                        value.charAt(0) + value.slice(1).toLowerCase()
                      }
                    />
                  </PieChart>
                </ResponsiveContainer>
              </Box>
            </>
          )}

          {devices && devices.length === 0 && (
            <Text fontSize="sm" color={textMuted} textAlign="center" py={2}>
              No device data for the last 30 days.
            </Text>
          )}

          <Flex justify="flex-end">
            <Button
              size="sm"
              variant="outline"
              isLoading={loading}
              onClick={() => fetch(0, true)}
            >
              Refresh
            </Button>
          </Flex>
        </>
      )}
    </VStack>
  )
}

// ---------------------------------------------------------------------------
// Root page
// ---------------------------------------------------------------------------

export default function HomePage() {
  const [mounted, setMounted] = useState(false)
  const bg = useColorModeValue('gray.100', 'gray.900')
  const headerBg = useColorModeValue('white', 'gray.800')

  useEffect(() => { setMounted(true) }, [])

  if (!mounted) return null

  return (
    <Box minH="100vh" bg={bg}>
      <Box bg={headerBg} borderBottomWidth={1} py={4} mb={8}>
        <Container maxW="2xl">
          <Heading size="md">URL Shortener</Heading>
        </Container>
      </Box>

      <Container maxW="4xl" pb={16}>
        <Tabs colorScheme="blue" variant="enclosed">
          <TabList>
            <Tab>URL Shortener</Tab>
            <Tab>Short URLs</Tab>
            <Tab>Analytics</Tab>
          </TabList>
          <TabPanels>
            <TabPanel px={0} pt={6}>
              <ShortenForm />
            </TabPanel>
            <TabPanel px={0} pt={6}>
              <ShortUrlsPanel />
            </TabPanel>
            <TabPanel px={0} pt={6}>
              <AnalyticsPanel />
            </TabPanel>
          </TabPanels>
        </Tabs>
      </Container>
    </Box>
  )
}