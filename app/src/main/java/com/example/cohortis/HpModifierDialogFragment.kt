package com.example.cohortis

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.cohortis.databinding.FragmentHpModifierBinding

/**
 * A dialog fragment providing a calculator-like interface for modifying a member's
 * hpCurrent if called from partyFragment, or hpFull if called from editMember.
 * Supports direct value setting, addition (healing), subtraction (damage), and 
 * rolling for values based on hpDiceRoll string.
 */
class HpModifierDialogFragment : DialogFragment() {

    private var _binding: FragmentHpModifierBinding? = null
    private val binding get() = _binding!!

    private lateinit var member: Member
    private var isFromEdit: Boolean = false
    private var stayOpen: Boolean = false
    private var onApplied: ((Member) -> Unit)? = null
    private var onRollRequested: ((Member, String) -> Int)? = null
    private var accumulator: Int = 0

    companion object {
        /**
         * Creates a new instance of the HP modifier dialog.
         *
         * @param member The member whose HP is being modified.
         * @param isFromEdit If true, modifying HP Full. If false, modifying HP Current.
         * @param stayOpen If true, the dialog won't auto-dismiss after applying changes.
         * @param onRollRequested Optional callback for handling dice rolls (e.g., for logging).
         * @param onApplied Callback invoked after the HP value has been updated.
         */
        fun newInstance(
            member: Member,
            isFromEdit: Boolean = false,
            stayOpen: Boolean = false,
            onRollRequested: ((Member, String) -> Int)? = null,
            onApplied: (Member) -> Unit
        ): HpModifierDialogFragment {
            val fragment = HpModifierDialogFragment()
            fragment.member = member
            fragment.isFromEdit = isFromEdit
            fragment.stayOpen = stayOpen
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

        // Digits 0-9
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

        // Tapping the Result (Box 3) sets the value directly
        binding.btnBox3.setOnClickListener {
            val oldHp = member.hpCurrent
            if (isFromEdit) {
                member.hpFull = accumulator.coerceIn(0, 999)
                member.hpCurrent = member.hpFull // Reset current to full when explicitly setting new full
            } else {
                member.hpCurrent = accumulator.coerceIn(-9, 999)
            }
            
            // Stay open if specifically requested or if value increased (healing)
            if (stayOpen || member.hpCurrent > oldHp) {
                updateDisplay()
            } else {
                updateDisplayAndDismiss()
            }
        }
    }

    /**
     * Configures the context-sensitive buttons based on whether we are editing HP Full or HP Current.
     */
    private fun setupBoxes() {
        if (isFromEdit) {
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

            binding.tvBox2Display.text = "${member.hpFull} : HP FULL"
        } else {
            binding.tvBox1Label.text = "HP FULL"
            binding.btnBox1.text = member.hpFull.toString()
            binding.btnBox1.setOnClickListener {
                member.hpCurrent = member.hpFull
                if (stayOpen) updateDisplay() else updateDisplayAndDismiss()
            }

            binding.tvBox2Display.text = "${member.hpCurrent} : CURRENT"
        }
    }

    /**
     * Creates a clickable spannable string for members with multiple hit dice segments.
     * Dice roll strings use the following format:
     * [N*|Nx][X]dY[+Z|-Z][(,l|)[N*|Nx][X]dY[+Z|-Z]]...
     * For Example:
     *  "6x8d4+7 , d8"
     *  "8d6 , d10 | 3x5d6"
     */
    private fun setupDiceSpannable(rawText: String) {
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
            
            if (i < segments.size - 1) {
                val sepStart = builder.length
                builder.append("   |   ")
                val sepEnd = builder.length
                builder.setSpan(ForegroundColorSpan(Color.LTGRAY), sepStart, sepEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        
        binding.btnBox1.text = builder
        binding.btnBox1.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        binding.btnBox1.setOnClickListener(null)
        binding.btnBox1.isClickable = true
    }

    /**
     * Executes a dice roll for a specific segment and updates the accumulator.
     */
    private fun rollSegment(segment: String) {
        binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        val rollResult = onRollRequested?.invoke(member, segment) 
            ?: DiceRoller.rollSegmentTotal(segment)
        
        accumulator = rollResult
        if (accumulator > 999) accumulator = 999
        updateAccumulatorDisplay()
    }

    /** Updates the text display of the current accumulator value. */
    private fun updateAccumulatorDisplay() {
        binding.btnBox3.text = accumulator.toString()
    }

    /**
     * Applies a positive or negative modifier to the member's HP.
     * Includes scaling logic for HP Current when HP Full is modified.
     *
     * @param mod The amount to add or subtract.
     */
    private fun applyModifier(mod: Int) {
        if (isFromEdit) {
            val oldFull = member.hpFull
            member.hpFull = (member.hpFull + mod).coerceIn(0, 999)
            
            // If wounded, keep current relative to full or just add mod
            if (member.hpCurrent >= oldFull) {
                member.hpCurrent = member.hpFull
            } else {
                member.hpCurrent = (member.hpCurrent + mod).coerceIn(-9, 999)
            }
        } else {
            member.hpCurrent = (member.hpCurrent + mod).coerceIn(-9, 999)
        }
        
        if (stayOpen || mod > 0) {
            updateDisplay()
        } else {
            updateDisplayAndDismiss()
        }
    }

    /**
     * Updates UI and dismisses the dialog with a slight delay for visual confirmation.
     */
    private fun updateDisplayAndDismiss() {
        updateDisplay()
        binding.btnPlus.isEnabled = false
        binding.btnMinus.isEnabled = false
        binding.btnBox3.isEnabled = false
        binding.btnBox1.isEnabled = false
        
        binding.root.postDelayed({
            if (isAdded) dismiss()
        }, 250)
    }

    /**
     * Resets local state and updates the main UI display for the member.
     */
    private fun updateDisplay() {
        if (isFromEdit) {
            binding.tvBox2Display.text = "${member.hpFull} : HP FULL"
        } else {
            binding.tvBox2Display.text = "${member.hpCurrent} : CURRENT"
        }
        
        onApplied?.invoke(member)
        
        // Reset accumulator for next modifier
        accumulator = 0
        updateAccumulatorDisplay()
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
