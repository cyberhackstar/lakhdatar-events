# Running this repo on the GitHub Free plan

A private repo gets 2,000 free Linux runner minutes per month. Heavy jobs burn them quickly:
CI on `main` builds and scans three multi-architecture images (about 25-30 min wall-clock across jobs), an
Enterprise Release Qualification run is roughly 50 min plus a 45 min load job, and every Dependabot PR used to
trigger a full CI run.

What the repo now does to stay inside the free tier:

- Dependabot: monthly, grouped minor/patch only, at most 1-2 open PRs per ecosystem, no major-version jumps.
  Security updates are still opened by GitHub.
- CI skips documentation-only changes (`**/*.md`, `docs/**`, `**/*.txt`).
- CodeQL runs on pushes to `main`, not on every pull request.
- `enterprise-e2e-staging` no longer runs on a nightly schedule (run it manually).

Working rules:

1. Batch changes. Merge several fixes into `main` in one PR instead of one PR per tweak.
2. Run Enterprise Release Qualification once per release candidate, only after Staging Deploy and E2E pass.
3. Deploy and qualify a SHA that changed code. A docs-only commit does not build images, so it has no signed images.
4. Check Settings -> Billing and plans -> Usage this month before starting a big run.
5. If a run ends in 2-5 seconds with "recent account payments have failed or your spending limit needs to be
   increased", it never started: fix billing, do not change code.

Zero-minute options if the limit is hit mid-month: wait for the monthly reset, set a small spending limit, run a
self-hosted runner on your own Oracle VM (self-hosted runners do not use free minutes), or make the repository public
(standard runners are free for public repositories; review the repo for private content first).
