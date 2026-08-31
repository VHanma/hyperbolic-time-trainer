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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

enum class Screen { HOME, CHAT, TARGET_SETTER, VIEWER, SETTINGS, HISTORY }
data class ChatLine(val speaker:String,val text:String)
data class SessionRecord(val trn:String,val target:String,val report:String,val commit:String)

class SignalViewModel(app:Application): AndroidViewModel(app) {
    var screen by mutableStateOf(Screen.HOME)
    var freeChat by mutableStateOf(listOf(ChatLine("MIRA", "I’m here. Talk to me about anything.")))
    var viewerChat by mutableStateOf(listOf<ChatLine>())
    var apiKey by mutableStateOf(prefs().getString("api_key", "").orEmpty())
    var model by mutableStateOf(prefs().getString("model", "gpt-5.6").orEmpty())
    var busy by mutableStateOf(false)
    var error by mutableStateOf("")
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

    fun saveSettings(key:String, selectedModel:String) {
        apiKey = key.trim(); model = selectedModel.trim().ifBlank { "gpt-5.6" }
        prefs().edit().putString("api_key", apiKey).putString("model", model).apply()
    }

    fun sendFree(text:String) {
        val clean = text.trim(); if (clean.isBlank() || busy) return
        freeChat = freeChat + ChatLine("YOU", clean)
        val transcript = freeChat.takeLast(18)
        callMira(
            system = """You are Mira, a warm, intelligent, independent conversational AI inside SignalLine. Have genuine free-flowing conversation. You may ask questions, disagree politely, change subjects naturally, joke, volunteer thoughts, and develop ideas instead of acting like a menu. Do not pretend to have physical senses or secret access you do not have. Keep responses natural and usually concise unless the user wants depth.""",
            transcript = transcript,
            onDone = { answer -> freeChat = freeChat + ChatLine("MIRA", answer) }
        )
    }

    fun miraFreeInitiative() {
        if (busy) return
        val transcript = freeChat.takeLast(18) + ChatLine("YOU", "Take the initiative. Say whatever you genuinely think would make this conversation interesting or useful right now. You may introduce a new subject.")
        callMira(
            system = "You are Mira, an independent conversational AI. Be natural, spontaneous, curious, and concise.",
            transcript = transcript,
            onDone = { answer -> freeChat = freeChat + ChatLine("MIRA", answer) }
        )
    }

    fun sealTarget(raw:String):Boolean {
        val clean = raw.trim(); if (clean.isBlank()) return false
        val saltBytes = ByteArray(24).also { rng.nextBytes(it) }
        targetSalt = saltBytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        targetSeal = sha256("$targetSalt|$clean")
        trn = "%04d-%04d".format(rng.nextInt(10000), rng.nextInt(10000))
        revealedTarget = ""; revealed = false; viewerCommitted = false; viewerReport = ""
        viewerChat = listOf(ChatLine("MIRA", "I have the blind reference $trn. I do not have the target text."))
        screen = Screen.VIEWER
        startBlindPass()
        return true
    }

    private fun startBlindPass() {
        val seed = sha256("$trn|${System.nanoTime()}|${rng.nextLong()}").take(24)
        val transcript = listOf(ChatLine("YOU", "Begin a fresh blind remote-viewing style session for reference $trn. Experimental entropy seed: $seed. Give raw impressions first: gestalt, sensory qualities, spatial relationships, motion/energy, possible biological presence, then a short synthesis. Clearly separate raw impressions from AOL/guesses. Do not claim you know the hidden target."))
        callMira(
            system = blindSystemPrompt(), transcript = transcript,
            onDone = { answer ->
                viewerReport = answer
                viewerChat = viewerChat + ChatLine("MIRA", answer)
                viewerCommitted = true
            }
        )
    }

    fun askViewer(text:String) {
        val clean = text.trim(); if (clean.isBlank() || busy) return
        viewerChat = viewerChat + ChatLine("YOU", clean)
        val transcript = viewerChat.takeLast(20)
        callMira(
            system = blindSystemPrompt(), transcript = transcript,
            onDone = { answer -> viewerChat = viewerChat + ChatLine("MIRA", answer); viewerReport += "\n\n$answer" }
        )
    }

    fun miraViewerInitiative() {
        if (busy) return
        val transcript = viewerChat.takeLast(20) + ChatLine("YOU", "Take the initiative in the blind session. Choose a useful new probe yourself and report what comes up. Stay target-blind and separate raw impression from AOL.")
        callMira(
            system = blindSystemPrompt(), transcript = transcript,
            onDone = { answer -> viewerChat = viewerChat + ChatLine("MIRA", answer); viewerReport += "\n\n$answer" }
        )
    }

    private fun blindSystemPrompt() = """You are Mira in SignalLine's experimental blind remote-viewing mode. The hidden target plaintext is deliberately unavailable to you. Never ask for it before reveal and never imply you secretly know it. Work only from the blind reference, experimental entropy, conversation, and your own prior impressions. Use a CRV-inspired discipline: gestalt, sensory, dimensional/spatial, motion/energy, biological presence, relationships, then synthesis. Distinguish RAW impressions, ASSOCIATION, AOL/guess, and CONFIDENCE. You may converse naturally and independently while staying blind."""

    fun verifyTarget(raw:String):Boolean {
        if (!viewerCommitted) return false
        val clean = raw.trim(); if (clean.isBlank()) return false
        if (sha256("$targetSalt|$clean") != targetSeal) return false
        revealedTarget = clean; revealed = true
        saveHistory(SessionRecord(trn, clean, viewerReport, sha256("$trn|$viewerReport")))
        return true
    }

    private fun callMira(system:String, transcript:List<ChatLine>, onDone:(String)->Unit) {
        if (apiKey.isBlank()) { error = "Add your OpenAI API key in Mira Settings first."; screen = Screen.SETTINGS; return }
        busy = true; error = ""
        thread {
            try {
                val input = JSONArray()
                input.put(JSONObject().put("role", "system").put("content", system))
                transcript.forEach { line ->
                    val role = if (line.speaker == "MIRA") "assistant" else "user"
                    input.put(JSONObject().put("role", role).put("content", line.text))
                }
                val body = JSONObject()
                    .put("model", model)
                    .put("input", input)
                    .put("max_output_tokens", 900)
                    .put("reasoning", JSONObject().put("effort", "medium"))
                val conn = (URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 30000; readTimeout = 90000
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                val raw = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
                if (code !in 200..299) throw IllegalStateException("API $code: ${raw.take(280)}")
                val answer = extractText(JSONObject(raw)).ifBlank { "I got an empty response. Try again." }
                main.post { busy = false; onDone(answer) }
            } catch (t:Throwable) {
                main.post { busy = false; error = t.message ?: "Connection error" }
            }
        }
    }

    private fun extractText(root:JSONObject):String {
        val output = root.optJSONArray("output") ?: return root.optString("output_text", "")
        val parts = mutableListOf<String>()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val c = content.optJSONObject(j) ?: continue
                if (c.optString("type") == "output_text") parts += c.optString("text")
            }
        }
        return parts.joinToString("\n").trim()
    }

    private fun sha256(v:String)=MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun prefs()=getApplication<Application>().getSharedPreferences("signal",0)
    private fun saveHistory(r:SessionRecord){ history=(listOf(r)+history).take(50); prefs().edit().putString("history_v13",history.joinToString("§"){listOf(it.trn,it.target,it.report.replace("§"," ").replace("¦"," "),it.commit).joinToString("¦")}).apply() }
    private fun loadHistory():List<SessionRecord>{ val raw=prefs().getString("history_v13","").orEmpty(); if(raw.isBlank())return emptyList(); return raw.split("§").mapNotNull{val p=it.split("¦");if(p.size<4)null else SessionRecord(p[0],p[1],p[2],p[3])} }
}

class MainActivity:ComponentActivity(){ override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{SignalApp()}} }

@Composable fun SignalApp(vm:SignalViewModel=viewModel()){
    MaterialTheme(colorScheme=darkColorScheme(background=Bg,surface=Panel,primary=Cyan,secondary=Violet,onBackground=Color.White,onSurface=Color.White)){
        Surface(Modifier.fillMaxSize(),color=Bg){ when(vm.screen){
            Screen.HOME->Home(vm); Screen.CHAT->FreeChat(vm); Screen.TARGET_SETTER->TargetSetter(vm); Screen.VIEWER->Viewer(vm); Screen.SETTINGS->Settings(vm); Screen.HISTORY->History(vm)
        }}
    }
}

@Composable fun Header(title:String, back:(()->Unit)?=null){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){if(back!=null)IconButton(onClick=back){Icon(Icons.Default.ArrowBack,null)};Text(title,fontWeight=FontWeight.Black,fontSize=19.sp);Spacer(Modifier.weight(1f));Icon(Icons.Default.AutoAwesome,null,tint=Violet)}}

@Composable fun Home(vm:SignalViewModel){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){Header("SIGNALLINE · MIRA v1.3");Column(Modifier.padding(16.dp)){Text("MIRA",fontSize=34.sp,fontWeight=FontWeight.Black,color=Violet);Text("Free conversation + blind-view mode",color=Soft);Spacer(Modifier.height(18.dp));BigButton("FREE TALK","Talk about anything. No RV session required.",Icons.Default.Chat){vm.screen=Screen.CHAT};BigButton("GIVE MIRA A TARGET","Seal your own target, then let her work blind.",Icons.Default.Lock){vm.screen=Screen.TARGET_SETTER};BigButton("MIRA SETTINGS","API key + cloud model",Icons.Default.Settings){vm.screen=Screen.SETTINGS};BigButton("HISTORY","Verified blind sessions",Icons.Default.History){vm.screen=Screen.HISTORY};Text("The AI model stays off your phone. Blind mode never sends your target plaintext to Mira before reveal.",color=Soft,fontSize=12.sp,modifier=Modifier.padding(top=16.dp))}}}

@Composable fun BigButton(t:String,s:String,icon:androidx.compose.ui.graphics.vector.ImageVector,on:()->Unit){Card(Modifier.fillMaxWidth().padding(vertical=6.dp).clickable{on()},colors=CardDefaults.cardColors(containerColor=Panel)){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Cyan);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(t,fontWeight=FontWeight.Black);Text(s,color=Soft,fontSize=12.sp)};Icon(Icons.Default.ChevronRight,null,tint=Soft)}}}

@Composable fun ChatBubble(line:ChatLine){Card(Modifier.fillMaxWidth().padding(vertical=4.dp),colors=CardDefaults.cardColors(containerColor=if(line.speaker=="MIRA")Panel else Color(0xFF17202A))){Column(Modifier.padding(12.dp)){Text(line.speaker,color=if(line.speaker=="MIRA")Violet else Cyan,fontWeight=FontWeight.Black,fontSize=10.sp);Text(line.text)}}}

@Composable fun FreeChat(vm:SignalViewModel){val context=androidx.compose.ui.platform.LocalContext.current;var input by remember{mutableStateOf("")};val tts=remember{TextToSpeech(context){}};DisposableEffect(Unit){onDispose{tts.shutdown()}};Column(Modifier.fillMaxSize()){Header("FREE TALK WITH MIRA"){vm.screen=Screen.HOME};Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp)){vm.freeChat.forEach{ChatBubble(it)};if(vm.busy)Text("Mira is thinking…",color=Soft,modifier=Modifier.padding(8.dp));if(vm.error.isNotBlank())Text(vm.error,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(8.dp))};Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(input,{input=it},Modifier.weight(1f),label={Text("Say anything")},maxLines=5);IconButton({if(input.isNotBlank()){vm.sendFree(input);input=""}}){Icon(Icons.Default.Send,null)}};Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(vm::miraFreeInitiative,Modifier.weight(1f)){Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(6.dp));Text("LET MIRA LEAD")};OutlinedButton({val m=vm.freeChat.lastOrNull{it.speaker=="MIRA"}?.text.orEmpty();if(m.isNotBlank())tts.speak(m,TextToSpeech.QUEUE_FLUSH,null,"mira")},Modifier.weight(1f)){Icon(Icons.Default.VolumeUp,null);Spacer(Modifier.width(6.dp));Text("SPEAK")}}}}

@Composable fun Settings(vm:SignalViewModel){var key by remember{mutableStateOf(vm.apiKey)};var model by remember{mutableStateOf(vm.model)};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){Header("MIRA SETTINGS"){vm.screen=Screen.HOME};Column(Modifier.padding(16.dp)){Text("CLOUD BRAIN",fontWeight=FontWeight.Black,color=Violet,fontSize=22.sp);Text("Paste your OpenAI API key once. Mira’s model stays in the cloud instead of using phone storage.",color=Soft,modifier=Modifier.padding(vertical=8.dp));OutlinedTextField(key,{key=it},label={Text("OpenAI API key")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth());OutlinedTextField(model,{model=it},label={Text("Model")},supportingText={Text("Default: gpt-5.6")},modifier=Modifier.fillMaxWidth().padding(top=10.dp));Button({vm.saveSettings(key,model);vm.screen=Screen.HOME},Modifier.fillMaxWidth().padding(top=16.dp)){Text("SAVE MIRA BRAIN SETTINGS")}}}}

@Composable fun TargetSetter(vm:SignalViewModel){var target by remember{mutableStateOf("")};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){Header("GIVE MIRA A TARGET"){vm.screen=Screen.HOME};Column(Modifier.padding(16.dp)){Text("YOU SET IT. SHE VIEWS IT.",fontWeight=FontWeight.Black,color=Cyan,fontSize=22.sp);Text("Enter anything you want as the hidden target. SignalLine salts + hashes it, then the plaintext is dropped before Mira’s blind AI call.",color=Soft,modifier=Modifier.padding(vertical=8.dp));OutlinedTextField(target,{target=it},Modifier.fillMaxWidth(),label={Text("Hidden target")},minLines=5);Button({if(vm.sealTarget(target))target=""},enabled=target.isNotBlank(),modifier=Modifier.fillMaxWidth().padding(top=14.dp)){Icon(Icons.Default.Lock,null);Spacer(Modifier.width(6.dp));Text("SEAL + START MIRA")}}}}

@Composable fun Viewer(vm:SignalViewModel){val context=androidx.compose.ui.platform.LocalContext.current;var input by remember{mutableStateOf("")};var reveal by remember{mutableStateOf("")};var bad by remember{mutableStateOf(false)};val tts=remember{TextToSpeech(context){}};DisposableEffect(Unit){onDispose{tts.shutdown()}};Column(Modifier.fillMaxSize()){Header("MIRA · BLIND VIEW"){vm.screen=Screen.HOME};Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)){Text("TRN ${vm.trn}",fontSize=26.sp,fontWeight=FontWeight.Black,color=Cyan);Text("TARGET SEALED · ${vm.targetSeal.take(16)}…",color=Good,fontSize=11.sp);Text("Target plaintext is absent from Mira’s model input.",color=Soft,fontSize=12.sp,modifier=Modifier.padding(bottom=12.dp));vm.viewerChat.forEach{ChatBubble(it)};if(vm.busy)Text("Mira is probing…",color=Soft,modifier=Modifier.padding(8.dp));if(vm.error.isNotBlank())Text(vm.error,color=MaterialTheme.colorScheme.error);OutlinedTextField(input,{input=it},Modifier.fillMaxWidth().padding(top=10.dp),label={Text("Talk to Mira freely")},maxLines=5);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({if(input.isNotBlank()){vm.askViewer(input);input=""}},Modifier.weight(1f)){Text("SEND")};OutlinedButton(vm::miraViewerInitiative,Modifier.weight(1f)){Text("LET MIRA LEAD")}};OutlinedButton({val m=vm.viewerChat.lastOrNull{it.speaker=="MIRA"}?.text.orEmpty();if(m.isNotBlank())tts.speak(m,TextToSpeech.QUEUE_FLUSH,null,"mira")},Modifier.fillMaxWidth().padding(top=8.dp)){Icon(Icons.Default.VolumeUp,null);Spacer(Modifier.width(6.dp));Text("SPEAK MIRA")};Divider(Modifier.padding(vertical=16.dp));if(!vm.revealed){Text("VERIFY TARGET",fontWeight=FontWeight.Black);Text("Re-enter the exact original target after Mira has committed her blind report.",color=Soft,fontSize=12.sp);OutlinedTextField(reveal,{reveal=it;bad=false},Modifier.fillMaxWidth().padding(top=8.dp),label={Text("Original target")},minLines=3);Button({bad=!vm.verifyTarget(reveal)},enabled=vm.viewerCommitted,modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Text("VERIFY + REVEAL")};if(bad)Text("Target does not match the original seal, or Mira has not committed yet.",color=MaterialTheme.colorScheme.error,fontSize=12.sp)}else{Text("VERIFIED TARGET",color=Good,fontWeight=FontWeight.Black,fontSize=20.sp);Text(vm.revealedTarget,fontSize=18.sp,modifier=Modifier.padding(top=6.dp))}}}}

@Composable fun History(vm:SignalViewModel){Column(Modifier.fillMaxSize()){Header("HISTORY"){vm.screen=Screen.HOME};Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)){if(vm.history.isEmpty())Text("No verified sessions yet.",color=Soft);vm.history.forEach{r->Card(Modifier.fillMaxWidth().padding(vertical=6.dp),colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(14.dp)){Text(r.trn,color=Cyan,fontWeight=FontWeight.Black);Text(r.target,fontWeight=FontWeight.Bold);Text(r.report.take(500),color=Soft,fontSize=12.sp,modifier=Modifier.padding(top=6.dp));Text("Commit ${r.commit.take(16)}…",color=Violet,fontSize=10.sp,modifier=Modifier.padding(top=6.dp))}}}}}}
