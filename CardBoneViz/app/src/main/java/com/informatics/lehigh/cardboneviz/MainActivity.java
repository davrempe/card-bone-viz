package com.informatics.lehigh.cardboneviz;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;

import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import javax.microedition.khronos.egl.EGLConfig;

import com.informatics.lehigh.cardboardarlibrary.GarActivity;
import com.informatics.lehigh.cardboardarlibrary.StereoScreenRenderer;

import org.opencv.core.Core;
import org.opencv.core.Mat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class MainActivity extends GarActivity {

    //
    // CONSTANTS
    //
    private static final String TAG = "MainActivity";
    private static final int SURF_DATA = R.raw.data1;

    private static final int BYTES_PER_FLOAT = 4;
    private static final int BYTES_PER_SHORT = 2;
    private static final float PADDING_SIZE = 0.005f;
    private static final float MARKER_SIZE = 0.035f;

    //
    // PREFERENCES
    //
    /** Render the bone on detected marker */
    private static final boolean DRAW_BONE = false;
    /** Render the axes on detected marker */
    private static final boolean DRAW_AXES = true;
    /** Only render models if the marker is currently detected */
    private static final boolean ONLY_DRAW_WHEN_DETECTED = false;

    // Bone drawing properties
    //
    // CHANGE DRAW BONE SETTING HERE
    //
    /** The coefficient to scale the bone model down by when rendering */
    private static final float SCALING_COEFF = 0.0005f; //0.0005f;
    /** Distance abover the marker for the bone to float */
    private static final float BONE_HOVER_DIST = 0.04f;//0.04f;
    /** Elements in vertices returned from SURF file */
    private static final int ELEMENTS_PER_POINT = 3;
    /** Elements in position data passed to shaders */
    private static final int ELEMENTS_PER_POSITION = 4;
    private static final int ELEMENTS_PER_COLOR = 4;
    private static final int ELEMENTS_PER_NORMAL = 3;
    private static final int ELEMENTS_PER_BONE_VERTEX = ELEMENTS_PER_POSITION + ELEMENTS_PER_NORMAL + ELEMENTS_PER_COLOR;
    private static final int BONE_STRIDE = ELEMENTS_PER_BONE_VERTEX * BYTES_PER_FLOAT;
    // We keep the light always position just above the user.
    private static final float[] LIGHT_POS_IN_WORLD_SPACE = new float[] {0.0f, 2.0f, 0.0f, 1.0f};
    // All bone vertices should be same color
    private static final float[] BONE_COLOR = new float [] {1.0f, 0.0f, 0.0f, 1.0f};

    // Axis drawing properties
    //
    // CHANGE DRAW AXIS SETTING HERE
    //
    private static final int NUM_AXIS_VERTICES = 6;
    private static final float AXIS_LENGTH = (MARKER_SIZE + 2.0f*PADDING_SIZE);
    private static final float AXIS_DEPTH = StereoScreenRenderer.SCREEN_DEPTH;// + 0.01f;
    // places axis in top left corner of marker
    private static final float[] AXIS_VERTICES = new float[] {
            -AXIS_LENGTH / 2.0f, AXIS_LENGTH / 2.0f, 0.0f, 1.0f, // x axis
            AXIS_LENGTH / 2.0f, AXIS_LENGTH / 2.0f, 0.0f, 1.0f,
            -AXIS_LENGTH / 2.0f, AXIS_LENGTH / 2.0f, 0.0f, 1.0f, // y axis
            -AXIS_LENGTH / 2.0f, -AXIS_LENGTH / 2.0f, 0.0f, 1.0f,
            -AXIS_LENGTH / 2.0f, AXIS_LENGTH / 2.0f, 0.0f, 1.0f, // z axis
            -AXIS_LENGTH / 2.0f, AXIS_LENGTH / 2.0f, AXIS_LENGTH, 1.0f
    };
    private static final int ELEMENTS_PER_AXIS_VERTEX = ELEMENTS_PER_POSITION + ELEMENTS_PER_COLOR;
    private static final int AXIS_STRIDE = ELEMENTS_PER_AXIS_VERTEX * BYTES_PER_FLOAT;

    //
    // OpenGL-related members
    //
    /** Model matrix for bone */
    private float[] mModelBone;
    /** Matrix that translates bone model to about origin and scales to meters */
    private float[] mBoneNorm;
    /** ModelView matrix */
    private float[] mModelView;
    /** ModelViewProjection matrix */
    private float[] mModelViewProjection;
    /** vector for light position in eye space */
    private final float[] mLightPosInEyeSpace = new float[4];
    /** number of vertices that make up bone model */
    int mNumBoneVerts;
    /** number tris that make up bone model */
    int mNumBoneTris;
    /** Buffer for bone vertices */
    private int mBoneVertBuf;
    /** Buffer for bone indices */
    private int mBoneIndexBuf;
    /** Program using bone shaders */
    private int mBoneProgram;
    /** Attribute location for bone position */
    private int mBonePositionParam;
    /** Attribute location for bone normals */
    private int mBoneNormalParam;
    /** Attribute location for bone colors */
    private int mBoneColorParam;
    /** Attribute location for bone modelview matrix */
    private int mBoneModelViewParam;
    /** Attribute location for ModelViewProjection matrix */
    private int mBoneModelViewProjectionParam;
    /** Attribute location for light position */
    private int mBoneLightPositionParam;

    //
    // Draw axis-related members
    //
    /** Model matrix for axes */
    private float [] mModelAxis;
    /** ModelViewProjection matrix for axes */
    private float [] mModelViewProjectionAxis;
    /** Buffer for axis vertices */
    private int mAxisVertBuf;
    /** Program using axis shaders */
    private int mAxisProgram;
    /** Attribute location for axis position */
    private int mAxisPositionParam;
    /** Attribute location for axis colors */
    private int mAxisColorParam;
    /** Attribute location for axis ModelViewProjection matrix */
    private int mAxisModelViewProjectionParam;


    //
    // MISC
    //
    /** Renderer used for the bone models */
    BoneRenderer boneRenderer;
    /** Renderer used for the marker cube axes */
    AxisRenderer axisRenderer;
    /** Image processor to track ultrasound wand location */
    private UltrasoundTracker mUltraTracker;
    /** The thread being used to run ultrasound tracking */
    private Thread mTrackingThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (DRAW_BONE) {
            boneRenderer = new BoneRenderer(this);
        }
        if (DRAW_AXES) {
            axisRenderer = new AxisRenderer(this);
        }

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

            // transform to world coordinates from camera
            // create m in column major order
            // invert forward vec because in opengl -z is forward
            float[] m = new float[] {
                    rightVec[0], upVec[0], -forwardVec[0], 0.0f,
                    rightVec[1], upVec[1], -forwardVec[1], 0.0f,
                    rightVec[2], upVec[2], -forwardVec[2], 0.0f,
                    0.0f, 0.0f, 0.0f, 1.0f
            };
            // a = M^T * b where b is vector representation in camera (cardboard) basis
            float[] mt = new float[16];
            Matrix.transposeM(mt, 0, m, 0);

            // must use rvec to find rotation matrix
            // transform rotation axis to world space
            float angleRad = (float)Core.norm(rvecMat, Core.NORM_L2);
            float angle = (float)Math.toDegrees(angleRad);
            float rvecCam[] = {(float)rvecMat.get(0, 0)[0] / angleRad, -1.0f * (float)rvecMat.get(1, 0)[0] / angleRad, -1.0f * (float)rvecMat.get(2, 0)[0] / angleRad, 0.0f};
            float rvec[] = new float[4];
            Matrix.multiplyMV(rvec, 0, mt, 0, rvecCam, 0);
            // Build rotation matrix
            float rot[] = new float[16];
            Matrix.setIdentityM(rot, 0);
            Matrix.rotateM(rot, 0, angle, rvec[0], rvec[1], rvec[2]);

            // getting the translation vector is easy
            // negate y and z for opengl coordinates
            float tvecCam[] = new float[4];
            tvecCam[0] = (float) tvecMat.get(0, 0)[0];
            tvecCam[1] = -(float) tvecMat.get(1, 0)[0];
            tvecCam[2] = -(float) tvecMat.get(2, 0)[0];
            tvecCam[3] = 1.0f;

            // TODO
            // adjust z so not too close to camera
            // update x and y accordingly
            // must move away in cardboard -z as well as properly scale so this translation isn't noticed
            float xRatio = tvecCam[0] / Math.abs(tvecCam[2]);
            float yRatio = tvecCam[1] / Math.abs(tvecCam[2]);
            float scaleRatio = 1.0f / Math.abs(tvecCam[2]);
            float distToMoveZ = Math.abs(Math.abs(tvecCam[2]) - Math.abs(AXIS_DEPTH));
            float adjustZ = (AXIS_DEPTH <= tvecCam[2] ? -distToMoveZ : distToMoveZ); // actually moves to the initial Z position
            float scaleFact = 1.0f - adjustZ * scaleRatio;
            float adjustX = -adjustZ * xRatio;
            float adjustY = -adjustZ * yRatio;
            tvecCam[0] += adjustX;
            tvecCam[1] += adjustY;
            tvecCam[2] += adjustZ;
            // build the scale matrix
            float[] scale = new float[16];
            Matrix.setIdentityM(scale, 0);
            Matrix.scaleM(scale, 0, scaleFact, scaleFact, scaleFact);

            // transform to world coords
            float tvec[] = new float[4];
            Matrix.multiplyMV(tvec, 0, mt, 0, tvecCam, 0);
            // Build translation matrix
            float trans[] = new float[16];
            Matrix.setIdentityM(trans, 0);
            Matrix.translateM(trans, 0, tvec[0], tvec[1], tvec[2]);

            // TODO get the coordinates together better to avoid unnecessary multiplications
            // multiply together, we want v' = TSRM^T v
           // markerTransform = trans;
            float[] basisChangeRot = new float[16];
            float[] addScale = new float[16];
            Matrix.multiplyMM(basisChangeRot, 0, rot, 0, mt, 0);
            Matrix.multiplyMM(addScale, 0, scale, 0, basisChangeRot, 0);

            float centerCubeTransform[] = new float [16];
            Matrix.setIdentityM(centerCubeTransform, 0);
            Matrix.multiplyMM(centerCubeTransform, 0, trans, 0, addScale, 0);

            // TODO pass center cube transform to renderers

            // want to translate axis so sitting on top marker
            // because location is in center of marker cube
            float[] aboveSurfaceVec = new float[] {0.0f, 0.0f, (AXIS_LENGTH / 2.0f) + PADDING_SIZE, 1.0f};
            // apply full transformation
            float[] transAxesVec = new float[4];
            Matrix.multiplyMV(transAxesVec, 0, centerCubeTransform, 0, aboveSurfaceVec, 0);
            // Build translation matrix
            float transAxes[] = new float[16];
            Matrix.setIdentityM(transAxes, 0);
            Matrix.translateM(transAxes, 0, transAxesVec[0], transAxesVec[1], transAxesVec[2]);

            float axisTransform[] = new float [16];
            Matrix.setIdentityM(axisTransform, 0);
            Matrix.multiplyMM(axisTransform, 0, transAxes, 0, addScale, 0);

            // want to translate bone so floating above marker
            aboveSurfaceVec = new float[] {0.0f, 0.0f, BONE_HOVER_DIST, 1.0f};
            // apply full transformation
            float[] transBoneVec = new float[4];
            Matrix.multiplyMV(transBoneVec, 0, axisTransform, 0, aboveSurfaceVec, 0);
            // Build translation matrix
            float transBone[] = new float[16];
            Matrix.setIdentityM(transBone, 0);
            Matrix.translateM(transBone, 0, transBoneVec[0], transBoneVec[1], transBoneVec[2]);

            float boneTransform[] = new float [16];
            Matrix.setIdentityM(boneTransform, 0);
            Matrix.multiplyMM(boneTransform, 0, transBone, 0, addScale, 0);

            if (DRAW_BONE){
                Matrix.multiplyMM(mModelBone, 0, boneTransform, 0, mBoneNorm, 0);
                Log.d(TAG, "UPDATED BONE MODEL");
            }
            if (DRAW_AXES) {
                mModelAxis = axisTransform;
                //Log.d(TAG, "UPDATED AXIS MODEL");
            }
        }

        glutil.checkGLError("onReadyToDraw");
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

        // Set the position of the light
        Matrix.multiplyMV(mLightPosInEyeSpace, 0, view, 0, LIGHT_POS_IN_WORLD_SPACE, 0);

        if (DRAW_BONE) {
            Matrix.multiplyMM(mModelView, 0, view, 0, mModelBone, 0);
            Matrix.multiplyMM(mModelViewProjection, 0, perspective, 0, mModelView, 0);
        }
        if (DRAW_AXES) {
            GLES20.glLineWidth(5.0f);
            // set up matrices for axes
            float [] axisMV = new float [16];
            Matrix.multiplyMM(axisMV, 0, view, 0, mModelAxis, 0);
            Matrix.multiplyMM(mModelViewProjectionAxis, 0, perspective, 0, axisMV, 0);
        }

        if (ONLY_DRAW_WHEN_DETECTED) {
            // only draw if last frame processed produced a detected marker
            if (mUltraTracker.isMarkerDetected()) {
                // draw the axes
                if (DRAW_AXES) {
                    drawAxes();
                }
                if (DRAW_BONE) {
                    // draw the bone model
                    drawBone();
                }
            }
        } else {
            // draw the axes
            if (DRAW_AXES) {
                drawAxes();
            }
            if (DRAW_BONE) {
                // draw the bone model
                drawBone();
            }
        }
    }

    public void drawBone() {
        GLES20.glUseProgram(mBoneProgram);

        // Point to uniform variables
        GLES20.glUniform3fv(mBoneLightPositionParam, 1, mLightPosInEyeSpace, 0);
        // Set the ModelView in the shader, used to calculate lighting
        GLES20.glUniformMatrix4fv(mBoneModelViewParam, 1, false, mModelView, 0);
        // Set the ModelViewProjection matrix in the shader.
        GLES20.glUniformMatrix4fv(mBoneModelViewProjectionParam, 1, false, mModelViewProjection, 0);

        // bind attributes
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mBoneVertBuf);
        // position
        GLES20.glVertexAttribPointer(mBonePositionParam, ELEMENTS_PER_POSITION, GLES20.GL_FLOAT, false,
                BONE_STRIDE, 0);
        GLES20.glEnableVertexAttribArray(mBonePositionParam);
        // colors
        GLES20.glVertexAttribPointer(mBoneColorParam, ELEMENTS_PER_COLOR, GLES20.GL_FLOAT, false,
                BONE_STRIDE, ELEMENTS_PER_POSITION * BYTES_PER_FLOAT);
        GLES20.glEnableVertexAttribArray(mBoneColorParam);
        // normals
        GLES20.glVertexAttribPointer(mBoneNormalParam, ELEMENTS_PER_NORMAL, GLES20.GL_FLOAT, false,
                BONE_STRIDE, (ELEMENTS_PER_POSITION + ELEMENTS_PER_COLOR) * BYTES_PER_FLOAT);
        GLES20.glEnableVertexAttribArray(mBoneNormalParam);

        // indices
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mBoneIndexBuf);
        // draw
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 3 * mNumBoneTris, GLES20.GL_UNSIGNED_SHORT, 0);

        // free buffers
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        glutil.checkGLError("Drawing bone");
    }

    private void drawAxes() {
        GLES20.glUseProgram(mAxisProgram);

        // Set the ModelViewProjection matrix in the shader.
        GLES20.glUniformMatrix4fv(mAxisModelViewProjectionParam, 1, false, mModelViewProjectionAxis, 0);

        // bind attributes
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mAxisVertBuf);
        // position
        GLES20.glVertexAttribPointer(mAxisPositionParam, ELEMENTS_PER_POSITION, GLES20.GL_FLOAT, false,
                AXIS_STRIDE, 0);
        GLES20.glEnableVertexAttribArray(mAxisPositionParam);
        // colors
        GLES20.glVertexAttribPointer(mAxisColorParam, ELEMENTS_PER_COLOR, GLES20.GL_FLOAT, false,
                AXIS_STRIDE, ELEMENTS_PER_POSITION * BYTES_PER_FLOAT);
        GLES20.glEnableVertexAttribArray(mAxisColorParam);

        // draw
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, NUM_AXIS_VERTICES);

        // free buffer
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        glutil.checkGLError("Drawing axes");
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
