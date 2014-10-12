package pl.mobilization.organizuj.to;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by marekdef on 12.10.14.
 */
public class DataStorage {
    private static final String AUTHENTICITY_TOKEN = "AUTHENTICITY_TOKEN";
    private static final String CSRF = "CSRF";
    private static final String NEW_RELIC_ID = "NEW_RELIC_ID";
    private static final String COOKIES = "COOKIES";
    private static final Logger LOGGER = LoggerFactory.getLogger(DataStorage.class);

    private static boolean invalid = false;
    private final SharedPreferences sharedPreferences;
    private final SharedPreferences sharedPreferencesCookies;

    private String csrf;
    private String newRelicId;
    private Map<String, String>  cookies = new HashMap<String, String>();;
    private String authenticityToken;

    public DataStorage(Context context) {
        sharedPreferences = context.getSharedPreferences("Organizuj.to", Context.MODE_PRIVATE);
        sharedPreferencesCookies = context.getSharedPreferences("Organizuj.to.cookies", Context.MODE_PRIVATE);

        authenticityToken = sharedPreferences.getString(AUTHENTICITY_TOKEN, null);
        csrf = sharedPreferences.getString(CSRF, null);
        newRelicId = sharedPreferences.getString(NEW_RELIC_ID, null);

        convertSP2Cookies();
    }

    private void convertSP2Cookies() {
        Map<String, ?> all = sharedPreferencesCookies.getAll();

        for(Map.Entry<String, ?> set: all.entrySet()) {
            cookies.put(set.getKey(),String.valueOf(set.getValue()));
        }
    }

    public void invalidate() {
        sharedPreferences.edit().clear().commit();
    }

    private void storeValue(String key, String value) {
        sharedPreferences.edit().putString(key, value).commit();
    }

    public void storeToken(String authenticity_token) {
        this.authenticityToken = authenticity_token;
        storeValue(AUTHENTICITY_TOKEN, authenticity_token);
    }

    public void storeCSRF(String csrf) {
        this.csrf = csrf;
        storeValue(CSRF, csrf);
    }

    public void storeRelic(String newRelicId) {
        this.newRelicId = newRelicId;
        storeValue(NEW_RELIC_ID, newRelicId);
    }

    public void storeCookies(Map<String, String> cookies) {
        this.cookies = cookies;
        SharedPreferences.Editor edit = this.sharedPreferencesCookies.edit();
        edit.clear();
        for(Map.Entry<String, String> entry : cookies.entrySet()) {
            edit.putString(entry.getKey(), entry.getValue());
        }
        edit.commit();
    }

    public Map<String, String> getCookies() {
        return cookies;
    }

    public String getNewRelicId() {
        return newRelicId;
    }

    public String getCsrf() {
        return csrf;
    }

    public String getAuthenticityToken() {
        return authenticityToken;
    }
}
