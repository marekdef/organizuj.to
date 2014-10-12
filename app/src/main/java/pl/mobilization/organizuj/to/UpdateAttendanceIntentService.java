package pl.mobilization.organizuj.to;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import com.google.gson.Gson;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import pl.mobilization.organizuj.to.json.Attendee;
import pl.mobilization.organizuj.to.json.Guest;

import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_ID;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_ISPRESENT;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_NEEDSUPDATE;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.TABLE_NAME;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class UpdateAttendanceIntentService extends IntentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateAttendanceIntentService.class);
    // TODO: Rename actions, choose action names that describe tasks that this
    // IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS
    private static final String ACTION_CHECKIN = "pl.mobilization.organizuj.to.action.UPDATE";

    private AttendeesDBOpenHelper attendeesDBOpenHelper;
    private DataStorage dataStorage;
    private SQLiteDatabase writableDatabase;

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    // TODO: Customize helper method
    public static void startActionCheckin(Context context) {
        Intent intent = new Intent(context, UpdateAttendanceIntentService.class);
        intent.setAction(ACTION_CHECKIN);
        context.startService(intent);
    }
    public UpdateAttendanceIntentService() {
        super("UpdateAttendanceIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_CHECKIN.equals(action)) {
                handleActionCheckIn();
            }
        }
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private void handleActionCheckIn() {
        Cursor cursor = writableDatabase.query(TABLE_NAME,
                new String[]{COLUMN_ID, COLUMN_ISPRESENT},
                COLUMN_NEEDSUPDATE + " = 1 ", null, null, null, null);

        int count = cursor.getCount();
        if (count == 0) {
            LOGGER.debug("handleActionCheckIn No items to checkin");
            return;
        }

        Map<Integer, Boolean> incomingRequests = new HashMap<Integer, Boolean>(count);

        while(cursor.moveToNext()) {
            int id = cursor.getInt(0);
            int present = cursor.getInt(1);
            incomingRequests.put(id, present == 0 ? false : true);

            cursor.moveToNext();
        }

        cursor.close();

        String authenticity_token = dataStorage.getAuthenticityToken();

        String csrf  = dataStorage.getCsrf();
        String newRelicId  = dataStorage.getNewRelicId();
        Map<String, String> cookies = dataStorage.getCookies();

        Connection method = Jsoup.connect("http://organizuj.to").
                data("authenticity_token", authenticity_token).
                data("aid", "D:1                                                                                                                                                                                                                                                            ").
                ignoreContentType(true).
                header("Accept", "application/json, text/javascript, */*; q=0.01").
                header("Accept-Encoding", "gzip,deflate,sdch").
                referrer("http://organizuj.to/o/events/mobilization-4/attendances").
                header("Host", "organizuj.to").
                header("X-CSRF-Token", csrf).
                header("X-Requested-With", "XMLHttpRequest").
                header("X-NewRelic-ID", newRelicId).
                cookies(cookies).method(Connection.Method.POST);

        Map<Integer, Boolean> responses = new HashMap<Integer, Boolean>(count);

        for(Map.Entry<Integer, Boolean> entry: incomingRequests.entrySet()) {
            sendPresence(entry.getKey(), entry.getValue(), method, responses);
        }

        writableDatabase.beginTransaction();

        SQLiteStatement sqLiteStatement = writableDatabase.compileStatement("UPDATE " + TABLE_NAME  + " " +
                        "SET "+ COLUMN_NEEDSUPDATE +" = 0 " +
                        "WHERE "+ COLUMN_ID+" = ?");

        for(Map.Entry<Integer, Boolean> response: responses.entrySet()) {
            sqLiteStatement.clearBindings();
            sqLiteStatement.bindLong(1, response.getKey());

            sqLiteStatement.execute();
        }
        writableDatabase.setTransactionSuccessful();
        writableDatabase.endTransaction();
    }

    private void sendPresence(Integer id, Boolean present, Connection method, Map<Integer, Boolean> out) {



        Connection.Response response4 = null;
        try {
            response4 = method.data("is_present", present ? "1" : "0").url(String.format("http://organizuj.to/o/events/mobilization-4/guests/%s/is_present", id)).execute();


            String body = response4.body();
            Guest guest = new Gson().fromJson(body, Guest.class);
            if(id != guest.id || guest.is_present != present) {
                LOGGER.warn("Response {} does not match the request {} {}", guest, id, present);
                return;
            }

            out.put(guest.id, guest.is_present);
        } catch (IOException e) {
            LOGGER.error("IOException while updating guest", e);
        }


    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);

        attendeesDBOpenHelper = new AttendeesDBOpenHelper(this);
        writableDatabase = attendeesDBOpenHelper.getWritableDatabase();
        dataStorage = new DataStorage(this);
    }

}
