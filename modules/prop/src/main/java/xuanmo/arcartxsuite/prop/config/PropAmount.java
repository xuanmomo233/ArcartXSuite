package xuanmo.arcartxsuite.prop.config;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.attribute.SymphonyBridge;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.api.script.AriaBridge;

/** 配置中的固定数值，或在道具使用时求值的 Aria 公式。 */
public sealed interface PropAmount {
    double resolve(Inputs inputs, AriaBridge aria);

    static PropAmount load(Object input, String path) {
        if (input instanceof Number number && Double.isFinite(number.doubleValue())) {
            return new Fixed(number.doubleValue());
        }
        if (input instanceof String) {
            return new Formula(path, text(input, path), Map.of(), Map.of());
        }
        if (!(input instanceof Map<?, ?> formula)) {
            throw new IllegalArgumentException(path + " 必须为有限数值、Aria 表达式或公式配置节");
        }
        for (Object key : formula.keySet()) {
            if (!"aria".equals(key) && !"papi".equals(key) && !"symphony".equals(key)) {
                throw new IllegalArgumentException(path + " 含未知字段 " + key);
            }
        }
        String expression = text(formula.get("aria"), path + ".aria");
        Map<String, String> papi = bindings(formula, "papi", path);
        Map<String, String> symphony = bindings(formula, "symphony", path);
        for (var entry : symphony.entrySet()) {
            String variablePath = path + ".symphony." + entry.getKey();
            if (papi.containsKey(entry.getKey())) {
                throw new IllegalArgumentException(variablePath + " 与 papi 变量重名");
            }
            String id = PropEffects.namespacedId(entry.getValue(), variablePath).toString();
            if (id.length() > 128) {
                throw new IllegalArgumentException(variablePath + " 属性 ID 超出 Symphony 的 128 字符限制");
            }
            entry.setValue(id);
        }
        return new Formula(path, expression, papi, symphony);
    }

    private static Map<String, String> bindings(Map<?, ?> formula, String key, String path) {
        Map<String, String> variables = new LinkedHashMap<>();
        if (!formula.containsKey(key)) return variables;
        String section = path + "." + key;
        if (!(formula.get(key) instanceof Map<?, ?> bindings)) {
            throw new IllegalArgumentException(section + " 必须为变量到数据来源的映射");
        }
        for (var entry : bindings.entrySet()) {
            String name = text(entry.getKey(), section + " 变量名");
            if (!Formula.VARIABLE.matcher(name).matches() || name.equals("level") || name.equals("stats")) {
                throw new IllegalArgumentException(section + " 变量名无效或已保留: " + name);
            }
            String value = text(entry.getValue(), section + "." + name);
            if (variables.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException(section + " 变量名重复: " + name);
            }
        }
        return variables;
    }

    private static String text(Object value, String path) {
        if (value instanceof String text && !text.isBlank()) return text.trim();
        throw new IllegalArgumentException(path + " 必须为非空字符串");
    }

    record Fixed(double value) implements PropAmount {
        @Override
        public double resolve(Inputs inputs, AriaBridge aria) {
            return value;
        }
    }

    record Formula(String path, String expression, Map<String, String> papi, Map<String, String> symphony) implements PropAmount {
        private static final Pattern VARIABLE = Pattern.compile("[a-zA-Z_][a-zA-Z_0-9]*");

        public Formula {
            papi = Map.copyOf(papi);
            symphony = Map.copyOf(symphony);
        }

        @Override
        public double resolve(Inputs inputs, AriaBridge aria) {
            try {
                if (!aria.available()) throw new EvaluationException("Aria 不可用", null);
                Map<String, Object> bindings = new LinkedHashMap<>();
                bindings.put("level", inputs.player.getLevel());
                bindings.put("stats", inputs);
                symphony.forEach((name, id) -> bindings.put(name, inputs.symphony(id)));
                papi.forEach((name, placeholder) -> bindings.put(name, inputs.papi(placeholder)));
                if (inputs.failure != null) throw inputs.failure;
                Object result = aria.eval(expression, bindings);
                if (inputs.failure != null) throw inputs.failure;
                if (result instanceof Number number && Double.isFinite(number.doubleValue())) {
                    return number.doubleValue();
                }
                throw new EvaluationException("Aria 公式未返回有限数值", null);
            } catch (EvaluationException exception) {
                throw new EvaluationException(path + ": " + exception.getMessage(), exception);
            } catch (RuntimeException | LinkageError exception) {
                String message = inputs.failure == null ? "数值求值失败" : inputs.failure.getMessage();
                throw new EvaluationException(path + ": " + message, exception);
            }
        }
    }

    /** 一次道具使用的公式输入。在 Aria 中通过 global.stats 访问。 */
    final class Inputs {
        private final Player player;
        private final SymphonyBridge symphony;
        private final PlaceholderResolverAPI placeholders;
        private final Map<String, Double> resolvedPlaceholders = new HashMap<>();
        private Map<String, Double> attributes;
        private EvaluationException failure;

        public Inputs(Player player, SymphonyBridge symphony, PlaceholderResolverAPI placeholders) {
            this.player = player;
            this.symphony = symphony;
            this.placeholders = placeholders;
        }

        public double symphony(String attribute) {
            try {
                String raw = text(attribute, "Symphony 属性 ID");
                String id = PropEffects.namespacedId(raw.contains(":") ? raw : "symphony:" + raw, "Symphony 属性 ID").toString();
                if (attributes == null) {
                    if (!symphony.available()) throw new EvaluationException("Symphony 不可用", null);
                    attributes = symphony.attributeValues(player);
                }
                Double value = attributes.get(id);
                if (value == null || !Double.isFinite(value)) {
                    throw new EvaluationException("Symphony 属性 " + id + " 不可用", null);
                }
                return value;
            } catch (RuntimeException | LinkageError exception) {
                return rememberFailure("读取 Symphony 属性失败", exception);
            }
        }

        public double papi(String placeholder) {
            try {
                String token = text(placeholder, "PAPI 占位符");
                return resolvedPlaceholders.computeIfAbsent(token, this::readPlaceholder);
            } catch (RuntimeException | LinkageError exception) {
                return rememberFailure("读取 PAPI 数值失败", exception);
            }
        }

        private double readPlaceholder(String token) {
            if (!placeholders.available()) throw new EvaluationException("PlaceholderAPI 不可用", null);
            String value = placeholders.applyPlaceholders(player, token);
            try {
                double number = Double.parseDouble(value.trim());
                if (Double.isFinite(number)) return number;
            } catch (NumberFormatException ignored) {
                // 统一报告占位符名称，不输出解析结果。
            }
            throw new EvaluationException("PAPI 占位符 " + token + " 未返回有限数值", null);
        }

        private double rememberFailure(String reason, Throwable exception) {
            String detail = exception instanceof EvaluationException || exception instanceof IllegalArgumentException
                ? exception.getMessage() : reason;
            if (failure == null) failure = new EvaluationException(detail, exception);
            return Double.NaN;
        }
    }

    final class EvaluationException extends RuntimeException {
        private EvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
