#!/usr/bin/env python3
import colorsys
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import sys

try:
    import tomllib
except ModuleNotFoundError:
    print('Python 3.11+ is required (missing tomllib)', file=sys.stderr)
    raise SystemExit(1)

THEME_DIR = Path.home() / '.local/state/omarchy/current/theme'
DEFAULT_NAME = 'Omarchy'
OUTPUT_DIR = Path.home() / '.config/omarchy-intellij'
MANIFEST_PATH = OUTPUT_DIR / 'manifest.json'
THEME_JSON_PATH = OUTPUT_DIR / 'theme.json'
SCHEME_XML_PATH = OUTPUT_DIR / 'omarchy.xml'
REFRESH_TOKEN_PATH = OUTPUT_DIR / 'refresh.token'
REQUIRED = [
    'background',
    'foreground',
    'accent',
    'cursor',
    'selection_background',
    'selection_foreground',
]


def hex_to_rgb(value: str) -> tuple[int, int, int]:
    value = value.strip().lstrip('#')
    if len(value) != 6:
        raise ValueError(f'Expected #RRGGBB, got {value!r}')
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def rgb_to_hex(rgb: tuple[int, int, int]) -> str:
    return '#' + ''.join(f'{max(0, min(255, c)):02x}' for c in rgb)


def xml_hex(value: str) -> str:
    return value.lstrip('#').upper()


def mix(a: str, b: str, ratio: float) -> str:
    ar, ag, ab = hex_to_rgb(a)
    br, bg, bb = hex_to_rgb(b)
    rgb = (
        round(ar + (br - ar) * ratio),
        round(ag + (bg - ag) * ratio),
        round(ab + (bb - ab) * ratio),
    )
    return rgb_to_hex(rgb)


def shift_lightness(color: str, amount: float) -> str:
    r, g, b = [c / 255 for c in hex_to_rgb(color)]
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    l = max(0.0, min(1.0, l + amount))
    rgb = colorsys.hls_to_rgb(h, l, s)
    return rgb_to_hex(tuple(round(c * 255) for c in rgb))


def luminance(color: str) -> float:
    r, g, b = [c / 255 for c in hex_to_rgb(color)]

    def channel(v: float) -> float:
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4

    r, g, b = channel(r), channel(g), channel(b)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def is_dark(color: str) -> bool:
    return luminance(color) < 0.4


def sanitize_name(value: str) -> str:
    return re.sub(r'\s+', ' ', value).strip() or DEFAULT_NAME


def display_theme_name() -> str:
    theme_name_file = Path.home() / '.config/omarchy/current/theme.name'
    if len(sys.argv) > 1 and sys.argv[1].strip():
        raw = sys.argv[1].strip()
    elif theme_name_file.exists():
        raw = theme_name_file.read_text(encoding='utf-8').strip()
    else:
        raw = DEFAULT_NAME

    raw = raw.replace('-', ' ').replace('_', ' ').title()
    return f'Omarchy {sanitize_name(raw)}'


def current_theme_dir() -> Path:
    return THEME_DIR


def load_palette(colors_file: Path) -> dict[str, str]:
    with colors_file.open('rb') as f:
        data = tomllib.load(f)
    palette = {k: v for k, v in data.items() if isinstance(v, str) and v.startswith('#')}

    palette['cursor'] = palette['bright_foreground']
    palette['selection_background'] = palette['selection']
    palette['selection_foreground'] = palette['bright_foreground']

    ansi_aliases = {
        'color1': 'red',
        'color2': 'green',
        'color3': 'yellow',
        'color4': 'blue',
        'color5': 'magenta',
        'color6': 'cyan',
        'color9': 'bright_red',
        'color10': 'bright_green',
        'color11': 'bright_yellow',
        'color12': 'bright_blue',
        'color13': 'bright_magenta',
        'color14': 'bright_cyan',
    }
    for ansi, semantic in ansi_aliases.items():
        if ansi not in palette and semantic in palette:
            palette[ansi] = palette[semantic]

    missing = [key for key in REQUIRED if key not in palette]
    if missing:
        raise SystemExit(f'Missing required colors in {colors_file}: {", ".join(missing)}')
    return palette


def build_theme_json(name: str, palette: dict[str, str]) -> dict:
    bg = palette['background']
    fg = palette['foreground']
    accent = palette['accent']
    cursor = palette['cursor']
    sel_bg = palette['selection_background']
    sel_fg = palette['selection_foreground']

    dark = is_dark(bg)
    surface0 = shift_lightness(bg, 0.03 if dark else -0.03)
    surface1 = shift_lightness(bg, 0.06 if dark else -0.06)
    border = mix(bg, fg, 0.18)
    border_subtle = mix(bg, fg, 0.28)
    disabled = mix(fg, bg, 0.45)
    button = mix(bg, accent, 0.20)
    button_hover = mix(bg, accent, 0.30)
    inactive_selection = mix(sel_bg, bg, 0.45)

    colors = {
        'background': bg,
        'backgroundElevated': surface0,
        'backgroundSelected': sel_bg,
        'backgroundHover': surface1,
        'backgroundFrame': surface0,
        'foreground': fg,
        'foregroundBright': shift_lightness(fg, 0.08 if dark else -0.08),
        'foregroundDim': disabled,
        'accent': accent,
        'accentSecondary': palette.get('color4', accent),
        'border': border,
        'borderSubtle': border_subtle,
        'selection': sel_bg,
        'selectionInactive': inactive_selection,
        'red': palette.get('color1', accent),
        'orange': palette.get('color9', palette.get('color3', accent)),
        'yellow': palette.get('color3', accent),
        'green': palette.get('color2', accent),
        'blue': palette.get('color4', accent),
        'purple': palette.get('color5', accent),
        'teal': palette.get('color6', accent),
        'caret': cursor,
        'selectionForeground': sel_fg,
    }

    return {
        'name': name,
        'dark': dark,
        'author': 'Generated from Omarchy colors',
        'parentTheme': 'ExperimentalDark' if dark else 'ExperimentalLight',
        'colors': colors,
        'ui': {
            'Islands': 1,
            'Island.arc': 20,
            'Island.arc.compact': 16,
            'Island.borderColor': 'border',
            'Island.borderWidth': 4,
            'Island.borderWidth.compact': 3,
            'Island.inactiveAlpha': 0.55,
            '*': {
                'background': 'background',
                'foreground': 'foreground',
                'selectionBackground': 'selection',
                'selectionForeground': 'selectionForeground',
                'selectionInactiveBackground': 'selectionInactive',
                'inactiveBackground': 'background',
                'disabledBackground': 'background',
                'disabledForeground': 'foregroundDim',
                'disabledText': 'foregroundDim',
                'borderColor': 'border',
                'separatorColor': 'border',
                'focusColor': 'accent',
                'focusedBorderColor': 'accent',
                'selectedForeground': 'selectionForeground',
                'selectedBackground': 'selection',
                'hoverBackground': 'backgroundHover',
                'pressedBackground': 'backgroundSelected',
                'acceleratorForeground': 'foregroundDim',
                'errorForeground': 'red',
                'warningForeground': 'orange',
                'infoForeground': 'accent',
            },
            'MainWindow': {
                'background': 'backgroundFrame',
                'Border.color': 'border',
            },
            'Window': {
                'background': 'backgroundFrame',
            },
            'MainToolbar': {
                'background': 'backgroundFrame',
                'borderColor': '#00000000',
                'Dropdown.hoverBackground': 'backgroundHover',
                'Dropdown.pressBackground': 'backgroundSelected',
                'Icon.hoverBackground': 'backgroundHover',
                'Icon.pressBackground': 'backgroundSelected',
            },
            'TitlePane': {
                'background': 'backgroundFrame',
                'inactiveBackground': 'backgroundFrame',
                'foreground': 'foreground',
                'inactiveForeground': 'foregroundDim',
                'Button.hoverBackground': 'backgroundSelected',
            },
            'Panel': {
                'background': 'background',
                'foreground': 'foreground',
            },
            'Viewport': {
                'background': 'background',
            },
            'ToolWindow': {
                'background': 'background',
                'Header.background': 'background',
                'Header.inactiveBackground': 'background',
                'Header.borderColor': 'border',
                'HeaderTab.underlinedTabBackground': 'backgroundElevated',
                'HeaderTab.selectedInactiveBackground': 'backgroundElevated',
                'HeaderTab.hoverBackground': 'backgroundHover',
                'HeaderTab.hoverInactiveBackground': 'backgroundHover',
                'HeaderTab.underlineColor': 'accent',
                'HeaderTab.underlineHeight': 3,
                'Button.hoverBackground': 'backgroundSelected',
                'Button.selectedBackground': 'backgroundSelected',
                'Button.selectedForeground': 'foregroundBright',
                'Stripe.background': 'backgroundFrame',
                'Stripe.borderColor': '#00000000',
                'Stripe.Icon.foreground': 'foreground',
                'Stripe.Icon.selectedForeground': 'foregroundBright',
                'Stripe.Icon.disabledForeground': 'foregroundDim',
            },
            'Tree': {
                'background': 'background',
                'foreground': 'foreground',
                'selectionBackground': 'selection',
                'selectionForeground': 'selectionForeground',
                'selectionInactiveBackground': 'selectionInactive',
                'hoverBackground': 'backgroundHover',
                'modifiedItemForeground': 'accent',
                'rowHeight': 24,
                'hash': 'borderSubtle',
                'indentLineColor': 'borderSubtle',
            },
            'List': {
                'background': 'background',
                'foreground': 'foreground',
                'selectionBackground': 'selection',
                'selectionForeground': 'selectionForeground',
                'selectionInactiveBackground': 'selectionInactive',
                'hoverBackground': 'backgroundHover',
            },
            'Table': {
                'background': 'background',
                'foreground': 'foreground',
                'selectionBackground': 'selection',
                'selectionForeground': 'selectionForeground',
                'selectionInactiveBackground': 'selectionInactive',
                'hoverBackground': 'backgroundHover',
                'gridColor': 'border',
                'stripeColor': 'backgroundElevated',
            },
            'EditorTabs': {
                'background': 'background',
                'underTabsBorderColor': 'border',
                'underlineColor': 'accent',
                'underlineHeight': 3,
                'underlinedTabBackground': 'backgroundElevated',
                'underlinedTabForeground': 'foreground',
                'underlinedBorderColor': 'accent',
                'inactiveUnderlinedTabBorderColor': 'foregroundDim',
                'inactiveUnderlinedTabBackground': 'background',
                'inactiveUnderlineColor': 'foregroundDim',
                'hoverBackground': 'backgroundHover',
                'inactiveColoredFileBackground': '#00000000',
            },
            'DefaultTabs': {
                'background': 'background',
                'underlineColor': 'accent',
                'underlineHeight': 3,
                'hoverBackground': 'backgroundHover',
                'inactiveUnderlineColor': 'foregroundDim',
            },
            'Editor': {
                'background': 'background',
                'foreground': 'foreground',
                'shortcutForeground': 'accent',
                'SearchField.borderColor': 'border',
            },
            'EditorPane': {
                'background': 'background',
                'foreground': 'foreground',
            },
            'TextField': {
                'background': 'backgroundElevated',
                'foreground': 'foreground',
                'caretForeground': 'caret',
                'selectionBackground': 'selection',
                'selectionForeground': 'selectionForeground',
                'borderColor': 'border',
                'focusedBorderColor': 'accent',
                'hoverBorderColor': 'borderSubtle',
            },
            'TextArea': {
                'background': 'backgroundElevated',
                'foreground': 'foreground',
                'caretForeground': 'caret',
                'selectionBackground': 'selection',
                'selectionForeground': 'selectionForeground',
            },
            'ComboBox': {
                'background': 'backgroundElevated',
                'foreground': 'foreground',
                'selectionBackground': 'selection',
                'selectionForeground': 'selectionForeground',
                'nonEditableBackground': 'backgroundElevated',
                'ArrowButton.iconColor': 'foreground',
                'ArrowButton.disabledIconColor': 'foregroundDim',
                'ArrowButton.nonEditableBackground': 'backgroundElevated',
                'borderColor': 'border',
                'focusedBorderColor': 'accent',
                'hoverBorderColor': 'borderSubtle',
            },
            'Button': {
                'arc': 8,
                'background': button,
                'foreground': 'foreground',
                'startBackground': button,
                'endBackground': button,
                'startBorderColor': 'borderSubtle',
                'endBorderColor': 'borderSubtle',
                'focusedBorderColor': 'accent',
                'disabledBorderColor': 'border',
                'shadowColor': '#00000000',
                'shadowWidth': 0,
                'default': {
                    'background': 'accent',
                    'foreground': 'selectionForeground',
                    'startBackground': 'accent',
                    'endBackground': 'accent',
                    'startBorderColor': 'accent',
                    'endBorderColor': 'accent',
                    'focusedBorderColor': 'accent',
                    'focusColor': accent + '66',
                    'shadowColor': '#00000000',
                },
            },
            'ActionButton': {
                'hoverBackground': button_hover,
                'hoverBorderColor': button_hover,
                'pressedBackground': 'backgroundSelected',
                'pressedBorderColor': 'backgroundSelected',
                'borderColor': '#00000000',
            },
            'Popup': {
                'background': 'backgroundElevated',
                'foreground': 'foreground',
                'borderColor': 'borderSubtle',
                'Header.activeBackground': 'backgroundSelected',
                'Header.inactiveBackground': 'backgroundElevated',
                'Advertiser.foreground': 'foregroundDim',
                'Advertiser.background': 'backgroundElevated',
                'separatorColor': 'border',
                'paintBorder': True,
            },
            'PopupMenu': {
                'background': 'backgroundElevated',
                'foreground': 'foreground',
                'selectionBackground': 'selection',
                'selectionForeground': 'selectionForeground',
                'borderColor': 'borderSubtle',
            },
            'Menu': {
                'background': 'backgroundElevated',
                'foreground': 'foreground',
                'selectionBackground': 'selection',
                'selectionForeground': 'selectionForeground',
                'borderColor': 'borderSubtle',
                'separatorColor': 'border',
                'acceleratorForeground': 'foregroundDim',
                'acceleratorSelectionForeground': 'selectionForeground',
            },
            'MenuItem': {
                'background': 'backgroundElevated',
                'foreground': 'foreground',
                'selectionBackground': 'selection',
                'selectionForeground': 'selectionForeground',
                'acceleratorForeground': 'foregroundDim',
                'disabledForeground': 'foregroundDim',
            },
            'StatusBar': {
                'background': 'backgroundFrame',
                'foreground': 'foreground',
                'borderColor': '#00000000',
                'Widget.hoverBackground': 'backgroundSelected',
            },
            'Notification': {
                'background': 'backgroundElevated',
                'foreground': 'foreground',
                'borderColor': 'borderSubtle',
            },
            'ProgressBar': {
                'trackColor': 'backgroundElevated',
                'progressColor': 'accent',
                'indeterminateStartColor': 'accent',
                'indeterminateEndColor': 'accentSecondary',
                'failedColor': 'red',
                'failedEndColor': 'red',
                'passedColor': 'green',
                'passedEndColor': 'green',
            },
            'ScrollBar': {
                'thumbColor': mix(surface1, fg, 0.12),
                'thumbBorderColor': mix(surface1, fg, 0.12),
                'hoverThumbColor': mix(surface1, fg, 0.22),
                'hoverThumbBorderColor': mix(surface1, fg, 0.22),
                'trackColor': bg,
            },
            'Link': {
                'activeForeground': 'accent',
                'hoverForeground': shift_lightness(accent, 0.08 if dark else -0.08),
                'pressedForeground': 'accent',
            },
            'WelcomeScreen': {
                'background': 'background',
            },
        },
        'icons': {
            'ColorPalette': {
                'Actions.Blue': palette.get('color4', accent),
                'Actions.Green': palette.get('color2', accent),
                'Actions.Yellow': palette.get('color3', accent),
                'Actions.Red': palette.get('color1', accent),
                'Checkbox.Background.Default': surface0,
                'Checkbox.Border.Default': border,
                'Checkbox.Focus.Thin.Default': accent,
                'Objects.Grey': disabled,
            }
        },
    }


def build_editor_scheme_xml(name: str, palette: dict[str, str]) -> str:
    bg = palette['background']
    fg = palette['foreground']
    accent = palette['accent']
    cursor = palette['cursor']
    sel_bg = palette['selection_background']
    sel_fg = palette['selection_foreground']
    line = shift_lightness(bg, 0.05 if is_dark(bg) else -0.05)
    gutter = shift_lightness(bg, 0.03 if is_dark(bg) else -0.03)
    ident = mix(accent, bg, 0.65)

    def opt(name: str, value: str) -> str:
        return f'    <option name="{name}" value="{xml_hex(value)}" />'

    def attr(name: str, *, fg_value: str | None = None, bg_value: str | None = None, effect: str | None = None, effect_type: str | None = None, font_type: str | None = None) -> str:
        parts = [f'    <option name="{name}">', '      <value>']
        if fg_value:
            parts.append(f'        <option name="FOREGROUND" value="{xml_hex(fg_value)}" />')
        if bg_value:
            parts.append(f'        <option name="BACKGROUND" value="{xml_hex(bg_value)}" />')
        if effect:
            parts.append(f'        <option name="EFFECT_COLOR" value="{xml_hex(effect)}" />')
        if effect_type:
            parts.append(f'        <option name="EFFECT_TYPE" value="{effect_type}" />')
        if font_type:
            parts.append(f'        <option name="FONT_TYPE" value="{font_type}" />')
        parts += ['      </value>', '    </option>']
        return '\n'.join(parts)

    lines = [
        f'<scheme name="{name}" version="142" parent_scheme="Darcula">',
        '  <colors>',
        opt('CARET_COLOR', cursor),
        opt('CARET_ROW_COLOR', line),
        opt('CONSOLE_BACKGROUND_KEY', bg),
        opt('GUTTER_BACKGROUND', gutter),
        opt('INDENT_GUIDE', mix(fg, bg, 0.75)),
        opt('LINE_NUMBERS_COLOR', mix(fg, bg, 0.50)),
        opt('RIGHT_MARGIN_COLOR', mix(fg, bg, 0.80)),
        opt('SELECTION_BACKGROUND', sel_bg),
        opt('SELECTION_FOREGROUND', sel_fg),
        opt('TEARLINE_COLOR', mix(fg, bg, 0.78)),
        opt('WHITESPACES', mix(fg, bg, 0.72)),
        '  </colors>',
        '  <attributes>',
        attr('DEFAULT_TEXT', fg_value=fg, bg_value=bg),
        attr('TEXT', fg_value=fg, bg_value=bg),
        attr('CARET_ROW', bg_value=line),
        attr('IDENTIFIER_UNDER_CARET_ATTRIBUTES', bg_value=ident),
        attr('TEXT_SEARCH_RESULT_ATTRIBUTES', bg_value=mix(accent, bg, 0.30), effect=accent, effect_type='BOXED'),
        attr('LIVE_TEMPLATE_ATTRIBUTES', effect=accent, effect_type='ROUNDED_BOX'),
        attr('MATCHED_BRACE_ATTRIBUTES', bg_value=mix(accent, bg, 0.30), effect=accent, effect_type='BOXED'),
        attr('WRITE_SEARCH_RESULT_ATTRIBUTES', bg_value=mix(palette.get('color1', accent), bg, 0.35), effect=palette.get('color1', accent), effect_type='BOXED'),
        '  </attributes>',
        '</scheme>',
    ]
    return '\n'.join(lines) + '\n'


def build_manifest(name: str, dark: bool) -> dict:
    generated_at = datetime.now(timezone.utc).isoformat()
    return {
        'schemaVersion': 2,
        'name': name,
        'generatedAt': generated_at,
        'dark': dark,
        'themeFile': THEME_JSON_PATH.name,
        'schemeFile': SCHEME_XML_PATH.name,
    }


def main() -> int:
    colors_file = current_theme_dir() / 'colors.toml'
    if not colors_file.exists():
        raise SystemExit(f'Colors file not found: {colors_file}')

    palette = load_palette(colors_file)
    name = display_theme_name()
    dark = is_dark(palette['background'])
    theme_json = build_theme_json(name, palette)
    editor_xml = build_editor_scheme_xml(name, palette)
    manifest = build_manifest(name, dark)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    MANIFEST_PATH.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    THEME_JSON_PATH.write_text(json.dumps(theme_json, indent=2) + '\n', encoding='utf-8')
    SCHEME_XML_PATH.write_text(editor_xml, encoding='utf-8')
    REFRESH_TOKEN_PATH.write_text(manifest['generatedAt'] + '\n', encoding='utf-8')

    print(f'Wrote {MANIFEST_PATH}')
    print(f'Wrote {THEME_JSON_PATH}')
    print(f'Wrote {SCHEME_XML_PATH}')
    print(f'Updated {REFRESH_TOKEN_PATH}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
