package com.hyphenate.easeui;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import com.hyphenate.chat.EMClient;
import com.hyphenate.chat.EMMessage;
import com.hyphenate.chat.EMOptions;
import com.hyphenate.easeui.domain.EaseAvatarOptions;
import com.hyphenate.easeui.manager.EaseChatPresenter;
import com.hyphenate.easeui.model.EaseNotifier;
import com.hyphenate.easeui.provider.EaseEmojiconInfoProvider;
import com.hyphenate.easeui.provider.EaseSettingsProvider;
import com.hyphenate.easeui.provider.EaseUserProfileProvider;

public class EaseUI {
    private static final String TAG = EaseUI.class.getSimpleName();
    private static EaseUI instance;

    private EaseSettingsProvider settingsProvider;

    private EaseEmojiconInfoProvider mEmojiconInfoProvider;

    private EaseUserProfileProvider userProvider;
    /**
     * chat avatar options which we can easily control the style
     */
    private EaseAvatarOptions avatarOptions;
    /**
     * application context
     */
    private Context appContext = null;
    /**
     * the notifier
     */
    private EaseNotifier notifier = null;

    /**
     * init flag: test if the sdk has been inited before, we don't need to init again
     */
    private boolean sdkInited = false;

    public boolean isVoiceCalling;
    public boolean isVideoCalling;
    private EaseChatPresenter presenter;

    private EaseUI() {}

    public static EaseUI getInstance() {
        if(instance == null) {
            synchronized (EaseUI.class) {
                if(instance == null) {
                    instance = new EaseUI();
                }
            }
        }
        return instance;
    }

    /**
     * 针对应用开启其他进程时，application会执行onCreate的情况，需要检查是否当前是在主进程
     * SDK只在主进程进行初始化一次
     * @param context
     * @return
     */
    public synchronized boolean init(Context context, EMOptions options) {
        if(sdkInited) {
            return true;
        }
        appContext = context.getApplicationContext();
        // if there is application has remote service, application:onCreate() maybe called twice
        // this check is to make sure SDK will initialized only once
        // return if process name is not application's name since the package name is the default process name
        if (!isMainProcess(appContext)) {
            Log.e(TAG, "enter the service process!");
            return false;
        }
        if(options == null) {
            options = initChatOptions();
        }
        EMClient.getInstance().init(context, options);
        initNotifier();
        presenter = new EaseChatPresenter();
        presenter.attachApp(appContext);
        sdkInited = true;
        return true;
    }

    protected EMOptions initChatOptions(){
        Log.d(TAG, "init HuanXin Options");

        EMOptions options = new EMOptions();
        // change to need confirm contact invitation
        options.setAcceptInvitationAlways(false);
        // set if need read ack
        options.setRequireAck(true);
        // set if need delivery ack
        options.setRequireDeliveryAck(false);

        return options;
    }

    /**
     * 判断是否在主进程
     * @param context
     * @return
     */
    public boolean isMainProcess(Context context) {
        int pid = android.os.Process.myPid();
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo appProcess : activityManager.getRunningAppProcesses()) {
            if (appProcess.pid == pid) {
                return context.getApplicationInfo().packageName.equals(appProcess.processName);
            }
        }
        return false;
    }

    private void initNotifier(){
        notifier = new EaseNotifier(appContext);
    }

    public void addChatPresenter(EaseChatPresenter presenter) {
        this.presenter = presenter;
        this.presenter.attachApp(appContext);
    }

    public EaseChatPresenter getChatPresenter() {
        return presenter;
    }

    /**
     * get emojicon provider
     * @return
     */
    public EaseEmojiconInfoProvider getEmojiconInfoProvider() {
        return mEmojiconInfoProvider;
    }

    /**
     * set emojicon provider
     * @param emojiconInfoProvider
     * @return
     */
    public EaseUI setEmojiconInfoProvider(EaseEmojiconInfoProvider emojiconInfoProvider) {
        mEmojiconInfoProvider = emojiconInfoProvider;
        return this;
    }

    /**
     * get settings provider
     * @return
     */
    public EaseSettingsProvider getSettingsProvider() {
        if(settingsProvider == null) {
            settingsProvider = getDefaultSettingsProvider();
        }
        return settingsProvider;
    }

    /**
     * set settting provider
     * @param settingsProvider
     * @return
     */
    public EaseUI setSettingsProvider(EaseSettingsProvider settingsProvider) {
        this.settingsProvider = settingsProvider;
        return this;
    }

    /**
     * get user profile provider
     * @return
     */
    public EaseUserProfileProvider getUserProvider() {
        return userProvider;
    }

    public EaseNotifier getNotifier(){
        return notifier;
    }

    /**
     * set user profile provider
     * @param userProvider
     * @return
     */
    public EaseUI setUserProvider(EaseUserProfileProvider userProvider) {
        this.userProvider = userProvider;
        return this;
    }

    /**
     * get avatar options
     * @return
     */
    public EaseAvatarOptions getAvatarOptions() {
        return avatarOptions;
    }

    /**
     * set avatar options
     * @param avatarOptions
     */
    public EaseUI setAvatarOptions(EaseAvatarOptions avatarOptions) {
        this.avatarOptions = avatarOptions;
        return this;
    }

    public Context getContext() {
        return appContext;
    }

    private EaseSettingsProvider getDefaultSettingsProvider() {
        return new EaseSettingsProvider() {
            @Override
            public boolean isMsgNotifyAllowed(EMMessage message) {
                return false;
            }

            @Override
            public boolean isMsgSoundAllowed(EMMessage message) {
                return false;
            }

            @Override
            public boolean isMsgVibrateAllowed(EMMessage message) {
                return false;
            }

            @Override
            public boolean isSpeakerOpened() {
                return false;
            }
        };
    }
}
