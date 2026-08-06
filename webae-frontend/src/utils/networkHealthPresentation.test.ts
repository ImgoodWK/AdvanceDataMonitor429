import { describe, expect, it } from 'vitest';
import {
  formatNetworkHealthEvidence,
  networkHealthFreshness,
  networkHealthIssueMessageKey,
  networkHealthIssueSuggestionKey,
  networkHealthStatusLabelKey,
  networkHealthStatusTone,
} from './networkHealthPresentation';

describe('network health presentation', () => {
  it('never presents unknown or future statuses as healthy', () => {
    expect(networkHealthStatusTone('healthy')).toBe('success');
    expect(networkHealthStatusTone('unknown')).toBe('default');
    expect(networkHealthStatusTone('future-status')).toBe('default');
    expect(networkHealthStatusTone(undefined)).toBe('default');
    expect(networkHealthStatusLabelKey('healthy')).toBe('networkHealthStatus_healthy');
    expect(networkHealthStatusLabelKey('unknown')).toBe('networkHealthStatus_unknown');
    expect(networkHealthStatusLabelKey('future-status')).toBe('networkHealthStatus_unknown');
    expect(networkHealthStatusLabelKey(undefined)).toBe('networkHealthStatus_unknown');
  });

  it('shows stale explicitly even when an age is present', () => {
    expect(networkHealthFreshness({ stale: true, sampleAgeMs: 20_000 })).toEqual({
      tone: 'warning',
      labelKey: 'networkHealthStale',
    });
    expect(networkHealthFreshness({ stale: false, sampleAgeMs: null }).labelKey).toBe('networkHealthStale');
    expect(networkHealthFreshness({ stale: false, sampleAgeMs: 100 }).labelKey).toBe('networkHealthFresh');
  });

  it('prefers public DTO translation keys and falls back to fixed issue codes', () => {
    expect(networkHealthIssueMessageKey({
      code: 'no_link',
      severity: 'error',
      messageKey: 'webae.networkHealth.issue.noLink',
    })).toBe('networkHealthIssue_no_link');
    expect(networkHealthIssueSuggestionKey({
      code: 'sample_stale',
      severity: 'unknown',
      suggestionKey: 'webae.networkHealth.suggestion.waitForSample',
    })).toBe('networkHealthSuggestion_sample_stale');
    expect(networkHealthIssueMessageKey({ code: 'future_issue', severity: 'unknown' }))
      .toBe('networkHealthIssue_future_issue');
  });

  it('formats structured evidence and bounds long values', () => {
    expect(formatNetworkHealthEvidence({ used: 9, max: 8 })).toBe('{"used":9,"max":8}');
    expect(formatNetworkHealthEvidence('abcdef', 5)).toBe('abcd…');
    expect(formatNetworkHealthEvidence(null)).toBe('');
  });
});
