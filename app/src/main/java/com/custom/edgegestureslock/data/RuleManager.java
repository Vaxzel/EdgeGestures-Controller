package com.custom.edgegestureslock.data;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import com.custom.edgegestureslock.model.RuleModel;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class RuleManager {
    public static final String ACTION_RELOAD_RULES = "com.custom.edgegestureslock.ACTION_RELOAD_RULES";
    public static final String AUTHORITY = "com.custom.edgegestureslock.provider";
    public static final String FALLBACK_FILE_TMP = "/data/local/tmp/edge_rules.json";
    
    private static RuleManager sInstance;
    private final Context mContext;
    private final Map<String, RuleModel> mRules = new HashMap<>();

    public static synchronized RuleManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new RuleManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private RuleManager(Context context) {
        this.mContext = context;
        loadRules();
    }

    public synchronized Map<String, RuleModel> getAllRules() {
        return new HashMap<>(mRules);
    }

    public synchronized RuleModel getRule(String packageName) {
        RuleModel r = mRules.get(packageName);
        return r != null ? r : new RuleModel();
    }

    public synchronized void setRule(String packageName, RuleModel rule) {
        if (rule == null || !rule.hasActiveRules()) {
            mRules.remove(packageName);
        } else {
            mRules.put(packageName, rule);
        }
        saveRules();
    }

    public synchronized void removeRule(String packageName) {
        mRules.remove(packageName);
        saveRules();
    }

    public synchronized String toJsonString() {
        JSONObject root = new JSONObject();
        try {
            JSONObject rulesObj = new JSONObject();
            for (Map.Entry<String, RuleModel> entry : mRules.entrySet()) {
                rulesObj.put(entry.getKey(), entry.getValue().toJsonObject());
            }
            root.put("rules", rulesObj);
        } catch (Exception ignored) {}
        return root.toString();
    }

    public synchronized void fromJsonString(String jsonStr) {
        mRules.clear();
        if (jsonStr == null || jsonStr.trim().isEmpty()) return;
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject rulesObj = root.optJSONObject("rules");
            if (rulesObj != null) {
                Iterator<String> keys = rulesObj.keys();
                while (keys.hasNext()) {
                    String pkg = keys.next();
                    JSONObject rObj = rulesObj.optJSONObject(pkg);
                    if (rObj != null) {
                        RuleModel model = RuleModel.fromJsonObject(rObj);
                        if (model.hasActiveRules()) {
                            mRules.put(pkg, model);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public synchronized void loadRules() {
        // 1. Try internal files
        File internalFile = new File(mContext.getFilesDir(), "edge_rules.json");
        String content = readFile(internalFile);

        // 2. Fallback to /data/local/tmp
        if (content == null || content.isEmpty()) {
            content = readFile(new File(FALLBACK_FILE_TMP));
        }

        // 3. Fallback to Download folder
        if (content == null || content.isEmpty()) {
            File dlFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "edge_rules.json");
            content = readFile(dlFile);
        }

        if (content != null && !content.isEmpty()) {
            fromJsonString(content);
        }
    }

    public synchronized void saveRules() {
        final String jsonStr = toJsonString();

        // 1. Save to internal app files
        File internalFile = new File(mContext.getFilesDir(), "edge_rules.json");
        writeFile(internalFile, jsonStr);

        // 2. Direct write to /data/local/tmp if possible
        writeFile(new File(FALLBACK_FILE_TMP), jsonStr);

        // 3. Root write to Edge Gestures app directory & /data/local/tmp
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File intFile = new File(mContext.getFilesDir(), "edge_rules.json");
                    String cmd = "mkdir -p /data/data/com.ss.edgegestures/files && " +
                                 "cp " + intFile.getAbsolutePath() + " /data/data/com.ss.edgegestures/files/edge_rules.json && " +
                                 "chmod 666 /data/data/com.ss.edgegestures/files/edge_rules.json && " +
                                 "cp " + intFile.getAbsolutePath() + " /data/local/tmp/edge_rules.json && " +
                                 "chmod 777 /data/local/tmp/edge_rules.json";
                    Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                    p.waitFor();
                } catch (Throwable ignored) {}
            }
        }).start();

        // 4. Save to Download folder
        try {
            File dlFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "edge_rules.json");
            writeFile(dlFile, jsonStr);
        } catch (Throwable ignored) {}

        // 5. Send broadcast carrying full JSON payload directly to Edge Gestures
        try {
            Intent broadcast = new Intent(ACTION_RELOAD_RULES);
            broadcast.setPackage("com.ss.edgegestures");
            broadcast.addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            broadcast.putExtra("rules_json", jsonStr);
            mContext.sendBroadcast(broadcast);
        } catch (Throwable ignored) {}
    }

    private static String readFile(File file) {
        if (!file.exists() || !file.canRead()) return null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeFile(File file, String data) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data.getBytes("UTF-8"));
                fos.flush();
            }
            // Make readable for other processes
            file.setReadable(true, false);
            file.setWritable(true, false);
        } catch (Exception ignored) {}
    }
}
