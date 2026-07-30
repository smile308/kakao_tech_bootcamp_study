import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const viewStatus200 = new Counter('view_status_200');
const commentCreateStatus201 = new Counter('comment_create_status_201');
const commentDeleteStatus204 = new Counter('comment_delete_status_204');
const unexpectedStatus = new Counter('unexpected_http_status');
const unexpectedStatusCounters = {
  400: new Counter('http_status_400'),
  401: new Counter('http_status_401'),
  403: new Counter('http_status_403'),
  404: new Counter('http_status_404'),
  409: new Counter('http_status_409'),
  429: new Counter('http_status_429'),
  500: new Counter('http_status_500'),
};
const otherUnexpectedStatus = new Counter('http_status_other');
const commentLifecycleFailed = new Rate('comment_lifecycle_failed');
const commentCreateDuration = new Trend('comment_create_duration', true);
const commentDeleteDuration = new Trend('comment_delete_duration', true);
const commentLifecycleDuration = new Trend('comment_lifecycle_duration', true);

const baseUrl = (__ENV.BASE_URL || '').replace(/\/$/, '');
const accessToken = __ENV.ACCESS_TOKEN || '';
const postId = __ENV.POST_ID || '';
const testProfile = __ENV.TEST_PROFILE || 'comment-only';
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

const commentScenario = {
  executor: 'constant-arrival-rate',
  rate: 5,
  timeUnit: '1s',
  duration: '60s',
  preAllocatedVUs: 20,
  maxVUs: 100,
  exec: 'commentLifecycle',
};

const profileScenarios = {
  'comment-smoke': {
    comment_lifecycle: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '10s',
      exec: 'commentLifecycle',
    },
  },
  'comment-only': {
    comment_lifecycle: commentScenario,
  },
  'mixed-normal': {
    hot_post_view: {
      executor: 'constant-arrival-rate',
      rate: 200,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 50,
      maxVUs: 500,
      exec: 'viewPost',
    },
    comment_lifecycle: commentScenario,
  },
};

if (!profileScenarios[testProfile]) {
  throw new Error(`Unknown TEST_PROFILE: ${testProfile}`);
}

export const options = {
  scenarios: profileScenarios[testProfile],
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    comment_lifecycle_failed: ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function requestParams(operation) {
  return {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    tags: {
      target_version: targetVersion,
      test_profile: testProfile,
      data_variant: dataVariant,
      operation,
    },
    timeout: '10s',
  };
}

function recordUnexpectedStatus(operation, status) {
  unexpectedStatus.add(1, {
    operation,
    status: String(status),
  });

  const statusCounter = unexpectedStatusCounters[status]
    || otherUnexpectedStatus;
  statusCounter.add(1, { operation });
}

export function viewPost() {
  const response = http.get(
    `${baseUrl}/posts/${postId}`,
    requestParams('view'),
  );

  if (response.status === 200) {
    viewStatus200.add(1);
  } else {
    recordUnexpectedStatus('view', response.status);
  }

  check(response, {
    'view status is 200': (result) => result.status === 200,
  });
}

export function commentLifecycle() {
  const lifecycleStartedAt = Date.now();
  const content = `카운터 간섭 테스트 댓글 vu=${__VU} iter=${__ITER}`;
  const createResponse = http.post(
    `${baseUrl}/posts/${postId}/comments`,
    JSON.stringify({ commentContent: content }),
    requestParams('comment_create'),
  );

  commentCreateDuration.add(createResponse.timings.duration);

  const created = check(createResponse, {
    'comment create status is 201': (result) => result.status === 201,
  });

  if (!created) {
    recordUnexpectedStatus('comment_create', createResponse.status);
    commentLifecycleFailed.add(true);
    commentLifecycleDuration.add(Date.now() - lifecycleStartedAt);
    return;
  }

  commentCreateStatus201.add(1);

  let commentId;
  try {
    commentId = createResponse.json('commentId');
  } catch (error) {
    commentLifecycleFailed.add(true);
    commentLifecycleDuration.add(Date.now() - lifecycleStartedAt);
    return;
  }

  if (!commentId) {
    commentLifecycleFailed.add(true);
    commentLifecycleDuration.add(Date.now() - lifecycleStartedAt);
    return;
  }

  const deleteResponse = http.del(
    `${baseUrl}/posts/${postId}/comments`,
    JSON.stringify({ commentId }),
    requestParams('comment_delete'),
  );

  commentDeleteDuration.add(deleteResponse.timings.duration);

  const deleted = check(deleteResponse, {
    'comment delete status is 204': (result) => result.status === 204,
  });

  if (deleted) {
    commentDeleteStatus204.add(1);
  } else {
    recordUnexpectedStatus('comment_delete', deleteResponse.status);
  }

  commentLifecycleFailed.add(!deleted);
  commentLifecycleDuration.add(Date.now() - lifecycleStartedAt);
}
