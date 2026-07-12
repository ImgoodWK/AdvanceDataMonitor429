package com.imgood.textech.webae.dto;

public class OrderRequest {

    public int networkId;
    public String itemName; // 物品注册名或显示名（用于匹配）
    public int amount; // 下单数量
    public String rawText; // 原始文本（传给 submitCraft）
    public String locale; // 语言环境
    /** 可选：指定 AE2 合成 CPU 名称；为空时由 AE2 自动分配。 */
    public String cpuName;
    /** 可选：按样板 ID 下单（优先于 itemName）。 */
    public String patternId;
}
