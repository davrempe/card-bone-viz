package com.informatics.lehigh.cardboneviz;

import android.content.res.Resources;
import android.graphics.Camera;
import android.hardware.camera2.CameraAccessException;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;

import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import com.informatics.lehigh.cardboardarlibrary.StereoCamera;
import com.informatics.lehigh.cardboardarlibrary.GLUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MainActivity extends GvrActivity implements GvrView.StereoRenderer {

    private static final float CAMERA_Z = 0.01f;
    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100.0f;
    private static final String TAG = "MainActivity";

    private float[] camera;

    private GvrView m_gvrView;
    private StereoCamera m_stereoCam;
    private GLUtil m_glutil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        camera = new float[16];
        Resources res = getResources();
        m_stereoCam = new StereoCamera(res);
        m_glutil = new GLUtil(res);

        initializeGvrView();

        try {
            m_stereoCam.initCamera(this);
        } catch(CameraAccessException cae) {
            Log.e(TAG, "COULD NOT ACCESS CAMERA");
            cae.printStackTrace();
        }

    }

    public void initializeGvrView() {
        setContentView(R.layout.common_ui);

        m_gvrView = (GvrView) findViewById(R.id.gvr_view);
        m_gvrView.setEGLConfigChooser(8, 8, 8, 8, 16, 8);

        m_gvrView.setRenderer(this);
        m_gvrView.setTransitionViewEnabled(true);
        m_gvrView.setOnCardboardBackButtonListener(
                new Runnable() {
                    @Override
                    public void run() {
                        onBackPressed();
                    }
                });
        setGvrView(m_gvrView);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onSurfaceChanged(int i, int i1) {
        Log.i(TAG, "onSurfaceChanged");
    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceCreated(EGLConfig eglConfig) {
        Log.i(TAG, "onSurfaceCreated");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up well.

        m_stereoCam.initRenderer();

        m_glutil.checkGLError("onSurfaceCreated");
    }

    @Override
    public void onNewFrame(HeadTransform headTransform) {
        float[] forwardVec = new float[3];
        float[] upVec = new float[3];
        float[] rightVec = new float[3];
        headTransform.getForwardVector(forwardVec, 0);
        headTransform.getUpVector(upVec, 0);
        headTransform.getRightVector(rightVec, 0);
        try {
            m_stereoCam.updateStereoView(rightVec, upVec, forwardVec);
        } catch(CameraAccessException cae) {
            Log.e(TAG, "COULDN'T ACCESS CAMERA ON REFRESH");
        }

        // Build the camera matrix.
        Matrix.setLookAtM(camera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

        m_glutil.checkGLError("onReadyToDraw");
    }

    @Override
    public void onDrawEye(Eye eye) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        m_glutil.checkGLError("colorParam");

        float [] view = new float[16];
        // Apply the eye transformation to the camera.
        Matrix.multiplyMM(view, 0, eye.getEyeView(), 0, camera, 0);
        // Get the perspective matrix
        float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);

        // draw the stereo camera
        m_stereoCam.drawStereoView(view, perspective);
    }

    @Override
    public void onFinishFrame(Viewport viewport) {

    }
}
