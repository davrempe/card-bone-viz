package com.informatics.lehigh.cardboneviz;

import android.app.Activity;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.informatics.lehigh.cardboardarlibrary.GLRenderer;
import com.informatics.lehigh.cardboardarlibrary.GarUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class BoneRenderer implements GLRenderer {

    private static final String TAG = "BoneRenderer";
    private static final int SURF_DATA = R.raw.data1;

    private static final int BYTES_PER_FLOAT = 4;
    private static final int BYTES_PER_SHORT = 2;

    // Bone drawing properties
    //
    // CHANGE DRAW BONE SETTING HERE
    //
    /** The coefficient to scale the bone model down by when rendering */
    private static final float SCALING_COEFF = 0.0005f; //0.0005f;
    /** Distance above the center of the cube marker for the bone to hover */
    private static final float BONE_HOVER_DIST = 0.1f;//0.04f;
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
    /** Transform from center of cube tracker to world space */
    private float[] mCenterCubeTransform;
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
    /** GarUtil instance */
    private GarUtil garutil;

    /** The calling activity */
    private Activity callingActivity;


    public BoneRenderer(Activity activity) {
        callingActivity = activity;

        mModelView = new float[16];
        mModelViewProjection = new float[16];

        mModelBone = new float[16];
        mBoneNorm = new float[16];
        Matrix.setIdentityM(mModelBone, 0);
        Matrix.setIdentityM(mBoneNorm, 0);

        garutil = new GarUtil(activity.getResources());
    }

    /**
     * Initialize all GL elements of the renderer like buffers, textures,
     * and shaders. Should be called from
     * {@link GvrView.StereoRenderer#onSurfaceCreated onSurfaceCreated()}.
     */
    @Override
    public void init() {
        // Parse the SURF data file to get bone vertex, normal, and index data
        SurfParser surfParse = new SurfParser(callingActivity.getResources().openRawResource(SURF_DATA));
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

        garutil.checkGLError("bindingBuffers");

        // create and link shaders
        int vertexShader = garutil.loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.bone_vert);
        int fragmentShader = garutil.loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.bone_frag);

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
        garutil.checkGLError("binding attributes");

        GLES20.glLinkProgram(mBoneProgram);
        GLES20.glUseProgram(mBoneProgram);
        garutil.checkGLError("Link program");

        mBoneModelViewParam = GLES20.glGetUniformLocation(mBoneProgram, "u_MVMatrix");
        mBoneModelViewProjectionParam = GLES20.glGetUniformLocation(mBoneProgram, "u_MVP");
        mBoneLightPositionParam = GLES20.glGetUniformLocation(mBoneProgram, "u_LightPos");
        garutil.checkGLError("binding uniforms");

        //
        // Initialize bone model normalization matrix
        // We want to first translate by the negative centroid to move close to (0, 0, 0),
        // then scale from _____? (original scale in SURF file) to m.
        //
        float transCent[] = new float[16];
        Matrix.setIdentityM(transCent, 0);
        Matrix.translateM(transCent, 0, -boneCentroid[0], -boneCentroid[1], -boneCentroid[2]);
        float scaleMat[] = new float[16];
        Matrix.setIdentityM(scaleMat, 0);
        Matrix.scaleM(scaleMat, 0, SCALING_COEFF, SCALING_COEFF, SCALING_COEFF);

        Matrix.multiplyMM(mBoneNorm, 0, scaleMat, 0, transCent, 0);
    }

    /**
     * Updates the GL elements of the renderer like vertex buffers
     * and model matrices. These things are independent of any single view
     * and is meant to be called once per frame. Should be called from
     * {@link GvrView.StereoRenderer#onNewFrame onNewFrame()}.
     *
     * @param headTransform Contains the up, right, and forward vectors of the
     *                      cardboard viewer needed for calculations.
     */
    @Override
    public void update(HeadTransform headTransform) {
        // Find point of origin
        float[] origin = new float[] {0.0f, 0.0f, 0.0f, 1.0f};
        float[] originVec = new float[4];
        Matrix.multiplyMV(originVec, 0, mCenterCubeTransform, 0, origin, 0);

        // want to translate bone so floating above marker
        float[] aboveSurfaceVec = new float[] {0.0f, 0.0f, BONE_HOVER_DIST, 1.0f};
        // apply full transformation
        float[] transBoneVec = new float[4];
        Matrix.multiplyMV(transBoneVec, 0, mCenterCubeTransform, 0, aboveSurfaceVec, 0);

        // Subtract to get vector from center of cube to bone center
        float[] translateVec = new float[] {transBoneVec[0] - originVec[0],
                                            transBoneVec[1] - originVec[1],
                                            transBoneVec[2] - originVec[2]};

        // Build translation matrix
        float transBone[] = new float[16];
        Matrix.setIdentityM(transBone, 0);
        Matrix.translateM(transBone, 0, translateVec[0], translateVec[1], translateVec[2]);

        float boneTransform[] = new float [16];
        Matrix.setIdentityM(boneTransform, 0);
        Matrix.multiplyMM(boneTransform, 0, transBone, 0, mCenterCubeTransform, 0);

        // include the normalization transform and update model matrix
        Matrix.multiplyMM(mModelBone, 0, boneTransform, 0, mBoneNorm, 0);
    }

    /**
     * Draws the GL elements defined by this renderer. This should be called from
     * {@link GvrView.StereoRenderer#onDrawEye onDrawEye()}.
     *
     * @param view        The 4x4 view matrix to use for rendering.
     * @param perspective The 4x4 projection matrix to user for rendering.
     */
    @Override
    public void draw(float[] view, float[] perspective) {
        // Set the position of the light
        Matrix.multiplyMV(mLightPosInEyeSpace, 0, view, 0, LIGHT_POS_IN_WORLD_SPACE, 0);

        Matrix.multiplyMM(mModelView, 0, view, 0, mModelBone, 0);
        Matrix.multiplyMM(mModelViewProjection, 0, perspective, 0, mModelView, 0);

        // now actually draw the bone model
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

        garutil.checkGLError("Drawing bone");
    }

    /**
     * Sets the matrix that transforms from cube tracker coordinates to the
     * world space. This will be used to render the bone on top of the cube marker.
     * @param transform The 4x4 transformation matrix.
     */
    public void setCenterCubeTransform(float[] transform) {
        mCenterCubeTransform = transform;
    }

}
