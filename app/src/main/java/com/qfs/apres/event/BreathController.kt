package com.qfs.apres.event

import com.qfs.apres.event.CompoundEvent

class BreathController(channel: Int, value: Int): CompoundEvent(channel, value) {
    override val controller = 0x02
}
class BreathControllerMSB(channel: Int, value: Int): VariableControlChange(channel, value) {
    override val controller = 0x02
}
class BreathControllerLSB(channel: Int, value: Int): VariableControlChange(channel, value) {
    override val controller = 0x22
}
