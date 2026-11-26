package sa.omrati.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import java.util.*;

/** User-started foreground location service. All calculations stay on device. */
public class PrayerService extends Service implements LocationListener {
 static final String PREF="prayer_tracking";
 static final String[] NAMES={"الفجر","الظهر","العصر","المغرب","العشاء"};
 private final Handler handler=new Handler(Looper.getMainLooper());
 private LocationManager manager; private Location last;
 private android.content.SharedPreferences prefs;
 private final Runnable tick=new Runnable(){public void run(){check();handler.postDelayed(this,15000);}};
 @Override public IBinder onBind(Intent i){return null;}
 @Override public void onCreate(){super.onCreate();prefs=getSharedPreferences(PREF,MODE_PRIVATE);manager=(LocationManager)getSystemService(LOCATION_SERVICE);}
 private Notification.Builder builder(String channel){return Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this);}
 private PendingIntent open(){return PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 @Override public int onStartCommand(Intent i,int flags,int id){
  if(i!=null&&"stop".equals(i.getAction())){prefs.edit().putBoolean("enabled",false).apply();stopSelf();return START_NOT_STICKY;}
  if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){prefs.edit().putBoolean("enabled",false).apply();stopSelf();return START_NOT_STICKY;}
  NotificationManager nm=getSystemService(NotificationManager.class);
  if(Build.VERSION.SDK_INT>=26){nm.createNotificationChannel(new NotificationChannel("location_active","متابعة الموقع",NotificationManager.IMPORTANCE_LOW));nm.createNotificationChannel(new NotificationChannel("prayer_times","الصلاة والأذان والإقامة",NotificationManager.IMPORTANCE_HIGH));}
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,PrayerService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  try {startForeground(51,builder("location_active").setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("عُمرتي • متابعة الموقع").setContentText("حساب مواقيت الصلاة محليًا").setOngoing(true).setContentIntent(open()).addAction(android.R.drawable.ic_menu_close_clear_cancel,"إيقاف",stop).build());}catch(Exception e){prefs.edit().putBoolean("enabled",false).apply();stopSelf();return START_NOT_STICKY;}
  prefs.edit().putBoolean("enabled",true).apply();
  manager.removeUpdates(this);
  for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER})try{if(manager.isProviderEnabled(provider)){Location l=manager.getLastKnownLocation(provider);if(l!=null&&System.currentTimeMillis()-l.getTime()<300000&&(last==null||l.getTime()>last.getTime()))last=l;manager.requestLocationUpdates(provider,30000,20,this);}}catch(Exception ignored){}
  handler.removeCallbacks(tick);handler.post(tick);return START_STICKY;
 }
 @Override public void onLocationChanged(Location l){last=l;check();}
 @Override public void onProviderEnabled(String p){}
 @Override public void onProviderDisabled(String p){}
 @Override public void onStatusChanged(String p,int s,Bundle b){}
 static double rad(double d){return Math.toRadians(d);}
 static double angle(double lat,double dec,double alt){double x=(Math.sin(rad(alt))-Math.sin(rad(lat))*Math.sin(dec))/(Math.cos(rad(lat))*Math.cos(dec));return x < -1||x > 1?Double.NaN:Math.toDegrees(Math.acos(x))*4;}
 static double[] times(Calendar c,double lat,double lon){
  int n=c.get(Calendar.DAY_OF_YEAR); double g=2*Math.PI/365*(n-1);
  double eq=229.18*(.000075+.001868*Math.cos(g)-.032077*Math.sin(g)-.014615*Math.cos(2*g)-.040849*Math.sin(2*g));
  double dec=.006918-.399912*Math.cos(g)+.070257*Math.sin(g)-.006758*Math.cos(2*g)+.000907*Math.sin(2*g)-.002697*Math.cos(3*g)+.00148*Math.sin(3*g);
  double noon=720-4*lon-eq+c.getTimeZone().getOffset(c.getTimeInMillis())/60000.0;
  double set=noon+angle(lat,dec,-.833);double asrAlt=Math.toDegrees(Math.atan(1/(1+Math.tan(Math.abs(rad(lat)-dec)))));
  android.icu.util.IslamicCalendar hijri=new android.icu.util.IslamicCalendar();hijri.setCalculationType(android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);hijri.setTimeInMillis(c.getTimeInMillis());
  return new double[]{noon-angle(lat,dec,-18.5),noon+1,noon+angle(lat,dec,asrAlt),set,set+(hijri.get(android.icu.util.Calendar.MONTH)==8?120:90)};
 }
 private void check(){if(last==null||System.currentTimeMillis()-last.getTime()>6*3600000L)return;Calendar now=Calendar.getInstance();double[] t=times(now,last.getLatitude(),last.getLongitude());int minute=now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE);String day=now.get(Calendar.YEAR)+"-"+now.get(Calendar.DAY_OF_YEAR);StringBuilder schedule=new StringBuilder();
  for(int j=0;j<5;j++){if(Double.isNaN(t[j]))continue;int m=(int)Math.round(t[j]);if(m<0||m>=1440)continue;schedule.append(NAMES[j]).append(" ").append(String.format(Locale.US,"%02d:%02d",m/60,m%60)).append("\n");if(prefs.getBoolean("adhan",true)&&minute==m)sendOnce(day+"a"+j, "حان وقت صلاة "+NAMES[j],"وقت الأذان التقديري بحسب موقعك",60+j);int delay=prefs.getInt("iqama"+j,0);if(delay>0&&minute==m+delay)sendOnce(day+"i"+j,"تذكير بالإقامة • "+NAMES[j],"موعد تقديري حسب المدة التي ضبطتها، تحقق من المسجد",70+j);}
  prefs.edit().putString("schedule",schedule.toString()).putLong("updated",System.currentTimeMillis()).apply();
 }
 private void sendOnce(String key,String title,String text,int id){if(prefs.getString("sent"+id,"").equals(key))return;if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;NotificationManager nm=getSystemService(NotificationManager.class);if(!nm.areNotificationsEnabled())return;nm.notify(id,builder("prayer_times").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setDefaults(Notification.DEFAULT_ALL).setContentIntent(open()).setAutoCancel(true).build());prefs.edit().putString("sent"+id,key).apply();}
 @Override public void onDestroy(){handler.removeCallbacks(tick);if(manager!=null)manager.removeUpdates(this);stopForeground(true);super.onDestroy();}
}
