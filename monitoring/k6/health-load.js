
import http from 'k6/http';
import { check } from 'k6';

export const options = {
    scenarios: {
        health_load: {
            executor: 'shared-iterations',
            vus: 500,
            iterations: 500,
            maxDuration: '60s',
        },
    },
    thresholds: {
        checks: ['rate==1'],
    },
    noConnectionReuse: false,
    discardResponseBodies: true,
};

export default function () {
    const response = http.get(
        'http://localhost:8080/actuator/health',
        {
            timeout: '10s',
        }
    );

    check(response, {
        'health endpoint returns 200': (r) => r.status === 200,
    });

    if (response.status !== 200) {
        console.error(
            `Health endpoint status: ${response.status}, error: ${response.error || 'none'}`
        );
    }
}
