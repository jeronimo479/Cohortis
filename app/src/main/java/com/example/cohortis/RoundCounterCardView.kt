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
        // Call super so accessibility services and any OnClickListener are notified.
        super.performClick()
        // We handle clicks via gestures/touch, so report the click as handled.
        return true
    }

}
