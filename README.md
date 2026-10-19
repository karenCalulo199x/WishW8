# WishWait 🦉⏳

**Your honest shopping buddy.** Before you tap "Buy", tell Ollie the owl. WishWait shows what the item *really* costs you (paychecks and hours of work), suggests a cooling-off period, reminds you, and celebrates every smart decision — buy it mindfully or let it go and keep the money.

No account. No AI. No ads. No servers. Everything stays on the phone.

## Features

| | |
|---|---|
| **Real-cost verdict** | Price → × monthly income, work hours, work days, months of saving, plus a "day → year of pay" impact meter |
| **Smart cooldown** | 1 day for a treat → 30 days for months of salary. Real needs halve the wait; risky moods add 50% |
| **Live countdown + reminders** | Ring countdown, a halfway check-in and a "time to decide" notification (survives reboot) |
| **Want-o-meter** | Daily 1–5 desire check-in with a chart — watch the hype fade |
| **Daily reflection question** | "Would you still want it at full price?" and 7 more, rotating |
| **What-if calculator** | Save up first · Installment (with interest, warns above 20% of income) · Cost per use · Invest instead |
| **Wins** | Money kept by letting go, hours of work kept, mindful vs early buys, 8 badges |
| **Regret check** | After buying: "Glad you bought it?" + a reminder 14 days later if unanswered |
| **Learns from regret** | Regretted buys in a category add 3–7 days to future cooldowns in that category |
| **Worth-it tracker** | Tap "I used it" after buying; cost per use drops until it "pays for itself" (default goal: 1 hour of work per use) |
| **Edit wishes** | Fix name, price, category or note without losing the cooldown or history |
| **Backup & restore** | Export a JSON file (Drive, email, computer) and restore it on a new phone — still no account |
| **Friendly reminders** | Notifications only between 9 AM and 9 PM; payment reminders at 9 AM on the due date |
| **Installment tracker** | Months + interest → monthly payment, balance left, due-date reminders, "Paid off!" celebration |
| **Share to WishWait** | Share or paste a product link; name, price and store are filled in when the page allows it |
| **Savings goal** | One simple goal; money kept by letting go can be added in one tap |
| **Payday & sale days** | Knows your payday, double-date sales (1.1 … 12.12) and the shops' Payday Sales on the 15th and 30th (each can be switched off in Settings): +2 days on payday and sale days, +3 on 9.9–12.12 |
| **Best day to buy** | For a wish you still really want: buy on the coming sale, or wait for payday if the sale lands in the tight days before it. Faded wants get "let it go" instead. A 7 PM nudge the evening before |
| **Home screen widget** | Money kept, the next countdown (or "ready to decide"), sale-day heads-up and an "Ask Ollie first" button |
| **Wants per cutoff** | Cutoff pay = income ÷ paydays. Wants bought through WishWait are added up per cutoff against your line (Chill 40%, Balanced 30%, Strict 20%), checked when adding, planning the best day and buying |
| **Need or want** | A clear Need / Want choice on the add form and in Edit; a need must say why (broken, for work). Each wish card shows the label |
| **Support Ollie** | Optional one-time tips through Google Play Billing (Settings → About). Tips unlock nothing; a hidden "Ollie's Friend" badge appears after the first tip |
| **Google Drive backup (optional)** | "Sign in with Google" saves a backup to a hidden folder in the user's own Drive (`drive.appdata`), refreshed a couple of minutes after changes; restore on a new phone. Looks before writing, so a new phone never overwrites the backup it came to restore. No WishWait server |
| **Weekly backup reminder** | Optional nudge when no backup was saved for a week (only when Drive backup is off) |

### Behavioral strategies built in

- **Delayed gratification / 30-day rule** — cooldown scales with price weight.
- **Cost in hours of life** — prices translated into work time.
- **HALT check** — hungry, angry/stressed, lonely/down, tired (plus bored and hyped) lengthen the wait.
- **Sale & social-pressure flags** — "Would you buy it at full price?", "Is this for you or for them?"
- **Desire tracking** — seeing feelings fade makes letting go easy.
- **Positive reinforcement** — letting go is celebrated (confetti!) and counted as money kept.
- **Opportunity cost & cost per use** — two lenses that make big buys clearer.

## How Ollie decides

One set of rules, used everywhere. No AI — every number below is in `domain/`.

**1. The wait (when you add a wish)**
1. Base days from the price tier (share of monthly salary): 1 → 30 days.
2. × strictness: Chill ×0.5, Balanced ×1, Strict ×1.5.
3. Real need — you pick **Need** and say why (broken / for work): ÷2. Risky mood: ×1.5 (at least +1 day).
4. Flat extras: regret history in this category +3–7 days.
5. Flat extras **for wants only**: sale day +2 (9.9–12.12: +3), payday window +2, past your wants line for this cutoff +3.
6. A calm small treat (Chill or a real need) can be 0 days — no extras then. Cap: 90 days.

**2. Wants per cutoff**
- Cutoff = payday up to the next payday (no payday set → the calendar month). Cutoff pay = monthly income ÷ number of paydays.
- Counts wants bought through WishWait in that cutoff; installments count by due date instead of the full price. Needs never count.
- Line = strictness: Chill 40%, Balanced 30%, Strict 20% of cutoff pay.
- Checked at three moments: adding a wish (+3 days), planning the best day (spread wishes across cutoffs), and tapping "I bought it" (a gentle "wait for payday / buy anyway").

**3. Best day to buy** (wish ready or ready within 3 days, a sale within 7 days)
- Faded want (latest check-in lower than the first) → "maybe let it go". Never for needs.
- Real want (latest 4–5, or not lower than the first) → the coming sale; or payday if the sale is in the 3 tight days before payday.
- Two wishes on the same day that together pass the line → the one you've wanted longest keeps the day, the other moves to the next cutoff.
- Evening nudge at 7 PM before that sale or payday.

**4. Always**
- Ollie suggests; he never blocks, and you always have the final say.
- He only knows what you run through WishWait, and says so.
- Notifications only between 9 AM and 9 PM.
- Every place that gives advice reads the same rules: the verdict, the wish screen, the widget, the "time to decide" and evening notifications, and the installment calculator (its warning uses your wants line).
- No payday set: the month is the cutoff, and Ollie says "this month" instead of "this cutoff".
- Changing currency with a rate converts everything together: wishes, income, savings, installments and the savings goal.

## Architecture

Clean Architecture + MVI, single `:app` module, manual DI.

```
domain/        Pure Kotlin. Models, repository interfaces, use cases (no Android imports)
  model/       Wish, UserProfile, Tier, PriceImpact, Verdict, Stats, Achievement…
  usecase/     CalculatePriceImpact, BuildVerdict, AffordabilityCalculator, ComputeStats, Create/Decide/Extend…
data/          DataStore (wishes as JSON), AlarmManager reminders, notifications
di/            AppContainer — every dependency wired in one place
presentation/
  mvi/         MviViewModel<State, Intent, Effect>
  onboarding/ home/ addwish/ detail/ calculator/ wins/ settings/
               each = Contract (State/Intent/Effect) + ViewModel + Screen
  components/  CountdownRing, ImpactMeter, FriendBubble, DesirePicker, DesireChart, Confetti…
  theme/       Material 3 light/dark color schemes, type, shapes
```

Data flow: `Screen → onIntent(Intent) → ViewModel → UseCase → Repository → Flow → State → Screen`. One-off events (navigate, snackbar) go out as `Effect`s.

## Stack

Kotlin 2.1.20 · AGP 8.10.1 · Gradle 9.3.1 · Compose BOM 2025.10.00 (Material 3 1.4) · Navigation Compose 2.9.5 · Lifecycle 2.9.4 · DataStore 1.1.7 · minSdk 26 · target/compile 36.

No KSP/kapt, no Hilt, no Room — fast builds, nothing to configure.

## Translations & accessibility

All text lives in `app/src/main/res/values/strings.xml`. To add Filipino, copy it to `values-fil/strings.xml` and translate the values (keep the `%1$s` placeholders). Emoji-only controls have TalkBack labels, and section titles are marked as headings.

## Known limitations

- **Shopee's share panel** ("Share Links to Earn Commission") only lists partner apps, so WishWait can't appear there. Use **Copy Link** → **Paste product link** in WishWait.
- **Reading details from a link** works when the shop's page exposes its product name/price. Shopee often serves a generic page or a verification check to unknown browsers, and TikTok Shop shares only a link — in those cases WishWait leaves the fields blank (never fills a wrong name) and puts the cursor there.
- Share text is parsed with rules, not AI; unusual share formats may need a quick edit.

## Run

Open the folder in Android Studio → let Gradle sync → Run ▶︎.
Unit tests: `./gradlew test` (domain math + JSON storage).

## Before publishing to Google Play

### Release build (upload key + signed bundle)
1. Android Studio → **Build → Generate Signed App Bundle or APK → Android App Bundle → Create new…**
   Save the key **outside** the project, e.g. `~/AndroidStudioProjects/keys/wishwait-upload.jks`, alias `upload`.
   Back up the .jks file and both passwords (password manager). Play App Signing protects the app key, but losing the upload key means a reset request to Google.
2. Copy `keystore.properties.example` to `keystore.properties` and fill it in (it is git-ignored).
3. Build: `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`. Upload that to Play.
4. Every new upload: raise `versionCode` by 1 (and `versionName`) in `app/build.gradle.kts`.
5. Smoke-test a release build before uploading (R8 is on): `./gradlew installRelease`, then add a wish, restart the app (data still there), export/import a backup, Sign in with Google, open Support Ollie.
   For Google sign-in on a locally installed release build, add an Android client with the **upload key** SHA-1 (`./gradlew signingReport` → variant `release`). Builds installed from Play need the **app signing key** SHA-1 from Play Console → App integrity.

### Google Drive backup setup (Google Cloud Console, free)
Sign-in is tied to the app's package name and signing key, so there is no client ID or secret in the code.
1. Go to console.cloud.google.com and create a project (e.g. "WishWait").
2. APIs & Services → Library → enable **Google Drive API**.
3. Google Auth Platform → **Branding**: app name WishWait, support email, app logo, and the privacy policy link. **Audience**: External. **Data access**: add the scope `.../auth/drive.appdata` (it is non-sensitive).
4. **Clients** → Create client → **Android**: package `com.karencalulo.wishwait` and a SHA-1 fingerprint. Make one client per key:
   - Debug key: run `./gradlew signingReport` in Android Studio's terminal and copy the SHA1 of the `debug` variant.
   - Release: Play Console → Test and release → App integrity → **App signing key certificate** SHA-1 (and the upload key SHA-1 for local release builds).
5. Audience → **Publish app** before release. While in "Testing", only test users you add can sign in.
Until this is set up, "Sign in with Google" shows "Couldn't connect to Google Drive" instead of crashing.

### Tip jar setup (Play Console)
Store listing text: see `docs/store-listing.md`.

1. Set up a payments profile / merchant account (Play Console → Monetize → payments) so Google can pay you.
2. Create three **one-time products** (Monetize → Products → One-time products) with these exact IDs, mark them **consumable** where the console asks, and set prices:
   - `ollie_tip_small` – A little thanks (e.g. ₱49)
   - `ollie_tip_coffee` – Coffee for Ollie (e.g. ₱99)
   - `ollie_tip_lunch` – Lunch for Ollie (e.g. ₱199)
3. Upload a build to the internal testing track and add yourself as a license tester to try tips without being charged. Tips only load for builds installed from Google Play.
4. Privacy policy: `docs/privacy-policy.html` (host it and paste the link in App content → Privacy policy).


1. Create an upload keystore (Build → Generate Signed App Bundle) — keep it out of git.
2. Bump `versionCode` / `versionName` in `app/build.gradle.kts`.
3. Build a release bundle: `./gradlew bundleRelease`.
4. Data safety form: the app collects **no data** (everything is local).
5. Play's 14-day closed test with 12+ testers is required for new personal developer accounts.

## Release process (versioning & changelog)

Every version uploaded to Play follows the same steps, so GitHub history
always mirrors Play Console history:

1. Bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`.
2. Add an entry to [`CHANGELOG.md`](CHANGELOG.md) under the new version
   (move anything from `[Unreleased]` into it).
3. Write the user-facing "what's new" copy in `docs/releases/<versionName>.md`
   — this is what gets pasted into Play Console's release notes.
4. Build and smoke-test the release bundle (see above).
5. Commit, then tag the commit: `git tag -a vX.Y.Z -m "vX.Y.Z - <short summary>"`
   and `git push origin vX.Y.Z`.
6. Upload the bundle to the relevant Play track and paste in the what's-new
   copy from step 3.
