package com.imgood.textech.compat.ae;

/** Byte/type snapshot for a single storage cell. */
public final class AeCellStats {

    public long totalBytes;
    public long usedBytes;
    public int totalTypes;
    public int usedTypes;
    public boolean infinite;
    public boolean present;

    public void clear() {
        totalBytes = 0L;
        usedBytes = 0L;
        totalTypes = 0;
        usedTypes = 0;
        infinite = false;
        present = false;
    }
}
