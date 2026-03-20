/** @type {import('jest').Config} */
module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  testMatch: ['**/smoke/**/*.test.ts'],
  transform: {
    '^.+\\.ts$': ['ts-jest', {
      tsconfig: {
        module: 'commonjs',
        target: 'ES2020',
        esModuleInterop: true,
        strict: false,
        skipLibCheck: true,
      },
    }],
  },
  testTimeout: 30000,
  verbose: true,
  // Smoke tests should fail fast — no single test should take > 30s
};
