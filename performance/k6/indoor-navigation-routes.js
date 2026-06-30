import http from 'k6/http'
import { check, sleep } from 'k6'
import { Rate } from 'k6/metrics'

const failureRate = new Rate('indoor_navigation_route_failures')

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080'
const ROUTE_PATH = __ENV.ROUTE_PATH || '/api/v1/navigation/routes'

export const options = {
  scenarios: {
    indoor_navigation_routes: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: numberEnv('VUS', 10) },
        { duration: __ENV.DURATION || '1m', target: numberEnv('VUS', 10) },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    indoor_navigation_route_failures: ['rate<0.01'],
  },
}

export default function () {
  const response = http.post(
    `${BASE_URL}${ROUTE_PATH}`,
    JSON.stringify(buildIndoorNavigationPayload()),
    {
      headers: {
        'Content-Type': 'application/json',
      },
      tags: {
        api: 'indoor-navigation-routes',
      },
    },
  )

  const ok = check(response, {
    'status is 200': (res) => res.status === 200,
    'response is successful': (res) => {
      const body = parseJson(res.body)
      return body?.isSuccess === true
    },
    'route is indoor only': (res) => {
      const body = parseJson(res.body)
      const routes = body?.result?.routes || []

      return routes.length > 0 && routes.every((route) => {
        const legs = route?.legs || []
        return legs.length > 0 && legs.every((leg) => leg.mode === 'INDOOR')
      })
    },
  })

  failureRate.add(!ok)
  sleep(numberEnv('SLEEP_SECONDS', 1))
}

function buildIndoorNavigationPayload() {
  return {
    startX: numberEnv('START_X', 0),
    startY: numberEnv('START_Y', 0),
    endX: numberEnv('END_X', 0),
    endY: numberEnv('END_Y', 0),
    startName: __ENV.START_NAME || '구찌',
    endName: __ENV.END_NAME || '더로우',
    startPoiId: numberEnv('START_POI_ID', 104),
    destinationPoiId: numberEnv('DESTINATION_POI_ID', 125),
    includeIndoor: booleanEnv('INCLUDE_INDOOR', true),
    routeTypes: routeTypesEnv(),
  }
}

function routeTypesEnv() {
  return (__ENV.ROUTE_TYPES || 'WALK')
    .split(',')
    .map((type) => type.trim().toUpperCase())
    .filter(Boolean)
}

function numberEnv(name, fallback) {
  const value = __ENV[name]
  if (value === undefined || value === '') {
    return fallback
  }

  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    throw new Error(`Invalid numeric ENV ${name}: ${value}`)
  }
  return parsed
}

function booleanEnv(name, fallback) {
  const value = __ENV[name]
  if (value === undefined || value === '') {
    return fallback
  }
  return value.toLowerCase() === 'true'
}

function parseJson(value) {
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}
