import http from 'k6/http'
import { check, sleep } from 'k6'
import { Rate } from 'k6/metrics'

const failureRate = new Rate('navigation_route_failures')

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080'
const ROUTE_PATH = __ENV.ROUTE_PATH || '/api/v1/navigation/routes'

export const options = {
  scenarios: {
    baseline_navigation_routes: {
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
    http_req_duration: ['p(95)<1500'],
    navigation_route_failures: ['rate<0.01'],
  },
}

export default function () {
  const payload = JSON.stringify(buildNavigationPayload())

  const response = http.post(`${BASE_URL}${ROUTE_PATH}`, payload, {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: {
      api: 'navigation-routes',
    },
  })

  const ok = check(response, {
    'status is 200': (res) => res.status === 200,
    'response is successful': (res) => {
      const body = parseJson(res.body)
      return body?.isSuccess === true
    },
  })

  failureRate.add(!ok)
  sleep(numberEnv('SLEEP_SECONDS', 1))
}

function buildNavigationPayload() {
  return {
    startX: numberEnv('START_X', 126.951744),
    startY: numberEnv('START_Y', 37.478095),
    endX: numberEnv('END_X', 126.98079324400408),
    endY: numberEnv('END_Y', 37.56026711864722),
    startName: __ENV.START_NAME || '현재 위치',
    endName: __ENV.END_NAME || '더로우',
    destinationBuildingId:
      __ENV.DESTINATION_BUILDING_ID || 'fd53441c-1081-460f-a4a9-b068d0215e35',
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
