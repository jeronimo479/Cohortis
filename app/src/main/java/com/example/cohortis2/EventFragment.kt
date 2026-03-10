package com.example.cohortis2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.cohortis2.databinding.FragmentEventBinding

class EventFragment : Fragment() {
    private var _binding: FragmentEventBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    fun addLog(message: CharSequence) {
        binding.tvLog2.text = binding.tvLog1.text
        binding.tvLog1.text = message
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
