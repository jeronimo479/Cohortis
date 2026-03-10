package com.example.cohortis2

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.view.GestureDetector
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
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.cohortis2.databinding.ActivityMainBinding
import com.example.cohortis2.databinding.DialogEditCharacterBinding
import com.example.cohortis2.databinding.DialogLibraryBinding
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dataManager: DataManager
    private var partyFragment: PartyFragment? = null
    private var eventFragment: EventFragment? = null

    private var currentRound = 1
    private var memberLibrary = mutableListOf<Member>()
    private var partyLibrary = mutableListOf<Party>()
    private var activeParties = mutableListOf<Party>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Enable edge-to-edge and handle insets for status bar, nav bar, and cutouts (camera hole)
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
        binding.content.roundCounterCard.setOnClickListener {
            currentRound++
            binding.content.tvRoundNumber.text = currentRound.toString()
            dataManager.currentRound = currentRound
            eventFragment?.addLog("Round $currentRound started")
        }
        binding.content.roundCounterCard.setOnLongClickListener {
            showRoundPickerDialog()
            true
        }
    }

    private fun showRoundPickerDialog() {
        val picker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 999
            value = currentRound
        }
        AlertDialog.Builder(this)
            .setTitle("Set Round")
            .setView(picker)
            .setPositiveButton("Set") { _, _ ->
                currentRound = picker.value
                binding.content.tvRoundNumber.text = currentRound.toString()
                dataManager.currentRound = currentRound
            }
            .setNeutralButton("Reset") { _, _ ->
                currentRound = 1
                binding.content.tvRoundNumber.text = currentRound.toString()
                dataManager.currentRound = currentRound
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun rollDamage(member: Member, segment: String) {
        val attackResults = DiceRoller.rollDamageSegmentDetailed(segment)
        if (attackResults.isEmpty()) return

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
            
            logBuilder.append("=")
                .append(result.damageExpr)
                .append("=")
                .append(result.damageTotal.toString())
        }
        
        eventFragment?.addLog(logBuilder)
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
            etHpCurrent.setText(member.hpCurrent.toString())
            etHpFull.setText(member.hpFull.toString())
            etThac0.setText(member.thac0.toString())
            etArmorClass.setText(member.armorClass.toString())
            
            var internalAttacks = if (member.attacks.isBlank()) "1" else member.attacks
            etAttacksCycle.setText(internalAttacks)

            etDamageRolls.setText(member.damageRolls)
            etSpecialDetections.setText(member.specialDetections ?: "")
            etSpecialAttacks.setText(member.specialAttacks ?: "")

            val updateVisibility = { isPC: Boolean ->
                llClassAttacks.visibility = if (isPC) View.VISIBLE else View.GONE
                tilHitDice.visibility = if (isPC) View.GONE else View.VISIBLE
                tilArmorClass.visibility = View.VISIBLE
            }

            updateVisibility(member.isPC)
            cbIsPC.setOnCheckedChangeListener { _, isChecked -> updateVisibility(isChecked) }

            val attacksSequence = listOf("1", "3/2", "2")
            fun cycleAttacks() {
                val currentIndex = attacksSequence.indexOf(internalAttacks)
                val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % attacksSequence.size
                internalAttacks = attacksSequence[nextIndex]
                etAttacksCycle.setText(internalAttacks)
            }

            etAttacksCycle.setOnClickListener { cycleAttacks() }
            
            val attacksSwipeDetector = GestureDetector(this@MainActivity, object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    if (e1 == null) return false
                    val diffY = e1.y - e2.y
                    if (diffY > 50 && abs(velocityY) > 50) {
                        cycleAttacks()
                        return true
                    }
                    return false
                }
            })
            etAttacksCycle.setOnTouchListener { v, event ->
                if (attacksSwipeDetector.onTouchEvent(event)) return@setOnTouchListener true
                if (event.action == MotionEvent.ACTION_UP) v.performClick()
                true
            }

            etHpFull.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (etHpFull.hasFocus()) {
                        etHpCurrent.setText(s?.toString() ?: "")
                    }
                }
            })

            val hpFullSwipeDetector = GestureDetector(this@MainActivity, object : GestureDetector.SimpleOnGestureListener() {
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
            })
            etHpFull.setOnTouchListener { v, event ->
                hpFullSwipeDetector.onTouchEvent(event)
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
                    Toast.makeText(this, "Party copied as '$newName'", Toast.LENGTH_SHORT).show()
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
                    // Update in library
                    partyLibrary.find { it.id == party.id }?.name = newName
                    // Update in active parties
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
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        val dt = sdf.format(Date())
        val titleView = TextView(this).apply {
            text = "Created By Jeronimo $dt\nversion 1.0.0"
            setPadding(60, 40, 60, 0)
            textSize = 14f
            setTextColor(0xFF555555.toInt())
        }
        
        val options = arrayOf("MASTER RESET")
        AlertDialog.Builder(this)
            .setCustomTitle(titleView)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showMasterResetConfirm()
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
                currentRound = 1
                refreshActiveParties()
                binding.content.tvRoundNumber.text = "1"
                eventFragment?.addLog("SYSTEM RESET COMPLETE")
                Toast.makeText(this, "All data cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMemberLibraryManager(targetParty: Party? = null) {
        val dialog = Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val libBinding = DialogLibraryBinding.inflate(layoutInflater)
        dialog.setContentView(libBinding.root)

        libBinding.tvLibraryTitle.text = "Member Library"
        libBinding.btnCreate.text = "Create Member"
        libBinding.cbClone.visibility = View.VISIBLE

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
                    if (libBinding.cbClone.isChecked) {
                        addCloneToParty(template, targetParty)
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
                    if (libBinding.cbClone.isChecked) {
                        addCloneToParty(template, targetParty)
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

    private fun addCloneToParty(template: Member, targetParty: Party) {
        val tags = ('a'..'z') + ('0'..'9')
        val existingTags = targetParty.members
            .filter { it.name == template.name && it.cloneTag != 0.toChar() }
            .map { it.cloneTag }
            .toSet()

        val availableTag = tags.firstOrNull { it !in existingTags }

        if (availableTag == null) {
            Toast.makeText(this, "Max clones (36) reached for '${template.name}' in this party", Toast.LENGTH_SHORT).show()
        } else {
            val cloned = template.clone()
            cloned.cloneTag = availableTag
            targetParty.members.add(cloned)
            targetParty.members.sortBy { it.name.lowercase() }
            refreshActiveParties()
            Toast.makeText(this, "Added ${cloned.cloneTag})${template.name}", Toast.LENGTH_SHORT).show()
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
                
                // Clicking the checkbox area toggles visibility
                cbActive.setOnClickListener {
                    if (cbActive.isChecked) {
                        if (activeParties.none { it.id == party.id }) activeParties.add(party)
                    } else {
                        activeParties.removeAll { it.id == party.id }
                    }
                    refreshActiveParties()
                }
                
                // Clicking the name opens management dialog
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
            createNewParty()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun createNewParty() {
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
                } else {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                    createNewParty()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
