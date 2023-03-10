package com.example.lsnavigation;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.gridlines.LatLonGridlineOverlay2;

public class MainActivity extends AppCompatActivity {
    MapView map = null;
    IMapController mapController = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        Configuration.getInstance()
                .load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        setContentView(R.layout.activity_main);

        map = (MapView) findViewById(R.id.map);

        mapController = map.getController();
        mapController.setZoom(12.0);
        GeoPoint centerPoint = new GeoPoint(35.658531702121714, 139.54329084890188);
        mapController.setCenter(centerPoint);

        map.setTileSource(TileSourceFactory.MAPNIK);

        map.setMultiTouchControls(true);

        LatLonGridlineOverlay2 overlay2 = new LatLonGridlineOverlay2();
        //map.getOverlays().add(overlay2);


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

}