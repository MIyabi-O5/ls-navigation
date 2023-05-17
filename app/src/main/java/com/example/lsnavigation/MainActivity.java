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
import android.widget.Button;
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
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    MapView map;
    IMapController mapController;
    Button connectButton;

    protected void findViews(){
        map = (MapView) findViewById(R.id.map);
        connectButton = (Button) findViewById(R.id.connectButton);
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
        GeoPoint centerPoint = new GeoPoint(35.658531702121714, 139.54329084890188);
        mapController.setCenter(centerPoint);
        map.setMultiTouchControls(true);
        mapController.setZoom(16.0);
        Marker marker = new Marker(map);
        GeoPoint white = new GeoPoint(35.682267446330904, 139.54380069251135);
        marker.setPosition(white);
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
            cadenceMonitor.setText(msg);
        }
    };

}