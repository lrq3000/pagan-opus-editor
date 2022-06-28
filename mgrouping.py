from __future__ import annotations
from typing import Optional, List, Tuple, Dict

from apres import NoteOn, NoteOff, PitchWheelChange, MIDI, SetTempo
from structures import Grouping, BadStateError

ERROR_CHUNK_SIZE = 19

class MissingCommaError(Exception):
    """Thrown when MGrouping.from_string() fails"""
    def __init__(self, repstring, fail_index, beat):
        bound_a = max(0, fail_index - ERROR_CHUNK_SIZE)
        bound_b = min(len(repstring), fail_index + ERROR_CHUNK_SIZE)
        chunk = repstring[bound_a:fail_index] + " " + repstring[fail_index:bound_b]
        if bound_a > 0:
            chunk = "..." + chunk[3:]
        if bound_b < len(repstring):
            chunk = chunk[0:-3] + "..."
        msg = f"\nError in beat {beat}\n"
        msg += f"Can't place notes in structural subgrouping or vice versa. You likely missed a \"{MGrouping.CH_NEXT}\" Here:\n"
        msg += ("-" * (fail_index - bound_a)) + "!\n"
        msg += chunk + "\n"
        msg += ("-" * (fail_index - bound_a)) + "^"
        super().__init__(msg)

class UnclosedGroupingError(Exception):
    """Thrown when a grouping is opened but not closed"""
    def __init__(self, repstring, index):
        bound_a = max(0, index - ERROR_CHUNK_SIZE)
        bound_b = min(len(repstring), index + ERROR_CHUNK_SIZE)
        chunk = repstring[bound_a:bound_b]
        msg = f"Unmatched \"{MGrouping.CH_OPEN}\" or \"{MGrouping.CH_CLOPEN}\" at position {index}: \n"
        if bound_a > 0:
            chunk = "..." + chunk[3:]
        if bound_b < len(repstring):
            chunk = chunk[0:-3] + "..."
        msg += ("-" * (index - bound_a)) + "!\n"
        msg += chunk + "\n"
        msg += ("-" * (index - bound_a)) + "^"
        super().__init__(msg)


class MGrouping(Grouping):
    CH_OPEN = "["
    CH_CLOSE = "]"
    CH_NEXT = ","
    # NOTE: CH_CLOPEN is a CH_CLOSE, CH_NEXT, and CH_OPEN in that order
    CH_CLOPEN = "|"
    SPECIAL_CHARS = (CH_OPEN, CH_CLOSE, CH_NEXT, CH_CLOPEN)

    @staticmethod
    def from_string(repstring: str, base: int = 12):
        # NOTE: Should the pitch bend be solely based on the fraction or should it consider the (1 / base)?

        # Remove all Whitespace
        repstring = repstring.strip()
        for character in " \n\t":
            repstring = repstring.replace(character, "")

        output = MGrouping()
        output.set_size(1)
        grouping_stack: List[MGrouping] = [output]
        register: List[Optional[int], Optional[int], Optional[float]] = [None, None, 0]
        opened_indeces: List[int] = []
        

        for i, character in enumerate(repstring):
            if character in (MGrouping.CH_CLOSE, MGrouping.CH_CLOPEN):
                # Remove completed grouping from stack
                grouping_stack.pop()
                opened_indeces.pop()

            if character in (MGrouping.CH_NEXT, MGrouping.CH_CLOPEN):
                # Back up existing divisions
                sub_divisions = grouping_stack[-1].divisions

                # Resize Active Grouping
                grouping_stack[-1].set_size(len(sub_divisions) + 1)

                # Replace Active Grouping's Divisions with backups
                grouping_stack[-1].divisions = sub_divisions

            if character in (MGrouping.CH_OPEN, MGrouping.CH_CLOPEN):
                new_grouping = grouping_stack[-1][-1]
                try:
                    new_grouping.set_size(1)
                except BadStateError as b:
                    raise MissingCommaError(repstring, i, len(output) - 1)
                grouping_stack.append(new_grouping)

                opened_indeces.append(i)

            if character not in MGrouping.SPECIAL_CHARS:
                if register[0] is None:
                    register[0] = int(character, base)
                elif register[1] is None:
                    register[1] = int(character, base)
                    #TODO: Pitch Bend

                    leaf = grouping_stack[-1][-1]
                    if register is not None:
                        try:
                            leaf.add_event(tuple(register))
                        except BadStateError as b:
                            raise MissingCommaError(repstring, i - 1, len(output) - 1, leaf._get_state())
                    register = [None, None, 0]


        if len(grouping_stack) > 1:
            raise UnclosedGroupingError(repstring, opened_indeces.pop())

        return output

    def set_note(self, note: int) -> None:
        if self.is_event():
            self.clear_events()

        self.add_event(note)

    def unset_note(self) -> None:
        self.clear_events()

def to_midi(opus, **kwargs):
    tempo = 120
    if ('tempo' in kwargs):
        tempo = kwargs['tempo']

    midi = MIDI("")
    midi.add_event( SetTempo(bpm=tempo) )
    if opus.__class__ == list:
        tracks = opus
    else:
        tracks = [opus]

    for track, grouping in enumerate(tracks):
        midi.add_event(
            PitchWheelChange(
                channel=track,
                value=0
            ),
            tick=0,
        )
        current_tick = 0
        for m in range(len(grouping)):
            beat = grouping[m]
            beat.flatten()
            div_size = midi.ppqn / len(beat)
            open_events = []
            for i, subgrouping in enumerate(beat):
                if not subgrouping.events:
                    continue

                while open_events:
                    octave, note, pitch_bend = open_events.pop()
                    midi.add_event(
                        NoteOff(
                            note=note + (octave * 12),
                            channel=track,
                            velocity=0
                        ),
                        tick=int(current_tick + (i * div_size)),
                    )
                for event in subgrouping.events:
                    if not event:
                        continue

                    open_events.append(event)
                    octave, note, pitch_bend = event
                    midi.add_event(
                        NoteOn(
                            note=note + (octave * 12),
                            channel=track,
                            velocity=64
                        ),
                        tick=int(current_tick + (i * div_size)),
                    )

                    if pitch_bend != 0:
                        midi.add_event(
                            PitchWheelChange(
                                channel=track,
                                value=pitch_bend
                            ),
                            tick=int(current_tick + (i * div_size)),
                        )
                        midi.add_event(
                            PitchWheelChange(
                                channel=track,
                                value=0
                            ),
                            tick=int(current_tick + ((i + 1) * div_size)),
                        )
            current_tick += midi.ppqn

            while open_events:
                octave, note, pitch_bend = open_events.pop()
                midi.add_event(
                    NoteOff(
                        note=note + (octave * 12),
                        channel=track,
                        velocity=0
                    ),
                    tick=int(current_tick),
                )
    return midi


if __name__ == "__main__":
    opus: List[MGrouping] = [
        MGrouping.from_string("""
            [22,32,22,32|22,32,22,32|
            22,32,[22,32,22,32]|22,32,20,30|
            22,32,22,32|22,32,22,32|
            22,32,22,32|22,32,20,30]
        """),

        MGrouping.from_string("""
            [42,42,42,42,42,42,42,42|42,42,42,40,40,40,40,40|
            42,42,42,42,42,42,42,42|42,42,42,40,40,40,40,40|
            42,42,42,42,42,42,42,42|42,42,42,40,40,40,40,40|
            42,42,42,42,42,42,42,42|42,42,42,40,40,40,40,40]
        """)
    ]

    midi = to_midi(opus, tempo=60)
    midi.save("test.mid")
