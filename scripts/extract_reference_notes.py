#!/usr/bin/env python3
import xml.etree.ElementTree as ET
from pathlib import Path
from mido import MidiFile

ROOT = Path(__file__).resolve().parent.parent
XML = ROOT / 'Free-trial-photo-2026-02-13-14-27-38.xml'
MID = ROOT / 'Free-trial-photo-2026-02-13-14-27-38.mid'
OUT = ROOT / 'src/main/java/tatar/eljah/recorder/ReferenceComposition.java'
TARGET = 54

SEMITONES = {'C':0,'C#':1,'Db':1,'D':2,'D#':3,'Eb':3,'E':4,'F':5,'F#':6,'Gb':6,'G':7,'G#':8,'Ab':8,'A':9,'A#':10,'Bb':10,'B':11}
INV = {v:k for k,v in SEMITONES.items() if len(k)==1 or 'b' in k}


def read_xml_notes():
    root = ET.parse(XML).getroot()
    out = []
    for n in root.findall('.//note'):
        if n.find('rest') is not None:
            continue
        p = n.find('pitch')
        step = p.findtext('step')
        alter = p.findtext('alter')
        octave = int(p.findtext('octave'))
        if alter == '-1':
            step += 'b'
        elif alter == '1':
            step += '#'
        out.append((step, octave, n.findtext('type') or 'quarter'))
    return out


def midi_to_name(num):
    octv = num // 12 - 1
    sem = num % 12
    name = INV.get(sem, 'C')
    if name in ('A#', 'C#', 'D#', 'F#', 'G#'):
        flat_map = {'A#':'Bb','C#':'Db','D#':'Eb','F#':'Gb','G#':'Ab'}
        name = flat_map[name]
    return name, octv


def read_midi_notes():
    mid = MidiFile(MID)
    out = []
    for msg in mid:
        if msg.type == 'note_on' and msg.velocity > 0:
            out.append(midi_to_name(msg.note))
    return out


def main():
    xml_notes = read_xml_notes()
    midi_notes = read_midi_notes()

    base = xml_notes[:TARGET]
    midi_base = midi_notes[:TARGET]

    mismatches = []
    for i, (x, m) in enumerate(zip(base, midi_base)):
        if (x[0], x[1]) != m:
            mismatches.append((i, x, m))

    if mismatches:
        print('WARN mismatches detected:', mismatches[:5])

    pitches = ','.join(f'"{n[0]}"' for n in base)
    octaves = ','.join(str(n[1]) for n in base)
    durations = ','.join(f'"{n[2]}"' for n in base)

    java = f'''package tatar.eljah.recorder;

import java.util.ArrayList;
import java.util.List;

public final class ReferenceComposition {{
    private ReferenceComposition() {{}}

    public static final int EXPECTED_NOTES = {TARGET};

    private static final String[] PITCHES = new String[]{{{pitches}}};
    private static final int[] OCTAVES = new int[]{{{octaves}}};
    private static final String[] DURATIONS = new String[]{{{durations}}};

    public static List<NoteEvent> expected54() {{
        List<NoteEvent> out = new ArrayList<NoteEvent>();
        for (int i = 0; i < EXPECTED_NOTES; i++) {{
            out.add(new NoteEvent(PITCHES[i], OCTAVES[i], DURATIONS[i], 1 + (i / 4)));
        }}
        return out;
    }}
}}
'''
    OUT.write_text(java, encoding='utf-8')
    print(f'Generated {OUT} from XML/MIDI with {TARGET} notes')


if __name__ == '__main__':
    main()
