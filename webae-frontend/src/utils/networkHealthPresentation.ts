import type {
  NetworkHealthDiagnosticDto,
  NetworkHealthIssueDto,
  NetworkHealthStatus,
} from '@/types/dto';

export type NetworkHealthTone = 'success' | 'warning' | 'error' | 'default';

const ISSUE_MESSAGE_KEYS: Record<string, string> = {
  'webae.networkHealth.issue.noRegisteredNetwork': 'networkHealthIssue_no_registered_network',
  'webae.networkHealth.issue.noLink': 'networkHealthIssue_no_link',
  'webae.networkHealth.issue.gridMissing': 'networkHealthIssue_grid_missing',
  'webae.networkHealth.issue.monitorUnbound': 'networkHealthIssue_monitor_unbound',
  'webae.networkHealth.issue.monitorStale': 'networkHealthIssue_monitor_stale',
  'webae.networkHealth.issue.storageUnavailable': 'networkHealthIssue_storage_unavailable',
  'webae.networkHealth.issue.craftingUnavailable': 'networkHealthIssue_crafting_unavailable',
  'webae.networkHealth.issue.networkConnectorUnavailable': 'networkHealthIssue_network_connector_unavailable',
  'webae.networkHealth.issue.channelOverLimit': 'networkHealthIssue_channel_over_limit',
  'webae.networkHealth.issue.sampleStale': 'networkHealthIssue_sample_stale',
};

const ISSUE_SUGGESTION_KEYS: Record<string, string> = {
  'webae.networkHealth.suggestion.registerNetwork': 'networkHealthSuggestion_no_registered_network',
  'webae.networkHealth.suggestion.bindNetworkLink': 'networkHealthSuggestion_no_link',
  'webae.networkHealth.suggestion.checkAeCable': 'networkHealthSuggestion_grid_missing',
  'webae.networkHealth.suggestion.loadMonitorChunk': 'networkHealthSuggestion_monitor_stale',
  'webae.networkHealth.suggestion.checkStorageGrid': 'networkHealthSuggestion_storage_unavailable',
  'webae.networkHealth.suggestion.checkCraftingGrid': 'networkHealthSuggestion_crafting_unavailable',
  'webae.networkHealth.suggestion.reduceChannels': 'networkHealthSuggestion_channel_over_limit',
  'webae.networkHealth.suggestion.waitForSample': 'networkHealthSuggestion_sample_stale',
};

export function networkHealthStatusTone(status: NetworkHealthStatus | undefined): NetworkHealthTone {
  if (status === 'healthy') return 'success';
  if (status === 'degraded') return 'warning';
  if (status === 'failed') return 'error';
  return 'default';
}

/** Keep labels on the public four-state contract even if a newer server sends an unknown value. */
export function networkHealthStatusLabelKey(status: string | undefined): string {
  if (status === 'healthy' || status === 'degraded' || status === 'failed') {
    return `networkHealthStatus_${status}`;
  }
  return 'networkHealthStatus_unknown';
}

export function networkHealthSeverityTone(severity: string | undefined): NetworkHealthTone {
  if (severity === 'error') return 'error';
  if (severity === 'warning') return 'warning';
  return 'default';
}

export function networkHealthFreshness(row: Pick<NetworkHealthDiagnosticDto, 'stale' | 'sampleAgeMs'>): {
  tone: NetworkHealthTone;
  labelKey: 'networkHealthStale' | 'networkHealthFresh';
} {
  const stale = row.stale || row.sampleAgeMs == null;
  return {
    tone: stale ? 'warning' : 'success',
    labelKey: stale ? 'networkHealthStale' : 'networkHealthFresh',
  };
}

/** Prefer the DTO's public message contract, then fall back to its fixed issue code. */
export function networkHealthIssueMessageKey(issue: NetworkHealthIssueDto): string {
  if (issue.messageKey && ISSUE_MESSAGE_KEYS[issue.messageKey]) {
    return ISSUE_MESSAGE_KEYS[issue.messageKey];
  }
  return issue.code ? `networkHealthIssue_${issue.code}` : 'networkHealthUnknownIssue';
}

/** Prefer the DTO's public suggestion contract, then fall back to its fixed issue code. */
export function networkHealthIssueSuggestionKey(issue: NetworkHealthIssueDto): string {
  if (issue.suggestionKey && ISSUE_SUGGESTION_KEYS[issue.suggestionKey]) {
    return ISSUE_SUGGESTION_KEYS[issue.suggestionKey];
  }
  return issue.code ? `networkHealthSuggestion_${issue.code}` : 'networkHealthSuggestion_generic';
}

/** Render structured evidence compactly without leaking an unbounded object into the table. */
export function formatNetworkHealthEvidence(evidence: unknown, maxLength = 180): string {
  if (evidence == null) return '';
  let text: string;
  if (typeof evidence === 'string') {
    text = evidence;
  } else if (typeof evidence === 'number' || typeof evidence === 'boolean') {
    text = String(evidence);
  } else {
    try {
      text = JSON.stringify(evidence);
    } catch {
      text = String(evidence);
    }
  }
  if (text.length <= maxLength) return text;
  return `${text.slice(0, Math.max(0, maxLength - 1))}…`;
}
