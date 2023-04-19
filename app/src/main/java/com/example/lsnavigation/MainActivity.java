package com.example.lsnavigation;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

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
        GeoPoint kohnan = new GeoPoint(35.682267446330904, 139.54380069251135);
        marker.setPosition(kohnan);
        map.getOverlays().add(marker);
        marker.setTitle("働けカス");
        Drawable icon = getDrawable(R.drawable.white);
        marker.setIcon(icon);
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

    protected void findViews(){
        map = (MapView) findViewById(R.id.map);
    }

}