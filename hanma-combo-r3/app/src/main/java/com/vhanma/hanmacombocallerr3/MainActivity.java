package com.vhanma.hanmacombocallerr3;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import android.util.Base64;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private static final int BG=Color.rgb(3,3,5), PANEL=Color.rgb(18,19,23), EDGE=Color.rgb(48,50,59);
    private static final int RED=Color.rgb(255,63,58), GOLD=Color.rgb(255,190,72), CYAN=Color.rgb(77,220,238), GREEN=Color.rgb(65,210,111), ORANGE=Color.rgb(255,155,55), WHITE=Color.rgb(245,245,248), MUTED=Color.rgb(177,177,187);
    private static final String PREFS="hanma_omega_r7";

    private TacticalEngine engine;
    private TacticalEngine.Result current;
    private TextToSpeech tts;
    private boolean ttsReady=false, paused=false, whyOpen=false, branchHidden=false, ghostReplay=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long roundEndsAt=0L, pausedRemaining=0L;

    private LinearLayout root, fightBox;
    private TextView timer, phase, pressure, opponent, cue, state, mind, reply, reaction, shard, sourceChip, why, footer;
    private Button pauseBtn;
    private Spinner modeSpin, oppSpin, rangeSpin, stanceSpin, profileSpin, doctrineSpin, roundSpin, revealSpin;
    private SeekBar difficulty;
    private SharedPreferences prefs;
    private final List<Button> outcomeButtons=new ArrayList<>();
    private final List<TacticalEngine.Result> roundTape=new ArrayList<>();
    private List<TacticalEngine.Result> ghostTape=Collections.emptyList();
    private int ghostIndex=0;
    private int calls=0, clean=0, late=0, blocked=0, missed=0, countered=0;
    private TacticalEngine.Config lastConfig;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        initEngine(); initTts(); showSetup();
    }

    private void initEngine(){
        engine=new TacticalEngine(readAssetLines("combos_named.txt"),readAssetLines("combos_codes.txt"));
        String validation=engine.validateKnowledge();
        if(!validation.isEmpty()) throw new IllegalStateException(validation);
        String saved=prefs.getString("combat_state","");
        if(saved.startsWith("R7|")||saved.startsWith("R8|")) engine.importCombatState(saved);
    }

    private List<String> readAssetLines(String name){
        List<String> out=new ArrayList<>();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(getAssets().open(name)))){
            String s; while((s=r.readLine())!=null){s=s.trim();if(!s.isEmpty())out.add(s);}
        }catch(Exception ignored){}
        return out;
    }

    private void initTts(){
        tts=new TextToSpeech(this,status->{
            if(status==TextToSpeech.SUCCESS){ttsReady=true;tts.setSpeechRate(1.05f);tts.setPitch(0.96f);}
        });
    }

    private GradientDrawable box(int color,int stroke,int radius){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;
    }
    private int dp(int v){return Math.max(1,Math.round(v*getResources().getDisplayMetrics().density));}
    private TextView tv(String s,float size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(12),dp(9),dp(12),dp(9));if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private Button btn(String s,int color){
        Button b=new Button(this);b.setText(s);b.setTextColor(WHITE);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setBackground(box(color,EDGE,14));return b;
    }
    private LinearLayout row(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.HORIZONTAL);x.setGravity(Gravity.CENTER);return x;}
    private LinearLayout.LayoutParams lp(int w,int h,float wt){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h,wt);p.setMargins(dp(4),dp(4),dp(4),dp(4));return p;}
    private TextView label(String s){TextView t=tv(s,12,MUTED,true);t.setPadding(dp(4),dp(14),dp(4),dp(4));return t;}
    private Spinner spinner(String[] vals){
        Spinner s=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,vals){
            @Override public View getView(int p,View c,android.view.ViewGroup g){TextView v=(TextView)super.getView(p,c,g);v.setTextColor(WHITE);v.setBackgroundColor(PANEL);v.setPadding(dp(12),dp(12),dp(12),dp(12));return v;}
            @Override public View getDropDownView(int p,View c,android.view.ViewGroup g){TextView v=(TextView)super.getDropDownView(p,c,g);v.setTextColor(WHITE);v.setBackgroundColor(PANEL);v.setPadding(dp(12),dp(14),dp(12),dp(14));return v;}
        };s.setAdapter(a);s.setBackground(box(PANEL,EDGE,12));return s;
    }

    private void showSetup(){
        handler.removeCallbacksAndMessages(null); paused=false;ghostReplay=false;branchHidden=false;
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(BG);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(18),dp(16),dp(24));root.setBackgroundColor(BG);sc.addView(root);
        TextView title=tv("R8 Ω ALL-SOURCE FIGHT INTELLIGENCE",27,RED,true);title.setGravity(Gravity.CENTER);root.addView(title);
        TextView sub=tv("Reaction graph • conditioning betrayal • ringcraft • source doctrines • skill shards • exact ghost memory",13,CYAN,false);sub.setGravity(Gravity.CENTER);root.addView(sub);

        modeSpin=spinner(TacticalEngine.MODES);oppSpin=spinner(TacticalEngine.OPPONENTS);profileSpin=spinner(TacticalEngine.PROFILES);doctrineSpin=spinner(TacticalEngine.DOCTRINES);
        rangeSpin=spinner(new String[]{"Boxing","Long","Pocket","Clinch","Ground","Top"});stanceSpin=spinner(new String[]{"Orthodox","Southpaw","Switch"});
        roundSpin=spinner(new String[]{"3 minutes","5 minutes","10 minutes"});revealSpin=spinner(new String[]{"0.8 sec","1.4 sec","2.0 sec","3.0 sec"});
        root.addView(label("MODE"));root.addView(modeSpin);
        root.addView(label("RIVAL BRAIN"));root.addView(oppSpin);
        root.addView(label("FIGHTER / STYLE PROFILE"));root.addView(profileSpin);
        root.addView(label("SOURCE DOCTRINE"));root.addView(doctrineSpin);
        root.addView(label("START RANGE"));root.addView(rangeSpin);
        root.addView(label("STANCE"));root.addView(stanceSpin);
        LinearLayout rs=row(); LinearLayout left=new LinearLayout(this),right=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);right.setOrientation(LinearLayout.VERTICAL);
        left.addView(label("ROUND"));left.addView(roundSpin);right.addView(label("BRANCH REVEAL"));right.addView(revealSpin);rs.addView(left,lp(0,-2,1));rs.addView(right,lp(0,-2,1));root.addView(rs);
        root.addVie