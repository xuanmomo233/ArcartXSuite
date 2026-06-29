/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.util;

import ink.ptms.chemdah.taboolib.common5.Coerce;

public class StringNumber {
    private NumberType type;
    private Number number;
    private String source;

    public StringNumber(long number) {
        this.number = number;
        this.type = NumberType.INT;
    }

    public StringNumber(double number) {
        this.number = number;
        this.type = NumberType.DOUBLE;
    }

    public StringNumber(String source) {
        this.source = source;
        try {
            this.number = Double.parseDouble(this.source);
            this.type = this.isInt(this.number.doubleValue()) ? NumberType.INT : NumberType.DOUBLE;
        }
        catch (Throwable ignored) {
            this.type = NumberType.STRING;
        }
    }

    public StringNumber add(String v) {
        StringNumber numberFormat = new StringNumber(v);
        if (this.isNumber() && numberFormat.isNumber()) {
            this.number = this.number.doubleValue() + numberFormat.getNumber().doubleValue();
            this.type = this.isInt(this.number.doubleValue()) ? NumberType.INT : NumberType.DOUBLE;
        } else {
            this.source = this.source + numberFormat.getSource();
            this.type = NumberType.STRING;
        }
        return this;
    }

    public StringNumber subtract(String v) {
        StringNumber numberFormat = new StringNumber(v);
        if (this.isNumber() && numberFormat.isNumber()) {
            this.number = this.number.doubleValue() - numberFormat.getNumber().doubleValue();
            this.type = this.isInt(this.number.doubleValue()) ? NumberType.INT : NumberType.DOUBLE;
        }
        return this;
    }

    public boolean isInt(double value2) {
        return (double)Coerce.toInteger((Object)value2) == value2;
    }

    public Object get() {
        switch (this.type.ordinal()) {
            case 1: {
                return this.number.longValue();
            }
            case 0: {
                return this.number.doubleValue();
            }
        }
        return this.source;
    }

    public boolean isNumber() {
        return this.type == NumberType.INT || this.type == NumberType.DOUBLE;
    }

    public Number getNumber() {
        return this.number;
    }

    public NumberType getType() {
        return this.type;
    }

    public String getSource() {
        return this.source;
    }

    public String toString() {
        return "StringNumber{type=" + (Object)((Object)this.type) + ", number=" + this.number + ", source='" + this.source + '\'' + '}';
    }

    public static enum NumberType {
        DOUBLE,
        INT,
        STRING;

    }
}

