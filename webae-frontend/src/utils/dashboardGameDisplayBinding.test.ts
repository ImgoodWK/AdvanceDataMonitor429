import { describe, expect, it } from 'vitest';
import {
  buildGameDisplayBindingJson,
  GAME_DISPLAY_BINDING_FORMAT,
  isGameDisplayBinding,
  parseGameDisplayBindingJson,
} from './dashboardGameDisplayBinding';

describe('dashboard game display binding', () => {
  it('round-trips binding JSON', () => {
    const json = buildGameDisplayBindingJson({
      format: GAME_DISPLAY_BINDING_FORMAT,
      version: 1,
      mode: 'dashboard_live',
      displayId: 'abc',
      viewToken: 'tok',
      title: '  Plant   board ',
      exportedAt: 42,
      viewportHint: { width: 960, height: 720 },
      webaeOrigin: '',
      embedPath: '/embed/dashboard/abc?token=tok',
    });
    const parsed = parseGameDisplayBindingJson(json);
    expect(isGameDisplayBinding(parsed)).toBe(true);
    expect(parsed.title).toBe('Plant board');
    expect(parsed.displayId).toBe('abc');
    expect(parsed.mode).toBe('dashboard_live');
  });

  it('rejects snapshot-shaped payloads', () => {
    expect(() =>
      parseGameDisplayBindingJson(
        JSON.stringify({ format: 'textech-webae-display-snapshot', version: 1, displayId: 'x' })
      )
    ).toThrow(/invalid_display_binding/);
  });
});
