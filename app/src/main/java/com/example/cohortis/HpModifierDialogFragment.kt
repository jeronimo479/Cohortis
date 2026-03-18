package com.example.cohortis

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.cohortis.databinding.FragmentHpModifierBinding

class HpModifierDialogFragment : DialogFragment() {

    private var _binding: FragmentHpModifierBinding? = null
    private val binding get() = _binding!!

    private lateinit var member: Member
    private var accumulator: Int = 0
    private var onApplied: ((Member) -> Unit)? = null

    companion object {
        fun newInstance(member: Member, onApplied: (Member) -> Unit): HpModifierDialogFragment {
            val fragment = HpModifierDialogFragment()
            fragment.member = member
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
        binding.tvCharName.text = displayName
        
        updateHpDisplays()
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

        // Tapping the Accumulator pastes the exact value into current HP and dismisses
        binding.tvAccumulator.setOnClickListener {
            member.hpCurrent = accumulator
            onApplied?.invoke(member)
            dismiss()
        }

        // Tapping HP Full copies HP Full value into HP Current and dismisses
        binding.tvHpFullValue.setOnClickListener {
            member.hpCurrent = member.hpFull
            onApplied?.invoke(member)
            dismiss()
        }
    }

    private fun updateHpDisplays() {
        binding.tvHpFullValue.text = member.hpFull.toString()
        binding.tvHpCurrentValue.text = member.hpCurrent.toString()
    }

    private fun updateAccumulatorDisplay() {
        binding.tvAccumulator.text = accumulator.toString()
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
