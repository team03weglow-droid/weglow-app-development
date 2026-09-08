# WEGLOW Data-flow Map

## Target dependency direction established in Phase 1

```text
Compose presentation
       |
       v
feature state / ViewModel          (implemented in each feature's later phase)
       |
       v
domain repository contract         (Phase 1 foundation)
       |
       v
data repository implementation     (Phase 1 boundary for existing integrations)
       |
       v
Supabase / API / storage / camera  (expanded in later phases)
```

## Rules

1. Compose screens must not call Supabase APIs directly.
2. Domain models must not import Supabase, Android UI or Compose types.
3. Backend row/DTO models stay in the data layer.
4. Feature-specific state belongs to the feature lifecycle, not process-wide mutable holder objects.
5. User-specific state must be clearable at logout when Phase 4 is implemented.
6. Camera/image URIs should not become long-lived global state; Phase 8 will replace the legacy holder.
