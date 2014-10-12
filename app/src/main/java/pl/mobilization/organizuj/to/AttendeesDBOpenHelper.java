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
    private static final int DATABASE_VERSION = 11;


    public  static final String COLUMN_ID = "_id";
    public  static final String COLUMN_FNAME = "first_name";
    public  static final String COLUMN_LNAME = "last_name";
    public  static final String COLUMN_EMAIL = "email";
    public  static final String COLUMN_ISPRESENT = "ispresent";
    public  static final String COLUMN_NEEDSUPDATE = "needsupdate";
    public static final String COLUMN_TYPE = "type";

    private static final String DATABASE_CREATE = "CREATE TABLE " + TABLE_NAME.toUpperCase() +
            "( "+COLUMN_ID +" integer primary key, "+
            COLUMN_FNAME+" text,"+
            COLUMN_LNAME + " text," +
            COLUMN_ISPRESENT + " boolean not null default false," +
            COLUMN_NEEDSUPDATE + " boolean not null default false," +
            COLUMN_EMAIL + " text," +
            COLUMN_TYPE + " text default 'Attendee'" +
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
