# WEGLOW Navigation Map — Existing Baseline

Phase 1 records the current flow but does not redesign navigation (Phase 3).

```text
loading -> login
login -> home
login -> signup
signup -> age_selection -> skin_type -> gender_selection -> skin_sensitivity -> welcome_intro -> home

Main tabs:
home <-> discover <-> scan <-> routines <-> profile

scan -> scan_results
scan -> hairstyle_results
scan_results -> routines
profile -> login (current UI navigation only; real logout is Phase 4)
```

## Known navigation debt

- Routes are raw strings.
- Startup does not resolve session/onboarding state.
- Authentication success is not currently a prerequisite for Home navigation.
- Logout navigation does not currently prove session invalidation.

These are intentionally documented rather than implemented in Phase 1.
