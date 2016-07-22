package com.leanote.android.util;

import android.text.TextUtils;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.leanote.android.Leanote;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;


public class ABTestingUtils {
    private static Map<String, Boolean> sRemoteControlMap;

    // URL where remote control values are stored
    private final static String REMOTE_CONTROL_URL = "http://api.wordpress.org/androidapp/feedback-check/1.0/";

    // Update it max every 6 hours if the application is not killed
    private final static int SECONDS_BETWEEN_TWO_UPDATES = 60 * 60 * 6;

    public enum Feature {
        HELPSHIFT
    }

    public static boolean isFeatureEnabled(Feature feature) {
        switch (feature) {
            case HELPSHIFT:
                return getBooleanRemoteControlField("feedback-enabled", false);
        }
        return false;
    }

    private static boolean getBooleanRemoteControlField(String fieldName, boolean defaultValue) {
        if (sRemoteControlMap != null) {
            // async refresh current data
            fetchRemoteData.runIfNotLimited();
            // return current value (we don't need to wait for the data to be refreshed)
            String value = MapUtils.getMapStr(sRemoteControlMap, fieldName);
            return value.equalsIgnoreCase("true");
        } else {
            // if it's null, request it and return default value
            fetchRemoteData.runIfNotLimited();
            return defaultValue;
        }
    }

    private static RateLimitedTask fetchRemoteData = new RateLimitedTask(SECONDS_BETWEEN_TWO_UPDATES) {
        @Override
        protected boolean run() {
            fetchRemoteControlData();
            return true;
        }
    };

    private static void fetchRemoteControlData() {
        Response.Listener<String> listener = new Response.Listener<String>() {
            public void onResponse(String response) {
                if (TextUtils.isEmpty(response)){
                    return;
                }
                try {
                    JSONObject object = new JSONObject(response);
                    sRemoteControlMap = new HashMap<>();
                    sRemoteControlMap.put("feedback-enabled", object.getBoolean("feedback-enabled"));
                } catch (JSONException e) {
                    AppLog.e(AppLog.T.UTILS, e);
                }
            }
        };
        Response.ErrorListener errorListener = new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                AppLog.w(AppLog.T.UTILS, "Unable to fetch: " + REMOTE_CONTROL_URL);
                AppLog.e(AppLog.T.UTILS, volleyError);
            }
        };
        StringRequest req = new StringRequest(Request.Method.GET, REMOTE_CONTROL_URL, listener, errorListener);
        if (Leanote.requestQueue != null) {
            Leanote.requestQueue.add(req);
        }
    }
}