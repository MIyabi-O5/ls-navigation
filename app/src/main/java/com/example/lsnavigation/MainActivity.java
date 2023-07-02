package com.example.lsnavigation;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.felhr.usbserial.UsbSerialDevice;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.modules.IArchiveFile;
import org.osmdroid.tileprovider.modules.OfflineTileProvider;
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.gridlines.LatLonGridlineOverlay2;

import java.io.File;
import java.util.Arrays;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    MapView map;
    IMapController mapController;

    public static final double homePointLat = 35.658531702121714;   //　debug用に電通大
    public static final double homePointLon = 139.54329084890188;   //　debug用に電通大
    GeoPoint centerPoint;   // 現在値
    GeoPoint homePoint = new GeoPoint(homePointLat, homePointLon);     // プラットホーム座標;

    public static final double pylonPointLat = 35.6555431;
    public static final double pylonPointLon = 139.5437312;

    GeoPoint pylonPoint = new GeoPoint(pylonPointLat, pylonPointLon);    // パイロン座標

    //世界観測値系
    public static final double GRS80_A = 6378137.000;//長半径 a(m)
    public static final double GRS80_E2 = 0.00669438002301188;//第一遠心率  eの2乗

    Button connectButton;
    LinearLayout fragmentLayout;
    ImageView imageBlack;


    protected void findViews(){
        map = (MapView) findViewById(R.id.map);
        connectButton = (Button) findViewById(R.id.connectButton);
        fragmentLayout = (LinearLayout) findViewById(R.id.sensorFragment);
        imageBlack = (ImageView) findViewById(R.id.imageBlack);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        Configuration.getInstance()
                .load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        setContentView(R.layout.activity_main);

        // 初期化
        findViews();

        /*
        map.setUseDataConnection(false);
        map.setTileSource(new XYTileSource(
                "/sdcard/osmdroid/offlineMap.zip",
                16,
                16,
                256,
                ".png",
                new String[]{}
        ));
         */

        mapController = map.getController();
        map.setTileSource(TileSourceFactory.MAPNIK);
        centerPoint = new GeoPoint(35.658531702121714, 139.54329084890188);


        mapController.setCenter(centerPoint);
        map.setMultiTouchControls(true);
        mapController.setZoom(16.0);

        // パイロン座標の表示
        Marker marker = new Marker(map);
        marker.setPosition(pylonPoint);
        map.getOverlays().add(marker);
        marker.setTitle("働けカス");
        Drawable icon = getDrawable(R.drawable.white);
        marker.setIcon(icon);


        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(SerialService.ACTION);

        // Receiverの登録、manifestにも追記する必要があるReceiverも存在する
        registerReceiver(receiver, intentFilter);

        Intent intent = new Intent(this, SerialService.class);
        connectButton.setOnClickListener(view -> {
            startService(intent);
            // buttonを押したら邪魔なので見えなくする
            connectButton.setVisibility(View.GONE);
        });

    }

    public void onResume(){
        super.onResume();
        if(map != null){
            map.onResume();
        }
    }

    public void onPause(){
        super.onPause();
        if(map != null){
            map.onPause();
        }
    }

    // UIの更新のためにReceiverをMainActivityに記述する
    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            TextView cadenceMonitor = (TextView) findViewById(R.id.cadenceMonitor);
            String msg = intent.getStringExtra("message");
            // msgのなかにGPS, Cadence, heightの文字列処理を行う
            if (msg.contains("gps")){
                String[] gpsArray = msg.split(",");

                double latitude = Double.parseDouble(gpsArray[1]) / 10000000;
                double longitude = Double.parseDouble(gpsArray[2]) / 10000000;
                int heading = (int)Double.parseDouble(gpsArray[5]) / 100000;
                // ブラックの回転
                imageBlack.setRotation(heading);

                centerPoint = new GeoPoint(latitude, longitude);
                mapController.setCenter(centerPoint);
                // pylonPointまでの距離(m)
                int distancePylon = (int)calcDistance(pylonPointLat, pylonPointLon, latitude, longitude);
                // homePointまでの距離(m)
                int distanceHome = 18000 - distancePylon;
                cadenceMonitor.setText(String.valueOf(distancePylon));
                Log.i("debugGPS", "distanceHome" + String.valueOf(distanceHome));
                Log.i("debugGPS", "distancePylon" + String.valueOf(distancePylon));
            } else if (msg.contains("cadence")) {
                String[] cadenceArray = msg.split(",");
                cadenceMonitor.setText(cadenceArray[1]);
                Log.i("debugCadence", "cadence" + String.valueOf(cadenceArray[1]));
            } else if (msg.contains("height")) {
                String[] altimeterArray = msg.split(",");
                int height = Integer.parseInt(altimeterArray[2]);
                if(height < 2000 || height > 5000) {
                    fragmentLayout.setBackgroundColor(getResources().getColor(R.color.red, getTheme()));
                } else {
                    fragmentLayout.setBackgroundColor(getResources().getColor(R.color.blue, getTheme()));
                }
                Log.i("debugHeight", Arrays.toString(altimeterArray));
            }

            //cadenceMonitor.setText(msg);

        }
    };

    public static double deg2rad(double deg){
        return deg * Math.PI / 180.0;
    }

    public static double calcDistance(double lat1, double lng1, double lat2, double lng2){
        double my = deg2rad((lat1 + lat2) / 2.0); //緯度の平均値
        double dy = deg2rad(lat1 - lat2); //緯度の差
        double dx = deg2rad(lng1 - lng2); //経度の差

        //卯酉線曲率半径を求める(東と西を結ぶ線の半径)
        double sinMy = Math.sin(my);
        double w = Math.sqrt(1.0 - GRS80_E2 * sinMy * sinMy);
        double n = GRS80_A / w;

        //子午線曲線半径を求める(北と南を結ぶ線の半径)
        double mnum = GRS80_A * (1 - GRS80_E2);
        double m = mnum / (w * w * w);

        //ヒュベニの公式
        double dym = dy * m;
        double dxncos = dx * n * Math.cos(my);
        return Math.sqrt(dym * dym + dxncos * dxncos);
    }


}