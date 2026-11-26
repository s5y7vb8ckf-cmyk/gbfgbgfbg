package sa.omrati.app;
import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.webkit.*;
import androidx.webkit.WebViewAssetLoader;
import java.util.Locale;

public class MainActivity extends Activity {
 private WebView web; private TextToSpeech tts; private boolean ready; private GeolocationPermissions.Callback pending; private String origin;
 private static final String HOST="appassets.androidplatform.net";
 @Override public void onCreate(Bundle b){super.onCreate(b);
  tts=new TextToSpeech(this,status->{if(status==TextToSpeech.SUCCESS){int r=tts.setLanguage(new Locale("ar","SA"));ready=r>=0;tts.setSpeechRate(.78f);}});
  web=new WebView(this);setContentView(web);
  WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setGeolocationEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setSupportMultipleWindows(false);
  WebViewAssetLoader loader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
  web.addJavascriptInterface(new Voice(),"OmratiNative");
  web.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return loader.shouldInterceptRequest(r.getUrl());}
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){Uri u=r.getUrl();if("https".equals(u.getScheme())&&HOST.equals(u.getHost()))return false;if("https".equals(u.getScheme()))try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}return true;}
  });
  web.setWebChromeClient(new WebChromeClient(){@Override public void onGeolocationPermissionsShowPrompt(String o,GeolocationPermissions.Callback c){if(!("https://"+HOST).equals(o)){c.invoke(o,false,false);return;}if(hasLocation()){c.invoke(o,true,false);return;}pending=c;origin=o;requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},7);}});
  web.loadUrl("https://"+HOST+"/assets/index.html");
 }
 private boolean hasLocation(){return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==7&&pending!=null){pending.invoke(origin,hasLocation(),false);pending=null;}}
 public class Voice { @JavascriptInterface public void speak(String text){runOnUiThread(()->{if(ready)tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"dua");else android.widget.Toast.makeText(MainActivity.this,"القراءة العربية غير متوفرة على الجهاز",0).show();});} @JavascriptInterface public void stop(){runOnUiThread(()->{if(tts!=null)tts.stop();});} }
 @Override protected void onPause(){super.onPause();if(web!=null){web.evaluateJavascript("if(typeof stopGps==='function')stopGps();if(typeof pauseSmart==='function')pauseSmart();",null);web.onPause();}if(tts!=null)tts.stop();}
 @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
 @Override protected void onDestroy(){if(tts!=null)tts.shutdown();if(web!=null){web.removeJavascriptInterface("OmratiNative");web.destroy();}super.onDestroy();}
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}
