package com.example.cohortis

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.cohortis.databinding.FragmentEventBinding

class EventFragment : Fragment() {
    private var _binding: FragmentEventBinding? = null
    private val binding get() = _binding!!
    
    private val eventHistory = mutableListOf<CharSequence>()
    private val MAX_HISTORY = 500

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.root.setOnLongClickListener {
            showHistoryDialog()
            true
        }
    }

    fun addLog(message: CharSequence) {
        eventHistory.add(message)
        if (eventHistory.size > MAX_HISTORY) {
            eventHistory.removeAt(0)
        }
        
        binding.tvLog1.text = binding.tvLog2.text
        binding.tvLog2.text = message
    }

    private fun showHistoryDialog() {
        // Use full screen theme
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#222222"))
            // Added top padding to avoid camera cutout and bottom for nav bar
            val topPadding = (16 * resources.displayMetrics.density).toInt()
            val bottomPadding = (32 * resources.displayMetrics.density).toInt()
            setPadding(0, topPadding, 0, bottomPadding)
        }

        val listView = ListView(requireContext()).apply {
            divider = null
            dividerHeight = 0
            setBackgroundColor(Color.parseColor("#222222"))
        }
        
        val displayList = eventHistory.toList()
        
        val adapter = object : ArrayAdapter<CharSequence>(requireContext(), 0, displayList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val textView = (convertView as? TextView) ?: TextView(context).apply {
                    setTextColor(Color.parseColor("#00FF00"))
                    textSize = 13f 
                    val horizontalPadding = (24 * resources.displayMetrics.density).toInt()
                    val verticalPadding = (4 * resources.displayMetrics.density).toInt()
                    setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                    includeFontPadding = false
                }
                textView.text = getItem(position)
                return textView
            }
        }
        
        listView.adapter = adapter
        
        // Remove close button and make list take full space
        container.addView(listView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
        
        dialog.setContentView(container)
        
        listView.post {
            listView.setSelection(adapter.count - 1)
        }
        
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
