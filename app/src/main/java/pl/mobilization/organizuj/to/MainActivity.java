package pl.mobilization.organizuj.to;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
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
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;

import com.google.gson.Gson;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.helper.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import pl.mobilization.organizuj.to.client.R;
import pl.mobilization.organizuj.to.json.Attendee;


public class MainActivity extends ActionBarActivity implements View.OnClickListener, LoaderManager.LoaderCallbacks<Cursor> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainActivity.class);
    private static final int ATTENDEE_LOADER = 1;
    private static final String[] COLUMNS = {AttendeesDBOpenHelper.COLUMN_ISPRESENT, AttendeesDBOpenHelper.COLUMN_FNAME, AttendeesDBOpenHelper.COLUMN_LNAME, AttendeesDBOpenHelper.COLUMN_EMAIL, AttendeesDBOpenHelper.COLUMN_TYPE} ;
    private static final int[] FIELDS = new int[]{ R.id.present, R.id.first_name, R.id.last_name, R.id.email, R.id.type};
    private AttendeesDBOpenHelper attendeesDBOpenHelper;
    private SQLiteDatabase writableDatabase;
    private ListView listView;

    private SimpleCursorAdapter adapter;
    private EditText editTextFilter;
    private DataStorage dataStorage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View viewById = findViewById(R.id.button);

        viewById.setOnClickListener(this);

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
        listView.setChoiceMode(ListView.CHOICE_MODE_NONE);
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
                if(view.getId() == R.id.present) {
                    ((CheckBox)view).setChecked(cursor.getInt(i) == 1);
                    ((CheckBox)view).setText("") ;
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
    public void onClick(View v) {
        SharedPreferences pref = getSharedPreferences(getString(R.string.shared_pref), MODE_PRIVATE);
        final String username = pref.getString(getString(R.string.loginPropKey), "");
        final String password = pref.getString(getString(R.string.passwordPropKey), "");

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
                        dataStorage.storeToken(authenticity_token);

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

                        SQLiteStatement sqLiteStatement = writableDatabase.compileStatement(String.format("INSERT OR REPLACE INTO ATTENDEES (%s, %s, %s, %s, %s, %s ) VALUES (?, ?, ?, ?, ?, ?)", AttendeesDBOpenHelper.COLUMN_ID, AttendeesDBOpenHelper.COLUMN_FNAME, AttendeesDBOpenHelper.COLUMN_LNAME, AttendeesDBOpenHelper.COLUMN_ISPRESENT, AttendeesDBOpenHelper.COLUMN_EMAIL, AttendeesDBOpenHelper.COLUMN_TYPE));
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
                                adapter.notifyDataSetChanged();
                            }
                        });
                    } catch (IOException e) {
                        LOGGER.error("IOException while inserting Attendees", e);
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
                        selection = AttendeesDBOpenHelper.COLUMN_FNAME + " LIKE ? AND " + AttendeesDBOpenHelper.COLUMN_LNAME +"  LIKE ?";
                    } else {
                        selection = AttendeesDBOpenHelper.COLUMN_FNAME + " LIKE ? OR " + AttendeesDBOpenHelper.COLUMN_LNAME +"  LIKE ?";
                        selectionArgs = new String[] {String.format("%%%s%%", filter), String.format("%%%s%%", filter)};
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