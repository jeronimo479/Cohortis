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

class SwipeDiceRollerDialogFragment : DialogFragment() {

    private var _binding: DialogSwipeDiceRollerBinding? = null
    private val binding get() = _binding!!

    private var onDiceEntered: ((String) -> Unit)? = null
    private var initialValue: String = ""
    private var title: String = "Dice Roller"

    private val diceSegments = mutableListOf<DiceSegment>()
    private var currentIndex = 0
    private var selectedField = Field.Y
    private var isFirstDigitAfterSelection = true

    enum class Field { N, X, Y, Z }

    data class DiceSegment(
        var n: Int = 1,
        var x: Int = 1,
        var y: Int = 6,
        var z: Int = 0,
        var isPositive: Boolean = true,
        var isFollowedByComma: Boolean = false
    )

    companion object {
        fun newInstance(title: String, initialValue: String, onDiceEntered: (String) -> Unit): SwipeDiceRollerDialogFragment {
            val fragment = SwipeDiceRollerDialogFragment()
            fragment.title = title
            fragment.initialValue = initialValue
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
        
        selectedField = Field.Y
        isFirstDigitAfterSelection = true

        setupSelectionListeners()
        setupKeypad()
        setupNavigation()
        updateUI()
    }

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
        
        // hpDice rolls can't have a comma.
        if (title.contains("Hit Dice", ignoreCase = true)) {
            binding.btnComma.visibility = View.GONE
        }

        binding.btnComma.setOnClickListener {
            // The comma button should add a new field to the series, just after the current one.
            diceSegments[currentIndex].isFollowedByComma = true
            diceSegments.add(currentIndex + 1, DiceSegment())
            currentIndex++
            selectField(Field.Y)
        }

        binding.tvPageIndicator.setOnLongClickListener {
            // A long tap on page 1/n should insert a new diceRoll segment in front of the previous first.
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            diceSegments.add(0, DiceSegment())
            currentIndex = 0
            selectField(Field.Y)
            true
        }
    }

    private fun selectField(field: Field) {
        selectedField = field
        isFirstDigitAfterSelection = true
        updateUI()
    }

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
            selectField(Field.Y)
            true
        }

        binding.btnC.setOnClickListener { backspace() }

        binding.btnDel.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            deleteCurrentSegment()
            true
        }
    }

    private fun setupNavigation() {
        binding.btnPrev.setOnClickListener {
            if (currentIndex > 0) {
                saveChangesAndDismiss(false)
                currentIndex--
                selectField(Field.Y)
            }
        }
        
        binding.btnPrev.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            saveChangesAndDismiss(false)
            diceSegments.add(currentIndex, DiceSegment())
            selectField(Field.Y)
            true
        }

        binding.btnNext.setOnClickListener {
            saveChangesAndDismiss(false)
            if (currentIndex == diceSegments.size - 1) {
                diceSegments.add(DiceSegment())
            }
            currentIndex++
            selectField(Field.Y)
        }
        
        binding.btnNext.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            saveChangesAndDismiss(false)
            diceSegments.add(currentIndex + 1, DiceSegment())
            currentIndex++
            selectField(Field.Y)
            true
        }
    }

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

    private fun deleteCurrentSegment() {
        if (diceSegments.size > 1) {
            diceSegments.removeAt(currentIndex)
            if (currentIndex >= diceSegments.size) {
                currentIndex = diceSegments.size - 1
            }
        } else {
            diceSegments[0] = DiceSegment()
        }
        selectField(Field.Y)
    }

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
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        binding.tvFullString.text = spannable
    }

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
