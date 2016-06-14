package com.informatics.lehigh.cardboneviz;

import android.content.res.Resources;
import android.hardware.camera2.CameraAccessException;
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

import com.informatics.lehigh.cardboardarlibrary.StereoCamera;
import com.informatics.lehigh.cardboardarlibrary.GLUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class MainActivity extends GvrActivity implements GvrView.StereoRenderer {

    //
    // CONSTANTS
    //
    private static final int SURF_DATA = R.raw.data1;
    private static final float CAMERA_Z = 0.01f;
    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100.0f;
    private static final int BYTES_PER_FLOAT = 4;
    private static final int BYTES_PER_SHORT = 2;
    /** Elements in vertices returned from SURF file */
    private static final int ELEMENTS_PER_POINT = 3;
    /** Elements in position data passed to shaders */
    private static final int ELEMENTS_PER_POSITION = 4;
    private static final int ELEMENTS_PER_COLOR = 4;
    private static final int ELEMENTS_PER_NORMAL = 3;
    private static final int ELEMENTS_PER_VERTEX = ELEMENTS_PER_POSITION + ELEMENTS_PER_NORMAL + ELEMENTS_PER_COLOR;
    private static final int STRIDE = ELEMENTS_PER_VERTEX * BYTES_PER_FLOAT;
    private static final String TAG = "MainActivity";
    // We keep the light always position just above the user.
    private static final float[] LIGHT_POS_IN_WORLD_SPACE = new float[] {0.0f, 2.0f, 0.0f, 1.0f};
    // All bone vertices should be same color
    private static final float[] BONE_COLOR = new float [] {1.0f, 0.0f, 0.0f, 1.0f};
    private static final float[] BONE_INIT_POS = new float[] {0.5f, 0.0f, -1.5f};

    //
    // OpenGL-related members
    //
    /** Model matrix for bone */
    private float[] mModelBone;
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
    /** Attribute location for screen position */
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
    // MISC
    //
    private StereoCamera mStereoCam;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mModelBone = new float[16];
        mView = new float[16];
        mModelView = new float[16];
        mModelViewProjection = new float[16];
        mCamera = new float[16];

        Resources res = getResources();
        mStereoCam = new StereoCamera(res);
        glutil = new GLUtil(res);

        initializeGvrView();

        try {
            mStereoCam.initCamera(this);
        } catch(CameraAccessException cae) {
            Log.e(TAG, "COULD NOT ACCESS CAMERA");
            cae.printStackTrace();
        }

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
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f);

        // Initialize stereo camera rendering
        mStereoCam.initRenderer();
        glutil.checkGLError("initStereoRenderer");

        // Parse the SURF data file to get bone vertex, normal, and index data
        SurfParser surfParse = new SurfParser(getResources().openRawResource(SURF_DATA));
        surfParse.parse();
        mNumBoneVerts = surfParse.getNumVerts();
        mNumBoneTris = surfParse.getNumTris();
        float [] boneVertices = surfParse.getVertices();
        // scale the vertices down to meters from mm
        for (int i = 0; i < boneVertices.length; i++) {
            boneVertices[i] /= 100.0f;
        }
        float [] boneNormals = surfParse.getNormals();
        short [] boneIndices = surfParse.getIndices();

        Log.d("NUM VERTS", String.valueOf(surfParse.getNumVerts()));
        Log.d("NUM TRIS", String.valueOf(surfParse.getNumTris()));
        Log.d("VERTS1", "(" + String.valueOf(boneVertices[0]) + ", " + String.valueOf(boneVertices[1]) + ", " + String.valueOf(boneVertices[2]) + ")");
        Log.d("NORMS1", "(" + String.valueOf(boneNormals[0]) + ", " + String.valueOf(boneNormals[1]) + ", " + String.valueOf(boneNormals[2]) + ")");
        Log.d("INDICES1", "(" + String.valueOf(boneIndices[0]) + ", " + String.valueOf(boneIndices[1]) + ", " + String.valueOf(boneIndices[2]) + ")");
        Log.d("VERTS_LAST", "(" + String.valueOf(boneVertices[(surfParse.getNumVerts()-1)*3]) + ", " + String.valueOf(boneVertices[(surfParse.getNumVerts()-1)*3 + 1]) + ", " + String.valueOf(boneVertices[(surfParse.getNumVerts()-1)*3 + 2]) + ")");
        Log.d("NORMS_LAST", "(" + String.valueOf(boneNormals[(surfParse.getNumVerts()-1)*3]) + ", " + String.valueOf(boneNormals[(surfParse.getNumVerts()-1)*3 + 1]) + ", " + String.valueOf(boneNormals[(surfParse.getNumVerts()-1)*3 + 2]) + ")");
        Log.d("INDICES_LAST", "(" + String.valueOf(boneIndices[(surfParse.getNumTris()-1)*3]) + ", " + String.valueOf(boneIndices[(surfParse.getNumTris()-1)*3 + 1]) + ", " + String.valueOf(boneIndices[(surfParse.getNumTris()-1)*3 + 2]) + ")");

        //
        // Aggregate bone data
        //
        // A vertex object is structured as {vec4 position, vec4 color, vec3 normal}
        //
        int dataSize = mNumBoneVerts * ELEMENTS_PER_VERTEX;
        float [] boneData = new float[dataSize];
        for (int i = 0; i < mNumBoneVerts; i++) {
            boneData[i * ELEMENTS_PER_VERTEX] = boneVertices[i * ELEMENTS_PER_POINT];
            boneData[i * ELEMENTS_PER_VERTEX + 1] = boneVertices[i * ELEMENTS_PER_POINT + 1];
            boneData[i * ELEMENTS_PER_VERTEX + 2] = boneVertices[i * ELEMENTS_PER_POINT + 2];
            boneData[i * ELEMENTS_PER_VERTEX + 3] = 1.0f;
            boneData[i * ELEMENTS_PER_VERTEX + 4] = BONE_COLOR[0];
            boneData[i * ELEMENTS_PER_VERTEX + 5] = BONE_COLOR[1];
            boneData[i * ELEMENTS_PER_VERTEX + 6] = BONE_COLOR[2];
            boneData[i * ELEMENTS_PER_VERTEX + 7] = BONE_COLOR[3];
            boneData[i * ELEMENTS_PER_VERTEX + 8] = boneNormals[i * ELEMENTS_PER_NORMAL];
            boneData[i * ELEMENTS_PER_VERTEX + 9] = boneNormals[i * ELEMENTS_PER_NORMAL + 1];
            boneData[i * ELEMENTS_PER_VERTEX + 10] = boneNormals[i * ELEMENTS_PER_NORMAL + 2];
        }

        // Initialize GL elements for bone model
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
        int [] buffers = new int[2];
        GLES20.glGenBuffers(2, buffers, 0);
        mBoneVertBuf = buffers[0];
        mBoneIndexBuf = buffers[1];

        // bind vertex buffer
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mBoneVertBuf);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, boneDataFloatBuf.capacity()*BYTES_PER_FLOAT,
                boneDataFloatBuf, GLES20.GL_STATIC_DRAW);

        // bind index buffer
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mBoneIndexBuf);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, boneIndexShortBuffer.capacity()*BYTES_PER_SHORT,
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

        Matrix.setIdentityM(mModelBone, 0);
        Matrix.translateM(mModelBone, 0, BONE_INIT_POS[0], BONE_INIT_POS[1], BONE_INIT_POS[2]);
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
            mStereoCam.updateStereoView(rightVec, upVec, forwardVec);
        } catch(CameraAccessException cae) {
            Log.e(TAG, "COULDN'T ACCESS CAMERA ON REFRESH");
        }

        // Build the camera matrix.
        Matrix.setLookAtM(mCamera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

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
        // Get the perspective matrix
        float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);
        Matrix.multiplyMM(mModelView, 0, mView, 0, mModelBone, 0);
        Matrix.multiplyMM(mModelViewProjection, 0, perspective, 0, mModelView, 0);

        // draw the stereo camera
        mStereoCam.drawStereoView(mView, perspective);

        // draw the bone model
        drawBone();
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
                STRIDE, 0);
        GLES20.glEnableVertexAttribArray(mBonePositionParam);
        // colors
        GLES20.glVertexAttribPointer(mBoneColorParam, ELEMENTS_PER_COLOR, GLES20.GL_FLOAT, false,
                STRIDE, ELEMENTS_PER_POSITION * BYTES_PER_FLOAT);
        GLES20.glEnableVertexAttribArray(mBoneColorParam);
        // normals
        GLES20.glVertexAttribPointer(mBoneNormalParam, ELEMENTS_PER_NORMAL, GLES20.GL_FLOAT, false,
                STRIDE, (ELEMENTS_PER_POSITION + ELEMENTS_PER_COLOR) * BYTES_PER_FLOAT);
        GLES20.glEnableVertexAttribArray(mBoneNormalParam);

        // indices
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mBoneIndexBuf);
        // draw
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mNumBoneTris, GLES20.GL_UNSIGNED_SHORT, 0);

        // free buffers
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        glutil.checkGLError("Drawing bone");
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
