package pl.mobilization.organizuj.to;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.google.gson.Gson;

import org.apache.commons.lang3.BooleanUtils;
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

import pl.mobilization.organizuj.to.client.R;
import pl.mobilization.organizuj.to.json.Attendee;

import static android.widget.AbsListView.CHOICE_MODE_NONE;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_EMAIL;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_FNAME;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_FROM_SERVER;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_ID;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_LNAME;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_LOCAL_PRESENCE;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_NEEDSUPDATE;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_REMOTE_PRESENCE;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_TYPE;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.TABLE_NAME;


public class MainActivity extends ActionBarActivity implements View.OnClickListener, LoaderManager.LoaderCallbacks<Cursor> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainActivity.class);
    private static final int ATTENDEE_LOADER = 1;
    private static final String[] COLUMNS = {COLUMN_REMOTE_PRESENCE, COLUMN_LOCAL_PRESENCE, COLUMN_FNAME, COLUMN_LNAME, COLUMN_EMAIL, COLUMN_TYPE} ;
    private static final int[] FIELDS = new int[]{ R.id.remote, R.id.local, R.id.first_name, R.id.last_name, R.id.email, R.id.type};
    public static final String HOST = "eventshaper.pl";
    public static final String BASE_URL = "http://" + HOST;
    public static final String UPDATE_ATTENDEES = "pl.mobilization.organizuj.to.update_attendees";
    private AttendeesDBOpenHelper attendeesDBOpenHelper;
    private SQLiteDatabase writableDatabase;
    private ListView listView;

    private SimpleCursorAdapter adapter;
    private EditText editTextFilter;
    private DataStorage dataStorage;

    private static Map<String, Integer> COLOR_MAP = new HashMap<String, Integer>();

    static {
        COLOR_MAP.put("Attendee", 0xFFEEEEEE);
        COLOR_MAP.put("VIP", 0xFFFF0000);
        COLOR_MAP.put("Speaker", 0xFF0000FF);
        COLOR_MAP.put("Organizer", 0xFFFFFF00);
    }

    private Button refreshButton;
    private TextView textViewStalaSaramak;
    private BroadcastReceiver broadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textViewStalaSaramak = (TextView)findViewById(R.id.stalasaramaka);

        IntentFilter filter = new IntentFilter();
        filter.addAction(UPDATE_ATTENDEES);
        LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                getSupportLoaderManager().restartLoader(ATTENDEE_LOADER, null, MainActivity.this);
            }
        }, filter);

        refreshButton = (Button)findViewById(R.id.button);

        refreshButton.setOnClickListener(this);

        dataStorage = new DataStorage(this);

        editTextFilter = (EditText) findViewById(R.id.filter);

        editTextFilter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                getSupportLoaderManager().restartLoader(ATTENDEE_LOADER, null, MainActivity.this);
            }
        });


        attendeesDBOpenHelper = new AttendeesDBOpenHelper(this);
        writableDatabase = attendeesDBOpenHelper.getWritableDatabase();

        listView = (ListView) findViewById(R.id.listview);

        listView.setChoiceMode(CHOICE_MODE_NONE);
        adapter = new SimpleCursorAdapter(

                        this,                // Current context
                        R.layout.attendee,  // Layout for a single row
                        null,                // No Cursor yet
                        COLUMNS,        // Cursor columns to use
                        FIELDS,           // Layout fields to use
                        0                    // No flags
                ) {


        };

        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int i) {
                if(view.getId() == R.id.remote || view.getId() == R.id.local) {
                    ((CheckBox)view).setChecked(cursor.getInt(i) == 1);
                    return true;
                } else if (view.getId() == R.id.type) {
                    String type = cursor.getString(i);
                    Integer color = COLOR_MAP.get(type);
                    if(color != null) {
                        ((View) view.getParent().getParent()).setBackgroundColor(color);
                        ((TextView) view).setText(type);
                    }
                    return true;
                }
                return false;
            }
        });

        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                LOGGER.debug("onItemClick on view {} at {} on id {}", view, position, id);
                CheckBox local = (CheckBox) view.findViewById(R.id.local);

                if(local == null)
                    return;

                ContentValues cv = new ContentValues();
                cv.put(COLUMN_NEEDSUPDATE, 1);
                cv.put(COLUMN_LOCAL_PRESENCE, !local.isChecked());

                int rows = writableDatabase.update(TABLE_NAME, cv, COLUMN_ID + " = ?",

                        new String[]{String.valueOf(id)});

                getSupportLoaderManager().restartLoader(ATTENDEE_LOADER, null, MainActivity.this);

                UpdateAttendanceIntentService.startActionCheckin(MainActivity.this);
            }
        });

        getSupportLoaderManager().initLoader(ATTENDEE_LOADER, null, this);
    }

    @Override
    public void takeKeyEvents(boolean get) {
        super.takeKeyEvents(get);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_logout) {
            SharedPreferences pref = getSharedPreferences(getString(R.string.shared_pref), MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            editor.putString(getString(R.string.loginPropKey), "");
            editor.putString(getString(R.string.passwordPropKey), "");
            editor.commit();
            Intent i = new Intent(this, LoginActivity.class);
            startActivity(i);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onResume() {
        super.onResume();
        refreshButton.callOnClick();
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, new IntentFilter(UpdateAttendanceIntentService.ACTION_UPDATE_ATT));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
    }

    @Override
    public void onClick(View v) {
        SharedPreferences pref = getSharedPreferences(getString(R.string.shared_pref), MODE_PRIVATE);
        final String username = pref.getString(getString(R.string.loginPropKey), "");
        final String password = pref.getString(getString(R.string.passwordPropKey), "");
        final ProgressDialog ringProgressDialog = ProgressDialog.show(MainActivity.this, "Please wait ...", "Synchronizing ...", true);

        ringProgressDialog.setCancelable(true);

        if(v.getId() == R.id.button) {
            new Thread() {
                @Override
                public void run() {
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
                        textViewStalaSaramak.post(new Runnable() {
                            @Override
                            public void run() {
                                textViewStalaSaramak.setText(String.format("%.2f", stalaSaramaka));

                            }
                        });
                    } catch (IOException e) {
                        LOGGER.error("IOException while inserting Attendees", e);
                    } finally {
                        listView.post(new Runnable() {
                            @Override
                            public void run() {
                                getSupportLoaderManager().restartLoader(ATTENDEE_LOADER, null, MainActivity.this);
                                ringProgressDialog.dismiss();
                            }
                        });
                    }
                }

                private float insertAttendeesIntoDBAndCalculateStalaSaramaka(Attendee[] attendees) {
                    //set everything is local
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(COLUMN_FROM_SERVER, 0);
                    writableDatabase.update(TABLE_NAME, contentValues, null, null);

                    writableDatabase.beginTransaction();

                    SQLiteStatement sqLiteStatement = writableDatabase.compileStatement(String.format("INSERT OR REPLACE INTO "  + TABLE_NAME + " " +
                            "(%s, %s, %s, %s, %s, %s, %s) " +
                            "VALUES (?, ?, ?, ?, ?, ?, 1) " ,
                            COLUMN_ID,
                            COLUMN_FNAME,
                            COLUMN_LNAME,
                            COLUMN_REMOTE_PRESENCE,
                            COLUMN_EMAIL,
                            COLUMN_TYPE,
                            COLUMN_FROM_SERVER));

                    int present_count = 0;

                    for(Attendee attendee: attendees) {
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
                    writableDatabase.delete(TABLE_NAME, COLUMN_FROM_SERVER+"=0", null);
                    //synchronize statuses where there is no local change (needs update is = 0)
                    writableDatabase.execSQL("UPDATE " + TABLE_NAME + " SET " + COLUMN_LOCAL_PRESENCE + " = " + COLUMN_REMOTE_PRESENCE + " WHERE " + COLUMN_NEEDSUPDATE + " = 0");
                    writableDatabase.setTransactionSuccessful();
                    writableDatabase.endTransaction();
                    if(attendees.length == 0)
                        return 0;
                    return 100 - (100.0f*present_count)/attendees.length;

                }
            }.start();
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        switch (i) {
            case ATTENDEE_LOADER:
                String filter = editTextFilter.getText().toString().trim();
                String selection = null;
                String[] selectionArgs = null;
                if (!TextUtils.isEmpty(filter)) {
                    if(filter.contains(" ")) {
                        String[] split = filter.split(" ", 2);
                        selectionArgs = new String[] {String.format("%%%s%%", split[0]), String.format("%%%s%%", split[1])};
                        selection = COLUMN_FNAME + " LIKE ? AND " + COLUMN_LNAME +"  LIKE ?";
                    } else {
                        selection = COLUMN_FNAME + " LIKE ? OR " + COLUMN_LNAME +"  LIKE ? OR " + COLUMN_EMAIL + " LIKE ?" ;
                        selectionArgs = new String[] {String.format("%%%s%%", filter), String.format("%%%s%%", filter), String.format("%%%s%%", filter)};
                    }
                }
                return new CursorLoader(this, AttendeesProvider.ATTENDEES_URI, null, selection, selectionArgs, null);
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        adapter.changeCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        adapter.changeCursor(null);
    }
}