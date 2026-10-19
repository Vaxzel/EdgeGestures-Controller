package com.custom.edgegestureslock.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import com.custom.edgegestureslock.data.RuleManager;

public class RuleProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if ("getRules".equals(method)) {
            RuleManager rm = RuleManager.getInstance(getContext());
            rm.loadRules();
            Bundle res = new Bundle();
            res.putString("rules_json", rm.toJsonString());
            return res;
        } else if ("setRule".equals(method) && arg != null && extras != null) {
            boolean left = extras.getBoolean("left", false);
            boolean right = extras.getBoolean("right", false);
            boolean bottom = extras.getBoolean("bottom", false);
            RuleManager rm = RuleManager.getInstance(getContext());
            rm.setRule(arg, new com.custom.edgegestureslock.model.RuleModel(left, right, bottom));
            Bundle res = new Bundle();
            res.putBoolean("success", true);
            return res;
        }
        return super.call(method, arg, extras);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/vnd.com.custom.edgegestureslock.rules";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
