package com.qfs.apres.soundfont

import kotlin.math.pow

class Generator(
    var sfGenOper: Int,
    var shAmount: Int,
    var wAmount: Int
) {
    fun asInt(): Int {
        return shAmount + (wAmount * 256)
    }

    fun asIntSigned(): Int {
        val unsigned = shAmount + (wAmount * 256)
        // Get 2's compliment
        return if (unsigned shr 15 == 1) {
            0 - (((unsigned xor 0xFFFF) + 1) and 0x7FFF)
        } else {
            unsigned
        }
    }
    fun asTimecent(): Double {
        val p = this.asIntSigned() / 1200
        return (2.0).pow(p.toDouble())
    }
    fun asPair(): Pair<Int, Int> {
        return Pair(this.shAmount, this.wAmount)
    }
}