package com.imgood.textech.webae.order;

import java.util.ArrayList;
import java.util.List;

/**
 * Persisted batch order preset for one WebAE owner.
 */
public final class WebOrderTemplate {

    public String id = "";
    public String name = "";
    /** AE2 crafting CPU name; empty = auto assign. */
    public String cpuName = "";
    public int networkId = 0;
    public List<WebOrderTemplateItem> items = new ArrayList<WebOrderTemplateItem>();
    public long updatedAt = 0L;
}
