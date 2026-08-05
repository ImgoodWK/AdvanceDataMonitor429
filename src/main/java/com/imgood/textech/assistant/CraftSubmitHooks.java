package com.imgood.textech.assistant;

import appeng.api.networking.crafting.ICraftingLink;

/**
 * Optional lifecycle callbacks for AE2 craft submit (WebAE order tracking).
 * Invoked on the server thread after calculation completes or fails.
 */
public interface CraftSubmitHooks {

    /** Job accepted by a crafting CPU; {@code link} is the standalone/CPU-side crafting link. */
    void onSubmitted(ICraftingLink link, String craftingId, String resolvedCpuName);

    /** Calculation timeout, simulation-only, submit rejected, or other hard failure. */
    void onFailed(String reason);
}
