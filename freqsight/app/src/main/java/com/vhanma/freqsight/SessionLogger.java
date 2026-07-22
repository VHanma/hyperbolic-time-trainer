package com.vhanma.freqsight;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class SessionLogger {
    private final Context context;
    private File sessionDir;
    private File transcript;
    private File sensorCsv;
    private File audioCsv;
    private File beaconCsv;
    private int eventCount;

    SessionLogger(Context context) { this.context = context.getApplicationContext(); }

    synchronized File startSession() {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File base = new File(context.getExternalFilesDir(null), "FREQSIGHT/sessions");
        sessionDir = new File(base, stamp);
        sessionDir.mkdirs();
        new File(sessionDir, "events").mkdirs();
        transcript = new File(sessionDir, "transcript.txt");
        sensorCsv = new File(sessionDir, "sensors.csv");
        audioCsv = new File(sessionDir, "audio.csv");
        beaconCsv = new File(sessionDir, "beacon.csv");
        eventCount = 0;
        write(transcript, "FREQSIGHT EVIDENCE TRANSCRIPT\nRule: No message without a mark. No word without a waveform. No English without evidence.\n\n", false);
        write(sensorCsv, "timestamp,mag_x,mag_y,mag_z,mag_uT,accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z,light_lux,vibration,stable,anomaly\n", false);
        write(audioCsv, "timestamp,rms,peak_hz,peak_db,click,pulse,repeated,pattern\n", false);
        write(beaconCsv, "timestamp,pattern,frequency_hz,duration_ms,light,audible,label\n", false);
        return sessionDir;
    }

    synchronized File ensureSession() { return sessionDir == null ? startSession() : sessionDir; }

    synchronized void logSensor(DataModels.SensorSnapshot s) {
        ensureSession();
        write(sensorCsv, String.format(Locale.US,
                "%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.5f,%.5f,%.5f,%.3f,%.5f,%s,%s\n",
                s.timestampMs,s.magX,s.magY,s.magZ,s.magMagnitude,s.accelX,s.accelY,s.accelZ,
                s.gyroX,s.gyroY,s.gyroZ,s.lightLux,s.vibrationScore,s.stable,escape(s.anomalySummary)), true);
    }

    synchronized void logAudio(DataModels.AudioSnapshot a) {
        ensureSession();
        write(audioCsv, String.format(Locale.US,"%d,%.6f,%.2f,%.2f,%s,%s,%s,%s\n",
                a.timestampMs,a.rms,a.peakFrequencyHz,a.peakDb,a.click,a.pulse,a.repeatedPattern,escape(a.pattern)),true);
    }

    synchronized void logBeacon(BeaconEngine.BeaconPulse p) {
        ensureSession();
        write(beaconCsv, String.format(Locale.US,"%d,%s,%.2f,%d,%s,%s,%s\n",
                p.timestampMs,escape(p.pattern),p.frequencyHz,p.durationMs,p.lightOn,p.audible,escape(p.label)),true);
    }

    synchronized void logEvidence(DataModels.EvidenceEvent e, DataModels.VisualEvent v) {
        ensureSession();
        eventCount++;
        String stem = String.format(Locale.US,"event_%04d_frame_%08d",eventCount,e.frameNumber);
        File dir = new File(sessionDir,"events");
        if(v.rawFrame!=null) saveBitmap(v.rawFrame,new File(dir,stem+"_raw.jpg"),Bitmap.CompressFormat.JPEG,94);
        if(v.processedFrame!=null) saveBitmap(v.processedFrame,new File(dir,stem+"_processed.png"),Bitmap.CompressFormat.PNG,100);
        File json = new File(dir,stem+".json");
        String body = "{\n"+
                "  \"timestamp\": "+e.timestampMs+",\n"+
                "  \"frame\": "+e.frameNumber+",\n"+
                "  \"region\": \""+json(e.region)+"\",\n"+
                "  \"rawSource\": \""+json(e.rawSource)+"\",\n"+
                "  \"rawPattern\": \""+json(e.rawPattern)+"\",\n"+
                "  \"sensorCorrelation\": \""+json(e.sensorCorrelation)+"\",\n"+
                "  \"englishRendering\": \""+json(e.englishRendering)+"\",\n"+
                "  \"alternateRendering\": \""+json(e.alternateRendering)+"\",\n"+
                "  \"confidence\": "+e.confidence+",\n"+
                "  \"status\": \""+json(e.status)+"\"\n"+
                "}\n";
        write(json,body,false);
        write(transcript,e.transcriptBlock()+"\n",true);
    }

    synchronized Uri exportZip() {
        ensureSession();
        File zipFile = new File(context.getCacheDir(),"FREQSIGHT_"+sessionDir.getName()+".zip");
        try(ZipOutputStream zos=new ZipOutputStream(new FileOutputStream(zipFile))){zipDirectory(sessionDir,sessionDir,zos);}catch(Exception e){return null;}
        ContentValues values=new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME,zipFile.getName());
        values.put(MediaStore.Downloads.MIME_TYPE,"application/zip");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS+"/FREQSIGHT");
        ContentResolver resolver=context.getContentResolver();
        Uri uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
        if(uri==null)return null;
        try(OutputStream out=resolver.openOutputStream(uri);FileInputStream in=new FileInputStream(zipFile)){
            byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);
            return uri;
        }catch(Exception e){return null;}
    }

    synchronized File getSessionDir(){return sessionDir;}

    private void zipDirectory(File root,File file,ZipOutputStream zos)throws Exception{
        if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File c:children)zipDirectory(root,c,zos);return;}
        String name=root.toURI().relativize(file.toURI()).getPath();
        zos.putNextEntry(new ZipEntry(name));
        try(FileInputStream in=new FileInputStream(file)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)zos.write(b,0,n);}zos.closeEntry();
    }

    private void saveBitmap(Bitmap b,File f,Bitmap.CompressFormat format,int quality){try(FileOutputStream out=new FileOutputStream(f)){b.compress(format,quality,out);}catch(Exception ignored){}}
    private void write(File f,String text,boolean append){try(BufferedWriter w=new BufferedWriter(new FileWriter(f,append))){w.write(text);}catch(Exception ignored){}}
    private String escape(String s){if(s==null)return "";return s.replace("\n"," ").replace(",",";");}
    private String json(String s){if(s==null)return "";return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");}
}
