package com.example.cohortis

import android.app.AlertDialog
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.cohortis.databinding.ItemCharacterBinding

class MemberAdapter(
    private var members: MutableList<Member>,
    private val onHpChanged: (Member) -> Unit,
    private val onDamageTapped: (Member, String) -> Unit,
    private val onCharacterLongTapped: (Member) -> Unit
) : RecyclerView.Adapter<MemberAdapter.MemberViewHolder>() {

    class MemberViewHolder(val binding: ItemCharacterBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemCharacterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = members[position]
        holder.binding.apply {
            val isClone = member.cloneTag != 0.toChar()
            
            val nameBase = member.name.trim().split(" ").firstOrNull() ?: ""
            val formattedName = if (nameBase.length > 9) nameBase.take(9) else nameBase
            
            val displayName = if (isClone) {
                "${member.cloneTag})$formattedName"
            } else {
                formattedName
            }
            
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
            setupDamageSpannable(tvDamage, member)
            
            ivSpecial.visibility = if (member.hasSpecial()) View.VISIBLE else View.INVISIBLE
            ivSpecial.setOnClickListener {
                showSpecialSplash(it, member)
            }

            root.setOnLongClickListener {
                onCharacterLongTapped(member)
                true
            }

            // Tapping hpCurrent opens the HpModifierDialogFragment
            tvHP.setOnClickListener {
                val activity = it.context as? AppCompatActivity
                activity?.let { act ->
                    HpModifierDialogFragment.newInstance(member) { updatedMember ->
                        updateHpDisplay(tvHP, updatedMember)
                        tvName.setTextColor(if (updatedMember.hpCurrent <= 0) Color.GRAY else defaultNameColor)
                        onHpChanged(updatedMember)
                    }.show(act.supportFragmentManager, "hp_modifier")
                }
            }

            tvHP.setOnLongClickListener {
                onCharacterLongTapped(member)
                true
            }
        }
    }

    private fun formatHitDice(hd: String): String {
        if (hd.isBlank()) return ""
        // Regex to match optional X, mandatory 'd', mandatory Y, optional +/-Z
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
        val detection = member.specialDetections ?: ""
        val attacks = member.specialAttacks ?: ""
        
        if (detection.isBlank() && attacks.isBlank()) return

        val msg = StringBuilder()

        if (detection.isNotBlank()) msg.append("Detects: $detection")
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

    private fun setupDamageSpannable(textView: TextView, member: Member) {
        val fullText = member.damageRolls
        val spannable = SpannableString(fullText)
        
        val segments = fullText.split(Regex("\\s*[|l]\\s*"))
        
        var currentPos = 0
        
        for (segment in segments) {
            if (segment.isEmpty()) continue
            
            val start = fullText.indexOf(segment, currentPos)
            if (start == -1) continue

            val end = start + segment.length
            
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    onDamageTapped(member, segment.trim())
                }
                override fun updateDrawState(ds: android.text.TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = false 
                    ds.color = textView.currentTextColor
                }
            }
            
            spannable.setSpan(clickableSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            currentPos = end
        }
        
        textView.text = spannable
        textView.movementMethod = android.text.method.LinkMovementMethod.getInstance()
    }

    override fun getItemCount(): Int = members.size

    fun updateList(newList: List<Member>) {
        members.clear()
        members.addAll(newList)
        notifyDataSetChanged()
    }
}
