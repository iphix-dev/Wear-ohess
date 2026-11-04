package fr.wristrelay;
import android.app.*;import android.content.*;import android.content.pm.ServiceInfo;import android.bluetooth.*;import android.os.*;import java.io.*;import java.util.*;import java.util.concurrent.*;import org.json.*;
public class RelayService extends Service {
 static final UUID ID=UUID.fromString("96bf751e-9529-4f99-9c1e-f952fedc4091");
 static volatile RelayService instance;
 volatile boolean running;volatile BluetoothSocket socket;volatile BluetoothServerSocket server;
 final ArrayBlockingQueue<String> queue=new ArrayBlockingQueue<>(64);
 final ExecutorService threads=Executors.newFixedThreadPool(2);
 final Map<String,Integer> ids=new HashMap<>();int nextId=100;
 NotificationManager nm;Thread link;
 public IBinder onBind(Intent i){return null;}
 public void onCreate(){super.onCreate();nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("status","État de liaison",NotificationManager.IMPORTANCE_LOW));NotificationChannel c=new NotificationChannel("messages","Messages reçus",NotificationManager.IMPORTANCE_DEFAULT);c.enableVibration(true);nm.createNotificationChannel(c);NotificationChannel calls=new NotificationChannel("calls","Alertes d'appel",NotificationManager.IMPORTANCE_HIGH);calls.enableVibration(true);nm.createNotificationChannel(calls);
 android.util.Log.i("WristRelay", "WR_RECOVERY_V2 onCreate pid=" + android.os.Process.myPid());
 if(getSharedPreferences("cfg",0).getBoolean("relay_enabled",false)) {
     ensureStarted("onCreate");
 }
}
 Notification status(String text){PendingIntent p=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return new Notification.Builder(this,"status").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("WristRelay actif").setContentText(text).setContentIntent(p).setOngoing(true).build();}
 public int onStartCommand(Intent intent,int flags,int startId){
 android.util.Log.i("WristRelay", "WR_RECOVERY_V2 onStartCommand id="+startId+", nullIntent="+(intent==null));
 if(!getSharedPreferences("cfg",0).getBoolean("relay_enabled",false)) {
     stopSelf(); return START_NOT_STICKY;
 }
 return ensureStarted("onStartCommand") ? START_STICKY : START_NOT_STICKY;
 }

 private synchronized boolean ensureStarted(String origin) {
     if(running && link!=null && link.isAlive()) {
         android.util.Log.i("WristRelay", "WR_RECOVERY_V2 deja actif depuis "+origin);
         return true;
     }
     if(running) {
         android.util.Log.e("WristRelay", "WR_RECOVERY_V2 thread liaison termine; arret pour eviter un faux etat actif");
         stopSelf(); return false;
     }
     String peer=getSharedPreferences("cfg",0).getString("peer","");
     if(peer.isEmpty()) {
         android.util.Log.w("WristRelay", "WR_RECOVERY_V2 appareil non selectionne");
         stopSelf(); return false;
     }
     try {
         if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
             throw new SecurityException("BLUETOOTH_CONNECT non accordee");
         }
         Notification n=status("Reprise de liaison : "+origin);
         startForeground(1,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
         running=true;
         instance=this;
         threads.execute(()->writer());
         link=new Thread(()->{
             try {connectLoop(peer);}
             finally {android.util.Log.w("WristRelay","WR_RECOVERY_V2 fin du thread Bluetooth");}
         },"relay-link");
         link.start();
         android.util.Log.i("WristRelay","WR_RECOVERY_V2 boucle lancee depuis "+origin);
         return true;
     } catch(Exception e) {
         android.util.Log.e("WristRelay","WR_RECOVERY_V2 echec depuis "+origin,e);
         running=false;
         if(instance==this)instance=null;
         stopSelf();
         return false;
     }
 }
 static void send(JSONObject j){RelayService s=instance;if(s==null||!s.running||s.socket==null)return;String line=j.toString();if(!s.queue.offer(line)){s.queue.poll();s.queue.offer(line);}}
 void writer(){while(running){try{String line=queue.take();BluetoothSocket s=socket;if(s!=null){DataOutputStream out=new DataOutputStream(s.getOutputStream());byte[] b=line.getBytes("UTF-8");out.writeInt(b.length);out.write(b);out.flush();}}catch(InterruptedException e){return;}catch(Exception e){android.util.Log.w("WristRelay","Erreur emission Bluetooth",e);closeSocket();}}}
 void connectLoop(String peer){long delay=5000;while(running){try{BluetoothAdapter a=getSystemService(BluetoothManager.class).getAdapter();if(a==null||!a.isEnabled())throw new IOException("Bluetooth désactivé");BluetoothSocket s;
 if(BuildConfig.WATCH){server=a.listenUsingRfcommWithServiceRecord("WristRelay",ID);try{s=server.accept();}finally{server.close();server=null;}if(!peer.equals(s.getRemoteDevice().getAddress())){s.close();continue;}}
 else{s=a.getRemoteDevice(peer).createRfcommSocketToServiceRecord(ID);
socket=s;
android.util.Log.i("WristRelay","Tentative Bluetooth vers " + peer);
s.connect();
android.util.Log.i("WristRelay","Connexion Bluetooth établie vers " + peer);
}
 socket=s;queue.clear();delay=5000;nm.notify(1,status("Connecté — aucun ping périodique"));DataInputStream in=new DataInputStream(s.getInputStream());while(running){int size=in.readInt();if(size<1||size>16384)throw new IOException("Trame invalide");byte[] b=new byte[size];in.readFully(b);// WR_CALL_ACTIONS_V1
JSONObject packet = new JSONObject(new String(b,"UTF-8"));
if(BuildConfig.WATCH) receive(packet);
else if ("battery".equals(packet.optString("op"))) {
BatteryHistory.record(this, packet.optInt("level", -1));
} else CallActions.execute(this, packet);}
 }catch(Exception e){if(running){nm.notify(1,status("Déconnecté : "+e.getClass().getSimpleName()));android.util.Log.w("WristRelay","Liaison interrompue",e);}}finally{closeSocket();queue.clear();}if(running)try{Thread.sleep(delay);delay=Math.min(delay*2,300000);}catch(InterruptedException e){return;}}
 }
 void receive(JSONObject j)throws Exception{
if ("battery_request".equals(j.optString("op"))) {
    BatteryManager battery = (BatteryManager)getSystemService(BATTERY_SERVICE);
    int level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    JSONObject response = new JSONObject();
    response.put("op", "battery");
    response.put("level", level);
    send(response);
    return;
}
String key=j.optString("key");if("remove".equals(j.optString("op"))){Integer id=ids.remove(key);if(id!=null)nm.cancel(id);return;}if(!"post".equals(j.optString("op")))return;if(!ids.containsKey(key)){if(ids.size()>=100){String oldest=ids.keySet().iterator().next();nm.cancel(ids.remove(oldest));}ids.put(key,nextId++);}boolean call=j.optBoolean("call");String title=j.optString("title"),text=j.optString("text");Notification.Builder b=new Notification.Builder(this,call?"calls":"messages").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(j.optString("app")+" — "+title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setOnlyAlertOnce(true).setAutoCancel(true).setLocalOnly(true).setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(call?Notification.CATEGORY_CALL:Notification.CATEGORY_MESSAGE);if(call)CallActions.addButtons(this,b,j);nm.notify(ids.get(key),b.build());}
 synchronized void closeSocket(){BluetoothSocket s=socket;socket=null;if(s!=null)try{s.close();}catch(Exception ignored){}}
 public void onDestroy(){android.util.Log.w("WristRelay","onDestroy : service détruit");running=false;instance=null;closeSocket();if(server!=null)try{server.close();}catch(Exception ignored){}if(link!=null)link.interrupt();threads.shutdownNow();queue.clear();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}

 // WR_RECOVERY_V2
 @Override public void onTaskRemoved(Intent rootIntent) {
     android.util.Log.w("WristRelay", "WR_RECOVERY_V2 onTaskRemoved running="+running
         +", linkAlive="+(link!=null && link.isAlive())
         +", socketPresente="+(socket!=null));
     if(getSharedPreferences("cfg",0).getBoolean("relay_enabled",false)) {
         ensureStarted("onTaskRemoved");
     }
     super.onTaskRemoved(rootIntent);
 }
}
