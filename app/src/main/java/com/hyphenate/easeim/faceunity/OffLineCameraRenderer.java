package com.hyphenate.easeim.faceunity;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLES11Ext;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;

import com.faceunity.core.camera.FUCamera;
import com.faceunity.core.camera.FUCameraPreviewData;
import com.faceunity.core.entity.FUCameraConfig;
import com.faceunity.core.enumeration.CameraTypeEnum;
import com.faceunity.core.faceunity.FUAIKit;
import com.faceunity.core.faceunity.FURenderKit;
import com.faceunity.core.faceunity.OffLineRenderHandler;
import com.faceunity.core.listener.OnFUCameraListener;
import com.faceunity.core.model.facebeauty.FaceBeautyBlurTypeEnum;
import com.faceunity.core.utils.GlUtil;
import com.faceunity.nama.FUConfig;
import com.faceunity.nama.FURenderer;
import com.faceunity.nama.data.FaceUnityDataFactory;
import com.faceunity.nama.utils.FuDeviceUtils;
import com.hyphenate.chat.EMClient;
import com.hyphenate.easeim.faceunity.profile.CSVUtils;
import com.hyphenate.easeim.faceunity.profile.Constant;
import com.superrtc.sdk.RtcConnection;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 用户自定义数据采集及数据处理，接入 faceunity 美颜贴纸
 *
 * @author Richie on 2019.12.20
 */
public class OffLineCameraRenderer implements SensorEventListener {
    private Activity mContext;
    private static final String TAG = "OffLineCameraRenderer";
    private CSVUtils mCSVUtils;

    //处理相机的线程
    private CustomGLHandler mBackgroundHandler;

    protected FUCamera mFuCamera;
    protected FUCameraConfig mFUCameraConfig;
    protected int mCameraTextureId;
    protected OnFUCameraListener mOnFUCameraListener;

    protected FURenderer mFURenderer;
    private Object mFURenderInputDataLock = new Object();
    private volatile FUCameraPreviewData mFUCameraPreviewData;
    private SensorManager mSensorManager;
    private HandlerThread mBackgroundThread;
    private FaceUnityDataFactory mFaceUnityDataFactory;

    public OffLineCameraRenderer(Activity activity, FaceUnityDataFactory dataFactory) {
        this.mContext = activity;
        this.mFaceUnityDataFactory = dataFactory;

        mFURenderer = FURenderer.getInstance();
        mFURenderer.setup(mContext);
//        mFURenderer.setReadBackSync(true);
        mFuCamera = FUCamera.getInstance();
        mFUCameraConfig = new FUCameraConfig();
        mFUCameraConfig.isHighestRate = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mFUCameraConfig.setCameraType(CameraTypeEnum.CAMERA2);
        } else {
            mFUCameraConfig.setCameraType(CameraTypeEnum.CAMERA1);
        }

        //设置环信接收外部视频源
        EMClient.getInstance().callManager().getCallOptions().setEnableExternalVideoData(true);

        mOnFUCameraListener = previewData -> {
            //下面这些需要在GL线程
            synchronized (mFURenderInputDataLock) {
                mFUCameraPreviewData = previewData;
            }
            requestRender();
        };

        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);

        //性能相关
        initCsvUtil(mContext);
    }

    public FURenderer getFURenderer() {
        return mFURenderer;
    }

    public void openCamera() {
        startBackgroundThread();
        mBackgroundHandler.post(() -> {
            //保证线程GL环境！！
            mFURenderer.createEGLContext();
            mCameraTextureId = GlUtil.createTextureObject(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
            mFuCamera.openCamera(mFUCameraConfig, mCameraTextureId, mOnFUCameraListener);
        });
    }

    public void closeCamera() {
        if (mBackgroundHandler != null) {
            mBackgroundHandler.removeCallbacksAndMessages(0);
            mBackgroundHandler.post(() -> {
                mFuCamera.closeCamera();
                mCSVUtils.close();
                mFURenderer.releaseEGLContext();
                mFURenderer.release();
            });

            stopBackgroundThread();
        }
    }

    public void onResume() {
        Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void onPause() {
        mSensorManager.unregisterListener(this);
    }

    public void onDestroy() {
//        mFURenderer.release();
    }

    /**
     * 切换相机
     */
    public void switchCamera() {
        Log.d(TAG, "switchCamera: ");
        mBackgroundHandler.post(() -> mFuCamera.switchCamera());
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("camera_thread");
        mBackgroundThread.start();
        mBackgroundHandler = new CustomGLHandler(mBackgroundThread.getLooper(),this);
    }

    private void stopBackgroundThread() {
        if (mBackgroundThread == null) {
            return;
        }
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void initCsvUtil(Context context) {
        mCSVUtils = new CSVUtils(context);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
        String dateStrDir = format.format(new Date(System.currentTimeMillis()));
        dateStrDir = dateStrDir.replaceAll("-", "").replaceAll("_", "");
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault());
        String dateStrFile = df.format(new Date());
        String filePath = Constant.filePath + dateStrDir + File.separator + "excel-" + dateStrFile + ".csv";
        Log.d(TAG, "initLog: CSV file path:" + filePath);
        StringBuilder headerInfo = new StringBuilder();
        headerInfo.append("version：").append(mFURenderer.getVersion()).append(CSVUtils.COMMA)
                .append("机型：").append(android.os.Build.MANUFACTURER).append(android.os.Build.MODEL)
                .append("处理方式：Texture").append(CSVUtils.COMMA);
        mCSVUtils.initHeader(filePath, headerInfo);
    }

    public void onDrawFrame() {
        try {
            mFuCamera.getSurfaceTexture().updateTexImage();
        } catch (Exception e) {
            e.printStackTrace();
        }
        FUCameraPreviewData fuCameraPreviewData;
        synchronized (mFURenderInputDataLock) {
            if (mFUCameraPreviewData == null || mFUCameraPreviewData.getBuffer() == null) {
                return;
            }
            fuCameraPreviewData = new FUCameraPreviewData(mFUCameraPreviewData.getBuffer(), mFUCameraPreviewData.getCameraFacing(), mFUCameraPreviewData.getCameraOrientation(), mFUCameraPreviewData.getWidth(), mFUCameraPreviewData.getHeight());
        }

        if (fuCameraPreviewData == null || fuCameraPreviewData.getBuffer() == null)
            return;

        if (FUConfig.DEVICE_LEVEL > FuDeviceUtils.DEVICE_LEVEL_MID) {
            cheekFaceNum();
        }

        int cameraOrientation = fuCameraPreviewData.getCameraOrientation();
        mFURenderer.setInputOrientation(cameraOrientation);
        long start = System.nanoTime();
        mFaceUnityDataFactory.bindCurrentRenderer();
        byte[] data;
        if (mFaceUnityDataFactory.isMakeupLoaded()) {
            data = mFURenderer.onDrawFrameDoubleInput(0, fuCameraPreviewData.getBuffer(), fuCameraPreviewData.getWidth(), fuCameraPreviewData.getHeight());
        }else {
            data = mFURenderer.onDrawFrameDoubleInput(mCameraTextureId, fuCameraPreviewData.getBuffer(), fuCameraPreviewData.getWidth(), fuCameraPreviewData.getHeight());
        }
//        byte[] data = mFURenderer.onDrawFrameSingleInput(fuCameraPreviewData.getBuffer(), fuCameraPreviewData.getWidth(), fuCameraPreviewData.getHeight());
        if (mCSVUtils != null) {
            long renderTime = System.nanoTime() - start;
            mCSVUtils.writeCsv(null, renderTime);
        }
        EMClient.getInstance().callManager().inputExternalVideoData(data,
                RtcConnection.FORMAT.NV21, fuCameraPreviewData.getWidth(),
                fuCameraPreviewData.getHeight(), fuCameraPreviewData.getWidth(), fuCameraPreviewData.getHeight(), cameraOrientation);
    }

    private void cheekFaceNum() {
        //根据有无人脸 + 设备性能 判断开启的磨皮类型
        float faceProcessorGetConfidenceScore = FUAIKit.getInstance().getFaceProcessorGetConfidenceScore(0);
        if (faceProcessorGetConfidenceScore >= 0.95) {
            //高端手机并且检测到人脸开启均匀磨皮，人脸点位质
            if (FURenderKit.getInstance().getFaceBeauty() != null && FURenderKit.getInstance().getFaceBeauty().getBlurType() != FaceBeautyBlurTypeEnum.EquallySkin) {
                FURenderKit.getInstance().getFaceBeauty().setBlurType(FaceBeautyBlurTypeEnum.EquallySkin);
                FURenderKit.getInstance().getFaceBeauty().setEnableBlurUseMask(true);
            }
        } else {
            if (FURenderKit.getInstance().getFaceBeauty() != null && FURenderKit.getInstance().getFaceBeauty().getBlurType() != FaceBeautyBlurTypeEnum.FineSkin) {
                FURenderKit.getInstance().getFaceBeauty().setBlurType(FaceBeautyBlurTypeEnum.FineSkin);
                FURenderKit.getInstance().getFaceBeauty().setEnableBlurUseMask(false);
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            if (Math.abs(x) > 3 || Math.abs(y) > 3) {
                if (Math.abs(x) > Math.abs(y)) {
                    mFURenderer.setDeviceOrientation(x > 0 ? 0 : 180);
                } else {
                    mFURenderer.setDeviceOrientation(y > 0 ? 90 : 270);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private static final int RENDER_WHAT = 999;

    /**
     * 触发Render 刷新 回调onDrawFrame
     */
    private void requestRender() {
        Message msg = new Message();
        msg.what = RENDER_WHAT;
        if (mBackgroundHandler != null) {
            mBackgroundHandler.removeMessages(RENDER_WHAT);
            mBackgroundHandler.sendMessage(msg);
        }
    }

    private static class CustomGLHandler extends Handler {
        private WeakReference<OffLineCameraRenderer> weakReference;
        public CustomGLHandler(@NonNull Looper looper,OffLineCameraRenderer offLineCameraRenderer) {
            super(looper);
            weakReference = new WeakReference<>(offLineCameraRenderer);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            OffLineCameraRenderer offLineRenderHandler = weakReference.get();
            if (msg != null) {
                if (msg.what == RENDER_WHAT) {
                    offLineRenderHandler.onDrawFrame();
                }
            }
        }
    }
}
