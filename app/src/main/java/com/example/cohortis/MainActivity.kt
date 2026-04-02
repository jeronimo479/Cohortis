package com.example.cohortis

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.cohortis.databinding.ActivityMainBinding
import com.example.cohortis.databinding.DialogEditMemberBinding
import com.example.cohortis.databinding.DialogLibraryBinding
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

/**
 * The main entry point of the application. Manages the overall UI, fragments, 
 * data persistence, and high-level workflows like importing/exporting data.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dataManager: DataManager
    private var partyFragment: PartyFragment? = null
    private var eventFragment: EventFragment? = null

    private var currentRound = 0
    private var memberLibrary = mutableListOf<Member>()
    private var partyLibrary = mutableListOf<Party>()
    private var activeParties = mutableListOf<Party>()

    /** Launcher for the system file picker to import JSON data. */
    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { importFromJson(it) }
    }

    /** Launcher for the system file picker to export data as a JSON file. */
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let { exportToJson(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        binding.btnAddParty.setOnClickListener {
            showPartyLibraryManager()
        }

        binding.btnAddParty.setOnLongClickListener {
            val tempParty = Party(id = UUID.randomUUID(), name = "", isActive = true)
            editPartyDialog(tempParty, isNew = true)
            true
        }

        binding.tvAppTitle.setOnClickListener {
            showMemberLibraryManager()
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        dataManager = DataManager(this)
        loadData()

        setupFragments()
        setupRoundCounter()
    }

    /**
     * Loads application data from persistent storage and initializes local state.
     */
    private fun loadData() {
        memberLibrary = dataManager.memberLibrary.apply { sortBy { it.name.lowercase() } }
        
        val loadedParties = dataManager.partyLibrary
        sortPartyLibrary(loadedParties)
        partyLibrary = loadedParties

        updateActivePartiesList()
        currentRound = dataManager.currentRound
    }

    /**
     * Sorts the party library alphabetically, placing the priority party at the top.
     *
     * @param list The mutable list of parties to sort.
     */
    private fun sortPartyLibrary(list: MutableList<Party>) {
        val priorityId = dataManager.priorityPartyId
        val priority = list.find { it.id == priorityId }
        
        if (priority != null) {
            list.remove(priority)
            list.sortBy { it.name.lowercase() }
            list.add(0, priority)
        } else {
            list.sortBy { it.name.lowercase() }
        }
    }

    /**
     * Updates the list of currently active parties, filtered and sorted by priority.
     */
    private fun updateActivePartiesList() {
        val priorityId = dataManager.priorityPartyId
        activeParties = partyLibrary.asSequence()
            .filter { it.isActive }
            .sortedWith(compareByDescending<Party> { it.id == priorityId }
                .thenBy { it.name.lowercase() })
            .toMutableList()
    }

    /**
     * Persists all current data using the [DataManager].
     */
    private fun saveData() {
        dataManager.saveAll(memberLibrary, partyLibrary, activeParties, dataManager.priorityPartyId, currentRound)
    }

    /**
     * Initializes and attaches the UI fragments for party display and event logging.
     */
    private fun setupFragments() {
        partyFragment = PartyFragment()
        eventFragment = EventFragment()

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.party_container, partyFragment!!)
            .replace(R.id.event_container, eventFragment!!)
            .commitNow()

        binding.root.post {
            partyFragment?.setupRecyclerView(
                parties = activeParties,
                onHpChanged = { member, _ ->
                    updateAllReferences(member)
                    refreshActiveParties()
                },
                onHpComplete = { member, start, end ->
                    if (start != end) {
                        eventFragment?.addLog("${member.getDisplayName()} $start -> $end hpCurrent")
                    }
                },
                onDamageTapped = { member, segment ->
                    rollDamage(member, segment)
                },
                onMemberLongTapped = { member: Member, party: Party ->
                    showEditMemberDialog(member, fromParty = party)
                },
                onOpenPartyLibrary = { party ->
                    if (party != null) {
                        editPartyDialog(party)
                    } else {
                        showPartyLibraryManager()
                    }
                },
                onPartyRenameRequested = { party ->
                    editPartyDialog(party)
                },
                onOpenMemberLibrary = { party ->
                    showMemberLibraryManager(party)
                },
                onCreateMemberRequested = { party ->
                    createNewMember(party)
                }
            )
            refreshActiveParties()
        }
    }

    /**
     * Refreshes the active party list UI and saves the current state.
     */
    private fun refreshActiveParties() {
        updateActivePartiesList()
        partyFragment?.updateParties(activeParties)
        saveData()
    }

    /**
     * Synchronizes changes to a member object across all libraries and active lists.
     * Skips for clones (identified by [Member.cloneTag]) as they are unique instances.
     *
     * @param member The updated member object.
     */
    private fun updateAllReferences(member: Member) {
        if (member.cloneTag != 0.toChar()) {
            return
        }

        activeParties.forEach { party ->
            val idx = party.members.indexOfFirst { it.id == member.id }
            if (idx != -1) party.members[idx] = member
        }
        val libIdx = memberLibrary.indexOfFirst { it.id == member.id }
        if (libIdx != -1) memberLibrary[libIdx] = member
        
        partyLibrary.forEach { party ->
            val idx = party.members.indexOfFirst { it.id == member.id }
            if (idx != -1) party.members[idx] = member
        }
    }

    /**
     * Configures the gesture detectors for the round counter UI.
     * Supports single tap (next), double tap (prev), long press + swipe (reset).
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupRoundCounter() {
        binding.content.tvRoundNumber.text = currentRound.toString()
        
        var isLongPressing = false
        var startX = 0f

        val roundGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                currentRound++
                if (currentRound > 99) currentRound = 0
                updateRoundDisplay()
                logRoundChange(getString(R.string.round_started, currentRound))
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (currentRound > 0) {
                    currentRound--
                    updateRoundDisplay()
                    logRoundChange(getString(R.string.round_decreased, currentRound))
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                isLongPressing = true
                binding.content.roundCounterCard.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        })

        binding.content.roundCounterCard.setOnTouchListener { v, event ->
            roundGestureDetector.onTouchEvent(event)
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    isLongPressing = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isLongPressing) {
                        val diffX = event.x - startX
                        if (diffX < -100) { // Slide left threshold
                            currentRound = 0
                            updateRoundDisplay()
                            logRoundChange(getString(R.string.rounds_reset))
                            isLongPressing = false // Reset
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    v.performClick()
                    isLongPressing = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    isLongPressing = false
                }
            }
            true
        }
    }

    /**
     * Updates the UI text and persistent storage for the current round number.
     */
    private fun updateRoundDisplay() {
        binding.content.tvRoundNumber.text = currentRound.toString()
        dataManager.currentRound = currentRound
    }

    /**
     * Logs a message related to round changes to the event log.
     *
     * @param message The message to log.
     */
    private fun logRoundChange(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        eventFragment?.addLog("[$time] $message")
    }

    /**
     * Performs a detailed damage roll for a member and logs the results.
     * Includes d20 to-hit rolls and individual damage dice results.
     *
     * @param member The member performing the roll.
     * @param segment The dice expression segment to roll (e.g., "1d8+2").
     * @return The total damage sum of the rolls.
     */
    private fun rollDamage(member: Member, segment: String): Int {
        val attackResults = DiceRoller.rollDamageSegmentDetailed(segment)
        if (attackResults.isEmpty()) return 0

        binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        val nameStr = member.getDisplayName()
        
        val logBuilder = SpannableStringBuilder()
        logBuilder.append(nameStr).append(" ")

        attackResults.forEachIndexed { index, result ->
            if (index > 0) logBuilder.append(" ")
            
            val rollStr = result.d20.toString()
            val start = logBuilder.length
            logBuilder.append(rollStr)
            val end = logBuilder.length
            
            val circleSpan = CircleSpan(
                backgroundColor = ContextCompat.getColor(this, android.R.color.darker_gray),
                textColor = ContextCompat.getColor(this, android.R.color.white)
            )
            logBuilder.setSpan(circleSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            
            logBuilder.append(" ")
                .append(result.damageExpr)
                .append(" = ")
                .append(result.damageTotal.toString())
        }
        
        eventFragment?.addLog(logBuilder)
        return attackResults.sumOf { it.damageTotal }
    }

    /**
     * Helper to set up a simple +/- stepper for an [EditText].
     */
    private fun setupStepper(valueView: EditText, minusBtn: View, plusBtn: View, min: Int, max: Int, onChanged: ((Int) -> Unit)? = null) {
        minusBtn.setOnClickListener {
            val current = valueView.text.toString().toIntOrNull() ?: 0
            if (current > min) {
                val newVal = current - 1
                valueView.setText(newVal.toString())
                onChanged?.invoke(newVal)
            }
        }
        plusBtn.setOnClickListener {
            val current = valueView.text.toString().toIntOrNull() ?: 0
            if (current < max) {
                val newVal = current + 1
                valueView.setText(newVal.toString())
                onChanged?.invoke(newVal)
            }
        }
    }

    /**
     * Helper to open the dice roller dialog.
     */
    private fun editHpDiceRolls(title: String, initialValue: String, isPC: Boolean, isHpOrHd: Boolean, onComplete: (String) -> Unit) {
        SwipeDiceRollerDialogFragment.newInstance(
            title = title,
            initialValue = initialValue,
            isPC = isPC,
            isHpOrHd = isHpOrHd,
            onDiceEntered = onComplete
        ).show(supportFragmentManager, "swipe_dice_${title.lowercase().replace(" ", "_")}")
    }

    /**
     * Shows a dialog to edit the details of a [Member].
     *
     * @param member The member to edit.
     * @param fromParty Optional party context if editing a member within a party.
     * @param fromLibrary Boolean indicating if the edit is triggered from the global library.
     * @param onChanged Callback invoked after changes are saved.
     */
    private fun showEditMemberDialog(
        member: Member, 
        fromParty: Party? = null,
        fromLibrary: Boolean = false,
        onChanged: (() -> Unit)? = null
    ) {
        val dp = resources.displayMetrics.density
        val dialogBinding = DialogEditMemberBinding.inflate(LayoutInflater.from(this))
        
        val titleLayout = RelativeLayout(this).apply {
            val p = (16 * dp).toInt()
            setPadding(p, p, p, 0)
        }
        val tvTitle = TextView(this).apply {
            text = if (member.cloneTag != 0.toChar()) getString(R.string.edit_clone) else getString(R.string.edit_member)
            setTextColor(Color.BLACK)
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        titleLayout.addView(tvTitle)

        var dialog: AlertDialog? = null

        val btnDel = TextView(this).apply {
            text = "DEL"
            setTextColor(Color.RED)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding((16 * dp).toInt(), 0, 0, 0)
            layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            setOnClickListener {
                if (fromParty != null) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(getString(R.string.remove_member_title))
                        .setMessage(getString(R.string.remove_member_msg, member.getDisplayName()))
                        .setPositiveButton(getString(R.string.remove_btn)) { _, _ ->
                            fromParty.members.removeAll { it.id == member.id }
                            refreshActiveParties()
                            onChanged?.invoke()
                            dialog?.dismiss()
                        }
                        .setNegativeButton(getString(R.string.skip), null)
                        .show()
                } else {
                    // fromLibrary or general delete
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(getString(R.string.permanent_delete_title))
                        .setMessage(getString(R.string.permanent_delete_msg, member.name))
                        .setPositiveButton(getString(R.string.delete_btn)) { _, _ ->
                            memberLibrary.removeAll { it.id == member.id }
                            // Also remove from any parties if needed, although mostly template based
                            partyLibrary.forEach { it.members.removeAll { m -> m.id == member.id } }
                            saveData()
                            refreshActiveParties()
                            onChanged?.invoke()
                            dialog?.dismiss()
                        }
                        .setNegativeButton(getString(R.string.skip), null)
                        .show()
                }
            }
        }
        titleLayout.addView(btnDel)

        dialogBinding.apply {
            etName.setText(member.name)
            cbIsPC.isChecked = member.isPC
            etClassLevel.setText(member.classLevels)
            
            etHitDice.setText(member.hitDice)
            etHitDice.isFocusable = false
            etHitDice.setOnClickListener {
                val wasEmpty = member.hitDice.isBlank()
                val oldHpCurrent = member.hpCurrent
                val editTitle = if (cbIsPC.isChecked) getString(R.string.edit_hp) else getString(R.string.edit_hd)
                editHpDiceRolls(
                    title = editTitle,
                    initialValue = etHitDice.text.toString(),
                    isPC = cbIsPC.isChecked,
                    isHpOrHd = true
                ) { diceStr ->
                    etHitDice.setText(diceStr)
                    member.hitDice = diceStr
                    if (wasEmpty && diceStr.isNotBlank()) {
                        val rolledHp = DiceRoller.rollSegmentTotal(diceStr)
                        member.hpFull = rolledHp
                        member.hpCurrent = rolledHp
                        btnEditHpFull.text = rolledHp.toString()
                        btnEditHpCurrent.text = rolledHp.toString()
                        
                        refreshActiveParties()
                        if (oldHpCurrent != rolledHp) {
                            eventFragment?.addLog(getString(R.string.log_hp_change_simple, member.getDisplayName(), oldHpCurrent, rolledHp, "hpCurrent"))
                        }
                    }
                }
            }
            
            btnEditHpFull.text = member.hpFull.toString()
            btnEditHpFull.setOnClickListener {
                member.hitDice = etHitDice.text.toString()
                HpModifierDialogFragment.newInstance(
                    member = member,
                    isFromEdit = true,
                    stayOpen = true,
                    onRollRequested = if (member.isPC) ({ m, s -> rollDamage(m, s) }) else null,
                    onComplete = { m, start, end ->
                        if (start != end) {
                            eventFragment?.addLog(getString(R.string.log_hp_change_simple, m.getDisplayName(), start, end, "hpFull"))
                        }
                    },
                    onApplied = { updatedMember ->
                        btnEditHpFull.text = updatedMember.hpFull.toString()
                        btnEditHpCurrent.text = updatedMember.hpCurrent.toString()
                        member.hpFull = updatedMember.hpFull
                        member.hpCurrent = updatedMember.hpCurrent
                        refreshActiveParties()
                    }
                ).show(supportFragmentManager, "hp_modifier_edit_full")
            }

            btnEditHpCurrent.text = member.hpCurrent.toString()
            btnEditHpCurrent.setOnClickListener {
                HpModifierDialogFragment.newInstance(
                    member = member,
                    isFromEdit = false,
                    stayOpen = true,
                    onRollRequested = if (member.isPC) ({ m, s -> rollDamage(m, s) }) else null,
                    onComplete = { m, start, end ->
                        if (start != end) {
                            eventFragment?.addLog(getString(R.string.log_hp_change_simple, m.getDisplayName(), start, end, "hpCurrent"))
                        }
                    },
                    onApplied = { updatedMember ->
                        btnEditHpCurrent.text = updatedMember.hpCurrent.toString()
                        member.hpCurrent = updatedMember.hpCurrent
                        refreshActiveParties()
                    }
                ).show(supportFragmentManager, "hp_modifier_edit_current")
            }
            
            etThac0.setText(member.thac0.toString())
            setupStepper(etThac0, btnThac0Minus, btnThac0Plus, 0, 20)
            
            etArmorClass.setText(member.armorClass.toString())
            setupStepper(etArmorClass, btnAcMinus, btnAcPlus, -10, 10)

            etDamageRolls.setText(member.damageRolls)
            etDamageRolls.isFocusable = false
            etDamageRolls.setOnClickListener {
                editHpDiceRolls(
                    title = getString(R.string.damage_rolls),
                    initialValue = etDamageRolls.text.toString(),
                    isPC = cbIsPC.isChecked,
                    isHpOrHd = false
                ) { diceStr ->
                    etDamageRolls.setText(diceStr)
                    member.damageRolls = diceStr
                }
            }

            etSpecialDetections.setText(member.specialDetections ?: "")
            etSpecialAttacks.setText(member.specialAttacks ?: "")

            val updateVisibility = { isPC: Boolean ->
                llClassAttacks.visibility = if (isPC) View.VISIBLE else View.GONE
            }

            updateVisibility(member.isPC)
            cbIsPC.setOnCheckedChangeListener { _, isChecked -> updateVisibility(isChecked) }

            btnRemoveDelete.visibility = View.GONE

            btnSaveMember.setOnClickListener {
                member.apply {
                    name = etName.text.toString()
                    isPC = cbIsPC.isChecked
                    classLevels = etClassLevel.text.toString()
                    hitDice = etHitDice.text.toString()
                    hpFull = btnEditHpFull.text.toString().toIntOrNull() ?: hpFull
                    hpCurrent = btnEditHpCurrent.text.toString().toIntOrNull() ?: hpCurrent
                    thac0 = etThac0.text.toString().toIntOrNull() ?: thac0
                    armorClass = etArmorClass.text.toString().toIntOrNull() ?: armorClass
                    damageRolls = etDamageRolls.text.toString()
                    specialDetections = etSpecialDetections.text.toString()
                    specialAttacks = etSpecialAttacks.text.toString()
                }
                updateAllReferences(member)
                memberLibrary.sortBy { it.name.lowercase() }
                partyLibrary.forEach { it.members.sortBy { m -> m.name.lowercase() } }
                refreshActiveParties()
                onChanged?.invoke()
                dialog?.dismiss()
            }
        }

        val builder = AlertDialog.Builder(this)
            .setCustomTitle(titleLayout)
            .setView(dialogBinding.root)
        
        dialog = builder.create()
        dialog.show()
    }

    /**
     * Unified dialog to create or edit a party.
     *
     * @param party The party to edit, or a new empty party for creation.
     * @param isNew True if this is a new party being created.
     * @param onComplete Callback invoked when the user finishes and saves.
     */
    private fun editPartyDialog(party: Party, isNew: Boolean = false, onComplete: (() -> Unit)? = null) {
        val dp = resources.displayMetrics.density
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * dp).toInt()
            setPadding(p, p, p, p)
        }
        
        val etName = EditText(this).apply {
            setText(party.name)
            hint = getString(R.string.party_name_hint)
            setTextColor(Color.BLACK)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            maxLines = 1
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        
        val cbActive = CheckBox(this).apply {
            text = getString(R.string.active)
            isChecked = if (isNew) true else party.isActive
            setTextColor(Color.BLACK)
            isEnabled = party.name.isNotEmpty()
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val cbPriority = CheckBox(this).apply {
            text = getString(R.string.priority_party)
            isChecked = (party.id == dataManager.priorityPartyId)
            setTextColor(Color.BLACK)
            isEnabled = party.name.isNotEmpty()
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val rowCheckboxes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(cbActive)
            addView(cbPriority)
        }

        val membersContainer = GridLayout(this).apply {
            columnCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = true
            setPadding(8, 8, 8, 8)
        }

        val scrollMembers = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (200 * dp).toInt()
            )
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            addView(membersContainer)
        }

        fun refreshMembers() {
            membersContainer.removeAllViews()
            if (party.members.isEmpty()) {
                membersContainer.addView(TextView(this).apply {
                    text = getString(R.string.no_members)
                    setTextColor(Color.GRAY)
                })
            } else {
                party.members.forEach { member ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(4, 2, 4, 2)
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        val params = GridLayout.LayoutParams()
                        params.width = 0
                        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        layoutParams = params
                    }
                    val btnRemove = Button(this, null, 0, android.R.style.Widget_Material_Button_Borderless).apply {
                        text = "X"
                        setTextColor(Color.RED)
                        val size = (32 * dp).toInt()
                        layoutParams = LinearLayout.LayoutParams(size, size)
                        setPadding(0, 0, 0, 0)
                        setOnClickListener {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle(getString(R.string.remove_member_title))
                                .setMessage(getString(R.string.remove_member_msg, member.getDisplayName()))
                                .setPositiveButton(getString(R.string.remove_btn)) { _, _ ->
                                    party.members.remove(member)
                                    refreshMembers()
                                }
                                .show()
                        }
                    }
                    val nameView = TextView(this).apply {
                        text = member.getDisplayName(full = false)
                        setTextColor(Color.BLACK)
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    row.addView(btnRemove)
                    row.addView(nameView)
                    membersContainer.addView(row)
                }
            }
        }
        refreshMembers()
        
        val btnAddFromLibrary = Button(this, null, 0, android.R.style.Widget_Material_Button_Small).apply {
            text = getString(R.string.from_library)
            isAllCaps = false
            setPadding(0, 0, 0, 0)
            isEnabled = party.name.isNotEmpty()
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                showMemberLibraryManager(party) {
                    refreshMembers()
                }
            }
        }
        
        val btnCreateNewMember = Button(this, null, 0, android.R.style.Widget_Material_Button_Small).apply {
            text = getString(R.string.create)
            isAllCaps = false
            setPadding(0, 0, 0, 0)
            isEnabled = party.name.isNotEmpty()
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                createNewMember(party) {
                    refreshMembers()
                }
            }
        }

        val rowMemberButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            val tvLabel = TextView(this@MainActivity).apply {
                text = getString(R.string.member_label)
                setTextColor(Color.BLACK)
                setPadding(0, 0, 8, 0)
            }
            addView(tvLabel)
            addView(btnAddFromLibrary)
            addView(btnCreateNewMember)
        }

        val btnResetAllHp = Button(this).apply {
            text = getString(R.string.reset_all_hp)
            isEnabled = party.name.isNotEmpty()
            setOnClickListener {
                party.members.forEach { it.hpCurrent = it.hpFull }
                refreshActiveParties()
                Toast.makeText(this@MainActivity, getString(R.string.all_hp_reset_toast), Toast.LENGTH_SHORT).show()
            }
        }

        val updateEnabledStates = { hasName: Boolean ->
            cbActive.isEnabled = hasName
            cbPriority.isEnabled = hasName
            btnAddFromLibrary.isEnabled = hasName
            btnCreateNewMember.isEnabled = hasName
            btnResetAllHp.isEnabled = hasName
            if (hasName && isNew && !cbActive.isChecked) cbActive.isChecked = true
        }

        dialogView.addView(etName)
        dialogView.addView(rowCheckboxes)
        dialogView.addView(rowMemberButtons)
        dialogView.addView(btnResetAllHp)
        dialogView.addView(scrollMembers)

        val titleLayout = RelativeLayout(this).apply {
            val p = (16 * dp).toInt()
            setPadding(p, p, p, 0)
        }
        val tvTitle = TextView(this).apply {
            text = if (isNew) getString(R.string.create_new_party) else getString(R.string.manage_party)
            setTextColor(Color.BLACK)
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        titleLayout.addView(tvTitle)

        var dialog: AlertDialog? = null

        if (!isNew) {
            val btnDel = TextView(this).apply {
                text = "DEL"
                setTextColor(Color.RED)
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding((16 * dp).toInt(), 0, 0, 0)
                layoutParams = RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                    addRule(RelativeLayout.CENTER_VERTICAL)
                }
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(getString(R.string.permanent_delete_title))
                        .setMessage(getString(R.string.permanent_delete_msg, party.name))
                        .setPositiveButton(getString(R.string.delete_btn)) { _, _ ->
                            if (party.id == dataManager.priorityPartyId) {
                                dataManager.priorityPartyId = null
                            }
                            partyLibrary.removeAll { it.id == party.id }
                            activeParties.removeAll { it.id == party.id }
                            sortPartyLibrary(partyLibrary)
                            refreshActiveParties()
                            onComplete?.invoke()
                            dialog?.dismiss()
                        }
                        .setNegativeButton(getString(R.string.skip), null)
                        .show()
                }
            }
            titleLayout.addView(btnDel)
        }

        val builder = AlertDialog.Builder(this)
            .setCustomTitle(titleLayout)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.ok), null)

        dialog = builder.create()
        dialog.show()

        val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

        val syncOkButton = {
            val currentText = etName.text.toString().trim()
            if (currentText.isEmpty()) {
                okButton.isEnabled = false
                okButton.text = getString(R.string.ok)
            } else {
                val isDuplicate = partyLibrary.any { it.id != party.id && it.name.equals(currentText, ignoreCase = true) }
                if (isDuplicate) {
                    okButton.isEnabled = false
                    okButton.text = getString(R.string.dupe)
                } else {
                    okButton.isEnabled = true
                    okButton.text = getString(R.string.ok)
                }
            }
        }
        syncOkButton()

        okButton.setOnClickListener {
            val newName = etName.text.toString().trim()
            party.name = newName
            party.isActive = cbActive.isChecked
            if (cbPriority.isChecked) {
                dataManager.priorityPartyId = party.id
            } else if (party.id == dataManager.priorityPartyId) {
                dataManager.priorityPartyId = null
            }
            if (isNew) {
                partyLibrary.add(party)
            }
            sortPartyLibrary(partyLibrary)
            refreshActiveParties()
            onComplete?.invoke()
            dialog.dismiss()
        }

        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasName = s?.toString()?.trim()?.isNotEmpty() == true
                updateEnabledStates(hasName)
                syncOkButton()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }



    /**
     * Shows the app settings dialog, allowing import/export and master reset.
     */
    private fun showSettingsDialog() {
        var versionTapCount = 0
        val titleView = TextView(this).apply {
            text = getString(R.string.settings_version_info, BuildConfig.VERSION_NAME, BuildConfig.BUILD_TIME)
            setPadding(60, 40, 60, 0)
            textSize = 14f
            setTextColor(0xFF555555.toInt())
            setOnClickListener {
                versionTapCount++
                if (versionTapCount >= 3) {
                    performMasterReset()
                }
            }
        }
        
        val options = arrayOf(getString(R.string.import_json_library), getString(R.string.export_json_library))
        AlertDialog.Builder(this)
            .setCustomTitle(titleView)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> importLauncher.launch("application/json")
                    1 -> {
                        val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
                        val fileName = "Cohortis_Library_${sdf.format(Date())}.json"
                        exportLauncher.launch(fileName)
                    }
                }
            }
            .show()
    }

    /**
     * Performs a "Master Reset" which wipes all data.
     */
    private fun performMasterReset() {
        dataManager.clearAll()
        loadData()
        currentRound = 0
        refreshActiveParties()
        binding.content.tvRoundNumber.text = "0"
        eventFragment?.clearLog()
        eventFragment?.addLog(getString(R.string.system_reset_complete))
        Toast.makeText(this, getString(R.string.master_reset_complete), Toast.LENGTH_SHORT).show()
    }

    /**
     * Handles the logic for importing data from a selected JSON file URI.
     */
    private fun importFromJson(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val json = inputStream.bufferedReader().use { it.readText() }
                val gson = GsonBuilder().setPrettyPrinting().create()
                val importData: Map<String, Any> = gson.fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)
                val importedMembersJson = gson.toJson(importData["members"])
                val importedPartiesJson = gson.toJson(importData["parties"])
                val importedMembers: List<Member>? = gson.fromJson(importedMembersJson, object : TypeToken<List<Member>>() {}.type)
                val importedParties: List<Party>? = gson.fromJson(importedPartiesJson, object : TypeToken<List<Party>>() {}.type)
                val membersQueue = importedMembers?.toMutableList() ?: mutableListOf()
                processImportQueue(membersQueue, importedParties ?: emptyList(), 0, 0)
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.import_failed, e.message), Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    /**
     * Recursively processes the queue of members to be imported, handling conflicts.
     *
     * @param membersQueue Queue of members left to import.
     * @param partiesToImport List of parties to import.
     * @param membersAdded Counter for members added.
     * @param partiesAdded Counter for parties added.
     */
    private fun processImportQueue(
        membersQueue: MutableList<Member>,
        partiesToImport: List<Party>,
        membersAdded: Int,
        partiesAdded: Int
    ) {
        if (membersQueue.isEmpty()) {
            var finalPartiesAdded = partiesAdded
            partiesToImport.forEach { imported ->
                if (partyLibrary.none { it.name.equals(imported.name, ignoreCase = true) }) {
                    val partyWithNewIds = imported.copy(
                        id = UUID.randomUUID(),
                        members = imported.members.map { it.copy(id = UUID.randomUUID()) }.toMutableList(),
                        isActive = false
                    )
                    partyLibrary.add(partyWithNewIds)
                    finalPartiesAdded++
                }
            }
            sortPartyLibrary(partyLibrary)
            saveData()
            loadData()
            refreshActiveParties()
            Toast.makeText(this, getString(R.string.imported_summary, membersAdded, finalPartiesAdded), Toast.LENGTH_LONG).show()
            return
        }

        val imported = membersQueue.removeAt(0)
        val existing = memberLibrary.find { it.id == imported.id }

        if (existing == null) {
            if (memberLibrary.none { it.name.equals(imported.name, ignoreCase = true) }) {
                memberLibrary.add(imported)
                processImportQueue(membersQueue, partiesToImport, membersAdded + 1, partiesAdded)
            } else {
                processImportQueue(membersQueue, partiesToImport, membersAdded, partiesAdded)
            }
        } else {
            if (existing == imported) {
                processImportQueue(membersQueue, partiesToImport, membersAdded, partiesAdded)
            } else {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.import_conflict))
                    .setMessage(getString(R.string.import_conflict_msg, imported.name, imported.id))
                    .setPositiveButton(getString(R.string.overwrite)) { _, _ ->
                        val index = memberLibrary.indexOf(existing)
                        if (index != -1) memberLibrary[index] = imported
                        processImportQueue(membersQueue, partiesToImport, membersAdded + 1, partiesAdded)
                    }
                    .setNegativeButton(getString(R.string.skip)) { _, _ ->
                        processImportQueue(membersQueue, partiesToImport, membersAdded, partiesAdded)
                    }
                    .setNeutralButton(getString(R.string.import_as_new)) { _, _ ->
                        memberLibrary.add(imported.copy(id = UUID.randomUUID()))
                        processImportQueue(membersQueue, partiesToImport, membersAdded + 1, partiesAdded)
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    /**
     * Exports the current library and parties to a JSON file at the given URI.
     */
    private fun exportToJson(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val exportData = mapOf(
                    "members" to memberLibrary,
                    "parties" to partyLibrary
                )
                val gson = GsonBuilder().setPrettyPrinting().create()
                val json = gson.toJson(exportData)
                outputStream.write(json.toByteArray())
                Toast.makeText(this, getString(R.string.library_exported_success), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.export_failed, e.message), Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    /**
     * Shows a full-screen dialog to manage the global member library.
     * Allows creating members or adding them to a target party.
     *
     * @param targetParty If provided, selecting a member adds it to this party.
     * @param onDismiss Callback invoked when the library dialog is closed.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun showMemberLibraryManager(targetParty: Party? = null, onDismiss: (() -> Unit)? = null) {
        val dialog = Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val libBinding = DialogLibraryBinding.inflate(layoutInflater)
        dialog.setContentView(libBinding.root)

        if (targetParty != null) {
            libBinding.tvLibraryTitle.text = getString(R.string.member_library_from, targetParty.name.ifEmpty { getString(R.string.new_party) })
        } else {
            libBinding.tvLibraryTitle.text = getString(R.string.member_library)
        }
        
        libBinding.btnCreate.text = getString(R.string.create_member)
        libBinding.llCloneContainer.visibility = if (targetParty != null) View.VISIBLE else View.GONE

        libBinding.npCloneCount.apply {
            minValue = 0
            maxValue = 36
            value = 0
            wrapSelectorWheel = false
        }

        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, memberLibrary.map { it.name })
        libBinding.lvItems.adapter = adapter

        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean { return true }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val pos = libBinding.lvItems.pointToPosition(e.x.toInt(), e.y.toInt())
                if (pos != -1 && targetParty != null) {
                    val template = memberLibrary[pos]
                    val count = libBinding.npCloneCount.value
                    if (count > 0) {
                        addClonesToParty(template, targetParty, count)
                    } else {
                        addMemberByReference(template, targetParty)
                    }
                }
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                val pos = libBinding.lvItems.pointToPosition(e.x.toInt(), e.y.toInt())
                if (pos != -1) {
                    val member = memberLibrary[pos]
                    showEditMemberDialog(member, fromLibrary = true, onChanged = {
                        libBinding.lvItems.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, memberLibrary.map { it.name })
                    })
                }
            }
        })

        libBinding.lvItems.setOnTouchListener { v, event ->
            val handled = detector.onTouchEvent(event)
            if (handled && event.action == MotionEvent.ACTION_UP) {
                v.performClick()
            }
            handled
        }

        libBinding.btnCreate.setOnClickListener {
            createNewMember {
                libBinding.lvItems.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, memberLibrary.map { it.name })
            }
        }

        dialog.setOnDismissListener {
            onDismiss?.invoke()
        }
        dialog.show()
    }

    /**
     * Adds multiple unique clones of a member template to a party.
     * Clones are tagged with a unique character (a-z, 0-9).
     *
     * @param template Member template to clone.
     * @param targetParty Party to receive the clones.
     * @param count Number of clones to add.
     */
    private fun addClonesToParty(template: Member, targetParty: Party, count: Int) {
        val tags = ('a'..'z').toList() + ('0'..'9').toList()
        var addedCount = 0
        repeat(count) {
            val existingTags = targetParty.members
                .filter { it.name == template.name && it.cloneTag != 0.toChar() }
                .map { it.cloneTag }
                .toSet()
            val availableTag = tags.firstOrNull { it !in existingTags }
            if (availableTag != null) {
                val cloned = template.clone()
                cloned.cloneTag = availableTag
                targetParty.members.add(cloned)
                addedCount++
            } else { return@repeat }
        }
        if (addedCount > 0) {
            targetParty.members.sortBy { it.name.lowercase() }
            refreshActiveParties()
            Toast.makeText(this, getString(R.string.added_clones, addedCount, template.name), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Adds a reference to an existing library member to a target party.
     *
     * @param member Library member to add.
     * @param targetParty Target party.
     */
    private fun addMemberByReference(member: Member, targetParty: Party) {
        if (targetParty.members.any { it.id == member.id }) {
            Toast.makeText(this, getString(R.string.already_in_party, member.name), Toast.LENGTH_SHORT).show()
            return
        }
        targetParty.members.add(member)
        targetParty.members.sortBy { it.name.lowercase() }
        refreshActiveParties()
        Toast.makeText(this, getString(R.string.added_member, member.name), Toast.LENGTH_SHORT).show()
    }

    /**
     * Creates a new blank member in the global library and opens the edit dialog.
     *
     * @param targetParty Optional party to add the new member to.
     * @param onChanged Callback invoked after changes are saved.
     */
    private fun createNewMember(targetParty: Party? = null, onChanged: (() -> Unit)? = null) {
        val newMember = Member(name = "", classLevels = "")
        memberLibrary.add(newMember)
        when {
            targetParty != null -> {
                targetParty.members.add(newMember)
            }
        }
        saveData()
        refreshActiveParties()
        showEditMemberDialog(newMember, fromParty = targetParty, fromLibrary = targetParty == null, onChanged = onChanged)
    }

    /**
     * Shows a full-screen dialog to manage the global party library.
     */
    private fun showPartyLibraryManager() {
        val dialog = Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val libBinding = DialogLibraryBinding.inflate(layoutInflater)
        dialog.setContentView(libBinding.root)
        libBinding.tvLibraryTitle.text = getString(R.string.party_library)
        libBinding.btnCreate.text = getString(R.string.create_party)
        val adapter = object : ArrayAdapter<Party>(this, R.layout.item_party_library, partyLibrary) {
            @SuppressLint("ClickableViewAccessibility")
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_party_library, parent, false)
                val party = getItem(position)!!
                val tvName = view.findViewById<TextView>(R.id.tvPartyName)
                val cbActive = view.findViewById<CheckBox>(R.id.cbPartyActive)
                tvName.text = party.name
                tvName.setTextColor(Color.BLACK)
                cbActive.isChecked = party.isActive
                
                val cbDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        party.isActive = !party.isActive
                        refreshActiveParties()
                        notifyDataSetChanged()
                        return true
                    }
                })
                cbActive.setOnTouchListener { v, event ->
                    val handled = cbDetector.onTouchEvent(event)
                    if (!handled && event.action == MotionEvent.ACTION_UP) { v.performClick() }
                    true
                }
                tvName.setOnClickListener { editPartyDialog(party) { notifyDataSetChanged() } }
                view.setOnTouchListener(null)
                view.setOnClickListener(null)
                tvName.setOnLongClickListener(null)
                return view
            }
        }
        libBinding.lvItems.adapter = adapter
        libBinding.btnCreate.setOnClickListener { 
            val tempParty = Party(id = UUID.randomUUID(), name = "", isActive = true)
            editPartyDialog(tempParty, isNew = true) {
                adapter.notifyDataSetChanged() 
            }
        }
        dialog.show()
    }
}
