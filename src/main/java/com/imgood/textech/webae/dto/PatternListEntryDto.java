package com.imgood.textech.webae.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 网络样板总览条目 DTO —— 对应 WebAE {@code GET /api/patterns} 返回数组项。
 *
 * <p>
 * 由 {@link com.imgood.textech.webae.api.handler.PatternListHandler} 遍历当前网络
 * 所有 ME 接口的样板槽位解码得到，富样板信息用于前端仿增广样板终端展示。
 * </p>
 *
 * <p>
 * 字段：
 * </p>
 * <ul>
 * <li>{@code patternId} — 前端可引用的稳定 ID（格式 {@code <x>:<y>:<z>:<dim>#<slot>}）</li>
 * <li>{@code sourceInterface} — 来源接口坐标编码 {@code <x>:<y>:<z>:<dim>}</li>
 * <li>{@code sourceInterfaceName} — 来源接口显示名</li>
 * <li>{@code slotIndex} — 接口槽位索引（0-based）</li>
 * <li>{@code crafting} / {@code substitute} / {@code beSubstitute} — 样板标志</li>
 * <li>{@code author} — 作者（NBT 中存储，可能为空）</li>
 * <li>{@code inputs} — 输入槽位（最多 9×3=27，null 表示空槽）</li>
 * <li>{@code outputs} — 输出物品列表</li>
 * <li>{@code encodedNbt} — 编码 NBT JSON 字符串（用于回写/导出）</li>
 * </ul>
 */
public class PatternListEntryDto {

    public String patternId;
    public String sourceInterface;
    public String sourceInterfaceName;
    public int slotIndex;
    public boolean crafting;
    public boolean substitute;
    public boolean beSubstitute;
    public boolean programmableHatches;
    public String author;
    public List<PatternDto.PatternItemEntry> inputs;
    public List<PatternDto.PatternItemEntry> outputs;
    public String encodedNbt;

    public PatternListEntryDto() {
        this.inputs = new ArrayList<PatternDto.PatternItemEntry>();
        this.outputs = new ArrayList<PatternDto.PatternItemEntry>();
    }
}
