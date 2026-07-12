export interface McTextSegment {
  text: string;
  color?: string;
  bold?: boolean;
  italic?: boolean;
  underline?: boolean;
  strikethrough?: boolean;
  obfuscated?: boolean;
}

/** Minecraft 1.7.10 standard chat colors (§0–§f). */
const MC_COLORS: Record<string, string> = {
  '0': '#000000',
  '1': '#0000AA',
  '2': '#00AA00',
  '3': '#00AAAA',
  '4': '#AA0000',
  '5': '#AA00AA',
  '6': '#FFAA00',
  '7': '#AAAAAA',
  '8': '#555555',
  '9': '#5555FF',
  a: '#55FF55',
  b: '#55FFFF',
  c: '#FF5555',
  d: '#FF55FF',
  e: '#FFFF55',
  f: '#FFFFFF',
};

const FORMAT_PREFIX = '\u00A7';

interface StyleState {
  color?: string;
  bold?: boolean;
  italic?: boolean;
  underline?: boolean;
  strikethrough?: boolean;
}

const DEFAULT_STYLE: StyleState = {};

function normalizeFormatCodes(input: string): string {
  return input.replace(/&([0-9a-fk-or])/gi, (_m, code: string) => `${FORMAT_PREFIX}${code}`);
}

function segmentFromState(text: string, state: StyleState): McTextSegment {
  const seg: McTextSegment = { text };
  if (state.color) seg.color = state.color;
  if (state.bold) seg.bold = true;
  if (state.italic) seg.italic = true;
  if (state.underline) seg.underline = true;
  if (state.strikethrough) seg.strikethrough = true;
  return seg;
}

function applyFormatCode(state: StyleState, code: string): StyleState {
  const lower = code.toLowerCase();
  if (lower in MC_COLORS) {
    return { ...state, color: MC_COLORS[lower] };
  }
  switch (lower) {
    case 'r':
      return { ...DEFAULT_STYLE };
    case 'l':
      return { ...state, bold: true };
    case 'o':
      return { ...state, italic: true };
    case 'n':
      return { ...state, underline: true };
    case 'm':
      return { ...state, strikethrough: true };
    case 'k':
      // Obfuscation not animated on Web; skip the code only.
      return state;
    default:
      return state;
  }
}

export function parseMcFormatting(input: string): McTextSegment[] {
  if (!input) return [{ text: '' }];
  const normalized = normalizeFormatCodes(input);
  const segments: McTextSegment[] = [];
  let state: StyleState = { ...DEFAULT_STYLE };
  let buf = '';

  const flush = () => {
    if (buf.length === 0) return;
    segments.push(segmentFromState(buf, state));
    buf = '';
  };

  for (let i = 0; i < normalized.length; i++) {
    const ch = normalized[i];
    if (ch === FORMAT_PREFIX && i + 1 < normalized.length) {
      const code = normalized[i + 1];
      i++;
      flush();
      state = applyFormatCode(state, code);
      continue;
    }
    buf += ch;
  }
  flush();
  return segments.length > 0 ? segments : [{ text: '' }];
}

export function stripMcFormatting(input: string): string {
  if (!input) return '';
  const normalized = normalizeFormatCodes(input);
  return normalized.replace(/\u00A7[0-9a-fk-or]/gi, '');
}

export function getMcPrimaryColor(input: string): string | undefined {
  if (!input) return undefined;
  const normalized = normalizeFormatCodes(input);
  const match = normalized.match(/\u00A7([0-9a-f])/i);
  if (!match) return undefined;
  return MC_COLORS[match[1].toLowerCase()];
}
