package com.miritayo.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.*
import android.os.*
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

private val Blue=Color(0xFF2F7FF6); private val Navy=Color(0xFF173B67); private val Mint=Color(0xFF23AD8B)
private val Red=Color(0xFFE75367); private val SoftBlue=Color(0xFFEAF3FF); private val SoftMint=Color(0xFFE9F8F3); private val SoftRed=Color(0xFFFFF0F2); private val SoftGray=Color(0xFFF4F6F9)
private enum class Screen{HOME,REHEARSAL,LIVE,INCIDENT,GUARDIAN,COMPLETE,RECORDS,SETTINGS,ABOUT}
private enum class HelpMode{NONE,ACTUAL,TEST}
private enum class Incident(val label:String,val short:String,val icon:String,val message:String,val hint:String,val steps:List<String>){
 PASSED("정류장을 지나쳤을 때","정류장을 지나쳤어요","🚏","정류장을 지나쳤어요.","다음 정류장에서 내려요.",listOf("버스 안에서 기다려요.","다음 정류장에서 내려요.","내린 뒤 다시 확인해요.")),
 WRONG_BUS("다른 버스를 탔을 때","다른 버스를 탔어요","🚌","다른 버스를 탔어요.","다음 정류장에서 내려요.",listOf("버스 번호를 다시 확인해요.","다음 정류장에서 내려요.","맞는 버스를 찾아요.")),
 DELAY("버스가 늦을 때","버스가 늦어요","⏱️","버스가 늦고 있어요.","조금 더 기다려요.",listOf("정류장에서 기다려요.","조금 더 기다려요.","오래 기다리면 도움을 요청해요."))
}
private data class Log(val type:Incident,val help:Boolean,val test:Boolean,val seconds:Int,val at:Long)
private data class AppSettings(val tts:Boolean=true,val vibrate:Boolean=true,val largeText:Boolean=false,val icons:Boolean=true)
private val LocalIcons=staticCompositionLocalOf{true}

class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{App()}}}

@Composable private fun App(){
 val c=LocalContext.current; var screen by remember{mutableStateOf(Screen.HOME)}; var inc by remember{mutableStateOf(Incident.PASSED)}; var start by remember{mutableLongStateOf(0L)}
 var logs by remember{mutableStateOf(loadLogs(c))}; var pending by remember{mutableStateOf<Incident?>(null)}; var mode by remember{mutableStateOf(HelpMode.NONE)}; var requestedAt by remember{mutableLongStateOf(0L)}
 var gps by remember{mutableStateOf("GPS 확인 전")}; var s by remember{mutableStateOf(loadSettings(c))}; val tts=rememberTts(c)
 fun speak(x:String){if(s.tts)tts?.speak(x,TextToSpeech.QUEUE_FLUSH,null,"miritayo")}; fun vib(){if(!s.vibrate)return; val v=if(Build.VERSION.SDK_INT>=31)(c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator else @Suppress("DEPRECATION")(c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator); if(Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createOneShot(220,VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") v.vibrate(220)}
 val d=LocalDensity.current; val scaled=remember(d.density,s.largeText){Density(d.density,if(s.largeText)1.18f else 1f)}
 MaterialTheme(colorScheme=lightColorScheme(primary=Blue,secondary=Mint,background=Color(0xFFF7FAFF),surface=Color.White,onSurface=Navy)){
  CompositionLocalProvider(LocalDensity provides scaled,LocalIcons provides s.icons){Surface(Modifier.fillMaxSize(),color=Color(0xFFF7FAFF)){
   when(screen){
    Screen.HOME->Home(recommend(logs)){screen=it}
    Screen.REHEARSAL->Rehearsal(recommend(logs),{speak(it)}){screen=Screen.HOME}
    Screen.LIVE->Live(gps,{gps=it},{x->inc=x;start=System.currentTimeMillis();vib();speak(x.message+" "+x.hint);screen=Screen.INCIDENT}){screen=Screen.HOME}
    Screen.INCIDENT->IncidentView(inc,{speak(inc.message+" "+inc.hint+" "+inc.steps.joinToString(" "))},{logs=logs+Log(inc,false,false,elapsed(start),System.currentTimeMillis());saveLogs(c,logs);screen=Screen.COMPLETE},{pending=inc;mode=HelpMode.ACTUAL;requestedAt=System.currentTimeMillis();screen=Screen.GUARDIAN},{pending=inc;mode=HelpMode.TEST;requestedAt=System.currentTimeMillis();screen=Screen.GUARDIAN})
    Screen.GUARDIAN->Guardian(pending,mode,requestedAt,gps,{gps=it},{if(pending!=null&&mode!=HelpMode.NONE){logs=logs+Log(inc,mode==HelpMode.ACTUAL,mode==HelpMode.TEST,elapsed(start),System.currentTimeMillis());saveLogs(c,logs)};pending=null;mode=HelpMode.NONE;screen=if(start>0)Screen.COMPLETE else Screen.HOME},{pending=null;mode=HelpMode.NONE;screen=Screen.HOME})
    Screen.COMPLETE->Complete(inc,logs.lastOrNull(),{screen=Screen.RECORDS}){screen=Screen.HOME}
    Screen.RECORDS->Records(logs,{screen=Screen.REHEARSAL},{logs=emptyList();saveLogs(c,logs)}){screen=Screen.HOME}
    Screen.SETTINGS->Settings(s,{s=it;saveSettings(c,it)},{speak("미리타요 접근성 설정 화면입니다.")}){screen=Screen.HOME}
    Screen.ABOUT->About{screen=Screen.HOME}
   }
  }}
 }
}
@Composable private fun rememberTts(c:Context):TextToSpeech?{var t by remember{mutableStateOf<TextToSpeech?>(null)};DisposableEffect(Unit){lateinit var o:TextToSpeech;o=TextToSpeech(c){if(it==TextToSpeech.SUCCESS)o.language=Locale.KOREAN};t=o;onDispose{o.shutdown()}};return t}
private fun elapsed(s:Long)=if(s<=0)1 else ((System.currentTimeMillis()-s)/1000).toInt().coerceAtLeast(1)
@Composable private fun Page(title:String,sub:String?=null,body:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){if(LocalIcons.current)Text("🚌",fontSize=28.sp);Spacer(Modifier.width(8.dp));Column{Text("미리타요",24.sp,fontWeight=FontWeight.ExtraBold,color=Blue);Text("발달장애인의 대중교통 사전 연습·실전 이동 훈련",11.sp,color=Color.Gray)}};Text(title,30.sp,lineHeight=36.sp,fontWeight=FontWeight.ExtraBold,color=Navy);sub?.let{Text(it,17.sp,lineHeight=25.sp,color=Color(0xFF647991))};body();Spacer(Modifier.height(18.dp))}}
@Composable private fun BoxInfo(title:String,color:Color=Color.White,mod:Modifier=Modifier,body:@Composable ColumnScope.()->Unit){Surface(mod.fillMaxWidth(),shape=RoundedCornerShape(22.dp),color=color){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,18.sp,fontWeight=FontWeight.ExtraBold,color=Navy);body()}}}
@Composable private fun Home(rec:Incident?,go:(Screen)->Unit){Page("미리 연습하고,\n실제 이동 상황에 대비해요.","사전 연습과 실전 이동 훈련을 이어서 사용해요."){BoxInfo("연습 경로 예시",SoftBlue){Text("우리집 → 도서관",22.sp,fontWeight=FontWeight.Bold);Text("102번 버스 · 버스 지상 구간");Text("※ 실제 지도 길안내가 아닌 훈련용 예시 경로입니다.",12.sp,color=Color.Gray)};Button({go(Screen.REHEARSAL)},Modifier.fillMaxWidth().height(64.dp)){Text(if(rec==null)"미리 연습하기" else "추천 연습: ${rec.label}")};Button({go(Screen.LIVE)},Modifier.fillMaxWidth().height(64.dp),colors=ButtonDefaults.buttonColors(containerColor=Mint)){Text("실전 이동 훈련")};OutlinedButton({go(Screen.RECORDS)},Modifier.fillMaxWidth()){Text("기록 보기")};OutlinedButton({go(Screen.SETTINGS)},Modifier.fillMaxWidth()){Text("접근성 설정")};TextButton({go(Screen.GUARDIAN)},Modifier.fillMaxWidth()){Text("보호자 확인 화면")};TextButton({go(Screen.ABOUT)},Modifier.fillMaxWidth()){Text("앱 정보 · 향후 기능")}}}
@Composable private fun Rehearsal(rec:Incident?,speak:(String)->Unit,home:()->Unit){var step by remember{mutableIntStateOf(0)};val t=rec?:Incident.PASSED;val title=listOf("정류장을 확인해요.","102번 버스를 확인해요.",t.message);val hint=listOf("중앙도서관 정류장","교통카드를 준비하고 버스에 타요.",t.hint);Page("사전 리허설","한 화면에서 한 행동씩, 실전과 같은 표현으로 연습해요."){BoxInfo("${step+1} / 3",if(step==2)SoftRed else SoftBlue){Text(title[step],24.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth());Text(hint[step],19.sp,fontWeight=FontWeight.Bold,color=if(step==2)Red else Blue,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())};OutlinedButton({speak(title[step]+" "+hint[step])},Modifier.fillMaxWidth()){Text("음성으로 듣기")};Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){OutlinedButton({if(step>0)step-- else home()},Modifier.weight(1f)){Text("이전")};Button({if(step<2)step++ else home()},Modifier.weight(1f)){Text(if(step==2)"연습 완료" else "다음")}}}}
@Composable private fun Live(gps:String,onGps:(String)->Unit,onIncident:(Incident)->Unit,home:()->Unit){val c=LocalContext.current;val p=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){if(it.values.any{x->x})requestLocation(c,onGps) else onGps("위치 권한이 없어 GPS를 읽지 못했습니다.")};Page("실전 이동 훈련","현재 위치를 확인하고, 상황이 생기면 해당 항목을 선택해 단계별로 연습해요."){BoxInfo("현재 위치 확인",SoftBlue){Text(gps);OutlinedButton({val ok=ContextCompat.checkSelfPermission(c,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;if(ok)requestLocation(c,onGps) else p.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))},Modifier.fillMaxWidth()){Text("GPS로 현재 위치 확인")}};Text("어떤 상황인가요?",19.sp,fontWeight=FontWeight.Bold);Incident.values().forEach{x->OutlinedButton({onIncident(x)},Modifier.fillMaxWidth().height(60.dp)){Text((if(LocalIcons.current)x.icon+" " else "")+x.short)}};BoxInfo("현재 구현 / 후속 개발",SoftGray){Text("현재 구현: GPS 위치 확인 · 3개 상황 선택 · 단계별 대처 안내");Text("후속 개발: 실제 지도 · 실시간 버스 정보 · 자동 경로이탈 감지",color=Color.Gray)};TextButton(home,Modifier.align(Alignment.CenterHorizontally)){Text("홈으로")}}}
private fun requestLocation(c:Context,out:(String)->Unit){val lm=c.getSystemService(Context.LOCATION_SERVICE) as LocationManager;try{val last=listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER).mapNotNull{runCatching{lm.getLastKnownLocation(it)}.getOrNull()}.maxByOrNull{it.time};if(last!=null){out("현재 위치: %.5f, %.5f · 오차 약 %.0fm".format(last.latitude,last.longitude,last.accuracy));return};val l=object:LocationListener{override fun onLocationChanged(x:Location){out("현재 위치: %.5f, %.5f · 오차 약 %.0fm".format(x.latitude,x.longitude,x.accuracy));lm.removeUpdates(this)}};lm.requestLocationUpdates(if(lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER,0L,0f,l,Looper.getMainLooper());out("GPS 위치를 확인 중입니다.")}catch(_:Exception){out("현재 위치를 확인하지 못했습니다.")}}
@Composable private fun IncidentView(i:Incident,speak:()->Unit,success:()->Unit,help:()->Unit,test:()->Unit){Page("괜찮아요.","한 화면에서 한 행동씩 확인해요."){BoxInfo(i.short,SoftRed){Text(i.hint,22.sp,fontWeight=FontWeight.Bold,color=Red);i.steps.forEachIndexed{n,x->Text("${n+1}. $x",18.sp)}};OutlinedButton(speak,Modifier.fillMaxWidth()){Text("음성으로 다시 듣기")};Button(success,Modifier.fillMaxWidth().height(58.dp)){Text("혼자 다음 행동을 했어요")};OutlinedButton(help,Modifier.fillMaxWidth().height(58.dp)){Text("도움이 필요해요")};TextButton(test,Modifier.fillMaxWidth()){Text("도움요청 기능 확인(테스트)")}}}
@Composable private fun Guardian(pending:Incident?,mode:HelpMode,at:Long,gps:String,onGps:(String)->Unit,ack:()->Unit,home:()->Unit){val c=LocalContext.current;val p=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){if(it.values.any{x->x})requestLocation(c,onGps) else onGps("위치 권한이 없습니다.")};Page("보호자 확인 화면","도움 요청 시 필요한 상황과 위치정보를 앱 화면에서 확인해요."){if(pending==null)BoxInfo("현재 도움 요청 없음"){Text("실제 푸시 알림은 후속 FCM 연동 항목입니다.",color=Color.Gray)} else BoxInfo(if(mode==HelpMode.TEST)"기능 확인(테스트)" else "도움 요청",if(mode==HelpMode.TEST)SoftBlue else SoftRed){Text("상황: ${pending.short}",19.sp,fontWeight=FontWeight.Bold);Text("요청 시각: ${fmt(at)}");Text("위치정보: $gps");OutlinedButton({val ok=ContextCompat.checkSelfPermission(c,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;if(ok)requestLocation(c,onGps) else p.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))},Modifier.fillMaxWidth()){Text("현재 위치 다시 확인")};Button(ack,Modifier.fillMaxWidth()){Text(if(mode==HelpMode.TEST)"기능 확인 완료" else "보호자가 확인했어요")}};BoxInfo("후속 개발 예정",SoftGray){Text("• 네이버지도·카카오맵 실제 지도 연동");Text("• FCM 보호자 푸시 알림");Text("• 전화·메시지 빠른 연결")};TextButton(home,Modifier.align(Alignment.CenterHorizontally)){Text("홈으로")}}}
@Composable private fun Complete(i:Incident,l:Log?,records:()->Unit,home:()->Unit){Page("수행 기록을 저장했어요.","실제 수행 기록은 다음 리허설 추천에 반영됩니다."){BoxInfo("이번 수행",SoftMint){Text(i.label,20.sp,fontWeight=FontWeight.Bold);l?.let{Text("결과: "+when{it.test->"기능 확인 테스트";it.help->"실제 도움 요청";else->"독립 대처"});Text("대처시간: ${it.seconds}초");if(it.test)Text("※ 기능 테스트는 실제 도움요청 통계에서 제외됩니다.",12.sp,color=Color.Gray)}};Button(records,Modifier.fillMaxWidth()){Text("기록과 추천 보기")};OutlinedButton(home,Modifier.fillMaxWidth()){Text("홈으로")}}}
@Composable private fun Records(logs:List<Log>,practice:()->Unit,clear:()->Unit,home:()->Unit){val real=logs.filter{!it.test};val r=recommend(logs);val avg=if(real.isEmpty())0 else real.map{it.seconds}.average().roundToInt();Page("기록 → 다음 리허설","실제 수행 기록을 바탕으로 다시 연습할 상황을 규칙 기반으로 추천해요."){BoxInfo("다음 추천",SoftRed){Text(r?.label?:"실제 수행 기록이 아직 없어요.",20.sp,fontWeight=FontWeight.Bold)};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){BoxInfo("독립 대처",SoftMint,Modifier.weight(1f)){Text("${real.count{!it.help}}회",24.sp,fontWeight=FontWeight.Bold)};BoxInfo("실제 도움",SoftRed,Modifier.weight(1f)){Text("${real.count{it.help}}회",24.sp,fontWeight=FontWeight.Bold)}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){BoxInfo("기능 테스트",SoftBlue,Modifier.weight(1f)){Text("${logs.count{it.test}}회",24.sp,fontWeight=FontWeight.Bold)};BoxInfo("평균 대처시간",SoftGray,Modifier.weight(1f)){Text(if(real.isEmpty())"-" else "${avg}초",24.sp,fontWeight=FontWeight.Bold)}};BoxInfo("최근 기록"){if(logs.isEmpty())Text("아직 기록이 없습니다.") else logs.takeLast(8).reversed().forEach{Text("${it.type.short} · ${if(it.test)"기능 테스트" else if(it.help)"실제 도움 요청" else "독립 대처"} · ${it.seconds}초")}};Button(practice,Modifier.fillMaxWidth()){Text("추천 상황 다시 연습하기")};OutlinedButton(clear,Modifier.fillMaxWidth()){Text("기록 초기화")};TextButton(home,Modifier.align(Alignment.CenterHorizontally)){Text("홈으로")}}}
@Composable private fun Settings(s:AppSettings,change:(AppSettings)->Unit,speak:()->Unit,home:()->Unit){Page("접근성 설정","사용자가 필요한 안내 방식을 직접 선택해요."){BoxInfo("설정"){Row(verticalAlignment=Alignment.CenterVertically){Text("음성 안내",Modifier.weight(1f));Switch(s.tts,{change(s.copy(tts=it))})};Row(verticalAlignment=Alignment.CenterVertically){Text("진동 알림",Modifier.weight(1f));Switch(s.vibrate,{change(s.copy(vibrate=it))})};Row(verticalAlignment=Alignment.CenterVertically){Text("큰 글자",Modifier.weight(1f));Switch(s.largeText,{change(s.copy(largeText=it))})};Row(verticalAlignment=Alignment.CenterVertically){Text("그림·아이콘 함께 보기",Modifier.weight(1f));Switch(s.icons,{change(s.copy(icons=it))})}};OutlinedButton(speak,Modifier.fillMaxWidth()){Text("현재 화면 읽어주기")};BoxInfo("화면 원칙",SoftBlue){Text("• 한 화면에서 한 행동씩 안내");Text("• 짧고 구체적인 문장");Text("• 큰 버튼과 명확한 색상 구분");Text("• 10대·성인도 사용할 수 있는 표현")};TextButton(home,Modifier.align(Alignment.CenterHorizontally)){Text("홈으로")}}}
@Composable private fun About(home:()->Unit){Page("앱 정보 · 향후 기능","Miritayo v0.3 · 실증 피드백 반영 MVP"){BoxInfo("v0.3 반영 내용",SoftMint){Text("• ‘실전 이동 훈련’ 명칭 통일");Text("• 돌발상황 안내 문구 단순화");Text("• 보호자 화면 GPS 위치정보 확인");Text("• 실제 도움요청과 기능 테스트 기록 분리");Text("• 큰 글자 설정 실제 반영");Text("• 현재 구현/후속개발 기능 구분")};BoxInfo("초기 실증에서 확인",SoftBlue){Text("• 발달장애 당사자 1명 초기 실증");Text("• 3개 돌발상황 다음 행동 선택 성공");Text("• 돌발상황 안내 이해도 2/5 → 문구 단순화 필요");Text("• 실제 지도·경로 연계 필요 의견")};BoxInfo("후속 개발 예정",SoftGray){Text("• 네이버지도·카카오맵 실제 지도 연동");Text("• 버스 공공데이터 연계");Text("• 자동 경로이탈 감지");Text("• FCM 보호자 푸시");Text("• 서버·DB 및 추가 실증")};TextButton(home,Modifier.align(Alignment.CenterHorizontally)){Text("홈으로")}}}
private fun recommend(logs:List<Log>):Incident?{val r=logs.filter{!it.test};if(r.isEmpty())return null;return Incident.values().maxByOrNull{i->r.filter{it.type==i}.sumOf{(if(it.help)300 else 100)+it.seconds.coerceAtMost(60)}}}
private fun saveLogs(c:Context,logs:List<Log>){val a=JSONArray();logs.takeLast(50).forEach{a.put(JSONObject().put("type",it.type.name).put("help",it.help).put("test",it.test).put("seconds",it.seconds).put("at",it.at))};c.getSharedPreferences("miritayo",0).edit().putString("logs",a.toString()).apply()}
private fun loadLogs(c:Context):List<Log>=runCatching{val a=JSONArray(c.getSharedPreferences("miritayo",0).getString("logs","[]"));(0 until a.length()).map{i->val o=a.getJSONObject(i);val old=o.optBoolean("help",false);val has=o.has("test");Log(Incident.valueOf(o.getString("type")),if(has)old else false,if(has)o.optBoolean("test",false) else old,o.optInt("seconds",1).coerceAtLeast(1),o.optLong("at",System.currentTimeMillis()))}}.getOrDefault(emptyList())
private fun saveSettings(c:Context,s:AppSettings){c.getSharedPreferences("miritayo",0).edit().putBoolean("tts",s.tts).putBoolean("vib",s.vibrate).putBoolean("large",s.largeText).putBoolean("icons",s.icons).apply()}
private fun loadSettings(c:Context):AppSettings{val p=c.getSharedPreferences("miritayo",0);return AppSettings(p.getBoolean("tts",true),p.getBoolean("vib",true),p.getBoolean("large",false),p.getBoolean("icons",true))}
private fun fmt(t:Long)=if(t<=0)"-" else SimpleDateFormat("yyyy.MM.dd HH:mm:ss",Locale.KOREA).format(Date(t))
