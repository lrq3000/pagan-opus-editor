package com.qfs.radixulous

import com.qfs.radixulous.opusmanager.OpusEvent
import com.qfs.radixulous.opusmanager.BeatKey
import org.junit.Test
import org.junit.Assert.*
import com.qfs.radixulous.opusmanager.HistoryLayer as OpusManager
import com.qfs.radixulous.opusmanager.HistoryCache
import com.qfs.radixulous.opusmanager.OpusManagerBase
import com.qfs.radixulous.structure.OpusTree

class HistoryCacheUnitTest {
    @Test
    fun test_historycache_multi_counter() {
        //TODO("test_historycache_multi_counter")
    }
    @Test
    fun test_historycache_push_set_cursor() {
        //TODO("test_historycache_push_set_cursor")
    }
    @Test
    fun test_historycache_clear() {
        //TODO("test_historycache_clear")
    }
    @Test
    fun test_historycache_position() {
        var cache = HistoryCache()
        cache.add_position(listOf(0,1))

        var cached_position = cache.get_position()
        assertEquals(cached_position, listOf(0,1))
    }
    @Test
    fun test_historycache_beatkey() {
        var cache = HistoryCache()
        cache.add_beatkey(BeatKey(1,2,3))
        var cached_beatkey = cache.get_beatkey()
        assertEquals(cached_beatkey, BeatKey(1,2,3))
    }
    @Test
    fun test_historycache_boolean() {
        var cache = HistoryCache()
        cache.add_boolean(false)
        cache.add_boolean(true )
        assertEquals(cache.get_boolean(), true)
        assertEquals(cache.get_boolean(), false)
    }
    @Test
    fun test_historycache_int() {
        var cache = HistoryCache()
        cache.add_int(2)
        cache.add_int(5)
        assertEquals(cache.get_int(), 5)
        assertEquals(cache.get_int(),2)
    }
    @Test
    fun test_historycache_beat() {
        var cache = HistoryCache()
        var test_a = OpusTree<OpusEvent>()
        var test_b = OpusTree<OpusEvent>()
        cache.add_beat(test_a)
        cache.add_beat(test_b)

        assertEquals(
            "Failed To cache beat correctly",
            cache.get_beat(),
            test_b
        )
        assertEquals(
            "Failed To cache beat correctly",
            cache.get_beat(),
            test_a
        )
    }

    @Test
    fun test_historycache_undoer_keys() {
        var cache = HistoryCache()

        cache.append_undoer_key("test_key_a")
        cache.append_undoer_key("test_key_b")
        assertEquals( "Popped wrong undoer key", listOf("test_key_b"), cache.pop() )
        assertEquals( "Popped wrong undoer key", listOf("test_key_a"), cache.pop() )
        cache.lock()
        assertFalse( cache.append_undoer_key("test_key") )
        cache.unlock()
        assertEquals(
            "[Correctly] returned false when calling append_undoer_key, but still appended key\n",
            listOf<List<String>>(),
            cache.pop()
        )



    }
    ///------------------------------------------------------

    @Test
    fun test_setup_repopulate() {
        //TODO("test_setup_repopulate")
    }

    @Test
    fun test_repopulate() {
        var manager = OpusManager()
        manager.new()
        manager.split_tree(BeatKey(0,0,0), listOf(), 5)

        manager.apply_undo()
    }

   @Test
   fun test_remove() {
       var key = BeatKey(0,0,0)
       var test_event = OpusEvent(12,12,0,false)

       var manager = OpusManager()
       manager.new()
       manager.split_tree(key, listOf(), 3)
       manager.set_event(key, listOf(1), test_event)
       manager.remove(key, listOf(1))

       manager.apply_undo()
       assertEquals(
           "Failed to undo remove",
           3,
           manager.get_tree(key, listOf()).size,
       )
       assertEquals(
           "Failed to undo remove with correct tree",
           test_event,
           manager.get_tree(key, listOf(1)).get_event()
       )
   }

    @Test
    fun test_convert_event_to_relative() {
        //var manager = OpusManager()
        //manager.new()
        //manager.set_event(BeatKey(0,0,0), listOf(), OpusEvent(12,12,0,false))
        //manager.set_event(BeatKey(0,0,1), listOf(), OpusEvent(24,12,0,false))
        //manager.convert_event_to_relative(BeatKey(0,0,1), listOf())
        //manager.apply_undo()
        //TODO("test_convert_event_to_relative")
    }
    @Test
    fun test_convert_event_to_absolute() {
        //TODO("test_convert_event_to_absolute")
    }
    @Test
    fun test_set_percussion_event() {
        var manager = OpusManager()
        manager.new()

        try { manager.set_percussion_event(BeatKey(0,0,0), listOf()) } catch (e: Exception) {}
        assertEquals(
            "Appended to history stack on failure.",
            true,
            manager.history_cache.isEmpty()
        )

        manager.set_percussion_channel(0)
        manager.set_percussion_event(BeatKey(0,0,0), listOf())

        manager.apply_undo()

        assertEquals(
            "Failed to undo set_percussion_event().",
            false,
            manager.get_tree(BeatKey(0,0,0), listOf()).is_event()
        )
    }

    @Test
    fun test_set_event() {
        var event = OpusEvent(12, 12, 0, false)
        var event_b = OpusEvent(12, 12, 0, false)
        var manager = OpusManager()
        manager.new()

        manager.set_percussion_channel(0)
        // WILL throw. don't want to assertThrows.
        try { manager.set_event(BeatKey(0, 0, 0), listOf(), event) } catch (e: Exception) {}
        assertEquals(
            "Appended to history stack on failure.",
            true,
            manager.history_cache.isEmpty()
        )

        manager.unset_percussion_channel()
        manager.set_event(BeatKey(0,0,0), listOf(), event)
        manager.apply_undo()

        assertEquals(
            "Failed to undo set_event()",
            false,
            manager.get_tree(BeatKey(0,0,0), listOf()).is_event()
        )

        manager.set_event(BeatKey(0,0,0), listOf(), event_b)
        manager.set_event(BeatKey(0,0,0), listOf(), event)

        assertEquals(
            "Failed to undo set_event()",
            event_b,
            manager.get_tree(BeatKey(0,0,0), listOf()).get_event()
        )
    }

    @Test
    fun test_unset() {
        var event = OpusEvent(12, 12, 0, false)
        var manager = OpusManager()
        manager.new()
        manager.set_event(BeatKey(0,0,0), listOf(), event)
        manager.unset(BeatKey(0,0,0), listOf())
        manager.apply_undo()

        assertEquals(
            "Failed to undo unset()",
            event,
            manager.get_tree(BeatKey(0,0,0), listOf()).get_event()
        )
    }

    @Test
    fun test_new_channel() {
        var manager = OpusManager()
        manager.new()

        manager.new_channel()
        manager.new_channel()
        manager.apply_undo()
        assertEquals(
            "Failed to undo new_channel",
            2,
            manager.channels.size
        )
        manager.apply_undo()
        assertEquals(
            "Failed to undo new_channel",
            1,
            manager.channels.size
        )
    }

    @Test
    fun test_change_line_channel() {
        //TODO("test_change_line_channel")
    }
    @Test
    fun test_insert_beat() {
        //TODO("test_insert_beat")
    }
    @Test
    fun test_new_line() {
        //TODO("test_new_line")
    }
    @Test
    fun test_overwrite_beat() {
        //TODO("test_overwrite_beat")
    }
    @Test
    fun test_remove_beat() {
        //TODO("test_remove_beat")
    }
    @Test
    fun test_remove_channel() {
        //TODO("test_remove_channel")
    }
    @Test
    fun test_remove_line() {
        //TODO("test_remove_line")
    }
    @Test
    fun test_replace_beat() {
        //TODO("test_replace_beat")
    }
    @Test
    fun test_replace_tree() {
        //TODO("test_replace_tree")
    }
    @Test
    fun test_set_beat_count() {
        //TODO("test_set_beat_count")
    }
    @Test
    fun test_get_midi() {
        //TODO("test_get_midi")
    }
    @Test
    fun test_to_json() {
        //TODO("test_to_json")
    }
    @Test
    fun test_save() {
        //TODO("test_save")
    }
    @Test
    fun test_load() {
        //TODO("test_load")
    }
    @Test
    fun test_import_midi() {
        //TODO("test_import_midi")
    }
    @Test
    fun test_purge_cache() {
        //TODO("test_purge_cache")
    }
    @Test
    fun test_reset_cache() {
        //TODO("test_reset_cache")
    }

    @Test
    fun test_insert_after() {
        //TODO("test_insert_after")
    }

    @Test
    fun test_split_tree() {
        //TODO("test_split_tree")
    }
}
