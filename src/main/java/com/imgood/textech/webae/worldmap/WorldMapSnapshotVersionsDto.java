package com.imgood.textech.webae.worldmap;

import java.util.ArrayList;
import java.util.List;

/** Gson-friendly response for the pointer-retained world-map versions. */
public final class WorldMapSnapshotVersionsDto {

    public boolean success;
    /** ok, no_versions, or unknown when the current pointer cannot be trusted. */
    public String status = "unknown";
    public int currentVersion;
    public int previousVersion;
    public List<VersionInfo> versions = new ArrayList<VersionInfo>();

    public static final class VersionInfo {

        public int version;
        public long timestamp;
        public String source = "";
        public int tilePx;
        public boolean manifestAvailable;
        public boolean logicalAvailable;
    }
}
