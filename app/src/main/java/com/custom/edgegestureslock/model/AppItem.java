package com.custom.edgegestureslock.model;

import android.graphics.drawable.Drawable;

public class AppItem {
    public String packageName;
    public String appName;
    public boolean isSystem;
    public Drawable icon;
    public RuleModel rules;
    public boolean isExpanded;

    public AppItem(String packageName, String appName, boolean isSystem, Drawable icon, RuleModel rules) {
        this.packageName = packageName;
        this.appName = appName;
        this.isSystem = isSystem;
        this.icon = icon;
        this.rules = rules != null ? rules : new RuleModel();
    }

    public boolean hasActiveRules() {
        return rules != null && rules.hasActiveRules();
    }
}
