/**
 * WebSocket Smoke Test — Metrics Broadcast
 *
 * Covers AC-2: Open WebSocket to wss://<DEPLOYED_BE_URL>/ws/metrics and
 * receive at least one project_health message with a valid `status` field
 * within ≤ 5 seconds.
 *
 * Run: npm run smoke:test -- --testPathPattern=websocket
 * Required env: DEPLOYED_BE_URL (e.g. https://portfolio-platform-xxxx.as.a.run.app)
 */

import { WebSocket } from 'ws';

const BASE_URL = process.env.DEPLOYED_BE_URL;

if (!BASE_URL) {
  // Skip — smoke tests run against a deployed URL, not locally.
  // In CI, DEPLOYED_BE_URL is set via GitHub Actions env vars / secrets.
  describe.skip('WebSocket Smoke Tests — Metrics Broadcast', () => {
    it('skipped: DEPLOYED_BE_URL not set', () => {
      expect(true).toBe(true);
    });
  });
} else {
  const WS_URL = BASE_URL.replace(/^http/, 'ws') + '/ws/metrics';

  describe('WebSocket Smoke Tests — Metrics Broadcast', () => {

    it('AC-2: WebSocket /ws/metrics receives project_health message with valid status within 5s', (done) => {
      let messageReceived = false;
      let closedByTest = false;
      let timerId: NodeJS.Timeout;

      console.log(`  -> Connecting to ${WS_URL} ...`);

      const ws = new WebSocket(WS_URL, {
        handshakeTimeout: 10000,
      });

      ws.on('open', () => {
        console.log('  + WebSocket connection opened');
        timerId = setTimeout(() => {
          if (!messageReceived) {
            ws.close();
            done(new Error(
              `TIMEOUT: No project_health message received within 5 seconds from ${WS_URL}. ` +
              'The metrics broadcast pipeline may be down.'
            ));
          }
        }, 5000);
      });

      ws.on('message', (data: Buffer) => {
        try {
          const message = JSON.parse(data.toString());
          console.log(`  <- Received WebSocket message:`, JSON.stringify(message));

          if (message && typeof message.status === 'string' && message.status.length > 0) {
            if (!messageReceived) {
              messageReceived = true;
              clearTimeout(timerId);
              expect(typeof message.status).toBe('string');
              expect(message.status.length).toBeGreaterThan(0);
              console.log(`  + project_health message received -- status: "${message.status}"`);
              closedByTest = true;
              ws.close(1000, 'Smoke test passed');
              done();
            }
          } else {
            console.warn(`  ! Received message without valid status field:`, message);
          }
        } catch {
          console.warn(`  ! Failed to parse WebSocket message as JSON:`, data.toString());
        }
      });

      ws.on('close', (code: number, reason: Buffer) => {
        if (closedByTest || messageReceived) {
          console.log(`  + WebSocket closed by test -- code: ${code}`);
          return;
        }
        clearTimeout(timerId);
        done(new Error(`WebSocket closed (code=${code}) without receiving a valid project_health message.`));
      });

      ws.on('error', (err: Error) => {
        clearTimeout(timerId);
        console.error(`  x WebSocket error: ${err.message}`);
        done(new Error(`WebSocket connection failed: ${err.message}`));
      });
    });
  });
}
