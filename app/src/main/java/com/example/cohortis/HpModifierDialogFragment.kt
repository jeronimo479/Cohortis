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
 */
class HpModifierDialogFragment : DialogFragment() {

    private var _binding: FragmentHpModifierBinding? = null
    private val binding get() = _binding!!

    private lateinit var member: Member
    private var isFromEdit: Boolean = false
    private var stayOpen: Boolean = false
    private var onApplied: ((Member) -> Unit)? = null
    private var onComplete: ((Member, Int, Int) -> Unit)? = null // member, startVal, endVal
    private var onRollRequested: ((Member, String) -> Int)? = null
    private var accumulator: Int = 0

    private var originalHpFull: Int = 0
    private var originalHpCurrent: Int = 0
    private var startVal: Int = 0

    companion object {
        /**
         * Creates a new instance of the HP modifier dialog.
         *
         * @param member The member whose HP is being modified.
         * @param isFromEdit If true, modifying HP Full. If false, modifying HP Current.
         * @param stayOpen If true, the dialog won't auto-dismiss after applying changes.
         * @param onRollRequested Optional callback for handling dice rolls (e.g., for logging).
         * @param onComplete Call eventLog to show accepted hp delta.
         * @param onApplied Callback invoked after the HP value has been updated.
         */
        fun newInstance(
            member: Member,
            isFromEdit: Boolean = false,
            stayOpen: Boolean = false,
            onRollRequested: ((Member, String) -> Int)? = null,
            onComplete: ((Member, Int, Int) -> Unit)? = null,
            onApplied: (Member) -> Unit
        ): HpModifierDialogFragment {
            val fragment = HpModifierDialogFragment()
            fragment.member = member
            fragment.isFromEdit = isFromEdit
            fragment.stayOpen = stayOpen
            fragment.onRollRequested = onRollRequested
            fragment.onComplete = onComplete
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

        originalHpFull = member.hpFull
        originalHpCurrent = member.hpCurrent
        startVal = if (isFromEdit) member.hpFull else member.hpCurrent

        val displayName = member.getDisplayName()
        binding.tvMemberName.text = displayName
        
        setupBoxes()
        updateAccumulatorDisplay()

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

        binding.btnOk.setOnClickListener {
            updateDisplayAndDismiss()
        }

        // Apply modifier (+ / -)
        binding.btnPlus.setOnClickListener {
            applyModifier(accumulator)
        }

        binding.btnMinus.setOnClickListener {
            applyModifier(-accumulator)
            // If called from partyBox (not isFromEdit), the (-) button should close the dialog.
            if (!isFromEdit) {
                updateDisplayAndDismiss()
            }
        }

        // Tapping the Result (Box 3) sets the value directly
        binding.btnBox3.setOnClickListener {
            if (isFromEdit) {
                member.hpFull = accumulator.coerceIn(0, 999)
                member.hpCurrent = member.hpFull
            } else {
                member.hpCurrent = accumulator.coerceIn(-9, 999)
            }
            updateDisplay()
        }
    }

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
                updateDisplay()
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
            binding.btnBox1.setOnClickListener { rollSegment(rawText.trim()) }
            return
        }

        val builder = SpannableStringBuilder()
        for (i in segments.indices) {
            val segmentText = segments[i].trim()
            val start = builder.length
            builder.append(segmentText)
            val end = builder.length

            val span = object : ClickableSpan() {
                override fun onClick(widget: View) { rollSegment(segmentText) }
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
        val rollResult = onRollRequested?.invoke(member, segment) ?: DiceRoller.rollSegmentTotal(segment)
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
            if (member.hpCurrent >= oldFull) {
                member.hpCurrent = member.hpFull
            } else {
                member.hpCurrent = (member.hpCurrent + mod).coerceIn(-9, 999)
            }
        } else {
            member.hpCurrent = (member.hpCurrent + mod).coerceIn(-9, 999)
        }
        updateDisplay()
    }

    /**
     * Updates UI and dismisses the dialog with a slight delay for visual confirmation.
     */
    private fun updateDisplayAndDismiss() {
        updateDisplay()
        val endVal = if (isFromEdit) member.hpFull else member.hpCurrent
        onComplete?.invoke(member, startVal, endVal)
        
        binding.btnPlus.isEnabled = false
        binding.btnMinus.isEnabled = false
        binding.btnBox3.isEnabled = false
        binding.btnBox1.isEnabled = false
        binding.btnOk.isEnabled = false
        
        // Display change for a very brief time, then close.
        binding.root.postDelayed({ if (isAdded) dismiss() }, 250)
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

    override fun onCancel(dialog: android.content.DialogInterface) {
        super.onCancel(dialog)
        member.hpFull = originalHpFull
        member.hpCurrent = originalHpCurrent
        onApplied?.invoke(member)
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
