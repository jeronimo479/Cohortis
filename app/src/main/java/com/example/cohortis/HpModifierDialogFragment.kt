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
 * hpCurrent (on a [PartyMember]) or hpFull (on a [Member] template).
 */
class HpModifierDialogFragment : DialogFragment() {

    private var _binding: FragmentHpModifierBinding? = null
    private val binding get() = _binding!!

    private var partyMember: PartyMember? = null
    private lateinit var template: Member
    private var isFromEdit: Boolean = false
    private var stayOpen: Boolean = false
    private var onApplied: ((PartyMember?, Member) -> Unit)? = null
    private var onComplete: ((PartyMember?, Member, Int, Int) -> Unit)? = null // pm, template, startVal, endVal
    private var onRollRequested: ((Member, String) -> Int)? = null
    private var accumulator: Int = 0

    private var originalHpFull: Int = 0
    private var originalHpCurrent: Int = 0
    private var startVal: Int = 0

    companion object {
        fun newInstance(
            partyMember: PartyMember? = null,
            template: Member,
            isFromEdit: Boolean = false,
            stayOpen: Boolean = false,
            onRollRequested: ((Member, String) -> Int)? = null,
            onComplete: ((PartyMember?, Member, Int, Int) -> Unit)? = null,
            onApplied: (PartyMember?, Member) -> Unit
        ): HpModifierDialogFragment {
            val fragment = HpModifierDialogFragment()
            fragment.partyMember = partyMember
            fragment.template = template
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

        originalHpFull = template.hpFull
        originalHpCurrent = partyMember?.hpCurrent ?: 0
        startVal = if (isFromEdit) template.hpFull else (partyMember?.hpCurrent ?: 0)

        val displayName = partyMember?.getDisplayName(template) ?: template.name
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

        binding.btnPlus.setOnClickListener {
            applyModifier(accumulator)
        }

        binding.btnMinus.setOnClickListener {
            applyModifier(-accumulator)
            // If called from party view (not isFromEdit), the (-) button should close the dialog.
            if (!isFromEdit) {
                updateDisplayAndDismiss()
            }
        }

        binding.btnBox3.setOnClickListener {
            if (isFromEdit) {
                template.hpFull = accumulator.coerceIn(0, 999)
                partyMember?.let { it.hpCurrent = template.hpFull }
            } else {
                partyMember?.let { it.hpCurrent = accumulator.coerceIn(-9, 999) }
            }
            updateDisplay()
        }
    }

    private fun setupBoxes() {
        if (isFromEdit) {
            binding.tvBox1Label.text = "HP ROLLS"
            val rawText = template.hitDice
            if (rawText.isBlank()) {
                binding.btnBox1.text = "None"
                binding.btnBox1.setOnClickListener {
                    accumulator = 0
                    updateAccumulatorDisplay()
                }
            } else {
                setupDiceSpannable(rawText)
            }
            binding.tvBox2Display.text = "${template.hpFull} : HP FULL"
        } else {
            binding.tvBox1Label.text = "HP FULL"
            binding.btnBox1.text = template.hpFull.toString()
            binding.btnBox1.setOnClickListener {
                partyMember?.let { it.hpCurrent = template.hpFull }
                updateDisplay()
            }
            binding.tvBox2Display.text = "${partyMember?.hpCurrent ?: 0} : CURRENT"
        }
    }

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

    private fun rollSegment(segment: String) {
        binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        val rollResult = onRollRequested?.invoke(template, segment) ?: DiceRoller.rollSegmentTotal(segment)
        accumulator = rollResult
        if (accumulator > 999) accumulator = 999
        updateAccumulatorDisplay()
    }

    private fun updateAccumulatorDisplay() {
        binding.btnBox3.text = accumulator.toString()
    }

    private fun applyModifier(mod: Int) {
        if (isFromEdit) {
            val oldFull = template.hpFull
            template.hpFull = (template.hpFull + mod).coerceIn(0, 999)
            partyMember?.let {
                if (it.hpCurrent >= oldFull) {
                    it.hpCurrent = template.hpFull
                } else {
                    it.hpCurrent = (it.hpCurrent + mod).coerceIn(-9, 999)
                }
            }
        } else {
            partyMember?.let {
                it.hpCurrent = (it.hpCurrent + mod).coerceIn(-9, 999)
            }
        }
        updateDisplay()
    }

    private fun updateDisplayAndDismiss() {
        updateDisplay()
        val endVal = if (isFromEdit) template.hpFull else (partyMember?.hpCurrent ?: 0)
        onComplete?.invoke(partyMember, template, startVal, endVal)
        
        binding.btnPlus.isEnabled = false
        binding.btnMinus.isEnabled = false
        binding.btnBox3.isEnabled = false
        binding.btnBox1.isEnabled = false
        binding.btnOk.isEnabled = false
        
        binding.root.postDelayed({ if (isAdded) dismiss() }, 250)
    }

    private fun updateDisplay() {
        if (isFromEdit) {
            binding.tvBox2Display.text = "${template.hpFull} : HP FULL"
        } else {
            binding.tvBox2Display.text = "${partyMember?.hpCurrent ?: 0} : CURRENT"
        }
        onApplied?.invoke(partyMember, template)
        accumulator = 0
        updateAccumulatorDisplay()
    }

    override fun onCancel(dialog: android.content.DialogInterface) {
        super.onCancel(dialog)
        template.hpFull = originalHpFull
        partyMember?.hpCurrent = originalHpCurrent
        onApplied?.invoke(partyMember, template)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
