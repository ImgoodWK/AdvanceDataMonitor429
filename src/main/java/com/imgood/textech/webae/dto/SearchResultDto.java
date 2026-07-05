package com.imgood.textech.webae.dto;

/**
 * Single entry in {@code GET /api/search} aggregated results.
 */
public class SearchResultDto {

    /** Result category: {@code storage}, {@code recipe}, {@code gt}, or {@code pattern}. */
    public String type;
    /** Stable id for client de-duplication (e.g. {@code storage:0:item:...}). */
    public String id;
    /** Primary display label. */
    public String label;
    /** Secondary line (network, handler, coordinates, etc.). */
    public String subtitle;
    public int networkId = -1;

    /** Storage: {@code item}, {@code fluid}, or {@code essentia}. */
    public String category;
    public String itemId;
    public String registryName;
    public int meta;
    public long amount;

    /** Recipe navigation. */
    public String handlerId;
    public int recipeIndex = -1;

    /** GT machine coordinates. */
    public int x;
    public int y;
    public int z;
    public int dim;

    /** Pattern navigation. */
    public String patternId;
    public String gridKey;
    public String source;

    public SearchResultDto() {}
}
