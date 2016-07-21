package com.informatics.lehigh.cardboneviz;

import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import javax.microedition.khronos.egl.EGLConfig;

import com.informatics.lehigh.cardboardarlibrary.GarUtil;
import com.informatics.lehigh.cardboardarlibrary.GarActivity;

import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends GarActivity {

    //
    // CONSTANTS
    //
    private static final String TAG = "MainActivity";
    private static final float PADDING_SIZE = 0.005f;
    private static final float MARKER_SIZE = 0.035f;
    /**The filepath to the Camera Calibration Data file*/
    public static final String DATA_FILEPATH = "/CardBoneViz/camCalibData.csv";

    private static final boolean ACCURACY_TESTING = false;
    ArrayList<float[]> tvecList;
    ArrayList<Mat> rvecList;

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

        if (ACCURACY_TESTING) {
            tvecList = new ArrayList<>();
            rvecList = new ArrayList<>();
        }

        boneRenderer = new BoneRenderer(this);
        axisRenderer = new AxisRenderer(this);

        // initialize ultrasound wand tracker
        mUltraTracker = new UltrasoundTracker(getProcessingReader(), MARKER_SIZE, PADDING_SIZE);
        mTrackingThread = new Thread(mUltraTracker);
        mTrackingThread.start();
    }

    @Override
    protected List<Surface> setupCaptureSurfaces() {
        //this.setProcessingSurfaceResolution(new Size(1280, 720));
        return super.setupCaptureSurfaces();
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
    protected void onPause() {
        super.onPause();
        if (ACCURACY_TESTING) {
            calcTvecDeviation();
            calcRvecDeviation();
        }
        if (mUltraTracker.BENCHMARK_TESTING) {
            mUltraTracker.calcAvgFps();
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

            if (ACCURACY_TESTING) {
                if (tvecList.size() < 120) {
                    // add current tvec for later calculation
                    float[] cardTvec = new float[4];
                    GarUtil.tvecToCardboardCoords(cardTvec, tvecMat);
                    tvecList.add(cardTvec);
                    rvecList.add(rvecMat);
                    Log.i(TAG, "COLLECTED T/Rvec FRAME " + tvecList.size());
                } else {
                    Log.i(TAG, "FINISHED T/Rvec DATA COLLECTION");
                }
            }

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

    private void calcTvecDeviation() {
        // first need avg
        float[] sum = {0.0f, 0.0f, 0.0f, 0.0f};
        for (float[] tvec : tvecList) {
            sum[0] += tvec[0];
            sum[1] += tvec[1];
            sum[2] += tvec[2];
            sum[3] += tvec[3];
        }
        float num = tvecList.size();
        float[] avg = {sum[0] / num, sum[1] / num, sum[2] / num, sum[3] / num};

        // now calculate standard deviation
        sum[0] = 0.0f;
        sum[1] = 0.0f;
        sum[2] = 0.0f;
        sum[3] = 0.0f;
        for (float[] tvec : tvecList) {
            sum[0] += (tvec[0] - avg[0]) * (tvec[0] - avg[0]);
            sum[1] += (tvec[1] - avg[1]) * (tvec[1] - avg[1]);
            sum[2] += (tvec[2] - avg[2]) * (tvec[2] - avg[2]);
            sum[3] += (tvec[3] - avg[3]) * (tvec[3] - avg[3]);
        }
        float[] var = {sum[0] / num, sum[1] / num, sum[2] / num, sum[3] / num};
        double [] stdDev = {Math.sqrt(var[0]), Math.sqrt(var[1]), Math.sqrt(var[2]), Math.sqrt(var[3])};

        Log.i(TAG, "TVEC STD DEV = (" + stdDev[0] + ", " + stdDev[1] + ", " + stdDev[2] + ", " + stdDev[3] + ")");
    }

    private void calcRvecDeviation() {
        ArrayList<double[]> quatList = new ArrayList<>();
        for (Mat rvec: rvecList) {
            //Get Angle-Axis Representation
            double x = rvec.get(0, 0)[0];
            double y = rvec.get(1, 0)[0];
            double z = rvec.get(2, 0)[0];
            double a = Math.sqrt((x * x) + (y * y) + (z * z));

            //Normalize X,Y,Z
            x /= a;
            y /= a;
            z /= a;

            //Get Quaternions
            double[] quat = new double[4];
            quat[0] = Math.cos(a / 2);
            quat[1] = Math.sin(a / 2) * x;
            quat[2] = Math.sin(a / 2) * y;
            quat[3] = Math.sin(a / 2) * z;
            quatList.add(quat);
        }

        //calculate mean
        double totalW = 0;
        double totalX = 0;
        double totalY = 0;
        double totalZ = 0;

        for (double[] quatValues : quatList) {
            totalW += quatValues[0];
            totalX += quatValues[1];
            totalY += quatValues[2];
            totalZ += quatValues[3];
        }
        double[] mean = new double[4];
        mean[0] = totalW / quatList.size();
        mean[1] = totalX / quatList.size();
        mean[2] = totalY / quatList.size();
        mean[3] = totalZ / quatList.size();

        double[] summationVals = {0.0, 0.0, 0.0, 0.0};
        for (double[] quatValues : quatList) {
            //find standard deviation for qw
            summationVals[0] += Math.pow(quatValues[0] - mean[0],2);
            //find standard deviation for qx
            summationVals[1] += Math.pow(quatValues[1] - mean[1],2);
            //find standard deviation for qy
            summationVals[2] += Math.pow(quatValues[2] - mean[2],2);
            //find standard deviation for qz
            summationVals[3] += Math.pow(quatValues[3] - mean[3],2);
        }

        double[] var = {summationVals[0] / quatList.size(), summationVals[1] / quatList.size(), summationVals[2] / quatList.size(), summationVals[3] / quatList.size()};
        double [] stdDev = {Math.sqrt(var[0]), Math.sqrt(var[1]), Math.sqrt(var[2]), Math.sqrt(var[3])};

        Log.i(TAG, "RVEC STD DEV = (" + stdDev[0] + ", " + stdDev[1] + ", " + stdDev[2] + ", " + stdDev[3] + ")");
    }
}
