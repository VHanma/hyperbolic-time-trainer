package com.vhanma.signalline

import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.absoluteValue
import kotlin.math.sin

private val Bg = Color(0xFF080A0F)
private val Panel = Color(0xFF11151F)
private val Cyan = Color(0xFF7DF9FF)
private val Violet = Color(0xFFB48CFF)
private val Soft = Color(0xFFAAB5C8)
private val Good = Color(0xFF7DFFB2)

enum class Screen { HOME, TARGET_SETTER, SESSION, BOT, HISTORY, ACADEMY }
enum class Stage(val short:String, val title:String) { I("I","Ideogram + Gestalt"), II("II","Sensory"), III("III","Dimensional + Sketch"), IV("IV","Matrix"), V("V","Interrogation"), VI("VI","Model / Synthesis") }
data class Target(val id:String,val name:String,val family:String,val attrs:List<String>,val form:Int,val seed:Int)
data class SessionRecord(val trn:String,val target:String,val guess:String,val score:Int,val mode:String,val report:String,val commitment:String="",val bestStage:String="")
enum class BotMode(val label:String){ TRN_ONLY("TRN ONLY"), TRN_ENTROPY("TRN + ENTROPY"), REPLAY_CONTROL("REPLAY CONTROL") }
data class BotResult(val gestalt:String,val sensory:List<String>,val spatial:List<String>,val synthesis:String,val confidence:Int)
data class ChatLine(val speaker:String,val text:String)

private val targets = listOf(
 Target("T01","Waterfall Canyon","natural", listOf("water","cold","rushing","vertical","rock","open","mist","large"),0,11),
 Target("T02","Suspension Bridge","structure", listOf("metal","long","elevated","repeating","water","open","linear","large"),1,22),
 Target("T03","Desert Pyramid","structure", listOf("stone","warm","angular","dry","massive","open","ancient","symmetrical"),2,33),
 Target("T04","Radio Telescope","structure", listOf("metal","curved","dish","open","technical","large","quiet","sky"),3,44),
 Target("T05","Ice Cave","natural", listOf("cold","blue","enclosed","smooth","hard","echo","curved","wet"),4,55),
 Target("T06","Volcanic Crater","natural", listOf("hot","rock","circular","smoke","open","rough","energy","depth"),5,66),
 Target("T07","City Skyscraper","structure", listOf("vertical","glass","repeating","hard","bright","urban","tall","angular"),6,77),
 Target("T08","Forest Stream","natural", listOf("water","green","cool","flowing","wood","organic","soft","open"),7,88),
 Target("T09","Stone Arch","structure", listOf("stone","curved","opening","dry","massive","rough","outdoor","warm"),8,99),
 Target("T10","Spiral Stairwell","interior", listOf("curved","repeating","enclosed","hard","vertical","echo","metal","motion"),9,111),
 Target("T11","Harbor Crane","industrial", listOf("metal","water","vertical","mechanical","open","angular","large","industrial"),10,122),
 Target("T12","Geodesic Dome","structure", listOf("curved","repeating","enclosed","bright","geometric","large","smooth","structure"),11,133)
)

class SignalViewModel(app:Application): AndroidViewModel(app) {
 var screen by mutableStateOf(Screen.HOME)
 var active by mutableStateOf(false)
 var stage by mutableStateOf(Stage.I)
 var trn by mutableStateOf("")
 var target by mutableStateOf<Target?>(null)
 var locked by mutableStateOf(false)
 var revealed by mutableStateOf(false)
 var entries by mutableStateOf(mapOf<Stage,String>())
 var aols by mutableStateOf(listOf<String>())
 var points by mutableStateOf(listOf<List<Offset>>())
 var gestalt by mutableStateOf("")
 var confidence by mutableIntStateOf(50)
 var selectedCandidate by mutableStateOf<Target?>(null)
 var botResult by mutableStateOf<BotResult?>(null)
 var botMode by mutableStateOf(BotMode.TRN_ENTROPY)
 var commitment by mutableStateOf("")
 var candidatePacket by mutableStateOf<List<Target>>(emptyList())
 var viewerChat by mutableStateOf(listOf<ChatLine>())
 var revealedCustomTarget by mutableStateOf("")
 private var targetSalt = ""
 private var targetSeal = ""
 var history by mutableStateOf(loadHistory())
 private val rng=SecureRandom()
 private var started=0L
 fun startHuman(){ reset(); trn=randomTrn(); target=targets[rng.nextInt(targets.size)]; started=SystemClock.elapsedRealtime(); active=true; screen=Screen.SESSION }
 fun openTargetSetter(){ reset(); screen=Screen.TARGET_SETTER }
 fun reset(){ stage=Stage.I; locked=false; revealed=false; entries=emptyMap(); aols=emptyList(); points=emptyList(); gestalt=""; selectedCandidate=null; botResult=null; commitment=""; candidatePacket=emptyList(); viewerChat=emptyList(); revealedCustomTarget=""; targetSalt=""; targetSeal="" }
 fun setEntry(v:String){ entries=entries.toMutableMap().also{it[stage]=v} }
 fun addAol(v:String){ if(v.isNotBlank()) aols=aols+v.trim() }
 fun next(){ val i=Stage.entries.indexOf(stage); if(i<Stage.entries.lastIndex) stage=Stage.entries[i+1] }
 fun prev(){ val i=Stage.entries.indexOf(stage); if(i>0) stage=Stage.entries[i-1] }
 fun lock(){ commitment=sha256("$trn|${fullReport()}|${confidence}|${points.size}"); locked=true }
 fun reveal(){ if(locked) revealed=true }
 fun candidates():List<Target>{ if(candidatePacket.isNotEmpty()) return candidatePacket; val t=target?:return emptyList(); val dec=targets.filter{it.id!=t.id && it.family==t.family}.shuffled().take(2)+targets.filter{it.id!=t.id && it.family!=t.family}.shuffled().take(2); candidatePacket=(dec+t).shuffled(); return candidatePacket }
 fun judge(c:Target){ selectedCandidate=c; val t=target?:return; val s=if(c.id==t.id)100 else overlapScore(c,t); val best=bestStage(t); save(SessionRecord(trn,t.name,c.name,s,"HUMAN",fullReport(),commitment,best)) }
 fun cycleBotMode(){ botMode=BotMode.entries[(BotMode.entries.indexOf(botMode)+1)%BotMode.entries.size] }
 fun sealTargetAndRun(raw:String):Boolean{
  val clean=raw.trim(); if(clean.isBlank()) return false
  val saltBytes=ByteArray(16).also{rng.nextBytes(it)}
  targetSalt=saltBytes.joinToString(""){"%02x".format(it.toInt() and 0xff)}
  targetSeal=sha256("$targetSalt|$clean")
  trn=randomTrn(); target=null; revealedCustomTarget=""; revealed=false
  runSealedViewer(); return true
 }
 private fun runSealedViewer(){
  val channel=when(botMode){ BotMode.TRN_ONLY->trn; BotMode.TRN_ENTROPY->"$trn|${System.nanoTime()}|${rng.nextLong()}"; BotMode.REPLAY_CONTROL->"$trn|REPLAY-CONTROL" }
  val pass=(1..3).map{ hashInts("$channel|$it") }
  val g=listOf("land","water","structure","biological","energetic","interior","open natural","industrial")
  val sens=listOf("cold","warm","hard","smooth","rough","bright","dark","metallic","wet","dry","rushing","quiet","echoing","organic","mineral","vibrating")
  val spa=listOf("vertical","horizontal","curved","angular","large","small","open","enclosed","repeating","central mass","depth","elevated","circular","linear")
  val gs=g[pass.sumOf{it[0]}.absoluteValue%g.size]
  val ss=(0..5).map{ sens[pass[it%3][(it+1)%8].absoluteValue%sens.size] }.distinct()
  val sp=(0..4).map{ spa[pass[it%3][(it+2)%8].absoluteValue%spa.size] }.distinct()
  val synth="Primary gestalt: $gs. Strong qualities: ${ss.joinToString(", ")}. Spatial pattern: ${sp.joinToString(", ")}."
  val conf=42+(pass[0][3].absoluteValue%47)
  botResult=BotResult(gs,ss,sp,synth,conf)
  commitment=sha256("VIEWER|${botMode.label}|$trn|$synth|$conf")
  viewerChat=listOf(
   ChatLine("MIRA","I have the reference. I am staying blind to the target."),
   ChatLine("MIRA","My first gestalt is $gs."),
   ChatLine("MIRA","The strongest sensory hits are ${ss.take(4).joinToString(", ")}.")
  )
  locked=true; active=true; screen=Screen.BOT
 }
 fun miraInitiative(){
  val r=botResult?:return
  val pool=listOf(
   "I want to stay with ${r.spatial.firstOrNull() ?: r.gestalt} for a moment. It feels more important than naming the target.",
   "Something in the signal keeps pulling me back to ${r.sensory.take(2).joinToString(" and ")}. I want to follow that thread.",
   "I am getting a second layer now. The combination of ${r.gestalt} and ${r.spatial.take(2).joinToString(", ")} feels worth probing.",
   "I want to set the obvious guess aside and sit with the raw impressions a little longer.",
   "There is one impression I do not want to lose: ${r.sensory.firstOrNull() ?: r.gestalt}. I want to anchor on that before we move on.",
   "I feel like changing angle. I want to look at what surrounds the main feature rather than the feature itself.",
   "I want to check for a biological or human presence next, without forcing it.",
   "I want to zoom out. The overall relationship between ${r.spatial.take(3).joinToString(", ")} may matter more than any single detail."
  )
  val idx=hashInts("$trn|MIRA-INITIATIVE|${viewerChat.size}|${System.nanoTime()}")[0].absoluteValue%pool.size
  viewerChat=viewerChat+ChatLine("MIRA",pool[idx])
 }
 fun askViewer(q:String){
  val clean=q.trim(); if(clean.isBlank())return
  val r=botResult?:return
  val pool=listOf(
   "I keep coming back to ${r.gestalt}, with ${r.sensory.take(3).joinToString(", ")}.",
   "Spatially I keep getting ${r.spatial.take(3).joinToString(", ")}. That feels stronger than any specific identification.",
   "I want to separate raw signal from AOL. Raw qualities: ${r.sensory.joinToString(", ")}.",
   "The strongest shape relationship right now is ${r.spatial.joinToString(", ")}.",
   "My current synthesis is: ${r.synthesis}"
  )
  val idx=hashInts("$trn|CHAT|$clean|${viewerChat.size}")[0].absoluteValue%pool.size
  viewerChat=viewerChat+ChatLine("YOU",clean)+ChatLine("MIRA",pool[idx])
 }
 fun verifyTarget(raw:String):Boolean{
  if(!locked)return false
  val clean=raw.trim(); if(clean.isBlank())return false
  if(sha256("$targetSalt|$clean")!=targetSeal)return false
  revealedCustomTarget=clean; revealed=true
  val r=botResult
  save(SessionRecord(trn,clean,"Mira remote-viewing report",0,"BOT-CUSTOM-${botMode.label}",r?.synthesis.orEmpty(),commitment,"I-III"))
  return true
 }
 fun targetSealPreview()=targetSeal.take(16)
 fun fullReport()=Stage.entries.joinToString("\n"){"${it.short}: ${entries[it].orEmpty()}"}+"\nAOL: "+aols.joinToString(", ")
 private fun randomTrn()="%04d-%04d".format(rng.nextInt(10000),rng.nextInt(10000))
 private fun overlapScore(a:Target,b:Target)=((a.attrs.intersect(b.attrs.toSet()).size.toFloat()/b.attrs.size)*100).toInt()
 private fun stageScore(st:Stage,t:Target):Int{ val txt=(if(st==Stage.I) gestalt+" "+entries[st].orEmpty() else entries[st].orEmpty()).lowercase(); if(txt.isBlank())return 0; return (t.attrs.count{txt.contains(it.lowercase())}*100/t.attrs.size).coerceIn(0,100) }
 private fun bestStage(t:Target):String=Stage.entries.maxByOrNull{stageScore(it,t)}?.let{"${it.short}:${stageScore(it,t)}%"}.orEmpty()
 private fun sha256(v:String)=MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString(""){"%02x".format(it)}
 private fun hashInts(s:String):IntArray{ val d=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()); return IntArray(8){i->((d[i*4].toInt() shl 24) xor (d[i*4+1].toInt() shl 16) xor (d[i*4+2].toInt() shl 8) xor d[i*4+3].toInt())} }
 private fun prefs()=getApplication<Application>().getSharedPreferences("signal",0)
 private fun save(r:SessionRecord){ history=(listOf(r)+history).take(100); prefs().edit().putString("history",history.joinToString("§"){listOf(it.trn,it.target,it.guess,it.score,it.mode,it.report.replace("§"," ").replace("¦"," "),it.commitment,it.bestStage).joinToString("¦")}).apply() }
 private fun loadHistory():List<SessionRecord>{ val raw=prefs().getString("history","").orEmpty(); if(raw.isBlank())return emptyList(); return raw.split("§").mapNotNull{p->val x=p.split("¦"); if(x.size<6)null else SessionRecord(x[0],x[1],x[2],x[3].toIntOrNull()?:0,x[4],x.getOrElse(5){""},x.getOrElse(6){""},x.getOrElse(7){""})} }
}

class MainActivity:ComponentActivity(){ override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{SignalApp()}} }

@Composable fun SignalApp(vm:SignalViewModel=viewModel()){
 MaterialTheme(colorScheme=darkColorScheme(background=Bg,surface=Panel,primary=Cyan,secondary=Violet,onBackground=Color.White,onSurface=Color.White)){
  Surface(Modifier.fillMaxSize(),color=Bg){ when(vm.screen){Screen.HOME->Home(vm);Screen.TARGET_SETTER->TargetSetter(vm);Screen.SESSION->Session(vm);Screen.BOT->Bot(vm);Screen.HISTORY->History(vm);Screen.ACADEMY->Academy(vm)} }
 }
}

@Composable fun Header(title:String,back:(()->Unit)?=null){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){if(back!=null)IconButton(onClick=back){Icon(Icons.Default.ArrowBack,null)};Text(title,fontWeight=FontWeight.Black,fontSize=19.sp,letterSpacing=1.sp);Spacer(Modifier.weight(1f));Icon(Icons.Default.Radio,null,tint=Cyan)}}

@Composable fun Home(vm: SignalViewModel) {
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
  Header("SIGNALLINE · MIRA")
  Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(150.dp).background(Panel, RoundedCornerShape(28.dp))) {
   Canvas(Modifier.fillMaxSize()) {
    for (i in 0..9) { val y = size.height * (i / 10f); drawLine(Cyan.copy(alpha = .08f), Offset(0f, y), Offset(size.width, y)) }
    val path = Path(); for (x in 0..size.width.toInt() step 5) { val wave = (sin(x.toDouble() / 28.0) * 22.0 + sin(x.toDouble() / 9.0) * 5.0).toFloat(); val y = size.height / 2f + wave; if (x == 0) path.moveTo(x.toFloat(), y) else path.lineTo(x.toFloat(), y) }; drawPath(path, Cyan, style = Stroke(3f))
   }
   Column(Modifier.align(Alignment.Center).padding(24.dp)) { Text("MIRA\nREMOTE VIEWER", fontWeight = FontWeight.Black, fontSize = 24.sp); Text("You set the target. She works it blind.", color = Soft) }
  }
  Spacer(Modifier.height(18.dp))
  HomeButton("GIVE MIRA A TARGET", "Seal your target, then she views it blind", Icons.Default.Lock) { vm.openTargetSetter() }
  HomeButton("AUTONOMOUS PRACTICE", "Built-in blind target pool", Icons.Default.Memory) { vm.startHuman() }
  TextButton(onClick={vm.cycleBotMode()}, modifier=Modifier.padding(horizontal=16.dp)) { Text("VIEWER CHANNEL: ${vm.botMode.label}") }
  HomeButton("ACADEMY", "Protocol drills + stage guide", Icons.Default.School) { vm.screen = Screen.ACADEMY }
  HomeButton("HISTORY + ANALYTICS", "Past sessions and commitments", Icons.Default.QueryStats) { vm.screen = Screen.HISTORY }
  Text("Mira is the viewer. You are the target setter. Target plaintext is discarded before her acquisition pass.", Modifier.padding(20.dp), color = Soft, fontSize = 12.sp)
 }
}

@Composable fun HomeButton(t:String,s:String,icon:androidx.compose.ui.graphics.vector.ImageVector,on:()->Unit){Card(Modifier.padding(horizontal=16.dp,vertical=6.dp).fillMaxWidth().clickable{on()},colors=CardDefaults.cardColors(containerColor=Panel)){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Cyan,modifier=Modifier.size(28.dp));Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)){Text(t,fontWeight=FontWeight.Bold);Text(s,color=Soft,fontSize=13.sp)};Icon(Icons.Default.ChevronRight,null,tint=Soft)}}}

@Composable fun TargetSetter(vm:SignalViewModel){
 var targetText by remember{mutableStateOf("")}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){
  Header("GIVE MIRA A TARGET"){vm.screen=Screen.HOME}
  Column(Modifier.padding(16.dp)){
   Text("YOU ARE THE TARGET SETTER",fontWeight=FontWeight.Black,color=Cyan,fontSize=20.sp)
   Text("Type the target naturally. It can be a place, object, event, person, image you are looking at, or any target concept.",color=Soft,modifier=Modifier.padding(top=8.dp))
   OutlinedTextField(targetText,{targetText=it},label={Text("Target")},modifier=Modifier.fillMaxWidth().padding(top=14.dp),minLines=5)
   Card(Modifier.fillMaxWidth().padding(top=14.dp),colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(14.dp)){Text("ANTI-CHEAT SEAL",fontWeight=FontWeight.Black,color=Violet);Text("When you start, SignalLine hashes the target with a random salt and discards the plaintext. Mira receives only a TRN plus the selected experimental channel. After she commits, you type the target again and the hash must match.",color=Soft,fontSize=12.sp)}}
   Button(onClick={if(vm.sealTargetAndRun(targetText)){targetText=""}},enabled=targetText.isNotBlank(),modifier=Modifier.fillMaxWidth().padding(top=16.dp)){Icon(Icons.Default.Lock,null);Spacer(Modifier.width(8.dp));Text("SEAL TARGET + START MIRA")}
  }
 }
}

@Composable fun Session(vm:SignalViewModel){Column(Modifier.fillMaxSize()){Header("BLIND SESSION"){vm.screen=Screen.HOME};Column(Modifier.padding(horizontal=16.dp)){Text("TRN ${vm.trn}",color=Cyan,fontSize=22.sp,fontWeight=FontWeight.Black);Text("Target sealed · no feedback before commit",color=Soft,fontSize=12.sp);StageRail(vm)};Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)){if(vm.locked)Judging(vm) else StageBody(vm)};if(!vm.locked)StageNav(vm)}}

@Composable fun StageRail(vm:SignalViewModel){Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween){Stage.entries.forEach{st->Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.width(45.dp)){Surface(color=if(st==vm.stage)Violet else if(Stage.entries.indexOf(st)<Stage.entries.indexOf(vm.stage))Cyan.copy(alpha=.55f) else Panel,shape=RoundedCornerShape(50),modifier=Modifier.size(34.dp)){Box(contentAlignment=Alignment.Center){Text(st.short,fontWeight=FontWeight.Black)}};Text(st.title.substringBefore(" "),fontSize=8.sp,color=Soft,modifier=Modifier.padding(top=3.dp))}}}}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun StageBody(vm:SignalViewModel){Text("STAGE ${vm.stage.short} · ${vm.stage.title}",fontSize=21.sp,fontWeight=FontWeight.Black);Text(stageHelp(vm.stage),color=Soft,modifier=Modifier.padding(vertical=8.dp)); if(vm.stage==Stage.I){val opts=listOf("Land","Water","Structure","Biological","Energy","Interior");Text("Immediate gestalt",color=Cyan,fontSize=11.sp);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){opts.forEach{o->FilterChip(vm.gestalt==o,{vm.gestalt=o},{Text(o)})}}}; if(vm.stage==Stage.III)SketchPad(vm); var aol by remember(vm.stage){mutableStateOf("")};OutlinedTextField(vm.entries[vm.stage].orEmpty(),{vm.setEntry(it)},modifier=Modifier.fillMaxWidth().padding(top=12.dp),label={Text(if(vm.stage==Stage.IV)"S-2 · D · AI · EI · T · I · AOL · A/S" else "Raw impressions")},minLines=if(vm.stage==Stage.VI)5 else 4);Row(Modifier.fillMaxWidth().padding(top=10.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(aol,{aol=it},Modifier.weight(1f),label={Text("AOL / analytical overlay")});IconButton({vm.addAol(aol);aol=""}){Icon(Icons.Default.AddCircle,null,tint=Violet)}}; if(vm.aols.isNotEmpty())Text("AOL: ${vm.aols.joinToString(" · ")}",color=Violet,fontSize=12.sp,modifier=Modifier.padding(top=7.dp));Text("Confidence ${vm.confidence}%",color=Soft,modifier=Modifier.padding(top=14.dp));Slider(vm.confidence.toFloat(),{vm.confidence=it.toInt()},valueRange=0f..100f)}

fun stageHelp(s:Stage)=when(s){Stage.I->"Make one spontaneous ideogram. Name only the broad gestalt.";Stage.II->"Record color, temperature, texture, sound, smell, taste, density and motion.";Stage.III->"Sketch boundaries, dimensions and relationships. Avoid naming objects.";Stage.IV->"Populate a structured matrix. Objectify AOL immediately.";Stage.V->"Interrogate a strong lead: object, attribute, function, relationship, subject, topic.";Stage.VI->"Model the target spatially, then synthesize without forcing identification."}

@Composable fun SketchPad(vm:SignalViewModel){var current by remember{mutableStateOf<List<Offset>>(emptyList())};Canvas(Modifier.fillMaxWidth().height(260.dp).padding(top=8.dp).background(Color(0xFF0D1119),RoundedCornerShape(14.dp)).pointerInput(Unit){detectDragGestures(onDragStart={current=listOf(it)},onDrag={change,_->current=current+change.position},onDragEnd={if(current.isNotEmpty())vm.points=vm.points+listOf(current);current=emptyList()})}){(vm.points+listOf(current)).filter{it.size>1}.forEach{p->val q=Path().apply{moveTo(p[0].x,p[0].y);for(i in 1 until p.size)lineTo(p[i].x,p[i].y)};drawPath(q,Cyan,style=Stroke(4f,cap=StrokeCap.Round))}};TextButton({vm.points=emptyList()}){Text("CLEAR SKETCH")}}
@Composable fun StageNav(vm:SignalViewModel){Row(Modifier.padding(16.dp).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton({vm.prev()},enabled=!vm.locked&&vm.stage!=Stage.I,modifier=Modifier.weight(1f)){Text("BACK")}; if(vm.stage!=Stage.VI)Button({vm.next()},enabled=!vm.locked,modifier=Modifier.weight(1f)){Text("NEXT") } else Button({vm.lock()},enabled=!vm.locked,modifier=Modifier.weight(1f)){Icon(Icons.Default.Lock,null);Spacer(Modifier.width(6.dp));Text("COMMIT")}} }
@Composable fun Judging(vm:SignalViewModel){ val cand=remember(vm.trn){vm.candidates()};Text("Choose the candidate matching your locked session.",fontWeight=FontWeight.Bold);cand.forEach{t->TargetCard(t,hidden=false,selected=vm.selectedCandidate?.id==t.id){if(vm.selectedCandidate==null)vm.judge(t)}}; if(vm.selectedCandidate!=null){ val actual=vm.target!!;Spacer(Modifier.height(12.dp));Text(if(vm.selectedCandidate!!.id==actual.id)"DIRECT HIT" else "TARGET REVEAL",color=if(vm.selectedCandidate!!.id==actual.id)Good else Violet,fontSize=24.sp,fontWeight=FontWeight.Black);TargetCard(actual,false,false){};Text("Attributes: ${actual.attrs.joinToString(", ")}",color=Soft);Button({vm.screen=Screen.HISTORY},Modifier.fillMaxWidth().padding(top=12.dp)){Text("VIEW SESSION RECORD")}} }

@Composable fun TargetCard(t:Target,hidden:Boolean,selected:Boolean,on:()->Unit){Card(Modifier.padding(vertical=6.dp).fillMaxWidth().clickable{on()},colors=CardDefaults.cardColors(containerColor=if(selected)Color(0xFF173038) else Panel)){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){TargetArt(t,Modifier.size(90.dp));Spacer(Modifier.width(14.dp));Column{Text(if(hidden)"SEALED TARGET" else t.name,fontWeight=FontWeight.Bold);Text(if(hidden)"No target metadata available" else t.family.uppercase(),color=Soft,fontSize=11.sp)}}} }
@Composable fun TargetArt(t:Target,mod:Modifier){Canvas(mod.background(Color(0xFF0B1018),RoundedCornerShape(14.dp))){val w=size.width;val h=size.height; val c=Cyan.copy(alpha=.9f); when(t.form%6){0->{drawLine(c,Offset(w*.5f,h*.1f),Offset(w*.3f,h*.9f),7f);drawLine(c,Offset(w*.5f,h*.1f),Offset(w*.7f,h*.9f),7f);for(i in 0..5)drawCircle(Color.White.copy(alpha=.25f),6f,Offset(w*(.3f+i*.08f),h*(.25f+i*.1f)))};1->{drawLine(c,Offset(w*.1f,h*.55f),Offset(w*.9f,h*.55f),8f);drawLine(c,Offset(w*.25f,h*.2f),Offset(w*.25f,h*.8f),5f);drawLine(c,Offset(w*.75f,h*.2f),Offset(w*.75f,h*.8f),5f);};2->{val p=Path().apply{moveTo(w*.5f,h*.12f);lineTo(w*.12f,h*.88f);lineTo(w*.88f,h*.88f);close()};drawPath(p,c,style=Stroke(7f))};3->{drawCircle(c,w*.28f,Offset(w*.5f,h*.45f),style=Stroke(8f));drawLine(c,Offset(w*.5f,h*.72f),Offset(w*.5f,h*.95f),7f)};4->{for(i in 0..4)drawArc(c.copy(alpha=1f-i*.15f),180f,180f,false,topLeft=Offset(w*.08f+i*4,h*.08f+i*4),size=androidx.compose.ui.geometry.Size(w*.84f-i*8,h*.84f-i*8),style=Stroke(5f))};else->{drawCircle(c,w*.32f,Offset(w*.5f,h*.5f),style=Stroke(8f));drawCircle(Violet,w*.15f,Offset(w*.5f,h*.5f),style=Stroke(5f))}}} }

@Composable
fun Bot(vm:SignalViewModel){
 val context=LocalContext.current
 var input by remember{mutableStateOf("")}
 var revealText by remember{mutableStateOf("")}
 var verifyError by remember{mutableStateOf(false)}
 val tts=remember{ TextToSpeech(context){ } }
 DisposableEffect(Unit){ onDispose{tts.stop();tts.shutdown()} }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){
  Header("MIRA · REMOTE VIEWER"){vm.screen=Screen.HOME}
  Column(Modifier.padding(16.dp)){
   Text("TARGET REFERENCE",color=Soft,fontSize=10.sp);Text(vm.trn,color=Cyan,fontSize=28.sp,fontWeight=FontWeight.Black)
   Text("TARGET SEALED · PLAINTEXT DISCARDED",color=Good,fontSize=11.sp,fontWeight=FontWeight.Bold)
   Text("Seal ${vm.targetSealPreview()}…",color=Soft,fontSize=10.sp)
   Text("Viewer input: TRN + ${vm.botMode.label.lowercase()}. Target text is absent from the viewing pass.",color=Soft,fontSize=12.sp,modifier=Modifier.padding(top=8.dp))
   val r=vm.botResult
   if(r!=null){
    BotBlock("FIRST GESTALT",r.gestalt);BotBlock("SENSORY",r.sensory.joinToString(" · "));BotBlock("SPATIAL",r.spatial.joinToString(" · "));BotBlock("SYNTHESIS",r.synthesis)
    LinearProgressIndicator(progress={r.confidence/100f},modifier=Modifier.fillMaxWidth().padding(top=16.dp));Text("Mira confidence ${r.confidence}%",color=Soft,fontSize=12.sp)
   }
   Text("TALK TO MIRA",fontWeight=FontWeight.Black,color=Violet,modifier=Modifier.padding(top=18.dp))
   vm.viewerChat.forEach{line->Card(Modifier.fillMaxWidth().padding(top=6.dp),colors=CardDefaults.cardColors(containerColor=if(line.speaker=="MIRA")Panel else Color(0xFF17202A))){Column(Modifier.padding(12.dp)){Text(line.speaker,color=if(line.speaker=="MIRA")Violet else Cyan,fontSize=10.sp,fontWeight=FontWeight.Black);Text(line.text)}}}
   Row(Modifier.fillMaxWidth().padding(top=10.dp),verticalAlignment=Alignment.CenterVertically){
    OutlinedTextField(input,{input=it},label={Text("Ask her about the target")},modifier=Modifier.weight(1f))
    IconButton(onClick={if(input.isNotBlank()){vm.askViewer(input);input=""}}){Icon(Icons.Default.Send,null)}
   }
   val lastMira=vm.viewerChat.lastOrNull{it.speaker=="MIRA"}?.text.orEmpty()
   OutlinedButton(onClick={if(lastMira.isNotBlank())tts.speak(lastMira,TextToSpeech.QUEUE_FLUSH,null,"mira")},modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Icon(Icons.Default.VolumeUp,null);Spacer(Modifier.width(8.dp));Text("SPEAK MIRA'S LAST MESSAGE")}
   OutlinedButton(onClick={vm.miraInitiative()},modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(8.dp));Text("LET MIRA LEAD")}
   Divider(Modifier.padding(vertical=18.dp))
   if(!vm.revealed){
    Text("REVEAL + VERIFY",fontWeight=FontWeight.Black)
    Text("Re-enter the same target. Mira's report is already cryptographically committed.",color=Soft,fontSize=12.sp)
    OutlinedTextField(revealText,{revealText=it;verifyError=false},label={Text("Re-enter target")},modifier=Modifier.fillMaxWidth().padding(top=8.dp),minLines=3)
    Button(onClick={verifyError=!vm.verifyTarget(revealText)},modifier=Modifier.fillMaxWidth().padding(top=10.dp)){Icon(Icons.Default.LockOpen,null);Spacer(Modifier.width(8.dp));Text("VERIFY + REVEAL")}
    if(verifyError)Text("That does not match the sealed target. Enter the original target exactly.",color=MaterialTheme.colorScheme.error,fontSize=12.sp,modifier=Modifier.padding(top=8.dp))
   } else {
    Text("VERIFIED TARGET",fontWeight=FontWeight.Black,color=Good,fontSize=20.sp)
    Text(vm.revealedCustomTarget,modifier=Modifier.padding(top=8.dp),fontSize=18.sp)
    Text("Mira's report was committed before this target text returned to memory.",color=Soft,fontSize=12.sp,modifier=Modifier.padding(top=8.dp))
   }
  }
 }
}
@Composable fun BotBlock(t:String,v:String){Card(Modifier.fillMaxWidth().padding(top=10.dp),colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(14.dp)){Text(t,color=Cyan,fontSize=11.sp,fontWeight=FontWeight.Bold);Text(v,fontSize=17.sp,fontWeight=FontWeight.SemiBold)}}}

@Composable fun History(vm:SignalViewModel){Column(Modifier.fillMaxSize()){Header("HISTORY + ANALYTICS"){vm.screen=Screen.HOME};if(vm.history.isEmpty())Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("No locked sessions yet",color=Soft)}else LazyishHistory(vm.history)}}
@Composable fun LazyishHistory(h:List<SessionRecord>){Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)){val avg=if(h.isEmpty())0 else h.map{it.score}.average().toInt();Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Metric("SESSIONS",h.size.toString(),Modifier.weight(1f));Metric("AVG MATCH", "$avg%",Modifier.weight(1f))};h.forEach{r->Card(Modifier.fillMaxWidth().padding(top=8.dp),colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(14.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(r.mode,fontWeight=FontWeight.Black,color=if(r.mode.startsWith("BOT"))Violet else Cyan);Text("${r.score}%",fontWeight=FontWeight.Black)};Text(r.trn,color=Soft,fontSize=11.sp);Text(r.target,fontWeight=FontWeight.Bold);Text("Selected: ${r.guess}",color=Soft,fontSize=12.sp); if(r.bestStage.isNotBlank())Text("Best stage: ${r.bestStage}",color=Good,fontSize=11.sp); if(r.commitment.isNotBlank())Text("Commit: ${r.commitment.take(16)}…",color=Cyan,fontSize=10.sp)}}}}}
@Composable fun Metric(k:String,v:String,m:Modifier){Card(m,colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(16.dp)){Text(k,color=Soft,fontSize=10.sp);Text(v,fontSize=26.sp,fontWeight=FontWeight.Black,color=Cyan)}}}

@Composable fun Academy(vm:SignalViewModel){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){Header("ACADEMY"){vm.screen=Screen.HOME};Column(Modifier.padding(16.dp)){Text("DB-CRV FIELD MANUAL",fontSize=24.sp,fontWeight=FontWeight.Black);Text("Capture low-level impressions before high-level identification.",color=Cyan);val lessons=listOf("I · Ideogram" to "Immediate spontaneous mark. Decode movement, then broad gestalt.","II · Sensory" to "Color, temperature, texture, sound, smell, taste, density and motion. Keep it primitive.","III · Spatial" to "Dimensions, boundaries, relationships, viewpoint shifts and rough sketches.","IV · Matrix" to "Separate S-2, D, AI, EI, tangibles, intangibles, AOL and A/S.","V · Probe" to "Select one strong lead. Probe object, attribute, function, relationship, subject or topic.","VI · Model" to "Externalize the geometry into a spatial model and final synthesis.","AOL" to "Write the identification down instead of fighting it. Then recover the raw qualities that produced it.","Blind Lock" to "Commit the acquisition before candidate judging or target feedback.");lessons.forEach{(a,b)->Card(Modifier.fillMaxWidth().padding(top=10.dp),colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(15.dp)){Text(a,fontWeight=FontWeight.Black,color=Violet);Text(b,color=Soft)}}};Button({vm.startHuman()},Modifier.fillMaxWidth().padding(top=18.dp)){Text("START TRAINING SESSION")}}} }
