package com.imgood.textech.webae.dto;

public class OrderResult {

    public boolean success;
    public String message;
    public String craftJobId; // 合成任务 ID（用于轮询状态）
    public int estimatedTime; // 预计完成时间（秒，可 -1 未知）
}
