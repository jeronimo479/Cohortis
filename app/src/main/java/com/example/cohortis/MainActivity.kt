package com.example.cohortis

import android.app.AlertDialog
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.util.TypedValue
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dataManager: DataManager
    private var partyFragment: PartyFragment? = null
    private var eventFragment: EventFragment? = null

    private var currentRound = 0
    private var memberLibrary = mutableListOf<Member>()
    private var partyLibrary = mutableListOf<Party>()
    private var activeParties = mutableListOf<Party>()

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
        
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        binding.btnAddParty.setOnClickListener {
            createNewParty()
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

    private fun loadData() {
        memberLibrary = dataManager.memberLibrary.apply { sortBy { it.name.lowercase() } }
        
        val loadedParties = dataManager.partyLibrary
        
        // Ensure only one priority party exists
        val priorities = loadedParties.filter { it.isPriority }
        if (priorities.size > 1) {
            priorities.drop(1).forEach { it.isPriority = false }
        }

        sortPartyLibrary(loadedParties)
        partyLibrary = loadedParties

        updateActivePartiesList()
        currentRound = dataManager.currentRound
    }

    private fun sortPartyLibrary(list: MutableList<Party>) {
        val priority = list.find { it.isPriority }
        if (priority != null) {
            list.remove(priority)
            list.sortBy { it.name.lowercase() }
            list.add(0, priority)
        } else {
            list.sortBy { it.name.lowercase() }
        }
    }

    private fun updateActivePartiesList() {
        activeParties = partyLibrary.asSequence()
            .filter { it.isActive }
            .sortedWith(compareByDescending<Party> { it.isPriority }
                .thenBy { it.name.lowercase() })
            .toMutableList()
    }

    private fun saveData() {
        dataManager.saveAll(memberLibrary, partyLibrary, activeParties, currentRound)
    }

    private fun setupFragments() {
        partyFragment = PartyFragment()
        eventFragment = EventFragment()

        supportFragmentManager.beginTransaction()
            .replace(R.id.party_container, partyFragment!!)
            .replace(R.id.event_container, eventFragment!!)
            .commitNow()

        binding.root.post {
            partyFragment?.setupRecyclerView(
                parties = activeParties,
                onHpChanged = { member, oldHp ->
                    updateAllReferences(member)
                    refreshActiveParties()
                    if (oldHp != member.hpCurrent) {
                        eventFragment?.addLog("${member.getDisplayName()} hpCurrent $oldHp -> ${member.hpCurrent}")
                    }
                },
                onDamageTapped = { member, segment ->
                    rollDamage(member, segment)
                },
                onMemberLongTapped = { member: Member, party: Party ->
                    showEditMemberDialog(member, fromParty = party)
                },
                onOpenPartyLibrary = { _ ->
                    showPartyLibraryManager()
                },
                onPartyRenameRequested = { party ->
                    showPartyEditDialog(party)
                },
                onOpenMemberLibrary = { party ->
                    showMemberLibraryManager(party)
                }
            )
            refreshActiveParties()
        }
    }

    private fun refreshActiveParties() {
        updateActivePartiesList()
        partyFragment?.updateParties(activeParties)
        saveData()
    }

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

    private fun setupRoundCounter() {
        binding.content.tvRoundNumber.text = currentRound.toString()
        
        var isLongPressing = false
        var startX = 0f

        val roundGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                currentRound++
                if (currentRound > 99) currentRound = 0
                updateRoundDisplay()
                logRoundChange("Round $currentRound started")
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (currentRound > 0) {
                    currentRound--
                    updateRoundDisplay()
                    logRoundChange("Round decreased to $currentRound")
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
                            logRoundChange("Rounds Reset")
                            isLongPressing = false // Resetted
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (event.action == MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
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

    private fun showEditMemberDialog(
        member: Member, 
        fromParty: Party? = null,
        fromLibrary: Boolean = false,
        onChanged: (() -> Unit)? = null
    ) {
        val dialogBinding = DialogEditMemberBinding.inflate(LayoutInflater.from(this))
        
        dialogBinding.apply {
            etName.setText(member.name)
            cbIsPC.isChecked = member.isPC
            etClassLevel.setText(member.classLevels)
            
            etHitDice.setText(member.hitDice)
            etHitDice.isFocusable = false
            etHitDice.setOnClickListener {
                val wasEmpty = member.hitDice.isBlank()
                val oldHpCurrent = member.hpCurrent
                SwipeDiceRollerDialogFragment.newInstance("Hit Dice", etHitDice.text.toString(), member.isPC) { diceStr ->
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
                            eventFragment?.addLog("${member.getDisplayName()} hpCurrent $oldHpCurrent -> $rolledHp")
                        }
                    }
                }.show(supportFragmentManager, "swipe_dice_hitdice")
            }
            
            btnEditHpFull.text = member.hpFull.toString()
            btnEditHpFull.setOnClickListener {
                member.hitDice = etHitDice.text.toString()
                val oldHpCurrent = member.hpCurrent
                HpModifierDialogFragment.newInstance(
                    member = member,
                    isFromEdit = true,
                    stayOpen = true,
                    onRollRequested = if (member.isPC) ({ m, s -> rollDamage(m, s) }) else null,
                    onApplied = { updatedMember ->
                        btnEditHpFull.text = updatedMember.hpFull.toString()
                        btnEditHpCurrent.text = updatedMember.hpCurrent.toString()
                        member.hpFull = updatedMember.hpFull
                        member.hpCurrent = updatedMember.hpCurrent
                        refreshActiveParties()
                        if (oldHpCurrent != updatedMember.hpCurrent) {
                            eventFragment?.addLog("${member.getDisplayName()} hpCurrent $oldHpCurrent -> ${updatedMember.hpCurrent}")
                        }
                    }
                ).show(supportFragmentManager, "hp_modifier_edit_full")
            }

            btnEditHpCurrent.text = member.hpCurrent.toString()
            btnEditHpCurrent.setOnClickListener {
                val oldHpCurrent = member.hpCurrent
                HpModifierDialogFragment.newInstance(
                    member = member,
                    isFromEdit = false,
                    stayOpen = true,
                    onRollRequested = if (member.isPC) ({ m, s -> rollDamage(m, s) }) else null,
                    onApplied = { updatedMember ->
                        btnEditHpCurrent.text = updatedMember.hpCurrent.toString()
                        member.hpCurrent = updatedMember.hpCurrent
                        refreshActiveParties()
                        if (oldHpCurrent != updatedMember.hpCurrent) {
                            eventFragment?.addLog("${member.getDisplayName()} hpCurrent $oldHpCurrent -> ${updatedMember.hpCurrent}")
                        }
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
                val wasEmpty = member.damageRolls.isBlank()
                SwipeDiceRollerDialogFragment.newInstance("Damage Rolls", etDamageRolls.text.toString(), member.isPC) { diceStr ->
                    etDamageRolls.setText(diceStr)
                    member.damageRolls = diceStr
                    if (wasEmpty && member.hitDice.isNotBlank() && member.hpFull == 1 && member.hpCurrent == 1) {
                        val rolledHp = DiceRoller.rollSegmentTotal(member.hitDice)
                        member.hpFull = rolledHp
                        member.hpCurrent = rolledHp
                        btnEditHpFull.text = rolledHp.toString()
                        btnEditHpCurrent.text = rolledHp.toString()
                        refreshActiveParties()
                        eventFragment?.addLog("${member.getDisplayName()} hpCurrent 1 -> $rolledHp")
                    }
                }.show(supportFragmentManager, "swipe_dice_damage")
            }

            etSpecialDetections.setText(member.specialDetections ?: "")
            etSpecialAttacks.setText(member.specialAttacks ?: "")

            val updateVisibility = { isPC: Boolean ->
                llClassAttacks.visibility = if (isPC) View.VISIBLE else View.GONE
            }

            updateVisibility(member.isPC)
            cbIsPC.setOnCheckedChangeListener { _, isChecked -> updateVisibility(isChecked) }
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(if (member.cloneTag != 0.toChar()) "Edit Clone" else if (fromLibrary) "Edit Library Member" else "Edit Member")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                member.apply {
                    name = dialogBinding.etName.text.toString()
                    isPC = dialogBinding.cbIsPC.isChecked
                    classLevels = dialogBinding.etClassLevel.text.toString()
                    hitDice = dialogBinding.etHitDice.text.toString()
                    
                    hpFull = dialogBinding.btnEditHpFull.text.toString().toIntOrNull() ?: hpFull
                    hpCurrent = dialogBinding.btnEditHpCurrent.text.toString().toIntOrNull() ?: hpCurrent
                    
                    thac0 = dialogBinding.etThac0.text.toString().toIntOrNull() ?: thac0
                    armorClass = dialogBinding.etArmorClass.text.toString().toIntOrNull() ?: armorClass
                    damageRolls = dialogBinding.etDamageRolls.text.toString()
                    specialDetections = dialogBinding.etSpecialDetections.text.toString()
                    specialAttacks = dialogBinding.etSpecialAttacks.text.toString()
                }
                updateAllReferences(member)
                memberLibrary.sortBy { it.name.lowercase() }
                partyLibrary.forEach { it.members.sortBy { m -> m.name.lowercase() } }
                refreshActiveParties()
                onChanged?.invoke()
            }
            .setNeutralButton("Cancel", null)

        if (fromParty != null) {
            builder.setNegativeButton("Delete") { _, _ ->
                fromParty.members.removeAll { it.id == member.id }
                refreshActiveParties()
                onChanged?.invoke()
            }
        } else if (fromLibrary) {
            builder.setNegativeButton("Delete") { _, _ ->
                memberLibrary.removeAll { it.id == member.id }
                saveData()
                refreshActiveParties()
                onChanged?.invoke()
            }
        }

        builder.show()
    }

    private fun showPartyEditDialog(party: Party, onComplete: () -> Unit = {}) {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        
        val etName = EditText(this).apply {
            setText(party.name)
            hint = "Party Name"
            setTextColor(Color.BLACK)
        }
        
        val cbActive = CheckBox(this).apply {
            text = "Active"
            isChecked = party.isActive
            setTextColor(Color.BLACK)
        }

        val cbPriority = CheckBox(this).apply {
            text = "Priority Party"
            isChecked = party.isPriority
            setTextColor(Color.BLACK)
        }
        
        dialogView.addView(etName)
        dialogView.addView(cbActive)
        dialogView.addView(cbPriority)

        val builder = AlertDialog.Builder(this)
            .setTitle("Manage Party")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = etName.text.toString().trim()
                if (newName.isNotEmpty()) {
                    party.name = newName
                    party.isActive = cbActive.isChecked
                    val wasPriority = party.isPriority
                    party.isPriority = cbPriority.isChecked
                    
                    if (party.isPriority && !wasPriority) {
                        partyLibrary.forEach { if (it.id != party.id) it.isPriority = false }
                    }
                    
                    sortPartyLibrary(partyLibrary)
                    refreshActiveParties()
                    onComplete()
                }
            }
            .setNeutralButton("Copy") { _, _ ->
                showCopyPartyDialog(party, onComplete)
            }
            .setNegativeButton("Cancel", null)

        val dialog = builder.create()
        dialog.show()

        val btnDelete = TextView(this).apply {
            text = "DELETE FROM LIBRARY"
            setTextColor(Color.RED)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 48, 0, 0)
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("PERMANENT DELETE")
                    .setMessage("Delete '${party.name}' forever?")
                    .setPositiveButton("DELETE") { _, _ ->
                        partyLibrary.removeAll { it.id == party.id }
                        activeParties.removeAll { it.id == party.id }
                        sortPartyLibrary(partyLibrary)
                        refreshActiveParties()
                        onComplete()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        dialogView.addView(btnDelete)
    }

    private fun showCopyPartyDialog(party: Party, onComplete: () -> Unit = {}) {
        val input = EditText(this)
        input.setText("${party.name} (Copy)")
        input.setTextColor(Color.BLACK)
        AlertDialog.Builder(this)
            .setTitle("Copy Party")
            .setView(input)
            .setPositiveButton("Copy") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val copiedParty = party.copy(
                        id = UUID.randomUUID(),
                        name = newName,
                        members = party.members.map { it.clone() }.toMutableList(),
                        isActive = false,
                        isPriority = false
                    )
                    partyLibrary.add(copiedParty)
                    sortPartyLibrary(partyLibrary)
                    refreshActiveParties()
                    onComplete()
                    Toast.makeText(this, "Party copied as '${newName}'", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSettingsDialog() {
        val titleView = TextView(this).apply {
            text = "Cohortis\nversion ${BuildConfig.VERSION_NAME}\nbuild ${BuildConfig.BUILD_TIME}"
            setPadding(60, 40, 60, 0)
            textSize = 14f
            setTextColor(0xFF555555.toInt())
        }
        
        val options = arrayOf("Import JSON Library", "Export JSON Library", "MASTER RESET")
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
                    2 -> showMasterResetConfirm()
                }
            }
            .show()
    }

    private fun showMasterResetConfirm() {
        AlertDialog.Builder(this)
            .setTitle("MASTER RESET")
            .setMessage("This will DELETE ALL DATA (Parties, Members, Rounds). This cannot be undone.")
            .setPositiveButton("RESET EVERYTHING") { _, _ ->
                dataManager.clearAll()
                loadData()
                currentRound = 0
                refreshActiveParties()
                binding.content.tvRoundNumber.text = "0"
                eventFragment?.addLog("SYSTEM RESET COMPLETE")
                Toast.makeText(this, "All data cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

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
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

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
                        isActive = false,
                        isPriority = false
                    )
                    partyLibrary.add(partyWithNewIds)
                    finalPartiesAdded++
                }
            }
            sortPartyLibrary(partyLibrary)
            saveData()
            loadData()
            refreshActiveParties()
            Toast.makeText(this, "Imported $membersAdded members and $finalPartiesAdded parties", Toast.LENGTH_LONG).show()
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
                    .setTitle("Import Conflict")
                    .setMessage("Member '${imported.name}' (ID: ${imported.id}) has different data than the existing record. Overwrite?")
                    .setPositiveButton("Overwrite") { _, _ ->
                        val index = memberLibrary.indexOf(existing)
                        if (index != -1) memberLibrary[index] = imported
                        processImportQueue(membersQueue, partiesToImport, membersAdded + 1, partiesAdded)
                    }
                    .setNegativeButton("Skip") { _, _ ->
                        processImportQueue(membersQueue, partiesToImport, membersAdded, partiesAdded)
                    }
                    .setNeutralButton("Import as New") { _, _ ->
                        memberLibrary.add(imported.copy(id = UUID.randomUUID()))
                        processImportQueue(membersQueue, partiesToImport, membersAdded + 1, partiesAdded)
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

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
                Toast.makeText(this, "Library exported successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun showMemberLibraryManager(targetParty: Party? = null, onDismiss: (() -> Unit)? = null) {
        val dialog = Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val libBinding = DialogLibraryBinding.inflate(layoutInflater)
        dialog.setContentView(libBinding.root)

        libBinding.tvLibraryTitle.text = "Member Library"
        libBinding.btnCreate.text = "Create Member"
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
                        libBinding.lvItems.adapter = ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_list_item_1, memberLibrary.map { it.name })
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
                libBinding.lvItems.adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, memberLibrary.map { it.name })
            }
        }

        dialog.setOnDismissListener { onDismiss?.invoke() }
        dialog.show()
    }

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
            Toast.makeText(this, "Added $addedCount clones of ${template.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addMemberByReference(member: Member, targetParty: Party) {
        if (targetParty.members.any { it.id == member.id }) {
            Toast.makeText(this, "${member.name} is already in this party", Toast.LENGTH_SHORT).show()
            return
        }
        targetParty.members.add(member)
        targetParty.members.sortBy { it.name.lowercase() }
        refreshActiveParties()
        Toast.makeText(this, "Added ${member.name}", Toast.LENGTH_SHORT).show()
    }

    private fun createNewMember(onChanged: (() -> Unit)? = null) {
        val newMember = Member(name = "", classLevels = "")
        memberLibrary.add(newMember)
        saveData()
        refreshActiveParties()
        showEditMemberDialog(newMember, fromLibrary = true, onChanged = onChanged)
    }

    private fun showPartyLibraryManager() {
        val dialog = Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val libBinding = DialogLibraryBinding.inflate(layoutInflater)
        dialog.setContentView(libBinding.root)
        libBinding.tvLibraryTitle.text = "Party Library"
        libBinding.btnCreate.text = "Create Party"
        val adapter = object : ArrayAdapter<Party>(this, R.layout.item_party_library, partyLibrary) {
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
                tvName.setOnClickListener { showPartyEditDialog(party) { notifyDataSetChanged() } }
                view.setOnTouchListener(null)
                view.setOnClickListener(null)
                tvName.setOnLongClickListener(null)
                return view
            }
        }
        libBinding.lvItems.adapter = adapter
        libBinding.btnCreate.setOnClickListener { createNewParty { adapter.notifyDataSetChanged() } }
        dialog.show()
    }

    private fun createNewParty(onAdded: (() -> Unit)? = null) {
        val tempParty = Party(id = UUID.randomUUID(), name = "", isActive = true, isPriority = false)
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        val etName = EditText(this).apply { 
            hint = "Party Name" 
            setTextColor(Color.BLACK)
        }
        
        val cbActive = CheckBox(this).apply {
            text = "Active"
            isChecked = true
            setTextColor(Color.BLACK)
        }

        val cbPriority = CheckBox(this).apply {
            text = "Priority Party"
            isChecked = false
            setTextColor(Color.BLACK)
        }

        val tvMemberCount = TextView(this).apply {
            text = "Members: 0"
            setPadding(16, 0, 0, 0)
            setTextColor(Color.BLACK)
        }
        val btnAddMembers = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_input_add)
            background = ContextCompat.getDrawable(context, android.R.drawable.btn_default)
            layoutParams = LinearLayout.LayoutParams((48 * resources.displayMetrics.density).toInt(), (48 * resources.displayMetrics.density).toInt())
            setOnClickListener {
                showMemberLibraryManager(tempParty) {
                    tvMemberCount.text = "Members: ${tempParty.members.size}"
                }
            }
        }
        val memberRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(btnAddMembers)
            addView(tvMemberCount)
        }
        dialogView.addView(etName)
        dialogView.addView(cbActive)
        dialogView.addView(cbPriority)
        dialogView.addView(memberRow)

        AlertDialog.Builder(this)
            .setTitle("Create New Party")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isNotEmpty()) {
                    tempParty.name = name
                    tempParty.isActive = cbActive.isChecked
                    tempParty.isPriority = cbPriority.isChecked
                    
                    if (tempParty.isPriority) {
                        partyLibrary.forEach { it.isPriority = false }
                    }

                    partyLibrary.add(tempParty)
                    sortPartyLibrary(partyLibrary)
                    refreshActiveParties()
                    onAdded?.invoke()
                } else {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
