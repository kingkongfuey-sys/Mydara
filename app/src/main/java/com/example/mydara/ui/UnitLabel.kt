package com.example.mydara.ui

/** Which travelling unit batches get a value label (all map sizes). */
object UnitLabel {
    /** Batches carrying MORE than this many units show their number. */
    const val MIN_VALUE_EXCLUSIVE = 10

    fun visible(value: Int): Boolean = value > MIN_VALUE_EXCLUSIVE
}
