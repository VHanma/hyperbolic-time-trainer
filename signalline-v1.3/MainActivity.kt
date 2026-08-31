package com.vhanma.signalline

import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.concurrent.thread

private val Bg = Color(0xFF080A0F)
private val Panel = Color(0xFF11151F)
private val Cyan = Color(0xFF7DF9FF)
private val Violet = Color(0xFFB48CFF)
private val Soft = Color(0xFFAAB5C8)
private val Good = Color(0xFF7DFFB2)

enum class Screen { HOME, CHAT, TARGET_SETTER, VIEWER, STATUS, HISTORY }
data class ChatLine(val speaker:String,val text:String)
data class SessionRecord(val trn:String,val target:String,val report:String,val commit:String)
data class BrainProvider(val name:String,val url:String,val model:String,val auth:String?=null)

private val providers = listOf(
    BrainProvider("BlockRun · GPT-OSS 120B", "https://blockrun.ai/api/v1/chat/completions", "nvidia/gpt-oss-120b"),
    BrainProvider("BlockRun · Step 3.7 Flash", "https://blockrun.ai/api/v1/chat/completions", "nvidia/step-3.7-flash"),
    BrainProvider("LLM7 · Free", "https://api.llm7.io/v1/chat/completions", "default", "unused")
)

class SignalViewModel(app:Application): AndroidViewModel(app) {
    var screen by mutableStateOf(Screen.HOME)
    var freeChat by mutableStateOf(listOf(ChatLine("MIRA", "I’m here. Just talk to me.")))
    var viewerChat by mutableStateOf(listOf<ChatLine>())
    var busy by mutableStateOf(false)
    var error by mutableStateOf("")
    var activeProvider by mutableStateOf("AUTO · FREE")
    var lastProvider by mutableStateOf("Not connected yet")
    var trn by mutableStateOf("")
    var targetSeal by mutableStateOf("")
    private var targetSalt = ""
    var revealedTarget by mutableStateOf("")
    var revealed by mutableStateOf(false)
    var viewerCommitted by mutableStateOf(false)
    var viewerReport by mutableStateOf("")
    var history by mutableStateOf(loadHistory())
    private val rng = SecureRandom()
    private val main = Handler(Looper.getMainLooper())

    fun sendFree(text:String) {
        val clean = text.trim(); if (clean.isBlank() || busy) return
        freeChat = freeChat + ChatLine("YOU", clean)
        callMira(
            system = """You are Mira, a warm, intelligent, independent conversational AI inside SignalLine. Have genuine free-flowing conversation. You may ask questions, disagree politely, change subjects naturally, joke, volunteer thoughts, and develop ideas instead of acting like a menu. Speak naturally and usually concisely unless depth is useful. Do not pretend to have physical senses, consciousness, or secret access you do not have.""",
            transcript = freeChat.takeLast(20),
            onDone = { freeChat = freeChat + ChatLine("MIRA", it) }
        )
    }

    fun miraFreeInitiative() {
        if (busy) return
        val t = freeChat.takeLast(20) + ChatLine("YOU", "Take the initiative. Bring up whatever you think would make this conversation interesting, useful, or surprising right now. You may change the subject.")
        callMira(
            system = "You are Mira, an independent conversational AI. Be natural, spontaneous, curious, and concise.",
            transcript = t,
            onDone = { freeChat = freeChat + ChatLine("MIRA", it) }
        )
    }

    fun clearFreeChat(){ freeChat = listOf(ChatLine("MIRA", "Fresh slate. What’s on your mind?")); error="" }

    fun sealTarget(raw:String):Boolean {
        val clean = raw.trim(); if(clean.isBlank()) return false
        val saltBytes = ByteArray(24).also { rng.nextBytes(it) }
        targetSalt = saltBytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        targetSeal = sha256("$targetSalt|$clean")
        trn = "%04d-%04d".format(rng.nextInt(10000),rng.nextInt(10000))
        revealedTarget=""; revealed=false; viewerCommitted=false; viewerReport=""; error=""
        viewerChat = listOf(ChatLine("MIRA", "I have blind reference $trn. The target itself is unavailable to me."))
        screen = Screen.VIEWER
        startBlindPass()
        return true
    }

    private fun startBlindPass(){
        val entropy = sha256("$trn|${System.nanoTime()}|${rng.nextLong()}").take(24)
        val t = listOf(ChatLine("YOU", "Begin a fresh experimental blind remote-viewing style session for reference $trn. Entropy token: $entropy. Report raw gestalt, sensory qualities, spatial relationships, motion or energy, possible biological presence, then a short synthesis. Separate raw impressions from association or AOL. The hidden target must remain unknown."))
        callMira(blindPrompt(), t){ answer ->
            viewerReport = answer
            viewerChat = viewerChat + ChatLine("MIRA",answer)
            viewerCommitted = true
        }
    }

    fun askViewer(text:String){
        val clean=text.trim(); if(clean.isBlank()||busy)return
        viewerChat=viewerChat+ChatLine("YOU",clean)
        callMira(blindPrompt(),viewerChat.takeLast(22)){answer->
            viewerChat=viewerChat+ChatLine("MIRA",answer)
            viewerReport += "\n\n$answer"
        }
    }

    fun miraViewerInitiative(){
        if(busy)return
        val t=viewerChat.takeLast(22)+ChatLine("YOU","Take the initiative in the blind session. Pick a useful new probe yourself and report what arises. Stay blind. Separate raw impressions from AOL or guesses.")
        callMira(blindPrompt(),t){answer->viewerChat=viewerChat+ChatLine("MIRA",answer);viewerReport+="\n\n$answer"}
    }

    private fun blindPrompt()="""You are Mira in SignalLine's experimental blind-view mode. The hidden target plaintext, target salt, and target hash are deliberately unavailable to you. Never claim secret knowledge of them and never ask for the target before reveal. Work only from the blind reference, entropy token, conversation, and your prior impressions. Use CRV-inspired structure: gestalt, sensory, dimensional/spatial, motion/energy, biological presence, relationships, synthesis. Distinguish RAW IMPRESSION, ASSOCIATION/AOL, and CONFIDENCE. Converse naturally while remaining blind."""

    fun verifyTarget(raw:String):Boolean{
        if(!viewerCommitted)return false
        val clean=raw.trim(); if(clean.isBlank())return false
        if(sha256("$targetSalt|$clean")!=targetSeal)return false
        revealedTarget=clean; revealed=true
        saveHistory(SessionRecord(trn,clean,viewerReport,sha256("$trn|$viewerReport")))
        return true
    }

    fun clearHistory(){history=emptyList();prefs().edit().remove("history_v14").apply()}

    private fun callMira(system:String, transcript:List<ChatLine>, onDone:(String)->Unit){
        busy=true; error=""; activeProvider="AUTO · SEARCHING"
        thread {
            var failure=""
            for(provider in providers){
                try{
                    val answer=callProvider(provider,system,transcript)
                    if(answer.isNotBlank()){
                        main.post{busy=false;activeProvider="AUTO · FREE";lastProvider=provider.name;error="";onDone(answer)}
                        return@thread
                    }
                }catch(t:Throwable){ failure="${provider.name}: ${t.message ?: "failed"}" }
            }
            main.post{busy=false;activeProvider="AUTO · FREE";error="All free brains are temporarily unavailable. $failure"}
        }
    }

    private fun callProvider(provider:BrainProvider,system:String,transcript:List<ChatLine>):String{
        val messages=JSONArray()
        messages.put(JSONObject().put("role","system").put("content",system))
        transcript.forEach{line->messages.put(JSONObject().put("role",if(line.speaker=="MIRA")"assistant" else "user").put("content",line.text))}
        val body=JSONObject().put("model",provider.model).put("messages",messages).put("temperature",0.85).put("max_tokens",900)
        val conn=(URL(provider.url).openConnection() as HttpURLConnection).apply{
            requestMethod="POST";connectTimeout=18000;readTimeout=60000;doOutput=true
            setRequestProperty("Content-Type","application/json")
            provider.auth?.let{setRequestProperty("Authorization","Bearer $it")}
        }
        conn.outputStream.use{it.write(body.toString().toByteArray())}
        val code=conn.responseCode
        val stream=if(code in 200..299)conn.inputStream else conn.errorStream
        val raw=stream?.bufferedReader()?.use{it.readText()}.orEmpty()
        if(code !in 200..299)throw IllegalStateException("HTTP $code ${raw.take(160)}")
        val root=JSONObject(raw)
        val choices=root.optJSONArray("choices") ?: throw IllegalStateException("No choices")
        val first=choices.optJSONObject(0) ?: throw IllegalStateException("Empty choices")
        val msg=first.optJSONObject("message")
        return (msg?.optString("content") ?: first.optString("text")).trim()
    }

    private fun sha256(v:String)=MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun prefs()=getApplication<Application>().getSharedPreferences("signal",0)
    private fun saveHistory(r:SessionRecord){history=(listOf(r)+history).take(50);prefs().edit().putString("history_v14",history.joinToString("§"){listOf(it.trn,it.target,it.report.replace("§"," ").replace("¦"," "),it.commit).joinToString("¦")}).apply()}
    private fun loadHistory():List<SessionRecord>{val raw=prefs().getString("history_v14","").orEmpty();if(raw.isBlank())return emptyList();return raw.split("§").mapNotNull{val p=it.split("¦");if(p.size<4)null else SessionRecord(p[0],p[1],p[2],p[3])}}
}

class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{SignalApp()}}}

@Composable fun SignalApp(vm:SignalViewModel=viewModel()){
    MaterialTheme(colorScheme=darkColorScheme(background=Bg,surface=Panel,primary=Cyan,secondary=Violet,onBackground=Color.White,onSurface=Color.White)){
        Surface(Modifier.fillMaxSize(),color=Bg){when(vm.screen){
            Screen.HOME->Home(vm);Screen.CHAT->FreeChat(vm);Screen.TARGET_SETTER->TargetSetter(vm);Screen.VIEWER->Viewer(vm);Screen.STATUS->Status(vm);Screen.HISTORY->History(vm)
        }}
    }
}

@Composable fun Header(title:String,back:(()->Unit)?=null){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){if(back!=null)IconButton(onClick=back){Icon(Icons.Default.ArrowBack,null)};Text(title,fontWeight=FontWeight.Black,fontSize=19.sp);Spacer(Modifier.weight(1f));Icon(Icons.Default.AutoAwesome,null,tint=Violet)}}

@Composable fun Home(vm:SignalViewModel){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){Header("SIGNALLINE · MIRA v1.4");Column(Modifier.padding(16.dp)){Text("MIRA",fontSize=36.sp,fontWeight=FontWeight.Black,color=Violet);Text("Zero-setup cloud brain",color=Cyan,fontWeight=FontWeight.Bold);Text("No API key · no balance · automatic free-provider failover",color=Soft,fontSize=12.sp,modifier=Modifier.padding(top=4.dp));Spacer(Modifier.height(18.dp));BigButton("FREE TALK","Open it and talk. Nothing to configure.",Icons.Default.Chat){vm.screen=Screen.CHAT};BigButton("GIVE MIRA A TARGET","Seal a target, then she works blind.",Icons.Default.Lock){vm.screen=Screen.TARGET_SETTER};BigButton("BRAIN STATUS","See which free provider answered last.",Icons.Default.Memory){vm.screen=Screen.STATUS};BigButton("HISTORY","Verified blind sessions.",Icons.Default.History){vm.screen=Screen.HISTORY};Text("Blind mode never sends target plaintext, target salt, or target hash to the AI before reveal.",color=Soft,fontSize=12.sp,modifier=Modifier.padding(top=16.dp))}}}

@Composable fun BigButton(t:String,s:String,icon:androidx.compose.ui.graphics.vector.ImageVector,on:()->Unit){Card(Modifier.fillMaxWidth().padding(vertical=6.dp).clickable{on()},colors=CardDefaults.cardColors(containerColor=Panel)){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Cyan);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(t,fontWeight=FontWeight.Black);Text(s,color=Soft,fontSize=12.sp)};Icon(Icons.Default.ChevronRight,null,tint=Soft)}}}

@Composable fun ChatBubble(line:ChatLine){Card(Modifier.fillMaxWidth().padding(vertical=4.dp),colors=CardDefaults.cardColors(containerColor=if(line.speaker=="MIRA")Panel else Color(0xFF17202A))){Column(Modifier.padding(12.dp)){Text(line.speaker,color=if(line.speaker=="MIRA")Violet else Cyan,fontWeight=FontWeight.Black,fontSize=10.sp);Text(line.text)}}}

@Composable fun FreeChat(vm:SignalViewModel){val context=androidx.compose.ui.platform.LocalContext.current;var input by remember{mutableStateOf("")};val tts=remember{TextToSpeech(context){}};DisposableEffect(Unit){onDispose{tts.shutdown()}};Column(Modifier.fillMaxSize()){Header("FREE TALK WITH MIRA"){vm.screen=Screen.HOME};Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp)){vm.freeChat.forEach{ChatBubble(it)};if(vm.busy)Text("Mira is thinking · ${vm.activeProvider}",color=Soft,modifier=Modifier.padding(8.dp));if(vm.error.isNotBlank())Text(vm.error,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(8.dp))};Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(input,{input=it},Modifier.weight(1f),label={Text("Say anything")},maxLines=5);IconButton({if(input.isNotBlank()){vm.sendFree(input);input=""}}){Icon(Icons.Default.Send,null)}};Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(vm::miraFreeInitiative,Modifier.weight(1f)){Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(5.dp));Text("MIRA LEAD")};OutlinedButton({val m=vm.freeChat.lastOrNull{it.speaker=="MIRA"}?.text.orEmpty();if(m.isNotBlank())tts.speak(m,TextToSpeech.QUEUE_FLUSH,null,"mira")},Modifier.weight(1f)){Icon(Icons.Default.VolumeUp,null);Spacer(Modifier.width(5.dp));Text("SPEAK")};OutlinedButton(vm::clearFreeChat){Icon(Icons.Default.DeleteSweep,null)}}}}

@Composable fun TargetSetter(vm:SignalViewModel){var target by remember{mutableStateOf("")};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){Header("GIVE MIRA A TARGET"){vm.screen=Screen.HOME};Column(Modifier.padding(16.dp)){Text("YOU SET IT · SHE STAYS BLIND",fontWeight=FontWeight.Black,color=Cyan,fontSize=20.sp);Text("Type any target concept. SignalLine immediately converts it to a salted SHA-256 commitment, discards the plaintext from the viewing channel, then starts Mira.",color=Soft,modifier=Modifier.padding(top=8.dp));OutlinedTextField(target,{target=it},label={Text("Target")},minLines=5,modifier=Modifier.fillMaxWidth().padding(top=14.dp));Button({if(vm.sealTarget(target))target=""},enabled=target.isNotBlank(),modifier=Modifier.fillMaxWidth().padding(top=14.dp)){Icon(Icons.Default.Lock,null);Spacer(Modifier.width(8.dp));Text("SEAL + START MIRA")}}}}

@Composable fun Viewer(vm:SignalViewModel){val context=androidx.compose.ui.platform.LocalContext.current;var input by remember{mutableStateOf("")};var reveal by remember{mutableStateOf("")};var bad by remember{mutableStateOf(false)};val tts=remember{TextToSpeech(context){}};DisposableEffect(Unit){onDispose{tts.shutdown()}};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){Header("MIRA · BLIND VIEW"){vm.screen=Screen.HOME};Column(Modifier.padding(16.dp)){Text("TRN ${vm.trn}",color=Cyan,fontSize=27.sp,fontWeight=FontWeight.Black);Text("TARGET SEALED · VIEWER CHANNEL CLEAN",color=Good,fontSize=11.sp,fontWeight=FontWeight.Black);Text("Seal ${vm.targetSeal.take(16)}…",color=Soft,fontSize=10.sp);Spacer(Modifier.height(10.dp));vm.viewerChat.forEach{ChatBubble(it)};if(vm.busy)Text("Mira is probing · ${vm.activeProvider}",color=Soft,modifier=Modifier.padding(8.dp));if(vm.error.isNotBlank())Text(vm.error,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(8.dp));Row(Modifier.fillMaxWidth().padding(top=10.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(input,{input=it},Modifier.weight(1f),label={Text("Talk freely with Mira")});IconButton({if(input.isNotBlank()){vm.askViewer(input);input=""}}){Icon(Icons.Default.Send,null)}};Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(vm::miraViewerInitiative,Modifier.weight(1f)){Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(5.dp));Text("LET MIRA LEAD")};OutlinedButton({val m=vm.viewerChat.lastOrNull{it.speaker=="MIRA"}?.text.orEmpty();if(m.isNotBlank())tts.speak(m,TextToSpeech.QUEUE_FLUSH,null,"mira")},Modifier.weight(1f)){Icon(Icons.Default.VolumeUp,null);Spacer(Modifier.width(5.dp));Text("SPEAK")}};HorizontalDivider(Modifier.padding(vertical=18.dp));if(!vm.revealed){Text("VERIFY + REVEAL",fontWeight=FontWeight.Black);Text("Re-enter the exact target only after Mira has committed a report.",color=Soft,fontSize=12.sp);OutlinedTextField(reveal,{reveal=it;bad=false},label={Text("Original target")},minLines=3,modifier=Modifier.fillMaxWidth().padding(top=8.dp));Button({bad=!vm.verifyTarget(reveal)},enabled=vm.viewerCommitted,modifier=Modifier.fillMaxWidth().padding(top=10.dp)){Icon(Icons.Default.LockOpen,null);Spacer(Modifier.width(8.dp));Text(if(vm.viewerCommitted)"VERIFY + REVEAL" else "WAITING FOR MIRA")};if(bad)Text("That does not match the sealed target, or Mira has not committed yet.",color=MaterialTheme.colorScheme.error,fontSize=12.sp,modifier=Modifier.padding(top=8.dp))}else{Text("VERIFIED TARGET",color=Good,fontWeight=FontWeight.Black,fontSize=20.sp);Text(vm.revealedTarget,fontSize=18.sp,modifier=Modifier.padding(top=8.dp));Text("The viewing transcript was produced before this plaintext was restored.",color=Soft,fontSize=12.sp,modifier=Modifier.padding(top=8.dp))}}}}

@Composable fun Status(vm:SignalViewModel){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){Header("MIRA BRAIN STATUS"){vm.screen=Screen.HOME};Column(Modifier.padding(16.dp)){Text("ZERO-SETUP MODE",color=Good,fontSize=22.sp,fontWeight=FontWeight.Black);Text("Mira automatically tries multiple free hosted brains. There is no API-key or payment screen.",color=Soft,modifier=Modifier.padding(top=8.dp));StatusCard("CURRENT MODE",vm.activeProvider);StatusCard("LAST BRAIN USED",vm.lastProvider);StatusCard("FAILOVER ORDER",providers.joinToString("\n"){"• ${it.name}"});Text("Free public endpoints can throttle or change. SignalLine retries the next provider instead of making you manage balances.",color=Soft,fontSize=12.sp,modifier=Modifier.padding(top=14.dp))}}}

@Composable fun StatusCard(k:String,v:String){Card(Modifier.fillMaxWidth().padding(top=10.dp),colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(14.dp)){Text(k,color=Cyan,fontSize=10.sp,fontWeight=FontWeight.Black);Text(v,fontSize=16.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=4.dp))}}}

@Composable fun History(vm:SignalViewModel){Column(Modifier.fillMaxSize()){Header("HISTORY"){vm.screen=Screen.HOME};Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)){if(vm.history.isEmpty())Text("No verified blind sessions yet.",color=Soft)else vm.history.forEach{r->Card(Modifier.fillMaxWidth().padding(bottom=8.dp),colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(14.dp)){Text(r.trn,color=Cyan,fontWeight=FontWeight.Black);Text(r.target,fontWeight=FontWeight.Bold);Text(r.report.take(650),color=Soft,fontSize=12.sp,modifier=Modifier.padding(top=6.dp));Text("Commit ${r.commit.take(18)}…",color=Violet,fontSize=10.sp,modifier=Modifier.padding(top=6.dp))}}};if(vm.history.isNotEmpty())OutlinedButton(vm::clearHistory,Modifier.fillMaxWidth().padding(top=8.dp)){Icon(Icons.Default.DeleteSweep,null);Spacer(Modifier.width(6.dp));Text("CLEAR HISTORY")}}}}
