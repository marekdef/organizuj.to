package pl.mobilization.organizuj.to;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_ID;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_LNAME;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_LOCAL_PRESENCE;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_NEEDSUPDATE;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_REMOTE_PRESENCE;
import static pl.mobilization.organizuj.to.AttendeesDBOpenHelper.COLUMN_TYPE;


public class MainActivity extends ActionBarActivity implements View.OnClickListener, LoaderManager.LoaderCallbacks<Cursor> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainActivity.class);
    private static final int ATTENDEE_LOADER = 1;
    private static final String[] COLUMNS = {COLUMN_REMOTE_PRESENCE, COLUMN_LOCAL_PRESENCE, COLUMN_FNAME, COLUMN_LNAME, COLUMN_EMAIL, COLUMN_TYPE} ;
    private static final int[] FIELDS = new int[]{ R.id.remote, R.id.local, R.id.first_name, R.id.last_name, R.id.email, R.id.type};
    private AttendeesDBOpenHelper attendeesDBOpenHelper;
    private SQLiteDatabase writableDatabase;
    private ListView listView;

    private SimpleCursorAdapter adapter;
    private EditText editTextFilter;
    private DataStorage dataStorage;

    private static Map<String, Integer> COLOR_MAP = new HashMap<String, Integer>();

    static {
        COLOR_MAP.put("Attendee", 0xFF00FF00);
        COLOR_MAP.put("VIP", 0xFFFF0000);
        COLOR_MAP.put("Speaker", 0xFFFF);
        COLOR_MAP.put("Organizer", 0xFFFFFF00);

    }

    private Button refreshButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
                    ((CheckBox)view).setText("");
                    return true;
                } else if (view.getId() == R.id.type) {
                    String type = cursor.getString(i);
                    Integer color = COLOR_MAP.get(type);
                    ((View)view.getParent()).setBackgroundColor(color);
                    view.setBackgroundColor(color);
                    ((TextView)view).setText(type);
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

                ContentValues cv = new ContentValues();
                cv.put(COLUMN_NEEDSUPDATE, 1);
                cv.put(COLUMN_LOCAL_PRESENCE, !local.isChecked());

                int rows = writableDatabase.update(AttendeesDBOpenHelper.TABLE_NAME, cv, COLUMN_ID + " = ?",

                        new String[]{String.valueOf(id)});

                getSupportLoaderManager().restartLoader(ATTENDEE_LOADER, null, MainActivity.this);

                UpdateAttendanceIntentService.startActionCheckin(MainActivity.this);
            }
        });

        getSupportLoaderManager().initLoader(ATTENDEE_LOADER, null, this);
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
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onResume() {
        super.onResume();
        refreshButton.callOnClick();
    }

    @Override
    public void onClick(View v) {
        SharedPreferences pref = getSharedPreferences(getString(R.string.shared_pref), MODE_PRIVATE);
        final String username = pref.getString(getString(R.string.loginPropKey), "");
        final String password = pref.getString(getString(R.string.passwordPropKey), "");
        final ProgressDialog ringProgressDialog = ProgressDialog.show(MainActivity.this, "Please wait ...", "Downloading ...", true);

        ringProgressDialog.setCancelable(true);

        if(v.getId() == R.id.button) {
            new Thread() {
                @Override
                public void run() {
                    Connection connect = Jsoup.connect("http://organizuj.to/users/login");
                    Connection connect2 = Jsoup.connect("http://organizuj.to/users/login");
                    Connection connect3 = Jsoup.connect("http://organizuj.to/o/events/mobilization-4/attendances");
                    Connection connect4 = Jsoup.connect("http://organizuj.to/o/events/mobilization-4/attendances?agenda_day_id=1&sort_by=id&order=asc");


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
                                referrer("http://organizuj.to/users/login").
                                header("Origin", "http://organizuj.to").
                                cookies(cookies).
                                method(Connection.Method.POST).
                                execute();

                        int statusCode = response2.statusCode();
                        cookies = response2.cookies();

                        Document document3 = response2.parse();

                        Elements csrf_tokens = document3.getElementsByAttributeValue("name", "csrf-token");
                        Element csrf_token = csrf_tokens.get(0);
                        String csrf = csrf_token.attr("content");

                        String text = document3.outerHtml();

                        int startOfRelic = text.indexOf("xpid:\"");

                        String newRelicId = text.substring(startOfRelic + 6, startOfRelic + 50);
                        int endOfRelic = newRelicId.indexOf('"');
                        if(endOfRelic != -1) {
                            newRelicId = newRelicId.substring(0, endOfRelic);
                        }

                        dataStorage.storeCookies(cookies);
                        dataStorage.storeCSRF(csrf);
                        dataStorage.storeRelic(newRelicId);


                        Connection.Response response4 = connect4.data("authenticity_token", authenticity_token).
                                ignoreContentType(true).
                                header("Accept", "application/json, text/javascript, */*; q=0.01").
                                header("Accept-Encoding", "gzip,deflate,sdch").
                                referrer("http://organizuj.to/o/events/mobilization-4/attendances").
                                header("Host", "organizuj.to").
                                header("X-CSRF-Token", csrf).
                                header("X-Requested-With", "XMLHttpRequest").
                                header("X-NewRelic-ID", newRelicId).
                                cookies(cookies).
                                method(Connection.Method.GET).execute();

                        String body = response4.body();

                        Gson gson = new Gson();


                        Attendee[] attendees = gson.fromJson(body, Attendee[].class);

                        writableDatabase.beginTransaction();

                        SQLiteStatement sqLiteStatement = writableDatabase.compileStatement(String.format("INSERT OR REPLACE INTO ATTENDEES " +
                                "(%s, %s, %s, %s, %s, %s ) " +
                                "VALUES (?, ?, ?, ?, ?, ?)",
                                COLUMN_ID,
                                COLUMN_FNAME,
                                COLUMN_LNAME,
                                COLUMN_REMOTE_PRESENCE,
                                COLUMN_EMAIL,
                                COLUMN_TYPE));

                        for(Attendee attendee: attendees) {
                            sqLiteStatement.clearBindings();
                            sqLiteStatement.bindLong(1, attendee.id);
                            sqLiteStatement.bindString(2, StringUtils.defaultString(attendee.first_name));
                            sqLiteStatement.bindString(3, StringUtils.defaultString(attendee.last_name));
                            sqLiteStatement.bindLong(4, attendee.is_present ? 1 : 0);
                            sqLiteStatement.bindString(5, StringUtils.defaultString(attendee.email));
                            sqLiteStatement.bindString(6, StringUtils.defaultString(attendee.type, "Attendee"));


                            sqLiteStatement.execute();
                        }
                        writableDatabase.setTransactionSuccessful();
                        writableDatabase.endTransaction();
                        listView.post(new Runnable() {
                            @Override
                            public void run() {
                                getSupportLoaderManager().restartLoader(ATTENDEE_LOADER, null, MainActivity.this);
                                ringProgressDialog.dismiss();
                            }
                        });
                    } catch (IOException e) {
                        LOGGER.error("IOException while inserting Attendees", e);
                        ringProgressDialog.dismiss();
                    }
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