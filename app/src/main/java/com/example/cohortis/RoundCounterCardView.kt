package com.example.cohortis

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.card.MaterialCardView

/**
 * A custom MaterialCardView that overrides performClick to satisfy accessibility requirements
 * when using custom touch listeners.
 */
class RoundCounterCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr) {

    override fun performClick(): Boolean {
        // This ensures that any OnClickListener or accessibility event is triggered.
        return if (super.performClick()) {
            true
        } else {
            // Even if no listener is set, we return true to indicate the click was handled
            // as part of our custom gesture logic.
            true
        }
    }
}
