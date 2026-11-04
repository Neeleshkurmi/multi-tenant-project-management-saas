import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

http.setResponseCallback(
    http.expectedStatuses(201, 400, 409)
);

const statusCounters = {
    0: new Counter('response_status_0'),
    400: new Counter('response_status_400'),
    401: new Counter('response_status_401'),
    403: new Counter('response_status_403'),
    429: new Counter('response_status_429'),
    500: new Counter('response_status_500'),
    503: new Counter('response_status_503'),
};

export const options = {
    noConnectionReuse: false,
    discardResponseBodies: true,
    scenarios: {
        concurrent_invitation_creation: {
            executor: 'shared-iterations',
            vus: 500,
            iterations: 500,
            maxDuration: '60s',
        },
    },
    thresholds: {
        checks: ['rate==1'],
    },
};

const BASE_URL = 'http://localhost:8080';

export default function () {
    const token = 'eyJhbGciOiJIUzI1NiJ9.eyJ0ZW5hbnRfaWQiOjEsInN1YiI6IjEiLCJyb2xlIjoiT1dORVIiLCJpc3MiOiJwbXMtYXBpIiwiZXhwIjoxNzkzNzExMDY4LCJpYXQiOjE3OTM3MDc0NjgsImVtYWlsIjoibmtAZXguY29tIn0.O2gKxmaValrQ_kX8Qu_uM4pLmXsGD5n8j27jAa6PG0w';
    const tenantId = 1;

    const payload = JSON.stringify({
        tenantId: tenantId,
        email: 'concurrency-test-500-VUs-2@example.com',
        role: 'MEMBER',
    });

    const response = http.post(
        `${BASE_URL}/api/tenant-invitations`,
        payload,
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`,
            },
            timeout: '10s',
            redirects: 0,
        }
    );

    if (statusCounters[response.status]) {
        statusCounters[response.status].add(1);
    } else {
        console.log(`Other HTTP status: ${response.status}`);
    }

    console.log(`HTTP ${response.status}`);
    const created = response.status === 201;
    const duplicateRejected = response.status === 400 || response.status === 409;

    check(response, {
        'invitation created or duplicate rejected': () =>
            created || duplicateRejected,
    });

    if (created) {
        console.log('Invitation created');
    } else if (duplicateRejected) {
        console.log('Duplicate invitation rejected');
    } else {
        console.error(
             `HTTP ${response.status} | error: ${response.error || 'none'}`
         );
    }
}
