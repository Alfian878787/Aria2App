package com.gianlu.aria2app.Options;

import java.util.List;

class OptionChild {
    private String defValue;
    private String currValue;
    private List<String> values;
    private Option.TYPE type;

    OptionChild(Option.TYPE type, Object defValue, String currValue) {
        this(type, defValue, currValue, null);
    }

    private OptionChild(Option.TYPE type, Object defValue, String currValue, List<String> values) {
        this.type = type;
        if (defValue == null)
            this.defValue = null;
        else
            this.defValue = String.valueOf(defValue);
        this.currValue = currValue;
        this.values = values;
    }

    boolean isChanged() {
        return currValue != null;
    }

    void setCurrentValue(String currValue) {
        this.currValue = currValue;
    }

    List<String> getValues() {
        return values;
    }

    public String getValue() {
        if (isChanged()) return currValue;
        return defValue;
    }

    public Option.TYPE getType() {
        return type;
    }

    String getDefaultValue() {
        if (defValue == null) return "";
        return defValue;
    }
}
