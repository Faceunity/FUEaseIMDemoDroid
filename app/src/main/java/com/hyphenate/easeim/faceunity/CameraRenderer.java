package com.hyphenate.easeim.faceunity;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import com.faceunity.nama.FURenderer;
import com.faceunity.nama.IFURenderer;
import com.hyphenate.chat.EMClient;
import com.hyphenate.easeim.faceunity.profile.CSVUtils;
import com.hyphenate.easeim.faceunity.profile.Constant;
import com.superrtc.sdk.RtcConnection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 用户自定义数据采集及数据处理，接入 faceunity 美颜贴纸
 *
 * @author Richie on 2019.12.20
 */
public class CameraRenderer implements Camera.PreviewCallback {
    private static final String TAG = "CameraRenderer";
    private static final int DEFAULT_CAMERA_WIDTH = 1280;
    private static final int DEFAULT_CAMERA_HEIGHT = 720;
    private static final int PREVIEW_BUFFER_COUNT = 3;
    private Activity mActivity;
    private Camera mCamera;
    private byte[][] mPreviewCallbackBuffer;
    private int mCameraWidth = DEFAULT_CAMERA_WIDTH;
    private int mCameraHeight = DEFAULT_CAMERA_HEIGHT;
    private int mCameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private int mCameraOrientation = 270;
    private int mCameraTextureId;
    private SurfaceTexture mSurfaceTexture;
    private boolean mIsPreviewing;
    private Handler mBackgroundHandler;
    private Handler mPosterHandler;
    private byte[] mReadbackByte;
    private int mSkippedFrames;
    private FURenderer mFURenderer;
    private CSVUtils mCSVUtils;

    public CameraRenderer(Activity activity, FURenderer.OnDebugListener listener) {
        mActivity = activity;
        EMClient.getInstance().callManager().getCallOptions().setEnableExternalVideoData(true);
        FURenderer.setup(activity);
        mFURenderer = new FURenderer.Builder(activity)
                .setCreateEglContext(true)
                .setInputTextureType(IFURenderer.INPUT_TEXTURE_EXTERNAL_OES)
                .setCameraFacing(mCameraFacing)
                .setRunBenchmark(true)
                .setOnDebugListener(listener)
                .setInputImageOrientation(CameraUtils.getCameraOrientation(mCameraFacing))
                .build();
    }

    public FURenderer getFURenderer() {
        return mFURenderer;
    }

    public void onResume() {
        startBackgroundThread();
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                mFURenderer.onSurfaceCreated();
                initCsvUtil(mActivity);
                mCameraTextureId = GlUtil.createTextureObject(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
                openCamera(mCameraFacing);
                startPreview();
            }
        });
    }

    public void onPause() {
        if (mBackgroundHandler == null) {
            return;
        }
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                releaseCamera();
                if (mCameraTextureId > 0) {
                    GLES20.glDeleteTextures(1, new int[]{mCameraTextureId}, 0);
                    mCameraTextureId = 0;
                }
                mFURenderer.onSurfaceDestroyed();
                mCSVUtils.close();
            }
        });
        stopBackgroundThread();
    }

    /**
     * 切换相机
     */
    public void switchCamera() {
        Log.d(TAG, "switchCamera: ");
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                boolean isFront = mCameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT;
                mCameraFacing = isFront ? Camera.CameraInfo.CAMERA_FACING_BACK : Camera.CameraInfo.CAMERA_FACING_FRONT;
                releaseCamera();
                mSkippedFrames = 3;
                openCamera(mCameraFacing);
                startPreview();
                mFURenderer.onCameraChanged(mCameraFacing, mCameraOrientation);
                if (mFURenderer.getMakeupModule() != null) {
                    mFURenderer.getMakeupModule().setIsMakeupFlipPoints(mCameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT ? 0 : 1);
                }
            }
        });
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        mCamera.addCallbackBuffer(data);
        mSurfaceTexture.updateTexImage();
        long start = System.nanoTime();
        mFURenderer.onDrawFrameDualInput(data, mCameraTextureId, mCameraWidth, mCameraHeight,
                mReadbackByte, mCameraWidth, mCameraHeight);
        long time = System.nanoTime() - start;
        mCSVUtils.writeCsv(null, time);
        if (mSkippedFrames > 0) {
            mSkippedFrames--;
        } else {
            Log.e(TAG, "onPreviewFrame: " + mReadbackByte.length);
            if (mPosterHandler != null) {
                mPosterHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        EMClient.getInstance().callManager().inputExternalVideoData(mReadbackByte,
                                RtcConnection.FORMAT.NV21, mCameraWidth, mCameraHeight, mCameraWidth, mCameraHeight, mCameraOrientation);
                    }
                });
            }

        }
    }

    private void openCamera(int cameraFacing) {
        try {
            Camera.CameraInfo info = new Camera.CameraInfo();
            int cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
            int numCameras = Camera.getNumberOfCameras();
            if (numCameras <= 0) {
                throw new RuntimeException("No cameras");
            }
            for (int i = 0; i < numCameras; i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == cameraFacing) {
                    cameraId = i;
                    mCamera = Camera.open(i);
                    mCameraFacing = cameraFacing;
                    break;
                }
            }
            if (mCamera == null) {
                cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
                Camera.getCameraInfo(cameraId, info);
                mCamera = Camera.open(cameraId);
                mCameraFacing = cameraId;
            }

            mCameraOrientation = info.orientation;
            CameraUtils.setCameraDisplayOrientation(mActivity, cameraId, mCamera);
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewFormat(ImageFormat.NV21);
            int[] size = CameraUtils.choosePreviewSize(parameters, mCameraWidth, mCameraHeight);
            mCameraWidth = size[0];
            mCameraHeight = size[1];
            mCamera.setParameters(parameters);
            Log.d(TAG, "openCamera. facing: " + (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK
                    ? "back" : "front") + ", orientation:" + mCameraOrientation + ", cameraWidth:" + mCameraWidth
                    + ", cameraHeight:" + mCameraHeight);
        } catch (Exception e) {
            Log.e(TAG, "openCamera: ", e);
            releaseCamera();
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mActivity, "打开相机失败", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void startPreview() {
        if (mCameraTextureId <= 0 || mCamera == null || mIsPreviewing) {
            return;
        }
        try {
            mCamera.stopPreview();
            if (mPreviewCallbackBuffer == null) {
                mPreviewCallbackBuffer = new byte[PREVIEW_BUFFER_COUNT][mCameraWidth * mCameraHeight
                        * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8];
            }
            if (mReadbackByte == null) {
                mReadbackByte = new byte[mCameraWidth * mCameraHeight * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8];
            }
            mCamera.setPreviewCallbackWithBuffer(this);
            for (int i = 0; i < PREVIEW_BUFFER_COUNT; i++) {
                mCamera.addCallbackBuffer(mPreviewCallbackBuffer[i]);
            }
            mSurfaceTexture = new SurfaceTexture(mCameraTextureId);
            mCamera.setPreviewTexture(mSurfaceTexture);
            mCamera.startPreview();
            mIsPreviewing = true;
            Log.d(TAG, "startPreview: cameraTexId:" + mCameraTextureId);
        } catch (Exception e) {
            Log.e(TAG, "startPreview: ", e);
        }
    }

    private void releaseCamera() {
        Log.d(TAG, "releaseCamera()");
        try {
            mIsPreviewing = false;
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.setPreviewTexture(null);
                mCamera.setPreviewCallbackWithBuffer(null);
                mCamera.release();
                mCamera = null;
            }
            if (mSurfaceTexture != null) {
                mSurfaceTexture.release();
                mSurfaceTexture = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "releaseCamera: ", e);
        }
    }

    private void startBackgroundThread() {
        HandlerThread backgroundThread = new HandlerThread("camera_thread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        mBackgroundHandler = new Handler(backgroundThread.getLooper());

        HandlerThread posterThread = new HandlerThread("poster", Process.THREAD_PRIORITY_BACKGROUND);
        posterThread.start();
        mPosterHandler = new Handler(posterThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundHandler.getLooper().quitSafely();
        mBackgroundHandler = null;

        mPosterHandler.getLooper().quitSafely();
        mPosterHandler = null;
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
        headerInfo.append("version：").append(FURenderer.getVersion()).append(CSVUtils.COMMA)
                .append("机型：").append(android.os.Build.MANUFACTURER).append(android.os.Build.MODEL)
                .append("处理方式：Texture").append(CSVUtils.COMMA);
        mCSVUtils.initHeader(filePath, headerInfo);
    }

}
