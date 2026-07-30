import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const responseStatusCounters = {
  200: new Counter('http_status_200'),
  400: new Counter('http_status_400'),
  401: new Counter('http_status_401'),
  403: new Counter('http_status_403'),
  404: new Counter('http_status_404'),
  409: new Counter('http_status_409'),
  429: new Counter('http_status_429'),
  500: new Counter('http_status_500'),
};
const otherResponseStatus = new Counter('http_status_other');

const baseUrl = (__ENV.BASE_URL || '').replace(/\/$/, '');
const accessToken = __ENV.ACCESS_TOKEN || '';
const postId = __ENV.POST_ID || '';
const testProfile = __ENV.TEST_PROFILE || 'smoke';
const dataVariant = __ENV.DATA_VARIANT || 'default';
const targetVersion = __ENV.TARGET_VERSION || 'unknown';

if (!baseUrl) {
  throw new Error('BASE_URL is required');
}

if (!accessToken) {
  throw new Error('ACCESS_TOKEN is required');
}

if (!postId) {
  throw new Error('POST_ID is required');
}

const profiles = {
  smoke: {
    executor: 'constant-vus',
    vus: 1,
    duration: '3s',
    gracefulStop: '0s',
  },
  baseline: {
    executor: 'ramping-arrival-rate',
    startRate: 10,
    timeUnit: '1s',
    preAllocatedVUs: 50,
    maxVUs: 500,
    stages: [
      { target: 10, duration: '15s' },
      { target: 50, duration: '30s' },
      { target: 100, duration: '30s' },
      { target: 200, duration: '30s' },
      { target: 0, duration: '10s' },
    ],
    gracefulStop: '10s',
  },
  capacity: {
    executor: 'ramping-arrival-rate',
    startRate: 200,
    timeUnit: '1s',
    preAllocatedVUs: 100,
    maxVUs: 1000,
    stages: [
      { target: 200, duration: '15s' },
      { target: 500, duration: '25s' },
      { target: 1000, duration: '30s' },
      { target: 0, duration: '10s' },
    ],
    gracefulStop: '10s',
  },
};

if (!profiles[testProfile]) {
  throw new Error(`Unknown TEST_PROFILE: ${testProfile}`);
}

export const options = {
  scenarios: {
    hot_post_view: profiles[testProfile],
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const response = http.get(`${baseUrl}/posts/${postId}`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
    tags: {
      target_version: targetVersion,
      test_profile: testProfile,
      data_variant: dataVariant,
      endpoint: 'hot_post_view',
    },
    timeout: '10s',
  });

  const responseStatusCounter = responseStatusCounters[response.status]
    || otherResponseStatus;
  responseStatusCounter.add(1);

  check(response, {
    'status is 200': (result) => result.status === 200,
  });
}
