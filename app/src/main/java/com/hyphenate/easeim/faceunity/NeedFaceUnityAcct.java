package com.hyphenate.easeim.faceunity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;

import com.hyphenate.easeim.DemoApplication;
import com.hyphenate.easeim.R;
import com.hyphenate.easeim.faceunity.utils.PreferenceUtil;
import com.hyphenate.easeim.section.login.activity.SplashActivity;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;


public class NeedFaceUnityAcct extends AppCompatActivity {

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
