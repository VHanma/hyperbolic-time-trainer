from pathlib import Path
import sys
p=Path(sys.argv[1]);s=p.read_text()
def r(a,b):
 global s
 if a not in s: raise SystemExit('missing marker: '+a[:80])
 s=s.replace(a,b,1)
r('import android.speech.tts.TextToSpeech;','import android.speech.tts.TextToSpeech;\nimport android.speech.tts.UtteranceProgressListener;')
r('private boolean ttsReady=false, paused=false, whyOpen=false, branchHidden=false, ghostReplay=false;','private boolean ttsReady=false, paused=false, whyOpen=false, branchHidden=false, ghostReplay=false, rapidEnabled=false;')
r('private long roundEndsAt=0L, pausedRemaining=0L;','private long roundEndsAt=0L, pausedRemaining=0L;\n    private long rapidGapMs=700L;\n    private int utteranceSeq=0;\n    private String activeUtterance="";')
r('private Button pauseBtn;','private Button pauseBtn, rapidBtn;')
r('private Spinner modeSpin, oppSpin, rangeSpin, stanceSpin, profileSpin, doctrineSpin, roundSpin, revealSpin;','private Spinner modeSpin, oppSpin, rangeSpin, stanceSpin, profileSpin, doctrineSpin, roundSpin, revealSpin, rapidSpin;')
r('''    private void initTts(){
        tts=new TextToSpeech(this,status->{
            if(status==TextToSpeech.SUCCESS){ttsReady=true;tts.setSpeechRate(1.05f);tts.setPitch(0.96f);}
        });
    }
''','''    private void initTts(){
        tts=new TextToSpeech(this,status->{
            if(status==TextToSpeech.SUCCESS){
                ttsReady=true;tts.setSpeechRate(1.05f);tts.setPitch(0.96f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                    @Override public void onStart(String id){}
                    @Override public void onDone(String id){handler.post(()->onSpeechFinished(id));}
                    @Override public void onError(String id){handler.post(()->onSpeechFinished(id));}
                });
            }
        });
    }

    private void onSpeechFinished(String id){
        if(id==null||!id.equals(activeUtterance))return;
        if(rapidEnabled&&!paused&&!ghostReplay&&!branchHidden&&current!=null)scheduleRapid();
    }
    private String nextUtteranceId(String kind){activeUtterance="r82_"+kind+"_"+(++utteranceSeq);return activeUtterance;}
    private void scheduleRapid(){handler.removeCallbacks(rapidNext);if(rapidEnabled&&!paused&&!ghostReplay&&!branchHidden)handler.postDelayed(rapidNext,Math.max(250L,rapidGapMs));}
    private final Runnable rapidNext=new Runnable(){@Override public void run(){if(!rapidEnabled||paused||ghostReplay||branchHidden)return;if(roundEndsAt>0L&&System.currentTimeMillis()>=roundEndsAt){endRound();return;}nextCall("RESET");}};
''')
r('handler.removeCallbacksAndMessages(null); paused=false;ghostReplay=false;branchHidden=false;','handler.removeCallbacksAndMessages(null); paused=false;ghostReplay=false;branchHidden=false;rapidEnabled=false;')
r('roundSpin=spinner(new String[]{"3 minutes","5 minutes","10 minutes"});revealSpin=spinner(new String[]{"0.8 sec","1.4 sec","2.0 sec","3.0 sec"});','roundSpin=spinner(new String[]{"3 minutes","5 minutes","10 minutes"});revealSpin=spinner(new String[]{"0.8 sec","1.4 sec","2.0 sec","3.0 sec"});\n        rapidSpin=spinner(new String[]{"Off","Blitz • 0.35 sec gap","Fast • 0.70 sec gap","Flow • 1.20 sec gap","Technical • 2.00 sec gap"});rapidSpin.setSelection(Math.max(0,Math.min(4,prefs.getInt("rapid_mode",0))));')
r('left.addView(label("ROUND"));left.addView(roundSpin);right.addView(label("BRANCH REVEAL"));right.addView(revealSpin);rs.addView(left,lp(0,-2,1));rs.addView(right,lp(0,-2,1));root.addView(rs);','left.addView(label("ROUND"));left.addView(roundSpin);right.addView(label("BRANCH REVEAL"));right.addView(revealSpin);rs.addView(left,lp(0,-2,1));rs.addView(right,lp(0,-2,1));root.addView(rs);\n        root.addView(label("RAPID CALL • HANDS-FREE CADENCE"));root.addView(rapidSpin);\n        TextView rapidHelp=tv("Rapid waits until the spoken call finishes, adds the selected gap, then automatically calls the next exchange. No overlapping voice and no button presses required.",12,CYAN,false);root.addView(rapidHelp);')
r('private int selectedRevealMs(){int p=revealSpin.getSelectedItemPosition();return p==0?800:p==1?1400:p==2?2000:3000;}','private int selectedRevealMs(){int p=revealSpin.getSelectedItemPosition();return p==0?800:p==1?1400:p==2?2000:3000;}\n    private long selectedRapidGapMs(){int p=rapidSpin==null?0:rapidSpin.getSelectedItemPosition();return p==1?350L:p==2?700L:p==3?1200L:p==4?2000L:700L;}\n    private String rapidCadenceName(){if(rapidGapMs<=400)return "BLITZ";if(rapidGapMs<=800)return "FAST";if(rapidGapMs<=1400)return "FLOW";return "TECHNICAL";}')
r('lastConfig=c;lastRoundMs=selectedRoundMs();engine.setConfig(c);','lastConfig=c;lastRoundMs=selectedRoundMs();rapidEnabled=rapidSpin.getSelectedItemPosition()>0;rapidGapMs=selectedRapidGapMs();prefs.edit().putInt("rapid_mode",rapidSpin.getSelectedItemPosition()).apply();engine.setConfig(c);')
r('LinearLayout r4=row();Button whyB=btn("Ω WHY",PANEL);whyB.setOnClickListener(v->toggleWhy());Button ledger=btn("SOURCE LEDGER",PANEL);ledger.setOnClickListener(v->showLedger());r4.addView(whyB,lp(0,dp(58),1));r4.addView(ledger,lp(0,dp(58),1));fightBox.addView(r4);','LinearLayout r4=row();Button whyB=btn("Ω WHY",PANEL);whyB.setOnClickListener(v->toggleWhy());rapidBtn=btn("RAPID",rapidEnabled?Color.rgb(24,105,60):PANEL);rapidBtn.setOnClickListener(v->toggleRapid());Button ledger=btn("SOURCE LEDGER",PANEL);ledger.setOnClickListener(v->showLedger());r4.addView(whyB,lp(0,dp(58),1));r4.addView(rapidBtn,lp(0,dp(58),1));r4.addView(ledger,lp(0,dp(58),1));fightBox.addView(r4);updateRapidButton();')
r('if(paused||ghostReplay)return;handler.removeCallbacks(branchReveal);countFeedback(feedback);','if(paused||ghostReplay)return;handler.removeCallbacks(branchReveal);handler.removeCallbacks(rapidNext);countFeedback(feedback);')
r('private void speakOpponentOnly(){if(!ttsReady||current==null)return;tts.stop();tts.speak(TacticalEngine.toSpeech(current.opponentEvent)+". Solve it.",TextToSpeech.QUEUE_FLUSH,null,"r8_event");}','private void speakOpponentOnly(){if(!ttsReady||current==null){scheduleRapid();return;}tts.stop();tts.speak(TacticalEngine.toSpeech(current.opponentEvent)+". Solve it.",TextToSpeech.QUEUE_FLUSH,null,nextUtteranceId("event"));}')
r('private void speakSolutionOnly(){if(!ttsReady||current==null)return;tts.speak("Solution. "+TacticalEngine.toSpeech(current.cue),TextToSpeech.QUEUE_FLUSH,null,"r8_solution");}','private void speakSolutionOnly(){if(!ttsReady||current==null){scheduleRapid();return;}tts.speak("Solution. "+TacticalEngine.toSpeech(current.cue),TextToSpeech.QUEUE_FLUSH,null,nextUtteranceId("solution"));}')
r('private void speakCurrent(){if(!ttsReady||current==null)return;tts.stop();String text=TacticalEngine.toSpeech(current.opponentEvent)+". Answer. "+TacticalEngine.toSpeech(current.cue);tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"r8_call");}','private void speakCurrent(){if(current==null)return;if(!ttsReady){scheduleRapid();return;}tts.stop();String text=TacticalEngine.toSpeech(current.opponentEvent)+". Answer. "+TacticalEngine.toSpeech(current.cue);tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,nextUtteranceId("call"));}')
r('private void toggleWhy(){whyOpen=!whyOpen;why.setVisibility(whyOpen?View.VISIBLE:View.GONE);}','private void toggleWhy(){whyOpen=!whyOpen;why.setVisibility(whyOpen?View.VISIBLE:View.GONE);}\n    private void updateRapidButton(){if(rapidBtn!=null){rapidBtn.setText(rapidEnabled?"RAPID ON • "+rapidCadenceName():"RAPID OFF");rapidBtn.setBackground(box(rapidEnabled?Color.rgb(24,105,60):PANEL,EDGE,14));}}\n    private void toggleRapid(){if(ghostReplay)return;rapidEnabled=!rapidEnabled;if(rapidGapMs<250L)rapidGapMs=700L;handler.removeCallbacks(rapidNext);updateRapidButton();Toast.makeText(this,rapidEnabled?"Rapid Call "+rapidCadenceName()+" enabled":"Rapid Call off",Toast.LENGTH_SHORT).show();if(rapidEnabled&&!paused&&current!=null&&!branchHidden)speakCurrent();}')
r('handler.removeCallbacks(branchReveal);if(ttsReady)tts.stop();','handler.removeCallbacks(branchReveal);handler.removeCallbacks(rapidNext);if(ttsReady)tts.stop();')
r('else{paused=false;roundEndsAt=System.currentTimeMillis()+pausedRemaining;pauseBtn.setText("PAUSE");handler.post(timerTick);if(branchHidden)handler.postDelayed(branchReveal,engine.getConfig().revealDelayMs);}','else{paused=false;roundEndsAt=System.currentTimeMillis()+pausedRemaining;pauseBtn.setText("PAUSE");handler.post(timerTick);if(branchHidden)handler.postDelayed(branchReveal,engine.getConfig().revealDelayMs);else if(rapidEnabled&&current!=null)speakCurrent();}')
r('handler.removeCallbacks(timerTick);handler.removeCallbacks(branchReveal);if(ttsReady)tts.stop();','handler.removeCallbacks(timerTick);handler.removeCallbacks(branchReveal);handler.removeCallbacks(rapidNext);if(ttsReady)tts.stop();')
r('@Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}','@Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);activeUtterance="";if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}')
p.write_text(s)
