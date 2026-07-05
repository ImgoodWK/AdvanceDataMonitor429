package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 * WebAE pattern DTOs for JSON serialization.
 * 
 * Includes PatternDto (for encoding), InterfaceDto (for interface enumeration),
 * 
 * PatternInjectRequest/PatternInjectResult (for injection).
 * 
 */

public class PatternDto {

    /** Unique pattern identifier (frontend-generated). */

    public String patternId;

    /** true = crafting pattern, false = processing pattern. */

    public boolean crafting;

    /** Allow substitution. */

    public boolean substitute;

    /** Can be used as substitution. */

    public boolean beSubstitute;

    /** Author name. */

    public String author;

    /** Up to 9x3=27 input slots (null or "air" for empty). */

    public List<PatternItemEntry> inputs;

    /** Output item list. */

    public List<PatternItemEntry> outputs;

    /** Encoded NBT as JSON string (Base64 or Gson-serialized NBTTagCompound). */

    public String encodedNbt;

    public PatternDto() {

        this.inputs = new ArrayList<PatternItemEntry>();

        this.outputs = new ArrayList<PatternItemEntry>();

    }

    /**
     * 
     * Simplified item entry for pattern I/O.
     * 
     */

    public static class PatternItemEntry {

        public String registryName;

        public String displayName;

        public int meta;

        public int stackSize;

        public boolean isFluid;

        public PatternItemEntry() {}

        public PatternItemEntry(String registryName, String displayName, int meta, int stackSize) {

            this.registryName = registryName;

            this.displayName = displayName;

            this.meta = meta;

            this.stackSize = stackSize;

            this.isFluid = false;

        }

        public PatternItemEntry(String registryName, String displayName, int meta, int stackSize, boolean isFluid) {

            this.registryName = registryName;

            this.displayName = displayName;

            this.meta = meta;

            this.stackSize = stackSize;

            this.isFluid = isFluid;

        }

    }

    /**
     * 
     * ME Interface information DTO.
     * 
     */

    public static class InterfaceDto {

        /** Interface name (customName or termName). */

        public String name;

        public int x;

        public int y;

        public int z;

        /** Dimension ID. */

        public int dim;

        /** Number of PATTERN_CAPACITY upgrade cards (0-3). */

        public int capacityUpgrades;

        /** Active slot count = (upgrades+1)*9. */

        public int activeSlots;

        /** Per-slot state. */

        public List<SlotState> slots;

        /** Target machine name (ICraftingIconProvider). */

        public String targetMachineName;

        /** GT5u RecipeMap name of target machine. */

        public String targetRecipePool;

        /** Human-readable machine recipe type (machine name + recipe pool). */

        public String machineRecipeType;

        /** Occupied pattern slots with decoded summary for Web UI. */

        public List<ExistingPatternEntry> existingPatterns;

        public InterfaceDto() {

            this.slots = new ArrayList<SlotState>();

            this.existingPatterns = new ArrayList<ExistingPatternEntry>();

        }

        public static class SlotState {

            public int index;

            public boolean occupied;

            /** Pattern summary if occupied (e.g. "Iron Ingot -> Iron Plate"). */

            public String patternSummary;

            public SlotState() {}

            public SlotState(int index, boolean occupied, String patternSummary) {

                this.index = index;

                this.occupied = occupied;

                this.patternSummary = patternSummary;

            }

        }

        /** Existing pattern in an interface slot (Phase 6 API extension). */

        public static class ExistingPatternEntry {

            public int slotIndex;

            /** Pattern id: {@code <x>:<y>:<z>:<dim>#<slot>}. */

            public String patternId;

            public List<PatternItemEntry> outputs;

            public boolean crafting;

            public ExistingPatternEntry() {

                this.outputs = new ArrayList<PatternItemEntry>();

            }

        }

    }

    /**
     * 
     * Request DTO for pattern injection.
     * 
     */

    public static class PatternInjectRequest {

        /** Encoded pattern NBT as JSON string. */

        public String encodedNbt;

        public int interfaceX;

        public int interfaceY;

        public int interfaceZ;

        public int interfaceDim;

        public int slotIndex;

        public int networkId;

        /** When true (default), deduct one blank pattern from AE storage before inject. */

        public boolean consumeBlank = true;

    }

    /**
     * 
     * Result DTO for pattern injection.
     * 
     */

    public static class PatternInjectResult {

        public boolean success;

        public String message;

        /** Updated interface state after injection (null on failure). */

        public InterfaceDto updatedInterface;

        public PatternInjectResult() {}

        public PatternInjectResult(boolean success, String message) {

            this.success = success;

            this.message = message;

        }

        public PatternInjectResult(boolean success, String message, InterfaceDto updatedInterface) {

            this.success = success;

            this.message = message;

            this.updatedInterface = updatedInterface;

        }

    }

}
