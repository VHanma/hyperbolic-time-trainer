import com.vhanma.hanmacombocalleromega833.TacticalEngine;
import java.util.*;import java.nio.file.*;import java.io.*;import java.lang.reflect.*;
public class R833Harness{
 static void die(String s){throw new RuntimeException(s);} 
 static List<String> lines(String p)throws Exception{return Files.readAllLines(Paths.get(p));}
 static String side(String s){String l=s.toLowerCase(Locale.US);if(l.contains("left"))return "left";if(l.contains("right"))return "right";return "";}
 static boolean strike(String s){String l=s.toLowerCase(Locale.US);if(l.startsWith("fake ")||l.startsWith("crossblock")||l.startsWith("slip")||l.startsWith("roll")||l.startsWith("high shield")||l.contains("parry")||l.contains("catch"))return false;for(String w:new String[]{"jab","straight","hook","uppercut","overhand","elbow","knee","kick","teep","backfist","hammerfist"})if(l.contains(w))return true;return false;}
 static boolean defense(String s){String l=s.toLowerCase(Locale.US);return l.startsWith("slip")||l.startsWith("roll")||l.startsWith("crossblock")||l.startsWith("high shield")||l.contains("parry")||l.contains("catch")||l.contains("shield");}
 static boolean takedown(String s){String l=s.toLowerCase(Locale.US);return l.contains("double leg")||l.contains("single leg")||l.contains("body lock takedown")||l.contains("ankle pick")||l.contains("snapdown");}
 static void checkLibrary(List<String> lib){
   if(lib.size()!=898)die("library count "+lib.size());
   Set<String> u=new HashSet<>();int hooks=0,pk=0,kp=0,pt=0,ft=0;
   for(String x:lib){String l=x.toLowerCase(Locale.US);u.add(x);if(l.matches(".*\\b(lead|rear)\\b.*"))die("lead/rear leak "+x);if(l.matches(".*\\b(front|back) (hand|leg|arm|foot|side)\\b.*"))die("front/back limb leak "+x);String[] p=x.split(",\\s*");
     for(int i=0;i<p.length-1;i++){if(defense(p[i])){boolean earns=false;for(int j=i+1;j<Math.min(p.length,i+4);j++)if(strike(p[j])||takedown(p[j])){earns=true;break;}if(!earns)die("defense no offense "+x+" @"+p[i]);}}
     if(p.length>=2&&strike(p[p.length-2])&&defense(p[p.length-1])){String a=side(p[p.length-2]),b=side(p[p.length-1]);if(!a.isEmpty()&&!a.equals(b))die("end side mismatch "+x);if(p[p.length-2].toLowerCase().contains("hook")){hooks++;if(!p[p.length-1].toLowerCase().startsWith("high shield"))die("hook lacks shield "+x);}}
     if(strike(p[p.length-1]))die("unguarded terminal strike "+x);
     if(l.matches(".*(jab|straight|hook|uppercut|overhand).*(kick|teep).*"))pk++;
     if(l.matches(".*(kick|teep).*(jab|straight|hook|uppercut|overhand).*"))kp++;
     if(l.matches(".*(jab|straight|hook|uppercut|overhand).*(double leg|single leg|body lock takedown|ankle pick|snapdown).*"))pt++;
     if(l.matches(".*fake (double leg|single leg|body lock|ankle pick|takedown).*(jab|straight|hook|uppercut|overhand).*"))ft++;
   }
   if(u.size()!=898)die("duplicates "+u.size());if(pk<100||kp<100||pt<100||ft<100)die("transition coverage pk="+pk+" kp="+kp+" pt="+pt+" ft="+ft);
   System.out.println("LIB PASS unique="+u.size()+" hookEnds="+hooks+" pk="+pk+" kp="+kp+" pt="+pt+" ft="+ft);
 }
 static String lastFamily(TacticalEngine e)throws Exception{Field f=TacticalEngine.class.getDeclaredField("lastActions");f.setAccessible(true);List<?> a=(List<?>)f.get(e);if(a.isEmpty())return "";Object x=a.get(a.size()-1);Field ff=x.getClass().getDeclaredField("family");ff.setAccessible(true);return (String)ff.get(x);}
 static boolean strikeFam(String f){return Arrays.asList("jab","straight","hook","uppercut","overhand","lowkick","bodykick","headkick","sidekick","spinkick","teep","knee","elbow").contains(f);}
 static TacticalEngine.Config cfg(String m){TacticalEngine.Config c=new TacticalEngine.Config();c.mode=m;c.stance="Southpaw";c.opponent="Adaptive Rival";c.profile="Hanma Adaptive";c.doctrine="Omega Synthesis";c.difficulty=10;return c;}
 public static void main(String[]z)throws Exception{
   List<String>a=lines("combo-caller/app/src/main/assets/combos_named.txt"),b=lines("combo-caller/app/src/main/assets/combos_codes.txt");ArrayList<String>all=new ArrayList<>(a);all.addAll(b);checkLibrary(all);
   TacticalEngine e=new TacticalEngine(a,b);if(e.getRulesetLibraryCount()!=898)die("engine library "+e.getRulesetLibraryCount());String v=e.validateKnowledge();if(!v.isEmpty())die(v);
   e.setConfig(cfg("Hanma Ruleset"));for(int i=0;i<1800;i++){TacticalEngine.Result r=e.next("RESET");if(!r.cue.matches("(?i).*(left|right).*"))die("no side "+r.cue);if(r.cue.matches("(?i).*\\b(lead|rear)\\b.*"))die("ruleset leak "+r.cue);}
   String[] modes={"Boxing Only","All Striking","Muay Thai","Kickboxing","MMA","52 Blocks Boxing","Adaptive Fight IQ"};long calls=0;for(String m:modes){TacticalEngine t=new TacticalEngine(a,b);t.setConfig(cfg(m));for(int i=0;i<320;i++){TacticalEngine.Result r=t.next(i%17==0?"CLEAN":"RESET");calls++;String q=r.cue.toLowerCase(Locale.US);if(q.matches(".*\\b(lead|rear)\\b.*"))die("adaptive side leak "+m+" => "+r.cue);if(q.contains("front hand")||q.contains("back hand")||q.contains("front leg")||q.contains("back leg"))die("front/back limb leak "+r.cue);if(!t.lastResponseSolvedThreat())die("unsolved "+m+" "+r.cue);if(q.contains("degree")||q.contains("°")||q.contains("straight-block"))die("wording leak "+r.cue);String[] p=q.split("\\s*→\\s*");if(p.length<2)die("defense alone "+r.cue);String last=p[p.length-1].trim();String prev=p.length>1?p[p.length-2].trim():"";String fam=lastFamily(t);if(strikeFam(fam)){if(!defense(last))die("unguarded adaptive strike "+fam+" => "+r.cue);String as=side(prev),ds=side(last);if(!as.isEmpty()&&!as.equals(ds))die("adaptive terminal side "+r.cue);if(fam.equals("hook")&&!last.startsWith("high shield"))die("adaptive hook shield "+r.cue);}}
   }
   TacticalEngine s=new TacticalEngine(a,b);TacticalEngine.Config sc=cfg("Source Locked");sc.discipline="Boxing Only";s.setConfig(sc);for(int i=0;i<260;i++){String q=s.next("RESET").cue.toLowerCase(Locale.US);if(q.matches(".*\\b(lead|rear)\\b.*"))die("source side leak "+q);if(q.contains("front hand")||q.contains("back hand")||q.contains("front leg")||q.contains("back leg"))die("source front/back limb "+q);} 
   System.out.println("R8.3.3 ORTHODOX RULESET PASS adaptiveCalls="+calls+" sourceCalls="+s.getSourceCallCount());
 }
}
