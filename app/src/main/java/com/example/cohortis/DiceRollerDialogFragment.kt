package com.example.cohortis

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.cohortis.databinding.DialogDiceRollerBinding

class DiceRollerDialogFragment : DialogFragment() {

    private var _binding: DialogDiceRollerBinding? = null
    private val binding get() = _binding!!

    private var onDiceEntered: ((String) -> Unit)? = null
    private var initialValue: String = ""

    // State for the current segment
    private var currentN: Int? = null
    private var currentX: Int? = null
    private var currentY: Int? = null
    private var currentModifierSign: Int = 1
    private var currentInput: String = ""

    private val segments = mutableListOf<String>()

    companion object {
        fun newInstance(initialValue: String, onDiceEntered: (String) -> Unit): DiceRollerDialogFragment {
            val fragment = DiceRollerDialogFragment()
            fragment.initialValue = initialValue
            fragment.onDiceEntered = onDiceEntered
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogDiceRollerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Show initial value if present, but we start fresh for new input
        if (initialValue.isNotBlank()) {
            binding.tvDisplay.text = initialValue
        } else {
            updateDisplay()
        }

        val digits = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9
        )

        digits.forEach { btn ->
            btn.setOnClickListener {
                currentInput += btn.text
                updateDisplay()
            }
        }

        binding.btnMultiply.setOnClickListener {
            currentN = currentInput.toIntOrNull() ?: 1
            currentInput = ""
            updateDisplay()
        }

        binding.btnD.setOnClickListener {
            if (currentX == null) {
                currentX = currentInput.toIntOrNull() ?: 1
                currentInput = ""
            }
            updateDisplay()
        }

        binding.btnPlus.setOnClickListener {
            handleOperator(1)
        }

        binding.btnMinus.setOnClickListener {
            handleOperator(-1)
        }

        binding.btnDivide.setOnClickListener {
            finalizeSegment()
            updateDisplay()
        }

        binding.btnClear.setOnClickListener {
            undoLastAction()
        }

        binding.btnClear.setOnLongClickListener {
            clearAll()
            true
        }

        binding.btnApply.setOnClickListener {
            finishAndApply()
        }
    }

    private fun handleOperator(sign: Int) {
        if (currentY == null) {
            currentY = currentInput.toIntOrNull()
            currentInput = ""
            currentModifierSign = sign
        }
        updateDisplay()
    }

    private fun undoLastAction() {
        if (currentInput.isNotEmpty()) {
            currentInput = currentInput.substring(0, currentInput.length - 1)
        } else if (currentY != null) {
            currentInput = currentY.toString()
            currentY = null
        } else if (currentX != null) {
            currentInput = currentX.toString()
            currentX = null
        } else if (currentN != null) {
            currentInput = currentN.toString()
            currentN = null
        } else if (segments.isNotEmpty()) {
            segments.removeAt(segments.size - 1)
        }
        updateDisplay()
    }

    private fun clearAll() {
        segments.clear()
        currentN = null
        currentX = null
        currentY = null
        currentInput = ""
        currentModifierSign = 1
        updateDisplay()
    }

    private fun finalizeSegment() {
        val n = currentN ?: 1
        val x = currentX ?: 1
        var y = currentY
        var z = 0

        if (y == null) {
            y = currentInput.toIntOrNull() ?: 0
            currentInput = ""
        } else {
            z = currentInput.toIntOrNull() ?: 0
            currentInput = ""
        }

        if (y!! > 0) {
            val mod = z * currentModifierSign
            // Store as a string compatible with parseCombo
            val segment = "${n}x ${x}d$y${if (mod >= 0) "+" else ""}$mod"
            segments.add(segment)
        }

        // Reset for next segment
        currentN = null
        currentX = null
        currentY = null
        currentModifierSign = 1
        currentInput = ""
    }

    private fun finishAndApply() {
        finalizeSegment()
        if (segments.isNotEmpty()) {
            val result = segments.mapNotNull { seg ->
                DiceRoller.parseCombo(seg)?.let { (n, expr) ->
                    val nPart = if (n > 1) "${n}x " else ""
                    nPart + DiceRoller.formatExpr(expr)
                }
            }.joinToString(" | ")
            onDiceEntered?.invoke(result)
        }
        dismiss()
    }

    private fun updateDisplay() {
        val sb = StringBuilder()
        
        // Formatted display (concise)
        segments.forEachIndexed { index, seg ->
            val parsed = DiceRoller.parseCombo(seg)
            if (parsed != null) {
                if (index > 0) sb.append(" | ")
                val n = parsed.first
                val expr = parsed.second
                if (n > 1) sb.append("${n}x ")
                sb.append(DiceRoller.formatExpr(expr))
            }
        }

        if (segments.isNotEmpty() && (currentInput.isNotEmpty() || currentN != null || currentX != null || currentY != null)) {
            sb.append(" | ")
        }

        // Current Segment Preview (concise)
        val preview = StringBuilder()
        if (currentN != null && currentN!! > 1) preview.append("${currentN}x ")
        
        if (currentX != null) {
            if (currentX!! > 1) preview.append(currentX)
            preview.append("d")
        }

        if (currentY != null) {
            preview.append(currentY)
            val z = currentInput.toIntOrNull()
            if (z != null && z != 0) {
                preview.append(if (currentModifierSign > 0) "+" else "-")
                preview.append(z)
            }
        } else {
            if (currentInput.isNotEmpty()) {
                if (currentX != null) {
                    preview.append(currentInput) // Typing Y
                } else if (currentN != null) {
                    preview.append(currentInput)
                    preview.append("d") // Assuming typing X
                } else {
                    // Just typing a number. Assume dY for preview if no X or N.
                    preview.append("d").append(currentInput)
                }
            }
        }

        binding.tvDisplay.text = if (sb.isEmpty() && preview.isEmpty()) "" else sb.toString() + preview.toString()
        
        // Formula display N x X d Y +- Z
        val fN = currentN ?: 1
        val fX = currentX ?: 1
        val fY = currentY ?: if (currentX != null || currentN != null) currentInput.toIntOrNull() ?: 0 else currentInput.toIntOrNull() ?: 0
        val fZ = if (currentY != null) currentInput.toIntOrNull() ?: 0 else 0
        binding.tvFormula.text = "$fN x $fX d $fY ${if (currentModifierSign > 0) "+" else "-"} $fZ"
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
