# Task 2 — Server-Side Hairstyle Filtering Fix

**Scope:** Fix ONLY the Supabase hairstyle recommendation query so matching recommendations are filtered server-side instead of downloading the entire `hairstyles` table and filtering it in the Android app (`docs/ARCHITECTURAL_VIOLATIONS.md`, item 4). No other architectural finding was touched.

---

## 1. Was the original issue confirmed on current main?

Yes. `git log` showed no commits to the hairstyle files since the one already reviewed (`0edc4ef`), and a fresh read of `SupabaseHairstyleRepository.kt` confirmed the exact pattern flagged: `client.postgrest["hairstyles"].select().decodeList<JsonObject>()` fetched every row, then `matchingHairstyles()` filtered by face shape/gender in Kotlin.

## 2. What exactly was the old query doing?

`select()` with no filter — the entire `hairstyles` table, every column, every row, on every single scan — followed by `check(rows.isNotEmpty())` (a "table isn't totally empty" smoke test) and then in-memory filtering (`row.text("face_shape")?.equals(...)`, `matchesGender(...)`).

## 3. Exact root cause

Predicate logic (face-shape equality, gender/synonym matching) lived entirely in Kotlin instead of being pushed into the PostgREST query, so the client had to download rows it would immediately discard.

## 4. Exact Supabase query after the fix

```kotlin
client.postgrest["hairstyles"]
    .select {
        filter {
            ilike("face_shape", savedShape)
            if (normalizedGender == "male" || normalizedGender == "female") {
                or {
                    ilike("gender", normalizedGender)
                    genderSynonyms(normalizedGender).forEach { synonym -> ilike("gender", synonym) }
                }
            }
        }
    }
    .decodeList<JsonObject>()
```

Plus a fallback existence check, used only when that filtered fetch comes back empty:

```kotlin
client.postgrest["hairstyles"].select { head = true; count(Count.EXACT) }.countOrNull()
```

## 5. Exact server-side filters used

- `face_shape ilike '<savedShape>'` — always applied. PostgREST `ilike` with no wildcard characters is a case-insensitive exact match, which is the direct server-side equivalent of the previous `row.text("face_shape")?.equals(faceShape, ignoreCase = true)`.
- `gender ilike '<normalizedGender>' OR gender ilike '<synonym1>' OR ...` — applied only when the profile's gender is determinate (`male`/`female`); omitted entirely otherwise, exactly matching the old `normalizedGender !in setOf("male","female")` short-circuit (no gender filter → all genders for that face shape).

These are the real, currently-used column names, verified by reading `toHairstyleRecommendationOrNull()` (`face_shape`, `gender`, `hairstyle_name`, `hair_length`, `image_url`) and the passing test fixtures in `SupabaseHairstyleRepositoryMappingTest.kt`, which use identical column names and Title-Case values (`"Round"`, `"Male"`). There is no `CREATE TABLE public.hairstyles` migration in this repo (the table predates the migrations folder — only `20260913000000_allow_authenticated_hairstyle_reads.sql`, an RLS/grant migration, references it), so the column names used by the existing, already-shipped mapping code are the only authoritative source of the current schema. No schema change was needed or made.

## 6. How face shape/gender values are normalized/matched

- **Face shape:** server-side `ilike` (case-insensitive exact match) against `profile.faceShape` (the value the on-device classifier wrote: `"Heart"`/`"Oblong"`/`"Oval"`/`"Round"`/`"Square"`).
- **Gender:** `profile.gender` is lowercased/trimmed; if it's exactly `"male"` or `"female"` (the app only ever writes `"Male"`, `"Female"`, or `"Other/Prefer not to say"` from `GenderSelectionScreen`), the query OR's the exact value with its accepted spellings. The synonym lists that already existed in `matchesGender` were extracted into one shared `internal fun genderSynonyms(normalizedGender)` (`"male"` → `men, mens, man`; `"female"` → `women, womens, woman`) so the new server-side query and the existing client-side `matchesGender` check can never drift out of sync — this was the one small addition beyond the query itself, required for correctness consistency. Any other gender value (including `"Other/Prefer not to say"` or null) applies no gender filter, same as before.
- After the fetch, `matchingHairstyles(rows, savedShape, profile.gender)` still runs on the (now much smaller) result as a defense-in-depth pass — same tested function, unchanged behavior, now just operating on pre-filtered data instead of the whole table.

## 7. How zero matching rows are handled

This needed explicit handling because moving filtering server-side changes what an empty result *means*. Previously, `rows.isNotEmpty()` was checked against the *unfiltered* table, so it could tell "backend broken/empty" apart from "no styles for this shape." After filtering server-side, a naive port of that same check would have wrongly thrown "check database read access" for the fully legitimate case of a face-shape/gender combination that simply has no catalogued styles yet — a real regression the task's correctness requirements explicitly warned against.

**Fix:** the `rows.isNotEmpty()` check was removed from the filtered fetch. Instead, only when the filtered fetch returns empty does `ensureHairstylesTableIsReadable()` run — a `head: true` + `count(Count.EXACT)` request that transfers zero rows and only checks whether the table has *any* data at all:

- Filtered fetch has rows → proceed normally (happy path — zero extra cost).
- Filtered fetch is empty, but the table has other rows → returns `recommendations = emptyList()`, no exception, no fabricated data (same outcome as current main today for this case).
- Filtered fetch is empty and the table itself has zero rows → throws the original `"Hairstyles are unavailable. Please check database read access."`, preserving the original diagnostic.

## 8. Files modified

- `app/src/main/java/com/example/weglow/data/repository/SupabaseHairstyleRepository.kt`
- `app/src/test/java/com/example/weglow/data/repository/SupabaseHairstyleRepositoryMappingTest.kt`

## 9. Tests added/changed

- Added `matchesGenderSynonymSpellingsStoredInTheDatabase` — verifies a row stored with a synonym spelling (`"Men"`/`"Women"`) still matches the corresponding `"Male"`/`"Female"` profile gender, locking the exact value set the new server-side filter also uses.
- Added `genderSynonymsMatchTheValuesUsedForServerSideFiltering` — locks the literal synonym lists (`men, mens, man` / `women, womens, woman`) so the query-builder and the client-side check can't silently diverge later.
- Confirmed still passing (not duplicated) and already satisfying the task's "at minimum" list: `filtersByStoredShapeAndProfileGender` (matching face shape+gender returns only matching rows; unrelated rows excluded; zero-match case returns an empty list, not an exception) and `mapsLiveStyleFieldsAndImageUrl` (mapping unchanged).
- The actual network-level PostgREST call in `analyze()`/`ensureHairstylesTableIsReadable()` isn't unit-testable without introducing a mocked-HTTP-engine test harness that doesn't exist anywhere else in this codebase (every existing Supabase repository test in this project only exercises pure mapping/filter functions on local `JsonObject` fixtures) — adding that infrastructure would be a scope expansion beyond this task, so it was intentionally not done.

## 10. Gradle results

Had to set `JAVA_HOME` to Android Studio's bundled JBR — no system Java on `PATH`; environment note, not a code change.

| Command | Result |
|---|---|
| `./gradlew architectureCheck` | **BUILD SUCCESSFUL** |
| `./gradlew compileDebugKotlin` | **BUILD SUCCESSFUL** |
| `./gradlew testDebugUnitTest` | **BUILD SUCCESSFUL** |
| `./gradlew assembleDebug` | **BUILD SUCCESSFUL** |

## 11. `git diff --name-only`

```
app/src/main/java/com/example/weglow/data/repository/SupabaseHairstyleRepository.kt
app/src/test/java/com/example/weglow/data/repository/SupabaseHairstyleRepositoryMappingTest.kt
docs/ARCHITECTURE_REVIEW.md
```

`docs/ARCHITECTURE_REVIEW.md` is leftover from earlier in this session (unrelated to Task 2) — not touched by this task. Nothing was staged or committed.

## 12. Manual tests against the real Supabase database

1. Run a hairstyle scan for a real account whose profile gender is `Male`/`Female` and whose classified face shape has known catalogued rows — confirm the same recommendation cards appear as before this change (title, hair length, image).
2. Check your Supabase project's API logs (or a proxy) during a scan — confirm the request to `/hairstyles` now includes `face_shape=ilike.*` and (when gender is set) an `or=(gender.ilike...)` query string, and that the response body is now just the matching rows, not the whole table.
3. Temporarily test with a profile gender of `"Other/Prefer not to say"` — confirm recommendations for that face shape still appear across all genders (no gender filter applied), matching current behavior.
4. Pick/seed a face shape that currently has zero rows in `hairstyles` for the user's gender (but the table has other rows) — confirm the app shows an empty/no-recommendations state, not an error, and not a crash.
5. Temporarily rename or revoke SELECT on the `hairstyles` table (in a test project only) to simulate the table being unreadable/empty — confirm the app now shows "Hairstyles are unavailable. Please check database read access." exactly as before.
6. Test with a row whose `gender` column is stored as a synonym spelling (e.g. `"Men"` instead of `"Male"`) if any exist in your live data — confirm it still surfaces for a `Male` profile.

---

**Status:** Task 2 complete. Task 3 (the Local/Supabase repository responsibility split) and all other architecture cleanup were NOT addressed.
