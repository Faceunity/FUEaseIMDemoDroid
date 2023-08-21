package com.hyphenate.easeim.section.chat.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hyphenate.EMCallBack;
import com.hyphenate.EMError;
import com.hyphenate.chat.EMCallManager;
import com.hyphenate.chat.EMCallStateChangeListener;
import com.hyphenate.chat.EMClient;
import com.hyphenate.chat.EMConferenceStream;
import com.hyphenate.chat.EMConversation;
import com.hyphenate.chat.EMMessage;
import com.hyphenate.chat.EMMirror;
import com.hyphenate.chat.EMTextMessageBody;
import com.hyphenate.chat.EMWaterMarkOption;
import com.hyphenate.chat.EMWaterMarkPosition;
import com.hyphenate.easeim.DemoApplication;
import com.hyphenate.easeim.R;
import com.hyphenate.easeim.common.constant.DemoConstant;
import com.hyphenate.easeim.common.livedatas.LiveDataBus;
import com.hyphenate.easeim.common.manager.FloatWindowManager;
import com.hyphenate.easeim.common.utils.PreferenceManager;
import com.hyphenate.easeim.faceunity.OffLineCameraRenderer;
import com.hyphenate.easeim.section.base.BaseFragment;
import com.hyphenate.easeim.section.conference.CallFloatWindow;
import com.hyphenate.easeim.section.dialog.CompleteDialogFragment;
import com.hyphenate.easeim.section.dialog.DemoDialogFragment;
import com.hyphenate.easeui.constants.EaseConstant;
import com.hyphenate.easeui.manager.EasePreferenceManager;
import com.hyphenate.exceptions.EMServiceNotReadyException;
import com.hyphenate.util.EMLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;

public class EaseCallFragment extends BaseFragment {
    public final static String TAG = "CallActivity";
    protected final int MSG_CALL_MAKE_VIDEO = 0;
    protected final int MSG_CALL_MAKE_VOICE = 1;
    protected final int MSG_CALL_ANSWER = 2;
    protected final int MSG_CALL_REJECT = 3;
    protected final int MSG_CALL_END = 4;
    protected final int MSG_CALL_RELEASE_HANDLER = 5;
    protected final int MSG_CALL_SWITCH_CAMERA = 6;
    protected final int REQUEST_CODE_OVERLAY_PERMISSION = 1006;

    protected boolean isInComingCall;
    protected boolean isRefused = false;
    protected String username;
    protected CallingState callingState = CallingState.CANCELLED;
    protected String callDruationText;
    protected String msgid;
    protected AudioManager audioManager;
    protected SoundPool soundPool;
    protected Ringtone ringtone;
    protected int outgoing;
    protected EMCallStateChangeListener callStateListener;
    protected boolean isAnswered = false;
    protected int streamID = -1;
    protected int floatState;

    EMCallManager.EMCallPushProvider pushProvider;

    private Bitmap watermarkbitmap;
    private EMWaterMarkOption watermark;
    // 正在显示音视频Window的stream
    protected static EMConferenceStream windowStream;
    //用于防止多次打开请求悬浮框页面
    protected boolean requestOverlayPermission;
    //用于判断fragment是否执行过onBackPressed()方法
    public boolean isBackPress;

    /**
     * 0：voice call，1：video call
     */
    protected int callType = 0;

    //相机相关配置
    protected OffLineCameraRenderer mOffLineCameraRenderer;

    HandlerThread callHandlerThread = new HandlerThread("callHandlerThread");
    { callHandlerThread.start(); }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        pushProvider = new EMCallManager.EMCallPushProvider() {

            void updateMessageText(final EMMessage oldMsg, final String to) {
                // update local message text
                EMConversation conv = EMClient.getInstance().chatManager().getConversation(oldMsg.getTo());
                conv.removeMessage(oldMsg.getMsgId());
            }

            @Override
            public void onRemoteOffline(final String to) {

                //this function should exposed & move to Demo
                EMLog.d(TAG, "onRemoteOffline, to:" + to);
                String content = getString(com.hyphenate.easeim.R.string.push_incoming_call);
                final EMMessage message = EMMessage.createTxtSendMessage(content, to);
                // set the user-defined extension field
                // 设置ios端铃声文件及开启apns通知扩展
                JSONObject apns = new JSONObject();
                try {
                    //设置ios端自定义铃声文件
                    apns.put("em_push_sound", "ring.caf");
                    //对于华为EMUI 10以上系统需要设置以下参数，否则容易被华为通知智能分类分到营销通知渠道，从而不能播放自定义铃声
                    apns.put("em_push_name", content);
                    apns.put("em_push_content", content);
                    //保证 APNs 通知扩展
                    apns.put("em_push_mutable_content", true);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                message.setAttribute("em_apns_ext", apns);
                //设置呼叫类型
                message.setAttribute("is_voice_call", callType == 0);

                JSONObject extObject = new JSONObject();
                try {
                    extObject.put("type", "call");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                message.setAttribute("em_push_ext", extObject);

                //设置自定义铃声，"/raw/ring"为应用"/res/raw/**",ring为铃声文件名（不包含后缀）
                //若需更换铃声，请将铃声拷贝到"/res/raw/"目录下，并将下面的"ring"更换成相应的文件名即可
                JSONObject sound = new JSONObject();
                try {
                    //指定自定义渠道
                    sound.put("em_push_channel_id", "hyphenate_offline_push_notification");
                    sound.put("em_push_sound", "/raw/ring");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                message.setAttribute("em_android_push_ext", sound);
                //强制推送，忽略消息免打扰设置
                message.setAttribute("em_force_notification", true);
                message.setMessageStatusCallback(new EMCallBack(){

                    @Override
                    public void onSuccess() {
                        EMLog.d(TAG, "onRemoteOffline success");
                        updateMessageText(message, to);
                    }

                    @Override
                    public void onError(int code, String error) {
                        EMLog.d(TAG, "onRemoteOffline Error");
                        updateMessageText(message, to);
                    }

                    @Override
                    public void onProgress(int progress, String status) {
                    }
                });
                // send messages
                EMClient.getInstance().chatManager().sendMessage(message);
            }
        };

        EMClient.getInstance().callManager().setPushProvider(pushProvider);


        if(PreferenceManager.getInstance().isWatermarkResolution()) {
            try {
                InputStream in = this.getResources().getAssets().open("watermark.png");
                watermarkbitmap = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                e.printStackTrace();
            }
            watermark = new EMWaterMarkOption(watermarkbitmap, 75, 25, EMWaterMarkPosition.TOP_RIGHT, 8, 8);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initArguments();
        initView();
        initListener();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initData();
    }

    protected void initArguments() {

    }

    protected void initView() {

    }

    protected void initListener() {

    }

    protected void initData() {

    }

    @Override
    public void onResume() {
        super.onResume();
        //如果去申请悬浮框权限，则在页面可见时检查是否授予权限了
        if(requestOverlayPermission) {
            requestOverlayPermission = false;
            // Result of window permission request, resultCode = RESULT_CANCELED
            if (FloatWindowManager.checkPermission(mContext)) {
                doShowFloatWindow();
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        // 用于判断app是否进入后台
        if(mContext != null && !mContext.isFinishing() && !DemoApplication.getInstance().getLifecycleCallbacks().isOnForeground()) {
            showFloatWindow();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (soundPool != null)
            soundPool.release();
        if (ringtone != null && ringtone.isPlaying())
            ringtone.stop();
        audioManager.setMode(AudioManager.MODE_NORMAL);
        audioManager.setMicrophoneMute(false);

        if(callStateListener != null)
            EMClient.getInstance().callManager().removeCallStateChangeListener(callStateListener);

        if (pushProvider != null) {
            EMClient.getInstance().callManager().setPushProvider(null);
            pushProvider = null;
        }
        releaseHandler();
    }

    @Override
    public void onBackPress() {
        EMLog.d(TAG, "onBackPressed");
        isBackPress = true;
        handler.sendEmptyMessage(MSG_CALL_END);
        saveCallRecord();
        super.onBackPress();
    }

    void releaseHandler() {
        handler.sendEmptyMessage(MSG_CALL_RELEASE_HANDLER);
    }

    Runnable timeoutHangup = new Runnable() {

        @Override
        public void run() {
            handler.sendEmptyMessage(MSG_CALL_END);
        }
    };

    protected Handler handler = new Handler(callHandlerThread.getLooper()) {
        @Override
        public void handleMessage(Message msg) {
            EMLog.d("EMCallManager CallActivity", "handleMessage ---enter block--- msg.what:" + msg.what);
            switch (msg.what) {
                case MSG_CALL_MAKE_VIDEO:
                case MSG_CALL_MAKE_VOICE:
                    try {
                        // TODO: 2020/1/9 0009 此处需要进行修改，文件名称不一致，看看如何更好的提供给开发者
                        boolean record = EasePreferenceManager.getInstance().isRecordOnServer();
                        boolean merge = EasePreferenceManager.getInstance().isMergeStream();
                        if (msg.what == MSG_CALL_MAKE_VIDEO) {
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
                            if (mOffLineCameraRenderer != null) {
                                mOffLineCameraRenderer.openCamera();
                            }
                            EMClient.getInstance().callManager().makeVideoCall(username, "", record, merge);
                        } else {
                            EMClient.getInstance().callManager().makeVoiceCall(username, "", record, merge);
                        }
                    } catch (final EMServiceNotReadyException e) {
                        e.printStackTrace();
                        mContext.runOnUiThread(new Runnable() {
                            public void run() {
                                String st2 = e.getMessage();
                                if (e.getErrorCode() == EMError.CALL_REMOTE_OFFLINE) {
                                    st2 = getResources().getString(R.string.The_other_is_not_online);
                                } else if (e.getErrorCode() == EMError.USER_NOT_LOGIN) {
                                    st2 = getResources().getString(R.string.Is_not_yet_connected_to_the_server);
                                } else if (e.getErrorCode() == EMError.INVALID_USER_NAME) {
                                    st2 = getResources().getString(R.string.illegal_user_name);
                                } else if (e.getErrorCode() == EMError.CALL_BUSY) {
                                    st2 = getResources().getString(R.string.The_other_is_on_the_phone);
                                } else if (e.getErrorCode() == EMError.NETWORK_ERROR) {
                                    st2 = getResources().getString(R.string.can_not_connect_chat_server_connection);
                                }
                                Toast.makeText(mContext, st2, Toast.LENGTH_SHORT).show();
                                mContext.finish();
                            }
                        });
                    }
                    break;
                case MSG_CALL_ANSWER:
                    EMLog.d(TAG, "MSG_CALL_ANSWER");
                    if (ringtone != null)
                        ringtone.stop();
                    if (isInComingCall) {
                        if (mOffLineCameraRenderer != null) {
                            mOffLineCameraRenderer.openCamera();
                        }
                        try {
                            EMClient.getInstance().callManager().answerCall();
                            isAnswered = true;
                            // meizu MX5 4G, hasDataConnection(context) return status is incorrect
                            // MX5 con.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected() return false in 4G
                            // so we will not judge it, App can decide whether judge the network status

//                        if (NetUtils.hasDataConnection(CallActivity.this)) {
//                            EMClient.getInstance().callManager().answerCall();
//                            isAnswered = true;
//                        } else {
//                            runOnUiThread(new Runnable() {
//                                public void run() {
//                                    final String st2 = getResources().getString(R.string.Is_not_yet_connected_to_the_server);
//                                    Toast.makeText(CallActivity.this, st2, Toast.LENGTH_SHORT).show();
//                                }
//                            });
//                            throw new Exception();
//                        }
                        } catch (Exception e) {
                            e.printStackTrace();
                            saveCallRecord();
                            mContext.finish();
                            return;
                        }
                    } else {
                        EMLog.d(TAG, "answer call isInComingCall:false");
                    }
                    break;
                case MSG_CALL_REJECT:
                    if (ringtone != null)
                        ringtone.stop();
                    try {
                        EMClient.getInstance().callManager().rejectCall();
                    } catch (Exception e1) {
                        e1.printStackTrace();
                        saveCallRecord();
                        mContext.finish();
                    }
                    callingState = CallingState.REFUSED;
                    break;
                case MSG_CALL_END:
                    if (soundPool != null)
                        soundPool.stop(streamID);
                    EMLog.d("EMCallManager", "soundPool stop MSG_CALL_END");
                    if (mOffLineCameraRenderer != null) {
                        mOffLineCameraRenderer.closeCamera();
                    }
                    try {
                        EMClient.getInstance().callManager().endCall();
                    } catch (Exception e) {
                        saveCallRecord();
                        mContext.finish();
                    }

                    break;
                case MSG_CALL_RELEASE_HANDLER:
                    if (mOffLineCameraRenderer != null) {
                        mOffLineCameraRenderer.closeCamera();
                    }
                    try {
                        EMClient.getInstance().callManager().endCall();
                    } catch (Exception e) {
                    }
                    handler.removeCallbacks(timeoutHangup);
                    handler.removeMessages(MSG_CALL_MAKE_VIDEO);
                    handler.removeMessages(MSG_CALL_MAKE_VOICE);
                    handler.removeMessages(MSG_CALL_ANSWER);
                    handler.removeMessages(MSG_CALL_REJECT);
                    handler.removeMessages(MSG_CALL_END);
                    callHandlerThread.quit();
                    break;
                case MSG_CALL_SWITCH_CAMERA:
                    EMClient.getInstance().callManager().switchCamera();
                    if (mOffLineCameraRenderer != null) {
                        mOffLineCameraRenderer.switchCamera();
                    }
                    break;
                default:
                    break;
            }
            EMLog.d("EMCallManager CallActivity", "handleMessage ---exit block--- msg.what:" + msg.what);
        }
    };

    /**
     * play the incoming call ringtone
     *
     */
    protected int playMakeCallSounds() {
        try {
            audioManager.setMode(AudioManager.MODE_RINGTONE);
            audioManager.setSpeakerphoneOn(true);

            // play
            int id = soundPool.play(outgoing, // sound resource
                    0.3f, // left volume
                    0.3f, // right volume
                    1,    // priority
                    -1,   // loop，0 is no loop，-1 is loop forever
                    1);   // playback rate (1.0 = normal playback, range 0.5 to 2.0)
            return id;
        } catch (Exception e) {
            return -1;
        }
    }

    protected void openSpeakerOn() {
        try {
            if (!audioManager.isSpeakerphoneOn())
                audioManager.setSpeakerphoneOn(true);
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void closeSpeakerOn() {

        try {
            if (audioManager != null) {
                // int curVolume =
                // audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
                if (audioManager.isSpeakerphoneOn())
                    audioManager.setSpeakerphoneOn(false);
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                // audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                // curVolume, AudioManager.STREAM_VOICE_CALL);

            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void saveCallRecord() {
        @SuppressWarnings("UnusedAssignment") EMMessage message = null;
        @SuppressWarnings("UnusedAssignment") EMTextMessageBody txtBody = null;
        if (!isInComingCall) { // outgoing call
            message = EMMessage.createSendMessage(EMMessage.Type.TXT);
            message.setTo(username);
        } else {
            message = EMMessage.createReceiveMessage(EMMessage.Type.TXT);
            message.setFrom(username);
        }

        String st1 = getResources().getString(R.string.call_duration);
        String st2 = getResources().getString(R.string.Refused);
        String st3 = getResources().getString(R.string.The_other_party_has_refused_to);
        String st4 = getResources().getString(R.string.The_other_is_not_online);
        String st5 = getResources().getString(R.string.The_other_is_on_the_phone);
        String st6 = getResources().getString(R.string.The_other_party_did_not_answer);
        String st7 = getResources().getString(R.string.did_not_answer);
        String st8 = getResources().getString(R.string.Has_been_cancelled);
        String st12 = "service not enable";
        String st13 = "service arrearages";
        String st14 = "service forbidden";
        switch (callingState) {
            case NORMAL:
                txtBody = new EMTextMessageBody(st1 + callDruationText);
                break;
            case REFUSED:
                txtBody = new EMTextMessageBody(st2);
                break;
            case BEREFUSED:
                txtBody = new EMTextMessageBody(st3);
                break;
            case OFFLINE:
                txtBody = new EMTextMessageBody(st4);
                break;
            case BUSY:
                txtBody = new EMTextMessageBody(st5);
                break;
            case NO_RESPONSE:
                txtBody = new EMTextMessageBody(st6);
                break;
            case UNANSWERED:
                txtBody = new EMTextMessageBody(st7);
                break;
            case VERSION_NOT_SAME:
                txtBody = new EMTextMessageBody(getString(R.string.call_version_inconsistent));
                break;
            case SERVICE_ARREARAGES:
                txtBody = new EMTextMessageBody(st13);
                break;
            case SERVICE_NOT_ENABLE:
                txtBody = new EMTextMessageBody(st12);
                break;

            default:
                txtBody = new EMTextMessageBody(st8);
                break;
        }
        // set message extension
        if(callType == 0)
            message.setAttribute(EaseConstant.MESSAGE_ATTR_IS_VOICE_CALL, true);
        else
            message.setAttribute(EaseConstant.MESSAGE_ATTR_IS_VIDEO_CALL, true);

        // set message body
        message.addBody(txtBody);
        message.setMsgId(msgid);
        message.setStatus(EMMessage.Status.SUCCESS);
        message.setUnread(false);

        // save
        EMClient.getInstance().chatManager().saveMessage(message);

        LiveDataBus.get().with(DemoConstant.MESSAGE_CALL_SAVE).postValue(true);
    }


    protected void showFloatWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (FloatWindowManager.checkPermission(mContext)) {
                doShowFloatWindow();
            } else { // To reqire the window permission.
                if(!requestOverlayPermission) {
                    showDialog();
                }
            }
        } else {
            doShowFloatWindow();
        }
    }

    private void showDialog() {
        new CompleteDialogFragment.Builder(mContext)
                .setTitle(R.string.call_no_float_permission)
                .setContent(R.string.call_open_float_permission)
                .setOnConfirmClickListener(new DemoDialogFragment.OnConfirmClickListener() {
                    @Override
                    public void onConfirmClick(View view) {
                        FloatWindowManager.tryJumpToPermissionPage(mContext);
                        requestOverlayPermission = true;
                    }
                })
                .show();

    }

    protected void doShowFloatWindow() {
        CallFloatWindow.getInstance(DemoApplication.getInstance()).show();

        windowStream = new EMConferenceStream();
        windowStream.setUsername(EMClient.getInstance().getCurrentUser());

        CallFloatWindow.getInstance(DemoApplication.getInstance()).updateCallWindow(floatState);
        mContext.moveTaskToBack(false);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        EMLog.i(TAG, "onActivityResult: " + requestCode + ", result code: " + resultCode);
        if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            requestOverlayPermission = false;
            // Result of window permission request, resultCode = RESULT_CANCELED
            if (FloatWindowManager.checkPermission(mContext)) {
                doShowFloatWindow();
            } else {
                Toast.makeText(mContext, getString(com.hyphenate.easeim.R.string.alert_window_permission_denied), Toast.LENGTH_SHORT).show();
            }
        }
    }


    enum CallingState {
        CANCELLED, NORMAL, REFUSED, BEREFUSED, UNANSWERED, OFFLINE, NO_RESPONSE, BUSY, VERSION_NOT_SAME, SERVICE_ARREARAGES, SERVICE_NOT_ENABLE
    }
}
