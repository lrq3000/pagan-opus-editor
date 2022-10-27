from __future__ import annotations
from typing import Optional, Dict, List, Tuple

from .layer_base import OpusManagerBase

class HistoryLayer(OpusManagerBase):
    def __init__(self):
        super().__init__()
        self.history_ledger = []
        self.history_locked = False
        self.multi_counter = 0

    def apply_undo(self):
        if not self.history_ledger:
            return

        self.history_locked = True
        if isinstance(self.history_ledger[-1], list):
            for func, args, kwargs in self.history_ledger.pop():
                func(*args,**kwargs)
        else:
            func, args, kwargs = self.history_ledger.pop()
            func(*args,**kwargs)
        self.history_locked = False

    def append_undoer(self, func, *args, **kwargs):
        if self.history_locked:
            return

        if self.multi_counter:
            self.history_ledger[-1].append((func, args, kwargs))
        else:
            self.history_ledger.append([
                (func, args, kwargs),
                (self.cursor.set, self.cursor.to_list(), {})
            ])

    def open_multi(self):
        if self.history_locked:
            return

        if not self.multi_counter:
            self.history_ledger.append([])
        self.multi_counter += 1

    def close_multi(self):
        if self.history_locked:
            return

        self.multi_counter -= 1
        if not self.multi_counter:
            self.history_ledger[-1].append((self.cursor.set, self.cursor.to_list(), {}))

    def setup_repopulate(self, start_position):
        '''Traverse a grouping and setup the history to recreate it for remove functions'''

        if self.history_locked:
            return

        self.open_multi()

        stack = [start_position]
        while stack:
            position = stack.pop(0)
            grouping = self.get_grouping(position)
            if grouping.is_structural():
                self.append_undoer(self.split_grouping, position, len(grouping))
                for k in range(len(grouping)):
                    next_position = position.copy()
                    next_position.append(k)
                    stack.append(next_position)
            elif grouping.is_event():
                event = list(grouping.get_events())[0]
                self.append_undoer(self.set_event, event.note, position, relative=event.relative)
            else:
                self.append_undoer(self.unset, position)

        self.close_multi()

    def overwrite_beat(self, old_beat, new_beat):
        old_position = [self.get_y(old_beat[0], old_beat[1]), old_beat[2]]
        old_grouping = self.channel_groupings[old_beat[0]][old_beat[1]][old_beat[2]].copy()
        self.append_undoer(self.replace_grouping, old_position, old_grouping)
        super().overwrite_beat(old_beat, new_beat)

    def link_beats(self, beat, target):
        # Wrap function call in multi so any sub calls are considered together
        self.open_multi()
        self.append_undoer(self.unlink_beat, *beat)
        super().link_beats(beat, target)
        self.close_multi()

    def swap_channels(self, channel_a, channel_b):
        self.append_undoer(self.swap_channels, channel_a, channel_b)
        super().swap_channels(channel_a, channel_b)

    def new_line(self, channel=0, index=None):
        self.append_undoer(self.remove_line, channel, index)
        super().new_line(channel)

    def remove_line(self, channel, index=None):
        y = self.get_y(channel, index)
        self.open_multi()
        self.append_undoer(self.new_line, channel, index)
        for i in range(self.opus_beat_count):
            self.setup_repopulate([y, i])
        self.close_multi()

        super().remove_line(channel, index)

    def insert_after(self, position):
        # Else is implicitly handled by 'split_grouping'
        rposition = position.copy()
        rposition[-1] += 1
        self.append_undoer(self.remove, rposition)
        super().insert_after(position)

    def split_grouping(self, position: List[int], splits: int):
        self.setup_repopulate(position[0:2])
        super().split_grouping(position, splits)

    def remove(self, position):
        self.setup_repopulate(position[0:2])
        super().remove(position)

    def insert_beat(self, index):
        self.append_undoer(self.remove_beat, index)
        super().insert_beat(index)

    def remove_beat(self, index):
        self.open_multi()
        self.append_undoer(self.insert_beat, index)
        y = 0
        for i in self.channel_order:
            for j in range(len(self.channel_groupings[i])):
                self.setup_repopulate([y, index])
                y += 1
        self.close_multi()
        super().remove_beat(index)

    def set_event(self, value, position, *, relative=False):
        grouping = self.get_grouping(position)
        if not grouping.is_event():
            self.append_undoer(
                self.unset,
                position
            )
        else:
            original_event = list(grouping.get_events())[0]
            self.append_undoer(
                self.set_event,
                original_event.note,
                position,
                relative=original_event.relative
            )

        super().set_event(value, position, relative=relative)


    def unset(self, position):
        grouping = self.get_grouping(position)
        if grouping.is_event():
            original_event = list(grouping.get_events())[0]
            self.append_undoer(
                self.set_event,
                original_event.note,
                position,
                relative=original_event.relative
            )
        super().unset(position)