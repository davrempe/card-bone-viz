package com.informatics.lehigh.cardboneviz;

import android.os.Bundle;
import android.util.Log;

import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import javax.microedition.khronos.egl.EGLConfig;

import com.informatics.lehigh.cardboardarlibrary.GarUtil;
import com.informatics.lehigh.cardboardarlibrary.GarActivity;

import org.opencv.core.Mat;

public class MainActivity extends GarActivity {

    //
    // CONSTANTS
    //
    private static final String TAG = "MainActivity";
    private static final float PADDING_SIZE = 0.005f;
    private static final float MARKER_SIZE = 0.035f;
    /**The filepath to the Camera Calibration Data file*/
    public static final String DATA_FILEPATH = "/CardBoneViz/camCalibData.csv";

    //
    // PREFERENCES
    //
    /** Render the bone on detected marker */
    private static final boolean DRAW_BONE = true;
    /** Render the axes on detected marker */
    private static final boolean DRAW_AXES = true;
    /** Only render models if the marker is currently detected */
    private static final boolean ONLY_DRAW_WHEN_DETECTED = false;

    //
    // Renderers
    //
    /** Renderer used for the bone models */
    BoneRenderer boneRenderer;
    /** Renderer used for the marker cube axes */
    AxisRenderer axisRenderer;

    //
    // Cube tracking-related members
    //
    /** Image processor to track ultrasound wand location */
    private UltrasoundTracker mUltraTracker;
    /** The thread being used to run ultrasound tracking */
    private Thread mTrackingThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boneRenderer = new BoneRenderer(this);
        axisRenderer = new AxisRenderer(this);

        // initialize ultrasound wand tracker
        mUltraTracker = new UltrasoundTracker(getProcessingReader(), MARKER_SIZE, PADDING_SIZE);
        mTrackingThread = new Thread(mUltraTracker);
        mTrackingThread.start();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
        super.onRendererShutdown();

        //
        // cleanup
        //

        // stop tracking thread
        mUltraTracker.terminate();
        try {
            mTrackingThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "UNABLE TO PROPERLY SHUTDOWN TRACKING THREAD");
        }
    }

    @Override
    public void onSurfaceCreated(EGLConfig eglConfig) {
        super.onSurfaceCreated(eglConfig);
        Log.i(TAG, "onSurfaceCreated");

        if (DRAW_BONE) {
            boneRenderer.init();
        }
        if (DRAW_AXES) {
            axisRenderer.init();
        }
    }

    @Override
    public void onNewFrame(HeadTransform headTransform) {
        super.onNewFrame(headTransform);

        float[] forwardVec = new float[3];
        float[] upVec = new float[3];
        float[] rightVec = new float[3];
        headTransform.getForwardVector(forwardVec, 0);
        headTransform.getUpVector(upVec, 0);
        headTransform.getRightVector(rightVec, 0);

        if (mUltraTracker.isNewMarkerAvailable()) {
            // calculate bone model matrix based on marker location
            Mat tvecMat;
            Mat rvecMat;

            Mat [] params = mUltraTracker.getMarkerParams();
            tvecMat = params[0];
            rvecMat = params[1];

            Log.d(TAG, "TVEC: (" + tvecMat.get(0, 0)[0] + ", " + tvecMat.get(1, 0)[0] + ", " + tvecMat.get(2, 0)[0] + ")");

            float centerCubeTransform[] = new float [16];
            GarUtil.getTransformationFromTrackingParams(centerCubeTransform, tvecMat, rvecMat, headTransform);

            boneRenderer.setCenterCubeTransform(centerCubeTransform);
            axisRenderer.setCenterCubeTransform(centerCubeTransform);

            if (DRAW_BONE){
                boneRenderer.update(headTransform);
                Log.d(TAG, "UPDATED BONE MODEL");
            }
            if (DRAW_AXES) {
                axisRenderer.update(headTransform);
                Log.d(TAG, "UPDATED AXIS MODEL");
            }
        }

        garutil.checkGLError("onReadyToDraw");
    }

    /**
     * Draws the 3D scene to be laid over the current back-facing camera view.
     * These are the augmented portions of the application. This will be called
     * twice per frame, one for the left eye, and again for the right. Anything
     * drawn with an alpha value of 0.0 will be treated as background and filtered
     * from the final render to show the camera view in the background.
     *
     * @param view        The view matrix to use for eye being drawn.
     * @param perspective The perspective matrix to use for the eye being drawn.
     */
    @Override
    protected void drawObjects(float[] view, float[] perspective) {
//        IntBuffer viewport = IntBuffer.allocate(4);
//        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport);

     if (ONLY_DRAW_WHEN_DETECTED) {
            // only draw if last frame processed produced a detected marker
            if (mUltraTracker.isMarkerDetected()) {
                // draw the axes
                if (DRAW_AXES) {
                    axisRenderer.draw(view, perspective);
                }
                if (DRAW_BONE) {
                    // draw the bone model
                    boneRenderer.draw(view, perspective);
                }
            }
        } else {
            // draw the axes
            if (DRAW_AXES) {
                axisRenderer.draw(view, perspective);
            }
            if (DRAW_BONE) {
                // draw the bone model
                boneRenderer.draw(view, perspective);
            }
        }
    }

    @Override
    public void onFinishFrame(Viewport viewport) {}

    /**
     * Called when the Cardboard trigger is pulled.
     */
    @Override
    public void onCardboardTrigger() {
        Log.i(TAG, "onCardboardTrigger");
    }
}
