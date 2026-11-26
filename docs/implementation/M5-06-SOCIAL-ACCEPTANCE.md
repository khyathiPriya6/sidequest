# M5-06 — Full social acceptance and regression verification

Issue [#114](https://github.com/khyathiPriya6/sidequest/issues/114). Parent epic #6. Depends on merged [M5-03–05 PR #113](https://github.com/khyathiPriya6/sidequest/pull/113).

## Scope and evidence

The new `ui/e2e/social-real.spec.ts` runs Playwright against the isolated real PostgreSQL/API/Vite environment created by `backend/scripts/test-browser.sh`. It is marked `@auth-real` and skips normal UI-only runs unless `AUTH_E2E=1`.

**Social acceptance:** three independent registered browser sessions, PRIVATE default, exact same 404 as inaccessible/unknown social profile, authenticated-only social profile access, FRIENDS visibility before/after acceptance, real send/accept UI, stranger privacy, allowed response field whitelist, friends leaderboard (empty and weekly views), block and privacy downgrade, unblocking not reinstating relationships, forbidden friend re-request, CSRF failure and expired/non-authenticated access.

**Quest sharing:** real creator and moderator browser sessions, draft hidden from anonymous visitor, safety submission, moderator approval provisioned *only* through the disposable test database, creator publication, share button, anonymous published Quest link and unauthenticated join restriction. Existing E04 browser tests cover completed participation and XP; existing M4 and M5 PostgreSQL integration tests cover leaderboard reward aggregation and moderation restrictions.

## Run on the dedicated M5-06 worktree

Do not run migrations against an existing developer database. The browser harness creates and deletes its own disposable PostgreSQL cluster. Stop any dev Vite process using port 5173 first.

```bash
git fetch origin
git worktree add ../sidequest-m5-verify origin/test/m5-06-social-e2e
cd ../sidequest-m5-verify
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

(cd ui && npm ci && npm test && npm run build)
bash backend/scripts/test-postgres.sh
bash backend/scripts/test-browser.sh
```

For focused social browser verification:

```bash
bash backend/scripts/test-browser.sh social-real.spec.ts
```

For verification against the complete Java tests, run `mvn -f backend/pom.xml test` separately; PostgreSQL integration tests are intentionally run through the isolated script.

## Acceptance checklist

- [x] Add isolated real multi-account social browser test
- [x] Add approved-published Quest sharing browser test
- [x] Cover private default, friend consent, profile access, block/unblock, CSRF and guest access
- [ ] Record actual Java integration test counts and failures
- [ ] Record actual UI test and TypeScript/Vite build results on this branch
- [ ] Record actual Playwright test counts and failure traces
- [ ] Owner manually reviews friend mobile navigation, keyboard, contrast, messages, and accessibility
- [ ] Review privacy, rate limiting, event retention, and real-user abuse controls before deploying
- [ ] Approve/merge only after successful checks and review

**Verification status:** the connected GitHub editor added code but has **not executed** Java/PostgreSQL, TypeScript/Vite, or Playwright tests; none are represented as passing in this PR. Prior M5 results apply to earlier commits only. Test runs must be performed by the owner or CI and linked here.

## Rollback

This test-only PR introduces no schema migration, server authorization rule or production application behavior. Revert the new spec and verification note if needed; it must not affect running SideQuest. The browser harness provisions its own disposable database and moderator-only fixture.

## Teach-back

A browser E2E checks the full session and UI boundary, not only a Java service. Using three independent cookie jars proves a stranger cannot see a friend-only profile even while two other accounts can. Moderation is still enforced on share links: a URL makes a published Quest discoverable but does not publish a draft.

Questions:
1. Why must each E2E actor use a separate browser context?
2. Why does a friend-only profile still return 404 to a stranger who knows its URL?
3. Why is moderator provisioning restricted to the disposable test database?
