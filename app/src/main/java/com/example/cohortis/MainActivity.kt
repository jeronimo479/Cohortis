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
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
        uri?.let { exportToJson(uri) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        dataManager = DataManager(this)
        loadData()

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
                getTemplate = { id -> memberLibrary.find { it.id == id } },
                onHpChanged = { _, _, _ ->
                    refreshActiveParties()
                },
                onHpComplete = { partyMember, template, start, end ->
                    if (start != end) {
                        eventFragment?.addLog("${partyMember.getDisplayName(template)} $start -> $end hpCurrent")
                    }
                },
                onDamageTapped = { template, segment ->
                    rollDamage(template, segment)
                },
                onMemberLongTapped = { partyMember, template, party ->
                    showEditMemberDialog(template, partyMember = partyMember, fromParty = party)
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
     * Configures the gesture detectors for the round counter UI.
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

    private fun updateRoundDisplay() {
        binding.content.tvRoundNumber.text = currentRound.toString()
        dataManager.currentRound = currentRound
    }

    private fun logRoundChange(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        eventFragment?.addLog("[$time] $message")
    }

    private fun rollDamage(template: Member, segment: String): Int {
        val attackResults = DiceRoller.rollDamageSegmentDetailed(segment)
        if (attackResults.isEmpty()) return 0

        binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        val nameStr = template.name.split(" ").firstOrNull() ?: ""
        
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

    private fun editHpDiceRolls(title: String, initialValue: String, isPC: Boolean, isHpOrHd: Boolean, onComplete: (String) -> Unit) {
        SwipeDiceRollerDialogFragment.newInstance(
            title = title,
            initialValue = initialValue,
            isPC = isPC,
            isHpOrHd = isHpOrHd,
            onDiceEntered = onComplete
        ).show(supportFragmentManager, "swipe_dice_${title.lowercase().replace(" ", "_")}")
    }

    private fun showEditMemberDialog(
        template: Member, 
        partyMember: PartyMember? = null,
        fromParty: Party? = null,
        onChanged: (() -> Unit)? = null
    ) {
        val dp = resources.displayMetrics.density
        val dialogBinding = DialogEditMemberBinding.inflate(LayoutInflater.from(this))
        
        val titleLayout = RelativeLayout(this).apply {
            val p = (16 * dp).toInt()
            setPadding(p, p, p, 0)
        }
        val tvTitle = TextView(this).apply {
            text = if (partyMember?.cloneTag != 0.toChar() && partyMember != null) getString(R.string.edit_clone) else getString(R.string.edit_member)
            setTextColor(Color.BLACK)
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        titleLayout.addView(tvTitle)

        var dialog: AlertDialog? = null

        val btnDel = TextView(this).apply {
            text = getString(R.string.delete_short)
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
                if (fromParty != null && partyMember != null) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(getString(R.string.remove_member_title))
                        .setMessage(getString(R.string.remove_member_msg, partyMember.getDisplayName(template)))
                        .setPositiveButton(getString(R.string.remove_btn)) { _, _ ->
                            fromParty.members.remove(partyMember)
                            refreshActiveParties()
                            onChanged?.invoke()
                            dialog?.dismiss()
                        }
                        .setNegativeButton(getString(R.string.skip), null)
                        .show()
                } else {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(getString(R.string.permanent_delete_title))
                        .setMessage(getString(R.string.permanent_delete_msg, template.name))
                        .setPositiveButton(getString(R.string.delete_btn)) { _, _ ->
                            memberLibrary.removeAll { it.id == template.id }
                            partyLibrary.forEach { it.members.removeAll { pm -> pm.memberId == template.id } }
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
            etName.setText(template.name)
            cbIsPC.isChecked = template.isPC
            etClassLevel.setText(template.classLevels)
            
            etHitDice.setText(template.hitDice)
            etHitDice.isFocusable = false
            etHitDice.setOnClickListener {
                val wasEmpty = template.hitDice.isBlank()
                val oldHpCurrent = partyMember?.hpCurrent ?: 0
                val editTitle = if (cbIsPC.isChecked) getString(R.string.edit_hp) else getString(R.string.edit_hd)
                editHpDiceRolls(
                    title = editTitle,
                    initialValue = etHitDice.text.toString(),
                    isPC = cbIsPC.isChecked,
                    isHpOrHd = true
                ) { diceStr ->
                    etHitDice.setText(diceStr)
                    template.hitDice = diceStr
                    if (wasEmpty && diceStr.isNotBlank()) {
                        val rolledHp = DiceRoller.rollSegmentTotal(diceStr)
                        if (partyMember != null && partyMember.hpFull > 0) {
                            partyMember.hpFull = rolledHp
                            partyMember.hpCurrent = rolledHp
                        } else {
                            template.hpFull = rolledHp
                            partyMember?.hpCurrent = rolledHp
                        }
                        btnEditHpFull.text = rolledHp.toString()
                        btnEditHpCurrent.text = rolledHp.toString()
                        
                        refreshActiveParties()
                        if (partyMember != null && oldHpCurrent != rolledHp) {
                            eventFragment?.addLog(getString(R.string.log_hp_change_simple, partyMember.getDisplayName(template), oldHpCurrent, rolledHp, "hpCurrent"))
                        }
                    }
                }
            }
            
            btnEditHpFull.text = partyMember?.getEffectiveHpFull(template)?.toString() ?: template.hpFull.toString()
            btnEditHpFull.setOnClickListener {
                template.hitDice = etHitDice.text.toString()
                HpModifierDialogFragment.newInstance(
                    partyMember = partyMember,
                    template = template,
                    isFromEdit = true,
                    stayOpen = true,
                    onRollRequested = if (template.isPC) ({ m, s -> rollDamage(m, s) }) else null,
                    onComplete = { pmRes, t, start, end ->
                        if (start != end) {
                            eventFragment?.addLog(getString(R.string.log_hp_change_simple, pmRes?.getDisplayName(t) ?: t.name, start, end, if (pmRes != null && pmRes.hpFull > 0) "hpFull (instance)" else "hpFull"))
                        }
                    },
                    onApplied = { pmRes, updatedTemplate ->
                        btnEditHpFull.text = pmRes?.getEffectiveHpFull(updatedTemplate)?.toString() ?: updatedTemplate.hpFull.toString()
                        btnEditHpCurrent.text = pmRes?.hpCurrent?.toString() ?: "0"
                        refreshActiveParties()
                    }
                ).show(supportFragmentManager, "hp_modifier_edit_full")
            }

            btnEditHpCurrent.text = partyMember?.hpCurrent?.toString() ?: "0"
            btnEditHpCurrent.isEnabled = partyMember != null
            btnEditHpCurrent.setOnClickListener {
                partyMember?.let { pm ->
                    HpModifierDialogFragment.newInstance(
                        partyMember = pm,
                        template = template,
                        isFromEdit = false,
                        stayOpen = true,
                        onRollRequested = if (template.isPC) ({ m, s -> rollDamage(m, s) }) else null,
                        onComplete = { pmRes, t, start, end ->
                            if (start != end) {
                                eventFragment?.addLog(getString(R.string.log_hp_change_simple, pmRes?.getDisplayName(t) ?: t.name, start, end, "hpCurrent"))
                            }
                        },
                        onApplied = { updatedPm, _ ->
                            btnEditHpCurrent.text = updatedPm?.hpCurrent?.toString() ?: "0"
                            refreshActiveParties()
                        }
                    ).show(supportFragmentManager, "hp_modifier_edit_current")
                }
            }
            
            etThac0.setText(template.thac0.toString())
            setupStepper(etThac0, btnThac0Minus, btnThac0Plus, 0, 20)
            
            etArmorClass.setText(template.armorClass.toString())
            setupStepper(etArmorClass, btnAcMinus, btnAcPlus, -10, 10)

            etDamageRolls.setText(template.damageRolls)
            etDamageRolls.isFocusable = false
            etDamageRolls.setOnClickListener {
                editHpDiceRolls(
                    title = getString(R.string.damage_rolls),
                    initialValue = etDamageRolls.text.toString(),
                    isPC = cbIsPC.isChecked,
                    isHpOrHd = false
                ) { diceStr ->
                    etDamageRolls.setText(diceStr)
                    template.damageRolls = diceStr
                }
            }

            etMovement.setText(template.movement ?: "")
            etSize.setText(template.size ?: "")
            etXP.setText(template.xp.toString())

            etSpecialDetections.setText(template.specialDetections ?: "")
            etSpecialAttacks.setText(template.specialAttacks ?: "")

            val updateVisibility = { isPC: Boolean ->
                llClassAttacks.visibility = if (isPC) View.VISIBLE else View.GONE
                llNpcStats.visibility = if (isPC) View.GONE else View.VISIBLE
            }

            updateVisibility(template.isPC)
            cbIsPC.setOnCheckedChangeListener { _, isChecked -> updateVisibility(isChecked) }

            btnRemoveDelete.visibility = View.GONE

            val syncSaveButton = {
                val currentText = etName.text.toString().trim()
                val originalName = template.name.trim()
                
                if (currentText.isEmpty()) {
                    btnSaveMember.isEnabled = false
                    btnSaveMember.text = getString(R.string.ok)
                } else if (currentText.equals(originalName, ignoreCase = true)) {
                    btnSaveMember.isEnabled = true
                    btnSaveMember.text = getString(R.string.ok)
                } else {
                    val isDuplicate = memberLibrary.any { it.id != template.id && it.name.equals(currentText, ignoreCase = true) }
                    if (isDuplicate) {
                        btnSaveMember.isEnabled = false
                        btnSaveMember.text = getString(R.string.dupe)
                    } else {
                        btnSaveMember.isEnabled = true
                        btnSaveMember.text = getString(R.string.ok)
                    }
                }
            }

            etName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    syncSaveButton()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
            syncSaveButton()

            btnSaveMember.setOnClickListener {
                template.apply {
                    name = etName.text.toString().trim()
                    isPC = cbIsPC.isChecked
                    classLevels = etClassLevel.text.toString()
                    hitDice = etHitDice.text.toString()
                    hpFull = if (partyMember == null || partyMember.hpFull == 0) (btnEditHpFull.text.toString().toIntOrNull() ?: hpFull) else hpFull
                    thac0 = etThac0.text.toString().toIntOrNull() ?: thac0
                    armorClass = etArmorClass.text.toString().toIntOrNull() ?: armorClass
                    damageRolls = etDamageRolls.text.toString()
                    movement = etMovement.text.toString().trim().ifBlank { null }
                    size = etSize.text.toString().trim().ifBlank { null }
                    xp = etXP.text.toString().toIntOrNull() ?: 0
                    specialDetections = etSpecialDetections.text.toString()
                    specialAttacks = etSpecialAttacks.text.toString()
                }
                if (partyMember != null && partyMember.hpFull > 0) {
                    partyMember.hpFull = btnEditHpFull.text.toString().toIntOrNull() ?: partyMember.hpFull
                }
                memberLibrary.sortBy { it.name.lowercase() }
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
                party.members.forEach { partyMember ->
                    val template = memberLibrary.find { it.id == partyMember.memberId } ?: return@forEach
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
                        text = getString(R.string.remove_short)
                        setTextColor(Color.RED)
                        val size = (32 * dp).toInt()
                        layoutParams = LinearLayout.LayoutParams(size, size)
                        setPadding(0, 0, 0, 0)
                        setOnClickListener {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle(getString(R.string.remove_member_title))
                                .setMessage(getString(R.string.remove_member_msg, partyMember.getDisplayName(template)))
                                .setPositiveButton(getString(R.string.remove_btn)) { _, _ ->
                                    party.members.remove(partyMember)
                                    refreshMembers()
                                }
                                .show()
                        }
                    }
                    val nameView = TextView(this).apply {
                        text = partyMember.getDisplayName(template, full = false)
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
                party.members.forEach { pm ->
                    val template = memberLibrary.find { it.id == pm.memberId }
                    if (template != null) {
                        pm.hpCurrent = pm.getEffectiveHpFull(template)
                    }
                }
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
                text = getString(R.string.delete_short)
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

    private fun importFromJson(uri: Uri) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                if (jsonString.isBlank()) throw Exception("Selected file is empty.")

                val gson = dataManager.gson
                
                // 1. Manually parse to identify structure and extract templates
                val root = JsonParser.parseString(jsonString)
                val importedMembers = mutableListOf<Member>()
                val importedParties = mutableListOf<Party>()
                
                val extractedTemplates = mutableMapOf<UUID, Member>()

                fun scanForMembers(jsonObj: JsonObject) {
                    // Look for "members" key which could be template list or party members list
                    if (jsonObj.has("members") && jsonObj.get("members").isJsonArray) {
                        val arr = jsonObj.getAsJsonArray("members")
                        arr.forEach { 
                            if (it.isJsonObject) {
                                val mObj = it.asJsonObject
                                // In v1, full Member had hitDice or hpFull. In v2, PartyMember only has hpCurrent.
                                if (mObj.has("name") && (mObj.has("hitDice") || mObj.has("thac0") || mObj.has("hpFull"))) {
                                    try {
                                        val m = gson.fromJson(mObj, Member::class.java)
                                        if (m != null) extractedTemplates[m.id] = m
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                    // Recursively look for parties if this is the root
                    if (jsonObj.has("parties") && jsonObj.get("parties").isJsonArray) {
                        jsonObj.getAsJsonArray("parties").forEach { 
                            if (it.isJsonObject) scanForMembers(it.asJsonObject)
                        }
                    }
                }

                if (root.isJsonObject) {
                    val rootObj = root.asJsonObject
                    scanForMembers(rootObj)
                    
                    // Now parse the actual collections using standard logic
                    val membersType = object : TypeToken<List<Member>>() {}.type
                    val partiesType = object : TypeToken<List<Party>>() {}.type
                    
                    if (rootObj.has("members")) {
                        val topMembers = gson.fromJson<List<Member>>(rootObj.get("members"), membersType) ?: emptyList()
                        topMembers.forEach { m -> extractedTemplates[m.id] = m }
                    }
                    if (rootObj.has("parties")) {
                        importedParties.addAll(gson.fromJson<List<Party>>(rootObj.get("parties"), partiesType) ?: emptyList())
                    }
                }

                importedMembers.addAll(extractedTemplates.values)

                if (importedMembers.isEmpty() && importedParties.isEmpty()) {
                    throw Exception("No valid members or parties found in file.")
                }

                eventFragment?.addLog("[$time] Importing ${importedMembers.size} members and ${importedParties.size} parties...")
                processImportQueue(importedMembers.toMutableList(), importedParties, 0, 0, 0)
            }
        } catch (e: Exception) {
            val errorMsg = "Import failed: ${e.localizedMessage}"
            eventFragment?.addLog("[$time] $errorMsg")
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun processImportQueue(
        membersQueue: MutableList<Member>,
        partiesToImport: List<Party>,
        membersNew: Int,
        membersUpdated: Int,
        partiesAdded: Int,
        idMap: MutableMap<UUID, UUID> = mutableMapOf()
    ) {
        if (membersQueue.isEmpty()) {
            var finalPartiesAdded = partiesAdded
            partiesToImport.forEach { imported ->
                if (partyLibrary.none { it.name.equals(imported.name, ignoreCase = true) }) {
                    // Update member references based on the map (resolves ID mismatches during consolidation)
                    imported.members.forEach { pm ->
                        idMap[pm.memberId]?.let { pm.memberId = it }
                    }

                    // Try name-based resolution if ID mapping didn't help (crucial for v1 -> v2 mismatches)
                    imported.members.forEach { pm ->
                        if (memberLibrary.none { it.id == pm.memberId }) {
                            pm.tempName?.let { name ->
                                memberLibrary.find { it.name.equals(name, ignoreCase = true) }?.let {
                                    pm.memberId = it.id
                                }
                            }
                        }
                    }

                    // Final sanitization: ensure all PartyMember references actually point to a known template
                    val sanitizedMembers = imported.members.filter { pm ->
                        memberLibrary.any { it.id == pm.memberId }
                    }.toMutableList()
                    
                    val partyWithSanitizedMembers = imported.copy(
                        id = UUID.randomUUID(),
                        members = sanitizedMembers,
                        isActive = false
                    )
                    partyLibrary.add(partyWithSanitizedMembers)
                    finalPartiesAdded++
                }
            }
            sortPartyLibrary(partyLibrary)
            saveData()
            loadData()
            refreshActiveParties()
            val summary = if (membersUpdated > 0) {
                getString(R.string.imported_summary_detailed, membersNew, membersUpdated, finalPartiesAdded)
            } else {
                getString(R.string.imported_summary, membersNew, finalPartiesAdded)
            }
            Toast.makeText(this, summary, Toast.LENGTH_LONG).show()
            return
        }

        val imported = membersQueue.removeAt(0)
        // Check for existing by ID first, then by name for consolidation
        val existingById = memberLibrary.find { it.id == imported.id }
        val existingByName = memberLibrary.find { it.name.equals(imported.name, ignoreCase = true) }
        val existing = existingById ?: existingByName

        if (existing == null) {
            memberLibrary.add(imported)
            idMap[imported.id] = imported.id
            processImportQueue(membersQueue, partiesToImport, membersNew + 1, membersUpdated, partiesAdded, idMap)
        } else {
            // Conflict check
            if (existing == imported || (existingByName != null && existingById == null)) {
                // If it matches exactly or just by name (preferring existing library truth), map the ID
                idMap[imported.id] = existing.id
                processImportQueue(membersQueue, partiesToImport, membersNew, membersUpdated, partiesAdded, idMap)
            } else {
                // Same ID but different data
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.import_conflict))
                    .setMessage(getString(R.string.import_conflict_msg, imported.name, imported.id))
                    .setPositiveButton(getString(R.string.overwrite)) { _, _ ->
                        val index = memberLibrary.indexOf(existing)
                        if (index != -1) memberLibrary[index] = imported
                        idMap[imported.id] = imported.id
                        processImportQueue(membersQueue, partiesToImport, membersNew, membersUpdated + 1, partiesAdded, idMap)
                    }
                    .setNegativeButton(getString(R.string.skip)) { _, _ ->
                        idMap[imported.id] = existing.id
                        processImportQueue(membersQueue, partiesToImport, membersNew, membersUpdated, partiesAdded, idMap)
                    }
                    .setNeutralButton(getString(R.string.import_as_new)) { _, _ ->
                        val newId = UUID.randomUUID()
                        memberLibrary.add(imported.copy(id = newId))
                        idMap[imported.id] = newId
                        processImportQueue(membersQueue, partiesToImport, membersNew + 1, membersUpdated, partiesAdded, idMap)
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun exportToJson(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val exportData = LibraryExport(
                    version = DataManager.CURRENT_SCHEMA_VERSION,
                    members = memberLibrary.sortedBy { it.name.lowercase() },
                    parties = partyLibrary.sortedBy { it.name.lowercase() }
                )
                val json = dataManager.gson.toJson(exportData)
                outputStream.write(json.toByteArray())
                Toast.makeText(this, getString(R.string.library_exported_success), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.export_failed, e.message), Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

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
                    val template = memberLibrary[pos]
                    showEditMemberDialog(template, onChanged = {
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

    private fun addClonesToParty(template: Member, targetParty: Party, count: Int) {
        val tags = ('a'..'z').toList() + ('0'..'9').toList()
        var addedCount = 0
        repeat(count) {
            val existingTags = targetParty.members
                .filter { it.memberId == template.id && it.cloneTag != 0.toChar() }
                .map { it.cloneTag }
                .toSet()
            val availableTag = tags.firstOrNull { it !in existingTags }
            if (availableTag != null) {
                val rolledHp = template.rollHp()
                val partyMember = PartyMember(
                    memberId = template.id,
                    hpCurrent = rolledHp,
                    hpFull = rolledHp,
                    cloneTag = availableTag
                )
                targetParty.members.add(partyMember)
                addedCount++
            } else { return@repeat }
        }
        if (addedCount > 0) {
            targetParty.members.sortBy { pm -> memberLibrary.find { it.id == pm.memberId }?.name?.lowercase() ?: "" }
            refreshActiveParties()
            Toast.makeText(this, getString(R.string.added_clones, addedCount, template.name), Toast.LENGTH_SHORT).show()
        }
    }

    private fun addMemberByReference(template: Member, targetParty: Party) {
        if (targetParty.members.any { it.memberId == template.id && it.cloneTag == 0.toChar() }) {
            Toast.makeText(this, getString(R.string.already_in_party, template.name), Toast.LENGTH_SHORT).show()
            return
        }
        targetParty.members.add(PartyMember(memberId = template.id, hpCurrent = template.hpFull, hpFull = 0))
        targetParty.members.sortBy { pm -> memberLibrary.find { it.id == pm.memberId }?.name?.lowercase() ?: "" }
        refreshActiveParties()
        Toast.makeText(this, getString(R.string.added_member, template.name), Toast.LENGTH_SHORT).show()
    }

    private fun createNewMember(targetParty: Party? = null, onChanged: (() -> Unit)? = null) {
        val newTemplate = Member(name = "", classLevels = "")
        memberLibrary.add(newTemplate)
        
        var newPartyMember: PartyMember? = null
        if (targetParty != null) {
            newPartyMember = PartyMember(memberId = newTemplate.id, hpCurrent = 0, hpFull = 0)
            targetParty.members.add(newPartyMember)
        }
        
        saveData()
        refreshActiveParties()
        showEditMemberDialog(
            template = newTemplate, 
            partyMember = newPartyMember,
            fromParty = targetParty, 
            onChanged = onChanged
        )
    }

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
