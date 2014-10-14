package pl.mobilization.organizuj.to;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import com.google.gson.Gson;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import pl.mobilization.organizuj.to.json.Guest;

import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_ID;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_LOCAL_PRESENCE;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_REMOTE_PRESENCE;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_NEEDSUPDATE;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.TABLE_NAME;
import static pl.mobilization.organizuj.to.MainActivity.BASE_URL;
import static pl.mobilization.organizuj.to.MainActivity.HOST;

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
    public static final String ACTION_UPDATE_ATT = "pl.mobilization.organizuj.to.UPDATE_ATT";

    private AttendeesDBOpenHelper attendeesDBOpenHelper;
    private DataStorage dataStorage;
    private SQLiteDatabase writableDatabase;
    private Handler handler;

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
                new String[]{COLUMN_ID, COLUMN_LOCAL_PRESENCE},
                COLUMN_NEEDSUPDATE + " = 1 ", null, null, null, null);

        final int count = cursor.getCount();
        if (count == 0) {
            LOGGER.warn("handleActionCheckIn No items to checkin");
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


        Connection connection = Jsoup.connect(BASE_URL).
                ignoreContentType(true).
                header("Accept", "application/json, text/javascript, */*; q=0.01").
                header("Accept-Encoding", "gzip,deflate").

                header("Host", HOST).
                header("Origin", BASE_URL).
                header("X-CSRF-Token", csrf).
                header("X-Requested-With", "XMLHttpRequest").
                header("X-NewRelic-ID", newRelicId).
                referrer(BASE_URL + "/o/events/mobilization-4/attendances").

                data("authenticity_token", authenticity_token).
                data("aid", "D:1                                                                                                                                                                                                                                                            ").

                cookies(cookies).
                method(Connection.Method.POST);

        final Map<Integer, Boolean> responses = new HashMap<Integer, Boolean>(count);

        for(Map.Entry<Integer, Boolean> entry: incomingRequests.entrySet()) {
            sendPresence(entry.getKey(), entry.getValue(), connection, responses);
        }

        writableDatabase.beginTransaction();

        SQLiteStatement sqLiteStatement = writableDatabase.compileStatement("UPDATE " + TABLE_NAME  + " " +
                        "SET "+ COLUMN_NEEDSUPDATE +" = 0, " +
                         COLUMN_REMOTE_PRESENCE + " = ? "+
                        "WHERE "+ COLUMN_ID+" = ?");

        for(Map.Entry<Integer, Boolean> response: responses.entrySet()) {
            sqLiteStatement.clearBindings();
            sqLiteStatement.bindLong(1, response.getValue() ? 1 : 0);
            sqLiteStatement.bindLong(2, response.getKey());

            sqLiteStatement.execute();
        }
        writableDatabase.setTransactionSuccessful();
        writableDatabase.endTransaction();
        getApplicationContext().sendBroadcast(new Intent(ACTION_UPDATE_ATT));

        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(MainActivity.UPDATE_ATTENDEES));

        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(UpdateAttendanceIntentService.this, String.format("%d updated", responses.size()), Toast.LENGTH_SHORT ).show();
            }
        });
    }

    private void sendPresence(Integer id, Boolean present, Connection connection, Map<Integer, Boolean> out) {
        Connection.Response response4 = null;
        try {

            response4 = connection.data("is_present", present ? "1" : "0").
                    url(String.format("%s/o/events/mobilization-4/guests/%s/is_present", BASE_URL, id)).execute();

            int statusCode = response4.statusCode();
            if(statusCode != 200)  {
                LOGGER.warn("status code {}", statusCode);
                return;
            }

            String body = response4.body();
            Guest guest = new Gson().fromJson(body, Guest.class);
            if(id != guest.id || guest.is_present != present) {
                LOGGER.warn("Response {} does not match the request {} {}.\n{}", guest, id, present, body);
                return;
            }

            out.put(guest.id, guest.is_present);
        } catch (IOException e) {
            LOGGER.error("IOException while updating guest", e);
        }


    }

    @Override
    public void onCreate() {
        super.onCreate();

        attendeesDBOpenHelper = new AttendeesDBOpenHelper(this);
        writableDatabase = attendeesDBOpenHelper.getWritableDatabase();
        dataStorage = new DataStorage(this);
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        handler = new Handler();
    }

}
