# 1) Unused or effectively-unused code (still valid)

These are **semantic unuseds**, not syntax-related.

## 1.1 `PartyAdapter.onPartyRenameRequested`

*   Still **never invoked** anywhere in `PartyAdapter`
*   Passed through `PartyFragment` and `MainActivity`
*   Creates cognitive overhead with no behavior

**Recommendation**

*   Either remove it entirely, **or**
*   Wire it to a gesture (e.g., longpress party name distinct from edit)

This is still a legitimate cleanup opportunity.

***

## 1.2 `HpModifierDialogFragment.stayOpen`

*   Set via `newInstance`
*   Stored as a property
*   **Never consulted in logic**

Given how carefully the dialog handles:

*   auto-dismiss
*   disabling buttons
*   delayed close

…it’s clear `stayOpen` was intended to alter behavior but currently does not.

**Recommendation**

*   Either implement:
    ```kotlin
    if (!stayOpen) dismiss()
    ```
*   Or remove it to reduce mental load.

***

## 1.3 `showEditMemberDialog(fromLibrary: Boolean)`

*   Passed in several call sites
*   Never changes behavior inside the function
*   All delete logic keys only off `fromParty != null`

This is still dead signal.

**Recommendation**

*   Remove the parameter, or
*   Use it to differentiate:
    *   wording
    *   delete policy
    *   whether the member can be fully removed

***

## 1.4 `Member.lastToHitRoll`

*   Written nowhere
*   Read nowhere
*   Conceptually useful, but currently inert

**Recommendation**

*   Either populate it in `rollDamage()`, or
*   Remove until needed

This is a classic “future field that never arrived.”

***

# 2) Missing comments around *critical invariants*

Your code is generally well commented, but a few **behavioral invariants** are undocumented and important.

## 2.1 Clone identity rules (important!)

This logic is correct, but underdocumented:

```kotlin
if (member.cloneTag != 0.toChar()) return
```

This is a **core datamodel rule**:

*   `cloneTag == 0`  canonical library member
*   `cloneTag != 0`  ephemeral party-only instance

**Why this matters**
Someone refactoring `updateAllReferences()` later could easily “fix” this and break clone isolation.

**Strong recommendation**
Add a comment explaining:

*   why clones must not sync back
*   why ID equality alone is insufficient

***

## 2.2 DataManager caching contract

`DataManager` returns **mutable lists backed by an internal cache**, but persistence only happens on:

*   property reassignment
*   `saveAll()`

This is subtle and non-obvious.

**Risk**
A future contributor mutates:

```kotlin
dataManager.memberLibrary.add(...)
```

…and assumes it’s persisted.

**Recommendation**
Document clearly:

*   “Returned lists are live but not auto-persisted”
*   Or refactor API to make persistence explicit

***

## 2.3 Import semantics & ID regeneration

Your import logic is careful and correct, but complex:

*   Members:
    *   matched by ID
    *   then by name
*   Parties:
    *   matched only by name
    *   always re-ID’d
    *   members re-ID’d
    *   forced inactive

This is **policy**, not just implementation.

**Recommendation**
Add a short header comment describing:

*   conflict rules
*   why parties get new IDs
*   why imported parties are inactive

This will save future-you real time.

***

# 3) Architectural improvements (not bugs)

These are about robustness, performance, and maintainability.

## 3.1 Nested RecyclerView adapter recreation

In `PartyAdapter.onBindViewHolder()`:

*   A new `MemberAdapter` is created every bind

This works, but:

*   prevents view recycling optimizations
*   resets nested scroll state
*   costs allocations

**Improvement**
Create `MemberAdapter` once per `PartyViewHolder` and update its list.

This is a known RecyclerView best practice.

***

## 3.2 `notifyDataSetChanged()` everywhere

Both adapters use brute-force refreshes.

**Impact**

*   No animations
*   Worse performance on large lists
*   Harder to debug UI churn

**Improvement**
Use `ListAdapter + DiffUtil`.

Given your data classes already have stable IDs, this is low effort / high payoff.

***

## 3.3 Hardcoded UI strings

Despite a solid [strings.xml](https://ametekinc-my.sharepoint.com/personal/wayne_taylor_ametek_com/Documents/Microsoft%20Copilot%20Chat%20Files/strings.xml?EntityRepresentationId=3fd6e876-6175-4595-a905-fcb554e411e4), many strings are still inline:

*   Dialog titles
*   Button labels (“DEL”)
*   Event log messages
*   Confirmation text

**Recommendation**
Move remaining strings to resources:

*   improves consistency
*   enables localization
*   reduces magic text

***

## 3.4 Gesture thresholds in raw pixels

Round counter reset uses a hardcoded pixel delta.

**Improvement**
Scale by density (`dp  px`) for device independence.

***

# 4) Dice / segment logic consistency (design-level)

Even assuming all regexes are correct:

*   Dice parsing
*   HP roll parsing
*   Damage roll parsing
*   UI segment splitting

…are implemented **in multiple places** with similar-but-not-identical logic.

**Risk**
Future fixes must be applied in several files.

**Recommendation**
Centralize:

*   delimiter definition
*   segment splitting
*   formatting helpers

This would materially reduce maintenance cost.

***

# 5) What’s notably *good* (worth keeping as-is)

I want to call these out explicitly:

*    Clear separation of concerns (UI vs domain vs persistence)
*    Thoughtful UX details (haptics, long-press affordances)
*    Clone system is simple and effective
*    Event log is lightweight and decoupled
*    Dice UI is unusually polished for a utility app
*    Import conflict handling is user-friendly and safe

This is **well-structured senior-level Android code**, not a toy project.

***