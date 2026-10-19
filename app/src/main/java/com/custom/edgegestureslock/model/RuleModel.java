package com.custom.edgegestureslock.model;

import org.json.JSONException;
import org.json.JSONObject;

public class RuleModel {
    public boolean disableLeft;
    public boolean disableRight;
    public boolean disableBottom;

    public RuleModel() {
        this.disableLeft = false;
        this.disableRight = false;
        this.disableBottom = false;
    }

    public RuleModel(boolean left, boolean right, boolean bottom) {
        this.disableLeft = left;
        this.disableRight = right;
        this.disableBottom = bottom;
    }

    public boolean hasActiveRules() {
        return disableLeft || disableRight || disableBottom;
    }

    public JSONObject toJsonObject() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("left", disableLeft);
            obj.put("right", disableRight);
            obj.put("bottom", disableBottom);
        } catch (JSONException ignored) {}
        return obj;
    }

    public static RuleModel fromJsonObject(JSONObject obj) {
        if (obj == null) return new RuleModel();
        return new RuleModel(
            obj.optBoolean("left", false),
            obj.optBoolean("right", false),
            obj.optBoolean("bottom", false)
        );
    }
}
