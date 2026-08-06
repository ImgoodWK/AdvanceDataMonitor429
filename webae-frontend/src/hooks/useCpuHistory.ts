import { useCallback, useEffect, useState } from 'react';
import { getApiClient } from '@/api/client';
import { useAppContext } from '@/context/AppContext';
import type { CpuCapacityResponse, CpuHistoryResponse } from '@/types/dto';
import { normalizeCpuCapacityWindow } from '@/utils/cpuHistoryPresentation';

export interface CpuHistoryState {
  history: CpuHistoryResponse | null;
  capacity: CpuCapacityResponse | null;
  loading: boolean;
  error: string | null;
  historyError: string | null;
  capacityError: string | null;
}

const EMPTY_CPU_HISTORY_STATE: CpuHistoryState = {
  history: null,
  capacity: null,
  loading: false,
  error: null,
  historyError: null,
  capacityError: null,
};

/**
 * Loads the read-only CPU history and capacity summaries when a CPU detail
 * drawer is opened. This hook deliberately has no interval or refresh-tick
 * dependency: the server sampler owns collection cadence and the drawer only
 * reads the latest in-memory summary on demand.
 */
export function useCpuHistory(
  networkId: number | null,
  open: boolean,
  capacityWindow: string = '24h'
): CpuHistoryState & { reload: () => void } {
  const { isLoggedIn } = useAppContext();
  const [state, setState] = useState<CpuHistoryState>(EMPTY_CPU_HISTORY_STATE);
  const [requestVersion, setRequestVersion] = useState(0);

  const reload = useCallback(() => setRequestVersion((version) => version + 1), []);

  useEffect(() => {
    if (!open || networkId == null || !isLoggedIn) {
      // Clear cached rows whenever the drawer is inactive or auth/network
      // context changes. This prevents a previous owner's history from
      // remaining visible during logout or network switching.
      setState(EMPTY_CPU_HISTORY_STATE);
      return;
    }

    let active = true;
    setState({
      history: null,
      capacity: null,
      loading: true,
      error: null,
      historyError: null,
      capacityError: null,
    });
    const client = getApiClient();
    const network = encodeURIComponent(String(networkId));
    const window = normalizeCpuCapacityWindow(capacityWindow);
    const historyRequest = client.get<CpuHistoryResponse>(
      `/api/network/cpu/history?network=${network}&limit=500`
    );
    const capacityRequest = client.get<CpuCapacityResponse>(
      `/api/network/cpu/capacity?network=${network}&window=${window}`
    );

    void Promise.allSettled([historyRequest, capacityRequest]).then((results) => {
      if (!active) return;
      const historyResult = results[0];
      const capacityResult = results[1];
      const history = historyResult.status === 'fulfilled' && historyResult.value.success
        ? historyResult.value
        : null;
      const capacity = capacityResult.status === 'fulfilled' && capacityResult.value.success
        ? capacityResult.value
        : null;
      const historyError = historyResult.status === 'rejected' || (historyResult.status === 'fulfilled' && !historyResult.value.success)
        ? 'cpuHistoryRequestFailed'
        : null;
      const capacityError = capacityResult.status === 'rejected' || (capacityResult.status === 'fulfilled' && !capacityResult.value.success)
        ? 'cpuCapacityRequestFailed'
        : null;
      setState({
        history,
        capacity,
        loading: false,
        historyError,
        capacityError,
        error: historyError || capacityError,
      });
    });

    return () => {
      active = false;
    };
  }, [capacityWindow, isLoggedIn, networkId, open, requestVersion]);

  return { ...state, reload };
}
