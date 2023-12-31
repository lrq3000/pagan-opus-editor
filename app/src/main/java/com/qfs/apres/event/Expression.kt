package com.qfs.apres.event

import com.qfs.apres.event.CompoundEvent

class Expression(channel: Int, value: Int): CompoundEvent(channel, value) {
    override val controller = 0x0B
}
class ExpressionMSB(channel: Int, value: Int): VariableControlChange(channel, value) {
    override val controller = 0x0B
}
class ExpressionLSB(channel: Int, value: Int): VariableControlChange(channel, value) {
    override val controller = 0x2B
}
