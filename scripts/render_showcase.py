import math
import os
import shutil
import subprocess
import tempfile
import wave

import cv2
import imageio_ffmpeg
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

INPUT_VIDEO = r"C:\Users\user\Music\screen-20261124-230344-1795550601690.mp4"
PROJECT_DIR = r"c:\Users\user\.gemini\antigravity\scratch\tgiosdesign"
MEDIA_DIR = os.path.join(PROJECT_DIR, "Media")
OUT_VIDEO = os.path.join(MEDIA_DIR, "vault-showcase.mp4")
OUT_PREVIEW = os.path.join(MEDIA_DIR, "preview.jpg")
MUSIC_COPY = r"C:\Users\user\Music\Telegram_Vault_Showcase.mp4"

OUT_SIZE = 1080
OUT_FPS = 60
SUP_SCALE = 1.30
SUP_SIZE = int(round(OUT_SIZE * SUP_SCALE))

# Trim tiny start/end idle tails of screen-20261124-230344-1795550601690.mp4
TRIM_START = 0.2
TRIM_END = 22.2

# Camera keyframes relative to trimmed timeline (0.0 .. ~22.0s)
CAMERA_KEYFRAMES = [
    (0.00, 0.97, 0.0, 10.0),    # Hero reveal on Telegram 13.3 main screen
    (0.55, 1.00, 0.0, 0.0),
    (1.40, 1.00, 0.0, 0.0),
    (1.90, 1.09, 0.0, 44.0),    # Selection Mode + green avatar badge
    (3.30, 1.09, 0.0, 44.0),
    (4.10, 1.15, -20.0, 82.0),  # Zoom onto ⋮ overflow menu ("Hide in Vault")
    (5.70, 1.15, -20.0, 82.0),
    (6.30, 1.13, 0.0, 76.0),    # Pull down for archive -> Release -> Archived Chats
    (9.60, 1.13, 0.0, 76.0),
    (10.40, 1.18, 0.0, -88.0),  # Glide down to Pixel "Unlock Vault" biometric sheet
    (13.60, 1.18, 0.0, -88.0),
    (14.15, 1.02, 0.0, 12.0),   # Pull back to reveal unlocked Vault chat list
    (16.30, 1.02, 0.0, 12.0),
    (16.85, 1.14, -18.0, 80.0), # Zoom onto "Remove from Vault" inside Vault
    (18.55, 1.14, -18.0, 80.0),
    (19.30, 1.00, 0.0, 0.0),    # Lock & clean return to Telegram main chats
]


def smoothstep(t: float) -> float:
    t = max(0.0, min(1.0, t))
    return t * t * (3.0 - 2.0 * t)


def eval_camera(t: float, total_dur: float) -> tuple[float, float, float]:
    kfs = list(CAMERA_KEYFRAMES)
    if total_dur > kfs[-1][0]:
        kfs.append((total_dur, 1.00, 0.0, 0.0))

    if t <= kfs[0][0]:
        scale, px, py = kfs[0][1], kfs[0][2], kfs[0][3]
    else:
        scale, px, py = kfs[-1][1], kfs[-1][2], kfs[-1][3]
        for i in range(len(kfs) - 1):
            t0, s0, x0, y0 = kfs[i]
            t1, s1, x1, y1 = kfs[i + 1]
            if t0 <= t <= t1:
                dt = max(1e-5, t1 - t0)
                f = smoothstep((t - t0) / dt)
                scale = s0 + (s1 - s0) * f
                px = x0 + (x1 - x0) * f
                py = y0 + (y1 - y0) * f
                break

    breath_y = math.sin(t * 1.15) * 2.5
    breath_x = math.cos(t * 0.75) * 1.5
    return scale, px + breath_x, py + breath_y


def vault_tint_factor(t: float) -> float:
    if t < 9.8:
        return 0.0
    if t < 10.7:
        return smoothstep((t - 9.8) / 0.9)
    if t <= 19.2:
        return 1.0
    if t < 20.1:
        return 1.0 - smoothstep((t - 19.2) / 0.9)
    return 0.0


def load_status_font(size_px: int):
    for path in (
        r"C:\Windows\Fonts\seguisb.ttf",
        r"C:\Windows\Fonts\arialbd.ttf",
        r"C:\Windows\Fonts\segoeui.ttf",
    ):
        if os.path.exists(path):
            return ImageFont.truetype(path, size_px)
    return ImageFont.load_default()


def build_status_bar_overlay(sw: int, sh: int):
    """Creates crisp 9:41 + 3-arc Wi-Fi + rounded Battery foreground alpha mask (4x supersampled)."""
    bar_h = int(round(sh * 0.044))
    ss = 4
    w4, h4 = sw * ss, bar_h * ss
    fg4 = Image.new("L", (w4, h4), 0)
    d = ImageDraw.Draw(fg4)

    font = load_status_font(int(round(h4 * 0.44)))
    d.text((int(w4 * 0.085), int(h4 * 0.27)), "9:41", fill=255, font=font)

    # Authentic 3-arc Wi-Fi icon on right (4x supersampled for razor-sharp strokes)
    wx = int(w4 * 0.818)
    wy = int(h4 * 0.70)
    stroke_w = max(2, int(round(h4 * 0.058)))
    dot_r = max(2, int(round(h4 * 0.042)))
    d.ellipse([wx - dot_r, wy - dot_r, wx + dot_r, wy + dot_r], fill=255)
    for mult in (0.145, 0.245, 0.345):
        r = int(round(h4 * mult))
        d.arc(
            [wx - r, wy - r, wx + r, wy + r],
            start=225,
            end=315,
            fill=255,
            width=stroke_w,
        )

    # Crisp rounded Battery pill + terminal nub on far right
    bx0 = int(w4 * 0.865)
    by0 = int(h4 * 0.36)
    bw = int(w4 * 0.056)
    bh = int(h4 * 0.31)
    d.rounded_rectangle([bx0, by0, bx0 + bw, by0 + bh], radius=max(4, bh // 4), fill=255)
    nub_w = max(3, int(round(bw * 0.11)))
    nub_h = int(round(bh * 0.46))
    nub_y0 = by0 + (bh - nub_h) // 2
    d.rounded_rectangle(
        [bx0 + bw + max(2, ss // 2), nub_y0, bx0 + bw + max(2, ss // 2) + nub_w, nub_y0 + nub_h],
        radius=max(2, nub_w // 2),
        fill=255,
    )

    fg = fg4.resize((sw, bar_h), Image.Resampling.LANCZOS)
    return bar_h, (np.array(fg, dtype=np.float32)[:, :, None] / 255.0)


def build_phone_assets(screen_w: int, screen_h: int):
    """Builds photorealistic 3D-shaded Pixel 9 hardware frame via exact Rounded-Rect SDF."""
    aa = 2
    canvas_sz = SUP_SIZE * aa
    cx = canvas_sz * 0.5
    cy = canvas_sz * 0.5

    sw = screen_w * aa
    sh = screen_h * aa
    hx = sw * 0.5
    hy = sh * 0.5
    sx0 = int(round(cx - hx))
    sy0 = int(round(cy - hy))
    sx1 = sx0 + sw
    sy1 = sy0 + sh

    screen_r = 46.0 * SUP_SCALE * aa
    bezel_w = 11.5 * SUP_SCALE * aa
    rim_w = 18.5 * SUP_SCALE * aa  # ~7px cylindrical aluminum rail outside black glass bezel

    # 1. Multi-layer studio drop shadow (with screen aperture punched out later)
    rim_r_int = int(round(screen_r + rim_w))
    rim_pad_int = int(round(rim_w))
    shadow = Image.new("RGBA", (canvas_sz, canvas_sz), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        [
            sx0 - rim_pad_int,
            sy0 - rim_pad_int + int(22 * SUP_SCALE * aa),
            sx1 + rim_pad_int,
            sy1 + rim_pad_int + int(22 * SUP_SCALE * aa),
        ],
        radius=rim_r_int,
        fill=(8, 16, 36, 125),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=int(26 * SUP_SCALE * aa)))

    sd2 = Image.new("RGBA", (canvas_sz, canvas_sz), (0, 0, 0, 0))
    ImageDraw.Draw(sd2).rounded_rectangle(
        [
            sx0 - rim_pad_int,
            sy0 - rim_pad_int + int(7 * SUP_SCALE * aa),
            sx1 + rim_pad_int,
            sy1 + rim_pad_int + int(7 * SUP_SCALE * aa),
        ],
        radius=rim_r_int,
        fill=(6, 12, 28, 90),
    )
    sd2 = sd2.filter(ImageFilter.GaussianBlur(radius=int(8 * SUP_SCALE * aa)))
    shadow = Image.alpha_composite(shadow, sd2)

    # 2. Vectorized SDF cylindrical Pixel 9 aluminum rail + deep black glass bezel
    yy, xx = np.mgrid[0:canvas_sz, 0:canvas_sz].astype(np.float32)
    rx = xx - cx
    ry = yy - cy
    qx = np.abs(rx) - (hx - screen_r)
    qy = np.abs(ry) - (hy - screen_r)
    d = np.sqrt(np.maximum(qx, 0.0) ** 2 + np.maximum(qy, 0.0) ** 2) + np.minimum(np.maximum(qx, qy), 0.0) - screen_r

    frame_rgba = np.zeros((canvas_sz, canvas_sz, 4), dtype=np.float32)

    # Black glass bezel region (0 <= d < bezel_w)
    in_bezel = (d >= -0.8) & (d < bezel_w + 0.8)
    bezel_alpha = np.clip(d + 0.8, 0.0, 1.5) / 1.5
    # Subtle glass specular reflection near outer edge of black bezel
    bezel_shade = 15.0 + 7.0 * np.exp(-((d - bezel_w * 0.5) ** 2) / max(1.0, (bezel_w * 0.35) ** 2))
    frame_rgba[in_bezel, 0] = bezel_shade[in_bezel]
    frame_rgba[in_bezel, 1] = bezel_shade[in_bezel] + 1.0
    frame_rgba[in_bezel, 2] = bezel_shade[in_bezel] + 2.5
    frame_rgba[in_bezel, 3] = bezel_alpha[in_bezel] * 255.0

    # Cylindrical brushed-aluminum outer rail (bezel_w <= d <= rim_w)
    in_rim = (d >= bezel_w - 0.6) & (d <= rim_w + 1.0)
    t_rim = np.clip((d - bezel_w) / max(1e-4, rim_w - bezel_w), 0.0, 1.0)

    # Cylindrical metallic profile: dark gunmetal outer & inner edges, bright specular ridge at t~0.50
    ridge = np.exp(-((t_rim - 0.48) ** 2) / 0.075)
    lip = np.exp(-((t_rim - 0.14) ** 2) / 0.025)
    # Subtle top-left directional studio lighting across frame perimeter
    norm_len = np.sqrt(rx * rx + ry * ry) + 1e-4
    dir_light = 0.90 + 0.10 * ((-0.55 * rx - 0.83 * ry) / norm_len)
    metal_luma = np.clip((42.0 + 196.0 * ridge + 30.0 * lip) * dir_light, 28.0, 248.0)

    # Outer anti-aliased edge
    rim_outer_a = np.clip((rim_w + 0.8 - d) / 1.4, 0.0, 1.0)
    rim_inner_mix = np.clip((d - (bezel_w - 0.6)) / 1.2, 0.0, 1.0)

    # Top-center Pixel antenna band at 12 o'clock
    ant_half_w = 5.5 * SUP_SCALE * aa
    in_ant = in_rim & (ry < 0.0) & (np.abs(rx) <= ant_half_w)
    seam = np.exp(-((np.abs(rx) - ant_half_w) ** 2) / (1.2 * SUP_SCALE * aa) ** 2)

    rim_r_col = metal_luma * 0.97
    rim_g_col = metal_luma * 0.99
    rim_b_col = np.minimum(255.0, metal_luma * 1.02)

    # Apply antenna matte band
    ant_col = 152.0 + 38.0 * ridge - 32.0 * seam
    rim_r_col = np.where(in_ant, ant_col, rim_r_col)
    rim_g_col = np.where(in_ant, ant_col + 2.0, rim_g_col)
    rim_b_col = np.where(in_ant, ant_col + 6.0, rim_b_col)

    # Blend rim over bezel cleanly at bezel_w boundary
    existing_a = frame_rgba[in_rim, 3] / 255.0
    m = rim_inner_mix[in_rim]
    frame_rgba[in_rim, 0] = frame_rgba[in_rim, 0] * (1.0 - m) + rim_r_col[in_rim] * m
    frame_rgba[in_rim, 1] = frame_rgba[in_rim, 1] * (1.0 - m) + rim_g_col[in_rim] * m
    frame_rgba[in_rim, 2] = frame_rgba[in_rim, 2] * (1.0 - m) + rim_b_col[in_rim] * m
    frame_rgba[in_rim, 3] = np.maximum(existing_a, rim_outer_a[in_rim]) * rim_outer_a[in_rim] * 255.0

    # Side power & volume buttons with metallic cylindrical shading
    frame_img = Image.fromarray(np.clip(frame_rgba, 0, 255).astype(np.uint8), "RGBA")
    btn_layer = Image.new("RGBA", (canvas_sz, canvas_sz), (0, 0, 0, 0))
    bd = ImageDraw.Draw(btn_layer)
    bx0 = sx1 + rim_pad_int - int(1.5 * SUP_SCALE * aa)
    bx1 = sx1 + rim_pad_int + int(4.5 * SUP_SCALE * aa)
    for by_top_frac, by_bot_frac in ((0.22, 0.285), (0.325, 0.435)):
        by0_i = sy0 + int(sh * by_top_frac)
        by1_i = sy0 + int(sh * by_bot_frac)
        bd.rounded_rectangle([bx0, by0_i, bx1, by1_i], radius=int(3 * aa), fill=(168, 174, 184, 255))
        bd.line(
            [(bx0 + int(2 * aa), by0_i + int(3 * aa)), (bx0 + int(2 * aa), by1_i - int(3 * aa))],
            fill=(230, 234, 240, 255),
            width=max(2, int(1.5 * aa)),
        )
    frame_img = Image.alpha_composite(btn_layer, frame_img)

    # Recessed top earpiece speaker slit centered on top black bezel
    fd = ImageDraw.Draw(frame_img)
    slit_half_w = int(68 * SUP_SCALE * aa)
    slit_h = max(3, int(round(3.0 * SUP_SCALE * aa)))
    slit_y0 = sy0 - int(round(bezel_w * 0.72))
    fd.rounded_rectangle(
        [int(cx) - slit_half_w, slit_y0, int(cx) + slit_half_w, slit_y0 + slit_h],
        radius=slit_h // 2,
        fill=(5, 6, 8, 255),
    )
    fd.line(
        [(int(cx) - slit_half_w + 4, slit_y0 + slit_h + 1), (int(cx) + slit_half_w - 4, slit_y0 + slit_h + 1)],
        fill=(48, 52, 60, 190),
        width=max(1, int(aa * 0.75)),
    )

    # Punch out screen aperture from shadow cleanly
    screen_hole = Image.new("L", (canvas_sz, canvas_sz), 255)
    ImageDraw.Draw(screen_hole).rounded_rectangle([sx0, sy0, sx1, sy1], radius=int(round(screen_r)), fill=0)

    # Detailed multi-element Pixel punch-hole front camera
    cam_overlay = Image.new("RGBA", (canvas_sz, canvas_sz), (0, 0, 0, 0))
    cd = ImageDraw.Draw(cam_overlay)
    cam_r = int(round(12.2 * SUP_SCALE * aa))
    cam_y = sy0 + int(round(20.5 * SUP_SCALE * aa))
    cxi = int(round(cx))
    # Outer deep black housing
    cd.ellipse([cxi - cam_r, cam_y - cam_r, cxi + cam_r, cam_y + cam_r], fill=(10, 11, 14, 255))
    # Subtle dark-graphite lens barrel ring
    barrel_r = int(round(cam_r * 0.62))
    cd.ellipse([cxi - barrel_r, cam_y - barrel_r, cxi + barrel_r, cam_y + barrel_r], fill=(28, 32, 38, 255))
    # Optical glass pupil
    pupil_r = int(round(cam_r * 0.36))
    cd.ellipse([cxi - pupil_r, cam_y - pupil_r, cxi + pupil_r, cam_y + pupil_r], fill=(14, 22, 36, 255))
    # Crisp dual optical reflection highlights
    g1 = max(2, int(round(1.9 * SUP_SCALE * aa)))
    cd.ellipse(
        [cxi - int(cam_r * 0.16) - g1, cam_y - int(cam_r * 0.18) - g1, cxi - int(cam_r * 0.16) + g1, cam_y - int(cam_r * 0.18) + g1],
        fill=(130, 162, 205, 200),
    )
    g2 = max(1, int(round(1.1 * SUP_SCALE * aa)))
    cd.ellipse(
        [cxi + int(cam_r * 0.14) - g2, cam_y + int(cam_r * 0.14) - g2, cxi + int(cam_r * 0.14) + g2, cam_y + int(cam_r * 0.14) + g2],
        fill=(85, 115, 165, 150),
    )

    phone_chrome = Image.alpha_composite(shadow, frame_img)
    chrome_a = phone_chrome.getchannel("A")
    phone_chrome.putalpha(Image.fromarray(np.minimum(np.array(chrome_a), np.array(screen_hole))))
    phone_chrome = Image.alpha_composite(phone_chrome, cam_overlay)
    phone_chrome = phone_chrome.resize((SUP_SIZE, SUP_SIZE), Image.Resampling.LANCZOS)

    scr_mask_2x = Image.new("L", (canvas_sz, canvas_sz), 0)
    ImageDraw.Draw(scr_mask_2x).rounded_rectangle([sx0, sy0, sx1, sy1], radius=int(round(screen_r)), fill=255)
    scr_mask = scr_mask_2x.resize((SUP_SIZE, SUP_SIZE), Image.Resampling.LANCZOS)

    sup_sx0 = (SUP_SIZE - screen_w) // 2
    sup_sy0 = (SUP_SIZE - screen_h) // 2
    return np.array(phone_chrome), np.array(scr_mask), (sup_sx0, sup_sy0, screen_w, screen_h)


class PixelStudioBackdrop:
    """Mint-teal to periwinkle-blue gradient backdrop matching the Pixel presentation reference."""

    def __init__(self, grid_size: int = 180):
        y, x = np.mgrid[0:grid_size, 0:grid_size].astype(np.float32)
        self.u = x / (grid_size - 1)
        self.v = y / (grid_size - 1)
        self.nx = self.u * 2.0 - 1.0
        self.ny = self.v * 2.0 - 1.0

    def render(self, t_sec: float, vault_tint: float) -> np.ndarray:
        # Left mint-teal (#79CFC0) -> Right periwinkle-blue (#6A84DD)
        left_day = np.array([121.0, 207.0, 192.0], dtype=np.float32)
        right_day = np.array([106.0, 132.0, 221.0], dtype=np.float32)
        # Vault mood: deeper teal-slate (#5888A8) -> rich indigo (#4B58B8)
        left_vault = np.array([76.0, 132.0, 178.0], dtype=np.float32)
        right_vault = np.array([68.0, 78.0, 176.0], dtype=np.float32)

        left_col = left_day * (1.0 - vault_tint) + left_vault * vault_tint
        right_col = right_day * (1.0 - vault_tint) + right_vault * vault_tint

        diag = np.clip(self.u * 0.78 + self.v * 0.22, 0.0, 1.0)[..., None]
        base = left_col[None, None, :] * (1.0 - diag) + right_col[None, None, :] * diag

        # Soft drifting highlight orb for subtle depth
        ox = -0.35 + 0.22 * math.sin(t_sec * 0.45)
        oy = -0.25 + 0.18 * math.cos(t_sec * 0.35)
        orb = np.exp(-((self.nx - ox) ** 2 + (self.ny - oy) ** 2) * 1.4)[..., None]
        base = base + np.array([18.0, 24.0, 22.0], dtype=np.float32)[None, None, :] * orb

        return cv2.resize(
            np.clip(base, 0, 255).astype(np.uint8),
            (OUT_SIZE, OUT_SIZE),
            interpolation=cv2.INTER_CUBIC,
        ).astype(np.float32)


def synthesize_studio_audio(wav_path: str, duration_sec: float):
    sr = 48000
    n = int(duration_sec * sr)
    t_all = np.linspace(0.0, duration_sec, n, endpoint=False, dtype=np.float32)

    pad_freqs = [130.81, 196.00, 246.94, 293.66, 329.63]
    pad_l = np.zeros(n, dtype=np.float32)
    pad_r = np.zeros(n, dtype=np.float32)
    for idx, f in enumerate(pad_freqs):
        detune = 0.45 * (1 if idx % 2 == 0 else -1)
        pad_l += np.sin(2.0 * math.pi * (f - detune) * t_all)
        pad_r += np.sin(2.0 * math.pi * (f + detune) * t_all)
    pad_env = np.minimum(1.0, t_all / 1.0) * np.minimum(1.0, np.maximum(0.0, duration_sec - t_all) / 1.0)
    left = (pad_l / len(pad_freqs)) * 0.042 * pad_env
    right = (pad_r / len(pad_freqs)) * 0.042 * pad_env

    def add_sfx(start_s: float, f0: float, f1: float, dur_s: float, amp: float, pan: float = 0.0):
        i0 = int(start_s * sr)
        m = int(dur_s * sr)
        if i0 >= n or m <= 0:
            return
        m = min(m, n - i0)
        t = np.linspace(0.0, dur_s, m, endpoint=False, dtype=np.float32)
        freq = f0 + (f1 - f0) * (t / dur_s)
        phase = 2.0 * math.pi * np.cumsum(freq) / sr
        env = np.sin(math.pi * np.minimum(1.0, t / 0.007)) * np.exp(-t * 24.0)
        sig = amp * env * (np.sin(phase) + 0.25 * np.sin(2.0 * phase))
        gl = math.sqrt(max(0.0, 0.5 - 0.5 * pan))
        gr = math.sqrt(max(0.0, 0.5 + 0.5 * pan))
        left[i0 : i0 + m] += sig * gl
        right[i0 : i0 + m] += sig * gr

    # Synced to trimmed timeline (TRIM_START = 0.2s)
    add_sfx(1.75, 420.0, 560.0, 0.065, 0.12, pan=-0.10)  # Select chat
    add_sfx(4.35, 480.0, 660.0, 0.070, 0.13, pan=0.18)   # Open ⋮ popup menu
    add_sfx(5.60, 640.0, 420.0, 0.090, 0.15, pan=0.10)   # Hide in Vault
    add_sfx(7.55, 360.0, 540.0, 0.100, 0.14, pan=0.0)    # Release for archive
    add_sfx(10.50, 320.0, 520.0, 0.110, 0.15, pan=0.0)   # Hold Archived Chats -> Biometric
    add_sfx(12.25, 587.33, 587.33, 0.14, 0.16, pan=-0.12)# Fingerprint tap
    add_sfx(12.35, 880.00, 880.00, 0.26, 0.19, pan=0.12) # Checkmark chime
    add_sfx(12.45, 1174.66, 1174.66, 0.28, 0.11, pan=0.0)# Shimmer
    add_sfx(17.20, 460.0, 620.0, 0.075, 0.13, pan=0.15)  # ⋮ -> Remove from Vault
    add_sfx(18.55, 540.0, 720.0, 0.085, 0.14, pan=0.0)   # Restored to main
    add_sfx(19.50, 520.0, 330.0, 0.105, 0.15, pan=0.0)   # Lock Vault

    stereo = np.stack([left, right], axis=1)
    pcm = np.clip(stereo * 32767.0, -32768, 32767).astype(np.int16)
    with wave.open(wav_path, "wb") as wf:
        wf.setnchannels(2)
        wf.setsampwidth(2)
        wf.setframerate(sr)
        wf.writeframes(pcm.tobytes())


def main():
    os.makedirs(MEDIA_DIR, exist_ok=True)
    cap = cv2.VideoCapture(INPUT_VIDEO)
    if not cap.isOpened():
        raise RuntimeError(f"Cannot open {INPUT_VIDEO}")

    in_w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    in_h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    frames_bgr = []
    timestamps_ms = []
    while True:
        ok, frame = cap.read()
        if not ok:
            break
        timestamps_ms.append(cap.get(cv2.CAP_PROP_POS_MSEC))
        frames_bgr.append(frame)
    cap.release()

    if not frames_bgr:
        raise RuntimeError("No frames decoded from input video")
    if timestamps_ms[-1] <= 0.0:
        timestamps_ms = [i * (1000.0 / 30.0) for i in range(len(frames_bgr))]

    raw_dur = timestamps_ms[-1] / 1000.0
    trim_start = min(TRIM_START, max(0.0, raw_dur - 2.0))
    trim_end = min(TRIM_END, raw_dur)
    total_dur = max(1.0, trim_end - trim_start)
    total_out_frames = int(round(total_dur * OUT_FPS))

    sup_scr_h = int(round(884 * SUP_SCALE))
    sup_scr_w = int(round(sup_scr_h * (in_w / in_h)))
    sup_scr_w -= sup_scr_w % 2

    phone_chrome_rgba, scr_mask_u8, (sx0, sy0, sw, sh) = build_phone_assets(sup_scr_w, sup_scr_h)
    chrome_rgb = phone_chrome_rgba[:, :, :3].astype(np.float32)
    chrome_alpha = (phone_chrome_rgba[:, :, 3:4].astype(np.float32)) / 255.0
    chrome_premult = chrome_rgb * chrome_alpha
    inv_chrome_alpha = 1.0 - chrome_alpha
    scr_mask_f = (scr_mask_u8[sy0 : sy0 + sh, sx0 : sx0 + sw, None].astype(np.float32)) / 255.0

    bar_h, status_fg_alpha = build_status_bar_overlay(sw, sh)
    status_fg_dark = np.array([20.0, 23.0, 26.0], dtype=np.float32)[None, None, :]

    backdrop = PixelStudioBackdrop(grid_size=180)
    rng = np.random.default_rng(7)
    micro_grain = rng.uniform(-1.3, 1.3, (OUT_SIZE, OUT_SIZE, 1)).astype(np.float32)

    ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()
    tmp_dir = tempfile.mkdtemp(prefix="tg_pixel_showcase_")
    raw_mp4 = os.path.join(tmp_dir, "video_only.mp4")
    wav_path = os.path.join(tmp_dir, "studio_audio.wav")

    try:
        synthesize_studio_audio(wav_path, total_dur)

        fourcc = cv2.VideoWriter_fourcc(*"mp4v")
        writer = cv2.VideoWriter(raw_mp4, fourcc, OUT_FPS, (OUT_SIZE, OUT_SIZE))

        ts_arr = np.array(timestamps_ms, dtype=np.float64)
        preview_captures = {}

        comp_rgba = np.zeros((SUP_SIZE, SUP_SIZE, 4), dtype=np.uint8)
        comp_rgba[:, :, :3] = np.clip(chrome_premult, 0, 255).astype(np.uint8)
        comp_rgba[:, :, 3] = np.clip(chrome_alpha[:, :, 0] * 255.0, 0, 255).astype(np.uint8)
        screen_inv_chrome = inv_chrome_alpha[sy0 : sy0 + sh, sx0 : sx0 + sw]
        screen_chrome_premult = chrome_premult[sy0 : sy0 + sh, sx0 : sx0 + sw]
        screen_chrome_a = chrome_alpha[sy0 : sy0 + sh, sx0 : sx0 + sw]
        comp_rgba[sy0 : sy0 + sh, sx0 : sx0 + sw, 3] = np.clip(
            (screen_chrome_a[:, :, 0] + scr_mask_f[:, :, 0] * screen_inv_chrome[:, :, 0]) * 255.0,
            0,
            255,
        ).astype(np.uint8)

        for idx in range(total_out_frames):
            t_sec = idx / OUT_FPS
            t_ms = (trim_start + t_sec) * 1000.0
            src_idx = int(np.searchsorted(ts_arr, t_ms, side="right")) - 1
            src_idx = max(0, min(len(frames_bgr) - 1, src_idx))
            src_rgb = cv2.cvtColor(frames_bgr[src_idx], cv2.COLOR_BGR2RGB)

            vt = vault_tint_factor(t_sec)
            bg = backdrop.render(t_sec, vt) + micro_grain

            scr_resized = cv2.resize(src_rgb, (sw, sh), interpolation=cv2.INTER_AREA).astype(np.float32)

            # Replace original cluttered status bar with crisp Pixel "9:41" + Wi-Fi + Battery
            # Sample clean background pixel from bottom-left of top status strip to match dimming/Vault header
            sample_y = max(2, int(bar_h * 0.72))
            sample_x = max(2, int(sw * 0.04))
            strip_bg = scr_resized[sample_y, sample_x, :]
            scr_resized[:bar_h, :, :] = strip_bg[None, None, :]
            lum = float(strip_bg[0] * 0.299 + strip_bg[1] * 0.587 + strip_bg[2] * 0.114)
            fg_col = status_fg_dark if lum > 110 else np.array([240.0, 244.0, 248.0], dtype=np.float32)[None, None, :]
            scr_resized[:bar_h, :, :] = scr_resized[:bar_h, :, :] * (1.0 - status_fg_alpha) + fg_col * status_fg_alpha

            scr_blended = screen_chrome_premult + (scr_resized * scr_mask_f) * screen_inv_chrome
            comp_rgba[sy0 : sy0 + sh, sx0 : sx0 + sw, :3] = np.clip(scr_blended, 0, 255).astype(np.uint8)

            cam_scale, pan_x, pan_y = eval_camera(t_sec, total_dur)
            eff_scale = cam_scale / SUP_SCALE
            sup_c = SUP_SIZE * 0.5
            out_c = OUT_SIZE * 0.5
            tx = (out_c + pan_x) - sup_c * eff_scale
            ty = (out_c + pan_y) - sup_c * eff_scale
            mat = np.array([[eff_scale, 0.0, tx], [0.0, eff_scale, ty]], dtype=np.float32)

            warped = cv2.warpAffine(
                comp_rgba,
                mat,
                (OUT_SIZE, OUT_SIZE),
                flags=cv2.INTER_AREA if eff_scale < 1.0 else cv2.INTER_LINEAR,
                borderMode=cv2.BORDER_CONSTANT,
                borderValue=(0, 0, 0, 0),
            )

            w_rgb = warped[:, :, :3].astype(np.float32)
            w_a = warped[:, :, 3:4].astype(np.float32) / 255.0
            final_rgb = np.clip(w_rgb * w_a + bg * (1.0 - w_a), 0, 255).astype(np.uint8)
            writer.write(cv2.cvtColor(final_rgb, cv2.COLOR_RGB2BGR))

            for target_t in (4.8, 12.6, 15.2):
                if target_t not in preview_captures and t_sec >= target_t:
                    preview_captures[target_t] = Image.fromarray(final_rgb)

        writer.release()

        cmd = [
            ffmpeg_exe,
            "-y",
            "-i",
            raw_mp4,
            "-i",
            wav_path,
            "-c:v",
            "libx264",
            "-pix_fmt",
            "yuv420p",
            "-crf",
            "16",
            "-preset",
            "medium",
            "-c:a",
            "aac",
            "-b:a",
            "256k",
            "-movflags",
            "+faststart",
            "-shortest",
            OUT_VIDEO,
        ]
        subprocess.run(cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        shutil.copy2(OUT_VIDEO, MUSIC_COPY)

        panels = [
            preview_captures.get(4.8),
            preview_captures.get(12.6),
            preview_captures.get(15.2),
        ]
        if all(p is not None for p in panels):
            triptych = Image.new("RGB", (1620, 540))
            for i, p in enumerate(panels):
                triptych.paste(p.resize((540, 540), Image.Resampling.LANCZOS), (i * 540, 0))
            triptych.save(OUT_PREVIEW, quality=95)

        print(f"DONE: {OUT_VIDEO} ({os.path.getsize(OUT_VIDEO)} bytes), duration={total_dur:.2f}s, frames={total_out_frames}")
    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)


if __name__ == "__main__":
    main()
