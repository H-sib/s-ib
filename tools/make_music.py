"""Synthesises the original background music for the promo and tutorial videos (no samples, no downloads: every note is computed here, so the tracks are ours).
Usage: python tools\\make_music.py <output folder> [track ...]       writes one wav per track (all six when none is named). Run it with any Python that has numpy.
Each video has its own track, with its own key, tempo and sound, and a tempo that the video spec repeats (music|file|volume|bpm) so that cuts fall on the beat:
  teaser    76 bpm  D minor to D major   dark and cinematic: a drone, a heartbeat, taiko hits, a riser, a stop, then the reveal chord (31 beats, then it rests)
  trailer  128 bpm  E major              bright: four on the floor, a bouncing bass, plucked arpeggios, claps, a lift every eight bars
  pastel    72 bpm  F major seventh      calm: a soft electric piano, bells, a brushed shaker, plenty of room
  street   118 bpm  G mixolydian         loud and funky: a slapped bass, claps, a cowbell, brass stabs on the off beats
  play     112 bpm  C major              playful: marimba, a walking bass, wood block ticks, a little pizzicato answer
  code     100 bpm  F sharp minor        electronic: a square arpeggio, a deep bass, a tight kick and tick
  gallery   92 bpm  D major             for The Atelier: harp like plucks, soft strings, an upright style bass, brushed shaker, a bell every other bar
The music cannot be judged by a script: the person who publishes the videos has to listen to each track once (see STUDIO_HANDOFF.txt, VIDEOS REMADE)."""
import pathlib
import sys
import wave

import numpy as np

SR = 44100


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def env(n, attack=0.005, release=0.05, curve=1.0):
    e = np.ones(n)
    a, r = min(int(attack * SR), n), min(int(release * SR), n)
    if a:
        e[:a] = np.linspace(0, 1, a) ** curve
    if r:
        e[-r:] *= np.linspace(1, 0, r) ** curve
    return e


def time(seconds):
    return np.arange(int(seconds * SR)) / SR


# ---------------------------------------------------------------- voices

def pad(f, seconds, bright=1.0, attack=0.9, release=1.0):
    t = time(seconds)
    out = np.zeros_like(t)
    for detune in (-0.004, 0.0, 0.004):
        for k, amp in ((1, 1.0), (2, 0.38 * bright), (3, 0.16 * bright), (4, 0.07 * bright)):
            out += amp * np.sin(2 * np.pi * f * (1 + detune) * k * t + detune * 50)
    return out * env(len(t), attack, release, 1.5) / 6


def epiano(f, seconds=1.2):
    t = time(seconds)
    tone = np.sin(2 * np.pi * f * t) * np.exp(-2.2 * t) + 0.35 * np.sin(2 * np.pi * f * 2 * t) * np.exp(-4.5 * t) + 0.12 * np.sin(2 * np.pi * f * 5.04 * t) * np.exp(-14 * t)
    return tone * (1 + 0.08 * np.sin(2 * np.pi * 4.5 * t)) * env(len(t), 0.004, 0.2)


def bell(f, seconds=2.0):
    t = time(seconds)
    parts = ((1.0, 1.0, 2.0), (2.76, 0.5, 3.0), (5.4, 0.25, 4.5), (8.9, 0.12, 7.0))
    return sum(a * np.sin(2 * np.pi * f * r * t) * np.exp(-d * t) for r, a, d in parts) * env(len(t), 0.002, 0.1)


def pluck(f, seconds=0.5, decay=5.5):
    t = time(seconds)
    tone = np.sin(2 * np.pi * f * t) + 0.45 * np.sin(2 * np.pi * f * 2 * t) * np.exp(-6 * t) + 0.2 * np.sin(2 * np.pi * f * 3 * t) * np.exp(-9 * t)
    return tone * np.exp(-decay * t) * env(len(t), 0.004, 0.05)


def marimba(f, seconds=0.5):
    t = time(seconds)
    return (np.sin(2 * np.pi * f * t) * np.exp(-7 * t) + 0.4 * np.sin(2 * np.pi * f * 4 * t) * np.exp(-30 * t)) * env(len(t), 0.002, 0.04)


def saw(f, seconds, harmonics=14, cutoff=0.5, decay=6.0):
    t = time(seconds)
    out = np.zeros_like(t)
    for k in range(1, harmonics + 1):
        out += np.sin(2 * np.pi * f * k * t) / k * np.exp(-decay * k * cutoff * t)
    return out * env(len(t), 0.004, 0.06)


def square(f, seconds):
    t = time(seconds)
    out = np.zeros_like(t)
    for k in range(1, 12, 2):
        out += np.sin(2 * np.pi * f * k * t) / k
    return out * 0.6 * env(len(t), 0.003, 0.03)


def sub(f, seconds, attack=0.02, release=0.2):
    t = time(seconds)
    return (np.sin(2 * np.pi * f * t) + 0.25 * np.sin(2 * np.pi * f * 2 * t)) * env(len(t), attack, release)


def slap(f, seconds=0.3):
    t = time(seconds)
    return (np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * f * 3 * t) * np.exp(-25 * t)) * np.exp(-9 * t) * env(len(t), 0.002, 0.03)


def kick(depth=1.0):
    t = time(0.3)
    return np.sin(2 * np.pi * (46 * t + 74 * (1 - np.exp(-20 * t)) / 20)) * np.exp(-9 * t) * depth


def taiko():
    t = time(1.6)
    rng = np.random.default_rng(3)
    return np.sin(2 * np.pi * (52 + 40 * np.exp(-8 * t)) * t) * np.exp(-3.2 * t) + rng.standard_normal(len(t)) * np.exp(-30 * t) * 0.35


def noise_hit(seconds, decay, bright=1.0, seed=1):
    t = time(seconds)
    rng = np.random.default_rng(seed)
    noise = rng.standard_normal(len(t))
    noise = noise - np.concatenate(([0], noise[:-1])) * (0.5 + 0.45 * bright)
    return noise * np.exp(-decay * t)


def clap(seed=2):
    return noise_hit(0.2, 22, 0.6, seed) * 0.8


def hat(seed=3):
    return noise_hit(0.05, 90, 1.0, seed) * 0.5


def shaker(seed=4):
    t = time(0.12)
    return noise_hit(0.12, 35, 1.0, seed) * np.sin(np.pi * np.clip(t / 0.12, 0, 1)) * 0.6


def cowbell():
    t = time(0.25)
    return (np.sin(2 * np.pi * 587 * t) + np.sin(2 * np.pi * 845 * t)) * np.exp(-14 * t) * 0.4


def woodblock():
    t = time(0.08)
    return np.sin(2 * np.pi * 1450 * t) * np.exp(-60 * t) * 0.5


def riser(seconds, rise=2200):
    t = time(seconds)
    rng = np.random.default_rng(9)
    noise = rng.standard_normal(len(t))
    out, state = np.zeros(len(t)), 0.0
    for i in range(len(t)):
        state += (noise[i] - state) * (0.02 + 0.5 * (i / len(t)) ** 2)
        out[i] = state
    return (out * 3 + np.sin(2 * np.pi * (180 + rise * (t / seconds) ** 2) * t) * 0.25) * (t / seconds) ** 2.2 * 0.5


# ---------------------------------------------------------------- the mixing desk

class Mix:
    def __init__(self, seconds):
        self.track = np.zeros((2, int((seconds + 4) * SR)))
        self.seconds = seconds

    def add(self, sound, at, gain=1.0, pan=0.5):
        start = int(at * SR)
        if start >= self.track.shape[1] or start < 0:
            return
        sound = sound[: self.track.shape[1] - start] * gain
        self.track[0, start: start + len(sound)] += sound * (1 - pan) ** 0.5
        self.track[1, start: start + len(sound)] += sound * pan ** 0.5

    def echo(self, delay, feedback, mix):
        d, out = int(delay * SR), self.track.copy()
        for tap in range(1, 5):
            shifted = np.zeros_like(self.track)
            shifted[0, d * tap:] = self.track[1, : self.track.shape[1] - d * tap] * feedback ** tap
            shifted[1, d * tap:] = self.track[0, : self.track.shape[1] - d * tap] * feedback ** tap
            out += shifted * mix
        self.track = out

    def room(self, seconds=1.6, mix=0.22, seed=7):
        rng = np.random.default_rng(seed)
        n = int(seconds * SR)
        ir = rng.standard_normal((2, n)) * np.exp(-3.2 * np.arange(n) / SR)
        size = 1 << int(np.ceil(np.log2(self.track.shape[1] + n)))
        wet = np.stack([np.fft.irfft(np.fft.rfft(self.track[c], size) * np.fft.rfft(ir[c], size), size)[: self.track.shape[1]] for c in range(2)])
        wet *= np.abs(self.track).max() / (np.abs(wet).max() + 1e-9)
        self.track = self.track + wet * mix

    def done(self, fade_in=0.4, fade_out=2.0, loudness=0.17):
        """The mix with its peaks rounded off (so one big hit does not make the rest quiet) and the same average loudness for every track."""
        out = self.track[:, : int(self.seconds * SR)]
        out = out * env(out.shape[1], fade_in, fade_out)
        out = np.tanh(out / (np.percentile(np.abs(out), 99.5) + 1e-9) * 1.1)
        out *= loudness / (np.sqrt((out ** 2).mean()) + 1e-9)
        return np.clip(out, -0.95, 0.95)


def save(path, track):
    data = (np.clip(track.T, -1, 1) * 32767).astype("<i2")
    with wave.open(str(path), "wb") as out:
        out.setnchannels(2)
        out.setsampwidth(2)
        out.setframerate(SR)
        out.writeframes(data.tobytes())
    print("wrote", path, "%.0f s, %.1f MB, peak %.2f, loudness %.3f" % (track.shape[1] / SR, path.stat().st_size / 1e6, np.abs(track).max(), np.sqrt((track ** 2).mean())))


# ---------------------------------------------------------------- the six tracks

def teaser():
    bpm, beat = 76, 60 / 76
    mix = Mix(36)
    bars = [(38, [45, 50]), (38, [45, 50]), (34, [41, 46]), (36, [43, 48]), (38, [45, 50]), (34, [41, 46]), (31, [38, 43]), (38, [45, 50, 54])]   # D minor ... then D major at the reveal
    for index, (root, extra) in enumerate(bars):
        at = index * 4 * beat
        last = index == 7
        notes = [root] + extra if not last else [38, 45, 50, 54, 57]
        for n in notes:
            mix.add(pad(hz(n - 12 + (12 if last else 0)), 4 * beat + 1.5, 0.7 if not last else 1.3, 1.4 if not last else 0.05, 2.5), at, 0.9 if not last else 1.2, 0.35 + 0.3 * ((n % 5) / 5))
        if index >= 0 and not last:
            for k in range(2):
                mix.add(bell(hz(root + 24 + (7 if k else 0))), at + k * 2 * beat + 0.02, 0.18 + 0.03 * index, 0.3 + 0.4 * k)
        if index >= 2 and not last:                                    # a heartbeat
            for k in range(4):
                mix.add(kick(0.55), at + k * beat, 0.5 if k % 2 == 0 else 0.3, 0.5)
        if index >= 3 and not last:                                    # plucked eighths
            scale = [root + 12, root + 15, root + 19, root + 22, root + 24, root + 22, root + 19, root + 15]
            for k in range(8):
                mix.add(pluck(hz(scale[k]), 0.6, 4.5), at + k * beat / 2, 0.20, 0.25 + 0.5 * (k % 2))
        if index in (4, 5, 6):
            for k in (0, 2):
                mix.add(taiko(), at + k * beat, 0.8 + 0.1 * index, 0.5)
        if index in (5, 6):
            for k in range(16):
                mix.add(hat(k), at + k * beat / 4, 0.05 + 0.01 * k * (index - 4), 0.3 + 0.4 * (k % 2))
    mix.add(riser(3 * beat * 2, 3000), 5.7 * 4 * beat - 0.0, 0.9, 0.5)        # the lift into the stop
    for k in range(12):                                                    # a snare roll that speeds up
        at = 6 * 4 * beat + 2 * beat + k * (beat / 4) * (1 - 0.03 * k)
        mix.add(noise_hit(0.12, 30, 0.7, k), at, 0.25 + 0.04 * k, 0.5)
    mix.add(taiko(), 7 * 4 * beat, 1.5, 0.5)                                # the reveal
    mix.add(noise_hit(1.8, 3.0, 0.2, 77), 7 * 4 * beat, 0.5, 0.5)
    for k, n in enumerate([74, 78, 81, 86, 90]):
        mix.add(bell(hz(n), 3.5), 7 * 4 * beat + 0.1 + k * 0.16, 0.34, 0.2 + 0.15 * k)
    mix.echo(beat * 0.75, 0.38, 0.5)
    mix.room(2.4, 0.3)
    return mix.done(0.1, 3.2, 0.12)


def trailer():
    bpm, beat = 128, 60 / 128
    seconds = 80
    mix = Mix(seconds)
    chords = [(40, [4, 7]), (47, [4, 7]), (45, [3, 7]), (43, [4, 7])]       # E B C#m A (as the root midi note and its chord tones)
    bars = int(seconds / (4 * beat)) + 1
    for index in range(bars):
        root, tones = chords[index % 4]
        at = index * 4 * beat
        lift = index % 8 == 7
        for t in [0] + tones:
            mix.add(pad(hz(root + 12 + t), 4 * beat + 0.4, 1.0, 0.05, 0.3), at, 0.55, 0.5)
        for k in range(4):
            mix.add(kick(1.0), at + k * beat, 0.8, 0.5)
            mix.add(sub(hz(root - 12), beat * 0.45), at + k * beat + beat / 2, 0.55, 0.5)
        for k in (1, 3):
            mix.add(clap(index * 4 + k), at + k * beat, 0.35, 0.5)
        for k in range(8):
            mix.add(hat(index * 8 + k), at + k * beat / 2 + beat / 4, 0.12, 0.3 + 0.4 * (k % 2))
        if index >= 1:
            notes = [root + 24, root + 24 + tones[0], root + 24 + tones[1], root + 36]
            pattern = [0, 1, 2, 3, 2, 1, 2, 3] if not lift else [0, 1, 2, 3, 3, 2, 1, 0]
            for k in range(16):
                mix.add(pluck(hz(notes[pattern[k % 8]]), 0.4, 6.0), at + k * beat / 4, 0.17 if k % 2 else 0.24, 0.2 + 0.6 * ((k % 4) / 3))
        if lift:
            mix.add(riser(4 * beat, 2600), at, 0.4, 0.5)
    mix.echo(beat * 0.75, 0.3, 0.35)
    mix.room(1.2, 0.14)
    return mix.done(0.1, 2.5)


def pastel():
    bpm, beat = 72, 60 / 72
    seconds = 100
    mix = Mix(seconds)
    chords = [(53, [4, 7, 11]), (55, [4, 7, 9]), (52, [3, 7, 10]), (57, [3, 7, 10])]     # Fmaj7 G6 Em7 Am7
    bars = int(seconds / (4 * beat)) + 1
    for index in range(bars):
        root, tones = chords[index % 4]
        at = index * 4 * beat
        for t in [0] + tones:
            mix.add(epiano(hz(root + t), 3 * beat), at + (0.02 if t else 0), 0.30, 0.3 + 0.1 * t % 0.5)
        mix.add(sub(hz(root - 24), 2.2 * beat, 0.05, 0.6), at, 0.6, 0.5)
        notes = [root + 12 + t for t in [0] + tones] + [root + 24]
        pattern = [0, 2, 1, 3, 2, 4, 3, 1]
        for k in range(8):
            if (index + k) % 3 != 2:
                mix.add(bell(hz(notes[pattern[k] % len(notes)] + 12), 1.8), at + k * beat / 2, 0.075, 0.2 + 0.6 * (k % 2))
        for k in range(8):
            mix.add(shaker(index * 8 + k), at + k * beat / 2 + beat / 4, 0.10, 0.4 + 0.2 * (k % 2))
        mix.add(kick(0.45), at, 0.5, 0.5)
        mix.add(kick(0.3), at + 2.5 * beat, 0.35, 0.5)
    mix.echo(beat * 0.75, 0.4, 0.45)
    mix.room(2.2, 0.28)
    return mix.done(0.8, 3.0)


def street():
    bpm, beat = 118, 60 / 118
    seconds = 100
    mix = Mix(seconds)
    chords = [(43, [4, 7, 10]), (48, [4, 7, 10]), (43, [4, 7, 10]), (41, [4, 7, 10])]     # G7 C7 G7 F7 flavour (mixolydian)
    bars = int(seconds / (4 * beat)) + 1
    bass = [0, None, 0, 12, None, 7, 0, None, 10, None, 7, None, 5, 7, 0, None]
    for index in range(bars):
        root, tones = chords[index % 4]
        at = index * 4 * beat
        for k, step in enumerate(bass):
            if step is not None:
                mix.add(slap(hz(root - 12 + step), 0.28), at + k * beat / 4, 0.8 if k % 4 == 0 else 0.55, 0.5)
        for k in (0, 2):
            mix.add(kick(1.0), at + k * beat, 0.85, 0.5)
        mix.add(kick(0.9), at + 2.75 * beat, 0.6, 0.5)
        for k in (1, 3):
            mix.add(clap(index * 7 + k), at + k * beat, 0.5, 0.5)
        for k in range(16):
            mix.add(hat(index * 16 + k), at + k * beat / 4, 0.08 if k % 2 else 0.14, 0.3 + 0.4 * (k % 3) / 2)
        for k in (3, 6, 10, 14):
            mix.add(cowbell(), at + k * beat / 4, 0.28, 0.65)
        stabs = [(2, 1.0), (5, 0.7), (10, 1.0)]
        for k, g in stabs:
            if index % 2 == 1 or k != 5:
                for t in [0] + tones:
                    mix.add(saw(hz(root + 12 + t), 0.18, 12, 0.7, 11.0), at + k * beat / 2, 0.14 * g, 0.5)
    mix.echo(beat * 0.5, 0.25, 0.25)
    mix.room(0.8, 0.1)
    return mix.done(0.1, 2.5)


def play():
    bpm, beat = 112, 60 / 112
    seconds = 100
    mix = Mix(seconds)
    chords = [(48, [4, 7]), (53, [4, 7]), (55, [4, 7]), (48, [4, 7]), (45, [3, 7]), (53, [4, 7]), (55, [4, 7, 10]), (48, [4, 7])]     # C F G C Am F G7 C
    bars = int(seconds / (4 * beat)) + 1
    for index in range(bars):
        root, tones = chords[index % 8]
        at = index * 4 * beat
        walk = [0, tones[0], tones[1], tones[0]]
        for k in range(4):
            mix.add(marimba(hz(root - 12 + walk[k]), 0.4) * 1.4, at + k * beat, 0.5, 0.5)
        for k in (0, 1, 2, 3):
            mix.add(woodblock(), at + k * beat + beat / 2, 0.4, 0.3 + 0.4 * (k % 2))
        mix.add(kick(0.8), at, 0.7, 0.5)
        mix.add(kick(0.6), at + 2 * beat, 0.55, 0.5)
        mix.add(clap(index * 3 + 1), at + beat, 0.28, 0.5)
        mix.add(clap(index * 3 + 2), at + 3 * beat, 0.28, 0.5)
        melody = [root + 12 + t for t in [0] + tones] + [root + 24]
        phrase = [(0, 0), (1, 0.5), (2, 1), (3, 2), (2, 2.5), (1, 3)] if index % 2 == 0 else [(3, 0), (2, 0.5), (1, 1), (0, 2), (1, 2.75), (2, 3.25)]
        for idx, when in phrase:
            mix.add(marimba(hz(melody[idx] + 12), 0.6), at + when * beat, 0.40, 0.2 + 0.6 * (idx / 3))
        for t in tones:
            mix.add(pluck(hz(root + 12 + t), 0.3, 12.0), at + 1.5 * beat, 0.16, 0.7)
            mix.add(pluck(hz(root + 12 + t), 0.3, 12.0), at + 3.5 * beat, 0.16, 0.3)
    mix.echo(beat * 0.5, 0.3, 0.25)
    mix.room(0.9, 0.12)
    return mix.done(0.1, 2.5)


def code():
    bpm, beat = 100, 0.6
    seconds = 100
    mix = Mix(seconds)
    chords = [(42, [3, 7]), (38, [4, 7]), (45, [4, 7]), (40, [3, 7])]       # F#m D A E-ish
    bars = int(seconds / (4 * beat)) + 1
    for index in range(bars):
        root, tones = chords[index % 4]
        at = index * 4 * beat
        notes = [root + 24, root + 24 + tones[0], root + 24 + tones[1], root + 36]
        pattern = [0, 1, 2, 3, 2, 1, 0, 1, 2, 3, 3, 2, 1, 2, 3, 2]
        for k in range(16):
            mix.add(square(hz(notes[pattern[k]]), beat / 4 * 0.8), at + k * beat / 4, 0.11 + 0.03 * (k % 4 == 0), 0.15 + 0.7 * (k % 2))
        mix.add(sub(hz(root - 24), 4 * beat * 0.98, 0.03, 0.2), at, 0.75, 0.5)
        for k in range(4):
            mix.add(kick(0.9), at + k * beat, 0.7, 0.5)
        for k in range(8):
            mix.add(hat(index * 8 + k), at + k * beat / 2 + beat / 4, 0.12, 0.5)
        if index % 4 == 3:
            for k in range(8):
                mix.add(noise_hit(0.1, 40, 1.0, k), at + 3 * beat + k * beat / 8, 0.15 + 0.03 * k, 0.5)
    mix.echo(beat * 0.75, 0.3, 0.3)
    mix.room(0.8, 0.1)
    return mix.done(0.1, 2.5)


def gallery():
    bpm, beat = 92, 60 / 92
    seconds = 100
    mix = Mix(seconds)
    chords = [(50, [4, 7, 11]), (55, [4, 7, 11]), (47, [3, 7, 10]), (57, [4, 7, 10])]       # D, G, B minor seven, A seven
    bars = int(seconds / (4 * beat)) + 1
    for index in range(bars):
        root, tones = chords[index % 4]
        at = index * 4 * beat
        for t in [0] + tones:
            mix.add(pad(hz(root + 12 + t), 4 * beat + 0.8, 0.8, 0.5, 0.9), at, 0.34, 0.3 + 0.4 * (t % 3) / 2)
        for k, step in ((0, 0), (2, 7), (3, 4)):
            mix.add(sub(hz(root - 12 + step), beat * 0.9, 0.01, 0.25), at + k * beat, 0.62 if k == 0 else 0.4, 0.5)
        notes = [root + 12 + t for t in [0] + tones] + [root + 24, root + 24 + tones[0]]
        pattern = [0, 1, 2, 3, 4, 3, 2, 1] if index % 2 == 0 else [4, 3, 2, 1, 0, 1, 2, 3]
        for k in range(8):
            mix.add(pluck(hz(notes[pattern[k]]), 1.1, 3.0), at + k * beat / 2, 0.28 if k % 2 == 0 else 0.2, 0.2 + 0.6 * (k / 7))
        for k in range(8):
            mix.add(shaker(index * 8 + k), at + k * beat / 2 + beat / 4, 0.07, 0.4 + 0.2 * (k % 2))
        mix.add(kick(0.4), at, 0.35, 0.5)
        if index % 2 == 0:
            mix.add(bell(hz(root + 36), 2.4), at + 2 * beat, 0.10, 0.65)
    mix.echo(beat * 0.75, 0.35, 0.4)
    mix.room(1.8, 0.2)
    return mix.done(0.5, 2.5)



# ---------------------------------------------------------------- a track from parameters (no code to write: every session can make a different one)

KEYS = {"C": 0, "C#": 1, "Db": 1, "D": 2, "D#": 3, "Eb": 3, "E": 4, "F": 5, "F#": 6, "Gb": 6, "G": 7, "G#": 8, "Ab": 8, "A": 9, "A#": 10, "Bb": 10, "B": 11}
MODES = {"major": [0, 2, 4, 5, 7, 9, 11], "minor": [0, 2, 3, 5, 7, 8, 10], "dorian": [0, 2, 3, 5, 7, 9, 10], "mixolydian": [0, 2, 4, 5, 7, 9, 10], "lydian": [0, 2, 4, 6, 7, 9, 11],
         "phrygian": [0, 1, 3, 5, 7, 8, 10], "harmonic": [0, 2, 3, 5, 7, 8, 11], "pentatonic": [0, 2, 4, 7, 9, 0, 2]}
# the 16 steps of a bar: where each drum sound falls (steps counted 0 to 15, a step is a sixteenth)
DRUMS = {
    "none": {},
    "fourfloor": {"kick": [0, 4, 8, 12], "clap": [4, 12], "hat": [2, 6, 10, 14]},
    "halftime": {"kick": [0, 10], "clap": [8], "hat": [0, 2, 4, 6, 8, 10, 12, 14]},
    "breakbeat": {"kick": [0, 6, 10], "clap": [4, 12], "hat": [0, 2, 4, 6, 8, 10, 12, 14, 15]},
    "shuffle": {"kick": [0, 8], "clap": [4, 12], "hat": [0, 3, 6, 8, 11, 14]},
    "boomclap": {"kick": [0, 4, 8, 12], "clap": [2, 6, 10, 14]},
    "dub": {"kick": [0, 8], "clap": [12], "hat": [6, 14], "wood": [3, 11]},
    "trap": {"kick": [0, 7, 10], "clap": [8], "hat": [0, 2, 4, 6, 8, 10, 12, 13, 14, 15]},
    "latin": {"kick": [0, 6, 8, 14], "wood": [0, 3, 6, 10, 12], "shaker": list(range(0, 16, 2))},
    "brush": {"kick": [0], "shaker": [0, 3, 6, 8, 11, 14], "wood": [4, 12]},
}
BASS = {"root": [0, 8], "pulse": [0, 2, 4, 6, 8, 10, 12, 14], "syncopated": [0, 3, 6, 10, 12], "long": [0], "walking": [0, 4, 8, 12], "offbeat": [2, 6, 10, 14]}
ARPS = {"up": [0, 1, 2, 3, 0, 1, 2, 3], "updown": [0, 1, 2, 3, 2, 1, 2, 1], "skip": [0, 2, 1, 3, 2, 0, 3, 1], "none": []}
LEADS = {"pluck": lambda f: pluck(f, 0.5, 5.5), "marimba": lambda f: marimba(f, 0.6), "epiano": lambda f: epiano(f, 1.0), "bell": lambda f: bell(f, 1.6), "square": lambda f: square(f, 0.22),
         "saw": lambda f: saw(f, 0.3, 12, 0.6, 7.0), "harp": lambda f: pluck(f, 1.1, 3.0)}


def scale_note(root, mode, degree):
    octave, idx = divmod(degree, 7)
    return root + MODES[mode][idx] + 12 * octave


def generated(params):
    """params: key mode bpm seconds prog drums bass lead pad arp density swing seed echo room (all optional). Chords are built on the scale degrees of prog (1 is the tonic)."""
    g = lambda k, d: params.get(k, d)
    root = 48 + KEYS[g("key", "C")]
    mode, bpm, seconds = g("mode", "major"), float(g("bpm", 100)), float(g("seconds", 90))
    prog = [int(x) - 1 for x in str(g("prog", "1,5,6,4")).split(",")]
    drums, bass, lead, arp = g("drums", "fourfloor"), g("bass", "root"), g("lead", "pluck"), g("arp", "updown")
    pad_on, density, swing, seed = str(g("pad", "on")) == "on", int(g("density", 2)), float(g("swing", 0.0)), int(g("seed", 1))
    beat = 60.0 / bpm
    step = beat / 4
    rng = np.random.default_rng(seed)
    mix = Mix(seconds)
    for bar in range(int(seconds / (4 * beat)) + 1):
        at = bar * 4 * beat
        degree = prog[bar % len(prog)]
        chord = [scale_note(root, mode, degree + k) for k in (0, 2, 4, 6)]
        if pad_on:
            for n in chord[:3]:
                mix.add(pad(hz(n), 4 * beat + 0.6, 0.9, 0.5, 0.8), at, 0.30, 0.3 + 0.4 * ((n % 5) / 4))
        for st in BASS[bass]:
            when = at + st * step + (swing * step if st % 2 else 0)
            note = chord[0] - 12 + (7 if bass == "walking" and st in (8, 12) else 0)
            mix.add(sub(hz(note), step * (6 if bass in ("long", "root") else 2.5), 0.01, 0.15), when, 0.62 if st == 0 else 0.45, 0.5)
        for kind, steps in DRUMS[drums].items():
            for st in steps:
                when = at + st * step + (swing * step if st % 2 else 0)
                if kind == "kick":
                    mix.add(kick(0.9), when, 0.8, 0.5)
                elif kind == "clap":
                    mix.add(clap(bar * 16 + st), when, 0.38, 0.5)
                elif kind == "hat":
                    mix.add(hat(bar * 16 + st), when, 0.07 + 0.05 * (st % 4 == 2), 0.3 + 0.4 * ((st // 2) % 2))
                elif kind == "wood":
                    mix.add(woodblock(), when, 0.45, 0.65)
                elif kind == "shaker":
                    mix.add(shaker(bar * 16 + st), when, 0.1, 0.4)
        pattern = ARPS[arp]
        if pattern and density > 0:
            gap = 2 if density == 1 else 1
            for k in range(0, 16, gap):
                when = at + k * step + (swing * step if k % 2 else 0)
                tone = chord[pattern[(k // gap) % len(pattern)] % len(chord)] + 12 * (1 + (int(rng.integers(0, 2)) if density == 3 else 0))
                mix.add(LEADS[lead](hz(tone)), when, 0.20 if k % 4 else 0.28, 0.2 + 0.6 * ((k % 8) / 7))
        if bar % 4 == 3:
            mix.add(riser(2 * beat, 2200), at + 2 * beat, 0.25, 0.5)
    mix.echo(beat * 0.75, float(g("echo", 0.3)), 0.3)
    mix.room(float(g("room", 1.2)), 0.14)
    return mix.done(0.2, 2.5)


TRACKS = {"teaser": teaser, "trailer": trailer, "pastel": pastel, "street": street, "play": play, "code": code, "gallery": gallery}


def main():
    """python tools\\make_music.py <folder> [track ...]       the seven tracks of H-sib Workshop (teaser trailer pastel street play code gallery)
       python tools\\make_music.py <folder> --gen <name> key=D mode=dorian bpm=96 seconds=80 prog=1,4,6,5 drums=breakbeat bass=syncopated lead=marimba arp=updown pad=on density=2 swing=0.12 seed=7
         key: C to B with # or b       mode: major minor dorian mixolydian lydian phrygian harmonic pentatonic       drums: none fourfloor halftime breakbeat shuffle boomclap dub trap latin brush
         bass: root pulse syncopated long walking offbeat       lead: pluck marimba epiano bell square saw harp       arp: up updown skip none       density 1 to 3       swing 0 to 0.3       seed any number (another seed, another variation)"""
    folder = pathlib.Path(sys.argv[1])
    folder.mkdir(parents=True, exist_ok=True)
    if len(sys.argv) > 3 and sys.argv[2] == "--gen":
        name = sys.argv[3]
        params = dict(item.split("=", 1) for item in sys.argv[4:] if "=" in item)
        tables = (("mode", MODES), ("drums", DRUMS), ("bass", BASS), ("lead", LEADS), ("arp", ARPS), ("key", KEYS))
        bad = [k for k, table in tables if k in params and params[k] not in table]
        if bad:
            sys.exit("unknown value for: %s (python tools\\make_music.py shows the allowed ones)" % ", ".join(bad))
        save(folder / (name + ".wav"), generated(params))
        return
    for name in sys.argv[2:] or list(TRACKS):
        save(folder / (name + ".wav"), TRACKS[name]())


if __name__ == "__main__":
    main()
