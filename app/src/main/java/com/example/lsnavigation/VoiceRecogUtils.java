package com.example.lsnavigation;

import android.content.Intent;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.view.View;


public abstract class VoiceRecogUtils  extends MainActivity implements RecognitionListener {
 public static final String VOICE_KEY="VOICE_KEY";
 private static final String TAG="speechrecognizer";
    private static final int REQUEST_CODE=0;
    private SpeechRecognizer speechRecognizer;
    public Intent speechRecognizerIntent;
    public int count=0;

    public static int getVoiceIdForStr(String str){
     int ret=0;
     //以下に音声を追加していく
     if (str.contains("ブラック")) {
      ret = R.raw.black_1;
     }
     else if (str.contains("ホワイト")){
      ret = R.raw.white_1;
     }
     return ret;
    }
}


