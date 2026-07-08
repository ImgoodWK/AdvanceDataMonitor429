package com.imgood.textech.webae.order;

/**
 * Single line in a batch order template ({@code TeXTech/WebAE/web-order-templates.json}).
 */
public final class WebOrderTemplateItem {

    public String itemName = "";
    public int amount = 1;
    /** Optional pattern id ({@code x:y:z:dim#slot}); takes priority over itemName when ordering. */
    public String patternId;
}
