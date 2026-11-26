package sa.omrati.app;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.location.Location;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** One explicitly paired companion, encrypted Bluetooth Classic link. No server. */
final class CompanionLink {
 private final MainActivity activity;
 private final UUID uuid=UUID.fromString("7072f1d9-60df-4d29-bc61-293d675ff5a1");
 private final AtomicInteger generation=new AtomicInteger();
 private volatile BluetoothSocket socket;
 private volatile BluetoothServerSocket server;
 private final java.util.concurrent.atomic.AtomicBoolean ringRequest=new java.util.concurrent.atomic.AtomicBoolean();
 private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
 private android.media.Ringtone ringtone; private long lastRing=0; private volatile String ringStatus="";
 private volatile String status="غير متصل", peer="", remote="";
 CompanionLink(MainActivity a){activity=a;}
 private BluetoothAdapter adapter(){BluetoothManager m=(BluetoothManager)activity.getSystemService(Context.BLUETOOTH_SERVICE);return m==null?null:m.getAdapter();}
 void open(){if(Build.VERSION.SDK_INT>=31&&activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){activity.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},15);return;}openAfterPermission();}
 void openAfterPermission(){try{if(Build.VERSION.SDK_INT>=31&&activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){status="إذن البلوتوث مطلوب";return;}BluetoothAdapter a=adapter();if(a==null){status="البلوتوث غير مدعوم";return;}if(!a.isEnabled()){activity.startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));status="فعّل البلوتوث ثم أعد المحاولة";return;}new AlertDialog.Builder(activity).setTitle("مشاركة الموقع مع مرافق").setMessage("اقترنا أولاً من إعدادات البلوتوث. اختر الانتظار في أحد الهاتفين والاتصال في الآخر. سيشارك الجهازان موقعهما أثناء الاتصال فقط.").setPositiveButton("اختيار مرافق",(d,w)->choose(a)).setNeutralButton("انتظار اتصال",(d,w)->chooseHost(a)).setNegativeButton("إلغاء",null).show();}catch(Exception e){status="تعذر فتح البلوتوث";}}
 private void chooseHost(BluetoothAdapter a){select(a,true);}
 private void choose(BluetoothAdapter a){select(a,false);}
 private void select(BluetoothAdapter a,boolean host){try{ArrayList<BluetoothDevice> list=new ArrayList<>(a.getBondedDevices());if(list.isEmpty()){status="لا توجد أجهزة مقترنة";activity.startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));return;}String[] names=new String[list.size()];for(int i=0;i<names.length;i++)names[i]=list.get(i).getName()+" • "+list.get(i).getAddress();new AlertDialog.Builder(activity).setTitle("اختر الجهاز الموثوق").setItems(names,(d,w)->start(a,list.get(w),host)).setNegativeButton("إلغاء",null).show();}catch(Exception e){status="تعذر قراءة الأجهزة";}}
 private synchronized void start(BluetoothAdapter a,BluetoothDevice device,boolean host){stop();int g=generation.get();status=host?"بانتظار المرافق":"جارٍ الاتصال";peer=device.getName();new Thread(()->{BluetoothSocket local=null;BluetoothServerSocket listener=null;try{if(host){listener=a.listenUsingRfcommWithServiceRecord("Omrati",uuid);synchronized(this){if(g!=generation.get()){listener.close();return;}server=listener;}while(g==generation.get()){local=listener.accept();if(device.getAddress().equals(local.getRemoteDevice().getAddress()))break;local.close();local=null;}listener.close();}else{local=device.createRfcommSocketToServiceRecord(uuid);synchronized(this){if(g!=generation.get()){local.close();return;}socket=local;}local.connect();}if(local==null)return;synchronized(this){if(g!=generation.get()){local.close();return;}socket=local;status="متصل";}final BluetoothSocket connected=local;Thread sender=new Thread(()->send(connected,g));sender.start();DataInputStream in=new DataInputStream(local.getInputStream());while(g==generation.get()){String msg=in.readUTF();if(msg.length()>300)throw new IOException();if(msg.equals("RING1")){if(g==generation.get())handler.post(()->{if(g==generation.get())playRing();});continue;} String valid=validFix(msg);if(g==generation.get())remote=valid;}}catch(Exception e){if(g==generation.get())status="انقطع الاتصال؛ أعد الربط";}finally{try{if(local!=null)local.close();}catch(Exception ignored){}try{if(listener!=null)listener.close();}catch(Exception ignored){}if(g==generation.get()){remote="";if(status.equals("متصل"))status="انقطع الاتصال";}}}).start();}
 private String localFix(){return validFix(activity.getSharedPreferences(PrayerService.PREF,Context.MODE_PRIVATE).getString("fix",""));}
 private static String validFix(String raw){try{String[] v=raw.split(",");if(v.length!=4)return "";double lat=Double.parseDouble(v[0]),lon=Double.parseDouble(v[1]),acc=Double.parseDouble(v[2]);long t=Long.parseLong(v[3]);if(!Double.isFinite(lat)||!Double.isFinite(lon)||!Double.isFinite(acc)||Math.abs(lat)>90||Math.abs(lon)>180||acc<0||Math.abs(System.currentTimeMillis()-t)>60000)return "";return raw;}catch(Exception e){return "";}}
 private void send(BluetoothSocket s,int g){try{DataOutputStream out=new DataOutputStream(s.getOutputStream());while(g==generation.get()){out.writeUTF(localFix());if(ringRequest.getAndSet(false)){out.writeUTF("RING1");ringStatus="أُرسل طلب التنبيه";}out.flush();Thread.sleep(1000);}}catch(Exception e){try{s.close();}catch(Exception ignored){}}}
 void requestRing(){if(status.equals("متصل")){ringRequest.set(true);ringStatus="جارٍ إرسال التنبيه";}else ringStatus="المرافق غير متصل";}
 private void playRing(){long now=android.os.SystemClock.elapsedRealtime();if(lastRing!=0&&now-lastRing<20000)return;lastRing=now;stopRing();try{ringtone=android.media.RingtoneManager.getRingtone(activity,android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE));if(ringtone!=null){ringtone.play();handler.postDelayed(this::stopRing,15000);} android.os.Vibrator v=(android.os.Vibrator)activity.getSystemService(Context.VIBRATOR_SERVICE);if(v!=null)v.vibrate(800);ringStatus="طلب مرافقك تنبيه هاتفك";}catch(Exception e){ringStatus="تعذر تشغيل الصوت";}}
 void stopRing(){if(ringtone!=null){ringtone.stop();ringtone=null;}}
 synchronized void stop(){generation.incrementAndGet();ringRequest.set(false);ringStatus="";handler.post(this::stopRing);try{if(socket!=null)socket.close();}catch(Exception ignored){}try{if(server!=null)server.close();}catch(Exception ignored){}socket=null;server=null;remote="";peer="";status="غير متصل";}
 String state(){try{JSONObject j=new JSONObject();j.put("status",status);j.put("peer",peer);j.put("ringStatus",ringStatus);j.put("local",localFix());j.put("remote",validFix(remote));return j.toString();}catch(Exception e){return "{}";}}
}
