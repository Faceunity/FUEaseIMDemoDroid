package com.hyphenate.easeim.section.chat.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Group;

import com.faceunity.nama.FURenderer;
import com.faceunity.nama.ui.FaceUnityView;
import com.hyphenate.chat.EMCallSession;
import com.hyphenate.chat.EMCallStateChangeListener;
import com.hyphenate.chat.EMClient;
import com.hyphenate.chat.EMMirror;
import com.hyphenate.chat.EMVideoCallHelper;
import com.hyphenate.chat.EMWaterMarkOption;
import com.hyphenate.chat.EMWaterMarkPosition;
import com.hyphenate.easeim.DemoApplication;
import com.hyphenate.easeim.R;
import com.hyphenate.easeim.common.utils.PreferenceManager;
import com.hyphenate.easeim.common.utils.ViewScrollHelper;
import com.hyphenate.easeim.faceunity.CameraRenderer;
import com.hyphenate.easeim.faceunity.PreferenceUtil;
import com.hyphenate.easeim.section.conference.CallFloatWindow;
import com.hyphenate.easeui.EaseUI;
import com.hyphenate.easeui.manager.PhoneStateManager;
import com.hyphenate.exceptions.HyphenateException;
import com.hyphenate.media.EMCallSurfaceView;
import com.hyphenate.util.EMLog;
import com.superrtc.sdk.VideoView;

import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

public class VideoCallFragment extends EaseCallFragment implements View.OnClickListener, SensorEventListener {
    private TextView callStateTextView;
    private Group comingBtnContainer;
    private ImageButton refuseBtn;
    private ImageButton answerBtn;
    private ImageButton hangupBtn;
    private ImageView muteImage;
    private ImageView handsFreeImage;
    private TextView nickTextView;
    private Chronometer chronometer;
    private Group voiceContronlLayout;
    private ConstraintLayout rootContainer;
    private TextView monitorTextView;
    private TextView netwrokStatusVeiw;
    private TextView mTvFps;
    private Button switchCameraBtn;
    private ImageButton closeBtn;
    // 视频通话画面显示控件，这里在新版中使用同一类型的控件，方便本地和远端视图切换
    protected EMCallSurfaceView localSurface;
    protected EMCallSurfaceView oppositeSurface;
    private Group groupHangUp;
    private Group groupUseInfo;
    private Group groupOngoingSettings;

    private EMVideoCallHelper callHelper;
    private int surfaceState = -1;
    private boolean isMuteState;
    private boolean isHandsfreeState;
    private boolean isAnswered;
    private boolean endCallTriggerByMe = false;
    private boolean monitor = true;
    private boolean isInCalling;

    private Handler uiHandler;

    private Button toggleVideoBtn;
    private Bitmap watermarkbitmap;
    private EMWaterMarkOption watermark;
    private boolean isNewIntent;
    private boolean isPauseVideo;//是否暂停视频推流
    private SensorManager sensorManager;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        boolean isOpenFu = PreferenceUtil.ON.equals(PreferenceUtil.getString(mContext, PreferenceUtil.KEY_FACEUNITY_ISON));
        // setup camera renderer
        if (isOpenFu) {
            mCameraRenderer = new CameraRenderer(mContext, new FURenderer.OnDebugListener() {
                @Override
                public void onFpsChanged(double fps, double callTime) {
                    final String FPS = String.format(Locale.getDefault(), "%.2f", fps);
                    Log.e(TAG, "onFpsChanged: FPS " + FPS + " callTime " + String.format(Locale.getDefault(), "%.2f", callTime));
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mTvFps != null) {
                                mTvFps.setText("FPS: " + FPS);
                            }
                        }
                    });
                }
            });
        }
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null) {
            mContext.finish();
        }
        EMClient.getInstance().callManager().getCallOptions().setVideoResolution(480, 360);
        EMClient.getInstance().callManager().getCallOptions().setMaxVideoFrameRate(15);
        EMClient.getInstance().callManager().getCallOptions().setClarityFirst(false);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.demo_activity_video_call, null);
    }

    @Override
    protected void initArguments() {
        msgid = UUID.randomUUID().toString();
        Bundle bundle = getArguments();
        if(bundle != null) {
            isInComingCall = bundle.getBoolean("isComingCall", false);
            username = bundle.getString("username");
        }
    }

    @Override
    protected void initView() {
        EaseUI.getInstance().isVideoCalling = true;
        callType = 1;

        callStateTextView = (TextView) findViewById(R.id.tv_call_state);
        comingBtnContainer =  findViewById(R.id.ll_coming_call);
        rootContainer =  findViewById(R.id.root_layout);
        refuseBtn = findViewById(R.id.btn_refuse_call);
        answerBtn = findViewById(R.id.btn_answer_call);
        hangupBtn = findViewById(R.id.btn_hangup_call);
        closeBtn = (ImageButton)findViewById(R.id.btn_close_call);
        muteImage = (ImageView) findViewById(R.id.iv_mute);
        handsFreeImage = (ImageView) findViewById(R.id.iv_handsfree);
        callStateTextView = (TextView) findViewById(R.id.tv_call_state);
        nickTextView = (TextView) findViewById(R.id.tv_nick);
        chronometer = (Chronometer) findViewById(R.id.chronometer);
        voiceContronlLayout = findViewById(R.id.ll_voice_control);
        monitorTextView = (TextView) findViewById(R.id.tv_call_monitor);
        netwrokStatusVeiw = (TextView) findViewById(R.id.tv_network_status);
        mTvFps = (TextView) findViewById(R.id.tv_fps);
        switchCameraBtn = (Button) findViewById(R.id.btn_switch_camera);
        // local surfaceview
        localSurface = (EMCallSurfaceView) findViewById(R.id.local_surface);
        // remote surfaceview
        oppositeSurface = (EMCallSurfaceView) findViewById(R.id.opposite_surface);
        groupHangUp = findViewById(R.id.group_hang_up);
        groupUseInfo = findViewById(R.id.group_use_info);
        groupOngoingSettings = findViewById(R.id.group_ongoing_settings);

        nickTextView.setText(username);

        localSurface.setOnClickListener(this);
        localSurface.setZOrderMediaOverlay(true);
        localSurface.setZOrderOnTop(true);

        uiHandler = new Handler();

        FaceUnityView beautyControlView = findViewById(R.id.faceunity_view);

        boolean isOpenFu = PreferenceUtil.ON.equals(PreferenceUtil.getString(mContext, PreferenceUtil.KEY_FACEUNITY_ISON));

        if (isOpenFu) {
            beautyControlView.setModuleManager(mCameraRenderer.getFURenderer());
            sensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
            Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            beautyControlView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void initListener() {
        refuseBtn.setOnClickListener(this);
        answerBtn.setOnClickListener(this);
        hangupBtn.setOnClickListener(this);
        closeBtn.setOnClickListener(this);
        muteImage.setOnClickListener(this);
        handsFreeImage.setOnClickListener(this);
        rootContainer.setOnClickListener(this);
        switchCameraBtn.setOnClickListener(this);
        // set call state listener
        addCallStateListener();
        ViewScrollHelper.getInstance(mContext).makeViewCanScroll(localSurface);
    }

    @Override
    protected void initData() {
        //获取水印图片
        if(PreferenceManager.getInstance().isWatermarkResolution()) {
            try {
                InputStream in = this.getResources().getAssets().open("watermark.png");
                watermarkbitmap = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                e.printStackTrace();
            }
            watermark = new EMWaterMarkOption(watermarkbitmap, 75, 25, EMWaterMarkPosition.TOP_RIGHT, 8, 8);
        }
        if (!isInComingCall) {// outgoing call
            soundPool = new SoundPool(1, AudioManager.STREAM_RING, 0);
            outgoing = soundPool.load(mContext, R.raw.em_outgoing, 1);

            makeCallStatus();
            String st = getResources().getString(R.string.Are_connected_to_each_other);
            callStateTextView.setText(st);
            switchLocalToBig();
            handler.sendEmptyMessage(MSG_CALL_MAKE_VIDEO);
            handler.postDelayed(new Runnable() {
                public void run() {
                    streamID = playMakeCallSounds();
                }
            }, 300);
        } else { // incoming call

            callStateTextView.setText(getString(R.string.em_call_video_request, username));
            if(EMClient.getInstance().callManager().getCallState() == EMCallStateChangeListener.CallState.IDLE
                    || EMClient.getInstance().callManager().getCallState() == EMCallStateChangeListener.CallState.DISCONNECTED) {
                // the call has ended
                mContext.finish();
                return;
            }
            makeComingStatus();
            localSurface.setVisibility(View.INVISIBLE);
            Uri ringUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            audioManager.setMode(AudioManager.MODE_RINGTONE);
            audioManager.setSpeakerphoneOn(true);
            ringtone = RingtoneManager.getRingtone(mContext, ringUri);
            ringtone.play();
            switchLocalToBig();
        }

        final int MAKE_CALL_TIMEOUT = 50 * 1000;
        handler.removeCallbacks(timeoutHangup);
        handler.postDelayed(timeoutHangup, MAKE_CALL_TIMEOUT);

        // get instance of call helper, should be called after setSurfaceView was called
        callHelper = EMClient.getInstance().callManager().getVideoCallHelper();

        //设置小窗口悬浮类型
        CallFloatWindow.getInstance(DemoApplication.getInstance()).setCallType(CallFloatWindow.CallWindowType.VIDEOCALL);

    }

    @Override
    public void onResume() {
        super.onResume();
        if(isInCalling && isPauseVideo){
            try {
                EMClient.getInstance().callManager().resumeVideoTransfer();
                isPauseVideo = false;
            } catch (HyphenateException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 按下home键，或者其他操作返回桌面的，会执行这个方法
     */
    public void onUserLeaveHint() {
        if(isInCalling){
            //如果没有悬浮框权限，则暂停视频推流
            if(!isHaveOverlayPermission()) {
                try {
                    EMClient.getInstance().callManager().pauseVideoTransfer();
                    isPauseVideo = true;
                } catch (HyphenateException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onDestroy() {
        EaseUI.getInstance().isVideoCalling = false;
        stopMonitor();
        localSurface.getRenderer().dispose();
        localSurface = null;
        oppositeSurface.getRenderer().dispose();
        oppositeSurface = null;
        super.onDestroy();
    }

    @Override
    public void onBackPress() {
        callDruationText = chronometer.getText().toString();
        super.onBackPress();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.local_surface) {
            changeCallView();
        } else if (id == R.id.btn_refuse_call) { // decline the call
            isRefused = true;
            refuseBtn.setEnabled(false);
            handler.sendEmptyMessage(MSG_CALL_REJECT);
        } else if (id == R.id.btn_answer_call) { // answer the call
            EMLog.d(TAG, "btn_answer_call clicked");
            answerBtn.setEnabled(false);
            openSpeakerOn();
            if (ringtone != null)
                ringtone.stop();

            callStateTextView.setText(R.string.answering);
            handler.sendEmptyMessage(MSG_CALL_ANSWER);
            handsFreeImage.setImageResource(R.drawable.em_icon_speaker_on);
            isAnswered = true;
            isHandsfreeState = true;
            localSurface.setVisibility(View.VISIBLE);
            makeOngoingStatus();
            switchLocalToSmall();
        } else if (id == R.id.btn_hangup_call) { // hangup
            hangupBtn.setEnabled(false);
            chronometer.stop();
            endCallTriggerByMe = true;
            callStateTextView.setText(getResources().getString(R.string.hanging_up));
            EMLog.d(TAG, "btn_hangup_call");
            handler.sendEmptyMessage(MSG_CALL_END);
        }else if(id == R.id.btn_close_call) {
            floatState = surfaceState;
            showFloatWindow();
        } else if (id == R.id.iv_mute) { // mute
            if (isMuteState) {
                // resume voice transfer
                muteImage.setImageResource(R.drawable.em_icon_mute_normal);
                try {
                    EMClient.getInstance().callManager().resumeVoiceTransfer();
                } catch (HyphenateException e) {
                    e.printStackTrace();
                }
                isMuteState = false;
            } else {
                // pause voice transfer
                muteImage.setImageResource(R.drawable.em_icon_mute_on);
                try {
                    EMClient.getInstance().callManager().pauseVoiceTransfer();
                } catch (HyphenateException e) {
                    e.printStackTrace();
                }
                isMuteState = true;
            }
        } else if (id == R.id.iv_handsfree) { // handsfree
            if (isHandsfreeState) {
                // turn off speaker
                handsFreeImage.setImageResource(R.drawable.em_icon_speaker_normal);
                closeSpeakerOn();
                isHandsfreeState = false;
            } else {
                handsFreeImage.setImageResource(R.drawable.em_icon_speaker_on);
                openSpeakerOn();
                isHandsfreeState = true;
            }
            /*
        case R.id.btn_record_video: //record the video
            if(!isRecording){
//                callHelper.startVideoRecord(PathUtil.getInstance().getVideoPath().getAbsolutePath());
                callHelper.startVideoRecord("/sdcard/");
                EMLog.d(TAG, "startVideoRecord:" + PathUtil.getInstance().getVideoPath().getAbsolutePath());
                isRecording = true;
                recordBtn.setText(R.string.stop_record);
            }else{
                String filepath = callHelper.stopVideoRecord();
                isRecording = false;
                recordBtn.setText(R.string.recording_video);
                Toast.makeText(getApplicationContext(), String.format(getString(R.string.record_finish_toast), filepath), Toast.LENGTH_LONG).show();
            }
            break;
        */
        } else if (id == R.id.root_layout) {
            if (callingState == CallingState.NORMAL) {
                if (groupOngoingSettings.getVisibility() == View.VISIBLE) {
                    hideIconsStatus();
                    oppositeSurface.setScaleMode(VideoView.EMCallViewScaleMode.EMCallViewScaleModeAspectFill);

                } else {
                    showIconsStatus();
                    oppositeSurface.setScaleMode(VideoView.EMCallViewScaleMode.EMCallViewScaleModeAspectFit);
                }
            }
        } else if (id == R.id.btn_switch_camera) { //switch camera
            handler.sendEmptyMessage(MSG_CALL_SWITCH_CAMERA);
        }
    }

    /**
     * 来电话的状态
     */
    private void makeComingStatus() {
        voiceContronlLayout.setVisibility(View.INVISIBLE);
        comingBtnContainer.setVisibility(View.VISIBLE);
        groupUseInfo.setVisibility(View.VISIBLE);
        groupOngoingSettings.setVisibility(View.INVISIBLE);
        localSurface.setVisibility(View.INVISIBLE);
        groupHangUp.setVisibility(View.INVISIBLE);
        groupRequestLayout();
    }

    /**
     * 通话中的状态
     */
    private void makeOngoingStatus() {
        voiceContronlLayout.setVisibility(View.VISIBLE);
        comingBtnContainer.setVisibility(View.INVISIBLE);
        groupUseInfo.setVisibility(View.INVISIBLE);
        groupOngoingSettings.setVisibility(View.VISIBLE);
        localSurface.setVisibility(View.VISIBLE);
        groupHangUp.setVisibility(View.VISIBLE);
        groupRequestLayout();
    }

    /**
     * 拨打电话的状态
     */
    private void makeCallStatus() {
        voiceContronlLayout.setVisibility(View.INVISIBLE);
        comingBtnContainer.setVisibility(View.INVISIBLE);
        groupUseInfo.setVisibility(View.VISIBLE);
        groupOngoingSettings.setVisibility(View.INVISIBLE);
        localSurface.setVisibility(View.INVISIBLE);
        groupHangUp.setVisibility(View.VISIBLE);
        groupRequestLayout();
    }

    private void hideIconsStatus() {
        voiceContronlLayout.setVisibility(View.INVISIBLE);
        groupOngoingSettings.setVisibility(View.INVISIBLE);
        groupHangUp.setVisibility(View.INVISIBLE);
        netwrokStatusVeiw.setVisibility(View.INVISIBLE);
        monitorTextView.setVisibility(View.INVISIBLE);
        voiceContronlLayout.requestLayout();
        groupOngoingSettings.requestLayout();
        groupHangUp.requestLayout();
    }

    private void showIconsStatus() {
        voiceContronlLayout.setVisibility(View.VISIBLE);
        groupOngoingSettings.setVisibility(View.VISIBLE);
        groupHangUp.setVisibility(View.VISIBLE);
        netwrokStatusVeiw.setVisibility(View.VISIBLE);
        monitorTextView.setVisibility(View.VISIBLE);
        voiceContronlLayout.requestLayout();
        groupOngoingSettings.requestLayout();
        groupHangUp.requestLayout();
    }

    private void groupRequestLayout() {
        comingBtnContainer.requestLayout();
        voiceContronlLayout.requestLayout();
        groupHangUp.requestLayout();
        groupUseInfo.requestLayout();
        groupOngoingSettings.requestLayout();
    }

    /**
     * 切换通话界面，这里就是交换本地和远端画面控件设置，以达到通话大小画面的切换
     */
    private void changeCallView() {
        if (surfaceState == 0) {
            switchLocalToBig();
        } else {
            switchLocalToSmall();
        }
    }

    /**
     * 切换本地视频到小屏幕
     */
    private void switchLocalToSmall() {
        surfaceState = 0;
        EMClient.getInstance().callManager().setSurfaceView(localSurface, oppositeSurface);
    }

    /**
     * 切换本地视频到主屏幕
     */
    private void switchLocalToBig() {
        surfaceState = 1;
        EMClient.getInstance().callManager().setSurfaceView(oppositeSurface, localSurface);
    }

    void stopMonitor(){
        monitor = false;
    }

    /**
     * set call state listener
     */
    void addCallStateListener() {
        callStateListener = new EMCallStateChangeListener() {

            @Override
            public void onCallStateChanged(final CallState callState, final CallError error) {
                if(isActivityDisable()) {
                    return;
                }
                mContext.runOnUiThread(() -> {
                    switch (callState) {
                        case CONNECTING: // is connecting
                            callStateTextView.setText(R.string.Are_connected_to_each_other);
                            break;
                        case CONNECTED: // connected

                            break;
                        case ACCEPTED: // call is accepted
                            surfaceState = 0;
                            handler.removeCallbacks(timeoutHangup);

                            String callId = EMClient.getInstance().callManager().getCurrentCallSession().getCallId();

                            if(!EMClient.getInstance().callManager().getCurrentCallSession().getIscaller()){
                                //推流时设置水印图片
                                if(PreferenceManager.getInstance().isWatermarkResolution()){
                                    EMClient.getInstance().callManager().setWaterMark(watermark);

                                    //开启水印时候本地不开启镜像显示
                                    EMClient.getInstance().callManager().getCallOptions().
                                            setLocalVideoViewMirror(EMMirror.OFF);
                                }else{
                                    EMClient.getInstance().callManager().getCallOptions().
                                            setLocalVideoViewMirror(EMMirror.ON);
                                }
                            }
                            try {
                                if (soundPool != null)
                                    soundPool.stop(streamID);
                                EMLog.d("EMCallManager", "soundPool stop ACCEPTED");
                            } catch (Exception e) {
                            }
                            openSpeakerOn();
                            ((TextView) findViewById(R.id.tv_is_p2p)).setText(EMClient.getInstance().callManager().isDirectCall()
                                    ? R.string.direct_call : R.string.relay_call);
                            handsFreeImage.setImageResource(R.drawable.em_icon_speaker_on);
                            isHandsfreeState = true;
                            isInCalling = true;
                            chronometer.setVisibility(View.VISIBLE);
                            chronometer.setBase(SystemClock.elapsedRealtime());
                            // call durations start
                            chronometer.start();
                            nickTextView.setVisibility(View.INVISIBLE);
                            callStateTextView.setText(R.string.In_the_call);
//                            recordBtn.setVisibility(View.VISIBLE);
                            callingState = CallingState.NORMAL;
                            startMonitor();
                            makeOngoingStatus();
                            switchLocalToSmall();
                            // Start to watch the phone call state.
                            PhoneStateManager.get(mContext).addStateCallback(phoneStateCallback);

                            break;
                        case NETWORK_DISCONNECTED:
                            netwrokStatusVeiw.setVisibility(View.VISIBLE);
                            netwrokStatusVeiw.setText(R.string.network_unavailable);
                            break;
                        case NETWORK_UNSTABLE:
                            netwrokStatusVeiw.setVisibility(View.VISIBLE);
                            if (error == CallError.ERROR_NO_DATA) {
                                netwrokStatusVeiw.setText(R.string.no_call_data);
                            } else {
                                netwrokStatusVeiw.setText(R.string.network_unstable);
                            }
                            break;
                        case NETWORK_NORMAL:
                            netwrokStatusVeiw.setVisibility(View.INVISIBLE);
                            break;
                        case VIDEO_PAUSE:
                            Toast.makeText(mContext.getApplicationContext(), "VIDEO_PAUSE", Toast.LENGTH_SHORT).show();
                            break;
                        case VIDEO_RESUME:
                            Toast.makeText(mContext.getApplicationContext(), "VIDEO_RESUME", Toast.LENGTH_SHORT).show();

                            break;
                        case VOICE_PAUSE:
                            Toast.makeText(mContext.getApplicationContext(), "VOICE_PAUSE", Toast.LENGTH_SHORT).show();

                            break;
                        case VOICE_RESUME:
                            Toast.makeText(mContext.getApplicationContext(), "VOICE_RESUME", Toast.LENGTH_SHORT).show();

                            break;
                        case DISCONNECTED: // call is disconnected
                            handler.removeCallbacks(timeoutHangup);
                            @SuppressWarnings("UnnecessaryLocalVariable") final CallError fError = error;

                            chronometer.stop();
                            callDruationText = chronometer.getText().toString();
                            String s1 = getResources().getString(R.string.The_other_party_refused_to_accept);
                            String s2 = getResources().getString(R.string.Connection_failure);
                            String s3 = getResources().getString(R.string.The_other_party_is_not_online);
                            String s4 = getResources().getString(R.string.The_other_is_on_the_phone_please);
                            String s5 = getResources().getString(R.string.The_other_party_did_not_answer);

                            String s6 = getResources().getString(R.string.has_been_hang_up);
                            String s7 = getResources().getString(R.string.The_other_is_hang_up);
                            String s8 = getResources().getString(R.string.did_not_answer);
                            String s9 = getResources().getString(R.string.Has_been_cancelled);
                            String s10 = getResources().getString(R.string.Refused);
                            String st12 = "service not enable";
                            String st13 = "service arrearages";
                            String st14 = "service forbidden";

                            if (fError == CallError.REJECTED) {
                                callingState = CallingState.BEREFUSED;
                                callStateTextView.setText(s1);
                            } else if (fError == CallError.ERROR_TRANSPORT) {
                                callStateTextView.setText(s2);
                            } else if (fError == CallError.ERROR_UNAVAILABLE) {
                                callingState = CallingState.OFFLINE;
                                callStateTextView.setText(s3);
                            } else if (fError == CallError.ERROR_BUSY) {
                                callingState = CallingState.BUSY;
                                callStateTextView.setText(s4);
                            } else if (fError == CallError.ERROR_NORESPONSE) {
                                callingState = CallingState.NO_RESPONSE;
                                callStateTextView.setText(s5);
                            } else if (fError == CallError.ERROR_LOCAL_SDK_VERSION_OUTDATED || fError == CallError.ERROR_REMOTE_SDK_VERSION_OUTDATED) {
                                callingState = CallingState.VERSION_NOT_SAME;
                                callStateTextView.setText(R.string.call_version_inconsistent);
                            } else if (fError == CallError.ERROR_SERVICE_NOT_ENABLE) {
                                callingState = CallingState.SERVICE_NOT_ENABLE;
                                callStateTextView.setText(st12);
                            } else if (fError == CallError.ERROR_SERVICE_ARREARAGES) {
                                callingState = CallingState.SERVICE_ARREARAGES;
                                callStateTextView.setText(st13);
                            } else if (fError == CallError.ERROR_SERVICE_FORBIDDEN) {
                                callingState = CallingState.SERVICE_NOT_ENABLE;
                                callStateTextView.setText(st14);
                            } else {
                                if (isRefused) {
                                    callingState = CallingState.REFUSED;
                                    callStateTextView.setText(s10);
                                } else if (isAnswered) {
                                    callingState = CallingState.NORMAL;
                                    if (endCallTriggerByMe) {
//                                        callStateTextView.setText(s6);
                                    } else {
                                        callStateTextView.setText(s7);
                                    }
                                } else {
                                    if (isInComingCall) {
                                        callingState = CallingState.UNANSWERED;
                                        callStateTextView.setText(s8);
                                    } else {
                                        if (callingState != CallingState.NORMAL) {
                                            callingState = CallingState.CANCELLED;
                                            callStateTextView.setText(s9);
                                        } else {
                                            callStateTextView.setText(s6);
                                        }
                                    }
                                }
                            }
                            Toast.makeText(mContext, callStateTextView.getText(), Toast.LENGTH_SHORT).show();
                            CallFloatWindow.getInstance(DemoApplication.getInstance()).dismiss();
                            postDelayedCloseMsg();
                            break;
                    }
                });
            }
        };
        EMClient.getInstance().callManager().addCallStateChangeListener(callStateListener);
    }

    private void postDelayedCloseMsg() {
        uiHandler.postDelayed(new Runnable() {

            @Override
            public void run() {
                removeCallStateListener();

                // Stop to watch the phone call state.
                PhoneStateManager.get(mContext).removeStateCallback(phoneStateCallback);

                saveCallRecord();
                Animation animation = new AlphaAnimation(1.0f, 0.0f);
                animation.setDuration(1200);
                rootContainer.startAnimation(animation);
                mContext.finish();
            }

        }, 200);
    }

    /**
     * for debug & testing, you can remove this when release
     */
    void startMonitor(){
        monitor = true;
        EMCallSession callSession = EMClient.getInstance().callManager().getCurrentCallSession();
        final boolean isRecord = callSession.isRecordOnServer();
        final String serverRecordId = callSession.getServerRecordId();

        EMLog.e(TAG, "server record: " + isRecord);
        if (isRecord) {
            EMLog.e(TAG, "server record id: " + serverRecordId);
        }
        String recordString = " record? " + isRecord + " id: " + serverRecordId;
        if(isRecord) {
            recordString += " id: " + serverRecordId;
        }
        String recordStr = recordString;
        new Thread(new Runnable() {
            public void run() {
                while(monitor){
                    if(isActivityDisable()) {
                        return;
                    }
                    mContext.runOnUiThread(new Runnable() {
                        public void run() {
                            monitorTextView.setText("WidthxHeight："+callHelper.getVideoWidth()+"x"+callHelper.getVideoHeight()
                                    + "\nDelay：" + callHelper.getVideoLatency()
                                    + "\nFramerate：" + callHelper.getVideoFrameRate()
                                    + "\nLost：" + callHelper.getVideoLostRate()
                                    + "\nLocalBitrate：" + callHelper.getLocalBitrate()
                                    + "\nRemoteBitrate：" + callHelper.getRemoteBitrate()
                                    + "\n" + recordStr);

                            ((TextView)findViewById(R.id.tv_is_p2p)).setText(EMClient.getInstance().callManager().isDirectCall()
                                    ? R.string.direct_call : R.string.relay_call);
                        }
                    });
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }, "CallMonitor").start();
    }

    void removeCallStateListener() {
        EMClient.getInstance().callManager().removeCallStateChangeListener(callStateListener);
    }

    PhoneStateManager.PhoneStateCallback phoneStateCallback = new PhoneStateManager.PhoneStateCallback() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:   // 电话响铃
                    break;
                case TelephonyManager.CALL_STATE_IDLE:      // 电话挂断
                    // resume current voice conference.
                    if (isMuteState) {
                        try {
                            EMClient.getInstance().callManager().resumeVoiceTransfer();
                        } catch (HyphenateException e) {
                            e.printStackTrace();
                        }
                        try {
                            EMClient.getInstance().callManager().resumeVideoTransfer();
                        } catch (HyphenateException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:   // 来电接通 或者 去电，去电接通  但是没法区分
                    // pause current voice conference.
                    if (!isMuteState) {
                        try {
                            EMClient.getInstance().callManager().pauseVoiceTransfer();
                        } catch (HyphenateException e) {
                            e.printStackTrace();
                        }
                        try {
                            EMClient.getInstance().callManager().pauseVideoTransfer();
                        } catch (HyphenateException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
            }
        }
    };

    public void onNewIntent(Intent intent) {
        if(surfaceState == 0 ){
            EMClient.getInstance().callManager().setSurfaceView(localSurface,oppositeSurface);
        }else{
            EMClient.getInstance().callManager().setSurfaceView(oppositeSurface,localSurface);
        }

        // 防止activity在后台被start至前台导致window还存在
        CallFloatWindow.getInstance(DemoApplication.getInstance()).dismiss();
    }

    /**
     * 检查是否有悬浮框权限
     * @return
     */
    private boolean isHaveOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(mContext);
        }
        return true;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            if (Math.abs(x) > 3 || Math.abs(y) > 3) {
                if (Math.abs(x) > Math.abs(y)) {
                    mCameraRenderer.getFURenderer().onDeviceOrientationChanged(x > 0 ? 0 : 180);
                } else {
                    mCameraRenderer.getFURenderer().onDeviceOrientationChanged(y > 0 ? 90 : 270);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
