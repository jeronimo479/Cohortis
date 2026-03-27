package com.example.cohortis

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.cohortis.databinding.DialogSwipeDiceRollerBinding

/**
 * A dialog fragment providing a specialized UI for entering and editing complex dice expressions.
 * It allows users to cycle through different segments of a dice roll (e.g., "1d8 | 2d6")
 * and modify individual components (repeat count, dice count, sides, modifier).
 */
class SwipeDiceRollerDialogFragment : DialogFragment() {

    private var _binding: DialogSwipeDiceRollerBinding? = null
    private val binding get() = _binding!!

    private var onDiceEntered: ((String) -> Unit)? = null
    private var initialValue: String = ""
    private var title: String = "Dice Roller"
    private var isPC: Boolean = false
    private var isHpOrHd: Boolean = false

    private val diceSegments = mutableListOf<DiceSegment>()
    private var currentIndex = 0
    private var selectedField = Field.X
    private var isFirstDigitAfterSelection = true

    /**
     * Represents the specific part of a dice expression currently being edited.
     */
    enum class Field { 
        /** Repeat count (N) in "Nx XdY+Z". */
        N, 
        /** Number of dice (X) in "Nx XdY+Z". */
        X, 
        /** Number of sides (Y) in "Nx XdY+Z". */
        Y, 
        /** Modifier (Z) in "Nx XdY+Z". */
        Z 
    }

    /**
     * Internal data structure representing a single segment of a complex dice roll.
     */
    data class DiceSegment(
        var n: Int = 1,
        var x: Int = 1,
        var y: Int = 8,
        var z: Int = 0,
        var isPositive: Boolean = true,
        var isFollowedByComma: Boolean = false
    )

    companion object {
        /**
         * Creates a new instance of the swipe dice roller.
         *
         * @param title The title displayed at the top of the dialog.
         * @param initialValue The starting dice string to edit.
         * @param isPC If true, defaults selection to sides (Y) instead of count (X).
         * @param isHpOrHd If true, enables the PC/NPC specific field selection logic.
         * @param onDiceEntered Callback invoked whenever the dice string changes.
         */
        fun newInstance(
            title: String, 
            initialValue: String, 
            isPC: Boolean = false, 
            isHpOrHd: Boolean = false,
            onDiceEntered: (String) -> Unit
        ): SwipeDiceRollerDialogFragment {
            val fragment = SwipeDiceRollerDialogFragment()
            fragment.title = title
            fragment.initialValue = initialValue
            fragment.isPC = isPC
            fragment.isHpOrHd = isHpOrHd
            fragment.onDiceEntered = onDiceEntered
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogSwipeDiceRollerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvTitle.text = title

        parseInitialValue()
        if (diceSegments.isEmpty()) {
            diceSegments.add(DiceSegment())
        }
        
        // PCs often just change die type (d8 vs d10) for HP/HD, NPCs often change count (2d8 vs 3d8)
        selectedField = if (isHpOrHd) {
            if (isPC) Field.Y else Field.X
        } else {
            Field.X
        }
        isFirstDigitAfterSelection = true

        setupSelectionListeners()
        setupKeypad()
        setupNavigation()
        updateUI()
    }

    /**
     * Configures click listeners for the individual fields (N, X, Y, Z) and special buttons.
     */
    private fun setupSelectionListeners() {
        binding.pickerLayout.apply {
            tvN.setOnClickListener { selectField(Field.N) }
            tvX.setOnClickListener { selectField(Field.X) }
            tvY.setOnClickListener { selectField(Field.Y) }
            tvZ.setOnClickListener { selectField(Field.Z) }
            
            btnSign.setOnClickListener {
                diceSegments[currentIndex].isPositive = !diceSegments[currentIndex].isPositive
                selectField(Field.Z)
            }
        }
        
        // Hide comma for Hit Dice as they use pipes for segments
        if (title.contains("Hit Dice", ignoreCase = true)) {
            binding.btnComma.visibility = View.GONE
        }

        binding.btnComma.setOnClickListener {
            diceSegments[currentIndex].isFollowedByComma = true
            diceSegments.add(currentIndex + 1, DiceSegment())
            currentIndex++
            selectField(if (isHpOrHd && isPC) Field.Y else Field.X)
        }

        binding.tvPageIndicator.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            diceSegments.add(0, DiceSegment())
            currentIndex = 0
            selectField(if (isHpOrHd && isPC) Field.Y else Field.X)
            true
        }
    }

    /**
     * Sets the currently active field for editing.
     */
    private fun selectField(field: Field) {
        selectedField = field
        isFirstDigitAfterSelection = true
        updateUI()
    }

    /**
     * Sets up the numeric keypad and deletion buttons.
     */
    private fun setupKeypad() {
        val digitButtons = listOf(
            binding.btn0 to 0, binding.btn1 to 1, binding.btn2 to 2,
            binding.btn3 to 3, binding.btn4 to 4, binding.btn5 to 5,
            binding.btn6 to 6, binding.btn7 to 7, binding.btn8 to 8, binding.btn9 to 9
        )

        digitButtons.forEach { (btn, digit) ->
            btn.setOnClickListener { appendDigit(digit) }
        }

        binding.btn0.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            diceSegments[currentIndex] = DiceSegment()
            selectField(if (isHpOrHd && isPC) Field.Y else Field.X)
            true
        }

        binding.btnC.setOnClickListener { backspace() }

        binding.btnDel.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            deleteCurrentSegment()
            true
        }
    }

    /**
     * Configures the next/previous navigation buttons for cycling through segments.
     */
    private fun setupNavigation() {
        binding.btnPrev.setOnClickListener {
            if (currentIndex > 0) {
                saveChangesAndDismiss(false)
                currentIndex--
                selectField(if (isHpOrHd && isPC) Field.Y else Field.X)
            }
        }
        
        binding.btnPrev.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            saveChangesAndDismiss(false)
            diceSegments.add(currentIndex, DiceSegment())
            selectField(if (isHpOrHd && isPC) Field.Y else Field.X)
            true
        }

        binding.btnNext.setOnClickListener {
            saveChangesAndDismiss(false)
            if (currentIndex == diceSegments.size - 1) {
                diceSegments.add(DiceSegment())
            }
            currentIndex++
            selectField(if (isHpOrHd && isPC) Field.Y else Field.X)
        }
        
        binding.btnNext.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            saveChangesAndDismiss(false)
            diceSegments.add(currentIndex + 1, DiceSegment())
            currentIndex++
            selectField(if (isHpOrHd && isPC) Field.Y else Field.X)
            true
        }
    }

    /**
     * Appends a digit to the currently selected field.
     */
    private fun appendDigit(digit: Int) {
        val seg = diceSegments[currentIndex]
        
        val newVal = if (isFirstDigitAfterSelection) {
            digit
        } else {
            val currentStr = when (selectedField) {
                Field.N -> seg.n.toString()
                Field.X -> seg.x.toString()
                Field.Y -> seg.y.toString()
                Field.Z -> seg.z.toString()
            }
            val newStr = if (currentStr == "0") digit.toString() else currentStr + digit.toString()
            newStr.toIntOrNull() ?: 0
        }

        if (newVal <= 999) {
            when (selectedField) {
                Field.N -> seg.n = newVal
                Field.X -> seg.x = newVal
                Field.Y -> seg.y = newVal
                Field.Z -> seg.z = newVal
            }
            isFirstDigitAfterSelection = false
            updateUI()
        }
    }

    /**
     * Removes the last digit from the currently selected field.
     */
    private fun backspace() {
        val seg = diceSegments[currentIndex]
        val currentStr = when (selectedField) {
            Field.N -> seg.n.toString()
            Field.X -> seg.x.toString()
            Field.Y -> seg.y.toString()
            Field.Z -> seg.z.toString()
        }

        val newStr = if (currentStr.length > 1) currentStr.dropLast(1) else "0"
        val newVal = newStr.toIntOrNull() ?: 0

        when (selectedField) {
            Field.N -> seg.n = newVal
            Field.X -> seg.x = newVal
            Field.Y -> seg.y = newVal
            Field.Z -> seg.z = newVal
        }
        isFirstDigitAfterSelection = false
        updateUI()
    }

    /**
     * Deletes the currently displayed dice segment.
     */
    private fun deleteCurrentSegment() {
        if (diceSegments.size > 1) {
            diceSegments.removeAt(currentIndex)
            if (currentIndex >= diceSegments.size) {
                currentIndex = diceSegments.size - 1
            }
        } else {
            diceSegments[0] = DiceSegment()
        }
        selectField(if (isHpOrHd && isPC) Field.Y else Field.X)
    }

    /**
     * Updates all UI elements to reflect the current state of the active segment.
     */
    private fun updateUI() {
        if (diceSegments.isEmpty()) diceSegments.add(DiceSegment())
        val seg = diceSegments[currentIndex]
        binding.pickerLayout.apply {
            tvN.text = seg.n.toString()
            tvX.text = seg.x.toString()
            tvY.text = seg.y.toString()
            tvZ.text = seg.z.toString()
            btnSign.text = if (seg.isPositive) "+" else "-"

            val selectedColor = Color.parseColor("#F0F0F0")
            val normalColor = Color.TRANSPARENT
            
            tvN.setBackgroundColor(if (selectedField == Field.N) selectedColor else normalColor)
            tvX.setBackgroundColor(if (selectedField == Field.X) selectedColor else normalColor)
            tvY.setBackgroundColor(if (selectedField == Field.Y) selectedColor else normalColor)
            tvZ.setBackgroundColor(if (selectedField == Field.Z) selectedColor else normalColor)
        }

        binding.btnComma.setBackgroundColor(if (seg.isFollowedByComma) Color.parseColor("#E0E0E0") else Color.TRANSPARENT)

        binding.btnPrev.isEnabled = true
        binding.btnPrev.alpha = if (currentIndex > 0) 1.0f else 0.5f

        binding.tvPageIndicator.text = "${currentIndex + 1} / ${diceSegments.size}"
        
        updateFullStringPreview()
    }

    /**
     * Updates the preview text display of the entire multi-segment dice expression.
     * Highlights the segment currently being edited.
     */
    private fun updateFullStringPreview() {
        val builder = StringBuilder()
        var highlightStart = -1
        var highlightEnd = -1

        diceSegments.forEachIndexed { index, seg ->
            val start = builder.length
            val nPart = if (seg.n > 1) "${seg.n}x " else ""
            val xPart = if (seg.x > 1) "${seg.x}" else ""
            val zPart = if (seg.z != 0) (if (seg.isPositive) "+${seg.z}" else "-${seg.z}") else ""
            builder.append("${nPart}${xPart}d${seg.y}${zPart}")
            
            if (index == currentIndex) {
                highlightStart = start
                highlightEnd = builder.length
            }

            if (index < diceSegments.size - 1) {
                builder.append(if (seg.isFollowedByComma) ", " else " | ")
            }
        }

        val spannable = SpannableString(builder.toString())
        if (highlightStart != -1 && highlightEnd != -1) {
            spannable.setSpan(
                BackgroundColorSpan(Color.parseColor("#FFFF00")), // Yellow highlight
                highlightStart,
                highlightEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.tvFullString.text = spannable
    }

    /**
     * Parses the initial input string into a list of [DiceSegment] objects.
     */
    private fun parseInitialValue() {
        if (initialValue.isBlank()) return
        diceSegments.clear()
        
        val rawSegments = initialValue.split(Regex("(?<=[,|])|(?=[,|])"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            
        var currentSeg: DiceSegment? = null
        
        rawSegments.forEach { part ->
            if (part == "|" || part == ",") {
                currentSeg?.isFollowedByComma = (part == ",")
                currentSeg = null
            } else {
                val parsed = DiceRoller.parseCombo(part)
                if (parsed != null) {
                    val (n, expr) = parsed
                    val seg = DiceSegment(
                        n = n,
                        x = expr.diceCount,
                        y = expr.sides,
                        z = Math.abs(expr.modifier),
                        isPositive = expr.modifier >= 0
                    )
                    diceSegments.add(seg)
                    currentSeg = seg
                }
            }
        }
    }

    /**
     * Serializes the current state back into a dice string and invokes the callback.
     *
     * @param shouldDismiss If true, dismisses the dialog after saving.
     */
    private fun saveChangesAndDismiss(shouldDismiss: Boolean = false) {
        val result = StringBuilder()
        diceSegments.forEachIndexed { index, seg ->
            if (seg.y > 0) {
                val nPart = if (seg.n > 1) "${seg.n}x " else ""
                val xPart = if (seg.x > 1) "${seg.x}" else ""
                val zPart = if (seg.z != 0) (if (seg.isPositive) "+${seg.z}" else "-${seg.z}") else ""
                result.append("${nPart}${xPart}d${seg.y}${zPart}")
                
                if (index < diceSegments.size - 1) {
                    result.append(if (seg.isFollowedByComma) ", " else " | ")
                }
            }
        }
        
        onDiceEntered?.invoke(result.toString().trimEnd { it == '|' || it == ' ' || it == ',' })
        if (shouldDismiss) dismiss()
    }

    override fun onPause() {
        super.onPause()
        saveChangesAndDismiss(false)
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
