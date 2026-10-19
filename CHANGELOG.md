# Changelog

All notable changes to WishWait are documented here, in order from newest to
oldest. Format loosely follows [Keep a Changelog](https://keepachangelog.com/).
Each entry's version matches the `versionName` uploaded to Google Play, and the
matching "what's new" copy for the Play listing lives in
[`docs/releases/`](docs/releases/).

## [Unreleased]

### Added
- Cooldown presets (24h/3d/7d/30d) next to the day stepper when adding a wish.
- Optional delivery fee, factored into a "true cost" shown on the verdict and
  wish detail screens.
- Monthly bills field and budget-aware purchase advice (% of free budget a
  purchase would use).
- Savings goal progress bar on the Wins screen.
- Three new achievements (Early Saver, Second Thoughts, Week of Calm) and a
  30-day activity calendar on the Wins screen.

### Fixed
- Edge-to-edge system bars now use explicit, non-deprecated styling
  (`SystemBarStyle` + theme attrs) instead of framework defaults, resolving
  the Play Console pre-launch warning for Android 15+.

## [1.0.0] - 2026-10-12

### Added
- Initial release: real-cost verdict, smart cooldown, countdown + reminders,
  want-o-meter, daily reflection questions, what-if calculator, wins/badges,
  regret check, worth-it tracker, backup & restore, installment tracker,
  share-to-WishWait, savings goal, payday & sale-day awareness, home screen
  widget, wants-per-cutoff tracking, need-or-want labeling, optional tip
  support via Google Play Billing, and optional Google Drive backup.
