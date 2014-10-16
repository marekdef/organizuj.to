package pl.mobilization.organizuj.to;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import com.google.gson.Gson;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import pl.mobilization.organizuj.to.json.Attendee;
import pl.mobilization.organizuj.to.json.Guest;

import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_EMAIL;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_FNAME;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_FROM_SERVER;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_ID;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_LNAME;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_LOCAL_PRESENCE;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_REMOTE_PRESENCE;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_NEEDSUPDATE;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_TYPE;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.TABLE_NAME;
import static pl.mobilization.organizuj.to.MainActivity.BASE_URL;
import static pl.mobilization.organizuj.to.MainActivity.HOST;
import static pl.mobilization.organizuj.to.MainActivity.UPDATE_ATTENDEES_ACTION;

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
    private static final String ACTION_SYNC = "pl.mobilization.organizuj.to.action.SYNC";
    public static final String ACTION_UPDATE_ATT = "pl.mobilization.organizuj.to.UPDATE_ATT";
    private static final String PARAM_ID = "id";
    private static final String PARAM_PRESENT = "present";
    private static final String PARAM_USERNAME = "username";
    private static final String PARAM_PASSWORD = "password";

    private AttendeesDBOpenHelper attendeesDBOpenHelper;
    private DataStorage dataStorage;
    private Handler handler;

    public static void startActionSync(Context context, String username, String password) {
        Intent intent = new Intent(context, UpdateAttendanceIntentService.class);
        intent.setAction(ACTION_SYNC);
        intent.putExtra(PARAM_USERNAME, username);
        intent.putExtra(PARAM_PASSWORD, password);

        context.startService(intent);
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    // TODO: Customize helper method
    public static void startActionCheckin(Context context, long id, boolean present) {
        Intent intent = new Intent(context, UpdateAttendanceIntentService.class);
        intent.setAction(ACTION_CHECKIN);
        intent.putExtra(PARAM_ID, id);
        intent.putExtra(PARAM_PRESENT, present);

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
                long id = intent.getLongExtra(PARAM_ID, -1);
                boolean present = intent.getBooleanExtra(PARAM_PRESENT, false);
                handleActionCheckIn(id, present);
            } else {
                if (ACTION_SYNC.equals(action)) {
                    String username = intent.getStringExtra(PARAM_USERNAME);
                    String password = intent.getStringExtra(PARAM_PASSWORD);
                    handleActionSync(username, password);
                }
            }
        }
    }
    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private void handleActionCheckIn(long id, boolean present) {
        SQLiteDatabase writableDatabase = attendeesDBOpenHelper.getWritableDatabase();

        int reponses_size = 0;
        try {
            ContentValues cv = new ContentValues();
            cv.put(COLUMN_NEEDSUPDATE, 1);
            cv.put(COLUMN_LOCAL_PRESENCE, present);


            int update = writableDatabase.update(TABLE_NAME, cv, COLUMN_ID + " = ?",
                    new String[]{String.valueOf(id)});

            if(update == 0) {
                LOGGER.warn("No item marked to update");
            }


            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(UPDATE_ATTENDEES_ACTION));

            Cursor cursor = writableDatabase.query(TABLE_NAME,
                    new String[]{COLUMN_ID, COLUMN_LOCAL_PRESENCE},
                    COLUMN_NEEDSUPDATE + " = 1 ", null, null, null, null);

            final int count = cursor.getCount();
            if (count == 0) {
                LOGGER.warn("handleActionCheckIn No items to checkin");
                return;
            }

            Map<Integer, Boolean> incomingRequests = new HashMap<Integer, Boolean>(count);

            while (cursor.moveToNext()) {
                int local_id = cursor.getInt(0);
                int local_present = cursor.getInt(1);
                incomingRequests.put(local_id, local_present == 0 ? false : true);

                cursor.moveToNext();
            }

            cursor.close();

            String authenticity_token = dataStorage.getAuthenticityToken();

            String csrf = dataStorage.getCsrf();
            String newRelicId = dataStorage.getNewRelicId();
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

            for (Map.Entry<Integer, Boolean> entry : incomingRequests.entrySet()) {
                sendPresence(entry.getKey(), entry.getValue(), connection, responses);
            }

            reponses_size = responses.size();

            writableDatabase.beginTransaction();

            SQLiteStatement sqLiteStatement = writableDatabase.compileStatement("UPDATE " + TABLE_NAME + " " +
                    "SET " + COLUMN_NEEDSUPDATE + " = 0, " +
                    COLUMN_REMOTE_PRESENCE + " = ? " +
                    "WHERE " + COLUMN_ID + " = ?");

            for (Map.Entry<Integer, Boolean> response : responses.entrySet()) {
                sqLiteStatement.clearBindings();
                sqLiteStatement.bindLong(1, response.getValue() ? 1 : 0);
                sqLiteStatement.bindLong(2, response.getKey());

                sqLiteStatement.execute();
            }
            writableDatabase.setTransactionSuccessful();
            writableDatabase.endTransaction();
            getApplicationContext().sendBroadcast(new Intent(ACTION_UPDATE_ATT));

            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(UPDATE_ATTENDEES_ACTION));
        }
        finally {
            writableDatabase.close();
        }

        final int finalReponses_size = reponses_size;
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(UpdateAttendanceIntentService.this, String.format("%d updated", finalReponses_size), Toast.LENGTH_SHORT ).show();
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
        dataStorage = new DataStorage(this);
        attendeesDBOpenHelper = new AttendeesDBOpenHelper(this);
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        handler = new Handler();
    }

    public void handleActionSync(String username, String password) {
        Connection connect = Jsoup.connect(BASE_URL + "/users/login");
        Connection connect2 = Jsoup.connect(BASE_URL + "/users/login");
        Connection connect3 = Jsoup.connect(BASE_URL + "/o");
        Connection connect4 = Jsoup.connect(BASE_URL + "/o/events/mobilization-4/attendances");
        Connection connect5 = Jsoup.connect(BASE_URL + "/o/events/mobilization-4/attendances?agenda_day_id=1&sort_by=id&order=asc");

        try {
            Document document = connect.get();

            Elements elementsByAttributeValue = document.getElementsByAttributeValue("name", "authenticity_token");
            Element element = elementsByAttributeValue.get(0);
            String authenticity_token = element.val();
            dataStorage.storeToken(authenticity_token);

            Connection.Response response = connect.response();

            Map<String, String> cookies = response.cookies();

            Connection.Response response2 = connect2.
                    data("authenticity_token", authenticity_token).
                    data("user[email]", username).
                    data("user[password]", password).
                    data("user[remember_me]", "0").
                    data("commit", "Zaloguj").
                    referrer(BASE_URL + "/users/login").
                    header("Origin", HOST).
                    cookies(cookies).
                    method(Connection.Method.POST).
                    execute();

            int statusCode = response2.statusCode();
            cookies = response2.cookies();

            Connection.Response response3 = connect3.
                    data("authenticity_token", authenticity_token).
                    referrer(BASE_URL + "/users/login").
                    header("Origin", HOST).
                    cookies(cookies).
                    method(Connection.Method.GET).
                    execute();

            Document document3 = response3.parse();

            Elements csrf_tokens = document3.getElementsByAttributeValue("name", "csrf-token");
            Element csrf_token = csrf_tokens.get(0);
            String csrf = csrf_token.attr("content");

            Elements scripts = document3.getElementsByTag("head");
            Element relic = scripts.get(0);
            String text = relic.outerHtml();

            int startOfRelic = text.indexOf("xpid:\"");

            String newRelicId = text.substring(startOfRelic + 6, startOfRelic + 50);
            int endOfRelic = newRelicId.indexOf('"');
            if(endOfRelic != -1) {
                newRelicId = newRelicId.substring(0, endOfRelic);
            }

            dataStorage.storeCookies(cookies);
            dataStorage.storeCSRF(csrf);
            dataStorage.storeRelic(newRelicId);


            Connection.Response response4 = connect5.data("authenticity_token", authenticity_token).
                    ignoreContentType(true).
                    header("Accept", "application/json, text/javascript, */*; q=0.01").
                    header("Accept-Encoding", "gzip,deflate,sdch").
                    referrer(BASE_URL + "/o/events/mobilization-4/attendances").
                    header("Host", "organizuj.to").
                    header("X-CSRF-Token", csrf).
                    header("X-Requested-With", "XMLHttpRequest").
                    header("X-NewRelic-ID", newRelicId).
                    cookies(cookies).
                    method(Connection.Method.GET).execute();

            String body = response4.body();

            Gson gson = new Gson();

            Attendee[] attendees = gson.fromJson(body, Attendee[].class);
            final float stalaSaramaka = insertAttendeesIntoDBAndCalculateStalaSaramaka(attendees);
            dataStorage.storeStalaSaramaka(stalaSaramaka);
        } catch (IOException e) {
            LOGGER.error("IOException while inserting Attendees", e);
        } finally {
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(UPDATE_ATTENDEES_ACTION));
        }
    }

    private float insertAttendeesIntoDBAndCalculateStalaSaramaka(Attendee[] attendees) {
        SQLiteDatabase writableDatabase = attendeesDBOpenHelper.getWritableDatabase();
        try {
            //set everything is local
            ContentValues contentValues = new ContentValues();
            contentValues.put(COLUMN_FROM_SERVER, 0);

            writableDatabase.update(TABLE_NAME, contentValues, null, null);

            writableDatabase.beginTransaction();

            SQLiteStatement sqLiteStatement = writableDatabase.compileStatement(String.format("INSERT OR REPLACE INTO " + TABLE_NAME + " " +
                            "(%s, %s, %s, %s, %s, %s, %s) " +
                            "VALUES (?, ?, ?, ?, ?, ?, 1) ",
                    COLUMN_ID,
                    COLUMN_FNAME,
                    COLUMN_LNAME,
                    COLUMN_REMOTE_PRESENCE,
                    COLUMN_EMAIL,
                    COLUMN_TYPE,
                    COLUMN_FROM_SERVER));

            int present_count = 0;

            for (Attendee attendee : attendees) {
                sqLiteStatement.clearBindings();
                sqLiteStatement.bindLong(1, attendee.id);
                sqLiteStatement.bindString(2, StringUtils.defaultString(attendee.first_name));
                sqLiteStatement.bindString(3, StringUtils.defaultString(attendee.last_name));
                if (attendee.is_present) {
                    present_count++;
                    sqLiteStatement.bindLong(4, 1);
                } else {
                    sqLiteStatement.bindLong(4, 0);
                }
                sqLiteStatement.bindString(5, StringUtils.defaultString(attendee.email));
                sqLiteStatement.bindString(6, StringUtils.defaultString(attendee.type, "Attendee"));

                sqLiteStatement.execute();
            }

            //delete local entries
            writableDatabase.delete(TABLE_NAME, COLUMN_FROM_SERVER + "=0", null);
            //synchronize statuses where there is no local change (needs update is = 0)
            writableDatabase.execSQL("UPDATE " + TABLE_NAME + " SET " + COLUMN_LOCAL_PRESENCE + " = " + COLUMN_REMOTE_PRESENCE + " WHERE " + COLUMN_NEEDSUPDATE + " = 0");
            writableDatabase.setTransactionSuccessful();
            writableDatabase.endTransaction();
            if (attendees.length == 0)
                return 0;
            return 100 - (100.0f * present_count) / attendees.length;
        }finally {
            writableDatabase.close();
        }

    }



}
