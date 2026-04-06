Below is the **complete Gesture Almanac rewritten as a single, structured Markdown table**, covering **every known gesture** and **exact reaction** across **all screens, fragments, dialogs, and windows** in the project.

This is intentionally **exhaustive and mechanical**—suitable for:

*   QA verification
*   Regression testing
*   UX documentation
*   Future refactors

# Cohortis — Gesture Almanac (Table Format)

| Context / Screen             | UI Element                 | Gesture                 | Result / Behavior                                       |
| ---------------------------- | -------------------------- | ----------------------- | ------------------------------------------------------- |
| **Main Screen**              | App Title                  | Tap                     | Opens **Member Library Manager** (fullscreen dialog)    |
| Main Screen                  | Settings Button            | Tap                     | Opens **Settings Dialog**                               |
| Main Screen                  | Add Party Button (+)       | Tap                     | Opens **Party Library Manager**                         |
| Main Screen                  | Add Party Button (+)       | Long-press              | Opens **Create New Party Dialog** (new active party)    |
| **Round Counter**            | Counter Card               | Single Tap              | Increments round (wraps 0–99); logs “Round X started”   |
| Round Counter                | Counter Card               | Double Tap              | Decrements round (if >0); logs “Round decreased”        |
| Round Counter                | Counter Card               | Long-press + Swipe Left | Resets round to 0; logs “Rounds Reset”                  |
| **Party List**               | Party Name                 | Tap                     | Opens **Edit Party Dialog**                             |
| Party List                   | Add Member Button (+)      | Tap                     | Opens **Member Library Manager** (targeting this party) |
| Party List                   | Add Member Button (+)      | Long-press              | Creates new blank member; opens **Edit Member Dialog**  |
| **Member Row**               | Member Name                | Long-press              | Opens **Edit Member Dialog** (party context)            |
| Member Row                   | HP Value                   | Tap                     | Opens **HP Modifier Dialog** (hpCurrent)                |
| Member Row                   | HP Value                   | Long-press              | Opens **Edit Member Dialog**                            |
| Member Row                   | Damage Segment             | Tap                     | Rolls attack & damage; logs detailed results            |
| Member Row                   | Special Icon               | Tap                     | Shows temporary special info dialog (auto-dismiss)      |
| **Event Log Preview**        | Log Area                   | Long-press              | Opens **Fullscreen Event Log Dialog**                   |
| **Fullscreen Event Log**     | Delete (“DEL”)             | Tap                     | Shows confirmation dialog (“Clear Log?”)                |
| Fullscreen Event Log         | Delete (“DEL”)             | Long-press              | Clears log immediately; closes dialog                   |
| **Member Library Manager**   | Member Item                | Tap                     | Adds member to target party (if provided)               |
| Member Library Manager       | Member Item                | Long-press              | Opens **Edit Member Dialog**                            |
| Member Library Manager       | Clone Count Picker         | Increment/Decrement     | Sets number of clones to add                            |
| Member Library Manager       | Create Member Button       | Tap                     | Creates new member; opens **Edit Member Dialog**        |
| **Edit Member Dialog**       | Delete (“DEL”)             | Tap                     | Removes member (from party) or deletes from library     |
| Edit Member Dialog           | Hit Dice Field             | Tap                     | Opens **Swipe Dice Roller Dialog**                      |
| Edit Member Dialog           | HP Full Button             | Tap                     | Opens **HP Modifier Dialog** (hpFull)                   |
| Edit Member Dialog           | HP Current Button          | Tap                     | Opens **HP Modifier Dialog** (hpCurrent)                |
| Edit Member Dialog           | Damage Rolls Field         | Tap                     | Opens **Swipe Dice Roller Dialog**                      |
| Edit Member Dialog           | OK Button                  | Tap                     | Saves changes; updates references; closes dialog        |
| **HP Modifier Dialog**       | Digit Buttons              | Tap                     | Appends digit to accumulator                            |
| HP Modifier Dialog           | Clear Button               | Tap                     | Resets accumulator to 0                                 |
| HP Modifier Dialog           | Plus Button                | Tap                     | Adds accumulator to HP; resets accumulator              |
| HP Modifier Dialog           | Minus Button               | Tap                     | Subtracts accumulator; auto-closes if party context     |
| HP Modifier Dialog           | Dice Segment               | Tap                     | Rolls dice; loads result into accumulator               |
| HP Modifier Dialog           | OK Button                  | Tap                     | Finalizes HP change; closes dialog                      |
| **Swipe Dice Roller Dialog** | Field Selector (N/X/Y/Z)   | Tap                     | Selects field for numeric input                         |
| Swipe Dice Roller Dialog     | Digit Button               | Tap                     | Appends digit to selected field                         |
| Swipe Dice Roller Dialog     | Plus/Minus Toggle          | Tap                     | Toggles modifier sign, activate Z field                 |
| Swipe Dice Roller Dialog     | Comma Button               | Tap                     | Adds comma-separated roll segment                       |
| Swipe Dice Roller Dialog     | Pipe Button                | Tap                     | Adds newline-separated roll segment                     |
| Swipe Dice Roller Dialog     | Delete Button              | Tap                     | Deletes last digit                                      |
| Swipe Dice Roller Dialog     | Delete Button              | Long-press              | Opens delete-roll confirmation                          |
| Swipe Dice Roller Dialog     | Roll Preview Segment       | Tap                     | Selects roll segment for editing                        |
| Swipe Dice Roller Dialog     | OK Button                  | Tap                     | Commits dice string; closes dialog                      |
| **Edit Party Dialog**        | Party Name Field           | Text Input              | Updates name; enables controls                          |
| Edit Party Dialog            | Active Checkbox            | Tap                     | Toggles party active state                              |
| Edit Party Dialog            | Priority Checkbox          | Tap                     | Sets/unsets priority party                              |
| Edit Party Dialog            | Remove Member Button (“X”) | Tap                     | Removes member from party                               |
| Edit Party Dialog            | Add From Library           | Tap                     | Opens **Member Library Manager**                        |
| Edit Party Dialog            | Create Member              | Tap                     | Creates new member and adds to party                    |
| Edit Party Dialog            | Reset All HP               | Tap                     | Sets all members’ hpCurrent = hpFull                    |
| Edit Party Dialog            | OK Button                  | Tap                     | Saves party changes; closes dialog                      |
| **Party Library Manager**    | Party Item                 | Tap                     | Toggles party active state                              |
| Party Library Manager        | Party Name                 | Tap                     | Opens **Edit Party Dialog**                             |
| Party Library Manager        | Create Party Button        | Tap                     | Opens **Create New Party Dialog**                       |
| **Settings Dialog**          | Version Info               | Tap ×3                  | Triggers **Master Reset**                               |
| Settings Dialog              | Import JSON Library        | Tap                     | Opens system file picker                                |
| Settings Dialog              | Export JSON Library        | Tap                     | Opens system save dialog                                |
| **Master Reset**             | Confirmation               | Implicit                | Clears all data; resets state; logs event               |

***

## Notes & Invariants

*   **All gestures are tap-first**; no drag-and-drop anywhere
*   **Long-press is always destructive or advanced**
*   **cloneTag does not affect gestures**—only display and sync logic
*   Event logging occurs implicitly for:
    *   HP changes
    *   Dice rolls
    *   Round changes
    *   System resets

