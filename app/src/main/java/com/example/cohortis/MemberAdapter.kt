package com.example.cohortis

import android.app.AlertDialog
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.cohortis.databinding.ItemMemberBinding

/**
 * Recycler Adapter for displaying [Member] items within a party.
 * Handles display of HP, Armor Class, THAC0, and damage rolls.
 *
 * @param members The list of members in the party.
 * @param onHpChanged Callback when HP is updated (e.g., via HP modifier dialog).
 * @param onDamageTapped Callback when an attack roll is requested.
 * @param onMemberLongTapped Callback for editing or deleting a member.
 */
class MemberAdapter(
    private var members: MutableList<Member>,
    private val onHpChanged: (Member, Int) -> Unit,
    private val onDamageTapped: (Member, String) -> Int,
    private val onMemberLongTapped: (Member) -> Unit
) : RecyclerView.Adapter<MemberAdapter.MemberViewHolder>() {

    /**
     * ViewHolder for a single Member item.
     */
    class MemberViewHolder(val binding: ItemMemberBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = members[position]
        holder.binding.apply {
            val displayName = member.getDisplayName()
            tvName.text = displayName
            
            // Fade name if dead (HP <= 0)
            val defaultNameColor = tvName.textColors.defaultColor
            tvName.setTextColor(if (member.hpCurrent <= 0) Color.GRAY else defaultNameColor)
            
            // Display Class/Level for PCs, or formatted Hit Dice for NPCs
            if (member.isPC) {
                val classChar = member.classLevels.trim().firstOrNull { it.isLetter() } ?: ""
                val levelDigits = member.classLevels.filter { it.isDigit() }
                tvAttacks.text = "$classChar$levelDigits"
            } else {
                tvAttacks.text = formatHitDice(member.hitDice)
            }

            tvThac0.text = member.thac0.toString()
            tvAC.text = member.armorClass.toString()
            
            updateHpDisplay(tvHP, member)
            setupDamageLayout(llDamageContainer, member)
            
            ivSpecial.visibility = if (member.hasSpecial()) View.VISIBLE else View.INVISIBLE
            ivSpecial.setOnClickListener {
                showSpecialSplash(it, member)
            }

            root.setOnLongClickListener {
                onMemberLongTapped(member)
                true
            }

            // Tapping HP value opens the quick HP modifier tool
            tvHP.setOnClickListener {
                val activity = it.context as? AppCompatActivity
                val oldHp = member.hpCurrent
                activity?.let { act ->
                    HpModifierDialogFragment.newInstance(
                        member = member,
                        onRollRequested = if (member.isPC) ({ m, s -> onDamageTapped(m, s) }) else null,
                        onApplied = { updatedMember ->
                            updateHpDisplay(tvHP, updatedMember)
                            tvName.setTextColor(if (updatedMember.hpCurrent <= 0) Color.GRAY else defaultNameColor)
                            onHpChanged(updatedMember, oldHp)
                        }
                    ).show(act.supportFragmentManager, "hp_modifier")
                }
            }

            tvHP.setOnLongClickListener {
                onMemberLongTapped(member)
                true
            }
        }
    }

    /**
     * Formats a hit dice string into a concise display format (e.g., "1d8+2" -> "d8+2").
     * A roll segment will have the format: "[X] dY [+Z|-Z]". This will show the rolls
     * using the fewest number of characters possible. 
     *  If X=1, don't show it.
     *  If Z=0, don't show it.
     *  The minimum require is "dY" where Y represents an unsigned int from 1..999.
     */
    private fun formatHitDice(hd: String): String {
        if (hd.isBlank()) return ""
        val regex = Regex("""^(\d+)?d(\d+)([+-]\d+)?.*$""")
        val match = regex.find(hd.trim()) ?: return hd

        val x = match.groups[1]?.value ?: "1"
        val y = match.groups[2]?.value ?: ""
        val zStr = match.groups[3]?.value ?: ""

        val xPart = if (x == "1") "" else x
        val zValue = zStr.toIntOrNull() ?: 0
        val zPart = if (zValue == 0) "" else {
            if (zValue > 0) "+$zValue" else "$zValue"
        }

        return "${xPart}d$y$zPart"
    }

    /**
     * Shows a brief dialog displaying special detection/attack notes.
     * Automatically dismisses after 1.5 seconds.
     */
    private fun showSpecialSplash(view: View, member: Member) {
        val detections = member.specialDetections ?: ""
        val attacks = member.specialAttacks ?: ""
        
        if (detections.isBlank() && attacks.isBlank()) return

        val msg = StringBuilder()
        if (detections.isNotBlank()) msg.append("Detects: $detections")
        if (attacks.isNotBlank()) msg.append("\n\nAttacks: $attacks")

        val dialog = AlertDialog.Builder(view.context)
            .setTitle("${member.name} Special")
            .setMessage(msg.toString())
            .create()
            
        dialog.show()
        
        Handler(Looper.getMainLooper()).postDelayed({
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }, 1500)
    }

    /**
     * Updates the HP text and color based on current/max HP ratio. Green for 
     * Full health, Yellow for injured, Red for near death (< 10% of Full) 
     */
    private fun updateHpDisplay(textView: TextView, member: Member) {
        textView.text = member.hpCurrent.toString()
        val ratio = if (member.hpFull > 0) member.hpCurrent.toFloat() / member.hpFull else 0f
        
        when {
            member.hpCurrent >= member.hpFull -> {
                textView.setTextColor(Color.parseColor("#008000")) // Green
            }
            ratio < 0.1f -> {
                textView.setTextColor(Color.RED)
            }
            else -> {
                textView.setTextColor(Color.parseColor("#FFA500")) // Orange
            }
        }
    }

    /**
     * Dynamically adds clickable [TextView]s for each damage roll segment.
     * Segments are separated by pipes or 'l' characters in the [Member.damageRolls] string.
     */
    private fun setupDamageLayout(container: LinearLayout, member: Member) {
        container.removeAllViews()
        val rawText = member.damageRolls
        if (rawText.isBlank()) return

        val segments = rawText.split(Regex("\\s*[|l]\\s*")).filter { it.isNotBlank() }
        
        segments.forEachIndexed { index, segment ->
            val trimmed = segment.trim()
            val tv = TextView(container.context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
                text = trimmed
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                setOnClickListener { onDamageTapped(member, trimmed) }
            }
            container.addView(tv)
            
            if (index < segments.size - 1) {
                val divider = TextView(container.context).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    text = "|"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    setTextColor(Color.LTGRAY)
                    alpha = 0.5f
                }
                container.addView(divider)
            }
        }
    }

    override fun getItemCount(): Int = members.size

    /**
     * Updates the list of members and refreshes the RecyclerView.
     */
    fun updateList(newList: List<Member>) {
        members.clear()
        members.addAll(newList)
        notifyDataSetChanged()
    }
}
