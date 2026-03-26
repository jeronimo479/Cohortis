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

/**
 * A fragment that displays a rolling log of events (e.g., dice rolls, HP changes).
 * Maintains a history and allows viewing it in a full-screen dialog.
 */
class EventFragment : Fragment() {
    private var _binding: FragmentEventBinding? = null
    private val binding get() = _binding!!
    
    /** Internal storage for event history. */
    private val eventHistory = mutableListOf<CharSequence>()
    /** Maximum number of entries to keep in history. */
    private val MAX_HISTORY = 500

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Long click on the event bar opens the history viewer
        binding.root.setOnLongClickListener {
            showHistoryDialog()
            true
        }
    }

    /**
     * Adds a new message to the event log and updates the UI preview.
     * Automatically prunes history if [MAX_HISTORY] is exceeded.
     *
     * @param message The text or spannable string to add.
     */
    fun addLog(message: CharSequence) {
        eventHistory.add(message)
        if (eventHistory.size > MAX_HISTORY) {
            eventHistory.removeAt(0)
        }
        
        // Simple two-line preview display
        binding.tvLog1.text = binding.tvLog2.text
        binding.tvLog2.text = message
    }

    /**
     * Displays a full-screen dialog containing the complete event history in a [ListView].
     */
    private fun showHistoryDialog() {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#222222"))
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
                    setTextColor(Color.parseColor("#00FF00")) // Classic green terminal look
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
        container.addView(listView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
        dialog.setContentView(container)
        
        // Scroll to the latest entry
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
