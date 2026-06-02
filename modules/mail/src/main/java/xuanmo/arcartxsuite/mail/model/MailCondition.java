package xuanmo.arcartxsuite.mail.model;

import xuanmo.arcartxsuite.mail.config.MailConditionOperator;

public record MailCondition(
    String placeholder,
    MailConditionOperator operator,
    String expectedValue
) {
    public String serialize() {
        return placeholder + "\t" + operator.configKey() + "\t" + expectedValue;
    }

    public static MailCondition deserialize(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String[] parts = rawValue.split("\t", 3);
        if (parts.length < 3) {
            return null;
        }
        return new MailCondition(parts[0], MailConditionOperator.parse(parts[1]), parts[2]);
    }
}
