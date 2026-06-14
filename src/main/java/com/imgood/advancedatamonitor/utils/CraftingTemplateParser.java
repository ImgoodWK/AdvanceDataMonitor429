package com.imgood.advancedatamonitor.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 合成监控模板解析器。
 * 语法说明：
 * {br} → 换行符
 * {varName} → 全局统计变量
 * {varName:CPU名称} → 指定 CPU 的统计变量
 * {表达式 ? "true文本" : "false文本"}
 * 表达式格式：变量 [运算符 数字]
 * 支持运算符：> , < , >= , <= , == , !=
 * 如果表达式不含运算符，则直接将变量值作为布尔值（0 = false，非0 = true）
 * true/false 文本必须用双引号括起来，内部支持嵌套的{...}占位符
 *
 * 所有占位符都可以嵌套，解析会从最内层开始不断替换直到没有{}
 */
public class CraftingTemplateParser {

    /**
     * 解析模板并返回按行分割的文本列表。
     *
     * @param template 模板字符串
     * @param provider 数据提供者，负责根据变量名和可选参数返回值
     * @return 渲染用的文本行列表
     */
    public static List<String> parse(String template, DataProvider provider) {
        if (template == null || template.isEmpty()) {
            return new ArrayList<>();
        }
        // 反复替换所有占位符，直到字符串中不再有 '{'
        String resolved = resolveAll(template, provider);
        // 按换行符切分行
        String[] lines = resolved.split("\\n", -1);
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            // 保留空行（用户可能有意留空）
            result.add(line);
        }
        return result;
    }

    /**
     * 循环解析模板中最内层的占位符，直到所有 {} 都被处理。
     */
    private static String resolveAll(String template, DataProvider provider) {
        // 匹配不含嵌套花括号的最内层块
        Pattern innerBlock = Pattern.compile("\\{[^{}]*\\}");
        StringBuffer sb = new StringBuffer();
        boolean changed;
        do {
            changed = false;
            Matcher m = innerBlock.matcher(template);
            sb.setLength(0);
            while (m.find()) {
                String block = m.group(); // 包含大括号的原始块
                String content = block.substring(1, block.length() - 1); // 去掉大括号
                String replacement = evaluateBlock(content, provider);
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                changed = true;
            }
            m.appendTail(sb);
            template = sb.toString();
        } while (changed);
        return template;
    }

    /**
     * 处理一个花括号内的内容（已经去掉了 {}）。
     */
    private static String evaluateBlock(String content, DataProvider provider) {
        // 1. 去首尾空格
        content = content.trim();

        // 2. 换行标记
        if (content.equalsIgnoreCase("br")) {
            return "\n";
        }

        // 3. 检查是否为条件表达式（包含 '?' 和 ':'）
        if (content.contains("?")) {
            return evaluateConditional(content, provider);
        }

        // 4. 普通变量，可能带参数
        return evaluateVariable(content, provider);
    }

    /**
     * 解析普通变量：varName 或 varName:参数
     */
    private static String evaluateVariable(String expr, DataProvider provider) {
        // 允许变量名和参数中包含冒号以外的字符
        int colonIdx = expr.indexOf(':');
        String varName;
        String param = null;
        if (colonIdx != -1) {
            varName = expr.substring(0, colonIdx)
                .trim();
            param = expr.substring(colonIdx + 1)
                .trim();
            // 参数有可能为空（例如 "busyCpus:" ），视为无参数
            if (param.isEmpty()) param = null;
        } else {
            varName = expr.trim();
        }

        // 从数据提供者获取值
        Object value;
        if (param != null) {
            value = provider.getValue(varName, param);
        } else {
            value = provider.getValue(varName);
        }
        return valueToString(value);
    }

    /**
     * 解析条件表达式：
     * 格式： 表达式 ? "true文本" : "false文本"
     */
    private static String evaluateConditional(String expr, DataProvider provider) {
        // 寻找第一个 '?' 的位置
        int qmark = expr.indexOf('?');
        if (qmark == -1) {
            return "??";
        }

        String conditionPart = expr.substring(0, qmark)
            .trim();
        String remaining = expr.substring(qmark + 1)
            .trim();

        // 解析 true 文本和 false 文本，两者都必须用双引号括起来
        // 格式： "trueText" : "falseText"
        // 简单实现：从左到右扫描字符串，识别两个引号段
        String trueText = extractQuotedString(remaining);
        if (trueText == null) {
            return "??";
        }
        // trueText 之后应该紧跟着 ':' 和另一个引号段
        int afterTrueIdx = remaining.indexOf('"') + trueText.length() + 2; // 跳过前后引号
        if (afterTrueIdx >= remaining.length()) {
            return "??";
        }
        // 跳过 ':' 及空格
        String afterTrue = remaining.substring(afterTrueIdx)
            .trim();
        if (afterTrue.isEmpty() || afterTrue.charAt(0) != ':') {
            return "??";
        }
        afterTrue = afterTrue.substring(1)
            .trim(); // 去掉 ':'
        String falseText = extractQuotedString(afterTrue);
        if (falseText == null) {
            return "??";
        }

        // 判断条件真假
        boolean conditionResult = evaluateCondition(conditionPart, provider);

        // 选择对应文本，文本内部可能还包含占位符，但此时文本字符串内的 {} 还没有被解析
        // 因此需要递归调用 resolveAll 来展开（嵌套支持）
        String chosen = conditionResult ? trueText : falseText;
        // 递归解析 chosen 文本中的占位符
        return resolveAll(chosen, provider);
    }

    /**
     * 从字符串开头提取一个双引号括起来的字符串，返回去引号后的内容，失败返回 null。
     * 假设字符串已经去除了前导空格。
     */
    private static String extractQuotedString(String str) {
        if (str == null || str.isEmpty() || str.charAt(0) != '"') {
            return null;
        }
        int start = 1; // 跳过第一个引号
        int end = start;
        boolean escaped = false;
        while (end < str.length()) {
            char c = str.charAt(end);
            if (escaped) {
                escaped = false;
                end++;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                end++;
                continue;
            }
            if (c == '"') {
                // 找到了结束引号
                break;
            }
            end++;
        }
        if (end >= str.length()) {
            // 没有找到结束引号
            return null;
        }
        // 返回引号内的内容，将转义序列还原
        return str.substring(start, end)
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }

    /**
     * 计算条件表达式（conditionPart），例如 "busyCpus > 2" 或 "busyCpus:CPU#1 == 1"。
     */
    private static boolean evaluateCondition(String condition, DataProvider provider) {
        // 用正则提取：变量部分 (可能带参数) 运算符 数字(整数或小数)
        // 变量名允许字母数字和下划线，参数为冒号后的任意非空白字符（但受限...简化一下：参数为冒号后直到运算符前的非空白字符）
        // 正则：([A-Za-z0-9_]+(?:\s*:\s*[^<>=!\s]+)?)\s*([<>=!]=?|>=?|<=?)\s*(-?\d+\.?\d*)
        // 更宽松：先按运算符分割
        Pattern condPattern = Pattern
            .compile("([A-Za-z0-9_]+(?:\\s*:\\s*[^<>=!\\s]+)?)\\s*(>=?|<=?|==|!=)\\s*(-?\\d+\\.?\\d*)");
        Matcher m = condPattern.matcher(condition);
        if (m.matches()) {
            String varExpr = m.group(1)
                .trim();
            String op = m.group(2)
                .trim();
            String numStr = m.group(3)
                .trim();
            double compareNum;
            try {
                compareNum = Double.parseDouble(numStr);
            } catch (NumberFormatException e) {
                return false;
            }

            // 解析变量表达式，可能带参数
            String varName;
            String param = null;
            int colon = varExpr.indexOf(':');
            if (colon != -1) {
                varName = varExpr.substring(0, colon)
                    .trim();
                param = varExpr.substring(colon + 1)
                    .trim();
            } else {
                varName = varExpr.trim();
            }

            Object valObj;
            if (param != null) {
                valObj = provider.getValue(varName, param);
            } else {
                valObj = provider.getValue(varName);
            }

            // 尝试转换为数字比较
            Double varNum = toDouble(valObj);
            if (varNum != null) {
                switch (op) {
                    case ">":
                        return varNum > compareNum;
                    case "<":
                        return varNum < compareNum;
                    case ">=":
                        return varNum >= compareNum;
                    case "<=":
                        return varNum <= compareNum;
                    case "==":
                        return Math.abs(varNum - compareNum) < 0.0001;
                    case "!=":
                        return Math.abs(varNum - compareNum) >= 0.0001;
                    default:
                        return false;
                }
            } else {
                // 非数字则进行字符串比较（只对 == 和 != 有意义）
                String varStr = valueToString(valObj);
                switch (op) {
                    case "==":
                        return varStr.equals(numStr);
                    case "!=":
                        return !varStr.equals(numStr);
                    default:
                        return false; // 其他比较无意义
                }
            }
        } else {
            // 没有运算符或不匹配格式，直接将整个表达式当作变量取值，然后作为布尔值（0=false）
            Object valObj;
            // 可能带参数
            int colon = condition.indexOf(':');
            if (colon != -1) {
                String varName = condition.substring(0, colon)
                    .trim();
                String param = condition.substring(colon + 1)
                    .trim();
                valObj = provider.getValue(varName, param);
            } else {
                valObj = provider.getValue(condition.trim());
            }
            Double d = toDouble(valObj);
            if (d != null) {
                return d != 0;
            }
            // 字符串非空视为 true
            return valObj != null && !valObj.toString()
                .isEmpty();
        }
    }

    /**
     * 将数据对象转为字符串，用于简单变量替换。
     */
    private static String valueToString(Object val) {
        if (val == null) return "??";
        return val.toString();
    }

    private static Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // =================== 数据提供接口 ===================

    /**
     * 模板数据提供者，需要实现两个获取值的方法。
     */
    public interface DataProvider {

        /** 获取全局变量值 */
        Object getValue(String variable);

        /** 获取指定参数（如CPU名称）的变量值 */
        Object getValue(String variable, String argument);
    }
}
