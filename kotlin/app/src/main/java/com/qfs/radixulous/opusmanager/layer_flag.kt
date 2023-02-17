package com.qfs.radixulous.opusmanager
import android.util.Log
import com.qfs.radixulous.structure.OpusTree
import java.lang.Integer.max
enum class UpdateFlag {
    Beat,
    BeatMod,
    Line,
    AbsVal
}
data class LineFlag(var channel: Int, var line: Int, var beat_count: Int, var operation: Int) {}
class UpdatesCache {
    var order_queue: MutableList<UpdateFlag> = mutableListOf()
    private var beat_flag: MutableList<Pair<Int, Int>> = mutableListOf()
    private var beat_change: MutableList<BeatKey> = mutableListOf()
    private var line_flag: MutableList<LineFlag> = mutableListOf()
    private var absolute_value_flag: MutableList<Pair<BeatKey, List<Int>>> = mutableListOf()

    fun dequeue_order_flag(): UpdateFlag? {
        return if (this.order_queue.isEmpty()) {
            null
        } else {
            this.order_queue.removeFirst()
        }
    }

    fun dequeue_line(): LineFlag? {
        return if (this.line_flag.isEmpty()) {
            null
        } else {
            this.line_flag.removeFirst()
        }
    }
    fun dequeue_beat(): Pair<Int, Int>? {
        return if (this.beat_flag.isEmpty()) {
            null
        } else {
            this.beat_flag.removeFirst()
        }
    }
    fun dequeue_change(): BeatKey? {
        return if (this.beat_change.isEmpty()) {
            null
        } else {
            this.beat_change.removeFirst()
        }
    }

    fun dequeue_absolute_value(): Pair<BeatKey, List<Int>>? {
        return if (this.absolute_value_flag.isEmpty()) {
            null
        } else {
            this.absolute_value_flag.removeFirst()
        }
    }

    fun flag_beat_pop(index: Int) {
        this.order_queue.add(UpdateFlag.Beat)
        this.beat_flag.add(Pair(index, 0))
    }
    fun flag_beat_new(index: Int) {
        this.order_queue.add(UpdateFlag.Beat)
        this.beat_flag.add(Pair(index, 1))
    }
    fun flag_beat_change(beat_key: BeatKey) {
        this.order_queue.add(UpdateFlag.BeatMod)
        this.beat_change.add(beat_key)
    }
    fun flag_line_pop(channel: Int, line_offset: Int) {
        this.order_queue.add(UpdateFlag.Line)
        this.line_flag.add(LineFlag(channel, line_offset, 0, 0))
    }
    fun flag_line_new(channel: Int, line_offset: Int, beat_count: Int) {
        this.order_queue.add(UpdateFlag.Line)
        this.line_flag.add(LineFlag(channel, line_offset, beat_count, 1))
    }
    fun flag_absolute_value(beatkey: BeatKey, position: List<Int>) {
        this.order_queue.add(UpdateFlag.AbsVal)
        this.absolute_value_flag.add(Pair(beatkey, position))
    }
    fun purge() {
        this.order_queue.clear()
        this.beat_flag.clear()
        this.beat_change.clear()
        this.line_flag.clear()
        this.absolute_value_flag.clear()
    }
}

open class FlagLayer : LinksLayer() {
    private var cache = UpdatesCache()

    fun fetch_next_flag(): UpdateFlag? {
        return this.cache.dequeue_order_flag()
    }
    fun fetch_flag_line(): LineFlag? {
        return this.cache.dequeue_line()
    }

    fun fetch_flag_beat(): Pair<Int, Int>? {
        return this.cache.dequeue_beat()
    }

    fun fetch_flag_change(): BeatKey? {
        return this.cache.dequeue_change()
    }

    fun fetch_flag_absolute_value(): Pair<BeatKey, List<Int>>? {
        return this.cache.dequeue_absolute_value()
    }

    fun flag_beat_change(beat_key: BeatKey) {
        this.cache.flag_beat_change(beat_key)
    }


    override fun remove_channel(channel: Int) {
        for (i in 0 until this.channels[channel].size) {
            this.cache.flag_line_pop(channel, 0)
        }
        super.remove_channel(channel)
    }

    override fun replace_tree(beat_key: BeatKey, position: List<Int>, tree: OpusTree<OpusEvent>) {
        super.replace_tree(beat_key, position, tree)
        this.cache.flag_beat_change(beat_key)
    }

    override fun overwrite_beat(old_beat: BeatKey, new_beat: BeatKey) {
        super.overwrite_beat(old_beat, new_beat)
        this.cache.flag_beat_change(old_beat)
    }

    override fun insert_after(beat_key: BeatKey, position: List<Int>) {
        super.insert_after(beat_key, position)
        this.cache.flag_beat_change(beat_key)
    }

    override fun split_tree(beat_key: BeatKey, position: List<Int>, splits: Int) {
        super.split_tree(beat_key, position, splits)
        this.cache.flag_beat_change(beat_key)
    }

    override fun purge_cache() {
        this.cache.purge()
        super.purge_cache()
    }

    override fun reset_cache() {
        super.reset_cache()
        for (i in 0 until this.channels.size) {
            for (j in 0 until this.channels[i].size) {
                this.cache.flag_line_new(i, j, 0)
            }
        }
        for (x in 0 until this.opus_beat_count) {
            this.cache.flag_beat_new(x)
        }
    }

    override fun set_event(beat_key: BeatKey, position: List<Int>, event: OpusEvent) {

        super.set_event(beat_key, position, event)
        this.cache.flag_beat_change(beat_key)
    }

    override fun set_percussion_event(beat_key: BeatKey, position: List<Int>) {
        super.set_percussion_event(beat_key, position)
        this.cache.flag_beat_change(beat_key)
    }

    override fun unset(beat_key: BeatKey, position: List<Int>) {
        super.unset(beat_key, position)
        this.cache.flag_beat_change(beat_key)
    }

    override fun insert_beat(index: Int?) {
        super.insert_beat(index)
        this.cache.flag_beat_new(index ?: this.opus_beat_count - 1)
    }

    override fun new_line(channel: Int, index: Int?): List<OpusTree<OpusEvent>> {
        var output = super.new_line(channel, index)

        val line_index = index ?: (this.channels[channel].size - 1)
        this.cache.flag_line_new(channel, line_index, this.opus_beat_count)

        for (i in 0 until this.opus_beat_count)     {
            this.cache.flag_beat_change(BeatKey(channel, line_index, i))
        }

        return output
    }

    override fun remove(beat_key: BeatKey, position: List<Int>) {
        super.remove(beat_key, position)
        this.cache.flag_beat_change(beat_key)
    }

    override fun remove_beat(beat_index: Int) {
        super.remove_beat(beat_index)
        this.cache.flag_beat_pop(beat_index)
    }

    override fun remove_line(channel: Int, index: Int) {
        super.remove_line(channel, index)
        this.cache.flag_line_pop(channel, index)
    }

    //override fun link_beats(beat_key: BeatKey, target: BeatKey) {
    //    super.link_beats(beat_key, target)
    //    this.cache.flag_beat_change(beat_key)
    //    this.cache.flag_beat_change(target)
    //}

    override fun unlink_beat(beat_key: BeatKey) {
        this.cache.flag_beat_change(beat_key)
        var target_key = this.linked_beat_map.get(beat_key)
        if (target_key != null) {
            this.cache.flag_beat_change(target_key!!)
        }
        super.unlink_beat(beat_key)
    }

    override fun cache_absolute_value(beat_key: BeatKey, position: List<Int>, event_value: Int) {
        super.cache_absolute_value(beat_key, position, event_value)
        this.cache.flag_absolute_value(beat_key, position)
    }
}
