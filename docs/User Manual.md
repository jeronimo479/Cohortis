# Cohortis – User Manual (Text-Based)
version 2026-03-27

## 1. Overview

**Cohortis** is a tabletop RPG combat and party management tool.  
It allows you to:

*   Maintain a global **Member Library** (PCs, NPCs, monsters)
*   Create and manage **Parties**
*   Track **Hit Points**, **Armor Class**, **THAC0**
*   Perform **dice rolls** (HP, damage, attacks)
*   Track **combat rounds**
*   Maintain a rolling **event log**


***

## 2. Main Screen Layout

The main screen is divided into three conceptual areas:

    +------------------------------------------------+
    | Top Bar                                        |
    |  - <wrench> Settings                           |
    |  - (+) Add Party                               |
    +------------------------------------------------+
    | Party Area (main - upper)                      |
    |  - One or more Parties                 ________|
    |  - Each Party contains Members        / Round  |
    +---------------------------------------+ Counter|
    | Event Log (bottom)                    \________|
    |  - Rolling log of actions                      |
    +------------------------------------------------+

***

## 3. Parties

### What is a Party?

A **Party** is a collection of Members (PCs, NPCs, monsters).

Each Party has:

*   Name
*   Active flag (shown in main view)
*   Priority flag (sorted to top)
*   Members list

### Creating a Party

*   Tap **“Add Party”**
*   Enter name
*   Optionally:
    *   Set Active
    *   Set Priority
    *   Add Members from Library
*   Tap **Create**

### Editing a Party

*   Tap **Party Name**
*   Options:
    *   Rename party
    *   Toggle Active
    *   Toggle Priority
    *   Copy party
    *   Delete party

### Priority Party

*   Only one party can be Priority at a time
*   Priority party is always displayed first
*   Used to highlight the “current focus” party


## 4. Members

### What is a Member?

A **Member** represents:

*   A Player Character (PC)
*   An NPC
*   A monster

Members may appear:

*   In the global **Member Library**
*   In one or more Parties
*   As **clones** (multiple instances of the same monster)

### Member Attributes

*   Name
*   PC / NPC flag
*   Class / Level (PCs)
*   Hit Dice
*   HP Full / HP Current
*   THAC0
*   Armor Class
*   Damage Rolls
*   Special Detections / Attacks

***

## 5. Member Library

### Opening the Member Library

*   From a Party: tap **“Add Member”**
*   From Party Library or dialogs

### Member Library Actions

*   **Tap member** → add to party
*   **Long‑press member** → edit member
*   **Create Member** → adds a new blank member

### Cloning Members

When adding from library to a party:

*   Set clone count (0–36)
*   Clones are given unique tags (`a)`, `b)`, `1)`, etc.)
*   Each clone has independent HP

***

## 6. Hit Point Management

### Tapping HP

*   Tap a Member’s HP to open **HP Modifier Dialog**

### HP Modifier Dialog

Calculator-style interface:

*   Digits `0–9` build a number
*   `+` applies healing
*   `–` applies damage
*   Tap result box to **set HP directly**

Behavior:

*   Healing usually keeps dialog open
*   Damage usually closes dialog
*   HP is clamped to safe bounds

### HP Full vs HP Current

*   From Party view → modifies **HP Current**
*   From Edit Member → modifies **HP Full**

***

## 7. Dice Rolling

### Damage Rolls

*   Members have damage strings (e.g. `1d6 | 1d4`)
*   Each segment is clickable
*   Tapping performs:
    *   d20 roll
    *   Damage roll
    *   Logged with visual markers

### Hit Dice / Dice Editor

editDiceRolls should use the the following:
+----------+----------+
|       <Title>       |
|  <diceRollsString>  |
|                     |
| [N]x[X]d[Y](+/-)[Z] | <- Dice Segment
|                     |
|      7  8  9  |     |
|      4  5  6  ,     |
|      1  2  3        |
|     Del 0 (OK)      |
|                     |
+----------+----------+

where:
 diceRollString = NxXdY+Z(,|)NxXdY+Z written using concise notation described as:
If N=1 don't include "Nx".
If X=1 don't include "X".
If Z=0 don't include +/-Z
 
Caller function should specify if comma is allowed, if X or Y field is active.

Tapping Del should delete last digit of active field until zero is reached.
Long-press Del should splash dialog to delete dice roll segment.

A Dice roll is defined as NxXdY+Z. A dice segment is one or more Dice rolls separated by a comma. Two or more dice roll segments are separated by a Pipe '|'

Segments shall be selected by tapping the segment in the diceRollsString display.

Only the active field will be highlighted and you will use light blue background.

***

## 8. Round Counter

Located at the bottom overlay.

*   Displays current round number
*   Persists between sessions

***

## 9. Event Log

*   Displays recent actions (HP changes, rolls, round changes)
*   Supports formatted spans (colored dice circles)
*   **Long‑press event area** → full-screen history

***

# Gesture & Interaction Reference (Complete)

## Global / Main Screen

| Gesture | Where     | Action               |
| ------- | --------- | -------------------- |
| Tap     | Settings  | Open settings dialog |
| Tap     | Add Party | Create new party     |

***

## Party Area

| Gesture    | Target     | Action                 |
| ---------- | ---------- | ---------------------- |
| Tap        | Party Name | Open party edit dialog |
| Tap        | Add Member | Open member library    |
| Long‑press | Party Name | (currently unused)     |

***

## Member Item

| Gesture    | Target         | Action              |
| ---------- | -------------- | ------------------- |
| Tap        | HP value       | Open HP modifier    |
| Long‑press | HP value       | Open member edit    |
| Long‑press | Member row     | Open member edit    |
| Tap        | Damage segment | Roll damage         |
| Tap        | Special icon   | Show special splash |

***

## HP Modifier Dialog

| Gesture | Target     | Action            |
| ------- | ---------- | ----------------- |
| Tap     | Digit      | Append digit      |
| Tap     | Clear      | Reset accumulator |
| Tap     | +          | Apply healing     |
| Tap     | –          | Apply damage      |
| Tap     | Result box | Set HP directly   |

***

## Round Counter

| Gesture                 | Action            |
| ----------------------- | ----------------- |
| Single tap              | Advance round     |
| Double tap              | Decrement round   |
| Long‑press              | Arm reset         |
| Long‑press + swipe left | Reset rounds to 0 |

***

## Event Log

| Gesture    | Action            |
| ---------- | ----------------- |
| Long‑press | Open full history |

***

## Dice Roller Dialog

| Gesture             | Action                |
| ------------------- | --------------------- |
| Tap field (N/X/Y/Z) | Select field          |
| Tap digit           | Append digit          |
| Long‑press 0        | Reset segment         |
| Tap comma           | Add new segment       |
| Long‑press page     | Insert segment        |
| Swipe / Next        | Move between segments |

***

# Fragment & Dialog Interaction Tree

    MainActivity
    │
    ├── PartyFragment
    │   └── PartyAdapter
    │       └── PartyViewHolder
    │           └── MemberAdapter
    │               └── MemberViewHolder
    │                   ├── HpModifierDialogFragment
    │                   │   └── DiceRoller
    │                   └── showSpecialSplash (AlertDialog)
    │
    ├── EventFragment
    │   ├── addLog()
    │   └── Fullscreen History Dialog
    │
    ├── Dialogs
    │   ├── HpModifierDialogFragment
    │   │   └── SwipeDiceRollerDialogFragment
    │   │       └── DiceRoller
    │   │
    │   ├── SwipeDiceRollerDialogFragment
    │   │
    │   ├── Member Edit Dialog
    │   │   └── HpModifierDialogFragment
    │   │
    │   ├── Party Edit Dialog
    │   ├── Party Library Dialog
    │   ├── Member Library Dialog
    │   └── Settings Dialog
    │
    ├── DataManager
    │   ├── Member Library
    │   ├── Party Library
    │   ├── Active Parties
    │   └── Round Counter
    │
    └── DiceRoller
        ├── parseCombo()
        ├── rollSegmentTotal()
        └── rollDamageSegmentDetailed()

***

# Test Guide (Manual QA)

## Core Functional Tests

### Persistence

*   Create party → restart app → party exists
*   Modify HP → restart app → HP preserved
*   Change round → restart app → round preserved

### Party Logic

*   Only one priority party at a time
*   Active parties update immediately
*   Deleting party removes it everywhere

### Member Logic

*   Editing member updates all references
*   Clones have independent HP
*   Deleting library member removes it

### HP Logic

*   HP never exceeds 999
*   hpCurrent can go negative to ‑9
*   hpFull can go to 0
*   Healing reopens dialog
*   Damage closes dialog

### Dice Logic

*   Invalid dice strings do not crash
*   Multiple segments roll independently
*   Dice editor preserves formatting

### Event Log

*   Logs HP changes
*   Logs damage rolls
*   Long‑press shows history
*   Spans render correctly

***

