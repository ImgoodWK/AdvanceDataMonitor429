package com.imgood.textech.webae.scanner;

/**
 * Read-only DTO for a Link Scanner compatible block in loaded chunks.
 */
public final class LinkScannerBlockDto {

    public int dimension;
    public int x;
    public int y;
    public int z;
    public String blockTypeId = "";
    public String blockTypeLabelKey = "";
    public String owner = "";
    public String alias = "";
    public String locationKey = "";
}
