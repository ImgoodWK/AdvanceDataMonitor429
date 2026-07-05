package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified browse entry for {@code GET /api/patterns/browse} — Grid craftables and
 * Interface slot patterns merged with pagination.
 */
public class PatternBrowseEntryDto {

    /** {@code "grid"} or {@code "interface"}. */
    public String source;
    /** Interface slot ID ({@code <x>:<y>:<z>:<dim>#<slot>}); null for grid entries. */
    public String patternId;
    /** Grid entry unique key ({@code grid:<index>}); null for interface entries. */
    public String gridKey;
    public String sourceInterface;
    public String sourceInterfaceName;
    public int slotIndex;
    public int gridIndex;
    public boolean crafting;
    public boolean substitute;
    public boolean beSubstitute;
    public String author;
    public List<PatternDto.PatternItemEntry> inputs;
    public List<PatternDto.PatternItemEntry> outputs;
    /** Primary output display name (card title). */
    public String displayName;
    /** Primary output registry name (icon lookup). */
    public String registryName;
    public int meta;
    public long amount;
    public int inputsCount;
    public int outputsCount;

    public PatternBrowseEntryDto() {
        this.inputs = new ArrayList<PatternDto.PatternItemEntry>();
        this.outputs = new ArrayList<PatternDto.PatternItemEntry>();
    }
}
