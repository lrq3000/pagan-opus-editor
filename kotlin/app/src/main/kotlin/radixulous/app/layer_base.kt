package radixulous.app.opusmanager
import radixulous.app.structure.OpusTree

data class OpusEvent(var note: Int, var radix: Int, var channel: Int, var relative: Boolean)
data class BeatKey(var channel: Int, var line_offset: Int, var beat: Int)

open class OpusManagerBase {
    var RADIX: Int = 12
    var DEFAULT_PERCUSSION: Int = 0x35
    var channel_trees: Array<MutableList<OpusTree<OpusEvent>>> = Array(16, { _ -> mutableListOf() })
    var opus_beat_count: Int = 1
    var path: String? = null
    var percussion_map: HashMap<Int, Int> = HashMap<Int, Int>()


    open fun insert_after(beat_key: BeatKey, position: List<Int>) {
        if (position.isEmpty()) {
            throw Exception("Invalid Position {position}")
        }
        val tree = this.get_tree(beat_key, position)
        val parent = tree.get_parent()
        if (parent != null && position.last() != parent.size - 1) {
            val tmp = parent.get(parent.size - 1)
            var i = parent.size - 1;
            while (i > position.last() + 1) {
                parent.set(i, parent.get(i - 1))
                i -= 1
            }
            parent.set(i, tmp)
        }
    }

    open fun remove(beat_key: BeatKey, position: List<Int>) {
        val tree = this.get_tree(beat_key, position)
        val parent = tree.parent ?: return

        if (position == listOf(0) && parent.size == 1) {
            this.unset(beat_key, position)
            return
        }

        val index = position.last()
        val new_size = parent.size - 1

        if (new_size > 0) {
            for (i in parent.divisions.keys) {
                if (i < index || i == parent.size - 1) {
                    continue
                }
                parent.set(i, parent.get(i + 1))
            }
            parent.set_size(new_size, true)

            // replace the parent with the child
            if (new_size == 1 && this.get_beat_tree(beat_key) != parent) {
                parent.get_parent()!!.set(position[position.size - 2], parent.get(0))
            }
        }
    }

    open fun set_percussion_event(beat_key: BeatKey, position: List<Int>) {
        if (beat_key.channel != 9) {
            throw Exception("Attempting to set non-percussion channel")
        }

        var tree = this.get_tree(beat_key, position)
        if (tree.is_event()) {
            tree.unset_event()
        }

        var instrument = if (this.percussion_map.containsKey(beat_key.line_offset)) {
            this.percussion_map[beat_key.line_offset]!!
        } else {
            this.DEFAULT_PERCUSSION
        }

        tree.set_event(OpusEvent(
            instrument,
            this.RADIX,
            9,
            false
        ))
    }

    open fun set_event(beat_key: BeatKey, position: List<Int>, event: OpusEvent) {
        if (beat_key.channel == 9) {
            throw Exception("Attempting to set percussion channel")
        }

        var tree = this.get_tree(beat_key, position)
        tree.set_event(event)
    }

    open fun split_tree(beat_key: BeatKey, position: List<Int>, splits: Int) {
        var beat_tree = this.get_beat_tree(beat_key) ?: return
        val tree: OpusTree<OpusEvent> = if (position.size > 0) {
            this.get_tree(beat_key, position)
        } else {
            beat_tree.get(0)
        }

        if (tree.is_event()) {
            var new_tree = OpusTree<OpusEvent>()
            new_tree.set_size(splits)
            tree.replace_with(new_tree)
            new_tree.get(0).replace_with(tree)
        } else {
            tree.set_size(splits, true)
        }
    }

    open fun unset(beat_key: BeatKey, position: List<Int>) {
        val tree = this.get_tree(beat_key, position)
        if (tree.is_event()) {
            tree.unset_event()
        } else {
            tree.empty()
        }
    }

    open fun add_channel(channel: Int) {
        this.new_line(channel)
    }

    open fun change_line_channel(old_channel: Int, line_index: Int, new_channel: Int) {
        var tree = this.channel_trees[old_channel].removeAt(line_index)
        this.channel_trees[new_channel].add(tree)
    }

    open fun insert_beat(index: Int?) {
        this.opus_beat_count += 1
        for (channel in this.channel_trees) {
            for (line in channel) {
                line.insert(index, OpusTree<OpusEvent>())
            }
        }
    }

    open fun move_line(channel: Int, old_index: Int, new_index: Int) {
        if (old_index == new_index) {
            return
        }

        // Adjust the new_index so it doesn't get confused
        // when we pop() the old_index
        var adj_new_index: Int = if (new_index < 0) {
            this.channel_trees[channel].size + new_index
        } else {
            new_index
        }

        if (new_index < 0) {
            throw Exception("INDEXERROR")
        }
        if (old_index >= this.channel_trees[channel].size) {
            throw Exception("INDEXERROR")
        }

        var tree = this.channel_trees[channel].removeAt(old_index)
        this.channel_trees[channel].add(adj_new_index, tree)
    }

    open fun new_line(channel: Int, index: Int? = null) {
        var new_tree = OpusTree<OpusEvent>()
        new_tree.set_size(this.opus_beat_count)

        if (index == null) {
            this.channel_trees[channel].add(new_tree)
        } else {
            this.channel_trees[channel].add(index, new_tree)
        }
    }
    open fun overwrite_beat(old_beat: BeatKey, new_beat: BeatKey) {
        var new_tree = this.channel_trees[new_beat.channel][new_beat.line_offset].get(new_beat.beat).copy()
        var old_tree = this.channel_trees[old_beat.channel][old_beat.line_offset].get(old_beat.beat)
        old_tree.replace_with(new_tree)
        this.channel_trees[old_beat.channel][old_beat.line_offset].set(old_beat.beat, new_tree)
    }

    open fun remove_beat(beat_index: Int?) {
        for (channel in this.channel_trees) {
            for (line in channel) {
                line.pop(beat_index)
            }
        }
        this.set_beat_count(this.opus_beat_count - 1)
    }

    open fun remove_channel(channel: Int) {
        while (this.channel_trees[channel].size > 0) {
            this.remove_line(channel, 0)
        }
    }

    open fun remove_line(channel: Int, index: Int? = null) {
        var adj_index = if (index == null) {
            this.channel_trees[channel].size - 1
        } else {
            index
        }
        this.channel_trees[channel].removeAt(adj_index)
    }

    open fun replace_tree(beat_key: BeatKey, position: List<Int>, tree: OpusTree<OpusEvent>) {
        this.get_tree(beat_key, position).replace_with(tree)
    }

    open fun replace_beat(beat_key: BeatKey, tree: OpusTree<OpusEvent>) {
        var old_tree = this.channel_trees[beat_key.channel][beat_key.line_offset].get(beat_key.beat)
        old_tree.replace_with(tree)
    }



    open fun swap_channels(channel_a: Int, channel_b: Int) {
        var tmp = this.channel_trees[channel_b]
        this.channel_trees.set(channel_b, this.channel_trees[channel_a])
        this.channel_trees.set(channel_a, tmp)
    }

    //open fun export(path: String? = null, kwargs: HashMap) { }

    fun get_beat_tree(beat_key: BeatKey): OpusTree<OpusEvent> {
        var line_offset: Int
        if (beat_key.channel >= this.channel_trees.size) {
            throw Exception("Invalid BeatKey {beat_key}")
        }

        if (beat_key.line_offset < 0) {
            line_offset = this.channel_trees[beat_key.channel].size - beat_key.line_offset
        } else {
            line_offset = beat_key.line_offset
        }
        if (line_offset > this.channel_trees[beat_key.channel].size) {
            throw Exception("Invalid BeatKey {beat_key}")
        }
        return this.channel_trees[beat_key.channel][line_offset].get(beat_key.beat)
    }

    fun get_tree(beat_key: BeatKey, position: List<Int>): OpusTree<OpusEvent> {
        if (position.size < 1) {
            throw Exception("Invalid Position {position}")
        }
        var tree = this.get_beat_tree(beat_key)
        for (pos in position) {
            if (pos < tree.size) {
                tree = tree.get(pos)
            } else {
                throw Exception("Invalid Position {position}")
            }
        }

        return tree
    }

    private fun set_beat_count(new_count: Int) {
        this.opus_beat_count = new_count
        for (channel in this.channel_trees) {
            for (line in channel) {
                line.set_size(new_count, true)
            }
        }
    }

    open fun save(path: String? = null) { }
    open fun load(path: String) { }
    open fun new() {
        var new_line = OpusTree<OpusEvent>()
        new_line.set_size(4)
        this.channel_trees[0].add(new_line)
        this.opus_beat_count = 4
    }

    private fun get_working_dir(): String? { return "" }
    open fun load_folder(path: String) { }
    open fun load_file(path: String) { }

    fun import_midi(path: String) { }
}
