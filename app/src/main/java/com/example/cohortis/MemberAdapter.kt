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

class MemberAdapter(
    private var members: MutableList<Member>,
    private val onHpChanged: (Member, Int) -> Unit,
    private val onDamageTapped: (Member, String) -> Int,
    private val onMemberLongTapped: (Member) -> Unit
) : RecyclerView.Adapter<MemberAdapter.MemberViewHolder>() {

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
            
            val defaultNameColor = tvName.textColors.defaultColor
            tvName.setTextColor(if (member.hpCurrent <= 0) Color.GRAY else defaultNameColor)
            
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

            // Tapping hpCurrent opens the HpModifierDialogFragment
            tvHP.setOnClickListener {
                val activity = it.context as? AppCompatActivity
                val oldHp = member.hpCurrent
                activity?.let { act ->
                    HpModifierDialogFragment.newInstance(
                        member = member,
                        // For PCs, allow individual dice roll logging. For others, keep it silent.
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

    fun updateList(newList: List<Member>) {
        members.clear()
        members.addAll(newList)
        notifyDataSetChanged()
    }
}
