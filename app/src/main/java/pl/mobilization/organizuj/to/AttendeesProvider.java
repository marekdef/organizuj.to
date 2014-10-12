package pl.mobilization.organizuj.to;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;

public class AttendeesProvider extends ContentProvider {

    public static final String AUTHORITY = "pl.mobilization.organizuj.to.attendees";

    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);
    public static final Uri ATTENDEES_URI = Uri.withAppendedPath(CONTENT_URI, "attendees");

    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    private static final int ATTENDEE_LIST = 1;
    private static final int ATTENDEE_ID = 2;

    public static final Logger LOGGER = LoggerFactory.getLogger(AttendeesProvider.class);

    static {
        URI_MATCHER.addURI(AUTHORITY,
                "attendees",
                ATTENDEE_LIST);
        URI_MATCHER.addURI(AUTHORITY,
                "attendees/#",
                ATTENDEE_ID);
    }

    private static final String TAG = AttendeesProvider.class.getSimpleName();

    private AttendeesDBOpenHelper attendeesDBOpenHelper;
    private SQLiteDatabase writableDatabase;

    @Override
    public boolean onCreate() {
        attendeesDBOpenHelper = new AttendeesDBOpenHelper(getContext());
        return true;
    }

    @Override
    public String getType(Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case ATTENDEE_LIST:
                return  ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + AUTHORITY;
            case ATTENDEE_ID:
                return  ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + AUTHORITY ;

        }
        throw new IllegalArgumentException("Incorrect uri " + uri);
    }

    @Override
    public Cursor query(Uri uri, String[] projection,
                        String selection, String[] selectionArgs,
                        String sortOrder) {
        SQLiteDatabase db = attendeesDBOpenHelper.getReadableDatabase();
        SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        boolean useAuthorityUri = false;
        switch (URI_MATCHER.match(uri)) {
            case ATTENDEE_LIST:
                builder.setTables(AttendeesDBOpenHelper.TABLE_NAME);
                if (TextUtils.isEmpty(sortOrder)) {
                    sortOrder = " " + AttendeesDBOpenHelper.COLUMN_LNAME +" ASC";
                }
                break;
            case ATTENDEE_ID:
                builder.setTables(AttendeesDBOpenHelper.TABLE_NAME);
                // limit query to one row at most:
                builder.appendWhere(" _ID = " +
                        uri.getLastPathSegment());
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported URI: " + uri);
        }
        LOGGER.info("Selection {} args {}", selection, new ReflectionToStringBuilder(selectionArgs).toString());
        Cursor cursor =
                builder.query(
                        db,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder);
        // if we want to be notified of any changes:
        cursor.setNotificationUri(
                    getContext().getContentResolver(),
                    uri);
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
        if(URI_MATCHER.match(uri) != ATTENDEE_LIST)
            throw new IllegalArgumentException(
                "Unsupported URI for insertion: " + uri);
        long insert = attendeesDBOpenHelper.getWritableDatabase().insert(AttendeesDBOpenHelper.TABLE_NAME, null, contentValues);
        return getUriForId(insert, Uri.withAppendedPath(CONTENT_URI, "attendees"));
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = attendeesDBOpenHelper.getWritableDatabase();
        int delCount = 0;
        switch (URI_MATCHER.match(uri)) {
            case ATTENDEE_LIST:
                delCount = db.delete(
                        AttendeesDBOpenHelper.TABLE_NAME,
                        selection,
                        selectionArgs);
                break;
            case ATTENDEE_ID:
                String idStr = uri.getLastPathSegment();
                String where = " _ID = " + idStr;
                if (!TextUtils.isEmpty(selection)) {
                    where += " AND " + selection;
                }
                delCount = db.delete(
                        AttendeesDBOpenHelper.TABLE_NAME,
                        where,
                        selectionArgs);
                break;
            default:
                // no support for deleting photos or entities -
                // photos are deleted by a trigger when the item is deleted
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        // notify all listeners of changes:
        if (delCount > 0 ) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return delCount;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        SQLiteDatabase db = attendeesDBOpenHelper.getWritableDatabase();
        int updateCount = 0;
        switch (URI_MATCHER.match(uri)) {
            case ATTENDEE_LIST:
                updateCount = db.update(
                        AttendeesDBOpenHelper.TABLE_NAME,
                        values,
                        selection,
                        selectionArgs);
                break;
            case ATTENDEE_ID:
                String idStr = uri.getLastPathSegment();
                String where = "_id = " + idStr;
                if (!TextUtils.isEmpty(selection)) {
                    where += " AND " + selection;
                }
                updateCount = db.update(
                        AttendeesDBOpenHelper.TABLE_NAME,
                        values,
                        where,
                        selectionArgs);
                break;
            default:
                // no support for updating photos or entities!
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        // notify all listeners of changes:
        if (updateCount > 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return updateCount;
    }

    private Uri getUriForId(long id, Uri uri) {
        if (id > 0) {
            Uri itemUri = ContentUris.withAppendedId(uri, id);
                            // notify all listeners of changes:
                getContext().
                        getContentResolver().
                        notifyChange(itemUri, null);
                       return itemUri;
        }
        // s.th. went wrong:
        throw new SQLException(
                "Problem while inserting into uri: " + uri);
    }
}
