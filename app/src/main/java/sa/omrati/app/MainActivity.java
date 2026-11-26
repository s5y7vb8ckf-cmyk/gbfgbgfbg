package sa.omrati.app;
import android.Manifest;
import android.hardware.*;
import android.view.Surface;
import android.hardware.GeomagneticField;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Build;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.webkit.*;
import androidx.webkit.WebViewAssetLoader;
import java.util.Locale;

public class MainActivity extends Activity {
 private Sensor stepSensor; private boolean stepListening=false; private float stepBaseline=-1; private int stepCount=0; private boolean stepActive=false;
 private final SensorEventListener stepListener=new SensorEventListener(){
 public void onAccuracyChanged(Sensor sensor,int accuracy){}
 public void onSensorChanged(SensorEvent e){if(!stepListening)return; if(stepSensor.getType()==Sensor.TYPE_STEP_COUNTER){float value=e.values[0];if(stepBaseline>=0&&value>=stepBaseline)stepCount+=(int)(value-stepBaseline);stepBaseline=value;}else stepCount++; sendSteps("active");}
 };
 private void sendSteps(String status){if(web!=null)web.evaluateJavascript("window.receiveSteps&&receiveSteps("+stepCount+",'"+status+"')",null);}
 private void stopSteps(){if(sm!=null)sm.unregisterListener(stepListener);stepListening=false;stepBaseline=-1;}
 private void beginSteps(){stopSteps();if(!stepActive)return;if(stepSensor==null){sendSteps("unsupported");return;}if(Build.VERSION.SDK_INT>=29&&checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED){sendSteps("permission");return;}stepListening=sm.registerListener(stepListener,stepSensor,SensorManager.SENSOR_DELAY_NORMAL);sendSteps(stepListening?"active":"unsupported");}
 private SensorManager sm; private Sensor rotation; private boolean qiblaOn; private float declination; private boolean northReady; private int sensorAccuracy=SensorManager.SENSOR_STATUS_UNRELIABLE;
 private final SensorEventListener compass=new SensorEventListener(){
 public void onAccuracyChanged(Sensor sensor,int accuracy){sensorAccuracy=accuracy;}
 public void onSensorChanged(SensorEvent e){if(!qiblaOn||!northReady||web==null)return;float[] m=new float[9],r=new float[9],o=new float[3];SensorManager.getRotationMatrixFromVector(m,e.values);int d=getWindowManager().getDefaultDisplay().getRotation();int x=SensorManager.AXIS_X,y=SensorManager.AXIS_Y;if(d==Surface.ROTATION_90){x=SensorManager.AXIS_Y;y=SensorManager.AXIS_MINUS_X;}else if(d==Surface.ROTATION_180){x=SensorManager.AXIS_MINUS_X;y=SensorManager.AXIS_MINUS_Y;}else if(d==Surface.ROTATION_270){x=SensorManager.AXIS_MINUS_Y;y=SensorManager.AXIS_X;}SensorManager.remapCoordinateSystem(m,x,y,r);SensorManager.getOrientation(r,o);double h=(Math.toDegrees(o[0])+declination+360)%360;web.evaluateJavascript("window.qHeading&&qHeading("+h+","+sensorAccuracy+")",null);}
 };
 private void stopCompass(){qiblaOn=false;if(sm!=null)sm.unregisterListener(compass);}
 private WebView web; private TextToSpeech tts; private boolean ready; private GeolocationPermissions.Callback pending; private String origin;
 private boolean requestingLocation;
 private static final String CHANNEL="ritual_updates";
 private static final String HOST="appassets.androidplatform.net";
 @Override public void onCreate(Bundle b){super.onCreate(b);
  sm=(SensorManager)getSystemService(SENSOR_SERVICE);stepSensor=sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);if(stepSensor==null)stepSensor=sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);rotation=sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);if(sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)==null)rotation=null;
  tts=new TextToSpeech(this,status->{if(status==TextToSpeech.SUCCESS){int r=tts.setLanguage(new Locale("ar","SA"));ready=r>=0;tts.setSpeechRate(.78f);}});
  web=new WebView(this);setContentView(web);
  WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setMediaPlaybackRequiresUserGesture(false);s.setDomStorageEnabled(true);s.setGeolocationEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setSupportMultipleWindows(false);
  WebViewAssetLoader loader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
  web.addJavascriptInterface(new Voice(),"OmratiNative");
  web.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return loader.shouldInterceptRequest(r.getUrl());}
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){Uri u=r.getUrl();if("https".equals(u.getScheme())&&HOST.equals(u.getHost()))return false;if("https".equals(u.getScheme()))try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}return true;}
  });
  web.setWebChromeClient(new WebChromeClient(){@Override public void onGeolocationPermissionsShowPrompt(String o,GeolocationPermissions.Callback c){if(!("https://"+HOST).equals(o)){c.invoke(o,false,false);return;}if(hasLocation()){c.invoke(o,true,false);return;}pending=c;origin=o;if(!requestingLocation)askLocation();}});
  web.loadUrl("https://"+HOST+"/assets/index.html");
  createChannel();
  if(!getPreferences(MODE_PRIVATE).getBoolean("permissionsAsked",false)){web.post(()->{getPreferences(MODE_PRIVATE).edit().putBoolean("permissionsAsked",true).apply();if(!hasLocation())askLocation();else {getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked",true).apply();askNotifications();}});}
 }
 private void askLocation(){requestingLocation=true;requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},7);}
 private void askNotifications(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},8);}
 private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"تنبيهات المناسك",NotificationManager.IMPORTANCE_DEFAULT);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
 private void ritualNotification(String message){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;NotificationManager nm=getSystemService(NotificationManager.class);if(!nm.areNotificationsEnabled())return;PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder n=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);n.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("عُمرتي").setContentText(message).setContentIntent(pi).setAutoCancel(true);nm.notify(12,n.build());}
 private boolean hasLocation(){return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==9){beginSteps();return;}if(r==7){requestingLocation=false;if(trackingRequested&&hasLocation())startTracking();trackingRequested=false;if(pending!=null){pending.invoke(origin,hasLocation(),false);pending=null;}if(!getPreferences(MODE_PRIVATE).getBoolean("notificationAsked",false)){getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked",true).apply();web.post(()->askNotifications());}}}
 private boolean trackingRequested; private void startTracking(){if(!hasLocation()){trackingRequested=true;if(!requestingLocation)askLocation();return;}trackingRequested=false;try{Intent i=new Intent(this,PrayerService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}catch(Exception e){android.widget.Toast.makeText(this,"تعذر بدء خدمة الموقع",0).show();}}
 public class Voice {
 @JavascriptInterface public void trackingStart(){runOnUiThread(()->startTracking());}
 @JavascriptInterface public void trackingStop(){runOnUiThread(()->{getSharedPreferences(PrayerService.PREF,MODE_PRIVATE).edit().putBoolean("enabled",false).apply();stopService(new Intent(MainActivity.this,PrayerService.class));});}
 @JavascriptInterface public boolean trackingEnabled(){return getSharedPreferences(PrayerService.PREF,MODE_PRIVATE).getBoolean("enabled",false);}
 @JavascriptInterface public String prayerSchedule(){return getSharedPreferences(PrayerService.PREF,MODE_PRIVATE).getString("schedule","");}
 @JavascriptInterface public void prayerSettings(boolean adhan,int a,int b,int c,int d,int e){int[] v={a,b,c,d,e};android.content.SharedPreferences.Editor ed=getSharedPreferences(PrayerService.PREF,MODE_PRIVATE).edit().putBoolean("adhan",adhan);for(int j=0;j<5;j++)ed.putInt("iqama"+j,Math.max(0,Math.min(90,v[j])));ed.apply();}
 @JavascriptInterface public void stepsStart(int saved){runOnUiThread(()->{stepCount=Math.max(0,saved);stepActive=true;beginSteps();});} @JavascriptInterface public void stepsStop(){runOnUiThread(()->{stepActive=false;stopSteps();});} @JavascriptInterface public void stepsPermission(){runOnUiThread(()->{if(stepSensor==null){sendSteps("unsupported");return;}if(Build.VERSION.SDK_INT>=29&&checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION},9);else beginSteps();});} @JavascriptInterface public boolean hasCompass(){return rotation!=null;} @JavascriptInterface public void compassStart(double lat,double lon){runOnUiThread(()->{stopCompass();if(rotation==null)return;declination=new GeomagneticField((float)lat,(float)lon,0,System.currentTimeMillis()).getDeclination();northReady=true;qiblaOn=true;sensorAccuracy=0;if(!sm.registerListener(compass,rotation,SensorManager.SENSOR_DELAY_UI)){qiblaOn=false;web.evaluateJavascript("qFallback()",null);}});} @JavascriptInterface public void compassStop(){runOnUiThread(()->stopCompass());} @JavascriptInterface public void notifyRitual(String message){runOnUiThread(()->ritualNotification(message));} @JavascriptInterface public void speak(String text){runOnUiThread(()->{if(ready)tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"dua");else android.widget.Toast.makeText(MainActivity.this,"القراءة العربية غير متوفرة على الجهاز",0).show();});} @JavascriptInterface public void stop(){runOnUiThread(()->{if(tts!=null)tts.stop();});} }
 @Override protected void onPause(){super.onPause();stopSteps();stopCompass();if(web!=null){web.evaluateJavascript("if(typeof pauseIntro==='function')pauseIntro();if(typeof pauseScreenGps==='function')pauseScreenGps();if(typeof pauseSmart==='function')pauseSmart();",null);web.onPause();}if(tts!=null)tts.stop();}
 @Override protected void onResume(){super.onResume();if(stepActive)beginSteps();if(web!=null){web.onResume();web.evaluateJavascript("if(window.qResume)qResume();if(window.restoreTracking)restoreTracking()",null);}}
 @Override protected void onDestroy(){stopSteps();if(tts!=null)tts.shutdown();if(web!=null){web.removeJavascriptInterface("OmratiNative");web.destroy();}super.onDestroy();}
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}
