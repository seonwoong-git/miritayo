package com.miritayo.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

private val Blue=Color(0xFF2F7FF6); private val Navy=Color(0xFF173B67); private val Mint=Color(0xFF23AD8B)
private val Red=Color(0xFFE75367); private val SoftBlue=Color(0xFFEAF3FF); private val SoftMint=Color(0xFFE9F8F3); private val SoftRed=Color(0xFFFFF0F2)
private enum class Screen{HOME,REHEARSAL,LIVE,INCIDENT,GUARDIAN,COMPLETE,RECORDS,SETTINGS}
private enum class Incident(val label:String,val icon:String,val message:String,val hint:String,val steps:List<String>){
    PASSED("정류장을 지나쳤을 때","🚏","정류장을 지나쳤어요. 괜찮아요.","다음 정류장에서 내려요.", listOf("버스 안에서 기다려요.","다음 정류장을 확인해요.","다음 정류장에서 내려요.")),
    WRONG_BUS("다른 버스를 탔을 때","🚌","다른 버스를 탔어요. 괜찮아요.","다음 안전한 정류장에서 내려요.", listOf("버스 번호를 다시 확인해요.","안전하게 다음 정류장까지 가요.","내린 뒤 필요하면 도움을 요청해요.")),
    DELAY("버스가 늦을 때","⏱️","버스가 늦고 있어요. 괜찮아요.","이 정류장에서 기다려요.", listOf("정류장 안쪽에서 기다려요.","도착예정 시간을 다시 확인해요.","오래 기다리기 어렵다면 도움을 요청해요."))
}
private data class Log(val type:Incident,val help:Boolean,val seconds:Int,val at:Long)
private data class AppSettings(val tts:Boolean=true,val vibrate:Boolean=true,val largeText:Boolean=false)

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MiritayoApp()}}
}

@Composable
fun MiritayoApp(){
    val context=LocalContext.current
    var screen by remember{mutableStateOf(Screen.HOME)}
    var incident by remember{mutableStateOf(Incident.PASSED)}
    var incidentStart by remember{mutableLongStateOf(0L)}
    var logs by remember{mutableStateOf(loadLogs(context))}
    var guardianPending by remember{mutableStateOf<Incident?>(null)}
    var gpsText by remember{mutableStateOf("GPS 확인 전")}
    var settings by remember{mutableStateOf(loadSettings(context))}
    val tts=rememberTts(context)
    fun speak(text:String){if(settings.tts)tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"miritayo")}
    fun alert(){
        if(!settings.vibrate)return
        val vib=if(Build.VERSION.SDK_INT>=31)(context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator else (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        if(Build.VERSION.SDK_INT>=26)vib.vibrate(VibrationEffect.createOneShot(220,VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") vib.vibrate(220)
    }
    MaterialTheme(colorScheme=lightColorScheme(primary=Blue,secondary=Mint,background=Color(0xFFF7FAFF),surface=Color.White,onSurface=Navy)){
        Surface(Modifier.fillMaxSize(),color=Color(0xFFF7FAFF)){
            when(screen){
                Screen.HOME->Home(settings, onScreen={screen=it}, recommended=recommend(logs))
                Screen.REHEARSAL->Rehearsal(recommend(logs), onHome={screen=Screen.HOME})
                Screen.LIVE->Live(gpsText,onGps={gpsText=it},onIncident={inc->incident=inc;incidentStart=System.currentTimeMillis();alert();speak(inc.message+" "+inc.hint);screen=Screen.INCIDENT},onHome={screen=Screen.HOME})
                Screen.INCIDENT->IncidentScreen(incident,onSuccess={
                    val sec=((System.currentTimeMillis()-incidentStart)/1000).toInt().coerceAtLeast(1);logs=logs+Log(incident,false,sec,System.currentTimeMillis());saveLogs(context,logs);screen=Screen.COMPLETE
                },onHelp={guardianPending=incident;screen=Screen.GUARDIAN})
                Screen.GUARDIAN->GuardianScreen(guardianPending,onAck={
                    val sec=((System.currentTimeMillis()-incidentStart)/1000).toInt().coerceAtLeast(1);logs=logs+Log(incident,true,sec,System.currentTimeMillis());saveLogs(context,logs);guardianPending=null;screen=Screen.COMPLETE
                },onHome={screen=Screen.HOME})
                Screen.COMPLETE->Complete(incident,logs.lastOrNull(),onRecords={screen=Screen.RECORDS},onHome={screen=Screen.HOME})
                Screen.RECORDS->Records(logs,onPractice={screen=Screen.REHEARSAL},onClear={logs=emptyList();saveLogs(context,logs)},onHome={screen=Screen.HOME})
                Screen.SETTINGS->SettingsScreen(settings,onChange={settings=it;saveSettings(context,it)},onSpeak={speak("미리타요 접근성 설정 화면입니다.")},onHome={screen=Screen.HOME})
            }
        }
    }
}
@Composable private fun rememberTts(context:Context):TextToSpeech?{
    var tts by remember{mutableStateOf<TextToSpeech?>(null)}
    DisposableEffect(Unit){
        val obj=TextToSpeech(context){if(it==TextToSpeech.SUCCESS)tts?.language=Locale.KOREAN};tts=obj
        onDispose{obj.shutdown()}
    };return tts
}
@Composable private fun Page(title:String,sub:String?=null,content:@Composable ColumnScope.()->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(15.dp),color=SoftBlue){Text("🚌",Modifier.padding(10.dp),fontSize=26.sp)};Spacer(Modifier.width(10.dp));Column{Text("미리타요",fontSize=24.sp,fontWeight=FontWeight.ExtraBold,color=Blue);Text("발달장애인의 대중교통 사전훈련·실전지원",fontSize=11.sp,color=Color.Gray)}}
        Text(title,fontSize=30.sp,lineHeight=36.sp,fontWeight=FontWeight.ExtraBold,color=Navy);if(sub!=null)Text(sub,fontSize=17.sp,lineHeight=25.sp,color=Color(0xFF647991));content();Spacer(Modifier.height(18.dp))
    }
}
@Composable private fun Home(settings:AppSettings,onScreen:(Screen)->Unit,recommended:Incident?){
    Page("미리 연습하고,\n실전에서도 같은 안내를 받아요.","현재 구현과 데모 기능을 구분해 중간심사에서 과장 없이 시연하도록 구성했습니다."){
        Info("오늘의 MVP 경로",SoftBlue){Text("우리집 → 도서관",fontSize=22.sp,fontWeight=FontWeight.Bold);Text("102번 버스 · 버스 지상 구간",color=Color.Gray)}
        Button(onClick={onScreen(Screen.REHEARSAL)},modifier=Modifier.fillMaxWidth().height(64.dp),shape=RoundedCornerShape(20.dp)){Text(if(recommended==null)"📖 미리 연습하기" else "📖 추천 연습: ${recommended.label}",fontWeight=FontWeight.Bold)}
        Button(onClick={onScreen(Screen.LIVE)},modifier=Modifier.fillMaxWidth().height(64.dp),colors=ButtonDefaults.buttonColors(containerColor=Mint),shape=RoundedCornerShape(20.dp)){Text("📍 지금 출발하기",fontWeight=FontWeight.Bold)}
        OutlinedButton(onClick={onScreen(Screen.GUARDIAN)},modifier=Modifier.fillMaxWidth().height(58.dp),shape=RoundedCornerShape(18.dp)){Text("🛡️ 보호자 화면")}
        OutlinedButton(onClick={onScreen(Screen.RECORDS)},modifier=Modifier.fillMaxWidth().height(58.dp),shape=RoundedCornerShape(18.dp)){Text("📊 기록·다음 리허설")}
        OutlinedButton(onClick={onScreen(Screen.SETTINGS)},modifier=Modifier.fillMaxWidth().height(58.dp),shape=RoundedCornerShape(18.dp)){Text("⚙️ 개인별 접근성 설정")}
        Info("구현 상태",Color.White){Text("● 구현: 3개 돌발상황 · TTS · 진동 · 실제 GPS 읽기 · 기록기반 재훈련",fontSize=14.sp);Text("● 데모: 자동 경로이탈 판정 · 보호자 푸시",fontSize=14.sp);Text("○ 후속: 버스 공공데이터 · FCM · 서버 저장",fontSize=14.sp)}
    }
}
@Composable private fun Rehearsal(rec:Incident?,onHome:()->Unit){
    var step by remember{mutableIntStateOf(0)};val target=rec?:Incident.PASSED
    val titles=listOf("정류장을 확인해요.","102번 버스 번호를 확인해요.",target.message)
    val hints=listOf("중앙도서관 정류장","교통카드를 찍고 자리에 앉아요.",target.hint)
    Page("사전 리허설","실전에서 볼 문장과 같은 표현을 사용합니다."){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center){repeat(3){Box(Modifier.padding(4.dp).size(if(it==step)18.dp else 11.dp).background(if(it==step)Blue else Color.LightGray,RoundedCornerShape(50)))}}
        Surface(shape=RoundedCornerShape(26.dp),color=if(step==2)SoftRed else SoftBlue,modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(26.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)){Text(if(step==0)"🚏" else if(step==1)"🚌" else target.icon,fontSize=54.sp);Text(titles[step],fontSize=24.sp,fontWeight=FontWeight.ExtraBold,textAlign=TextAlign.Center);Surface(shape=RoundedCornerShape(16.dp),color=Color.White){Text(hints[step],Modifier.padding(15.dp),fontSize=19.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center,color=if(step==2)Red else Blue)}}}
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){OutlinedButton(onClick={if(step>0)step-- else onHome()},modifier=Modifier.weight(1f).height(56.dp)){Text("이전")};Button(onClick={if(step<2)step++ else onHome()},modifier=Modifier.weight(1f).height(56.dp)){Text(if(step==2)"연습 완료" else "다음")}}
    }
}
@Composable private fun Live(gpsText:String,onGps:(String)->Unit,onIncident:(Incident)->Unit,onHome:()->Unit){
    val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){grants->if(grants.values.any{it})requestLocation(context,onGps) else onGps("위치 권한이 없어 GPS를 읽지 못했습니다.")}
    Page("실전 이동","실제 GPS 읽기와 시연용 돌발상황 트리거를 분리했습니다."){
        Info("현재 이동",SoftMint){Text("102번 버스 · 도서관 방향",fontSize=21.sp,fontWeight=FontWeight.Bold);Text("다음 행동: 중앙도서관 정류장에서 내려요.",fontSize=17.sp)}
        Info("GPS",SoftBlue){Text(gpsText,fontSize=15.sp);OutlinedButton(onClick={
            val ok=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
            if(ok)requestLocation(context,onGps) else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
        },modifier=Modifier.fillMaxWidth()){Text("실제 GPS 읽기")}}
        Text("돌발상황 3종 시연",fontWeight=FontWeight.Bold,fontSize=19.sp)
        Incident.values().forEach{inc->OutlinedButton(onClick={onIncident(inc)},modifier=Modifier.fillMaxWidth().height(60.dp),shape=RoundedCornerShape(18.dp)){Text("${inc.icon} ${inc.label}",fontWeight=FontWeight.Bold)}}
        Text("※ 자동 감지는 아직 데모이며, 실제 앱에서는 GPS·정류장 순서·버스 API를 연결할 예정입니다.",fontSize=13.sp,color=Color.Gray)
    }
}
private fun requestLocation(context:Context,onGps:(String)->Unit){
    val lm=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers=listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER)
    try{
        val last=providers.mapNotNull{p->runCatching{lm.getLastKnownLocation(p)}.getOrNull()}.maxByOrNull{it.time}
        if(last!=null){onGps("실제 위치: %.5f, %.5f · 오차 약 %.0fm".format(last.latitude,last.longitude,last.accuracy));return}
        val listener=object:LocationListener{override fun onLocationChanged(l:Location){onGps("실제 위치: %.5f, %.5f · 오차 약 %.0fm".format(l.latitude,l.longitude,l.accuracy));lm.removeUpdates(this)}}
        lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,0L,0f,listener,Looper.getMainLooper());onGps("GPS 위치를 확인 중입니다.")
    }catch(e:SecurityException){onGps("위치 권한을 확인해 주세요.")}
}
@Composable private fun IncidentScreen(inc:Incident,onSuccess:()->Unit,onHelp:()->Unit){
    Page("괜찮아요.","사전 리허설과 같은 문장과 행동 순서를 다시 보여줍니다."){
        Surface(shape=RoundedCornerShape(26.dp),color=SoftRed,modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(inc.icon,fontSize=54.sp);Text(inc.message,fontSize=24.sp,fontWeight=FontWeight.ExtraBold,textAlign=TextAlign.Center);Spacer(Modifier.height(12.dp));Surface(shape=RoundedCornerShape(16.dp),color=Color.White){Text(inc.hint,Modifier.padding(15.dp),fontSize=19.sp,fontWeight=FontWeight.Bold,color=Red)}}}
        Info("지금 할 일",SoftMint){inc.steps.forEachIndexed{i,s->Text("${i+1}. $s",fontSize=17.sp)}}
        Button(onClick=onSuccess,modifier=Modifier.fillMaxWidth().height(60.dp),shape=RoundedCornerShape(19.dp)){Text("✓ 혼자 다음 행동을 했어요",fontWeight=FontWeight.Bold)}
        OutlinedButton(onClick=onHelp,modifier=Modifier.fillMaxWidth().height(60.dp),shape=RoundedCornerShape(19.dp)){Text("🆘 도움이 필요해요",fontWeight=FontWeight.Bold)}
    }
}
@Composable private fun GuardianScreen(pending:Incident?,onAck:()->Unit,onHome:()->Unit){
    Page("보호자 화면","상시 감시가 아니라 도움 요청 시 필요한 정보만 전달하는 방향입니다."){
        if(pending==null)Info("도움 요청 없음",Color.White){Text("실제 푸시는 후속 FCM 연동 항목입니다.",color=Color.Gray)}
        else Info("🆘 도움 요청",SoftRed){Text("상황: ${pending.label}",fontSize=19.sp,fontWeight=FontWeight.Bold);Text("현재 위치: GPS 확인값 전달 예정");Text("요청 시각: 방금");Button(onClick=onAck,modifier=Modifier.fillMaxWidth()){Text("보호자가 확인했어요")}}
        Info("개인정보 설계",SoftBlue){Text("• 도움 요청 시 위치·상황 공유");Text("• 상시 위치 감시는 하지 않음");Text("• 원시 위치기록 장기 보관 지양")}
        TextButton(onClick=onHome,modifier=Modifier.align(Alignment.CenterHorizontally)){Text("홈으로")}
    }
}
@Composable private fun Complete(inc:Incident,last:Log?,onRecords:()->Unit,onHome:()->Unit){
    Page("이동 기록을 저장했어요.","이번 결과는 다음 리허설 추천에 반영됩니다."){
        Info("이번 수행",SoftMint){Text(inc.label,fontSize=21.sp,fontWeight=FontWeight.Bold);if(last!=null){Text("보호자 도움: ${if(last.help)"요청함" else "요청하지 않음"}");Text("대처시간: ${last.seconds}초")}}
        Button(onClick=onRecords,modifier=Modifier.fillMaxWidth().height(58.dp)){Text("기록과 추천 보기")}
        OutlinedButton(onClick=onHome,modifier=Modifier.fillMaxWidth().height(58.dp)){Text("홈으로")}
    }
}
@Composable private fun Records(logs:List<Log>,onPractice:()->Unit,onClear:()->Unit,onHome:()->Unit){
    val rec=recommend(logs);val success=logs.count{!it.help};val helps=logs.count{it.help};val avg=if(logs.isEmpty())0 else logs.map{it.seconds}.average().roundToInt()
    Page("기록 → 다음 리허설","도움 요청 여부와 대처시간을 실제 저장해 다음 연습 우선순위를 계산합니다."){
        Info("다음 추천",SoftRed){Text(rec?.label?:"기록이 아직 없어요.",fontSize=21.sp,fontWeight=FontWeight.Bold)}
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){Info("독립 대처",SoftMint,Modifier.weight(1f)){Text("${success}회",fontSize=24.sp,fontWeight=FontWeight.Bold)};Info("도움 요청",SoftRed,Modifier.weight(1f)){Text("${helps}회",fontSize=24.sp,fontWeight=FontWeight.Bold)}}
        Info("평균 대처시간",SoftBlue){Text(if(logs.isEmpty())"-" else "${avg}초",fontSize=24.sp,fontWeight=FontWeight.Bold)}
        Info("최근 기록",Color.White){if(logs.isEmpty())Text("아직 기록이 없습니다.") else logs.takeLast(6).reversed().forEach{Text("${it.type.label} · ${if(it.help)"도움 요청" else "독립 대처"} · ${it.seconds}초",fontSize=14.sp)}}
        Button(onClick=onPractice,modifier=Modifier.fillMaxWidth()){Text("추천 상황 다시 연습하기")};OutlinedButton(onClick=onClear,modifier=Modifier.fillMaxWidth()){Text("기록 초기화")};TextButton(onClick=onHome,modifier=Modifier.align(Alignment.CenterHorizontally)){Text("홈으로")}
    }
}
@Composable private fun SettingsScreen(s:AppSettings,onChange:(AppSettings)->Unit,onSpeak:()->Unit,onHome:()->Unit){
    Page("개인별 접근성 설정","‘자폐 모드’처럼 일괄 적용하지 않고 사용자가 직접 선택합니다."){
        Info("설정",Color.White){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("음성 안내",Modifier.weight(1f));Switch(s.tts,{onChange(s.copy(tts=it))})}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("진동 알림",Modifier.weight(1f));Switch(s.vibrate,{onChange(s.copy(vibrate=it))})}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("큰 글자 선호",Modifier.weight(1f));Switch(s.largeText,{onChange(s.copy(largeText=it))})}
        }
        OutlinedButton(onClick=onSpeak,modifier=Modifier.fillMaxWidth()){Text("현재 화면 읽어주기")}
        Info("UI 원칙",SoftBlue){Text("• 한 번에 많은 선택지를 주지 않기");Text("• 짧고 구체적인 문장");Text("• 실수해도 쉽게 다시 하기");Text("• 성인 이용자를 유아화하지 않는 표현")}
        TextButton(onClick=onHome,modifier=Modifier.align(Alignment.CenterHorizontally)){Text("홈으로")}
    }
}
@Composable private fun Info(title:String,color:Color,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),color=color){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,fontSize=18.sp,fontWeight=FontWeight.ExtraBold,color=Navy);content()}}
}
private fun recommend(logs:List<Log>):Incident?{
    if(logs.isEmpty())return null
    return Incident.values().maxByOrNull{inc->logs.filter{it.type==inc}.sumOf{(if(it.help)300 else 100)+it.seconds.coerceAtMost(60)}}
}
private fun saveLogs(c:Context,logs:List<Log>){val a=JSONArray();logs.takeLast(50).forEach{a.put(JSONObject().put("type",it.type.name).put("help",it.help).put("seconds",it.seconds).put("at",it.at))};c.getSharedPreferences("miritayo",0).edit().putString("logs",a.toString()).apply()}
private fun loadLogs(c:Context):List<Log>{return runCatching{val a=JSONArray(c.getSharedPreferences("miritayo",0).getString("logs","[]"));(0 until a.length()).map{i->val o=a.getJSONObject(i);Log(Incident.valueOf(o.getString("type")),o.getBoolean("help"),o.getInt("seconds"),o.getLong("at"))}}.getOrDefault(emptyList())}
private fun saveSettings(c:Context,s:AppSettings){c.getSharedPreferences("miritayo",0).edit().putBoolean("tts",s.tts).putBoolean("vib",s.vibrate).putBoolean("large",s.largeText).apply()}
private fun loadSettings(c:Context)=AppSettings(c.getSharedPreferences("miritayo",0).getBoolean("tts",true),c.getSharedPreferences("miritayo",0).getBoolean("vib",true),c.getSharedPreferences("miritayo",0).getBoolean("large",false))
