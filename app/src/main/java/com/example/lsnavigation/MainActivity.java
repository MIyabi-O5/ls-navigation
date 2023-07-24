package com.example.lsnavigation;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
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
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    MapView map;
    IMapController mapController;

    // debug用電通大
    public static final double homePointLat = 35.658531702121714;
    public static final double homePointLon = 139.54329084890188;
    // -----------

    // debug用尾根幹
    public static final double pylonPointLat = 35.5991479;
    public static final double pylonPointLon = 139.3816600;
    //public static final double pylonPointLat = 35.6555431;
    //public static final double pylonPointLon = 139.5437312;
    // ------------
    GeoPoint centerPoint;   // 現在値
    GeoPoint homePoint = new GeoPoint(homePointLat, homePointLon);     // プラットホーム座標;
    GeoPoint pylonPoint = new GeoPoint(pylonPointLat, pylonPointLon);    // パイロン座標

    public static int distanceHome = 10;
    public static int distancePylon = 10;
    public static int groundSpeed = 0;
    public static int cadence = 0;
    public static int power = 0;
    public static int height = 0;

    public static int obtainObj;

    //世界観測値系
    public static final double GRS80_A = 6378137.000;//長半径 a(m)
    public static final double GRS80_E2 = 0.00669438002301188;//第一遠心率  eの2乗

    Button connectButton;
    RelativeLayout fragmentLayout;
    ImageView imageBlack;
    TextView cadenceMonitor;
    TextView controlDeg;
    SeekBar seekBar;

    public int offsetValue = 500;

    private SpeechRecognizer speechRecognizer;
    private final static String TAG = "SPEECH_RECOGNIZER";

    // Activityとserviceの通信
    Messenger messenger = null;
    boolean bound;
    // ---------------------

    // 定期実行Handler
    Handler handler;
    Runnable r;
    public boolean takeOffFlag = false;
    public static int previousDistanceObj = -20;  // 距離のフラグ、最新のobtainObjと比較して変化があった場合に距離の時報を再生する

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
        controlDeg = (TextView) findViewById(R.id.controlDeg);
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
            startService(intent);
            bindService(new Intent(this, SerialService.class), connection, Context.BIND_AUTO_CREATE);
            speechRecognizer.startListening(speechRecognizerIntent);
            // buttonを押したら邪魔なので見えなくする
            connectButton.setVisibility(View.GONE);

            // 定期実行関数、10秒おきに高度と距離を確認して必要ならばボイスを再生
            handler = new Handler(getMainLooper());
            r = new Runnable() {
                @Override
                public void run() {
                    //speechRecognizer.stopListening();

                    if(height < 2000 && takeOffFlag){   // takeOffFlagがtrueになるまで高度注意警報は実行しない
                        Message message = Message.obtain(null, SerialService.HEIGHT_1, 0, 0);
                        if(messenger != null){
                            try {
                                messenger.send(message);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                    }else {
                        // 現在距離の確認
                        int obj = outputCurrentDistance1ObtainObj();
                        if (previousDistanceObj != obj){
                            takeOffFlag = true;
                            previousDistanceObj = obj;  // フラグの更新、つまりは現在の距離の更新を意味する
                            Message message = Message.obtain(null, obj, 0, 0);
                            if(messenger != null){
                                try {
                                    messenger.send(message);
                                } catch (RemoteException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }

                    //speechRecognizer.startListening(speechRecognizerIntent);
                    // すでに音声認識エンジンを起動している状態で再度エンジンを起動するとERROR_RECOGNIZER_BUSYが出力される
                    handler.postDelayed(this, 10000);   // 10秒間隔で現在の状況を判断する
                }
            };
            handler.post(r);
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

                String str = data.get(0);

                if(str.contains("距離")) {
                    obtainObj = outputCurrentDistanceObtainObj();
                } else if (str.contains("コード")) {   // 音声認識辞書データの都合上"高度"="コード"と読み取ってしまう
                    obtainObj = outputCurrentHeightObtainObj();
                } else if (str.contains("ケイデンス")) {
                    obtainObj = outputCurrentCadenceObtainObj();
                } else if (str.contains("速度")) {
                    obtainObj = outputCurrentGroundSpeedObtainObj();
                } else if (str.contains("風速")) {
                    obtainObj = outputCurrentAirSpeedObtainObj();
                } else if (str.contains("パワー")) {
                    obtainObj = outputCurrentPowerObtainObj();
                } else if (str.contains("準備はいい")) {
                    obtainObj = SerialService.SYSTEM_4;
                } else if(str.contains("暑い") || str.contains("熱い")){
                    obtainObj = SerialService.OTHER_6;
                } else if(str.contains("辛い") || str.contains("つらい")){
                    obtainObj = SerialService.OTHER_5;
                } else if(str.contains("疲れた")){
                    obtainObj = SerialService.OTHER_4;
                } else if(str.contains("腹減")){
                    obtainObj = SerialService.OTHER_3;
                } else if(str.contains("ブラック")){
                    obtainObj = SerialService.OTHER_1;
                } else if(str.contains("ホワイト")){
                    obtainObj = SerialService.OTHER_2;
                } else {
                    speechRecognizer.startListening(speechRecognizerIntent);
                    return;
                }

                msg = Message.obtain(null, obtainObj, 0, 0);

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

    private int outputCurrentDistanceObtainObj(){
        if (distanceHome > 0 && distanceHome < 500) {
            obtainObj = SerialService.DISTANCE_0;
        } else if (distanceHome > 499 && distanceHome < 1000) {
            obtainObj = SerialService.DISTANCE_1;
        } else if (distanceHome > 999 && distanceHome < 1500) {
            obtainObj = SerialService.DISTANCE_2;
        } else if (distanceHome > 1499 && distanceHome < 2000) {
            obtainObj = SerialService.DISTANCE_3;
        } else if (distanceHome > 1999 && distanceHome < 2500) {
            obtainObj = SerialService.DISTANCE_4;
        } else if (distanceHome > 2499 && distanceHome < 3000) {
            obtainObj = SerialService.DISTANCE_5;
        } else if (distanceHome > 2999 && distanceHome < 3500) {
            obtainObj = SerialService.DISTANCE_6;
        } else if (distanceHome > 3499 && distanceHome < 4000) {
            obtainObj = SerialService.DISTANCE_7;
        } else if (distanceHome > 3999 && distanceHome < 4500) {
            obtainObj = SerialService.DISTANCE_8;
        } else if (distanceHome > 4499 && distanceHome < 5000) {
            obtainObj = SerialService.DISTANCE_9;
        } else if (distanceHome > 4999 && distanceHome < 5500) {
            obtainObj = SerialService.DISTANCE_10;
        } else if (distanceHome > 5499 && distanceHome < 6000) {
            obtainObj = SerialService.DISTANCE_11;
        } else if (distanceHome > 5999 && distanceHome < 6500) {
            obtainObj = SerialService.DISTANCE_12;
        } else if (distanceHome > 6499 && distanceHome < 7000) {
            obtainObj = SerialService.DISTANCE_13;
        } else if (distanceHome > 6999 && distanceHome < 7500) {
            obtainObj = SerialService.DISTANCE_14;
        } else if (distanceHome > 7499 && distanceHome < 8000) {
            obtainObj = SerialService.DISTANCE_15;
        } else if (distanceHome > 7999 && distanceHome < 8500) {
            obtainObj = SerialService.DISTANCE_16;
        } else if (distanceHome > 8499 && distanceHome < 9000) {
            obtainObj = SerialService.DISTANCE_17;
        } else if (distanceHome > 8999 && distanceHome < 9500) {
            obtainObj = SerialService.DISTANCE_18;
        } else if (distanceHome > 9499 && distanceHome < 10000) {
            obtainObj = SerialService.DISTANCE_19;
        } else if (distanceHome > 9999 && distanceHome < 10500) {
            obtainObj = SerialService.DISTANCE_20;
        } else if (distanceHome > 10499 && distanceHome < 11000) {
            obtainObj = SerialService.DISTANCE_21;
        } else if (distanceHome > 10999 && distanceHome < 11500) {
            obtainObj = SerialService.DISTANCE_22;
        } else if (distanceHome > 11499 && distanceHome < 12000) {
            obtainObj = SerialService.DISTANCE_23;
        } else if (distanceHome > 11999 && distanceHome < 12500) {
            obtainObj = SerialService.DISTANCE_24;
        } else if (distanceHome > 12499 && distanceHome < 13000) {
            obtainObj = SerialService.DISTANCE_25;
        } else if (distanceHome > 12999 && distanceHome < 13500) {
            obtainObj = SerialService.DISTANCE_26;
        } else if (distanceHome > 13499 && distanceHome < 14000) {
            obtainObj = SerialService.DISTANCE_27;
        } else if (distanceHome > 13999 && distanceHome < 14500) {
            obtainObj = SerialService.DISTANCE_28;
        } else if (distanceHome > 14499 && distanceHome < 15000) {
            obtainObj = SerialService.DISTANCE_29;
        } else if (distanceHome > 14999 && distanceHome < 15500) {
            obtainObj = SerialService.DISTANCE_30;
        } else if (distanceHome > 15499 && distanceHome < 16000) {
            obtainObj = SerialService.DISTANCE_31;
        } else if (distanceHome > 15999 && distanceHome < 16500) {
            obtainObj = SerialService.DISTANCE_32;
        } else if (distanceHome > 16499 && distanceHome < 17000) {
            obtainObj = SerialService.DISTANCE_33;
        } else if (distanceHome > 16999 && distanceHome < 17500) {
            obtainObj = SerialService.DISTANCE_34;
        } else if (distanceHome > 17499 && distanceHome < 18000) {
            obtainObj = SerialService.DISTANCE_35;
        } else {
            obtainObj = SerialService.DISTANCE_35;
        }
        return obtainObj;
    }

    // 距離時報用
    private int outputCurrentDistance1ObtainObj(){
        if (distanceHome > 30 && distanceHome < 500) {
            obtainObj = SerialService.DISTANCE1_0;
        } else if (distanceHome > 500 && distanceHome < 1000) {
            obtainObj = SerialService.DISTANCE1_1;
        } else if (distanceHome > 1000 && distanceHome < 2000) {
            obtainObj = SerialService.DISTANCE1_2;
        } else if (distanceHome > 2000 && distanceHome < 3000) {
            obtainObj = SerialService.DISTANCE1_3;
        } else if (distanceHome > 3000 && distanceHome < 4000) {
            obtainObj = SerialService.DISTANCE1_4;
        } else if (distanceHome > 4000 && distanceHome < 5000) {
            obtainObj = SerialService.DISTANCE1_5;
        } else if (distanceHome > 5000 && distanceHome < 6000) {
            obtainObj = SerialService.DISTANCE1_6;
        } else if (distanceHome > 6000 && distanceHome < 7000) {
            obtainObj = SerialService.DISTANCE1_7;
        } else if (distanceHome > 7000 && distanceHome < 8000) {
            obtainObj = SerialService.DISTANCE1_8;
        } else if (distanceHome > 8000 && distanceHome < 9000) {
            obtainObj = SerialService.DISTANCE1_9;
        } else if (distanceHome > 9000 && distanceHome < 10000) {
            obtainObj = SerialService.DISTANCE1_10;
        } else if (distanceHome > 10000 && distanceHome < 11000) {
            obtainObj = SerialService.DISTANCE1_11;
        } else if (distanceHome > 11000 && distanceHome < 12000) {
            obtainObj = SerialService.DISTANCE1_12;
        } else if (distanceHome > 12000 && distanceHome < 13000) {
            obtainObj = SerialService.DISTANCE1_13;
        } else if (distanceHome > 13000 && distanceHome < 14000) {
            obtainObj = SerialService.DISTANCE1_14;
        } else if (distanceHome > 14000 && distanceHome < 15000) {
            obtainObj = SerialService.DISTANCE1_15;
        } else if (distanceHome > 15000 && distanceHome < 16000) {
            obtainObj = SerialService.DISTANCE1_16;
        } else if (distanceHome > 16000 && distanceHome < 17000) {
            obtainObj = SerialService.DISTANCE1_17;
        } else if (distanceHome > 17000 && distanceHome < 18000) {
            obtainObj = SerialService.DISTANCE1_18;
        } else {
            obtainObj = -20;
        }
        return obtainObj;
    }

    private int outputCurrentGroundSpeedObtainObj(){
        if (groundSpeed < 1) {
            obtainObj = SerialService.GROUND_SPEED_1;
        } else if (groundSpeed > 5 && groundSpeed < 7) {
            obtainObj = SerialService.GROUND_SPEED_2;
        } else if (groundSpeed > 6 && groundSpeed < 10) {
            obtainObj = SerialService.GROUND_SPEED_3;
        } else if (groundSpeed > 9 && groundSpeed < 12) {
            obtainObj = SerialService.GROUND_SPEED_4;
        }else if (groundSpeed > 11 && groundSpeed < 15){
            obtainObj = SerialService.GROUND_SPEED_5;
        }else if (groundSpeed > 15){
            obtainObj = SerialService.GROUND_SPEED_6;
    } else {
            obtainObj = SerialService.OTHER_1;
        }
        return obtainObj;
    }
    private int outputCurrentHeightObtainObj(){
        if (height > 0 && height < 2000) {
            obtainObj = SerialService.HEIGHT_1;
        } else if (height > 2000 && height < 3000) {
            obtainObj = SerialService.HEIGHT_2;
        } else if (height > 3000 && height < 4000) {
            obtainObj = SerialService.HEIGHT_3;
        } else if (height > 4000 && height < 5000) {
            obtainObj = SerialService.HEIGHT_4;
        } else if (height > 5000) {
            obtainObj = SerialService.HEIGHT_5;
        } else {
            obtainObj = SerialService.OTHER_1;
        }
        return obtainObj;
    }

    private int outputCurrentCadenceObtainObj(){
        return obtainObj;
    }

    private int outputCurrentAirSpeedObtainObj(){
        return obtainObj;
    }

    private int outputCurrentPowerObtainObj(){
        if (power < 200) {
            obtainObj = SerialService.POWER_1;
        } else if (power > 200 && power < 230) {
            obtainObj = SerialService.POWER_2;
        } else if (power > 230 && power < 250) {
            obtainObj = SerialService.POWER_3;
        } else if (power > 250 && power < 280) {
            obtainObj = SerialService.POWER_4;
        } else if (power > 280 && power < 300) {
            obtainObj = SerialService.POWER_5;
        } else if (power > 300 && power < 330) {
            obtainObj = SerialService.POWER_6;
        } else if (power > 330 && power < 350) {
            obtainObj = SerialService.POWER_7;
        } else if (power > 350 && power < 380) {
            obtainObj = SerialService.POWER_8;
        } else if (power > 380 && power < 400) {
            obtainObj = SerialService.POWER_9;
        } else if (power > 400) {
            obtainObj = SerialService.POWER_10;
        } else {
            obtainObj = SerialService.OTHER_1;
        }
        return obtainObj;
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
            //distancePylon = (int)calcDistance(pylonPointLat, pylonPointLon, latitude, longitude);
            // homePointまでの距離(m)
            //distanceHome = 18000 - distancePylon;
            distanceHome = (int)calcDistance(homePointLat, homePointLon, latitude, longitude);

            Log.i("debugGPS", "distanceHome" + String.valueOf(distanceHome));
            Log.i("debugGPS", "distancePylon" + String.valueOf(distancePylon));

            // 操舵計表示
            int deg = Integer.parseInt(data[5]) - offsetValue;
            cadence = Integer.parseInt(data[6]);
            power = Integer.parseInt(data[8]);
            String str = String.valueOf(cadence) + "RPM\n" + String.valueOf(power) + "W";
            cadenceMonitor.setText(str);
            Log.i("debugCadence", "cadence" + String.valueOf(cadence));

            // 高度の色表示
            height = Integer.parseInt(data[4]);
            if(height < 2000 || height > 5000) {
                fragmentLayout.setBackgroundColor(getResources().getColor(R.color.red, getTheme()));
            } else {
                fragmentLayout.setBackgroundColor(getResources().getColor(R.color.blue, getTheme()));
            }
            Log.i("debugHeight", "height" + String.valueOf(height));

            groundSpeed = (Integer.parseInt(data[7]) / 1000);

        }
    };

    private static double deg2rad(double deg){
        return deg * Math.PI / 180.0;
    }

    private static double calcDistance(double lat1, double lng1, double lat2, double lng2){
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