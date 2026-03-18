package com.example.cohortis

import android.app.AlertDialog
import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.cohortis.databinding.ActivityMainBinding
import com.example.cohortis.databinding.DialogEditCharacterBinding
import com.example.cohortis.databinding.DialogLibraryBinding
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

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
        setSupportActionBar(binding.toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
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
        partyLibrary = dataManager.partyLibrary.apply { sortBy { it.name.lowercase() } }
        activeParties = dataManager.activeParties
        currentRound = dataManager.currentRound

        if (partyLibrary.isEmpty()) {
            val defaultParty = Party(name = "Default Party")
            partyLibrary.add(defaultParty)
            activeParties.add(defaultParty)
            saveData()
        }
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
                onHpChanged = { char ->
                    updateAllReferences(char)
                    refreshActiveParties()
                    val nameStr = if (char.cloneTag != 0.toChar()) "${char.cloneTag})${char.name}" else char.name
                    eventFragment?.addLog("$nameStr HP changed to ${char.hpCurrent}")
                },
                onDamageTapped = { char, segment ->
                    rollDamage(char, segment)
                },
                onCharacterLongTapped = { char, party ->
                    showEditCharacterDialog(char, fromParty = party)
                },
                onOpenPartyLibrary = { _ ->
                    showPartyLibraryManager()
                },
                onPartyRenameRequested = { party ->
                    showPartyEditDialog(party)
                },
                onOpenMemberLibrary = { party ->
                    showMemberLibraryManager(party)
                },
                onNewPartyRequested = {
                    showPartyLibraryManager()
                }
            )
            refreshActiveParties()
        }
    }

    private fun refreshActiveParties() {
        activeParties.sortBy { it.name.lowercase() }
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
        
        var isLongPressActive = false
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
                isLongPressActive = true
                binding.content.roundCounterCard.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        })

        binding.content.roundCounterCard.setOnTouchListener { _, event ->
            roundGestureDetector.onTouchEvent(event)
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    isLongPressActive = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isLongPressActive) {
                        val diffX = event.x - startX
                        if (diffX < -100) { // Slide left threshold
                            currentRound = 0
                            updateRoundDisplay()
                            logRoundChange("Rounds Reset")
                            Toast.makeText(this@MainActivity, "Round Reset", Toast.LENGTH_SHORT).show()
                            isLongPressActive = false // Reset only once per press
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isLongPressActive = false
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

    private fun rollDamage(member: Member, segment: String) {
        val attackResults = DiceRoller.rollDamageSegmentDetailed(segment)
        if (attackResults.isEmpty()) return

        // Add haptic feedback
        binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        val nameStr = if (member.cloneTag != 0.toChar()) "${member.cloneTag})${member.name}" else member.name
        
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
    }

    private fun setupStepper(valueView: EditText, minusBtn: View, plusBtn: View, min: Int, max: Int) {
        minusBtn.setOnClickListener {
            val current = valueView.text.toString().toIntOrNull() ?: 0
            if (current > min) valueView.setText((current - 1).toString())
        }
        plusBtn.setOnClickListener {
            val current = valueView.text.toString().toIntOrNull() ?: 0
            if (current < max) valueView.setText((current + 1).toString())
        }
    }

    private fun showEditCharacterDialog(
        member: Member, 
        fromParty: Party? = null,
        fromLibrary: Boolean = false,
        onChanged: (() -> Unit)? = null
    ) {
        val dialogBinding = DialogEditCharacterBinding.inflate(LayoutInflater.from(this))
        
        dialogBinding.apply {
            etName.setText(member.name)
            cbIsPC.isChecked = member.isPC
            etClassLevel.setText(member.classLevels)
            etHitDice.setText(member.hitDice)
            
            etHpFull.setText(member.hpFull.toString())
            setupStepper(etHpFull, btnHpFullMinus, btnHpFullPlus, -9, 300)
            
            etHpCurrent.setText(member.hpCurrent.toString())
            setupStepper(etHpCurrent, btnHpCurrentMinus, btnHpCurrentPlus, -9, 300)
            
            etThac0.setText(member.thac0.toString())
            setupStepper(etThac0, btnThac0Minus, btnThac0Plus, 0, 20)
            
            etArmorClass.setText(member.armorClass.toString())
            setupStepper(etArmorClass, btnAcMinus, btnAcPlus, -10, 10)

            etAttacksCycle.setText(if (member.attacks.isBlank()) "1" else member.attacks)

            etDamageRolls.setText(member.damageRolls)
            etSpecialDetections.setText(member.specialDetections ?: "")
            etSpecialAttacks.setText(member.specialAttacks ?: "")

            val updateVisibility = { isPC: Boolean ->
                llClassAttacks.visibility = if (isPC) View.VISIBLE else View.GONE
                llHitDiceRow.visibility = if (isPC) View.GONE else View.VISIBLE
            }

            updateVisibility(member.isPC)
            cbIsPC.setOnCheckedChangeListener { _, isChecked -> updateVisibility(isChecked) }

            etHpFull.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (etHpFull.hasFocus()) {
                        etHpCurrent.setText(s?.toString() ?: "")
                    }
                }
            })

            btnRerollHp.setOnClickListener {
                val tempMember = member.copy(hitDice = etHitDice.text.toString())
                val rolled = tempMember.rollHp()
                etHpFull.setText(rolled.toString())
                etHpCurrent.setText(rolled.toString())
            }

            val hpFullGestureDetector = GestureDetector(this@MainActivity, object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    if (e1 == null) return false
                    val diffX = e2.x - e1.x
                    val diffY = e2.y - e1.y
                    if (abs(diffX) > abs(diffY) && diffX > 100 && abs(velocityX) > 100) {
                        etHpCurrent.setText(etHpFull.text.toString())
                        return true
                    }
                    return false
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    etHpCurrent.setText(etHpFull.text.toString())
                    return true
                }
            })
            etHpFull.setOnTouchListener { v, event ->
                hpFullGestureDetector.onTouchEvent(event)
                false 
            }
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
                    hpCurrent = dialogBinding.etHpCurrent.text.toString().toIntOrNull() ?: hpCurrent
                    hpFull = dialogBinding.etHpFull.text.toString().toIntOrNull() ?: hpFull
                    thac0 = dialogBinding.etThac0.text.toString().toIntOrNull() ?: thac0
                    armorClass = dialogBinding.etArmorClass.text.toString().toIntOrNull() ?: armorClass
                    attacks = dialogBinding.etAttacksCycle.text.toString()
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
        val options = arrayOf("Rename", "Copy", "Delete from Library")
        
        AlertDialog.Builder(this)
            .setTitle("Manage Party: ${party.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenamePartyDialog(party, onComplete)
                    1 -> showCopyPartyDialog(party, onComplete)
                    2 -> {
                        AlertDialog.Builder(this)
                            .setTitle("PERMANENT DELETE")
                            .setMessage("Delete '${party.name}' from the library forever?")
                            .setPositiveButton("DELETE") { _, _ ->
                                partyLibrary.removeAll { it.id == party.id }
                                activeParties.removeAll { it.id == party.id }
                                refreshActiveParties()
                                onComplete()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showCopyPartyDialog(party: Party, onComplete: () -> Unit = {}) {
        val input = EditText(this)
        input.setText("${party.name} (Copy)")
        AlertDialog.Builder(this)
            .setTitle("Copy Party")
            .setView(input)
            .setPositiveButton("Copy") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val copiedParty = party.copy(
                        id = UUID.randomUUID(),
                        name = newName,
                        members = party.members.map { it.clone() }.toMutableList()
                    )
                    partyLibrary.add(copiedParty)
                    partyLibrary.sortBy { it.name.lowercase() }
                    activeParties.add(copiedParty)
                    refreshActiveParties()
                    onComplete()
                    Toast.makeText(this, "Party copied as '${newName}'", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenamePartyDialog(party: Party, onComplete: () -> Unit = {}) {
        val input = EditText(this)
        input.setText(party.name)
        AlertDialog.Builder(this)
            .setTitle("Rename Party")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    partyLibrary.find { it.id == party.id }?.name = newName
                    activeParties.find { it.id == party.id }?.name = newName
                    
                    partyLibrary.sortBy { it.name.lowercase() }
                    refreshActiveParties()
                    onComplete()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Settings").setIcon(android.R.drawable.ic_menu_preferences).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1 -> {
                showSettingsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSettingsDialog() {
        val titleView = TextView(this).apply {
            text = "Cohortis\nversion 1.0.0"
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
                if (partyLibrary.none { it.name.lowercase() == imported.name.lowercase() }) {
                    val partyWithNewIds = imported.copy(
                        id = UUID.randomUUID(),
                        members = imported.members.map { it.copy(id = UUID.randomUUID()) }.toMutableList()
                    )
                    partyLibrary.add(partyWithNewIds)
                    finalPartiesAdded++
                }
            }

            saveData()
            loadData()
            refreshActiveParties()
            Toast.makeText(this, "Imported $membersAdded members and $finalPartiesAdded parties", Toast.LENGTH_LONG).show()
            return
        }

        val imported = membersQueue.removeAt(0)
        val existing = memberLibrary.find { it.id == imported.id }

        if (existing == null) {
            // No UUID collision. Check for name collision.
            if (memberLibrary.none { it.name.lowercase() == imported.name.lowercase() }) {
                memberLibrary.add(imported)
                processImportQueue(membersQueue, partiesToImport, membersAdded + 1, partiesAdded)
            } else {
                // Name collision, diff UUID. Skip as potential duplicate.
                processImportQueue(membersQueue, partiesToImport, membersAdded, partiesAdded)
            }
        } else {
            // UUID collision.
            if (existing == imported) {
                // Identical records. skip.
                processImportQueue(membersQueue, partiesToImport, membersAdded, partiesAdded)
            } else {
                // Structural difference. Ask.
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

    private fun showMemberLibraryManager(targetParty: Party? = null) {
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

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, memberLibrary.map { it.name })
        libBinding.lvItems.adapter = adapter

        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

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

            override fun onDoubleTap(e: MotionEvent): Boolean {
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
                    showEditCharacterDialog(member, fromLibrary = true, onChanged = {
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

        dialog.show()
    }

    private fun addClonesToParty(template: Member, targetParty: Party, count: Int) {
        val tags = (('a'..'z') + ('0'..'9')).toList()
        var addedCount = 0
        
        for (i in 0 until count) {
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
            } else {
                break
            }
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
        showEditCharacterDialog(newMember, fromLibrary = true, onChanged = onChanged)
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
                val isCurrentlyActive = activeParties.any { it.id == party.id }
                cbActive.isChecked = isCurrentlyActive
                
                cbActive.setOnClickListener {
                    if (cbActive.isChecked) {
                        if (activeParties.none { it.id == party.id }) activeParties.add(party)
                    } else {
                        activeParties.removeAll { it.id == party.id }
                    }
                    refreshActiveParties()
                }
                
                tvName.setOnClickListener {
                    showPartyEditDialog(party) {
                        notifyDataSetChanged()
                    }
                }
                
                return view
            }
        }
        
        libBinding.lvItems.adapter = adapter

        libBinding.btnCreate.setOnClickListener {
            createNewParty {
                adapter.notifyDataSetChanged()
            }
        }

        dialog.show()
    }

    private fun createNewParty(onAdded: (() -> Unit)? = null) {
        val input = EditText(this)
        input.hint = "Party Name"
        AlertDialog.Builder(this)
            .setTitle("Create New Party")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newParty = Party(name = name)
                    partyLibrary.add(newParty)
                    partyLibrary.sortBy { it.name.lowercase() }
                    activeParties.add(newParty)
                    refreshActiveParties()
                    onAdded?.invoke()
                } else {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                    createNewParty(onAdded)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
