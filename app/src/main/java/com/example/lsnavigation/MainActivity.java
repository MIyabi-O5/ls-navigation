package com.example.lsnavigation;

import static java.lang.Math.abs;

import androidx.appcompat.app.ActionBar;
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
import java.util.Random;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    MapView map;
    IMapController mapController;
    Random rand = new Random();
    // MediaPlayerの再生用Key
    public static final String VOICE_KEY = "VOICE_KEY";
    // ----debug用TAG----
    private static final String MAIN_ACTIVITY_DEBUG_TAG = "MainActivity";
    private static final String SPEECH_DEBUG_TAG = "SPEECH_RECOGNIZER";
    private static final String SERIAL_DEBUG_TAG = "SERIAL";
    private static final String CADENCE_DEBUG_TAG = "CADENCE";
    // -----------------

    public static int cadence = 0;
    public static int power = 0;
    public static int height = 0;
    public static int deg = 0;

    public static int obtainObj;
    public static boolean isButtonVisible = true;
    Button connectButton;
    Button rebootButton;
    Button homePosButton;
    ImageView imageBlack;
    TextView altitudeView;
    TextView cadenceMonitor;

    private SpeechRecognizer speechRecognizer;
    private final static String TAG = "SPEECH_RECOGNIZER";

    // Activityとserviceの通信
    Messenger messenger = null;
    boolean bound;
    // ---------------------

    // 定期実行Handler
    Handler handler;
    Runnable r;

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
        //　コントロールボタン
        connectButton = (Button) findViewById(R.id.connectButton);
        rebootButton = (Button) findViewById(R.id.rebootButton);
        homePosButton = (Button) findViewById(R.id.homePosButton);
        imageBlack = (ImageView) findViewById(R.id.imageBlack);
        // state表示
        altitudeView = (TextView) findViewById(R.id.altitudeView);
        // 計器類の表示
        cadenceMonitor = (TextView) findViewById(R.id.cadenceView);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        Configuration.getInstance()
                .load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        setContentView(R.layout.activity_main);

        // actionbarの非表示
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        // 初期化
        findViews();

        //startVoiceService(R.raw.system_1);

        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        Intent speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 2);


        mapController = map.getController();
        map.setTileSource(TileSourceFactory.MAPNIK);
        GNSSUtils.centerPoint = new GeoPoint(35.6587588, 139.5434757);

        mapController.setCenter(GNSSUtils.homePoint);
        map.setMultiTouchControls(true);
        mapController.setZoom(15.0);

        // パイロン座標の表示
        Marker marker = new Marker(map);
        marker.setPosition(GNSSUtils.homePoint);
        map.getOverlays().add(marker);
        Drawable icon = getDrawable(R.drawable.white);
        marker.setIcon(icon);


        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(SerialService.ACTION);

        // Receiverの登録、manifestにも追記する必要があるReceiverも存在する
        registerReceiver(receiver, intentFilter);


        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle bundle) {
                Log.i(SPEECH_DEBUG_TAG, "onReadyForSpeech");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.i(SPEECH_DEBUG_TAG, "onBeginningOfSpeech");
            }

            @Override
            public void onRmsChanged(float v) {
                //Log.i(SPEECH_DEBUG_TAG, "onRmsChanged");
            }

            @Override
            public void onBufferReceived(byte[] bytes) {
                Log.i(SPEECH_DEBUG_TAG, "onBufferReceived");
            }

            @Override
            public void onEndOfSpeech() {
                Log.i(SPEECH_DEBUG_TAG, "onEndOfSpeech");
            }

            @Override
            public void onError(int i) {
                switch (i) {
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        Log.d(SPEECH_DEBUG_TAG, "ネットワークタイムエラー");
                        break;
                    case SpeechRecognizer.ERROR_NETWORK:
                        Log.d(SPEECH_DEBUG_TAG, "その外ネットワークエラー");
                        break;
                    case SpeechRecognizer.ERROR_AUDIO:
                        Log.d(SPEECH_DEBUG_TAG, "Audio エラー");
                        break;
                    case SpeechRecognizer.ERROR_SERVER:
                        Log.d(SPEECH_DEBUG_TAG, "サーバーエラー");
                        break;
                    case SpeechRecognizer.ERROR_CLIENT:
                        Log.d(SPEECH_DEBUG_TAG, "クライアントエラー");
                        break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        Log.d(SPEECH_DEBUG_TAG, "何も聞こえてないエラー");
                        break;
                    case SpeechRecognizer.ERROR_NO_MATCH:
                        Log.d(SPEECH_DEBUG_TAG, "適当な結果を見つけてませんエラー");
                        break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        Log.d(SPEECH_DEBUG_TAG, "RecognitionServiceが忙しいエラー");
                        break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                        Log.d(SPEECH_DEBUG_TAG, "RECORD AUDIOがないエラー");
                        break;
                }
                speechRecognizer.startListening(speechRecognizerIntent);
            }

            @Override
            public void onResults(Bundle results) {
                Log.i(TAG, "onResults");
                Message msg = null;
                ArrayList<String> data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                // debug用、本番では消す
                cadenceMonitor.setText(data.get(0));
                if(!bound){
                    speechRecognizer.startListening(speechRecognizerIntent);
                    return;
                }

                String str = data.get(0);

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

        connectButton.setOnClickListener(this);
        rebootButton.setOnClickListener(this);
        homePosButton.setOnClickListener(this);

    }

    // button類を押したときの動作
    @Override
    public void onClick(View view){
        int tmp = view.getId();
        if (tmp == R.id.connectButton){
            Log.d(MAIN_ACTIVITY_DEBUG_TAG, "connectButton");
            Intent intent = new Intent(this, SerialService.class);
            startService(intent);
            bindService(new Intent(this, SerialService.class), connection, Context.BIND_AUTO_CREATE);
            // 音声認識
            //speechRecognizer.startListening(speechRecognizerIntent);
            // buttonを押したら邪魔なので見えなくする
            connectButton.setVisibility(View.GONE);

            // 定期実行関数、10秒おきに高度と距離を確認して必要ならばボイスを再生
            handler = new Handler(getMainLooper());
            r = new Runnable() {
                @Override
                public void run() {
                    Log.d(MAIN_ACTIVITY_DEBUG_TAG, "Runnable");
                    handler.postDelayed(this, 10000);   // 10秒間隔で現在の状況を判断する
                }
            };
            handler.post(r);
        } else if (tmp == R.id.rebootButton) {
            Log.d(MAIN_ACTIVITY_DEBUG_TAG, "reboot");
        }else if (tmp == R.id.homePosButton){
            Log.d(MAIN_ACTIVITY_DEBUG_TAG, "homePosButton");
        }
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
            Log.d(SERIAL_DEBUG_TAG, msg);
        }
    };

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent){
        /*
         * 画面をタッチしたときにButton類を表示/非表示する
         * その後isButtonVisibleを更新する
         * @param motionEvent タッチイベント
         * @return boolean
         */
        Log.d(MAIN_ACTIVITY_DEBUG_TAG, "onTouchEvent");
        if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
            if(isButtonVisible){
                buttonsVisible(View.GONE);
                isButtonVisible = false;
            }else{
                buttonsVisible(View.VISIBLE);
                isButtonVisible = true;
            }
        }
        return true;
    }

    private void buttonsVisible(int viewVisible){
        /*
         * Button類を表示/非表示する
         * @param int viewVisible View.VISIBLE or View.GONE
         * @return void
         */
        connectButton.setVisibility(viewVisible);
        rebootButton.setVisibility(viewVisible);
        homePosButton.setVisibility(viewVisible);
    }

    private void startVoiceService(int data){
        /*
         * 音声ファイルを再生するサービスを開始する,
         * このサービスは再生が終了すると自動的に終了する
         * @param data 再生する音声ファイルのID, ex)R.raw.system_1
         * @return void
         */
        Intent intent = new Intent(this, VoiceService.class);
        intent.putExtra(VOICE_KEY, data);
        startService(intent);
    }

}