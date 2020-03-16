package com.hyphenate.chatuidemo.faceunity;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;

import com.hyphenate.chatuidemo.DemoApplication;
import com.hyphenate.chatuidemo.R;
import com.hyphenate.chatuidemo.ui.SplashActivity;

public class NeedFaceUnityAcct extends FragmentActivity {

    //是否使用 FaceUnity 美颜
    private boolean mIsOn = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_faceunity);

        final Button button = (Button) findViewById(R.id.btn_set);
        String isOpen = PreferenceUtil.getString(DemoApplication.getInstance(), PreferenceUtil.KEY_FACEUNITY_ISON);
        if (TextUtils.isEmpty(isOpen) || PreferenceUtil.OFF.equals(isOpen)) {
            mIsOn = false;
        } else {
            mIsOn = true;
        }
        button.setText(mIsOn ? "On" : "Off");

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsOn = !mIsOn;
                button.setText(mIsOn ? "On" : "Off");
            }
        });

        Button btnToMain = (Button) findViewById(R.id.btn_to_main);
        btnToMain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(NeedFaceUnityAcct.this, SplashActivity.class);
                PreferenceUtil.persistString(DemoApplication.getInstance(), PreferenceUtil.KEY_FACEUNITY_ISON,
                        mIsOn ? PreferenceUtil.ON : PreferenceUtil.OFF);
                startActivity(intent);
                finish();
            }
        });

    }
}
