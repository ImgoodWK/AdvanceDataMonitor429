package com.imgood.textech.webae.dto;

import java.util.List;

public class OrderBatchRequest {

    public int networkId;
    /** 可选：批量下单共用的 CPU 名称。 */
    public String cpuName;
    public List<OrderItem> items;

    public static class OrderItem {

        public String itemName;
        public int amount;
        /** 可选：按样板 ID 下单（优先于 itemName）。格式 `<x>:<y>:<z>:<dim>#<slot>`。 */
        public String patternId;
    }
}
