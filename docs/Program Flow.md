# Flow Chart (with EditMemberDialog paths)

## High‑Level Structure (Revised)

```text
MainActivity
│
├─ PartyFragment
│  │
│  ├─ EditPartyDialog
│  ├─ HpModifierDialog (HP Current)
│  ├─ EditMemberDialog
│  │   ├─ HpModifierDialog (HP Full)
│  │   ├─ HpModifierDialog (HP Current)
│  │   ├─ SwipeDiceRollerDialog (Hit Dice)
│  │   └─ SwipeDiceRollerDialog (Damage Rolls)
│  │
│  ├─ SpecialSplashDialog
│  └─ MemberLibraryDialog
│      └─ EditMemberDialog
│
├─ CreatePartyDialog
│  └─ MemberLibraryDialog
│      └─ CreateMemberDialog
│         └─EditMemberDialog* 
│
├─ PartyLibraryDialog
│  ├─ EditPartyDialog
│  └─ CopyPartyDialog
│
├─ EventFragment
│  └─ EventHistoryDialog
│
└─ SettingsDialog
```

***

# ✅ Detailed Gesture‑Annotated Flow (Corrected)

## 🧩 PartyFragment (Main Hub)

```text
+----------------------+
|   PartyFragment     |
+----------------------+
        │
        ├─ (Tap Party Name)
        │        ↓
        │   EditPartyDialog
        │
        ├─ (Tap HP Value)
        │        ↓
        │   HpModifierDialog (HP Current)
        │
        ├─ (Long‑press Member Row OR HP)
        │        ↓
        │   EditMemberDialog
        │
        ├─ (Tap Special Icon)
        │        ↓
        │   SpecialSplashDialog
        │
        ├─ (Tap "Add Member")
        │        ↓
        │   MemberLibraryDialog
        │        └─ (Tap "Create Member")
        │                 ↓
        │           CreateMemberDialog
        │
        └─ (Tap Damage Roll Segment)
                 ↓
           rollDamage() → EventFragment
```

***

## 🧍 EditMemberDialog (THIS was missing before)

```text
+----------------------+
|  EditMemberDialog   |
+----------------------+
        │
        ├─ (Tap "Edit HP Full")
        │        ↓
        │   HpModifierDialog (HP Full)
        │
        ├─ (Tap "Edit HP Current")
        │        ↓
        │   HpModifierDialog (HP Current)
        │
        ├─ (Tap Hit Dice Field)
        │        ↓
        │   SwipeDiceRollerDialog (Hit Dice)
        │        │
        │        └─ (Commit)
        │                 ↓
        │           Rolls HP → Updates HP Full & Current
        │
        ├─ (Tap Damage Rolls Field)
        │        ↓
        │   SwipeDiceRollerDialog (Damage Rolls)
        │
        └─ (Tap Save)
                 ↓
           updateAllReferences()
           refreshActiveParties()
```

***

## 🎲 Damage Roll Flow (From *both* places)

```text
Damage Roll Trigger
│
├─ From PartyFragment
│   └─ (Tap damage segment)
│            ↓
│      DiceRoller.rollDamageSegmentDetailed()
│            ↓
│      EventFragment.addLog()
│
└─ From EditMemberDialog
    └─ (Tap Damage Rolls → edit)
             ↓
       SwipeDiceRollerDialog
             ↓
       Updated damage string
```

***

## 🧮 HP Modifier Dialog (Shared)

```text
HpModifierDialog
│
├─ Source: PartyFragment
│        → modifies HP Current
│
├─ Source: EditMemberDialog
│        → modifies HP Full OR HP Current
│
├─ (Tap digits)
│        → accumulator
│
├─ (Tap + / −)
│        → apply modifier
│
├─ (Tap result box)
│        → set value directly
│
└─ (Dismiss)
         → onApplied() callback
```

***

# ✅ Why this correction matters

You were **100% correct** to flag this because:

*   `EditMemberDialog` is a **major branching node**
*   It is the **only place** HP Full is edited
*   It is the **only place** dice strings are authored
*   It is the **bridge** between data definition and runtime behavior

Leaving those paths out would give a **false mental model** of the app.

***

# ✅ Summary (Explicit Fix)

✅ Added:

*   `EditMemberDialog → HpModifierDialog (HP Full)`
*   `EditMemberDialog → HpModifierDialog (HP Current)`
*   `EditMemberDialog → SwipeDiceRollerDialog (Hit Dice)`
*   `EditMemberDialog → SwipeDiceRollerDialog (Damage Rolls)`

✅ Clarified:

*   Damage rolls originate in **two places**
*   HP modification has **two distinct contexts**

***

If you want next, I can:

*   Produce a **single ASCII diagram** that fits on one screen
*   Convert this into **Mermaid flowchart syntax**
*   Or annotate **which transitions mutate state vs just navigate**

You were right to call this out — this is exactly the level of precision that keeps documentation honest.
