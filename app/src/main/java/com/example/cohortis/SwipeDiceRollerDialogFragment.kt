package com.example.cohortis

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.cohortis.databinding.DialogSwipeDiceRollerBinding

/**
 * A dialog fragment providing a specialized UI for entering and editing complex dice expressions.
 * Dice rolls follow the format NxXdY+Z.
 */
class SwipeDiceRollerDialogFragment : DialogFragment() {

    private var _binding: DialogSwipeDiceRollerBinding? = null
    private val binding get() = _binding!!

    private var onDiceEntered: ((String) -> Unit)? = null
    private var initialValue: String = ""
    private var title: String = "Dice Roller"
    private var isCommaAllowed: Boolean = true
    private var initialField: Field = Field.X

    private val diceRolls = mutableListOf<DiceRoll>()
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
     * Internal data structure representing a single dice roll (NxXdY+Z).
     */
    data class DiceRoll(
        var n: Int = 1,
        var x: Int = 1,
        var y: Int = 8,
        var z: Int = 0,
        var isPositive: Boolean = true,
        var separator: String = "" // "," or "|"
    )

    companion object {
        /**
         * Creates a new instance of the swipe dice roller.
         *
         * @param title The title displayed at the top of the dialog.
         * @param initialValue The starting dice string to edit.
         * @param isPC If true, defaults selection to sides (Y) instead of count (X).
         * @param isHpOrHd <tbd>
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
            fragment.onDiceEntered = onDiceEntered
            
            // Logic for field defaults
            fragment.initialField = when {
                title.contains("Damage", ignoreCase = true) -> Field.Y
                isHpOrHd && isPC -> Field.Y
                else -> Field.X
            }
            // Comma disallowed for Hit Dice based on previous logic
            fragment.isCommaAllowed = !title.contains("Hit Dice", ignoreCase = true)
            
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
        binding.tvFullString.movementMethod = LinkMovementMethod.getInstance()

        parseInitialValue()
        if (diceRolls.isEmpty()) {
            diceRolls.add(DiceRoll())
        }
        
        selectedField = initialField
        isFirstDigitAfterSelection = true

        setupSelectionListeners()
        setupKeypad()
        updateUI()

        binding.btnOk.setOnClickListener {
            saveChangesAndDismiss(true)
        }
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
                diceRolls[currentIndex].isPositive = !diceRolls[currentIndex].isPositive
                selectField(Field.Z)
            }
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

        binding.btnComma.visibility = if (isCommaAllowed) View.VISIBLE else View.INVISIBLE
        binding.btnComma.setOnClickListener { addSeparator(",") }

        binding.btnPipe.setOnClickListener { addSeparator("|") }

        binding.btnDel.setOnClickListener { onDelTapped() }
        binding.btnDel.setOnLongClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showDeleteConfirmDialog()
            true
        }
    }

    /**
     * Configures the next/previous navigation buttons for cycling through segments.
     */
    private fun addSeparator(sep: String) {
        diceRolls[currentIndex].separator = sep
        diceRolls.add(currentIndex + 1, DiceRoll())
        currentIndex++
        selectField(initialField)
    }

    private fun appendDigit(digit: Int) {
        val roll = diceRolls[currentIndex]
        val currentVal = when (selectedField) {
            Field.N -> roll.n
            Field.X -> roll.x
            Field.Y -> roll.y
            Field.Z -> roll.z
        }

        val newVal = if (isFirstDigitAfterSelection) {
            digit
        } else {
            val currentStr = currentVal.toString()
            val newStr = if (currentStr == "0") digit.toString() else currentStr + digit.toString()
            newStr.toIntOrNull() ?: 0
        }

        // Validate Ranges
        val isValid = when (selectedField) {
            Field.N -> newVal in 0..99
            Field.X -> newVal in 0..999
            Field.Y -> newVal in 0..1000
            Field.Z -> newVal in 0..999
        }

        if (isValid) {
            when (selectedField) {
                Field.N -> roll.n = newVal
                Field.X -> roll.x = newVal
                Field.Y -> roll.y = newVal
                Field.Z -> roll.z = newVal
            }
            isFirstDigitAfterSelection = false
            updateUI()
        }
    }

    /**
     * Removes the last digit from the currently selected field.
     */
     private fun onDelTapped() {
        val roll = diceRolls[currentIndex]
        val currentStr = when (selectedField) {
            Field.N -> roll.n.toString()
            Field.X -> roll.x.toString()
            Field.Y -> roll.y.toString()
            Field.Z -> roll.z.toString()
        }

        val newStr = if (currentStr.length > 1) currentStr.dropLast(1) else "0"
        val newVal = newStr.toIntOrNull() ?: 0

        when (selectedField) {
            Field.N -> roll.n = newVal
            Field.X -> roll.x = newVal
            Field.Y -> roll.y = newVal
            Field.Z -> roll.z = newVal
        }
        isFirstDigitAfterSelection = false
        updateUI()
    }

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Roll")
            .setMessage("Delete the current dice roll?")
            .setPositiveButton("Delete") { _, _ -> deleteCurrentRoll() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCurrentRoll() {
        if (diceRolls.size > 1) {
            diceRolls.removeAt(currentIndex)
            if (currentIndex >= diceRolls.size) {
                currentIndex = diceRolls.size - 1
            }
        } else {
            diceRolls[0] = DiceRoll()
        }
        selectField(initialField)
    }

    private fun updateUI() {
        if (diceRolls.isEmpty()) diceRolls.add(DiceRoll())
        val roll = diceRolls[currentIndex]
        
        binding.pickerLayout.apply {
            tvN.text = roll.n.toString()
            tvX.text = roll.x.toString()
            tvY.text = roll.y.toString()
            tvZ.text = roll.z.toString()
            btnSign.text = if (roll.isPositive) "+" else "-"

            val highlightColor = Color.parseColor("#ADD8E6") // Light Blue
            val normalColor = Color.TRANSPARENT
            
            tvN.setBackgroundColor(if (selectedField == Field.N) highlightColor else normalColor)
            tvX.setBackgroundColor(if (selectedField == Field.X) highlightColor else normalColor)
            tvY.setBackgroundColor(if (selectedField == Field.Y) highlightColor else normalColor)
            tvZ.setBackgroundColor(if (selectedField == Field.Z) highlightColor else normalColor)
        }

        updateFullStringPreview()
    }

    /**
     * Updates the preview text display of the entire multi-segment dice expression.
     * Highlights the segment currently being edited.
     */
    private fun updateFullStringPreview() {
        val builder = StringBuilder()
        val spans = mutableListOf<Triple<Int, Int, Int>>() // Start, End, Index

        diceRolls.forEachIndexed { index, roll ->
            val start = builder.length
            
            // Notation rules:
            // If N=1 don't include "Nx"
            val nPart = if (roll.n != 1) "${roll.n}x " else ""
            // If X=1 don't include "X"
            val xPart = if (roll.x != 1) "${roll.x}" else ""
            // If Z=0 don't include +/-Z
            val zPart = if (roll.z != 0) (if (roll.isPositive) "+${roll.z}" else "-${roll.z}") else ""
            
            builder.append("${nPart}${xPart}d${roll.y}${zPart}")
            val end = builder.length
            spans.add(Triple(start, end, index))

            if (index < diceRolls.size - 1) {
                val sep = roll.separator.ifEmpty { if (isCommaAllowed) "," else "|" }
                builder.append(if (sep == ",") ", " else " | ")
            }
        }

        val spannable = SpannableString(builder.toString())
        for ((start, end, index) in spans) {
            // Highlight current index
            if (index == currentIndex) {
                spannable.setSpan(BackgroundColorSpan(Color.YELLOW), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            
            // Tapping selection
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    currentIndex = index
                    updateUI()
                }
                override fun updateDrawState(ds: android.text.TextPaint) {
                    ds.isUnderlineText = false
                }
            }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        binding.tvFullString.text = spannable
    }

    /**
     * Parses the initial input string into a list of [DiceSegment] objects.
     */
    private fun parseInitialValue() {
        if (initialValue.isBlank()) return
        diceRolls.clear()
        
        val parts = initialValue.split(Regex("(?<=[,|])|(?=[,|])"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            
        var lastRoll: DiceRoll? = null
        
        parts.forEach { part ->
            if (part == "|" || part == ",") {
                lastRoll?.separator = part
            } else {
                val parsed = DiceRoller.parseCombo(part)
                if (parsed != null) {
                    val (n, expr) = parsed
                    val roll = DiceRoll(
                        n = n,
                        x = expr.diceCount,
                        y = expr.sides,
                        z = Math.abs(expr.modifier),
                        isPositive = expr.modifier >= 0
                    )
                    diceRolls.add(roll)
                    lastRoll = roll
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
        diceRolls.forEachIndexed { index, roll ->
            if (roll.y > 0) {
                val nPart = if (roll.n != 1) "${roll.n}x " else ""
                val xPart = if (roll.x != 1) "${roll.x}" else ""
                val zPart = if (roll.z != 0) (if (roll.isPositive) "+${roll.z}" else "-${roll.z}") else ""
                result.append("${nPart}${xPart}d${roll.y}${zPart}")
                
                if (index < diceRolls.size - 1) {
                    val sep = roll.separator.ifEmpty { if (isCommaAllowed) "," else "|" }
                    result.append(sep)
                }
            }
        }
        
        onDiceEntered?.invoke(result.toString())
        if (shouldDismiss) dismiss()
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
