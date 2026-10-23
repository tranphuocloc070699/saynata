"""Build the Saynata logo files and app icons from one drawing (TASK-95, doc-7 Design 10-23).

The logo is two cat heads on a teal tile: a light cat with a Latin "S" and a sage cat with the Japanese "さ" (sa).
The letters are turned into outlines here, so the files look the same on every machine.

Fonts (both SIL Open Font License, not in the repo):
  - DM Serif Display Regular, for "S"
  - Noto Serif JP (variable), for "さ"; the script uses its Bold weight

Run from the repo root:
  python3 scripts/logo/build_logo.py --dm-serif ~/Library/Fonts/DMSerifDisplay-Regular.ttf --noto-jp NotoSerifJP[wght].ttf

Needs fontTools (pip install fonttools) and Google Chrome, which draws the PNG files (headless).
"""

import argparse
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = Path(__file__).resolve().parents[2]
LOGO_DIR = ROOT / "docs/design/logo"
ASSETS = ROOT / "EnglishTranslation/EnglishTranslation/Assets.xcassets"
EXTENSION_ICONS = ROOT / "extension/public/icons"
EXTENSION_LOGO_TS = ROOT / "extension/src/content/logo.ts"

# Theme.Colors (Assets.xcassets/Theme): Accent, Background, ChipFill.
ACCENT = "#2F6F62"
BACKGROUND = "#F3F5F1"
CHIP = "#DCEBE4"

CAT_W, CAT_H = 46, 38
FRONT_CAT = (10, 18)  # the light cat with "S"
BACK_CAT = (44, 52)  # the sage cat with "さ", drawn on top, lower right
LETTER_HEIGHT = 21

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"


def glyph_path(font: TTFont, char: str, cx: float, cy: float, height: float) -> str:
    """SVG path data for one character, scaled to `height` and centred on (cx, cy), y pointing down."""
    glyph_set = font.getGlyphSet()
    name = font.getBestCmap()[ord(char)]
    bounds = BoundsPen(glyph_set)
    glyph_set[name].draw(bounds)
    x_min, y_min, x_max, y_max = bounds.bounds
    scale = height / (y_max - y_min)
    dx = cx - (x_min + x_max) / 2 * scale
    dy = cy + (y_min + y_max) / 2 * scale
    pen = SVGPathPen(glyph_set, ntos=lambda v: f"{v:.2f}".rstrip("0").rstrip("."))
    glyph_set[name].draw(TransformPen(pen, (scale, 0, 0, -scale, dx, dy)))
    return pen.getCommands()


def ears(x: float, y: float, inner: bool = False) -> str:
    e = CAT_W * 0.08
    if inner:
        return (f"M{x + e + 4} {y + 3} L{x + e + 5} {y - 5} L{x + e + 11} {y + 2} Z "
                f"M{x + CAT_W - e - 4} {y + 3} L{x + CAT_W - e - 5} {y - 5} L{x + CAT_W - e - 11} {y + 2} Z")
    return (f"M{x + e} {y + 8} L{x + e + 3} {y - 12} L{x + e + CAT_W * 0.32} {y + 2} Z "
            f"M{x + CAT_W - e} {y + 8} L{x + CAT_W - e - 3} {y - 12} L{x + CAT_W - e - CAT_W * 0.32} {y + 2} Z")


def cat(x: float, y: float, fill: str, inner: str, letter_path: str | None, letter_fill: str) -> str:
    parts = [
        f'<path d="{ears(x, y)}" fill="{fill}" stroke="{fill}" stroke-width="4" stroke-linejoin="round"/>',
        f'<path d="{ears(x, y, inner=True)}" fill="{inner}"/>',
        f'<rect x="{x}" y="{y}" width="{CAT_W}" height="{CAT_H}" rx="16" fill="{fill}"/>',
    ]
    if letter_path:
        parts.append(f'<path d="{letter_path}" fill="{letter_fill}"/>')
    return "".join(parts)


def cat_outline(x: float, y: float, colour: str) -> str:
    """A rim around the back cat, in the tile colour, so it stays apart from the front cat where they overlap."""
    return (f'<path d="{ears(x, y)}" fill="{colour}" stroke="{colour}" stroke-width="7" stroke-linejoin="round"/>'
            f'<rect x="{x - 1.5}" y="{y - 1.5}" width="{CAT_W + 3}" height="{CAT_H + 3}" rx="17" fill="{colour}"/>')


def cats(s_path: str | None, sa_path: str | None) -> str:
    return (cat(*FRONT_CAT, BACKGROUND, CHIP, s_path, ACCENT)
            + cat_outline(*BACK_CAT, ACCENT)
            + cat(*BACK_CAT, CHIP, ACCENT, sa_path, ACCENT))


def svg(view_box: str, body: str, size: int | None = None) -> str:
    size_attr = f' width="{size}" height="{size}"' if size else ""
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view_box}"{size_attr}>{body}</svg>\n'


def letters(s_font: TTFont, sa_font: TTFont) -> tuple[str, str]:
    s = glyph_path(s_font, "S", FRONT_CAT[0] + CAT_W / 2, FRONT_CAT[1] + CAT_H / 2 + 1, LETTER_HEIGHT)
    sa = glyph_path(sa_font, "さ", BACK_CAT[0] + CAT_W / 2, BACK_CAT[1] + CAT_H / 2 + 1, LETTER_HEIGHT)
    return s, sa


def build_svgs(s: str, sa: str) -> dict[str, str]:
    # On a tile the cats are made 10% smaller and centred, so the ears stay clear of the iOS corner mask.
    body = f'<g transform="translate(50 50) scale(0.9) translate(-50 -48)">{cats(s, sa)}</g>'
    bare = cats(s, sa)
    # macOS draws no mask: the icon is an 824 pt tile inside a 1024 pt canvas with a soft shadow
    # (https://developer.apple.com/design/human-interface-guidelines/app-icons#macOS).
    mac_shadow = ('<defs><filter id="s" x="-10%" y="-10%" width="120%" height="125%">'
                  '<feDropShadow dx="0" dy="1" stdDeviation="1" flood-color="#1F2A26" flood-opacity="0.28"/>'
                  "</filter></defs>")
    mac = (f'{mac_shadow}<g transform="translate(9.765625 9.765625) scale(0.8046875)">'
           f'<rect width="100" height="100" rx="22.45" fill="{ACCENT}" filter="url(#s)"/>{body}</g>')
    # The menu bar wants a one-colour template image; macOS tints it. No letters: they are noise at 18 pt.
    menubar = ('<defs><mask id="m" maskUnits="userSpaceOnUse" x="0" y="0" width="100" height="100">'
               f'{cat(*FRONT_CAT, "#fff", "#fff", None, "#fff")}{cat_outline(*BACK_CAT, "#000")}'
               f'{cat(*BACK_CAT, "#fff", "#fff", None, "#fff")}</mask></defs>'
               '<rect width="100" height="100" fill="#000" mask="url(#m)"/>')
    return {
        "saynata-logo.svg": svg("0 0 100 100", f'<rect width="100" height="100" rx="22" fill="{ACCENT}"/>{body}'),
        "saynata-icon-ios.svg": svg("0 0 100 100", f'<rect width="100" height="100" fill="{ACCENT}"/>{body}'),
        "saynata-icon-mac.svg": svg("0 0 100 100", mac),
        "saynata-menubar.svg": svg("8 4 84 88", menubar, size=18),
        # The extension's selection button: the cats only, on its own teal circle.
        "saynata-cats.svg": svg("6 4 88 88", bare),
    }


def render(svg_text: str, out: Path, size: int, chrome: str, opaque: bool = False) -> None:
    """Draw an SVG to a size x size PNG with headless Chrome (transparent unless `opaque`)."""
    with tempfile.TemporaryDirectory() as tmp:
        page = Path(tmp) / "page.html"
        sized = re.sub(r'<svg ([^>]*?) width="\d+" height="\d+"', r"<svg \1", svg_text, count=1)
        sized = sized.replace("<svg ", f'<svg width="{size}" height="{size}" style="display:block" ', 1)
        page.write_text(f'<html><body style="margin:0">{sized}</body></html>')
        shot = Path(tmp) / "shot.png"
        subprocess.run(
            [chrome, "--headless=new", "--disable-gpu", "--hide-scrollbars", f"--window-size={size},{size}",
             "--force-device-scale-factor=1", "--default-background-color=00000000",
             f"--screenshot={shot}", page.as_uri()],
            check=True, capture_output=True,
        )
        out.parent.mkdir(parents=True, exist_ok=True)
        if opaque:
            # App Store icons must have no alpha channel.
            subprocess.run(["magick", shot, "-background", ACCENT, "-alpha", "remove", "-alpha", "off", out],
                           check=True)
        else:
            shutil.copy(shot, out)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dm-serif", required=True, type=Path, help="DMSerifDisplay-Regular.ttf")
    parser.add_argument("--noto-jp", required=True, type=Path, help="NotoSerifJP[wght].ttf (variable)")
    parser.add_argument("--chrome", default=CHROME)
    args = parser.parse_args()

    s_font = TTFont(args.dm_serif)
    sa_font = instancer.instantiateVariableFont(TTFont(args.noto_jp), {"wght": 700})
    svgs = build_svgs(*letters(s_font, sa_font))

    LOGO_DIR.mkdir(parents=True, exist_ok=True)
    for name, text in svgs.items():
        (LOGO_DIR / name).write_text(text)

    render(svgs["saynata-icon-ios.svg"], ASSETS / "AppIcon.appiconset/AppIcon.png", 1024, args.chrome, opaque=True)
    for size in (16, 32, 64, 128, 256, 512, 1024):
        render(svgs["saynata-icon-mac.svg"], ASSETS / f"AppIcon.appiconset/mac-{size}.png", size, args.chrome)
    for size in (16, 32, 48, 128):
        render(svgs["saynata-logo.svg"], EXTENSION_ICONS / f"icon-{size}.png", size, args.chrome)
    shutil.copy(LOGO_DIR / "saynata-menubar.svg", ASSETS / "MenuBarIcon.imageset/saynata-menubar.svg")
    cats_svg = svgs["saynata-cats.svg"].strip().replace("<svg ", '<svg aria-hidden="true" ', 1)
    EXTENSION_LOGO_TS.write_text(
        "/** The two-cat logo without its tile, for the selection button. Made by scripts/logo/build_logo.py; "
        "do not edit. */\n"
        f"export const LOGO_CATS = `{cats_svg}`;\n"
    )
    # Google Cloud OAuth consent screen: a square 120 x 120 PNG.
    render(svgs["saynata-icon-ios.svg"], LOGO_DIR / "google-consent-logo-120.png", 120, args.chrome, opaque=True)
    render(svgs["saynata-logo.svg"], LOGO_DIR / "saynata-logo-512.png", 512, args.chrome)


if __name__ == "__main__":
    main()
