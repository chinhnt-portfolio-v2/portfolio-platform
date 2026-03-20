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

  // CI can override timeout via SMOKE_WS_TIMEOUT_MS env var (default: 5000ms)
  // Cloud Run cold start + scheduler 60s cycle means first broadcast may take >5s
  const TIMEOUT_MS = parseInt(process.env.SMOKE_WS_TIMEOUT_MS ?? '5000', 10);

  describe('WebSocket Smoke Tests — Metrics Broadcast', () => {

    it(`AC-2: WebSocket /ws/metrics receives project_health message with valid status within ${TIMEOUT_MS}ms`, (done) => {
      let messageReceived = false;
      let timerId: NodeJS.Timeout;

      const ws = new WebSocket(WS_URL, {
        handshakeTimeout: 10000,
      });

      ws.on('open', () => {
        console.log('  + WebSocket connection opened');
        timerId = setTimeout(() => {
          if (!messageReceived) {
            ws.close();
            done(new Error(
              `TIMEOUT: No project_health message received within ${TIMEOUT_MS}ms from ${WS_URL}. ` +
              'The metrics broadcast pipeline may be down or the scheduler cycle (>60s) ' +
              'has not completed since deployment.'
            ));
          }
        }, TIMEOUT_MS);
      });

      ws.on('message', (data: Buffer) => {
        // Log only if test is still active — suppress extra messages that arrive
        // after done() has been called (e.g. second project snapshot after first message).
        if (done.hasBeenCalled) return;

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
              ws.terminate(); // Force destroy immediately — prevents queued messages from
                             // arriving after done() is called and causing Jest exit-1
              done();
            }
          } else {
            console.warn(`  ! Received message without valid status field:`, message);
          }
        } catch {
          console.warn(`  ! Failed to parse WebSocket message as JSON:`, data.toString());
        }
      });

      ws.on('close', (code: number) => {
        // done.hasBeenCalled catches both ws.terminate() and ws.close(1000).
        // If done() was already called, suppress any close-triggered logging.
        if (done.hasBeenCalled || messageReceived) return;
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
