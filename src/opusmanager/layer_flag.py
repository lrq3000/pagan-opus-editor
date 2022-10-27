from __future__ import annotations
from typing import Optional, Dict, List, Tuple

from .layer_history import HistoryLayer

class UpdatesCache:
    def __init__(self):
        self.cache = {}

    def flag(self, key, value):
        if key not in self.cache:
            self.cache[key] = []
        self.cache[key].append(value)

    def unflag(self, key, value):
        if key not in self.cache:
            return
        self.cache[key].remove(value)

    def fetch(self, key, noclobber=False):
        output = self.cache.get(key, [])
        if not noclobber:
            self.cache[key] = []
        return output

class FlagLayer(HistoryLayer):
    def __init__(self):
        super().__init__()
        self.updates_cache = UpdatesCache()

    ## Layer-Specific Functions
    def flag(self, key, value):
        self.updates_cache.flag(key, value)

    def fetch(self, key, noclobber=False):
        return self.updates_cache.fetch(key, noclobber)

    ## OpusManagerBase Functions
    def replace_grouping(self, position, grouping):
        super().replace_grouping(position, grouping)
        channel, index = self.get_channel_index(position[0])
        self.flag('beat_change', (channel, index, position[1]))

    def overwrite_beat(self, old_beat, new_beat):
        super().overwrite_beat(old_beat, new_beat)
        self.flag('beat_change', old_beat)

    def unlink_beat(self, channel, index, beat):
        super().unlink_beat(channel, index, beat)
        self.flag('beat_change', (channel, index, beat))

    def link_beats(self, beat, target):
        super().link_beats(beat, target)
        self.flag('beat_change', beat)

    def _insert_after_ignore_linked(self, position: List[int]):
        super()._insert_after_ignore_linked(position)
        channel, line = self.get_channel_index(position[0])
        self.flag('beat_change', (channel, line, position[1]))

    def _split_grouping_ignore_linked(self, position, splits):
        super()._split_grouping_ignore_linked(position, splits)
        channel, line = self.get_channel_index(position[0])
        self.flag('beat_change', (channel, line, position[1]))

    def swap_channels(self, channel_a, channel_b):
        super().swap_channels(channel_a, channel_b)

        len_a = len(self.channel_groupings[channel_a])
        len_b = len(self.channel_groupings[channel_b])
        for i in range(len_b):
            self.flag('line', (channel_a, len_b - 1 - i, 'pop'))

        for i in range(len_a):
            self.flag('line', (channel_b, len_a - 1 - i, 'pop'))

        for i in range(len_a):
            self.flag('line', (channel_a, i, 'new'))

        for i in range(len_b):
            self.flag('line', (channel_b, i, 'new'))

    def new(self):
        super().new()

        for i in range(self.opus_beat_count):
            self.flag('beat', (i, 'new'))

        for i, channel in enumerate(self.channel_groupings):
            for j, _line in enumerate(channel):
                self.flag('line', (i, j, 'init'))

    def load(self, path: str) -> None:
        super().load(path)

        for i in range(self.opus_beat_count):
            self.flag('beat', (i, 'new'))

        for i, channel in enumerate(self.channel_groupings):
            for j, _line in enumerate(channel):
                self.flag('line', (i, j, 'init'))

    def _set_event_ignore_link(self, value, position, *, relative=False):
        super()._set_event_ignore_link(value, position, relative=relative)

        channel, index = self.get_channel_index(position[0])
        self.flag('beat_change', (channel, index, position[1]))

    def _unset_ignore_link(self, position):
        super()._unset_ignore_link(position)

        channel, index = self.get_channel_index(position[0])
        self.flag('beat_change', (channel, index, position[1]))

    def insert_beat(self, index=None):
        if index is None:
            rindex = self.opus_beat_count - 1
        else:
            rindex = index

        super().insert_beat(index)

        self.flag('beat', (rindex, 'new'))

    def new_line(self, channel=0, index=None):
        super().new_line(channel, index)

        if index is None:
            line_index = len(self.channel_groupings[channel]) - 1
        else:
            line_index = index

        self.flag('line', (channel, line_index, 'new'))

    def _remove_ignore_link(self, position: List[int]):
        super()._remove_ignore_link(position)

        channel, index = self.get_channel_index(position[0])
        self.flag('beat_change', (channel, index, position[1]))

    def remove_beat(self, index=None):
        if index is None:
            rindex = self.opus_beat_count - 1
        else:
            rindex = index

        super().remove_beat(index)

        self.flag('beat', (rindex, 'pop'))

    def remove_line(self, channel, index=None):
        super().remove_line(channel, index)

        if index is None:
            index = len(self.channel_groupings[channel])

        # Flag changes to cache
        self.flag('line', (channel, index, 'pop'))
