package com.example.cohortis

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ClickableSpan
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.cohortis.databinding.FragmentHpModifierBinding

class HpModifierDialogFragment : DialogFragment() {

    private var _binding: FragmentHpModifierBinding? = null
    private val binding get() = _binding!!

    private lateinit var member: Member
    private var isFromEdit: Boolean = false
    private var onApplied: ((Member) -> Unit)? = null
    private var onRollRequested: ((Member, String) -> Int)? = null
    private var accumulator: Int = 0

    companion object {
        fun newInstance(
            member: Member,
            isFromEdit: Boolean = false,
            onRollRequested: ((Member, String) -> Int)? = null,
            onApplied: (Member) -> Unit
        ): HpModifierDialogFragment {
            val fragment = HpModifierDialogFragment()
            fragment.member = member
            fragment.isFromEdit = isFromEdit
            fragment.onRollRequested = onRollRequested
            fragment.onApplied = onApplied
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHpModifierBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val displayName = if (member.cloneTag != 0.toChar()) {
            "${member.cloneTag})${member.name}"
        } else {
            member.name
        }
        binding.tvMemberName.text = displayName
        
        setupBoxes()
        updateAccumulatorDisplay()

        // Digits
        val digitButtons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3,
            binding.btn4, binding.btn5, binding.btn6, binding.btn7,
            binding.btn8, binding.btn9
        )

        digitButtons.forEach { btn ->
            btn.setOnClickListener {
                val digit = btn.text.toString().toInt()
                accumulator = (accumulator * 10) + digit
                if (accumulator > 999) accumulator = 999
                updateAccumulatorDisplay()
            }
        }

        binding.btnClear.setOnClickListener {
            accumulator = 0
            updateAccumulatorDisplay()
        }

        // Apply modifier (+ / -)
        binding.btnPlus.setOnClickListener {
            applyModifier(accumulator)
        }

        binding.btnMinus.setOnClickListener {
            applyModifier(-accumulator)
        }

        // Tapping the Result (Box 3) pastes the exact value into Current HP (and Full if in edit mode)
        binding.btnBox3.setOnClickListener {
            if (isFromEdit) {
                member.hpFull = accumulator
                member.hpCurrent = accumulator
            } else {
                member.hpCurrent = accumulator
            }
            onApplied?.invoke(member)
            dismiss()
        }
    }

    private fun setupBoxes() {
        if (isFromEdit) {
            // Row 1: Action Button (Rolls)
            binding.tvBox1Label.text = "HP ROLLS"
            
            val rawText = member.hitDice
            if (rawText.isBlank()) {
                binding.btnBox1.text = "None"
                binding.btnBox1.setOnClickListener {
                    accumulator = 0
                    updateAccumulatorDisplay()
                }
            } else {
                setupDiceSpannable(rawText)
            }

            // Row 2: Display Only
            binding.tvBox2Display.text = "${member.hpFull} : HP FULL"
        } else {
            // Row 1: Action Button (Quick Heal)
            binding.tvBox1Label.text = "HP FULL"
            binding.btnBox1.text = member.hpFull.toString()
            binding.btnBox1.setOnClickListener {
                member.hpCurrent = member.hpFull
                onApplied?.invoke(member)
                dismiss()
            }

            // Row 2: Display Only
            binding.tvBox2Display.text = "${member.hpCurrent} : CURRENT"
        }
    }

    private fun setupDiceSpannable(rawText: String) {
        // Robust split handling pipes, commas, and lowercase Ls (often used as pipes)
        val segments = rawText.split(Regex("\\s*[|l,]\\s*")).filter { it.isNotBlank() }
        
        if (segments.size <= 1) {
            binding.btnBox1.text = rawText
            binding.btnBox1.setOnClickListener {
                rollSegment(rawText.trim())
            }
            return
        }

        val builder = SpannableStringBuilder()
        for (i in segments.indices) {
            val segmentText = segments[i].trim()
            val start = builder.length
            builder.append(segmentText)
            
            // Add padding/separator and include it in the clickable span to eliminate dead spots
            if (i < segments.size - 1) {
                builder.append("   |   ")
            }
            val end = builder.length

            val span = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    rollSegment(segmentText)
                }
                override fun updateDrawState(ds: android.text.TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = false
                    ds.color = binding.btnBox1.currentTextColor
                }
            }
            builder.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        binding.btnBox1.text = builder
        binding.btnBox1.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        // Clear the standard click listener to let spans handle touches
        binding.btnBox1.setOnClickListener(null)
        // Ensure the button is clickable to allow spans to work
        binding.btnBox1.isClickable = true
    }

    private fun rollSegment(segment: String) {
        // Haptic feedback for rolling
        binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        
        // This function REPLACES (pastes) the accumulator with the result of the roll.
        // It ensures that tapping a die combo (like d10) inserts only that result into the accumulator.
        val rollResult = onRollRequested?.invoke(member, segment) 
            ?: DiceRoller.rollSegmentTotal(segment)
        
        accumulator = rollResult
        if (accumulator > 999) accumulator = 999
        updateAccumulatorDisplay()
    }

    private fun updateAccumulatorDisplay() {
        binding.btnBox3.text = accumulator.toString()
    }

    private fun applyModifier(mod: Int) {
        member.hpCurrent += mod
        onApplied?.invoke(member)
        dismiss()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
