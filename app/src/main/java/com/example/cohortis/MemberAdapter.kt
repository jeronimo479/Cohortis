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
import java.util.UUID

/**
 * Recycler Adapter for displaying [PartyMember] items within a party.
 * Handles display of HP, Armor Class, THAC0, and damage rolls by referencing their [Member] template.
 */
class MemberAdapter(
    private var partyMembers: MutableList<PartyMember>,
    private val getTemplate: (UUID) -> Member?,
    private val onHpChanged: (PartyMember, Member, Int) -> Unit,
    private val onHpComplete: (PartyMember, Member, Int, Int) -> Unit,
    private val onDamageTapped: (Member, String) -> Int,
    private val onMemberLongTapped: (PartyMember, Member) -> Unit
) : RecyclerView.Adapter<MemberAdapter.MemberViewHolder>() {

    class MemberViewHolder(val binding: ItemMemberBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val partyMember = partyMembers[position]
        val template = getTemplate(partyMember.memberId)

        holder.binding.apply {
            if (template == null) {
                // Fallback for orphaned party members
                tvName.text = partyMember.tempName ?: "Unknown"
                tvName.setTextColor(Color.RED)
                tvAttacks.text = "?"
                tvThac0.text = "-"
                tvAC.text = "-"
                tvHP.text = partyMember.hpCurrent.toString()
                llDamageContainer.removeAllViews()
                ivSpecial.visibility = View.INVISIBLE
                root.setOnLongClickListener(null)
                tvHP.setOnClickListener(null)
                return
            }

            val displayName = partyMember.getDisplayName(template, full = false)
            tvName.text = displayName
            
            val defaultNameColor = tvName.textColors.defaultColor
            tvName.setTextColor(if (partyMember.hpCurrent <= 0) Color.GRAY else defaultNameColor)
            
            if (template.isPC) {
                val classChar = template.classLevels.trim().firstOrNull { it.isLetter() } ?: ""
                val levelDigits = template.classLevels.filter { it.isDigit() }
                tvAttacks.text = "$classChar$levelDigits"
            } else {
                tvAttacks.text = formatHitDice(template.hitDice)
            }

            tvThac0.text = template.thac0.toString()
            tvAC.text = template.armorClass.toString()
            
            updateHpDisplay(tvHP, partyMember, template)
            setupDamageLayout(llDamageContainer, template)
            
            ivSpecial.visibility = if (template.hasSpecial()) View.VISIBLE else View.INVISIBLE
            ivSpecial.setOnClickListener { showSpecialSplash(it, template) }

            root.setOnLongClickListener {
                onMemberLongTapped(partyMember, template)
                true
            }

            tvHP.setOnClickListener {
                val activity = it.context as? AppCompatActivity
                val oldHp = partyMember.hpCurrent
                activity?.let { act ->
                    HpModifierDialogFragment.newInstance(
                        partyMember = partyMember,
                        template = template,
                        onRollRequested = if (template.isPC) ({ m, s -> onDamageTapped(m, s) }) else null,
                        onComplete = { pm, t, start, end -> pm?.let { onHpComplete(it, t, start, end) } },
                        onApplied = { pm, t ->
                            pm?.let { nonNullPm ->
                                updateHpDisplay(tvHP, nonNullPm, t)
                                tvName.setTextColor(if (nonNullPm.hpCurrent <= 0) Color.GRAY else defaultNameColor)
                                onHpChanged(nonNullPm, t, oldHp)
                            }
                        }
                    ).show(act.supportFragmentManager, "hp_modifier")
                }
            }

            tvHP.setOnLongClickListener {
                onMemberLongTapped(partyMember, template)
                true
            }
        }
    }

    private fun formatHitDice(hd: String): String {
        if (hd.isBlank()) return ""
        val regex = Regex("""^(\d+)?d(\d+)([+-]\d+)?.*$""")
        val match = regex.find(hd.trim()) ?: return hd
        val x = match.groups[1]?.value ?: "1"
        val y = match.groups[2]?.value ?: ""
        val zStr = match.groups[3]?.value ?: ""
        val xPart = if (x == "1") "" else x
        val zValue = zStr.toIntOrNull() ?: 0
        val zPart = if (zValue == 0) "" else (if (zValue > 0) "+$zValue" else "$zValue")
        return "${xPart}d$y$zPart"
    }

    private fun showSpecialSplash(view: View, template: Member) {
        val detections = template.specialDetections ?: ""
        val attacks = template.specialAttacks ?: ""
        if (detections.isBlank() && attacks.isBlank()) return
        val msg = StringBuilder()
        if (detections.isNotBlank()) msg.append("Detects: $detections")
        if (attacks.isNotBlank()) msg.append("\n\nAttacks: $attacks")
        val dialog = AlertDialog.Builder(view.context).setTitle("${template.name} Special").setMessage(msg.toString()).create()
        dialog.show()
        Handler(Looper.getMainLooper()).postDelayed({ if (dialog.isShowing) dialog.dismiss() }, 1500)
    }

    private fun updateHpDisplay(textView: TextView, partyMember: PartyMember, template: Member) {
        textView.text = partyMember.hpCurrent.toString()
        val hpFull = partyMember.getEffectiveHpFull(template)
        val ratio = if (hpFull > 0) partyMember.hpCurrent.toFloat() / hpFull else 0f
        when {
            partyMember.hpCurrent >= hpFull -> textView.setTextColor(Color.parseColor("#008000"))
            ratio < 0.1f -> textView.setTextColor(Color.RED)
            else -> textView.setTextColor(Color.parseColor("#FFA500"))
        }
    }

    private fun setupDamageLayout(container: LinearLayout, template: Member) {
        container.removeAllViews()
        val rawText = template.damageRolls
        if (rawText.isBlank()) return
        val segments = rawText.split(Regex("\\s*[|l]\\s*")).filter { it.isNotBlank() }
        segments.forEachIndexed { index, segment ->
            val trimmed = segment.trim()
            val tv = TextView(container.context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
                text = trimmed
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                setOnClickListener { onDamageTapped(template, trimmed) }
            }
            container.addView(tv)
            if (index < segments.size - 1) {
                container.addView(TextView(container.context).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    text = "|"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    setTextColor(Color.LTGRAY)
                    alpha = 0.5f
                })
            }
        }
    }

    override fun getItemCount(): Int = partyMembers.size

    fun updateList(newList: List<PartyMember>) {
        partyMembers.clear()
        partyMembers.addAll(newList)
        notifyDataSetChanged()
    }
}
