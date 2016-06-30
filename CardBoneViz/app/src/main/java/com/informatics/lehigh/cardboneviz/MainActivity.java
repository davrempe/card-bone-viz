package com.informatics.lehigh.cardboneviz;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.media.ImageReader;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import javax.microedition.khronos.egl.EGLConfig;

import com.informatics.lehigh.cardboardarlibrary.StereoCamera;
import com.informatics.lehigh.cardboardarlibrary.GLUtil;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

public class MainActivity extends GvrActivity implements GvrView.StereoRenderer {

    //
    // CONSTANTS
    //
    private static final String TAG = "MainActivity";
    private static final int SURF_DATA = R.raw.data1;
    private static final float CAMERA_Z = 0.01f;
    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100.0f;
    private static final int BYTES_PER_FLOAT = 4;
    private static final int BYTES_PER_SHORT = 2;

    // Bone drawing properties
    //
    // CHANGE DRAW BONE SETTING HERE
    //
    private static final boolean DRAW_BONE = false;
    /** The coefficient to scale the bone model down by when rendering */
    private static final float SCALING_COEFF = 0.0005f;
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
    private static final float[] BONE_INIT_POS = new float[] {0.25f, 0.0f, -2.5f};

    // Axis drawing properties
    //
    // CHANGE DRAW AXIS SETTING HERE
    //
    private static final boolean DRAW_AXES = true;
    private static final int NUM_AXIS_VERTICES = 6;
    private static final float AXIS_LENGTH = 0.02f;
    private static final float AXIS_DEPTH = StereoCamera.SCREEN_DEPTH + 0.3f;
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
    /** View matrix */
    private float[] mView;
    /** ModelView matrix */
    private float[] mModelView;
    /** ModelViewProjection matrix */
    private float[] mModelViewProjection;
    /** Camera matrix */
    private float[] mCamera;
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
    /** GLUtil instance */
    private GLUtil glutil;

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
    /** Renders stereo-view of back-facing phone camera */
    private StereoCamera mStereoCam;
    /** Image processor to track ultrasound wand location */
    private UltrasoundTracker mUltraTracker;
    /** The thread being used to run ultrasound tracking */
    private Thread mTrackingThread;
    /** The surface to draw to for image processing purposes */
    private Surface mProcessingSurface;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i("OpenCV", "OpenCV loaded successfully");
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        mView = new float[16];
        mModelView = new float[16];
        mModelViewProjection = new float[16];
        mCamera = new float[16];

        if (DRAW_BONE) {
            mModelBone = new float[16];
            mBoneNorm = new float[16];
            Matrix.setIdentityM(mModelBone, 0);
            Matrix.setIdentityM(mBoneNorm, 0);
        }
        if (DRAW_AXES) {
            mModelAxis = new float[16];
            mModelViewProjectionAxis = new float[16];
            Matrix.setIdentityM(mModelAxis, 0);
        }

        mStereoCam = new StereoCamera(this);
        glutil = new GLUtil(getResources());

        initializeGvrView();

        // create surface for image processing
        Size imgSize = mStereoCam.getCameraImageSize();
        // We give it 5 max images so stack can fill while processing top image,
        // this allows StereoCamera to keep rendering at a high frame rate since
        // the capture waits to finish until the images on the ImageReader stack
        // are all closed.
        ImageReader imgReader = ImageReader.newInstance(imgSize.getWidth(), imgSize.getHeight(),
                ImageFormat.YUV_420_888, 5);
        mProcessingSurface = imgReader.getSurface();
        ArrayList<Surface> surfList = new ArrayList<Surface>();
        surfList.add(mProcessingSurface);

        try {
            mStereoCam.initCamera(surfList);
        } catch(CameraAccessException cae) {
            Log.e(TAG, "COULD NOT ACCESS CAMERA");
            cae.printStackTrace();
        }

        // initialize ultrasound wand tracker
        mUltraTracker = new UltrasoundTracker(imgReader);
        mTrackingThread = new Thread(mUltraTracker);
        mTrackingThread.start();
    }

    public void initializeGvrView() {
        setContentView(R.layout.common_ui);

        GvrView gvrView = (GvrView) findViewById(R.id.gvr_view);
        gvrView.setEGLConfigChooser(8, 8, 8, 8, 16, 8);

        gvrView.setRenderer(this);
        gvrView.setTransitionViewEnabled(true);
        gvrView.setOnCardboardBackButtonListener(
                new Runnable() {
                    @Override
                    public void run() {
                        onBackPressed();
                    }
                });
        setGvrView(gvrView);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_11, this, mLoaderCallback);
        } else {
            Log.d("OpenCV", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onSurfaceChanged(int i, int i1) {
        Log.i(TAG, "onSurfaceChanged");
    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");

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
        Log.i(TAG, "onSurfaceCreated");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f);

        // Initialize stereo camera rendering
        mStereoCam.initRenderer();
        glutil.checkGLError("initStereoRenderer");

        if (DRAW_BONE) {
            // Parse the SURF data file to get bone vertex, normal, and index data
            SurfParser surfParse = new SurfParser(getResources().openRawResource(SURF_DATA));
            surfParse.parse();
            mNumBoneVerts = surfParse.getNumVerts();
            mNumBoneTris = surfParse.getNumTris();
            float[] boneVertices = surfParse.getVertices();
            float[] boneCentroid = surfParse.getCentroid();
            float[] boneNormals = surfParse.getNormals();
            short[] boneIndices = surfParse.getIndices();

            Log.d("NUM VERTS", String.valueOf(surfParse.getNumVerts()));
            Log.d("NUM TRIS", String.valueOf(surfParse.getNumTris()));
            Log.d("CENTROID", "(" + String.valueOf(boneCentroid[0]) + ", " + String.valueOf(boneCentroid[1]) + ", " + String.valueOf(boneCentroid[2]) + ")");
            Log.d("VERTS1", "(" + String.valueOf(boneVertices[0]) + ", " + String.valueOf(boneVertices[1]) + ", " + String.valueOf(boneVertices[2]) + ")");
            Log.d("NORMS1", "(" + String.valueOf(boneNormals[0]) + ", " + String.valueOf(boneNormals[1]) + ", " + String.valueOf(boneNormals[2]) + ")");
            Log.d("INDICES1", "(" + String.valueOf(boneIndices[0]) + ", " + String.valueOf(boneIndices[1]) + ", " + String.valueOf(boneIndices[2]) + ")");
            Log.d("VERTS_LAST", "(" + String.valueOf(boneVertices[(surfParse.getNumVerts() - 1) * 3]) + ", " + String.valueOf(boneVertices[(surfParse.getNumVerts() - 1) * 3 + 1]) + ", " + String.valueOf(boneVertices[(surfParse.getNumVerts() - 1) * 3 + 2]) + ")");
            Log.d("NORMS_LAST", "(" + String.valueOf(boneNormals[(surfParse.getNumVerts() - 1) * 3]) + ", " + String.valueOf(boneNormals[(surfParse.getNumVerts() - 1) * 3 + 1]) + ", " + String.valueOf(boneNormals[(surfParse.getNumVerts() - 1) * 3 + 2]) + ")");
            Log.d("INDICES_LAST", "(" + String.valueOf(boneIndices[(surfParse.getNumTris() - 1) * 3]) + ", " + String.valueOf(boneIndices[(surfParse.getNumTris() - 1) * 3 + 1]) + ", " + String.valueOf(boneIndices[(surfParse.getNumTris() - 1) * 3 + 2]) + ")");

            //
            // Aggregate bone data
            //
            // A vertex object is structured as {vec4 position, vec4 color, vec3 normal}
            //
            int dataSize = mNumBoneVerts * ELEMENTS_PER_BONE_VERTEX;
            float[] boneData = new float[dataSize];
            for (int i = 0; i < mNumBoneVerts; i++) {
                boneData[i * ELEMENTS_PER_BONE_VERTEX] = boneVertices[i * ELEMENTS_PER_POINT];
                boneData[i * ELEMENTS_PER_BONE_VERTEX + 1] = boneVertices[i * ELEMENTS_PER_POINT + 1];
                boneData[i * ELEMENTS_PER_BONE_VERTEX + 2] = boneVertices[i * ELEMENTS_PER_POINT + 2];
                boneData[i * ELEMENTS_PER_BONE_VERTEX + 3] = 1.0f;
                boneData[i * ELEMENTS_PER_BONE_VERTEX + 4] = BONE_COLOR[0];
                boneData[i * ELEMENTS_PER_BONE_VERTEX + 5] = BONE_COLOR[1];
                boneData[i * ELEMENTS_PER_BONE_VERTEX + 6] = BONE_COLOR[2];
                boneData[i * ELEMENTS_PER_BONE_VERTEX + 7] = BONE_COLOR[3];
                boneData[i * ELEMENTS_PER_BONE_VERTEX + 8] = boneNormals[i * ELEMENTS_PER_NORMAL];
                boneData[i * ELEMENTS_PER_BONE_VERTEX + 9] = boneNormals[i * ELEMENTS_PER_NORMAL + 1];
                boneData[i * ELEMENTS_PER_BONE_VERTEX + 10] = boneNormals[i * ELEMENTS_PER_NORMAL + 2];
            }

            //
            // Initialize GL elements for bone model
            //

            // make buffer for bone vertices
            ByteBuffer bbBoneData = ByteBuffer.allocateDirect(boneData.length * BYTES_PER_FLOAT);
            bbBoneData.order(ByteOrder.nativeOrder());
            FloatBuffer boneDataFloatBuf = bbBoneData.asFloatBuffer();
            boneDataFloatBuf.put(boneData);
            boneDataFloatBuf.position(0);

            // make buffer for bone indices
            ByteBuffer bbBoneIndices = ByteBuffer.allocateDirect(boneIndices.length * BYTES_PER_SHORT);
            bbBoneIndices.order(ByteOrder.nativeOrder());
            ShortBuffer boneIndexShortBuffer = bbBoneIndices.asShortBuffer();
            boneIndexShortBuffer.put(boneIndices);
            boneIndexShortBuffer.position(0);

            // init gl buffers
            int[] buffers = new int[2];
            GLES20.glGenBuffers(2, buffers, 0);
            mBoneVertBuf = buffers[0];
            mBoneIndexBuf = buffers[1];

            // bind vertex buffer
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mBoneVertBuf);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, boneDataFloatBuf.capacity() * BYTES_PER_FLOAT,
                    boneDataFloatBuf, GLES20.GL_STATIC_DRAW);

            // bind index buffer
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mBoneIndexBuf);
            GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, boneIndexShortBuffer.capacity() * BYTES_PER_SHORT,
                    boneIndexShortBuffer, GLES20.GL_STATIC_DRAW);

            // free buffers
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

            glutil.checkGLError("bindingBuffers");

            // create and link shaders
            int vertexShader = glutil.loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.bone_vert);
            int fragmentShader = glutil.loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.bone_frag);

            mBoneProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(mBoneProgram, vertexShader);
            GLES20.glAttachShader(mBoneProgram, fragmentShader);

            // MUST BIND BEFORE LINKING SHADERS
            mBonePositionParam = 0;
            GLES20.glBindAttribLocation(mBoneProgram, mBonePositionParam, "a_Position");
            mBoneNormalParam = 1;
            GLES20.glBindAttribLocation(mBoneProgram, mBoneNormalParam, "a_Normal");
            mBoneColorParam = 2;
            GLES20.glBindAttribLocation(mBoneProgram, mBoneColorParam, "a_Color");
            glutil.checkGLError("binding attributes");

            GLES20.glLinkProgram(mBoneProgram);
            GLES20.glUseProgram(mBoneProgram);
            glutil.checkGLError("Link program");

            mBoneModelViewParam = GLES20.glGetUniformLocation(mBoneProgram, "u_MVMatrix");
            mBoneModelViewProjectionParam = GLES20.glGetUniformLocation(mBoneProgram, "u_MVP");
            mBoneLightPositionParam = GLES20.glGetUniformLocation(mBoneProgram, "u_LightPos");
            glutil.checkGLError("binding uniforms");

            //
            // Initialize bone model normalization matrix
            // We want to first translate by the negative centroid to move close to (0, 0, 0),
            // then scale from half mm (original scale in SURF file) to m.
            //
            float transCent[] = new float[16];
            Matrix.setIdentityM(transCent, 0);
            Matrix.translateM(transCent, 0, -boneCentroid[0], -boneCentroid[1], -boneCentroid[2]);
            float scaleMat[] = new float[16];
            Matrix.setIdentityM(scaleMat, 0);
            Matrix.scaleM(scaleMat, 0, SCALING_COEFF, SCALING_COEFF, SCALING_COEFF);

            Matrix.multiplyMM(mBoneNorm, 0, scaleMat, 0, transCent, 0);
        }
        //
        // Now for the axis data
        //
        if (DRAW_AXES) {
            // Aggregate axis data
            // Vertex object is of the form {vec4 position, vec4 color}
            int dataSize = NUM_AXIS_VERTICES * ELEMENTS_PER_AXIS_VERTEX;
            float[] axisData = new float[dataSize];
            for (int i = 0; i < NUM_AXIS_VERTICES; i++) {
                float [] curPos = {AXIS_VERTICES[i*ELEMENTS_PER_POSITION],
                                    AXIS_VERTICES[i*ELEMENTS_PER_POSITION + 1],
                                    AXIS_VERTICES[i*ELEMENTS_PER_POSITION + 2],
                                    AXIS_VERTICES[i*ELEMENTS_PER_POSITION + 3]};
                axisData[i * ELEMENTS_PER_AXIS_VERTEX] = curPos[0];
                axisData[i * ELEMENTS_PER_AXIS_VERTEX + 1] = curPos[1];
                axisData[i * ELEMENTS_PER_AXIS_VERTEX + 2] = curPos[2];
                axisData[i * ELEMENTS_PER_AXIS_VERTEX + 3] = curPos[3];
                float [] axisColor = new float[4];
                if (i < 2) {
                    axisColor[0] = 1.0f;
                    axisColor[1] = 0.0f;
                    axisColor[2] = 0.0f;
                } else if (i < 4) {
                    axisColor[0] = 0.0f;
                    axisColor[1] = 1.0f;
                    axisColor[2] = 0.0f;
                } else {
                    axisColor[0] = 0.0f;
                    axisColor[1] = 0.0f;
                    axisColor[2] = 1.0f;
                }
                axisData[i * ELEMENTS_PER_AXIS_VERTEX + 4] = axisColor[0];
                axisData[i * ELEMENTS_PER_AXIS_VERTEX + 5] = axisColor[1];
                axisData[i * ELEMENTS_PER_AXIS_VERTEX + 6] = axisColor[2];
                axisData[i * ELEMENTS_PER_AXIS_VERTEX + 7] = 1.0f;
            }

            // make buffer for axis data
            ByteBuffer bbAxisData = ByteBuffer.allocateDirect(axisData.length * BYTES_PER_FLOAT);
            bbAxisData.order(ByteOrder.nativeOrder());
            FloatBuffer axisDataFloatBuf = bbAxisData.asFloatBuffer();
            axisDataFloatBuf.put(axisData);
            axisDataFloatBuf.position(0);

            // init gl buffers
            int [] axisBuffs = new int[1];
            GLES20.glGenBuffers(1, axisBuffs, 0);
            mAxisVertBuf = axisBuffs[0];

            // bind vertex buffer
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mAxisVertBuf);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, axisDataFloatBuf.capacity()*BYTES_PER_FLOAT,
                    axisDataFloatBuf, GLES20.GL_STATIC_DRAW);

            // free buffer
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

            glutil.checkGLError("bindingAxisBuffers");

            // create and link shaders
            int vertexShader = glutil.loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.axis_vert);
            int fragmentShader = glutil.loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.axis_frag);

            mAxisProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(mAxisProgram, vertexShader);
            GLES20.glAttachShader(mAxisProgram, fragmentShader);

            // MUST BIND BEFORE LINKING SHADERS
            mAxisPositionParam = 3;
            GLES20.glBindAttribLocation(mAxisProgram, mAxisPositionParam, "a_Position");
            mAxisColorParam = 4;
            GLES20.glBindAttribLocation(mAxisProgram, mAxisColorParam, "a_Color");
            glutil.checkGLError("binding axis attributes");

            GLES20.glLinkProgram(mAxisProgram);
            GLES20.glUseProgram(mAxisProgram);
            glutil.checkGLError("Link axis program");

            mAxisModelViewProjectionParam = GLES20.glGetUniformLocation(mAxisProgram, "u_MVP");
            glutil.checkGLError("binding uniforms");
        }

    }

    @Override
    public void onNewFrame(HeadTransform headTransform) {
        float[] forwardVec = new float[3];
        float[] upVec = new float[3];
        float[] rightVec = new float[3];
        headTransform.getForwardVector(forwardVec, 0);
        headTransform.getUpVector(upVec, 0);
        headTransform.getRightVector(rightVec, 0);

        // pass in surface used for tracking processing
        ArrayList<Surface> surfList = new ArrayList<Surface>();
        surfList.add(mProcessingSurface);
        try {
            mStereoCam.updateStereoView(surfList, rightVec, upVec, forwardVec);
        } catch(CameraAccessException cae) {
            Log.e(TAG, "COULDN'T ACCESS CAMERA ON REFRESH");
        }

        // Build the camera matrix.
        Matrix.setLookAtM(mCamera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

        // calculate bone model matrix based on marker location
        Mat tvecMat;
        Mat rvecMat;
        float markerTransform[] = new float [16];
        Matrix.setIdentityM(markerTransform, 0);

        if (mUltraTracker.isMarkerDetected()) {
            Mat [] params = mUltraTracker.getMarkerParams();
            tvecMat = params[0];
            rvecMat = params[1];

            // getting the translation vector is easy
            // negate y and z for opengl coordinates
            float tvecCam[] = new float[4];
            tvecCam[0] = (float) tvecMat.get(0, 0)[0];
            tvecCam[1] = -(float) tvecMat.get(1, 0)[0];
            tvecCam[2] = -(float) tvecMat.get(2, 0)[0];
            tvecCam[3] = 1.0f;

            // adjust z so not too close to camera
            // update x and y accordingly
            // must move away in cardboard -z as well as properly scale so this translation isn't noticed
            float xRatio = tvecCam[0] / Math.abs(tvecCam[2]);
            float yRatio = tvecCam[1] / Math.abs(tvecCam[2]);
            float scaleRatio = 1.0f / Math.abs(tvecCam[2]);
            float distToMoveZ = Math.abs(Math.abs(tvecCam[2]) - Math.abs(AXIS_DEPTH));
            float adjustX = distToMoveZ * xRatio;
            float adjustY = distToMoveZ * yRatio;
            float scaleFact = distToMoveZ * scaleRatio;
            float adjustZ = (AXIS_DEPTH <= tvecCam[2] ? -distToMoveZ : distToMoveZ); // actually moves to the initial Z position
            tvecCam[0] += adjustX;
            tvecCam[1] += adjustY;
            tvecCam[2] += adjustZ;
            // build the scale matrix
            float[] scale = new float[16];
            Matrix.setIdentityM(scale, 0);
            Matrix.scaleM(scale, 0, scaleFact, scaleFact, scaleFact);

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
            float tvec[] = new float[4];
            Matrix.multiplyMV(tvec, 0, mt, 0, tvecCam, 0);
            // Build translation matrix
            float trans[] = new float[16];
            Matrix.setIdentityM(trans, 0);
            Matrix.translateM(trans, 0, tvec[0], tvec[1], tvec[2]);

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

            // TODO get the coordinates together better to avoid unnecessary multiplications
            // multiply together, we want v' = TSRM^T v
           // markerTransform = trans;
            float[] basisChangeRot = new float[16];
            float[] addScale = new float[16];
            Matrix.multiplyMM(basisChangeRot, 0, rot, 0, mt, 0);
            Matrix.multiplyMM(addScale, 0, scale, 0, basisChangeRot, 0);
            Matrix.multiplyMM(markerTransform, 0, trans, 0, addScale, 0);

            if (DRAW_BONE){
                Matrix.multiplyMM(mModelBone, 0, markerTransform, 0, mBoneNorm, 0);
                Log.d(TAG, "UPDATED BONE MODEL");
            }
            if (DRAW_AXES) {
                mModelAxis = markerTransform;
                Log.d(TAG, "UPDATED AXIS MODEL");
            }
        }

        // must include normalization transform in model matrix
        // TODO THIS LINE SHOULD NOT EXIST - only for initial translation
        //Matrix.translateM(markerTransform, 0, BONE_INIT_POS[0], BONE_INIT_POS[1], BONE_INIT_POS[2]);
//        Matrix.multiplyMM(mModelBone, 0, markerTransform, 0, mBoneNorm, 0);

        glutil.checkGLError("onReadyToDraw");
    }

    @Override
    public void onDrawEye(Eye eye) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        glutil.checkGLError("colorParam");

        // Apply the eye transformation to the camera.
        Matrix.multiplyMM(mView, 0, eye.getEyeView(), 0, mCamera, 0);
        // Set the position of the light
        Matrix.multiplyMV(mLightPosInEyeSpace, 0, mView, 0, LIGHT_POS_IN_WORLD_SPACE, 0);
        // Get the perspective matrix for bone model
        float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);

        if (DRAW_BONE) {
            Matrix.multiplyMM(mModelView, 0, mView, 0, mModelBone, 0);
            Matrix.multiplyMM(mModelViewProjection, 0, perspective, 0, mModelView, 0);
        }
        if (DRAW_AXES) {
            // set up matrices for axes
            float [] axisMV = new float [16];
            Matrix.multiplyMM(axisMV, 0, mView, 0, mModelAxis, 0);
            Matrix.multiplyMM(mModelViewProjectionAxis, 0, perspective, 0, axisMV, 0);
        }

        // draw the stereo camera
        mStereoCam.drawStereoView(mView, perspective);


        // draw the axes
        if (DRAW_AXES) {
            drawAxes();
        }
        if (DRAW_BONE){
            // draw the bone model
            drawBone();
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
