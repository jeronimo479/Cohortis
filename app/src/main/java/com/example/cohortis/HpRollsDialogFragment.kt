package com.example.cohortis

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.fragment.app.DialogFragment
import com.example.cohortis.databinding.DialogHpRollsBinding

class HpRollsDialogFragment : DialogFragment() {

    private var _binding: DialogHpRollsBinding? = null
    private val binding get() = _binding!!

    private var onHpRollsEntered: ((String) -> Unit)? = null
    private var initialValue: String = ""

    companion object {
        fun newInstance(initialValue: String, onHpRollsEntered: (String) -> Unit): HpRollsDialogFragment {
            val fragment = HpRollsDialogFragment()
            fragment.initialValue = initialValue
            fragment.onHpRollsEntered = onHpRollsEntered
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogHpRollsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRow(binding.npN1, binding.npX1, binding.npY1, binding.btnSign1, binding.npZ1, binding.row1, true)
        setupRow(binding.npN2, binding.npX2, binding.npY2, binding.btnSign2, binding.npZ2, binding.row2, false)
        setupRow(binding.npN3, binding.npX3, binding.npY3, binding.btnSign3, binding.npZ3, binding.row3, false)

        parseInitialValue()

        binding.btnSave.setOnClickListener {
            val result = mutableListOf<String>()
            
            fun getRowString(n: NumberPicker, x: NumberPicker, y: NumberPicker, btn: Button, z: NumberPicker): String? {
                if (n.value == 0 || y.value == 0) return null
                val sign = if (btn.text == "+") 1 else -1
                val zVal = z.value * sign
                
                // Using the concise format from previous steps
                val nPart = if (n.value > 1) "${n.value}x " else ""
                val xPart = if (x.value > 1) "${x.value}" else ""
                val zPart = if (zVal != 0) (if (zVal > 0) "+$zVal" else "$zVal") else ""
                
                return "${nPart}${xPart}d${y.value}$zPart"
            }

            getRowString(binding.npN1, binding.npX1, binding.npY1, binding.btnSign1, binding.npZ1)?.let { result.add(it) }
            getRowString(binding.npN2, binding.npX2, binding.npY2, binding.btnSign2, binding.npZ2)?.let { result.add(it) }
            getRowString(binding.npN3, binding.npX3, binding.npY3, binding.btnSign3, binding.npZ3)?.let { result.add(it) }

            onHpRollsEntered?.invoke(result.joinToString(" | "))
            dismiss()
        }
    }

    private fun setupRow(n: NumberPicker, x: NumberPicker, y: NumberPicker, btn: Button, z: NumberPicker, row: LinearLayout, isFirst: Boolean) {
        n.minValue = 0
        n.maxValue = 20
        n.value = if (isFirst) 1 else 0

        x.minValue = 1
        x.maxValue = 20
        x.value = 1

        y.minValue = 0
        y.maxValue = 100
        y.value = 0

        z.minValue = 0
        z.maxValue = 100
        z.value = 0

        btn.setOnClickListener {
            btn.text = if (btn.text == "+") "-" else "+"
        }

        val updateEnable = {
            val enabled = n.value > 0
            row.alpha = if (enabled) 1.0f else 0.5f
            x.isEnabled = enabled
            y.isEnabled = enabled
            btn.isEnabled = enabled
            z.isEnabled = enabled
        }

        n.setOnValueChangedListener { _, _, _ -> updateEnable() }
        updateEnable()
    }

    private fun parseInitialValue() {
        val segments = initialValue.split("|")
        val pickers = listOf(
            Triple(binding.npN1, binding.npX1, Triple(binding.npY1, binding.btnSign1, binding.npZ1)),
            Triple(binding.npN2, binding.npX2, Triple(binding.npY2, binding.btnSign2, binding.npZ2)),
            Triple(binding.npN3, binding.npX3, Triple(binding.npY3, binding.btnSign3, binding.npZ3))
        )

        segments.forEachIndexed { index, segment ->
            if (index < pickers.size) {
                val parsed = DiceRoller.parseCombo(segment.trim())
                if (parsed != null) {
                    val (nVal, expr) = parsed
                    val (npN, npX, rest) = pickers[index]
                    val (npY, btn, npZ) = rest
                    
                    npN.value = nVal
                    npX.value = expr.diceCount
                    npY.value = expr.sides
                    if (expr.modifier < 0) {
                        btn.text = "-"
                        npZ.value = -expr.modifier
                    } else {
                        btn.text = "+"
                        npZ.value = expr.modifier
                    }
                    
                    // Manually trigger alpha/enable update
                    val row = when(index) {
                        0 -> binding.row1
                        1 -> binding.row2
                        else -> binding.row3
                    }
                    row.alpha = 1.0f
                    npX.isEnabled = true
                    npY.isEnabled = true
                    btn.isEnabled = true
                    npZ.isEnabled = true
                }
            }
        }
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
