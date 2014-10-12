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

    private String authenticity_token;
    private String csrf;
    private String newRelicId;
    private Map<String, String> cookies;
    private String authenticityToken;

    public DataStorage(Context context) {
        sharedPreferences = context.getSharedPreferences("Organizuj.to", Context.MODE_PRIVATE);

        authenticity_token = sharedPreferences.getString(AUTHENTICITY_TOKEN, null);
        csrf = sharedPreferences.getString(CSRF, null);
        newRelicId = sharedPreferences.getString(NEW_RELIC_ID, null);

        String cookieserialized = sharedPreferences.getString(COOKIES, null);

        if(!TextUtils.isEmpty(cookieserialized))
        try {
            ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(cookieserialized.getBytes()));
            cookies = (Map<String, String>) objectInputStream.readObject();
            objectInputStream.close();
        } catch (IOException e) {
            LOGGER.error("IOException while deserializing cookies", e);
        } catch (ClassNotFoundException e) {
            LOGGER.error("ClassNotFoundException while deserializing cookies", e);
        } catch (ClassCastException e) {
            LOGGER.error("ClassNotFoundException while deserializing cookies", e);
        }
    }

    public void invalidate() {
        sharedPreferences.edit().clear().commit();
    }

    private void storeValue(String key, String value) {
        sharedPreferences.edit().putString(key, value).commit();
    }

    public void storeToken(String value) {
        storeValue(AUTHENTICITY_TOKEN, value);
    }

    public void storeCSRF(String value) {
        storeValue(CSRF, value);
    }

    public void storeRelic(String value) {
        storeValue(NEW_RELIC_ID, value);
    }

    public void storeCookies(Map<String, String> value) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(value);
            oos.close();
            bos.close();
        } catch (IOException e) {
            LOGGER.error("IOException while serializing cookies", e);
        }
        storeValue(COOKIES, bos.toString());
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
