package com.example.lsnavigation;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.IBinder;
import android.util.Log;

import com.felhr.usbserial.UsbSerialDevice;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SerialService extends Service {

    private final String TAG = "SerialService";
    private ScheduledExecutorService schedule;

    public static final String ACTION = "SerialService Action";
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    Context context;

    /**
     * USBシリアル通信の初期化を行う
     * @return 成功した場合はUsbSerialDevice、失敗した場合はnull
     */
    UsbSerialDevice initializeUsbSerial() {
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
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
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
    }


// サービスの起動都度に実行

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        // 定期的に実行
        schedule = Executors.newSingleThreadScheduledExecutor();
        // 1000msecごとに処理を実行
        schedule.scheduleAtFixedRate(()->{
            // 現在時刻をブロードキャスト配信
            Intent intent2 = new Intent(ACTION);
            intent2.putExtra("message", (new Date()).toString());
            // ブロードキャストの送信、受信側はレシーバーを作成しなければいけない-> BroadcastReceiver
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

    /* サービスバインド時実行
     * サービスとアクティビティ間で通信するときに利用するメソッド
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        schedule.shutdown();
    }
}