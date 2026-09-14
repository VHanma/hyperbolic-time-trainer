import com.vhanma.hanmacombocalleromega834.TacticalEngine;
import java.nio.file.*;import java.util.*;
public class R834Original795Harness{
 static void die(String s){throw new RuntimeException(s);} 
 static List<String> lines(String p)throws Exception{return Files.readAllLines(Paths.get(p));}
 public static void main(String[]z)throws Exception{
  TacticalEngine e=new TacticalEngine(lines("/tmp/combo-caller/app/src/main/assets/combos_named.txt"),lines("/tmp/combo-caller/app/src/main/assets/combos_codes.txt"),lines("/tmp/combo-caller/app/src/main/assets/mycombat_original_795.txt"));
  String v=e.validateKnowledge();if(!v.isEmpty())die(v);if(e.getOriginalComboCount()!=795)die("count "+e.getOriginalComboCount());
  LinkedHashMap<String,Integer> exp=new LinkedHashMap<>();exp.put("Boxing",100);exp.put("Kickboxing",100);exp.put("Muay Thai",95);exp.put("MMA",100);exp.put("Combat Sambo",100);exp.put("BJJ",150);exp.put("Wrestling",100);exp.put("Judo",50);
  long calls=0;for(Map.Entry<String,Integer>x:exp.entrySet()){
   if(e.getOriginalStyleCount(x.getKey())!=x.getValue())die(x.getKey()+" "+e.getOriginalStyleCount(x.getKey()));
   TacticalEngine.Config c=new TacticalEngine.Config();c.mode="MyCombat Originals (795)";c.discipline=x.getKey();e.setConfig(c);Set<String>u=new HashSet<>();
   for(int i=0;i<Math.min(60,x.getValue());i++){TacticalEngine.Result r=e.next("RESET");calls++;if(!r.state.endsWith(x.getKey()))die("state "+r.state);if(!"VERBATIM".equals(r.pattern))die("pattern");if(!r.why.contains("No Ω edits"))die("edited marker");u.add(r.cue);} 
   if(u.size()<Math.min(30,x.getValue()))die("repeat collapse "+x.getKey()+" "+u.size());System.out.println(x.getKey()+" count="+x.getValue()+" sampleUnique="+u.size());
  }
  if(e.getRulesetLibraryCount()!=898)die("ruleset changed "+e.getRulesetLibraryCount());if(e.getSourceCallCount()<130)die("source locked changed "+e.getSourceCallCount());
  System.out.println("R8.3.4 ORIGINAL 795 PASS total="+e.getOriginalComboCount()+" calls="+calls+" ruleset="+e.getRulesetLibraryCount()+" sourceLocked="+e.getSourceCallCount());
 }
}
