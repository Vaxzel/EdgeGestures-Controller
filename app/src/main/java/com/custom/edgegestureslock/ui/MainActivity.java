package com.custom.edgegestureslock.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.custom.edgegestureslock.data.RuleManager;
import com.custom.edgegestureslock.model.AppItem;
import com.custom.edgegestureslock.model.RuleModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private static final String PREF_NAME = "ui_settings";
    private static final String KEY_SHOW_SYSTEM = "show_system";
    private static final String KEY_THEME_MODE = "theme_mode"; // 0: Auto, 1: Dark, 2: Light

    private RuleManager mRuleManager;
    private AppListAdapter mAdapter;
    private final List<AppItem> mAllApps = new ArrayList<>();
    private final List<AppItem> mDisplayedApps = new ArrayList<>();

    private boolean mShowSystem = false;
    private int mThemeMode = 0; // 0: Auto
    private boolean mIsDarkTheme = true;
    private String mCurrentSearchQuery = "";

    private ListView mListView;
    private ProgressBar mProgressBar;
    private TextView mStatusHeaderTv;
    private TextView mFilterSystemBtn;
    private TextView mThemeBtn;
    private EditText mSearchInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRuleManager = RuleManager.getInstance(this);

        SharedPreferences sp = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        mShowSystem = sp.getBoolean(KEY_SHOW_SYSTEM, false);
        mThemeMode = sp.getInt(KEY_THEME_MODE, 0);
        resolveTheme();

        setContentView(buildMainLayout());
        loadInstalledApps();
    }

    private void resolveTheme() {
        if (mThemeMode == 1) {
            mIsDarkTheme = true;
        } else if (mThemeMode == 2) {
            mIsDarkTheme = false;
        } else {
            int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            mIsDarkTheme = (nightMode == Configuration.UI_MODE_NIGHT_YES);
        }
    }

    private View buildMainLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(mIsDarkTheme ? Color.parseColor("#121212") : Color.parseColor("#F5F7FA"));

        // Header Card
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        int padH = dpToPx(16);
        header.setPadding(padH, padH + dpToPx(8), padH, padH);
        header.setBackgroundColor(mIsDarkTheme ? Color.parseColor("#1E1E1E") : Color.parseColor("#FFFFFF"));

        // Title Row
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleTv = new TextView(this);
        titleTv.setText("Edge Gestures Config");
        titleTv.setTextSize(20);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setTextColor(mIsDarkTheme ? Color.parseColor("#FFFFFF") : Color.parseColor("#111111"));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        titleTv.setLayoutParams(titleLp);
        titleRow.addView(titleTv);

        // Theme Toggle Button
        mThemeBtn = createHeaderButton(getThemeBtnText(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mThemeMode = (mThemeMode + 1) % 3;
                getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_THEME_MODE, mThemeMode).apply();
                resolveTheme();
                recreate();
            }
        });
        titleRow.addView(mThemeBtn);

        header.addView(titleRow);

        // Subtitle & Status
        mStatusHeaderTv = new TextView(this);
        mStatusHeaderTv.setText("Memuat daftar aplikasi...");
        mStatusHeaderTv.setTextSize(12);
        mStatusHeaderTv.setTextColor(mIsDarkTheme ? Color.parseColor("#9E9E9E") : Color.parseColor("#757575"));
        header.addView(mStatusHeaderTv);

        // Search Bar
        mSearchInput = new EditText(this);
        mSearchInput.setHint("Cari nama aplikasi atau package...");
        mSearchInput.setHintTextColor(mIsDarkTheme ? Color.parseColor("#616161") : Color.parseColor("#9E9E9E"));
        mSearchInput.setTextColor(mIsDarkTheme ? Color.parseColor("#FFFFFF") : Color.parseColor("#212121"));
        mSearchInput.setTextSize(14);
        mSearchInput.setSingleLine(true);
        int padSearch = dpToPx(10);
        mSearchInput.setPadding(padSearch, padSearch, padSearch, padSearch);

        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setCornerRadius(dpToPx(10));
        searchBg.setColor(mIsDarkTheme ? Color.parseColor("#2A2A2A") : Color.parseColor("#F0F2F5"));
        searchBg.setStroke(dpToPx(1), mIsDarkTheme ? Color.parseColor("#383838") : Color.parseColor("#E0E0E0"));
        mSearchInput.setBackground(searchBg);

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        searchLp.setMargins(0, dpToPx(12), 0, dpToPx(8));
        mSearchInput.setLayoutParams(searchLp);
        mSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mCurrentSearchQuery = s.toString().trim();
                applyFilterAndSort();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        header.addView(mSearchInput);

        // Filter Controls Row
        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);

        mFilterSystemBtn = createPillButton(
            mShowSystem ? "Mode: Semua Aplikasi (User + System)" : "Mode: Aplikasi User Saja",
            mShowSystem,
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mShowSystem = !mShowSystem;
                    getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOW_SYSTEM, mShowSystem).apply();
                    mFilterSystemBtn.setText(mShowSystem ? "Mode: Semua Aplikasi (User + System)" : "Mode: Aplikasi User Saja");
                    updatePillButtonState(mFilterSystemBtn, mShowSystem);
                    applyFilterAndSort();
                }
            }
        );
        filterRow.addView(mFilterSystemBtn);

        header.addView(filterRow);
        root.addView(header);

        // Content Frame (List + Progress)
        FrameLayout contentFrame = new FrameLayout(this);
        LinearLayout.LayoutParams frameLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1.0f
        );
        contentFrame.setLayoutParams(frameLp);

        mListView = new ListView(this);
        mListView.setDivider(null);
        mListView.setDividerHeight(0);
        mListView.setSelector(android.R.color.transparent);
        mAdapter = new AppListAdapter(this, mIsDarkTheme, new AppListAdapter.OnRuleChangeListener() {
            @Override
            public void onRuleChanged(AppItem item) {
                if (item.hasActiveRules()) {
                    mRuleManager.setRule(item.packageName, item.rules);
                } else {
                    mRuleManager.removeRule(item.packageName);
                }
                updateStatusHeaderCount();
            }
        });
        mListView.setAdapter(mAdapter);
        contentFrame.addView(mListView);

        mProgressBar = new ProgressBar(this);
        FrameLayout.LayoutParams pbLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        );
        mProgressBar.setLayoutParams(pbLp);
        contentFrame.addView(mProgressBar);

        root.addView(contentFrame);
        return root;
    }

    private TextView createHeaderButton(String text, View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(mIsDarkTheme ? Color.parseColor("#64B5F6") : Color.parseColor("#1976D2"));
        tv.setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dpToPx(8));
        bg.setColor(mIsDarkTheme ? Color.parseColor("#263238") : Color.parseColor("#E3F2FD"));
        tv.setBackground(bg);
        tv.setOnClickListener(listener);
        return tv;
    }

    private TextView createPillButton(String text, boolean active, View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));
        updatePillButtonState(tv, active);
        tv.setOnClickListener(listener);
        return tv;
    }

    private void updatePillButtonState(TextView tv, boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dpToPx(16));
        if (active) {
            bg.setColor(Color.parseColor("#1565C0"));
            tv.setTextColor(Color.WHITE);
        } else {
            bg.setColor(mIsDarkTheme ? Color.parseColor("#2E2E2E") : Color.parseColor("#E0E0E0"));
            tv.setTextColor(mIsDarkTheme ? Color.parseColor("#BDBDBD") : Color.parseColor("#424242"));
        }
        tv.setBackground(bg);
    }

    private String getThemeBtnText() {
        if (mThemeMode == 1) return "Tema: Gelap";
        if (mThemeMode == 2) return "Tema: Terang";
        return "Tema: Auto";
    }

    private void loadInstalledApps() {
        mProgressBar.setVisibility(View.VISIBLE);
        new AsyncTask<Void, Void, List<AppItem>>() {
            @Override
            protected List<AppItem> doInBackground(Void... voids) {
                List<AppItem> list = new ArrayList<>();
                PackageManager pm = getPackageManager();
                List<PackageInfo> installedPackages = pm.getInstalledPackages(0);

                mRuleManager.loadRules();

                for (PackageInfo pi : installedPackages) {
                    if (pi.applicationInfo == null) continue;
                    boolean isSystem = (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    String appName = pi.applicationInfo.loadLabel(pm).toString();
                    RuleModel rule = mRuleManager.getRule(pi.packageName);
                    list.add(new AppItem(
                        pi.packageName,
                        appName,
                        isSystem,
                        pi.applicationInfo.loadIcon(pm),
                        rule
                    ));
                }
                return list;
            }

            @Override
            protected void onPostExecute(List<AppItem> result) {
                mProgressBar.setVisibility(View.GONE);
                mAllApps.clear();
                if (result != null) {
                    mAllApps.addAll(result);
                }
                applyFilterAndSort();
            }
        }.execute();
    }

    private void applyFilterAndSort() {
        mDisplayedApps.clear();
        String q = mCurrentSearchQuery.toLowerCase();

        int configuredCount = 0;
        for (AppItem item : mAllApps) {
            // Check system filter
            if (!mShowSystem && item.isSystem && !item.hasActiveRules()) {
                continue;
            }

            // Check search filter
            if (!q.isEmpty()) {
                boolean matchName = item.appName != null && item.appName.toLowerCase().contains(q);
                boolean matchPkg = item.packageName != null && item.packageName.toLowerCase().contains(q);
                if (!matchName && !matchPkg) {
                    continue;
                }
            }

            if (item.hasActiveRules()) {
                configuredCount++;
            }
            mDisplayedApps.add(item);
        }

        // Sort: Configured apps ON TOP, then alphabetically by AppName
        Collections.sort(mDisplayedApps, new Comparator<AppItem>() {
            @Override
            public int compare(AppItem o1, AppItem o2) {
                boolean c1 = o1.hasActiveRules();
                boolean c2 = o2.hasActiveRules();
                if (c1 && !c2) return -1;
                if (!c1 && c2) return 1;
                return o1.appName.compareToIgnoreCase(o2.appName);
            }
        });

        mStatusHeaderTv.setText(mDisplayedApps.size() + " aplikasi ditampilkan (" + configuredCount + " aktif terkonfigurasi)");
        mAdapter.updateData(mDisplayedApps);
    }

    private void updateStatusHeaderCount() {
        int configuredCount = 0;
        for (AppItem item : mDisplayedApps) {
            if (item.hasActiveRules()) {
                configuredCount++;
            }
        }
        mStatusHeaderTv.setText(mDisplayedApps.size() + " aplikasi ditampilkan (" + configuredCount + " aktif terkonfigurasi)");
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}
