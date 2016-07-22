package com.leanote.android.ui.main;

import android.content.Context;
import android.support.annotation.DrawableRes;
import android.support.annotation.StringRes;
import android.support.design.widget.TabLayout;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

import com.leanote.android.R;

/**
 * Created by binnchx on 10/13/15.
 */
public class LeaMainTabLayout extends TabLayout {

    public LeaMainTabLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LeaMainTabLayout(Context context) {
        super(context);
    }

    public LeaMainTabLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void createTabs() {
        addTab(R.drawable.main_tab_note, R.string.tabbar_note);
        addTab(R.drawable.main_tab_post, R.string.tabbar_post);
        addTab(R.drawable.main_tab_category, R.string.tabbar_category);
        addTab(R.drawable.main_tab_me, R.string.tabbar_accessibility_label_me);
    }

    private void addTab(@DrawableRes int iconId, @StringRes int contentDescriptionId) {
        View customView = LayoutInflater.from(getContext()).inflate(R.layout.tab_icon, this, false);

        ImageView icon = (ImageView) customView.findViewById(R.id.tab_icon);
        icon.setImageResource(iconId);

        addTab(newTab().setCustomView(customView).setContentDescription(contentDescriptionId));
    }

}
