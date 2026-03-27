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
 * Displays a rolling log of events (dice rolls, HP changes, etc.).
 * Long-press opens a fullscreen history viewer.
 */
class EventFragment : Fragment() {

    private var _binding: FragmentEventBinding? = null
    private val binding get() = _binding!!

    // Ring-buffer-ish storage: removeFirst() is O(1)
    private val eventHistory: ArrayDeque<CharSequence> = ArrayDeque(MAX_HISTORY)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Long-press opens history viewer
        binding.root.setOnLongClickListener {
            if (isAdded) showHistoryDialog()
            true
        }

        // Restore the two-line preview from stored history
        updatePreviewFromHistory()
    }

    /**
     * Adds a new message to the event log.
     * Safe to call even when the view is not created; it will store history only.
     *
     * NOTE: If addLog might be called from a background thread, keep the post{} below.
     */
    fun addLog(message: CharSequence) {
        if (eventHistory.size == MAX_HISTORY) {
            eventHistory.removeFirst()
        }
        eventHistory.addLast(message)

        // Update UI only if view exists; schedule on UI thread safely.
        _binding?.root?.post { updatePreviewFromHistory() }
    }

    /**
     * Updates the two-line preview.    
     */
    private fun updatePreviewFromHistory() {
        val size = eventHistory.size
        val last = if (size >= 1) eventHistory.last() else null
        val secondLast = if (size >= 2) eventHistory.elementAt(size - 2) else null

        binding.tvLog2.text = last ?: ""
        binding.tvLog1.text = secondLast ?: ""
    }

    /**
     * Fullscreen dialog showing the entire history.
     */
    private fun showHistoryDialog() {
        val ctx = context ?: return

        val dialog = Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val bgColor = Color.parseColor("#222222") // Very Dark Gray
        val textColor = Color.parseColor("#00FF00") // Green

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            setPadding(0, dp(16), 0, dp(32))
        }

        val listView = ListView(ctx).apply {
            divider = null
            dividerHeight = 0
            setBackgroundColor(bgColor)
        }

        // Snapshot so the dialog content is stable while open
        val displayList: List<CharSequence> = eventHistory.toList()

        val adapter = object : ArrayAdapter<CharSequence>(ctx, 0, displayList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = (convertView as? TextView) ?: TextView(context).apply {
                    setTextColor(textColor)
                    textSize = 13f
                    includeFontPadding = false
                    setPadding(dp(24), dp(4), dp(24), dp(4))
                }
                tv.text = getItem(position)
                return tv
            }
        }

        listView.adapter = adapter
        container.addView(
            listView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )

        dialog.setContentView(container)

        // Scroll to the newest entry
        listView.post {
            val count = adapter.count
            if (count > 0) listView.setSelection(count - 1)
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        private const val MAX_HISTORY = 500
    }
}
