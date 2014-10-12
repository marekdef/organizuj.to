package pl.mobilization.organizuj.to;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.text.TextUtils;

import com.google.gson.Gson;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

import pl.mobilization.organizuj.to.json.Guest;

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
    private static final String ACTION_UPDATE = "pl.mobilization.organizuj.to.action.UPDATE";

    // TODO: Rename parameters
    private static final String ATTENDEE_ID = "pl.mobilization.organizuj.to.attendee.id";
    private static final String ATTENDEE_PRESENCE = "pl.mobilization.organizuj.to.attendee.presense";
    private AttendeesDBOpenHelper attendeesDBOpenHelper;
    private DataStorage dataStorage;

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    // TODO: Customize helper method
    public static void startActionCheckin(Context context, String id, String presence) {
        Intent intent = new Intent(context, UpdateAttendanceIntentService.class);
        intent.setAction(ACTION_UPDATE);
        intent.putExtra(ATTENDEE_ID, id);
        intent.putExtra(ATTENDEE_PRESENCE, presence);
        context.startService(intent);
    }
    public UpdateAttendanceIntentService() {
        super("UpdateAttendanceIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_UPDATE.equals(action)) {
                final String param1 = intent.getStringExtra(ATTENDEE_ID);
                final String param2 = intent.getStringExtra(ATTENDEE_PRESENCE);
                handleActionCheckIn(param1, param2);
            }
        }
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private void handleActionCheckIn(String id, String local) {
        if(TextUtils.isEmpty(id) || TextUtils.isEmpty(local)) {
            LOGGER.warn("handleActionCheckIn - supplied empty params id>{}< local>{}<", id, local);
            return;
        }
        String[] selectionArgs = new String[] {id, local} ;
        attendeesDBOpenHelper.getWritableDatabase().rawQuery("UPDATE " + AttendeesDBOpenHelper.TABLE_NAME + " SET " + AttendeesDBOpenHelper.COLUMN_LOCAL + " = ?", selectionArgs);


        Connection connect = Jsoup.connect(String.format("http://organizuj.to/o/events/mobilization-4/guests/%s/is_present", id));

        String authenticity_token = dataStorage.getAuthenticityToken();

        String csrf  = dataStorage.getCsrf();
        String newRelicId  = dataStorage.getNewRelicId();
        Map<String, String> cookies = dataStorage.getCookies();
        Connection.Response response4 = null;
        try {
            connect.data("authenticity_token", authenticity_token).
                    ignoreContentType(true).
                    header("Accept", "application/json, text/javascript, */*; q=0.01").
                    header("Accept-Encoding", "gzip,deflate,sdch").
                    referrer("http://organizuj.to/o/events/mobilization-4/attendances").
                    header("Host", "organizuj.to").
                    header("X-CSRF-Token", csrf).
                    header("X-Requested-With", "XMLHttpRequest").
                    header("X-NewRelic-ID", newRelicId).
                    cookies(cookies).method(Connection.Method.POST).execute();

            String body = response4.body();
            Guest guest = new Gson().fromJson(body, Guest.class);
            if(!id.equals(guest.id) || guest.is_present == false)
                LOGGER.warn("Response {} does not match the request {} {}",guest, id, local);
        } catch (IOException e) {
            LOGGER.error("IOException while updating guest", e);
        }
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);

        attendeesDBOpenHelper = new AttendeesDBOpenHelper(this);
        dataStorage = new DataStorage(this);
    }

}
