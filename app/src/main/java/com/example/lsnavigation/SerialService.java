package com.example.lsnavigation;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SerialService extends Service {

    private final String TAG = "SerialService";
    public ScheduledExecutorService schedule;

    public static final String ACTION = "SerialServiceAction";
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    public static UsbSerialDevice serial;
    public static Context context;

    // ---ActivityからServiceの受信部、音声認識の結果をserial.writeする
    Messenger messenger;
    static final int DISTANCE_1 = 1;
    static final int DISTANCE_2 = 2;
    static final int DISTANCE_3 = 3;
    static final int DISTANCE_4 = 4;
    static final int DISTANCE_5 = 5;

    static boolean flag = false;

    static class VoiceHandler extends Handler{

        public VoiceHandler(Context cont){
            context = cont.getApplicationContext();
        }

        @Override
        public void handleMessage(Message msg){
            //serial.syncOpen();
            switch (msg.what) {
                case DISTANCE_1:
                    Toast.makeText(context.getApplicationContext(), "write", Toast.LENGTH_SHORT).show();
                    serial.syncWrite("b_distance_1.wav".getBytes(), 1000);
                    break;
                case DISTANCE_2:

                    serial.syncWrite("b_distance_2.wav".getBytes(), 1000);
                    break;
                case DISTANCE_3:
                    serial.syncWrite("b_distance_3.wav".getBytes(), 1000);
                    break;
            }
        }
    }

    /* サービスバインド時実行
     * サービスとアクティビティ間で通信するときに利用するメソッド
     */
    @Override
    public IBinder onBind(Intent intent) {
        messenger = new Messenger(new VoiceHandler(this));
        return messenger.getBinder();
    }


    /**
     * USBシリアル通信の初期化を行う
     * @return 成功した場合はUsbSerialDevice、失敗した場合はnull
     */
    public UsbSerialDevice initializeUsbSerial() {
        UsbManager usbManager = (UsbManager) getSystemService(context.USB_SERVICE);

        // USBデバイス一覧を取得して、一覧からUSBシリアルデバイスを探す
        UsbDevice usbDevice = null;
        for (UsbDevice d : usbManager.getDeviceList().values()) {
            // 関係ないデバイスに誤爆することがあるので、Raspberry Pi Picoが見つかるようにVendorIdもチェックする
            if (UsbSerialDevice.isSupported(d) && UsbSerialDevice.isCdcDevice(d) && d.getVendorId() == 0x2E8A) {
                usbDevice = d;
            }
        }
        if (usbDevice == null) {return null;}

        // 権限チェック
        if (!usbManager.hasPermission(usbDevice)) {
            // ユーザからアクセス許可をもらっていなかったら、許可を求めたうえで初期化は失敗扱いにしてnullを返す。
            // 一度ユーザが許可をすれば、次回以降のアプリ起動時に接続ができるようになる。
            // Flag->PendingIntent.FLAG_MUTABLE or FLAG.IMMUTABLE を設定しなければいけない
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE);
            usbManager.requestPermission(usbDevice, pendingIntent);
            return null;
        }

        // 権限があるのでデバイスに接続する
        UsbDeviceConnection usbDeviceConnection = usbManager.openDevice(usbDevice);
        return UsbSerialDevice.createUsbSerialDevice(usbDevice, usbDeviceConnection);
    }


    public SerialService() {
    }

    // サービス初回起動時に実行
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");
        serial = initializeUsbSerial();
        if (serial == null) {
            // USBシリアルの初期化に失敗
            Toast.makeText(this, "connection failed", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "connection failed");
        }else{
            Toast.makeText(this, "connected", Toast.LENGTH_SHORT).show();
            // Picoとの接続を確認し、Picoにmsgを送る
            serial.syncOpen();
            serial.syncWrite("connect".getBytes(), 1000);
            //serial.close();
        }
    }


// サービスの起動都度に実行

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        // 定期的に実行
        schedule = Executors.newSingleThreadScheduledExecutor();
        // 1000msecごとに処理を実行
        schedule.scheduleAtFixedRate(()->{
            String value = intent.getStringExtra("data");
            Log.i("service", value);

            //serial.syncOpen();
            //BufferedReader serialReader = new BufferedReader(new InputStreamReader(serial.getInputStream()));
            byte[] b = new byte[100];
            String buf = null;

            int n = serial.syncRead(b, 1000);
            if(n > 0) {
                byte[] received = new byte[n];
                System.arraycopy(b, 0, received, 0, n);
                buf = new String(received);
            }else{
                return;
            }

            // とりあえず受信する
            /*try {
                buf = serialReader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

             */

            Intent intent2 = new Intent(ACTION);
            intent2.putExtra("message", buf);
            // ブロードキャストの送信、受信側はレシーバーconnectを作成しなければいけない-> BroadcastReceiver
            sendBroadcast(intent2);
            Log.i(TAG, "onStartCommand");
        }, 0, 1000, TimeUnit.MILLISECONDS);
        /*
         * onStartCommandの戻り値は、サービスがシステムによって強制終了されたときにどのように振る舞うかを表す。
         * ------------------------
         * START_NOT_STICKY             サービスを起動しない
         * START_STICKY                 サービスを再起動
         * START_REDELIVER_INTENT       終了前と同じインテントを使って再起動する
         * START_STICKY_COMPATIBILITY   再起動は保証されない(START_STICKYとの互換)
         */
        return START_STICKY;
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        schedule.shutdown();
    }
}