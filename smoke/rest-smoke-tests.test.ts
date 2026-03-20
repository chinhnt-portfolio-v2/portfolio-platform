/**
 * REST Smoke Test — Contact Form → Admin Analytics Integration
 *
 * Covers AC-1: POST /api/v1/contact-submissions → GET /api/v1/admin/analytics
 *
 * Run: npm run smoke:test -- --testPathPattern=rest
 * Required env: DEPLOYED_BE_URL (e.g. https://portfolio-platform-xxxx.as.a.run.app)
 * Optional env: SMOKE_TEST_ADMIN_TOKEN (JWT for admin endpoint; if absent, analytics
 *               assertion is skipped and contact POST success alone is validated)
 */

import request from 'supertest';

// ── Configuration ─────────────────────────────────────────────────────────────

const BASE_URL = process.env.DEPLOYED_BE_URL;

// Smoke tests require a deployed URL — skip entire suite if not set
const describeOrSkip = BASE_URL ? describe : describe.skip;

describeOrSkip('REST Smoke Tests — Contact Form → Admin Analytics', () => {

  // Unique submission ID captured after POST
  let submissionId: string | null = null;
  let adminToken: string | undefined = process.env.SMOKE_TEST_ADMIN_TOKEN;

  // ── AC-1a: POST /api/v1/contact-submissions → HTTP 201 ─────────────────────

  it('AC-1a: POST /api/v1/contact-submissions returns HTTP 201', async () => {
    const response = await request(BASE_URL)
      .post('/api/v1/contact-submissions')
      .set('Content-Type', 'application/json')
      .send({
        name: 'Smoke Test Bot',
        email: `smoke-${Date.now()}@test.chinh.dev`,
        message: 'Automated smoke test contact submission',
        referralSource: 'direct',
      })
      .expect(201);

    expect(response.body).toBeDefined();
    submissionId = response.body?.id ?? null;
    console.log(`  + Contact submission created -- id: ${submissionId}`);
  });

  // ── AC-1b: GET /api/v1/admin/analytics → ≥ 1 contact submission ─────────

  it('AC-1b: GET /api/v1/admin/analytics returns HTTP 200 with contact submissions ≥ 1', async () => {
    if (!adminToken) {
      console.warn('  ! SMOKE_TEST_ADMIN_TOKEN not set -- skipping analytics assertion.');
      return;
    }

    const response = await request(BASE_URL)
      .get('/api/v1/admin/analytics')
      .set('Authorization', `Bearer ${adminToken}`)
      .expect(200);

    expect(response.body).toBeDefined();
    const body = response.body as { summary?: { totalSubmissions: number } };
    const total = body.summary?.totalSubmissions ?? 0;
    console.log(`  + Admin analytics -- totalSubmissions: ${total}`);
    expect(total).toBeGreaterThanOrEqual(1);
  });

  // ── AC-3: CI failure on non-zero exit ────────────────────────────────────
  // Implicitly validated by CI workflow exit code.

  afterAll(async () => {
    console.log('  + Smoke test suite complete (no cleanup needed)');
  });
});
