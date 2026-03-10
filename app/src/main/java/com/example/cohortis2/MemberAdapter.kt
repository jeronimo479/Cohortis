package com.example.cohortis2

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.cohortis2.databinding.ItemCharacterBinding

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
            val displayName = if (isClone) {
                "${member.cloneTag})${member.name}"
            } else {
                member.name
            }
            
            tvName.text = displayName
            
            if (member.isPC) {
                // For PCs, show Attacks in the '#' column if > 1
                tvAttacks.text = if (member.attacks == "1") "" else member.attacks
            } else {
                // For Monsters, show Hit Dice in the '#' column
                tvAttacks.text = member.hitDice
            }

            tvThac0.text = member.thac0.toString()
            tvAC.text = member.armorClass.toString()
            
            updateHpDisplay(tvHP, member)
            setupDamageSpannable(tvDamage, member)
            
            ivSpecial.visibility = if (member.hasSpecial()) View.VISIBLE else View.GONE

            root.setOnLongClickListener {
                onCharacterLongTapped(member)
                true
            }

            tvHP.setOnClickListener {
                if (member.hpCurrent > 0) {
                    member.hpCurrent--
                    updateHpDisplay(tvHP, member)
                    onHpChanged(member)
                }
            }

            tvHP.setOnLongClickListener {
                onCharacterLongTapped(member)
                true
            }
        }
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
