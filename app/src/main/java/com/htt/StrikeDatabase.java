package com.htt;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class StrikeDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "htt_strikes.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "strikes";

    public StrikeDatabase(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "speed_ms REAL," +
                "power_score REAL," +
                "technique_score REAL," +
                "power_level REAL," +
                "timestamp_ms INTEGER," +
                "is_perfect INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public void saveStrike(StrikeTracker.StrikeResult r) {
        ContentValues v = new ContentValues();
        v.put("speed_ms",        r.speedMs);
        v.put("power_score",     r.powerScore);
        v.put("technique_score", r.techniqueScore);
        v.put("power_level",     r.powerLevel);
        v.put("timestamp_ms",    r.timestampMs);
        v.put("is_perfect",      r.isPerfect ? 1 : 0);
        getWritableDatabase().insert(TABLE, null, v);
    }

    public StrikeTracker.StrikeResult getPersonalBest(String metric) {
        String col = metric.equals("speed") ? "speed_ms" : metric.equals("power") ? "power_score" : "power_level";
        Cursor c = getReadableDatabase().query(TABLE, null, null, null, null, null, col + " DESC", "1");
        if (c == null) return null;
        StrikeTracker.StrikeResult r = null;
        if (c.moveToFirst()) {
            r = new StrikeTracker.StrikeResult();
            r.speedMs        = c.getFloat(c.getColumnIndexOrThrow("speed_ms"));
            r.powerScore     = c.getFloat(c.getColumnIndexOrThrow("power_score"));
            r.techniqueScore = c.getFloat(c.getColumnIndexOrThrow("technique_score"));
            r.powerLevel     = c.getFloat(c.getColumnIndexOrThrow("power_level"));
            r.timestampMs    = c.getLong(c.getColumnIndexOrThrow("timestamp_ms"));
            r.isPerfect      = c.getInt(c.getColumnIndexOrThrow("is_perfect")) == 1;
        }
        c.close();
        return r;
    }

    public int getTotalStrikes() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE, null);
        int n = 0; if (c.moveToFirst()) n = c.getInt(0); c.close(); return n;
    }

    public int getPerfectStrikes() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE + " WHERE is_perfect=1", null);
        int n = 0; if (c.moveToFirst()) n = c.getInt(0); c.close(); return n;
    }

    public float[] getSessionStats(long sinceMs) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT AVG(speed_ms), AVG(power_score), AVG(power_level), COUNT(*) FROM " + TABLE +
                " WHERE timestamp_ms > ?", new String[]{String.valueOf(sinceMs)});
        float[] s = {0,0,0,0};
        if (c.moveToFirst()) { s[0]=c.getFloat(0); s[1]=c.getFloat(1); s[2]=c.getFloat(2); s[3]=c.getFloat(3); }
        c.close();
        return s;
    }
}
