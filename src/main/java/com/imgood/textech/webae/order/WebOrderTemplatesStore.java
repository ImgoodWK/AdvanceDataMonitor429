package com.imgood.textech.webae.order;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root document for {@code config/textech/web-order-templates.json}.
 * Templates are keyed by owner UUID (WebAE token owner).
 */
public final class WebOrderTemplatesStore {

    public int version = 1;
    public Map<String, List<WebOrderTemplate>> owners = new HashMap<String, List<WebOrderTemplate>>();

    public List<WebOrderTemplate> templatesForOwner(String ownerUuid) {
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return new ArrayList<WebOrderTemplate>();
        }
        List<WebOrderTemplate> list = owners.get(ownerUuid);
        if (list == null) {
            return new ArrayList<WebOrderTemplate>();
        }
        return list;
    }
}
