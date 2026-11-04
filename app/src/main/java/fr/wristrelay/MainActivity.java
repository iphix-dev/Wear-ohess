package fr.wristrelay;
import android.app.*; import android.os.*; import android.content.*; import android.bluetooth.*; import android.widget.*; import android.provider.Settings; import android.Manifest; import android.content.pm.PackageManager;
public class MainActivity extends Activity {
 LinearLayout box;
 private boolean batteryRequested;
 @Override protected void onResume() {
     super.onResume();
     if (!BuildConfig.WATCH && !batteryRequested) {
         batteryRequested = true;
         try {
             org.json.JSONObject request = new org.json.JSONObject();
             request.put("op", "battery_request");
             RelayService.send(request);
         } catch (org.json.JSONException e) {
             android.util.Log.e("WristRelay", "Impossible de demander la batterie de la montre", e);
         }
     }
 }
 void button(String t, Runnable r){ Button b=new Button(this); b.setText(t); box.addView(b); b.setOnClickListener(v->{try{r.run();}catch(Exception e){
    android.util.Log.e("WristRelay", "Échec du bouton : " + t, e);
    new AlertDialog.Builder(this)
        .setTitle("Erreur WristRelay")
        .setMessage(android.util.Log.getStackTraceString(e))
        .setPositiveButton("Fermer", null)
        .show();
}}); }
 public void onCreate(Bundle b){super.onCreate(b); ScrollView s=new ScrollView(this); box=new LinearLayout(this);box.setOrientation(1);box.setPadding(24,36,24,36);s.addView(box);setContentView(s);
 TextView title=new TextView(this);title.setText("WristRelay reprise V2 — "+(BuildConfig.WATCH?"MONTRE":"TÉLÉPHONE")+"\nPrototype : garder les deux appareils configurés. Autoriser uniquement votre appareil Bluetooth.");box.addView(title);
 button("1. Autoriser Bluetooth / notifications",()->{ java.util.ArrayList<String> p=new java.util.ArrayList<>();if(Build.VERSION.SDK_INT>=31){p.add(Manifest.permission.BLUETOOTH_CONNECT);p.add(Manifest.permission.BLUETOOTH_ADVERTISE);}if(Build.VERSION.SDK_INT>=33)p.add(Manifest.permission.POST_NOTIFICATIONS);if(!p.isEmpty())requestPermissions(p.toArray(new String[0]),1); });
 
button("2. Ouvrir les réglages Bluetooth",()->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
 if(BuildConfig.WATCH)button("Rendre la montre visible 120 secondes",()->startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,120)));
 button("3. Choisir l'appareil déjà associé",()->choose());
 if(!BuildConfig.WATCH){button("4. Autoriser la lecture des notifications",()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));button("Applications à transmettre",()->filters());}
 button("Démarrer la liaison",()->{if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)throw new IllegalStateException("Autoriser Bluetooth d'abord");if(getSharedPreferences("cfg",0).getString("peer","").isEmpty())throw new IllegalStateException("Choisir un appareil associe avant de demarrer");
if(!getSharedPreferences("cfg",0).edit().putBoolean("relay_enabled",true).commit())throw new IllegalStateException("Impossible de memoriser le demarrage");
try {startForegroundService(new Intent(this,RelayService.class));}
catch(RuntimeException e){getSharedPreferences("cfg",0).edit().putBoolean("relay_enabled",false).commit();throw e;}});
 if(!BuildConfig.WATCH)button("Notification de test",()->{org.json.JSONObject j=new org.json.JSONObject();try{j.put("op","post");j.put("key","test");j.put("app","WristRelay");j.put("title","Test Bluetooth");j.put("text","Message reçu sans Mobvoi ni Internet");j.put("call",false);RelayService.send(j);}catch(Exception ignored){} });
 button("Arrêter la liaison",()->{
if(!getSharedPreferences("cfg",0).edit().putBoolean("relay_enabled",false).commit())throw new IllegalStateException("Impossible de memoriser l'arret");
stopService(new Intent(this,RelayService.class));
});
 }
 void choose(){BluetoothAdapter a=getSystemService(BluetoothManager.class).getAdapter();java.util.ArrayList<BluetoothDevice> ds=new java.util.ArrayList<>(a.getBondedDevices());String[] names=new String[ds.size()];for(int i=0;i<names.length;i++)names[i]=ds.get(i).getName()+" / "+ds.get(i).getAddress();new AlertDialog.Builder(this).setTitle("Autoriser cet appareil seulement").setItems(names,(d,n)->{getSharedPreferences("cfg",0).edit().putString("peer",ds.get(n).getAddress()).apply();Toast.makeText(this,"Appareil enregistré",0).show();}).show();}
 void filters(){java.util.List<android.content.pm.ApplicationInfo> all=getPackageManager().getInstalledApplications(0);java.util.ArrayList<String> pk=new java.util.ArrayList<>(), labels=new java.util.ArrayList<>();for(android.content.pm.ApplicationInfo ai:all){if(getPackageManager().getLaunchIntentForPackage(ai.packageName)!=null){pk.add(ai.packageName);labels.add(ai.loadLabel(getPackageManager())+" ("+ai.packageName+")");}}java.util.Set<String> saved=getSharedPreferences("cfg",0).getStringSet("allow",new java.util.HashSet<>());java.util.Set<String> selected=new java.util.HashSet<>(saved);boolean[] checks=new boolean[pk.size()];for(int i=0;i<checks.length;i++)checks[i]=selected.contains(pk.get(i));new AlertDialog.Builder(this).setTitle("Aucune sélection = rien transmis").setMultiChoiceItems(labels.toArray(new String[0]),checks,(d,i,on)->{if(on)selected.add(pk.get(i));else selected.remove(pk.get(i));}).setPositiveButton("Enregistrer",(d,w)->getSharedPreferences("cfg",0).edit().putStringSet("allow",selected).apply()).setNegativeButton("Annuler",null).show();}
}
