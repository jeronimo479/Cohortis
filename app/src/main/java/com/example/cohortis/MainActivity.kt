package com.example.cohortis

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.gridlayout.widget.GridLayout
import com.example.cohortis.databinding.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dataManager: DataManager
    private var partyFragment: PartyFragment? = null
    private var eventFragment: EventFragment? = null

    private var memberLibrary = mutableListOf<Member>()
    private var partyLibrary = mutableListOf<Party>()
    private var activeParties = mutableListOf<Party>()

    private var currentRound: Int
        get() = dataManager.currentRound
        set(value) {
            dataManager.currentRound = value
            binding.content.tvRoundNumber.text = value.toString()
        }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { importFromJson(it) }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let { exportToJson(it) }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dataManager = DataManager(this)
        loadData()

        setupFragments()
        setupRoundCounter()

        binding.tvAppTitle.setOnClickListener {
            showSettingsDialog()
        }

        binding.btnHeaderParty.setOnClickListener {
            showPartyLibraryManager()
        }

        binding.btnHeaderParty.setOnLongClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val tempParty = Party(name = "", members = mutableListOf(), isActive = true)
            editPartyDialog(tempParty, isNew = true) {
                refreshActiveParties()
            }
            true
        }

        binding.btnHeaderMember.setOnClickListener {
            showMemberLibraryManager()
        }

        binding.btnHeaderMember.setOnLongClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            createNewMember()
            true
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navBarBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val extraMargin = if (navBarBottom > 0) navBarBottom else (16 * resources.displayMetrics.density).toInt()
            binding.content.eventContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = extraMargin
            }
            insets
        }
    }

    private fun setupFragments() {
        partyFragment = supportFragmentManager.findFragmentById(R.id.party_container) as? PartyFragment
        if (partyFragment == null) {
            partyFragment = PartyFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.party_container, partyFragment!!)
                .commitNow()
        }

        eventFragment = supportFragmentManager.findFragmentById(R.id.event_container) as? EventFragment
        if (eventFragment == null) {
            eventFragment = EventFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.event_container, eventFragment!!)
                .commitNow()
        }

        partyFragment?.let { frag ->
            frag.setupRecyclerView(
                parties = activeParties,
                getTemplate = { id -> memberLibrary.find { it.id == id } },
                onHpChanged = { _, _, _ ->
                    saveData()
                },
                onHpComplete = { pm, template, start, end ->
                    if (start != end) {
                        eventFragment?.addLog(getString(R.string.log_hp_change_simple, pm.getDisplayName(template), start, end, "hpCurrent"))
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

    private fun updateActivePartiesList() {
        activeParties.clear()
        
        val priorityId = dataManager.priorityPartyId
        if (priorityId != null) {
            val priorityParty = partyLibrary.find { it.id == priorityId }
            if (priorityParty != null) {
                priorityParty.isActive = true
                activeParties.add(priorityParty)
            }
        }

        partyLibrary.filter { it.isActive && it.id != priorityId }.forEach {
            activeParties.add(it)
        }
    }



    @SuppressLint("ClickableViewAccessibility")
    private fun setupRoundCounter() {
        binding.content.tvRoundNumber.text = currentRound.toString()

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                incrementRound()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                binding.content.roundCounterCard.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showRoundKeypadDialog()
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY)) {
                    if (diffX > 30 && kotlin.math.abs(velocityX) > 100) {
                        // Swipe Right -> Decrement Round
                        binding.content.roundCounterCard.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        decrementRound()
                        return true
                    }
                }
                return false
            }

            override fun onDown(e: MotionEvent): Boolean = true
        })

        binding.content.roundCounterCard.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                v.performClick()
            }
            gestureDetector.onTouchEvent(event)
        }
    }

    private fun incrementRound() {
        currentRound++
        binding.content.tvRoundNumber.text = currentRound.toString()
        val initRoll = kotlin.random.Random.nextInt(1, 11)
        eventFragment?.addLog(getString(R.string.round_started_init, currentRound, initRoll))
        saveData()
    }

    private fun decrementRound() {
        if (currentRound > 0) {
            currentRound--
            binding.content.tvRoundNumber.text = currentRound.toString()
            eventFragment?.addLog(getString(R.string.round_decreased, currentRound))
            saveData()
        }
    }



    private fun showRoundKeypadDialog() {
        val padBinding = DialogRoundPadBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(padBinding.root)
            .create()

        var padAccumulator = 0
        padBinding.btnAccumulator.text = padAccumulator.toString()

        val digitButtons = listOf(
            padBinding.btn0, padBinding.btn1, padBinding.btn2, padBinding.btn3,
            padBinding.btn4, padBinding.btn5, padBinding.btn6, padBinding.btn7,
            padBinding.btn8, padBinding.btn9
        )

        digitButtons.forEach { btn ->
            btn.setOnClickListener {
                val digit = btn.text.toString().toInt()
                padAccumulator = (padAccumulator * 10) + digit
                if (padAccumulator > 99) padAccumulator = 99
                padBinding.btnAccumulator.text = padAccumulator.toString()
            }
        }

        padBinding.btn0.setOnLongClickListener {
            padAccumulator = 0
            padBinding.btnAccumulator.text = padAccumulator.toString()
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            true
        }

        padBinding.btnEquals.setOnClickListener {
            setRoundDirectly(padAccumulator)
            dialog.dismiss()
        }

        padBinding.btnMinus.setOnClickListener {
            decrementRound()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setRoundDirectly(newRound: Int) {
        val clamped = newRound.coerceIn(0, 99)
        currentRound = clamped
        binding.content.tvRoundNumber.text = currentRound.toString()
        if (currentRound > 0) {
            eventFragment?.addLog(getString(R.string.round_started, currentRound))
        } else {
            eventFragment?.addLog(getString(R.string.rounds_reset))
        }
        saveData()
    }

    private fun loadData() {
        memberLibrary = dataManager.memberLibrary
        partyLibrary = dataManager.partyLibrary
        updateActivePartiesList()
    }

    private fun saveData() {
        dataManager.saveAll(memberLibrary, partyLibrary, dataManager.priorityPartyId, currentRound)
    }

    override fun onPause() {
        super.onPause()
        saveData()
    }



    private fun rollDamage(template: Member, segment: String): Int {
        val attackResults = DiceRoller.rollDamageSegmentDetailed(segment)
        var totalDmg = 0
        if (attackResults.isNotEmpty()) {
            attackResults.forEach { result ->
                totalDmg += result.damageTotal
                val logMsg = "${template.name}, d20=${result.d20}, ${result.damageExpr}=${result.damageTotal}"
                eventFragment?.addLog(logMsg)
            }
        } else {
            val total = DiceRoller.rollSegmentTotal(segment)
            eventFragment?.addLog("${template.name}, $segment=$total")
            totalDmg = total
        }
        return totalDmg
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

    /**
     * Displays a dialog to edit a [Member] template or a specific [PartyMember] instance.
     * 
     * @param template The base member data.
     * @param partyMember Optional instance data (like current HP) if editing a member within a party.
     * @param fromParty The party containing the member, if applicable (used for removal).
     * @param onChanged Callback triggered when changes are saved or the member is deleted.
     */
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
                            val name = pmRes?.getDisplayName(t) ?: t.name
                            eventFragment?.addLog(getString(R.string.log_full_hp_change, name, start, end))
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
                    movement = etMovement.text.toString().trim()
                    size = etSize.text.toString().trim()
                    xp = etXP.text.toString().toIntOrNull() ?: 0
                    specialDetections = etSpecialDetections.text.toString()
                    specialAttacks = etSpecialAttacks.text.toString()
                }
                if (partyMember != null && partyMember.hpFull > 0) {
                    partyMember.hpFull = btnEditHpFull.text.toString().toIntOrNull() ?: partyMember.hpFull
                }
                memberLibrary.sortBy { it.name.lowercase() }
                saveData()
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
     * Displays a dialog to create or edit a [Party], including its name, active status,
     * and member composition.
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
            isChecked = isNew || party.isActive
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

        val tvRosterTitle = TextView(this).apply {
            text = if (party.members.isEmpty()) "Roster (0)" else "Roster (${party.members.size})"
            setTextColor(Color.BLACK)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val cbSelectAll = CheckBox(this).apply {
            text = "All"
            setTextColor(Color.BLACK)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            isEnabled = party.members.isNotEmpty()
        }

        val rowRosterHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 4)
            addView(tvRosterTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(cbSelectAll)
        }

        val selectedMembers = mutableSetOf<PartyMember>()

        val btnAddMember = Button(this, null, 0, android.R.style.Widget_Material_Button_Small).apply {
            text = getString(R.string.add_member)
            isAllCaps = false
            isEnabled = party.name.isNotEmpty()
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnRemoveSelected = Button(this, null, 0, android.R.style.Widget_Material_Button_Small).apply {
            text = "Remove"
            isAllCaps = false
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Long-press to remove selected members", Toast.LENGTH_SHORT).show()
            }
        }

        val btnRestoreHpSelected = Button(this, null, 0, android.R.style.Widget_Material_Button_Small).apply {
            text = "Restore HP"
            isAllCaps = false
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Long-press to restore HP for selected members", Toast.LENGTH_SHORT).show()
            }
        }

        var refreshMembers: () -> Unit = {}

        fun updateActionButtonsState() {
            val hasSelection = selectedMembers.isNotEmpty()
            btnRemoveSelected.isEnabled = hasSelection
            btnRestoreHpSelected.isEnabled = hasSelection

            cbSelectAll.isEnabled = party.members.isNotEmpty()
            cbSelectAll.setOnCheckedChangeListener(null)
            cbSelectAll.isChecked = party.members.isNotEmpty() && selectedMembers.size == party.members.size
            cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedMembers.clear()
                    selectedMembers.addAll(party.members)
                } else {
                    selectedMembers.clear()
                }
                refreshMembers()
            }
        }

        refreshMembers = {
            membersContainer.removeAllViews()
            tvRosterTitle.text = if (party.members.isEmpty()) "Roster (0)" else "Roster (${party.members.size})"
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
                        gravity = Gravity.CENTER_VERTICAL
                        val params = GridLayout.LayoutParams()
                        params.width = 0
                        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        layoutParams = params
                    }
                    val cbSelect = CheckBox(this).apply {
                        isChecked = selectedMembers.contains(partyMember)
                        setTextColor(Color.BLACK)
                        setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                selectedMembers.add(partyMember)
                            } else {
                                selectedMembers.remove(partyMember)
                            }
                            updateActionButtonsState()
                        }
                    }
                    val nameView = TextView(this).apply {
                        text = partyMember.getDisplayName(template, full = false)
                        setTextColor(Color.BLACK)
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    row.addView(cbSelect)
                    row.addView(nameView)
                    membersContainer.addView(row)
                }
            }
            updateActionButtonsState()
        }
        refreshMembers()

        btnAddMember.setOnClickListener {
            showMemberLibraryManager(party) {
                refreshMembers()
            }
        }
        btnAddMember.setOnLongClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            createNewMember(party) {
                refreshMembers()
            }
            true
        }

        btnRemoveSelected.setOnLongClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            if (selectedMembers.isNotEmpty()) {
                party.members.removeAll { selectedMembers.contains(it) }
                selectedMembers.clear()
                refreshMembers()
            }
            true
        }

        btnRestoreHpSelected.setOnLongClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            if (selectedMembers.isNotEmpty()) {
                var restoredCount = 0
                party.members.forEach { pm ->
                    if (selectedMembers.contains(pm)) {
                        val template = memberLibrary.find { it.id == pm.memberId }
                        if (template != null) {
                            val oldHp = pm.hpCurrent
                            val fullHp = pm.getEffectiveHpFull(template)
                            if (oldHp != fullHp) {
                                pm.hpCurrent = fullHp
                                eventFragment?.addLog(getString(R.string.log_hp_change_simple, pm.getDisplayName(template), oldHp, fullHp, "hpCurrent"))
                                restoredCount++
                            }
                        }
                    }
                }
                refreshActiveParties()
                refreshMembers()
                Toast.makeText(this@MainActivity, "HP Restored for $restoredCount member(s)", Toast.LENGTH_SHORT).show()
            }
            true
        }

        val rowActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 4)
            addView(btnAddMember)
            addView(btnRemoveSelected)
            addView(btnRestoreHpSelected)
        }

        val updateEnabledStates = { hasName: Boolean ->
            cbActive.isEnabled = hasName
            cbPriority.isEnabled = hasName
            btnAddMember.isEnabled = hasName
            updateActionButtonsState()
            if (hasName && isNew && !cbActive.isChecked) cbActive.isChecked = true
        }

        dialogView.addView(etName)
dialogView.addView(rowCheckboxes)
dialogView.addView(rowActions)
dialogView.addView(rowRosterHeader)
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
                        .setTitle("Delete Party")
                        .setMessage("Delete party '${party.name}'?")
                        .setPositiveButton("Delete") { _, _ ->
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
                        .setNegativeButton("Cancel", null)
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
                        val fileName = "Cohortis_Library_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.json"
                        exportLauncher.launch(fileName)
                    }
                }
            }
            .show()
    }

    private fun exportToJson(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val exportData = LibraryExport(
                    version = DataManager.CURRENT_SCHEMA_VERSION,
                    members = memberLibrary.sortedBy { it.name.lowercase() },
                    parties = partyLibrary.sortedBy { it.name.lowercase() },
                    priorityPartyId = dataManager.priorityPartyId
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

    private fun importFromJson(uri: Uri) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                if (jsonString.isBlank()) throw Exception("Selected file is empty.")

                val gson = dataManager.gson
                val reader = com.google.gson.stream.JsonReader(java.io.StringReader(jsonString)).apply {
                    isLenient = true
                }
                val root = JsonParser.parseReader(reader)
                val extractedTemplates = mutableMapOf<String, Member>()
                val importedParties = mutableListOf<Party>()

                fun scanForMembers(jsonObj: JsonObject) {
                    if (jsonObj.has("members") && jsonObj.get("members").isJsonArray) {
                        jsonObj.getAsJsonArray("members").forEach { 
                            if (it.isJsonObject) {
                                val mObj = it.asJsonObject
                                val hasName = mObj.has("_name_") || mObj.has("name")
                                if (hasName && (mObj.has("hitDice") || mObj.has("thac0") || mObj.has("hpFull"))) {
                                    try {
                                        val m = gson.fromJson(mObj, Member::class.java)
                                        if (m != null && m.name.isNotBlank()) {
                                            val key = m.name.trim().lowercase()
                                            if (!extractedTemplates.containsKey(key)) {
                                                extractedTemplates[key] = m
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                    if (jsonObj.has("parties") && jsonObj.get("parties").isJsonArray) {
                        jsonObj.getAsJsonArray("parties").forEach { 
                            if (it.isJsonObject) scanForMembers(it.asJsonObject)
                        }
                    }
                }

                var importedPriorityId: UUID? = null
                if (root.isJsonObject) {
                    val rootObj = root.asJsonObject
                    val membersType = object : TypeToken<List<Member>>() {}.type
                    val partiesType = object : TypeToken<List<Party>>() {}.type
                    
                    if (rootObj.has("members") && rootObj.get("members").isJsonArray) {
                        val topMembers = gson.fromJson<List<Member>>(rootObj.get("members"), membersType) ?: emptyList()
                        topMembers.forEach { m ->
                            if (m.name.isNotBlank()) {
                                extractedTemplates[m.name.trim().lowercase()] = m
                            }
                        }
                    }

                    // Only scan embedded party members if top-level members array was missing or empty
                    if (extractedTemplates.isEmpty()) {
                        scanForMembers(rootObj)
                    }

                    if (rootObj.has("parties")) {
                        importedParties.addAll(gson.fromJson<List<Party>>(rootObj.get("parties"), partiesType) ?: emptyList())
                    }
                    
                    if (rootObj.has("priorityPartyId")) {
                        try {
                            importedPriorityId = UUID.fromString(rootObj.get("priorityPartyId").asString)
                        } catch (_: Exception) {}
                    }
                }

                val importedMembers = extractedTemplates.values.toList()
                if (importedMembers.isEmpty() && importedParties.isEmpty()) {
                    throw Exception("No valid members or parties found in file.")
                }

                eventFragment?.addLog("[$time] Importing ${importedMembers.size} members and ${importedParties.size} parties...")
                processImportQueue(importedMembers.toMutableList(), importedParties, 0, 0, 0, priorityId = importedPriorityId)
            }
        } catch (e: Exception) {
            val errorMsg = "Import failed: ${e.localizedMessage}"
            eventFragment?.addLog("[$time] $errorMsg")
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun formatMemberDetails(m: Member): String {
        val details = mutableListOf<String>()
        if (m.classLevels.isNotBlank()) details.add("Class: ${m.classLevels}")
        details.add("AC: ${m.armorClass}")
        details.add("THAC0: ${m.thac0}")
        if (m.hitDice.isNotBlank()) details.add("HD: ${m.hitDice}")
        details.add("HP: ${m.hpFull}")
        if (m.damageRolls.isNotBlank()) details.add("Dmg: ${m.damageRolls}")
        if (!m.specialAttacks.isNullOrBlank()) details.add("Attacks: ${m.specialAttacks}")
        if (!m.specialDetections.isNullOrBlank()) details.add("Special: ${m.specialDetections}")
        return details.joinToString(" | ")
    }

    private fun processImportQueue(
        membersQueue: MutableList<Member>,
        partiesToImport: List<Party>,
        membersNew: Int,
        membersUpdated: Int,
        partiesAdded: Int,
        idMap: MutableMap<UUID, UUID> = mutableMapOf(),
        priorityId: UUID? = null
    ) {
        if (membersQueue.isEmpty()) {
            var finalPartiesAdded = partiesAdded
            var finalPriorityId = priorityId
            
            partiesToImport.forEach { party ->
                var partyName = party.name.trim()
                if (partyName.isBlank()) partyName = "Imported Party"

                val sanitizedMembers = party.members.mapNotNull { pm ->
                    val mappedId = idMap[pm.memberId] ?: pm.memberId
                    val nameToFind = pm.tempName ?: pm.name
                    val template = memberLibrary.find { it.id == mappedId } 
                        ?: (if (!nameToFind.isNullOrBlank()) memberLibrary.find { it.name.equals(nameToFind, ignoreCase = true) } else null)
                    
                    if (template != null) {
                        pm.copy(memberId = template.id, name = template.name).apply { tempName = template.name }
                    } else {
                        val fallbackName = if (!nameToFind.isNullOrBlank()) nameToFind else "Imported Member"
                        val fallbackTemplate = Member(id = mappedId, name = fallbackName)
                        memberLibrary.add(fallbackTemplate)
                        pm.copy(memberId = fallbackTemplate.id, name = fallbackTemplate.name).apply { tempName = fallbackTemplate.name }
                    }
                }.toMutableList()

                val existingParty = partyLibrary.find { it.id == party.id }
                    ?: partyLibrary.find { it.name.trim().equals(partyName, ignoreCase = true) }

                if (existingParty == null) {
                    val newPartyId = UUID.randomUUID()
                    val isPriority = (party.id == priorityId)
                    if (isPriority) {
                        finalPriorityId = newPartyId
                    }
                    val importedParty = party.copy(
                        id = newPartyId,
                        name = partyName,
                        members = sanitizedMembers,
                        isActive = isPriority || party.isActive
                    )
                    partyLibrary.add(importedParty)
                    finalPartiesAdded++
                    eventFragment?.addLog("Imported Party: $partyName (${sanitizedMembers.size} members)")
                } else {
                    if (party.id == priorityId) {
                        finalPriorityId = existingParty.id
                    }
                    if (party.id == priorityId || party.isActive) {
                        existingParty.isActive = true
                    }
                    val existingKeys = existingParty.members.map { "${it.memberId}_${it.cloneTag}" }.toSet()
                    var addedCount = 0
                    sanitizedMembers.forEach { newPm ->
                        val key = "${newPm.memberId}_${newPm.cloneTag}"
                        if (key !in existingKeys) {
                            existingParty.members.add(newPm)
                            addedCount++
                        }
                    }
                    eventFragment?.addLog("Merged Party: $partyName ($addedCount members added)")
                }
            }

            memberLibrary.sortBy { it.name.lowercase() }
            sortPartyLibrary(partyLibrary)
            
            if (finalPriorityId != null) {
                dataManager.priorityPartyId = finalPriorityId
            }
            
            saveData()
            loadData()
            refreshActiveParties()

            val summary = if (membersUpdated > 0) {
                getString(R.string.imported_summary_detailed, membersNew, membersUpdated, finalPartiesAdded)
            } else {
                getString(R.string.imported_summary, membersNew, finalPartiesAdded)
            }
            eventFragment?.addLog("Summary: $summary")
            Toast.makeText(this, summary, Toast.LENGTH_LONG).show()
            return
        }

        val imported = membersQueue.removeAt(0)
        val existingById = memberLibrary.find { it.id == imported.id }
        val existingByName = memberLibrary.find { it.name.equals(imported.name, ignoreCase = true) }
        val existing = existingById ?: existingByName

        if (existing == null) {
            memberLibrary.add(imported)
            idMap[imported.id] = imported.id
            eventFragment?.addLog("Imported Member: ${imported.name}")
            processImportQueue(membersQueue, partiesToImport, membersNew + 1, membersUpdated, partiesAdded, idMap, priorityId)
        } else {
            val isIdentical = existing.name.equals(imported.name, ignoreCase = true) &&
                    existing.classLevels == imported.classLevels &&
                    existing.armorClass == imported.armorClass &&
                    existing.thac0 == imported.thac0 &&
                    existing.hitDice == imported.hitDice &&
                    existing.hpFull == imported.hpFull &&
                    existing.damageRolls == imported.damageRolls &&
                    existing.specialAttacks.orEmpty() == imported.specialAttacks.orEmpty() &&
                    existing.specialDetections.orEmpty() == imported.specialDetections.orEmpty()

            if (isIdentical) {
                idMap[imported.id] = existing.id
                processImportQueue(membersQueue, partiesToImport, membersNew, membersUpdated, partiesAdded, idMap, priorityId)
            } else {
                val conflictMsg = "Member '${imported.name}' exists in library with different stats:\n\n" +
                        "EXISTING IN LIBRARY:\n${formatMemberDetails(existing)}\n\n" +
                        "IMPORTED FROM FILE:\n${formatMemberDetails(imported)}\n\n" +
                        "Which member details do you want to keep?"

                AlertDialog.Builder(this)
                    .setTitle("Import Conflict: ${imported.name}")
                    .setMessage(conflictMsg)
                    .setPositiveButton("Overwrite") { _, _ ->
                        val index = memberLibrary.indexOf(existing)
                        if (index != -1) {
                            memberLibrary[index] = imported.copy(id = existing.id)
                        }
                        idMap[imported.id] = existing.id
                        eventFragment?.addLog("Overwrote Member: ${imported.name}")
                        processImportQueue(membersQueue, partiesToImport, membersNew, membersUpdated + 1, partiesAdded, idMap, priorityId)
                    }
                    .setNegativeButton("Keep Existing") { _, _ ->
                        idMap[imported.id] = existing.id
                        eventFragment?.addLog("Kept Existing Member: ${existing.name}")
                        processImportQueue(membersQueue, partiesToImport, membersNew, membersUpdated, partiesAdded, idMap, priorityId)
                    }
                    .setNeutralButton("Rename / Import New") { _, _ ->
                        var newName = "${imported.name} (Imported)"
                        var counter = 1
                        while (memberLibrary.any { it.name.equals(newName, ignoreCase = true) }) {
                            newName = "${imported.name} ($counter)"
                            counter++
                        }
                        val newId = UUID.randomUUID()
                        val renamedMember = imported.copy(id = newId, name = newName)
                        memberLibrary.add(renamedMember)
                        idMap[imported.id] = newId
                        eventFragment?.addLog("Imported Member as Variant: $newName")
                        processImportQueue(membersQueue, partiesToImport, membersNew + 1, membersUpdated, partiesAdded, idMap, priorityId)
                    }
                    .setCancelable(false)
                    .show()
            }
        }
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
                    cloneTag = availableTag,
                    name = template.name
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
        val isInAnyParty = partyLibrary.any { party ->
            party.members.any { it.memberId == template.id && it.cloneTag == 0.toChar() }
        }
        if (isInAnyParty) {
            Toast.makeText(this, getString(R.string.already_in_party, template.name), Toast.LENGTH_SHORT).show()
            return
        }
        val rolledHp = template.rollHp()
        val partyMember = PartyMember(
            memberId = template.id,
            hpCurrent = rolledHp,
            hpFull = rolledHp,
            name = template.name
        )
        targetParty.members.add(partyMember)
        targetParty.members.sortBy { pm -> memberLibrary.find { it.id == pm.memberId }?.name?.lowercase() ?: "" }
        refreshActiveParties()
        Toast.makeText(this, getString(R.string.added_member, template.name), Toast.LENGTH_SHORT).show()
    }

    private fun createNewMember(targetParty: Party? = null, onCreated: (() -> Unit)? = null) {
        val newTemplate = Member(name = "", classLevels = "")
        
        val newPartyMember = if (targetParty != null) {
            PartyMember(memberId = newTemplate.id, hpCurrent = 0, hpFull = 0, name = newTemplate.name)
        } else null
        
        showEditMemberDialog(
            template = newTemplate, 
            partyMember = newPartyMember,
            fromParty = targetParty, 
            onChanged = {
                if (newTemplate.name.isNotBlank()) {
                    if (!memberLibrary.any { it.id == newTemplate.id }) {
                        memberLibrary.add(newTemplate)
                    }
                    if (targetParty != null && newPartyMember != null && !targetParty.members.any { it === newPartyMember }) {
                        targetParty.members.add(newPartyMember)
                    }
                    saveData()
                    refreshActiveParties()
                }
                onCreated?.invoke()
            }
        )
    }

    private fun showPartyLibraryManager() {
        val dialog = Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val libBinding = DialogLibraryBinding.inflate(layoutInflater)
        dialog.setContentView(libBinding.root)

        libBinding.tvLibraryTitle.text = getString(R.string.party_library)
        libBinding.btnAddMemberTemplate.visibility = View.GONE
        libBinding.llCloneContainer.visibility = View.GONE

        val updateList = {
            val scrollIndex = libBinding.lvItems.firstVisiblePosition
            val scrollOffset = libBinding.lvItems.getChildAt(0)?.top ?: 0

            val names = partyLibrary.map { p ->
                if (p.id == dataManager.priorityPartyId) "${p.name} (*)" else p.name
            }.toMutableList()

            @Suppress("UNCHECKED_CAST")
            val existingAdapter = libBinding.lvItems.adapter as? ArrayAdapter<String>
            if (existingAdapter != null) {
                existingAdapter.clear()
                existingAdapter.addAll(names)
                existingAdapter.notifyDataSetChanged()
            } else {
                libBinding.lvItems.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
            }

            libBinding.lvItems.setSelectionFromTop(scrollIndex, scrollOffset)
        }
        updateList()

        libBinding.lvItems.setOnItemClickListener { _, _, position, _ ->
            val party = partyLibrary[position]
            party.isActive = true
            refreshActiveParties()
            saveData()
            Toast.makeText(this, "Added '${party.name}' to main screen", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        libBinding.lvItems.setOnItemLongClickListener { _, _, position, _ ->
            val party = partyLibrary[position]
            editPartyDialog(party) {
                updateList()
            }
            true
        }

        libBinding.btnPgUp.setOnClickListener {
            val visibleCount = libBinding.lvItems.lastVisiblePosition - libBinding.lvItems.firstVisiblePosition
            val pageStep = if (visibleCount > 0) visibleCount else 8
            val newPos = (libBinding.lvItems.firstVisiblePosition - pageStep).coerceAtLeast(0)
            libBinding.lvItems.setSelection(newPos)
        }
        libBinding.btnPgUp.setOnLongClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            libBinding.lvItems.setSelection(0)
            true
        }

        libBinding.btnPgDn.setOnClickListener {
            val visibleCount = libBinding.lvItems.lastVisiblePosition - libBinding.lvItems.firstVisiblePosition
            val pageStep = if (visibleCount > 0) visibleCount else 8
            val maxIndex = partyLibrary.size - 1
            val newPos = (libBinding.lvItems.firstVisiblePosition + pageStep).coerceAtMost(maxIndex.coerceAtLeast(0))
            libBinding.lvItems.setSelection(newPos)
        }
        libBinding.btnPgDn.setOnLongClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val maxIndex = partyLibrary.size - 1
            if (maxIndex >= 0) {
                libBinding.lvItems.setSelection(maxIndex)
            }
            true
        }

        dialog.show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showMemberLibraryManager(targetParty: Party? = null, onDismiss: (() -> Unit)? = null) {
        val dialog = Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val libBinding = DialogLibraryBinding.inflate(layoutInflater)
        dialog.setContentView(libBinding.root)

        libBinding.btnAddMemberTemplate.visibility = View.VISIBLE

        if (targetParty != null) {
            libBinding.tvLibraryTitle.text = getString(R.string.member_library_from, targetParty.name.ifEmpty { getString(R.string.new_party) })
        } else {
            libBinding.tvLibraryTitle.text = getString(R.string.member_library)
        }

        libBinding.llCloneContainer.visibility = if (targetParty != null) View.VISIBLE else View.GONE

        var cloneCount = 0
        libBinding.tvCloneCount.text = cloneCount.toString()

        libBinding.btnCloneMinus.setOnClickListener {
            if (cloneCount > 0) {
                cloneCount--
                libBinding.tvCloneCount.text = cloneCount.toString()
            }
        }

        libBinding.btnClonePlus.setOnClickListener {
            if (cloneCount < 36) {
                cloneCount++
                libBinding.tvCloneCount.text = cloneCount.toString()
            }
        }

        val updateMemberList = {
            val scrollIndex = libBinding.lvItems.firstVisiblePosition
            val scrollOffset = libBinding.lvItems.getChildAt(0)?.top ?: 0

            val names = memberLibrary.map { it.name }.toMutableList()

            @Suppress("UNCHECKED_CAST")
            val existingAdapter = libBinding.lvItems.adapter as? ArrayAdapter<String>
            if (existingAdapter != null) {
                existingAdapter.clear()
                existingAdapter.addAll(names)
                existingAdapter.notifyDataSetChanged()
            } else {
                libBinding.lvItems.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
            }

            libBinding.lvItems.setSelectionFromTop(scrollIndex, scrollOffset)
        }
        updateMemberList()

        libBinding.btnPgUp.setOnClickListener {
            val visibleCount = libBinding.lvItems.lastVisiblePosition - libBinding.lvItems.firstVisiblePosition
            val pageStep = if (visibleCount > 0) visibleCount else 8
            val newPos = (libBinding.lvItems.firstVisiblePosition - pageStep).coerceAtLeast(0)
            libBinding.lvItems.setSelection(newPos)
        }
        libBinding.btnPgUp.setOnLongClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            libBinding.lvItems.setSelection(0)
            true
        }

        libBinding.btnPgDn.setOnClickListener {
            val visibleCount = libBinding.lvItems.lastVisiblePosition - libBinding.lvItems.firstVisiblePosition
            val pageStep = if (visibleCount > 0) visibleCount else 8
            val maxIndex = memberLibrary.size - 1
            val newPos = (libBinding.lvItems.firstVisiblePosition + pageStep).coerceAtMost(maxIndex.coerceAtLeast(0))
            libBinding.lvItems.setSelection(newPos)
        }
        libBinding.btnPgDn.setOnLongClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val maxIndex = memberLibrary.size - 1
            if (maxIndex >= 0) {
                libBinding.lvItems.setSelection(maxIndex)
            }
            true
        }

        libBinding.btnAddMemberTemplate.setOnClickListener {
            createNewMember(targetParty) {
                updateMemberList()
            }
        }

        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean { return true }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val pos = libBinding.lvItems.pointToPosition(e.x.toInt(), e.y.toInt())
                if (pos != -1) {
                    val template = memberLibrary[pos]
                    if (targetParty != null) {
                        if (cloneCount > 0) {
                            addClonesToParty(template, targetParty, cloneCount)
                        } else {
                            addMemberByReference(template, targetParty)
                        }
                    } else {
                        showEditMemberDialog(template, onChanged = {
                            updateMemberList()
                        })
                    }
                }
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                val pos = libBinding.lvItems.pointToPosition(e.x.toInt(), e.y.toInt())
                if (pos != -1) {
                    val template = memberLibrary[pos]
                    showEditMemberDialog(template, onChanged = {
                        updateMemberList()
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

        dialog.setOnDismissListener {
            onDismiss?.invoke()
        }
        dialog.show()
    }

    private fun sortPartyLibrary(list: MutableList<Party>) {
        list.sortBy { it.name.lowercase() }
    }
}
