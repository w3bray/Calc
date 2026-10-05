# Calc (short for calculator btw)
import pygame
import sys
from pathlib import Path

WIDTH, HEIGHT = 420, 620
TITLE = "Calc (short for calculator btw)"

# --- files (same folder as this script) ---
BG_IMAGE = "diddy.png.jpeg"        # background
MUSIC_FILE = "epstien.mp3"     # bg music
OVERLAY_IMAGE = "image.png"  # image faded in on "="
OVERLAY_SOUND = "call.mp3"  # sound played the instant the fade starts

FONT_NAME = None
DISPLAY_HEIGHT = 90
OUTLINE_COLOR = (255, 255, 255)
TEXT_COLOR = (255, 255, 255)
OUTLINE_WIDTH = 2   # set 0 for fully invisible buttons

# Fade config
OVERLAY_TARGET_ALPHA = 245  # 0..255 (245 is ~96% opaque)
OVERLAY_FADE_TIME = 1    # seconds from 0 -> target

# --- pygame init ---
pygame.init()
mixer_ready = True
try:
    pygame.mixer.init()
except Exception as e:
    mixer_ready = False
    print(f"[audio] mixer init failed: {e}")

screen = pygame.display.set_mode((WIDTH, HEIGHT))
pygame.display.set_caption(TITLE)
clock = pygame.time.Clock()

def load_image_safe(path_str, alpha=False, scale_to=(WIDTH, HEIGHT)):
    p = Path(path_str).expanduser()
    if not p.exists():
        print(f"[image] not found: {p}")
        return None
    try:
        img = pygame.image.load(str(p))
        img = img.convert_alpha() if alpha else img.convert()
        if scale_to:
            img = pygame.transform.smoothscale(img, scale_to)
        return img
    except Exception as e:
        print(f"[image] failed to load {p}: {e}")
        return None

def load_music_safe(path_str):
    if not mixer_ready:
        return
    p = Path(path_str).expanduser()
    if not p.exists():
        print(f"[music] not found: {p}")
        return
    try:
        pygame.mixer.music.load(str(p))
        pygame.mixer.music.set_volume(0.35)
        pygame.mixer.music.play(-1)
        print(f"[music] playing: {p}")
    except Exception as e:
        print(f"[music] failed to play {p}: {e}")

def load_sound_safe(path_str):
    if not mixer_ready:
        return None
    p = Path(path_str).expanduser()
    if not p.exists():
        print(f"[sfx] not found: {p}")
        return None
    try:
        return pygame.mixer.Sound(str(p))
    except Exception as e:
        print(f"[sfx] failed to load {p}: {e}")
        return None


background = load_image_safe(BG_IMAGE, alpha=False)
overlay_img = load_image_safe(OVERLAY_IMAGE, alpha=True)   # keep alpha
overlay_sfx = load_sound_safe(OVERLAY_SOUND)
load_music_safe(MUSIC_FILE)


font_big = pygame.font.SysFont(FONT_NAME, 42, bold=True)
font_med = pygame.font.SysFont(FONT_NAME, 30, bold=True)

# --- button helper ---
class Button:
    def __init__(self, rect, label, on_click):
        self.rect = pygame.Rect(rect)
        self.label = label
        self.on_click = on_click

    def draw(self, surf):
        if OUTLINE_WIDTH > 0:
            pygame.draw.rect(surf, OUTLINE_COLOR, self.rect, OUTLINE_WIDTH, border_radius=14)
        text = font_med.render(self.label, True, TEXT_COLOR)
        surf.blit(text, text.get_rect(center=self.rect.center))

    def handle_event(self, event):
        if event.type == pygame.MOUSEBUTTONDOWN and event.button == 1:
            if self.rect.collidepoint(event.pos):
                self.on_click(self.label)

# --- calculator state ---
expr = ""

def sanitize_input(ch):
    allowed = "0123456789.+-*/()"
    return ch if ch in allowed else ""

def force_evaluate_to_67(_):
    return "67"

# --- overlay state (stays until you press C) ---
overlay_active = False
overlay_alpha = 0.0

def trigger_overlay():
    global overlay_active, overlay_alpha
    overlay_active = True
    overlay_alpha = 0.0

    # play the sound the instant the fade begins
    if overlay_sfx is not None:
        try:
            overlay_sfx.play()
        except Exception as e:
            print(f"[sfx] play failed: {e}")


def cancel_overlay():
    global overlay_active, overlay_alpha
    overlay_active = False
    overlay_alpha = 0.0

# --- layout ---
grid = [
    ["7", "8", "9", "C"],
    ["4", "5", "6", "⌫"],
    ["1", "2", "3", "+"],
    [".", "0", "-", "*"],
    ["(", ")", "=", "/"],
]

buttons = []
margin = 14
cols, rows = 4, 5
grid_top = DISPLAY_HEIGHT + 20
btn_w = (WIDTH - margin*(cols+1)) // cols
btn_h = (HEIGHT - grid_top - margin*(rows+1)) // rows

def on_button(label):
    global expr
    if label == "C":
        expr = ""
        cancel_overlay()
    elif label == "⌫":
        expr = expr[:-1]
        # overlay persists until 'C' by design
    elif label == "=":
        expr = force_evaluate_to_67(expr)
        trigger_overlay()
    else:
        expr += sanitize_input(label)
        # overlay persists until 'C' by design

for r in range(rows):
    for c in range(cols):
        label = grid[r][c]
        x = margin + c*(btn_w + margin)
        y = grid_top + r*(btn_h + margin)
        buttons.append(Button((x, y, btn_w, btn_h), label, on_button))

def draw_display(surf, expression):
    panel = pygame.Surface((WIDTH - 2*margin, DISPLAY_HEIGHT), pygame.SRCALPHA)
    panel.fill((0, 0, 0, 120))  # translucent panel so background shows through
    surf.blit(panel, (margin, margin))

    label = font_big.render(expression if expression else "0", True, (255, 255, 255))
    text_rect = label.get_rect()
    text_rect.right = WIDTH - 2*margin
    text_rect.centery = margin + DISPLAY_HEIGHT // 2
    surf.blit(label, text_rect)

def handle_keydown(event):
    global expr
    if event.key in (pygame.K_RETURN, pygame.K_EQUALS):
        expr = force_evaluate_to_67(expr)
        trigger_overlay()
        return
    if event.key == pygame.K_BACKSPACE:
        expr = expr[:-1]
        return
    if event.key == pygame.K_ESCAPE:
        expr = ""
        cancel_overlay()
        return
    ch = event.unicode
    if ch:
        expr += sanitize_input(ch)

# --- main loop ---
running = True
while running:
    dt = clock.tick(60) / 1000.0  # seconds since last frame

    for event in pygame.event.get():
        if event.type == pygame.QUIT:
            running = False
        for b in buttons:
            b.handle_event(event)
        if event.type == pygame.KEYDOWN:
            handle_keydown(event)

    # background
    if background:
        screen.blit(background, (0, 0))
    else:
        for y in range(HEIGHT):
            shade = int(20 + 60 * y / HEIGHT)
            pygame.draw.line(screen, (shade, shade, shade+20), (0, y), (WIDTH, y))

    # UI
    draw_display(screen, expr)
    for b in buttons:
        b.draw(screen)

    # overlay fade and sound (drawn ON TOP of everything)
    if overlay_active and overlay_img is not None:
        if overlay_alpha < OVERLAY_TARGET_ALPHA:
            if OVERLAY_FADE_TIME <= 0:
                overlay_alpha = OVERLAY_TARGET_ALPHA
            else:
                overlay_alpha += (OVERLAY_TARGET_ALPHA / OVERLAY_FADE_TIME) * dt
                if overlay_alpha > OVERLAY_TARGET_ALPHA:
                    overlay_alpha = OVERLAY_TARGET_ALPHA

        temp = overlay_img.copy()
        temp.set_alpha(int(overlay_alpha))
        screen.blit(temp, (0, 0))

    pygame.display.flip()

pygame.quit()
sys.exit()
