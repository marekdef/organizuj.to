package pl.mobilization.organizuj.to;

import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.ListView;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pl.mobilization.organizuj.to.json.Attendee;
import pl.mobilization.organizuj.to.organizujto.R;

import static pl.mobilization.organizuj.to.AttendeesProvider.CONTENT_URI;


public class MainActivity extends ActionBarActivity implements View.OnClickListener, LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "MainActivity";
    private static final int ATTENDEE_LOADER = 1;
    private static final String[] COLUMNS = {AttendeesDBOpenHelper.COLUMN_ISPRESENT_REMOTE, AttendeesDBOpenHelper.COLUMN_FNAME, AttendeesDBOpenHelper.COLUMN_LNAME} ;
    private static final int[] FIELDS = new int[]{ R.id.present, R.id.first_name, R.id.last_name};
    private AttendeesDBOpenHelper attendeesDBOpenHelper;
    private SQLiteDatabase writableDatabase;
    private ListView listView;

    private SimpleCursorAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportLoaderManager().initLoader(ATTENDEE_LOADER, null, this);
        
        setContentView(R.layout.activity_main);

        View viewById = findViewById(R.id.button);

        viewById.setOnClickListener(this);


        attendeesDBOpenHelper = new AttendeesDBOpenHelper(this);
        writableDatabase = attendeesDBOpenHelper.getWritableDatabase();

        listView = (ListView) findViewById(R.id.listview);

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
                if (view.getId() == R.id.checkbox) {
                    ((CheckBox) view).setChecked(cursor.getInt(i) == 1);
                    return true;
                }
                return false;
            }
        });

        listView.setAdapter(adapter);
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
        final String username = getResources().getString(R.string.username);
        final String password = getResources().getString(R.string.password);

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

                        int indexOf = text.indexOf("xpid:\"");

                        String newRelicId = text.substring(indexOf + 6, indexOf + 50);
                        indexOf = newRelicId.indexOf('"');
                        if(indexOf != -1) {
                            newRelicId = newRelicId.substring(0, indexOf);
                        }





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


                        /*
                        DefaultHttpClient defaultHttpClient = new DefaultHttpClient();
                        CookieStore cookieStore = defaultHttpClient.getCookieStore();
                        for (String key : cookies.keySet()) {
                            cookieStore.addCookie(new BasicClientCookie(key, cookies.get(key)));;
                        }
                        HttpGet get = new HttpGet("http://organizuj.to/o/events/mobilization-4/attendances?agenda_day_id=1&sort_by=id&order=asc&authenticity_token="+ URLEncoder.encode(authenticity_token,"UTF-8"));
                        */
                        //get.addHeader("Accept", "application/json, text/javascript, */*; q=0.01");
                        /*
                                get.addHeader("Accept-Encoding", "gzip,deflate,sdch");
                                get.addHeader("Host", "organizuj.to");
                                get.addHeader("X-CSRF-Token", csrf);
                                get.addHeader("X-Requested-With", "XMLHttpRequest");
                                get.addHeader("X-NewRelic-ID", newRelicId);
                                get.addHeader("Referer","http://organizuj.to/o/events/mobilization-4/attendances");




                        HttpResponse response4 = defaultHttpClient.execute(get);

                        InputStream content = response4.getEntity().getContent();
                        */

                        String body = response4.body();

                        Gson gson = new Gson();


                        Attendee[] attendees = gson.fromJson(body, Attendee[].class);

                        ContentValues contentValues = new ContentValues();
                        for(Attendee attendee: attendees) {
                            contentValues.put(AttendeesDBOpenHelper.COLUMN_ID, attendee.id);
                            contentValues.put(AttendeesDBOpenHelper.COLUMN_LNAME, attendee.last_name);
                            contentValues.put(AttendeesDBOpenHelper.COLUMN_FNAME, attendee.first_name);
                            contentValues.put(AttendeesDBOpenHelper.COLUMN_ISPRESENT_REMOTE, attendee.is_present);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        switch (i) {
            case ATTENDEE_LOADER:
                return new CursorLoader(this, AttendeesProvider.ATTENDEES_URI, null, null, null, null);
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