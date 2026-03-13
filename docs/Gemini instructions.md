# 1. Overview

1.1 Generate a complete Android app named Cohortis implementing all features in this specification.  
1.2 Use Kotlin, Material 3, the latest Android Studio, and a single‑Activity architecture with Fragments.  
1.3 Set minSdk = 23 (Android 6.0.1) and target/compile SDK = latest.  
1.4 Follow modern Jetpack best practices.  

# 2. Purpose of the Application

2.1 Cohortis is a party and encounter manager for tabletop RPGs.  
2.2 The application must allow creation and editing of Members (PCs, monsters, NPCs) and maintain a Member Library that includes PCs and Monsters.  
2.3 The application must allow creation and management of Parties that combine reference entries to library Members with per‑party cloned Members.  
2.4 The main encounter UI must show active parties and their members, provide a Round counter, and display an Event/Action Log.  
2.5 All data required by the app must persist using SharedPreferences with Gson serialization.

# 3. Data Models

3.1 Create models/Member.kt implementing the following data class:

```kotlin
enum class MemberType { PC, MONSTER }

data class Member(
    val id: java.util.UUID = java.util.UUID.randomUUID(),
    var type: MemberType,
    var name: String, // Limited to displaying 20 characters in the UI; longer names must be truncated with an ellipsis
    var thac0: Int, // range [1..20]
    var hpFull: Int,
    var hpCurrent: Int,
    var damageRolls: String,
    var specialDetections: String? = null,
    var specialAttacks: String? = null,

    // PCs only
    var classesAndLevels: String? = null,

    // Monsters only
    var hitDice: String = null, // max(1,[X]dY[(+|-)Z])  usually HD=d8
)
```

3.1.1 If `type` == `PC`, `classesAndLevels` must be provided.  
3.1.2 If `type` == `MONSTER`, `hitDice` must be provided.  
3.1.3 On creating a new Member, `hpCurrent` must default to `hpFull`.  
3.1.4 damageRolls  
3.1.4.1 A roll is written as "dX", which produces one random number from the set "[1..X]"; E.g. "d4" can yield 1, 2, 3, or 4.  
3.1.4.2 An attack is described as `"[<rolls>] d <faces> [(+|-) <modifier>]"`  
3.1.4.3 Cloning an attack is done by prepending `"[<attacks> x]"` E.g. "2x3d6+5", "3xd10+3", "2xd8".  
3.1.4.4 An attack combo, when mutliple attack occur at once, is described in `damageRolls` by placing a comma between attacks. E.g., "4xd8,d10" which represents four `d8` claw attacks and one `d10` bite.  
3.1.4.5 There can be exclusive attack combos where only one combo or the other is allowed. Exclusive attack combos in `damageRolls` are described using a lowercase 'l' or '|'. E.g., "3d6 l 8d6", "2xd8,d10 | 8d6".  

3.2 Create `models/Party.kt` implementing the following model using a unified `entries` list.

```kotlin
sealed interface PartyEntry {
    val displayName: String
}

data class MemberRef(
    val memberId: java.util.UUID, // points into memberLibrary
) : PartyEntry

data class MemberClone(
    val member: Member,
    val cloneTag: Char // NUL(0), 'a'..'z' and '0'..'9'
) : PartyEntry

data class Party(
    val id: java.util.UUID = java.util.UUID.randomUUID(),
    var name: String,
    val entries: MutableList<PartyEntry> = mutableListOf()
)
```

3.2.1 Referenced Members in a party must be stored as `MemberRef` entries that hold the Member’s UUID and are treated as shared references to the Member Library.  
3.2.2 Display name for a `MemberRef` must render as `"$name"`. E.g. `"Sir Lancelot"`.  
3.2.3 Cloned Members in a party must be stored as full `MemberClone` entries and are treated as per‑party clones that do not affect the Member Library.  
3.2.4 Display name for a `MemberClone` must render as `"$cloneTag)$name"`. E.g. `"a)Goblin"` or `"b)City Guard #2"`.  
3.2.5 The partyBox must ensure `cloneTag` uniqueness within a single Party for **all** `MemberClone` entries.  
3.2.6 If more than 36 clones exist in a party, the add‑clone operation must fail gracefully with an inline error explaining that available clone tags are exhausted.  
3.2.7 Deleting a `MemberClone` must immediately free its `cloneTag` for reuse within the same party.

# 4. Repository and Persistence

4.1 Create a singleton `CohortisRepository` that owns in‑memory state and persistence for `memberLibrary`, `partyLibrary`, `activeParties`, and `currentRound`.  
4.2 The repository must persist state using SharedPreferences with Gson for JSON serialization and must load all data during application startup and save immediately after every mutation.  
4.3 The repository must use the following SharedPreferences keys: `"members_json"`, `"parties_json"`, `"active_party_ids_json"`, `"current_round"`.  
4.4 The repository must correctly serialize and deserialize nested structures including `MemberClone` inside `Party.entries` using appropriate Gson `TypeToken`s and must clamp `hpCurrent` values to `0…hpFull` on load.  
4.5 Save the data using a `schemaVersion` so that as changes take place to `partyLibrary`, `memberLibrary`, and other artifacts, a converter can be written so that upgrades don't invalidate old records.  
4.6 Persist `cloneTag` for each `MemberClone` in JSON alongside its `member` and `displayName`.  
4.7 On load, validate that `cloneTag` is within range; if out of range or missing, reassign the lowest available tag within the party and save immediately.  
4.8 Expose `nextAvailableCloneTag(partyId: UUID): Char?` returning the lowest unused letter in range or `null` if all are used.  
4.9 Migrate legacy data by incrementing `schemaVersion` and assigning `cloneTag`s to existing clones deterministically using sorted order by `displayName` then `member.name`, taking the lowest available letters in ascending order.

# 5. Application Structure

5.1 The application must use a single `MainActivity` hosting two fragments: `PartyFragment` (top) and `EventLogFragment` (bottom).  
5.2 `activity_main.xml` must be a `ConstraintLayout` where `PartyFragment` occupies the top region and `EventLogFragment` occupies a bottom region sized to display two lines of text.  
5.3 `MainActivity` must load repository state at startup, initialize both fragments, and host an app‑bar Settings action that opens `SettingsActivity`.  
5.4 `SettingsActivity` must open the settings dialog.
5.5 Display a persistent Round Counter control.  

# 6. Main Encounter UI (PartyFragment)

6.1 `PartyFragment` must render a `RecyclerView` of Party cards using `MaterialCardView` and must present separate sections for **Referenced Members** and **Cloned Members**.  
6.2 Adding a **reference** from `PartyFragment` must allow choosing from the Member Library and must append the chosen id as a `MemberRef`; removing a reference must remove that `MemberRef`.  
6.3 Cloning a Member from `PartyFragment` must allow choosing from the Member Library and must deep‑copy the chosen Member into a `MemberClone` with a `cloneTag` assigned and an instance name defaulting to `"TemplateName #N"`; removing a clone must delete that `MemberClone`.  
6.4 Each referenced PC row must display name, `classesAndLevels`, THAC0, damageRolls, and an indicator if `specialDetections` or `specialAttacks` is present.  
6.5 Each referenced Monster row must display name, `HD: hitDice`, THAC0, damageRolls, an Attack# derived by counting attack groups in `damageRolls`, and indicators for `specialDetections` or `specialAttacks`.  
6.5.1 Attack group counting is defined as the number of groups separated by comma or semicolon as described in Section 6.  
6.6 Each cloned row must prefix the name with the `cloneTag` and a closing parenthesis, e.g., `a)City Guard`, must display fields appropriate to the underlying Member (PC or Monster), and must apply the same rules for Attack# derivation as the underlying Member type.  
6.7 Interacting with HP on any row must implement: single‑tap decrements `hpCurrent` by 1 not below 0, long‑press opens an HP editor dialog to set a value within `0…hpFull`, and a heal control increments `hpCurrent` by 1 not above `hpFull`, with persistence following Section 7.6 reference vs. clone rules.  
6.8 Tapping a member row outside HP controls must trigger a damage roll based on `damageRolls` and must log the result to `EventLogFragment` as specified in Section 9; long‑pressing a referenced row must offer Edit Member and Remove from Party, and long‑pressing a clone row must offer Rename Clone, Edit Clone, and Remove Clone.  
6.9 Tapping the party name in `PartyFragment` must open `PartyLibraryActivity` focused on that party for further management actions.  
6.10 Tapping the (+) button in `PartyFragment` must open `MemberLibraryActivity` to select a Member to add, offering “Add as Reference” and “Clone”.  
6.10.1 If no clone tags remain available for the party, the “Clone” operation must fail gracefully with a non‑blocking error message.  
6.11 All lists must use `ListAdapter` with `DiffUtil` to provide smooth updates and efficient UI refreshes.
6.12 Touching the damageRolls section of a member is used to display attack roll in the eventLog.  
6.12.1 A sub-string is created based on the location of the touch.  
6.12.2 The eventLong will display `"[$cloneTag)]<name> (<d20>:<XdY+Z>=roll)…"`. For Example:  
  * `"Adam 14:d8=7 20:d8=1"` where `member.name = "Adam"`, `damageRolls = "2xd8"`. d20=14, d8=7, d20=20, and d8=1
  * `"b)Goblin 8:d6+2=8"` where `cloneTag = 'b'`, `member.name = "Goblin"`, `damageRolls = "d6+2"`, d20=8, d6=6, and modifier=2 so total is 8.

# 7. Event Log UI (EventLogFragment)

7.1 Maintain an internal list of all log entries.  
7.2 Update immediately when new entries are posted.  
7.3 Display only the latest two lines in its visible area while retaining the full history in memory for the session.  
7.4 Allow Swiping up & down to view older and newer log entries, with a visual indication when the user is not at the bottom of the log.

# 8. Round Counter

8.1 The Round Counter control must be located at the bottom‑right seam between `PartyFragment` and `EventLogFragment` and must increment the round on single tap.  
8.2 The Round Counter must open a dialog on long‑press that provides a `NumberPicker` to set a specific round, a Reset‑to‑0 action, and a Cancel action, and must persist the current round after every change.  
8.3 The Round Counter must append a log entry to `EventLogFragment` stating `"Round advanced to X"` whenever the round changes.

# 9. Settings

9.1 `SettingsActivity` provide 'created by Jeronimo', the build date & time using yyyy-mm-dd HH:MM:SS, and the software version.  
```
app/build.gradle:
android {
    defaultConfig {
        def df = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        df.setTimeZone(TimeZone.getDefault())   // optional: use your local timezone

        buildConfigField(
            "String",
            "BUILD_TIMESTAMP",
            "\"${df.format(new Date())}\""
        )
    }
}

Kotlin:
val buildInfo = "Build: ${BuildConfig.BUILD_TIMESTAMP}"
textView.text = buildInfo
```

9.2 *Long-press* on (Master Reset) should open a confirmation dialog that clears all data from the repository and resets the app to its initial state as on first launch.

# 10. Member Library

10.1 Implement `MemberLibraryActivity` that displays a `RecyclerView` listing each member’s name.  
10.2 *Long-press* of member’s name should open a memberEditor:  
10.3 *Tapping* of member's name should clone that member to the party after assigning a unique `cloneTag.`  
10.4 *Swipe-right* of the member's name will add by reference that member to the party by inserting the Member’s UUID as a `MemberRef`. All changes to the member from the party will reflect back to the Member Library and all parties that reference it.
10.5 (Create) button should open memberEditor with blank fields to create a new member.
10.6 Import and export of Member definitions must be supported from the Member Library UI to allow backup/restore and sharing of stat blocks.  

# 11. Member Editor

11.1 Edit the fields used in `class Member`, validating the data when it is entered.  
11.2 (Save) button should save changes to the record provided and refresh the list with a confirmation toast..  
11.2 `type`' selector ("PC" or "Monster").  
11.3 If `type` is `PC`, show edit for `classesAndLevels`.  
11.4 If `type` is `Monster`, show edit for `hitDice`.  
11.5 `name`  
11.6 `thac0`  
11.7 `hpFull` & `hpCurrent`  
11.8 `damageRolls`  
11.9 `specialDetections`  
11.10 `specialAttacks`  

# 12. Party Library

12.1 Implement `PartyLibraryActivity` that lists all Parties and allows creating a new Party by name.  
12.2 `PartyLibraryActivity` must support long‑press actions on a Party to rename or delete the Party.  
12.3 `PartyLibraryActivity` must persist parties with `MemberRef` entries for referenced Members and `MemberClone` entries for cloned Members and must ensure these structures round‑trip via persistence.  
12.4 Adding a **reference** to a party must allow choosing from Member Library entries and must append the chosen id as a `MemberRef`; removing a referenced Member must remove that `MemberRef`.  
12.5 Cloning a Member into a party must allow choosing from Member Library entries, must deep‑copy the chosen Member into a `MemberClone`, must assign a `cloneTag` using the next‑available tag, and must prompt for an instance name defaulting to `"TemplateName #N"`; removing a cloned Member must delete that `MemberClone`.  
12.6 Editing a **referenced** Member within a party must persist changes back to the corresponding Member in `memberLibrary`, while editing a **cloned** Member must persist changes only within that party’s `MemberClone` and must not modify `memberLibrary`.  
12.7 The Party Library must provide a cleanup tool that detects references whose `memberId` no longer resolves to a Member in the Member Library and allows the user to remove those dangling references, which must be rendered as “(missing member)” in UI lists.  
12.8 The Party Library must expose query helpers for UI to list active parties, resolve referenced Members for a party, and list cloned Members for a party.  
12.9 Deleting a `MemberClone` must immediately free its `cloneTag` for reuse, and the repository must save after the mutation.

# 13. Non‑Functional Requirements

13.1 The application must run on Android 6.0.1 and newer (minSdk 23).  
13.2 The architecture must use Activity + Fragments + Repository + Adapters + Models, with ViewModels permitted where beneficial but not required.  
13.3 All `RecyclerView`s must use `ListAdapter` with `DiffUtil` to ensure smooth, efficient updates.  
13.4 The UI must use Material 3 theming and components consistent with platform guidelines.  
13.5 All editors must validate numeric inputs, must validate `damageRolls` syntax, must clamp `hpCurrent` to `0…hpFull`, and must produce clear inline error messages on invalid input.  
13.6 Missing referenced Members encountered in parties must render as `"(missing member)"` with a cleanup affordance to remove the dangling reference safely.  
13.7 Unit tests must be provided for the dice parser/roller (including multi‑group expressions and additive parts) and for JSON serialization/deserialization of the repository including nested `MemberClone` entries and `cloneTag` persistence.  
13.8 All repository operations described in Section 4 must persist correctly and load correctly on next app launch.  
13.9 The app must not crash during edits, deletions, clone creation, damage rolling, round changes, or persistence operations.  
13.10 Clone tag assignment, uniqueness, migration, and exhaustion behavior must be covered by unit tests, including attempting to create more than 36 clones in a single party.

# 14. Final Execution Requirements

14.1 Generate a complete Android Studio project implementing all functional and non‑functional requirements described in Sections 5 through  12.  
14.2 Provide all Kotlin source files, XML layout files, drawable resources, and configuration files required to implement these requirements.  
14.3 Ensure that all features defined in Sections 5 through 12 are fully implemented, integrated, and functional within the generated project.  
14.4 Ensure the resulting project builds successfully in Android Studio without modifications and runs correctly on a device or emulator.  
14.5 Provide a concise summary listing all created or modified files as part of project generation.

# 15. Android Gestures

15.1 *Tap* is a short press and release, corresponding to a pointer going down and then up.  
15.2 *Double‑tap* is a gesture where the pointer goes down, up, down, and up in quick succession.  
15.3 *Long‑press* is a gesture where the pointer goes down and is held in place for an extended duration before lifting.  
15.4 *Swipe* is a quick press‑move‑lift gesture used to scroll content or navigate between views.  
15.5 *Drag* is a controlled press‑move‑lift gesture where movement stops when the finger lifts and is used for repositioning or manipulating content.  
15.6 *Long‑press* drag begins with a long‑press followed by dragging and is used to rearrange or move objects.  
15.7 *Pinch open* is a two‑finger gesture where both fingers press, move outward, and lift to zoom in.  
15.8 *Pinch close* is a two‑finger gesture where both fingers press, move inward, and lift to zoom out.  
15.9 *Double‑touch* is a two‑finger gesture involving two touches in quick succession and is used for standardized scaling or text‑selection behavior.  
15.10 *Double‑touch* drag is a gesture where a quick second two‑finger touch is followed by an upward or downward drag to adjust zoom level dynamically.  
15.11 *Multi‑finger* tap is a gesture where multiple fingers tap the screen simultaneously, generating `ACTION_DOWN` for the first finger and `ACTION_POINTER_DOWN` for additional fingers.  
15.12 *Multi‑finger* press is a gesture where two or more pointers press the screen at the same time and remain stationary; Android reports these pointers using `ACTION_DOWN`, `ACTION_POINTER_DOWN`, `ACTION_MOVE`, and `ACTION_POINTER_UP`.  
15.13 *Multi‑finger* lift is the termination of a multi‑finger gesture, where non‑primary fingers generate `ACTION_POINTER_UP` events and the final finger generates `ACTION_UP`.

***