# Combined Progress

Implemented on `refining-the-app-for-playstore`. Production UI uses authenticated
Supabase records; synthetic data exists only in tests. The removed Home shortcut
cards remain removed. The former progress placeholder is now the progress chart.

## Calculation

`domain/progress/ProgressCalculator.kt` has no Android/UI/network dependencies.
For consecutive valid scans at `previous` and `latest`, the interval is
**(previous timestamp, latest timestamp]**:

- Signed change: `(previous count - latest count) / previous count * 100`.
- Scan points: `clamp(signed change, 0, 100) * 0.5`.
- Routine percentage: `completed distinct scheduled occurrences / due distinct
  scheduled occurrences * 100`.
- Routine points: `routine percentage * 0.5`.
- Combined score: sum of the two contributions, only when both are available.

50 → 25 concerns and 8/10 due tasks completed gives **25 + 40 = 65/100**.
An increase preserves the negative signed change and awards zero scan points.
Equal counts are a real zero improvement. A zero previous count makes relative
improvement/combined score unavailable, with separate explanations for zero/zero
and new concerns. Routine completion can still be shown independently.

Missing/negative/null counts, bad/future timestamps and absent detection settings
are excluded with an explanation. Incompatible adjacent valid scans form an
unavailable interval and the newer settings establish a fresh baseline; the
calculator never reaches back past that scan for a more favorable comparison.
The first valid scan is a baseline. No scheduled tasks or incomplete historical
routine coverage makes routine/combined values unavailable, not zero or perfect.

Only detected concern counts are used. Confidence is a detection filter, never
a severity score. New scans persist an exact fingerprint of the model hash,
labels, threshold, letterbox size, NMS settings and face-filter pipeline version.
Keep the pipeline version in `LocalAcneScanRepository` updated when processing
changes. Its returned confidence threshold now matches the actual 0.05 decoder
threshold. A faceless photo fails validation rather than becoming a zero-concern
scan. Retries use a deterministic per-account/per-capture scan UUID and an
ignore-duplicate upsert, so a retry cannot create another chart interval.

## Routine history and persistence

- `routine_schedules` stores plan/product snapshots, effective timestamps and an
  IANA time zone. The first plan starts when the server records it. No past
  schedule or device-only completion is fabricated during migration.
- Daily tasks are due at **08:00 for Morning and 20:00 for Evening** in that
  schedule's original time zone. The UI explains these times. Unmatched product
  slots are not scheduled tasks. Days when the app is closed still count.
- Changes take effect at the following midnight, preserving the current/past
  plan. A future revision may be replaced/cancelled before it takes effect.
- Occurrence identity is `(schedule_id, task_date, morning, step_index)`.
  Cloud writes set the desired boolean state, protected by that primary key.
  Repeating the same callback does not duplicate credit; unchecking removes it.
- Home and Routines share cloud-backed completion state. Historical calendar
  days display the corresponding saved plan, not today's generated routine.
  Future and pre-activation tasks cannot be checked. Old device-only checkmarks
  are not imported because their historical schedules cannot be verified.
- Daily routine completion stays independent of the combined chart. A checkbox
  interaction updates existing intervals, never creates a new scan point.
  Correcting a past due task's completion can update its existing interval;
  changing the routine plan cannot rewrite its scheduled denominator.
- Refresh on Home entry/resume, Routines entry, successful scan save and retry;
  local due-time refresh every 30 seconds does not create scan measurements.
- Reads are paginated instead of silently truncating history at the API row cap.
  Account changes cancel pending work and clear previous-account state.
- Daily notes retain the existing device-local journal storage.

The chart uses straight segments and visible points, with a fixed 0–100 vertical
scale and dated scan events on the horizontal axis. Unavailable intervals remain
gaps. Tap a point or its date for counts, signed change, completions,
contributions and interval timestamps. Latest worsening remains visible even
while inspecting an older point. The latest interval has a progress bar only
when a real combined score exists. All displayed scores are explicitly described
as engagement/progress indicators, not medically validated skin health scores.

## Database setup

Apply **`supabase/migrations/20261104000000_combined_progress.sql`** after the
existing user-data security migrations, before releasing this app version.
The migration is transactional and repeatable. It supports both the recorded
legacy scan-table shape without an `id` and the repository's UUID-id shape:

1. Adds `progress_scan_id` and nullable `detection_settings` to `Scan-profiles`.
   Existing counts/dates are preserved; legacy settings stay unknown.
2. Adds `routine_schedules` and `routine_task_completions` with owner indexes,
   RLS and SELECT-only authenticated grants. Both cascade on profile deletion,
   including the existing `delete_account_data` flow.
3. Adds two bounded authenticated RPCs, `register_routine_schedule` and
   `set_routine_task_completion`, with empty search paths and server-derived
   ownership. Anonymous execution and direct client table writes are denied.
   Completion RPC validation rejects future, unmatched, out-of-version and
   other-account tasks.

No Edge Function deployment, service-role key in Android, or new app secret is
needed. Do not rerun a consolidated setup over an existing database. The recorded
audit describes legacy schemas; hosted production state was not directly
inspected or migrated in this task. Install and verify this migration in staging
first, then the intended app backend. Old scans without comparable settings
cannot receive retrospective scores; tracking begins with real new records.

## Verification

JVM tests cover the 65/100 example, worsening, unchanged counts, zero baselines,
missing/invalid/incompatible scans, first scan, no routine tasks, zero completions,
interval boundaries, future exclusion, duplicate occurrences, signed changes,
consecutive comparisons, immutable schedule changes, stored time zones and
activation boundaries. ViewModel tests cover duplicate callbacks, unchecking,
failed writes/retry, reload persistence and account switches. SDK request tests
check explicit desired-state writes, product snapshots and owner-filtered reads.

Android instrumentation tests cover baseline UI, point selection/breakdown and
visible worsening, plus rejecting a faceless input with the actual bundled model.
The existing Home large-text test now targets the `Combined Progress` heading.

Verified locally: the 78-test JVM selection passed. After the final first-day
presentation adjustment, the 18 scoring/ViewModel tests passed again. Four
instrumentation tests passed on the connected Android 17 emulator: the two chart
tests, the bundled-model faceless-input test, and Home section reachability at
1.3 font scale. Both isolated database schema variants and the existing complete
migration/ownership/deletion runner passed. `git diff --check` passed.

Local database commands (PowerShell):

```powershell
npm install --prefix supabase/.temp/progress-tests --no-save --package-lock=false @electric-sql/pglite
$env:WEGLOW_PGLITE_MODULE = ([System.Uri](Join-Path (Get-Location) 'supabase/.temp/progress-tests/node_modules/@electric-sql/pglite/dist/index.js')).AbsoluteUri
node supabase/tests/progress.mjs
node supabase/tests/database.mjs
```

These use isolated in-memory databases, never hosted records. Checks cover both
scan schema shapes, repeatable migrations, legacy metadata preservation, duplicate
completion/uncheck behavior, due-time rejection, schedule immutability, anonymous
denial, cross-account isolation, direct-write denial and deletion cascades.
The existing full migration/ownership/deletion runner also passes.

The initial `:app:checkArchitecture` run failed on pre-existing ContentResolver
imports in the image helpers. Subsequent environment preparation corrected that
rule to permit ContentResolver within the intended `core/image` boundary only;
the architecture check now passes. The helper implementations remain unchanged.

## Temporary demonstration preview

Home offers clearly labeled Demo results when there are no real scored intervals,
with a Show my results / Show demo results toggle. The synthetic history lives in
`feature/progress/DemoProgress.kt` and uses ProgressCalculator without writing to
any repository. Real scored intervals are shown by default once available.
Sample scores are 40, 45, 60 and 65. The second interval worsens by 25% and earns
zero scan points; the latest uses 50 to 25 concerns and 80% routine completion
(25 scan points + 40 routine points = 65). Preview checkboxes do not alter these
sample intervals, saved completions or genuine user scores.
