#!/usr/bin/env python3
"""合成棋类音效并输出为 Minecraft 使用的 .ogg。

生成的音效全部由程序合成，不含任何外部素材，因此无版权顾虑。

用法：
    python tools/generate_sounds.py

依赖：numpy、ffmpeg（需在 PATH 中）。
输出目录：src/main/resources/assets/chess/sounds/
"""

from __future__ import annotations

import shutil
import struct
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np

RATE = 44100
OUT_DIR = Path(__file__).resolve().parent.parent / "src/main/resources/assets/chess/sounds"


def envelope(n: int, attack: float, decay: float, curve: float = 2.5) -> np.ndarray:
    """生成打击乐式包络：极短的攻击段 + 指数衰减。"""
    attack_samples = max(1, int(RATE * attack))
    env = np.exp(-curve * np.linspace(0, 1, n) * (0.05 / max(decay, 1e-4)))
    ramp = np.linspace(0, 1, attack_samples) ** 0.5
    env[:attack_samples] *= ramp
    return env


def modes(duration: float, freqs, gains, decays) -> np.ndarray:
    """叠加多个指数衰减正弦模态，模拟实体被敲击后的共振。"""
    n = int(RATE * duration)
    t = np.arange(n) / RATE
    out = np.zeros(n)
    for freq, gain, decay in zip(freqs, gains, decays):
        out += gain * np.sin(2 * np.pi * freq * t) * np.exp(-t / decay)
    return out


def noise_burst(duration: float, decay: float, lowpass: int = 0, poles: int = 1) -> np.ndarray:
    """短噪声爆发，用来做出敲击瞬间的接触声。

    ``poles`` 为低通级数，级数越高高频衰减越陡。落子这类闷响需要把高频压得很干净，
    单级一阶低通（-6 dB/oct）不够，实测要 3~4 级才不会残留「叮」的金属感。
    """
    n = int(RATE * duration)
    t = np.arange(n) / RATE
    rng = np.random.default_rng(0xC4E55)
    burst = rng.standard_normal(n) * np.exp(-t / decay)
    return lowpass_filter(burst, lowpass, poles) if lowpass > 0 else burst


def lowpass_filter(signal: np.ndarray, cutoff: int, poles: int = 1) -> np.ndarray:
    """串联多级一阶 IIR 低通。"""
    alpha = 1.0 - np.exp(-2 * np.pi * cutoff / RATE)
    out = signal
    for _ in range(max(1, poles)):
        filtered = np.empty_like(out)
        acc = 0.0
        for i in range(len(out)):
            acc += alpha * (out[i] - acc)
            filtered[i] = acc
        out = filtered
    return out



def tone(duration: float, start: float, end: float, gain: float = 1.0, decay: float = 0.35) -> np.ndarray:
    """线性扫频正弦，用于胜负提示音。"""
    n = int(RATE * duration)
    t = np.arange(n) / RATE
    freq = np.linspace(start, end, n)
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    return gain * np.sin(phase) * np.exp(-t / decay)


def normalize(signal: np.ndarray, peak: float = 0.82) -> np.ndarray:
    """归一化并做短促淡出，防止末尾出现爆音。"""
    maximum = np.max(np.abs(signal))
    if maximum > 0:
        signal = signal / maximum * peak
    fade = min(len(signal), int(RATE * 0.01))
    if fade > 0:
        signal[-fade:] *= np.linspace(1, 0, fade)
    return signal


def piece_place() -> np.ndarray:
    """落子：棋子按在厚木棋盘上的低沉闷响。

    重点是让能量集中在 90~470 Hz。此前版本的模态在 1.8~5.2 kHz，
    那个频段正是瓷器和金属的共振区，所以听起来像筷子敲碗。
    厚实的木头体积大、阻尼强，基频低且高频衰减极快。
    """
    duration = 0.26
    # 低频主体：木盘被压实时的整体振动。
    body = modes(
        duration,
        [96, 158, 227, 333, 452],
        [1.0, 0.86, 0.42, 0.2, 0.09],
        [0.085, 0.07, 0.045, 0.026, 0.015],
    )
    # 接触瞬间的摩擦声，截止压到 700 Hz 才不会带出叮声。
    contact = noise_burst(duration, 0.0045, lowpass=700, poles=3) * 1.6
    signal = body + contact
    # 整体再低通一次，确保没有任何高频残留。
    signal = lowpass_filter(signal, 1_500, poles=2)
    return normalize(signal * envelope(len(signal), 0.0015, 0.11))


def piece_capture() -> np.ndarray:
    """吃子：落子闷响之后紧跟一记提子的更低碰撞。"""
    first = piece_place() * 0.85
    second_body = modes(
        0.34,
        [72, 118, 176, 254],
        [1.0, 0.72, 0.34, 0.15],
        [0.12, 0.09, 0.055, 0.03],
    )
    second_contact = noise_burst(0.34, 0.007, lowpass=520, poles=3) * 1.3
    second = normalize(lowpass_filter(second_body + second_contact, 1_100, poles=2))
    gap = int(RATE * 0.075)
    signal = np.zeros(gap + len(second))
    signal[: len(first)] += first
    signal[gap : gap + len(second)] += second
    return normalize(signal)



def game_win() -> np.ndarray:
    """胜利：上行三音 + 泛音铃声。"""
    notes = [(523.25, 0.0), (659.25, 0.11), (783.99, 0.22), (1_046.50, 0.33)]
    total = int(RATE * 0.95)
    signal = np.zeros(total)
    for freq, offset in notes:
        segment = modes(0.62, [freq, freq * 2, freq * 3], [1.0, 0.32, 0.12], [0.2, 0.13, 0.08])
        start = int(RATE * offset)
        end = min(total, start + len(segment))
        signal[start:end] += segment[: end - start]
    return normalize(signal)


def game_lose() -> np.ndarray:
    """失败：下行两音，尾部略带失谐。"""
    signal = np.zeros(int(RATE * 0.9))
    for freq, offset, decay in [(392.00, 0.0, 0.24), (311.13, 0.14, 0.3), (233.08, 0.3, 0.42)]:
        segment = modes(0.6, [freq, freq * 1.99, freq * 2.97], [1.0, 0.26, 0.1], [decay, decay * 0.6, decay * 0.4])
        start = int(RATE * offset)
        end = min(len(signal), start + len(segment))
        signal[start:end] += segment[: end - start]
    return normalize(signal, peak=0.7)


def check_alert() -> np.ndarray:
    """将军：两声短促的高音警示。"""
    signal = np.zeros(int(RATE * 0.42))
    for offset in (0.0, 0.14):
        beep = tone(0.12, 1_320, 1_180, gain=1.0, decay=0.045)
        start = int(RATE * offset)
        end = min(len(signal), start + len(beep))
        signal[start:end] += beep[: end - start]
    return normalize(signal)


def write_wav(path: Path, signal: np.ndarray) -> None:
    data = (np.clip(signal, -1.0, 1.0) * 32767).astype("<i2").tobytes()
    with path.open("wb") as handle:
        handle.write(b"RIFF")
        handle.write(struct.pack("<I", 36 + len(data)))
        handle.write(b"WAVEfmt ")
        handle.write(struct.pack("<IHHIIHH", 16, 1, 1, RATE, RATE * 2, 2, 16))
        handle.write(b"data")
        handle.write(struct.pack("<I", len(data)))
        handle.write(data)


def main() -> int:
    if shutil.which("ffmpeg") is None:
        print("ffmpeg not found in PATH", file=sys.stderr)
        return 1
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    sounds = {
        "piece_place": piece_place(),
        "piece_capture": piece_capture(),
        "game_win": game_win(),
        "game_lose": game_lose(),
        "check_alert": check_alert(),
    }
    with tempfile.TemporaryDirectory() as tmp:
        for name, signal in sounds.items():
            wav = Path(tmp) / f"{name}.wav"
            ogg = OUT_DIR / f"{name}.ogg"
            write_wav(wav, signal)
            subprocess.run(
                ["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav),
                 "-c:a", "libvorbis", "-q:a", "5", "-ar", str(RATE), "-ac", "1", str(ogg)],
                check=True,
            )
            print(f"{ogg.name}  {ogg.stat().st_size / 1024:.1f} KiB  {len(signal) / RATE:.2f}s")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
