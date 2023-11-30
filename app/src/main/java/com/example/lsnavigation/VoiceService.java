package com.example.lsnavigation;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.util.Log;

public class VoiceService extends Service implements MediaPlayer.OnCompletionListener{
    private MediaPlayer mediaPlayer;
    private static final String SERVICE_TAG = "AUDIO_SERVICE";

    public VoiceService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int data = intent.getIntExtra(MainActivity.VOICE_KEY, 0);
        // Rawフォルダにある音声ファイルを再生
        mediaPlayer = MediaPlayer.create(this, data); // 音声ファイルを読み込み
        mediaPlayer.setOnCompletionListener(this); // 再生完了時のリスナーをセット
        mediaPlayer.setLooping(false);
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start(); // 音声再生を開始
            Log.d(SERVICE_TAG, "MediaPlayer started");
        }
        // START_NOT_STICKYを返すことで、サービスが強制終了された場合に再作成されないようにします
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mediaPlayer != null) {
            if(mediaPlayer.isPlaying()) {
                mediaPlayer.stop(); // 音声再生を停止
            }
            mediaPlayer.release(); // リソースを解放
            mediaPlayer = null;
        }
        super.onDestroy();
        Log.d(SERVICE_TAG, "MediaPlayer stopped");
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        stopSelf();
    }
}