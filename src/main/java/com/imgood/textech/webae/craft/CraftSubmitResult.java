package com.imgood.textech.webae.craft;

/**
 * Structured result of starting an AE2 craft via WebAE (calculation accepted or immediate failure).
 */
public final class CraftSubmitResult {

    public boolean accepted;
    public boolean failed;
    public String message;
    /** calculating | submitted | failed */
    public String phase;
    public String craftingId;
    public String cpuName;

    public static CraftSubmitResult calculating(String message) {
        CraftSubmitResult r = new CraftSubmitResult();
        r.accepted = true;
        r.failed = false;
        r.phase = "calculating";
        r.message = message != null ? message : "";
        return r;
    }

    public static CraftSubmitResult failed(String message) {
        CraftSubmitResult r = new CraftSubmitResult();
        r.accepted = false;
        r.failed = true;
        r.phase = "failed";
        r.message = message != null ? message : "failed";
        return r;
    }
}
