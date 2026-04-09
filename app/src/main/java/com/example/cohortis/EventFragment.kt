package com.example.cohortis

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.cohortis.databinding.FragmentEventBinding
import java.text.SimpleDateFormat
import java.util.*

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

        if (eventHistory.isEmpty()) {
            val startTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            addLog("Event Log started at $startTime")
        }

        // Restore the preview from stored history
        updatePreviewFromHistory()
    }

    /**
     * Adds a new message to the event log.
     * Safe to call even when the view is not created; it will store history only.
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
     * Clears the entire log history and adds a reset message.
     */
    fun clearLog() {
        eventHistory.clear()
        val startTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        addLog("Event Log reset at $startTime")
        updatePreviewFromHistory()
    }

    /**
     * Updates the four-line preview.    
     */
    private fun updatePreviewFromHistory() {
        val size = eventHistory.size
        
        binding.tvLog4.text = if (size >= 1) eventHistory.elementAt(size - 1) else ""
        binding.tvLog3.text = if (size >= 2) eventHistory.elementAt(size - 2) else ""
        binding.tvLog2.text = if (size >= 3) eventHistory.elementAt(size - 3) else ""
        binding.tvLog1.text = if (size >= 4) eventHistory.elementAt(size - 4) else ""
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

        val rootContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }

        // Title Bar
        val titleBar = RelativeLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            )
            setPadding(dp(16), 0, dp(16), 0)
        }

        val tvTitle = TextView(ctx).apply {
            text = "Full Event Log"
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER_VERTICAL
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
            params.addRule(RelativeLayout.ALIGN_PARENT_START)
            layoutParams = params
        }

        val btnDel = Button(ctx).apply {
            text = getString(R.string.delete_short)
            setTextColor(Color.RED)
            setBackgroundColor(Color.TRANSPARENT)
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
            params.addRule(RelativeLayout.ALIGN_PARENT_END)
            layoutParams = params
            
            setOnClickListener {
                AlertDialog.Builder(ctx)
                    .setTitle("Clear Log")
                    .setMessage("Clear the current event history?")
                    .setPositiveButton("Clear") { _, _ ->
                        clearLog()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            setOnLongClickListener {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                clearLog()
                dialog.dismiss()
                true
            }
        }

        titleBar.addView(tvTitle)
        titleBar.addView(btnDel)
        rootContainer.addView(titleBar)

        val listView = ListView(ctx).apply {
            divider = null
            dividerHeight = 0
            setBackgroundColor(bgColor)
            setPadding(0, 0, 0, dp(32))
            clipToPadding = false
        }

        val adapter = object : ArrayAdapter<CharSequence>(ctx, 0, eventHistory.toList()) {
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
        rootContainer.addView(
            listView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        dialog.setContentView(rootContainer)

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
