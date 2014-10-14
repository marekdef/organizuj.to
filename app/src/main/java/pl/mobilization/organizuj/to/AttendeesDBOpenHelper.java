package pl.mobilization.organizuj.to;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by marekdef on 08.10.14.
 */
public class AttendeesDBOpenHelper extends SQLiteOpenHelper {


    public static final String TABLE_NAME = "Attendees";
    private static final String DATABASE_NAME = TABLE_NAME + ".db";
    private static final int DATABASE_VERSION = 15;


    public  static final String COLUMN_ID = "_id";
    public  static final String COLUMN_FNAME = "first_name";
    public  static final String COLUMN_LNAME = "last_name";
    public  static final String COLUMN_EMAIL = "email";
    public  static final String COLUMN_REMOTE_PRESENCE = "remote_presence";
    public  static final String COLUMN_LOCAL_PRESENCE = "local_presence";
    public  static final String COLUMN_NEEDSUPDATE = "needsupdate";
    public static final String COLUMN_TYPE = "type";
    public static final String COLUMN_FROM_SERVER = "from_server";

    private static final String DATABASE_CREATE = "CREATE TABLE " + TABLE_NAME.toUpperCase() +
            "( "+COLUMN_ID +" integer primary key, "+
            COLUMN_FNAME+" text,"+
            COLUMN_LNAME + " text," +
            COLUMN_REMOTE_PRESENCE + " boolean not null default 0," +
            COLUMN_LOCAL_PRESENCE + " boolean not null default 0," +
            COLUMN_NEEDSUPDATE + " boolean not null default 0," +
            COLUMN_EMAIL + " text," +
            COLUMN_TYPE + " text default 'Attendee'," +
            COLUMN_FROM_SERVER + " boolean not null default 1"+
            ")";


    public AttendeesDBOpenHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(DATABASE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int old, int newVersion) {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(sqLiteDatabase);
    }
}
