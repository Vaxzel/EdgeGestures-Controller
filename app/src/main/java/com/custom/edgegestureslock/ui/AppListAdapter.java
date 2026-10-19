package com.custom.edgegestureslock.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.custom.edgegestureslock.model.AppItem;

import java.util.ArrayList;
import java.util.List;

public class AppListAdapter extends BaseAdapter {
    public interface OnRuleChangeListener {
        void onRuleChanged(AppItem item);
    }

    private final Context mContext;
    private final List<AppItem> mItems = new ArrayList<>();
    private final boolean mIsDarkMode;
    private final OnRuleChangeListener mRuleChangeListener;

    public AppListAdapter(Context context, boolean isDarkMode, OnRuleChangeListener listener) {
        this.mContext = context;
        this.mIsDarkMode = isDarkMode;
        this.mRuleChangeListener = listener;
    }

    public void updateData(List<AppItem> newItems) {
        mItems.clear();
        if (newItems != null) {
            mItems.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public AppItem getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ViewHolder holder;
        if (convertView == null) {
            holder = new ViewHolder();
            convertView = createItemView(holder);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final AppItem item = getItem(position);

        holder.appName.setText(item.appName);
        holder.pkgName.setText(item.packageName);

        if (item.icon != null) {
            holder.appIcon.setImageDrawable(item.icon);
        } else {
            holder.appIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        // Arrow indicator
        holder.arrowTv.setText(item.isExpanded ? "▲" : "▼");
        holder.arrowTv.setTextColor(item.isExpanded
            ? (mIsDarkMode ? Color.parseColor("#64B5F6") : Color.parseColor("#1976D2"))
            : (mIsDarkMode ? Color.parseColor("#757575") : Color.parseColor("#BDBDBD")));

        // Badges
        updateBadges(holder.badgeContainer, item);

        // Expandable Panel Visibility
        holder.expandablePanel.setVisibility(item.isExpanded ? View.VISIBLE : View.GONE);

        // Checkbox listeners setup (detach first to avoid trigger during recycling)
        holder.cbBottom.setOnCheckedChangeListener(null);
        holder.cbLeft.setOnCheckedChangeListener(null);
        holder.cbRight.setOnCheckedChangeListener(null);

        holder.cbBottom.setChecked(item.rules.disableBottom);
        holder.cbLeft.setChecked(item.rules.disableLeft);
        holder.cbRight.setChecked(item.rules.disableRight);

        CompoundButton.OnCheckedChangeListener checkListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (buttonView == holder.cbBottom) {
                    item.rules.disableBottom = isChecked;
                } else if (buttonView == holder.cbLeft) {
                    item.rules.disableLeft = isChecked;
                } else if (buttonView == holder.cbRight) {
                    item.rules.disableRight = isChecked;
                }

                // Update UI badges & card highlight immediately
                updateBadges(holder.badgeContainer, item);
                updateCardStyle(holder.cardLayout, item);

                // Auto-save via callback
                if (mRuleChangeListener != null) {
                    mRuleChangeListener.onRuleChanged(item);
                }
            }
        };

        holder.cbBottom.setOnCheckedChangeListener(checkListener);
        holder.cbLeft.setOnCheckedChangeListener(checkListener);
        holder.cbRight.setOnCheckedChangeListener(checkListener);

        // Reset button
        holder.resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                item.rules.disableBottom = false;
                item.rules.disableLeft = false;
                item.rules.disableRight = false;

                holder.cbBottom.setChecked(false);
                holder.cbLeft.setChecked(false);
                holder.cbRight.setChecked(false);

                updateBadges(holder.badgeContainer, item);
                updateCardStyle(holder.cardLayout, item);

                if (mRuleChangeListener != null) {
                    mRuleChangeListener.onRuleChanged(item);
                }
            }
        });

        // Header click toggles expand/collapse
        holder.headerLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                item.isExpanded = !item.isExpanded;
                notifyDataSetChanged();
            }
        });

        updateCardStyle(holder.cardLayout, item);
        return convertView;
    }

    private void updateBadges(LinearLayout container, AppItem item) {
        container.removeAllViews();
        if (item.hasActiveRules()) {
            if (item.rules.disableBottom) {
                container.addView(createBadge("BAWAH", Color.parseColor("#E65100"), Color.parseColor("#FFE0B2")));
            }
            if (item.rules.disableLeft) {
                container.addView(createBadge("KIRI", Color.parseColor("#1565C0"), Color.parseColor("#BBDEFB")));
            }
            if (item.rules.disableRight) {
                container.addView(createBadge("KANAN", Color.parseColor("#00695C"), Color.parseColor("#B2DFDB")));
            }
        } else {
            TextView normalTv = new TextView(mContext);
            normalTv.setText("Semua Sisi Aktif");
            normalTv.setTextSize(11);
            normalTv.setTextColor(mIsDarkMode ? Color.parseColor("#757575") : Color.parseColor("#9E9E9E"));
            container.addView(normalTv);
        }
    }

    private void updateCardStyle(View card, AppItem item) {
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dpToPx(14));
        if (item.hasActiveRules()) {
            if (mIsDarkMode) {
                cardBg.setColor(Color.parseColor("#162332"));
                cardBg.setStroke(dpToPx(1.5f), Color.parseColor("#2196F3"));
            } else {
                cardBg.setColor(Color.parseColor("#EBF3FB"));
                cardBg.setStroke(dpToPx(1.5f), Color.parseColor("#90CAF9"));
            }
        } else {
            cardBg.setColor(mIsDarkMode ? Color.parseColor("#1E1E1E") : Color.parseColor("#FFFFFF"));
            cardBg.setStroke(dpToPx(1), mIsDarkMode ? Color.parseColor("#2D2D2D") : Color.parseColor("#E0E0E0"));
        }
        card.setBackground(cardBg);
    }

    private View createItemView(ViewHolder holder) {
        LinearLayout card = new LinearLayout(mContext);
        card.setOrientation(LinearLayout.VERTICAL);
        holder.cardLayout = card;

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardLp.setMargins(dpToPx(12), dpToPx(5), dpToPx(12), dpToPx(5));
        card.setLayoutParams(cardLp);

        // 1. Header Layout (Clickable to Expand/Collapse)
        LinearLayout header = new LinearLayout(mContext);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dpToPx(12);
        header.setPadding(pad, pad, pad, pad);
        holder.headerLayout = header;

        // App Icon
        holder.appIcon = new ImageView(mContext);
        int iconSize = dpToPx(44);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.setMargins(0, 0, dpToPx(12), 0);
        holder.appIcon.setLayoutParams(iconLp);
        header.addView(holder.appIcon);

        // Text details column
        LinearLayout textCol = new LinearLayout(mContext);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textColLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textCol.setLayoutParams(textColLp);

        holder.appName = new TextView(mContext);
        holder.appName.setTextSize(15);
        holder.appName.setTypeface(Typeface.DEFAULT_BOLD);
        holder.appName.setTextColor(mIsDarkMode ? Color.parseColor("#EEEEEE") : Color.parseColor("#212121"));
        textCol.addView(holder.appName);

        holder.pkgName = new TextView(mContext);
        holder.pkgName.setTextSize(11);
        holder.pkgName.setTextColor(mIsDarkMode ? Color.parseColor("#9E9E9E") : Color.parseColor("#757575"));
        textCol.addView(holder.pkgName);

        // Badge Container
        holder.badgeContainer = new LinearLayout(mContext);
        holder.badgeContainer.setOrientation(LinearLayout.HORIZONTAL);
        holder.badgeContainer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        badgeLp.setMargins(0, dpToPx(4), 0, 0);
        holder.badgeContainer.setLayoutParams(badgeLp);
        textCol.addView(holder.badgeContainer);

        header.addView(textCol);

        // Arrow expand indicator
        holder.arrowTv = new TextView(mContext);
        holder.arrowTv.setTextSize(14);
        holder.arrowTv.setPadding(dpToPx(8), dpToPx(8), dpToPx(4), dpToPx(8));
        header.addView(holder.arrowTv);

        card.addView(header);

        // 2. Expandable Panel (Collapse Section)
        LinearLayout panel = new LinearLayout(mContext);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dpToPx(16), 0, dpToPx(16), dpToPx(14));
        holder.expandablePanel = panel;

        // Divider line
        View divider = new View(mContext);
        divider.setBackgroundColor(mIsDarkMode ? Color.parseColor("#2E3846") : Color.parseColor("#E2E8F0"));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1));
        divLp.setMargins(0, 0, 0, dpToPx(10));
        divider.setLayoutParams(divLp);
        panel.addView(divider);

        // Instruction label
        TextView guideTv = new TextView(mContext);
        guideTv.setText("Nonaktifkan gestur saat aplikasi ini aktif (Tersimpan otomatis):");
        guideTv.setTextSize(12);
        guideTv.setTypeface(Typeface.DEFAULT_BOLD);
        guideTv.setTextColor(mIsDarkMode ? Color.parseColor("#90CAF9") : Color.parseColor("#1565C0"));
        LinearLayout.LayoutParams guideLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        guideLp.setMargins(0, 0, 0, dpToPx(6));
        guideTv.setLayoutParams(guideLp);
        panel.addView(guideTv);

        // Checkboxes: Bottom, Left, Right
        holder.cbBottom = createSideCheckBox("Nonaktifkan Sisi Bawah (Bottom Edge)", Color.parseColor("#FB8C00"));
        panel.addView(holder.cbBottom);

        holder.cbLeft = createSideCheckBox("Nonaktifkan Sisi Kiri (Left Edge)", Color.parseColor("#1E88E5"));
        panel.addView(holder.cbLeft);

        holder.cbRight = createSideCheckBox("Nonaktifkan Sisi Kanan (Right Edge)", Color.parseColor("#00897B"));
        panel.addView(holder.cbRight);

        // Action row: Reset button
        LinearLayout actionRow = new LinearLayout(mContext);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams actionRowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionRowLp.setMargins(0, dpToPx(8), 0, 0);
        actionRow.setLayoutParams(actionRowLp);

        holder.resetBtn = new TextView(mContext);
        holder.resetBtn.setText("Reset / Aktifkan Semua Sisi");
        holder.resetBtn.setTextSize(11);
        holder.resetBtn.setTypeface(Typeface.DEFAULT_BOLD);
        holder.resetBtn.setTextColor(mIsDarkMode ? Color.parseColor("#EF5350") : Color.parseColor("#D32F2F"));
        holder.resetBtn.setPadding(dpToPx(10), dpToPx(5), dpToPx(10), dpToPx(5));

        GradientDrawable resetBg = new GradientDrawable();
        resetBg.setCornerRadius(dpToPx(6));
        resetBg.setColor(mIsDarkMode ? Color.parseColor("#2A1C1C") : Color.parseColor("#FFEBEE"));
        holder.resetBtn.setBackground(resetBg);

        actionRow.addView(holder.resetBtn);
        panel.addView(actionRow);

        card.addView(panel);
        return card;
    }

    private CheckBox createSideCheckBox(String text, int accentColor) {
        CheckBox cb = new CheckBox(mContext);
        cb.setText(text);
        cb.setTextSize(13);
        cb.setTextColor(mIsDarkMode ? Color.parseColor("#E0E0E0") : Color.parseColor("#333333"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dpToPx(2), 0, dpToPx(2));
        cb.setLayoutParams(lp);
        return cb;
    }

    private View createBadge(String text, int textColor, int bgColor) {
        TextView tv = new TextView(mContext);
        tv.setText(text);
        tv.setTextSize(10);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(textColor);
        tv.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dpToPx(6));
        bg.setColor(bgColor);
        tv.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, dpToPx(4), 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    private int dpToPx(float dp) {
        float density = mContext.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    static class ViewHolder {
        LinearLayout cardLayout;
        LinearLayout headerLayout;
        ImageView appIcon;
        TextView appName;
        TextView pkgName;
        TextView arrowTv;
        LinearLayout badgeContainer;
        LinearLayout expandablePanel;
        CheckBox cbBottom;
        CheckBox cbLeft;
        CheckBox cbRight;
        TextView resetBtn;
    }
}
