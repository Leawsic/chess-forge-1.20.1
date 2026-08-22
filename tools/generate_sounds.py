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


def noise_burst(duration: float, decay: float, lowpass: int = 0) -> np.ndarray:
    """短噪声爆发，用来做出敲击瞬间的「哒」声。"""
    n = int(RATE * duration)
    t = np.arange(n) / RATE
    rng = np.random.default_rng(0xC4E55)
    burst = rng.standard_normal(n) * np.exp(-t / decay)
    if lowpass > 0:
        # 一阶 IIR 低通，避免噪声过于刺耳。
        alpha = 1.0 - np.exp(-2 * np.pi * lowpass / RATE)
        filtered = np.zeros(n)
        acc = 0.0
        for i in range(n):
            acc += alpha * (burst[i] - acc)
            filtered[i] = acc
        return filtered
    return burst


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
    """落子：木质棋子磕在棋盘上的清脆短音。"""
    body = modes(0.16, [1_850, 2_640, 3_910, 5_200], [1.0, 0.55, 0.3, 0.16], [0.028, 0.02, 0.013, 0.008])
    click = noise_burst(0.16, 0.0035, lowpass=6_500) * 0.85
    signal = body + click
    return normalize(signal * envelope(len(signal), 0.0006, 0.05))


def piece_capture() -> np.ndarray:
    """吃子：落子声之后紧跟一记被提子的低沉碰撞。"""
    first = piece_place() * 0.9
    second_body = modes(0.22, [820, 1_180, 1_720, 2_450], [1.0, 0.6, 0.34, 0.18], [0.05, 0.036, 0.024, 0.014])
    second_click = noise_burst(0.22, 0.006, lowpass=3_200) * 0.7
    second = normalize(second_body + second_click) * 0.95
    gap = int(RATE * 0.055)
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
