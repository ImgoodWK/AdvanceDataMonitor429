package com.imgood.textech.webae.balance;

/**
 * Read-only cross-network resource balance suggestion (Phase 8).
 */
public final class NetworkBalanceSuggestion {

    /** {@code item} | {@code fluid} | {@code essentia} */
    public String resourceType = "item";
    public String itemId;
    public String displayName;
    /** Network that appears short on this resource. */
    public int needyNetworkId;
    public long needyAmount;
    /** Network with surplus that could cover the shortage (suggestion only). */
    public int sourceNetworkId;
    public long sourceAmount;
    /** Suggested transferable amount (min of surplus gap and shortage). */
    public long transferable;
}
