package com.example.lsnavigation;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.ArrayList;
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
    RelativeLayout fragmentLayout;
    ImageView imageBlack;
    TextView cadenceMonitor;

    SeekBar seekBar;

    public int offsetValue = 500;

    private SpeechRecognizer speechRecognizer;
    private final static String TAG = "SPEECH_RECOGNIZER";

    // Activityとserviceの通信

    Messenger messenger = null;
    boolean bound;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            messenger = new Messenger(iBinder);
            bound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            messenger = null;
            bound = false;
        }
    };


    protected void findViews(){
        map = (MapView) findViewById(R.id.map);
        connectButton = (Button) findViewById(R.id.connectButton);
        fragmentLayout = (RelativeLayout) findViewById(R.id.sensorFragment);
        imageBlack = (ImageView) findViewById(R.id.imageBlack);
        seekBar = (SeekBar) findViewById(R.id.seekBar);
        cadenceMonitor = (TextView) findViewById(R.id.cadenceMonitor);
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

        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        Intent speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 2);

        seekBar.setProgress(offsetValue);
        seekBar.setVisibility(View.GONE);


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
        Drawable icon = getDrawable(R.drawable.white);
        marker.setIcon(icon);


        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(SerialService.ACTION);

        // Receiverの登録、manifestにも追記する必要があるReceiverも存在する
        registerReceiver(receiver, intentFilter);

        Intent intent = new Intent(this, SerialService.class);
        connectButton.setOnClickListener(view -> {
            speechRecognizer.startListening(speechRecognizerIntent);
            startService(intent);
            bindService(new Intent(this, SerialService.class), connection, Context.BIND_AUTO_CREATE);
            // buttonを押したら邪魔なので見えなくする
            connectButton.setVisibility(View.GONE);
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                offsetValue = i;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // くすぐったいよー
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.setVisibility(View.GONE) ;
            }
        });


        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle bundle) {
                Log.i(TAG, "onReadyForSpeech");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.i(TAG, "onBeginningOfSpeech");

            }

            @Override
            public void onRmsChanged(float v) {
                //Log.i(TAG, "onRmsChanged");
            }

            @Override
            public void onBufferReceived(byte[] bytes) {
                Log.i(TAG, "onBufferReceived");
            }

            @Override
            public void onEndOfSpeech() {
                Log.i(TAG, "onEndOfSpeech");
            }

            @Override
            public void onError(int i) {
                switch (i) {
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        Log.d(TAG, "ネットワークタイムエラー");
                        break;
                    case SpeechRecognizer.ERROR_NETWORK:
                        Log.d(TAG, "その外ネットワークエラー");
                        break;
                    case SpeechRecognizer.ERROR_AUDIO:
                        Log.d(TAG, "Audio エラー");
                        break;
                    case SpeechRecognizer.ERROR_SERVER:
                        Log.d(TAG, "サーバーエラー");
                        break;
                    case SpeechRecognizer.ERROR_CLIENT:
                        Log.d(TAG, "クライアントエラー");
                        break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        Log.d(TAG, "何も聞こえてないエラー");
                        break;
                    case SpeechRecognizer.ERROR_NO_MATCH:
                        Log.d(TAG, "適当な結果を見つけてませんエラー");
                        break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        Log.d(TAG, "RecognitionServiceが忙しいエラー");
                        break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                        Log.d(TAG, "RECORD AUDIOがないエラー");
                        break;
                }
                speechRecognizer.startListening(speechRecognizerIntent);
            }

            @Override
            public void onResults(Bundle results) {
                Log.i(TAG, "onResults");
                Message msg = null;
                ArrayList<String> data = results.getStringArrayList(speechRecognizer.RESULTS_RECOGNITION);
                cadenceMonitor.setText(data.get(0));
                if(!bound){
                    speechRecognizer.startListening(speechRecognizerIntent);
                    return;
                }
                if(data.get(0).equals("ブラック距離")){
                    msg = Message.obtain(null, SerialService.DISTANCE_1, 0, 0);
                }else {
                    speechRecognizer.startListening(speechRecognizerIntent);
                    return;
                }

                try {
                    messenger.send(msg);
                }catch (RemoteException e){
                    e.printStackTrace();
                }
                speechRecognizer.startListening(speechRecognizerIntent);
            }

            @Override
            public void onPartialResults(Bundle bundle) {
                Log.i(TAG, "onPartialResults");
            }

            @Override
            public void onEvent(int i, Bundle bundle) {
                Log.i(TAG, "onEvent");
            }
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

            String msg = intent.getStringExtra("message");
            /*
             msgの構造
             data,lat,lon,heading,height,ctl\n
             これらのデータを配列に変換する
             */
            String[] data = msg.split(",");

            double latitude = Double.parseDouble(data[1]) / 10000000;
            double longitude = Double.parseDouble(data[2]) / 10000000;
            int heading = (int)Double.parseDouble(data[3]) / 100000;
            // ブラックの回転
            imageBlack.setRotation(heading);

            centerPoint = new GeoPoint(latitude, longitude);
            mapController.setCenter(centerPoint);
            // pylonPointまでの距離(m)
            int distancePylon = (int)calcDistance(pylonPointLat, pylonPointLon, latitude, longitude);
            // homePointまでの距離(m)
            int distanceHome = 18000 - distancePylon;
            Log.i("debugGPS", "distanceHome" + String.valueOf(distanceHome));
            Log.i("debugGPS", "distancePylon" + String.valueOf(distancePylon));

            // 操舵計表示
            int deg = Integer.parseInt(data[5]) - offsetValue;
            //int cad = Integer.parseInt(data[6]);
            cadenceMonitor.setText(String.valueOf(deg) + "\n" + data[6]);
            Log.i("debugCadence", "deg" + String.valueOf(deg));


            // 高度の色表示
            int height = Integer.parseInt(data[4]);
            if(height < 2000 || height > 5000) {
                fragmentLayout.setBackgroundColor(getResources().getColor(R.color.red, getTheme()));
            } else {
                fragmentLayout.setBackgroundColor(getResources().getColor(R.color.blue, getTheme()));
            }
            Log.i("debugHeight", "height" + String.valueOf(height));

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

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent){
        seekBar.setVisibility(View.VISIBLE);
        return true;
    }

}