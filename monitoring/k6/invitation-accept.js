
import http from 'k6/http';
import { check } from 'k6';

export const options = {
    scenarios: {
        concurrent_invitation_acceptance: {
            executor: 'shared-iterations',
            vus: 10,
            iterations: 10,
            maxDuration: '30s',
        },
    },
    thresholds: {
        checks: ['rate==1'],
    },
};

const BASE_URL = 'http://localhost:8080';

export default function () {
    const invitationToken = 'uhsEEKAGxZpe-TBycCBRRGWehfk9ntjCIpdGVpLreVg';
    const authToken = 'eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJwbXMtYXBpIiwic3ViIjoiMiIsImV4cCI6MTc5MzcwNjQ2OCwiaWF0IjoxNzkzNzAyODY4LCJlbWFpbCI6InNrQGV4LmNvbSJ9.KD0G8axNUnZEQK7TgDeoz3fTJ6c0lKH62cD4-1nwMTk';

    if (!invitationToken || !authToken) {
        throw new Error('INVITATION_TOKEN and AUTH_TOKEN must be set');
    }

    const response = http.post(
        `${BASE_URL}/api/tenant-invitations/accept`,
        JSON.stringify({
            token: invitationToken,
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${authToken}`,
            },
        }
    );

    const accepted = response.status === 204;
    const rejected = response.status === 400 || response.status === 409;

    check(response, {
        'accepted or duplicate acceptance rejected': () =>
            accepted || rejected,
    });

    if (accepted) {
        console.log('Invitation accepted');
    } else if (rejected) {
        console.log(`Duplicate acceptance rejected: HTTP ${response.status}`);
    } else {
        console.error(`Unexpected response: HTTP ${response.status}`);
    }
}
