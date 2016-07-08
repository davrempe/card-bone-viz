package com.informatics.lehigh.cardboardarlibrary;


import android.app.Activity;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.google.vr.sdk.base.HeadTransform;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

public class StereoScreenRenderer implements GLRenderer {

    private static final String TAG = "StereoScreenRenderer";

    //
    // CONSTANTS
    //
    /** Number of coordinates per screen vertex */
    private static final int COORDS_PER_VERTEX = 3;
    /** Number of bytes in a float */
    private static final int BYTES_PER_FLOAT = 4;
    public static final float SCREEN_DEPTH = -3.2f;
    /** Vertices making up screen (just a plane of 2 triangles) */
    private final float[] SCREEN_COORDS = new float[] {
            -1.78f, 1.0f, SCREEN_DEPTH,
            -1.78f, -1.0f, SCREEN_DEPTH,
            1.78f, 1.0f, SCREEN_DEPTH,
            -1.78f, -1.0f, SCREEN_DEPTH,
            1.78f, -1.0f, SCREEN_DEPTH,
            1.78f, 1.0f, SCREEN_DEPTH
    };
    /** Texture coordinates for the screen plane */
    private static final float[] SCREEN_TEX_COORDS = new float [] {
            0.0f, 0.0f, 0.0f, 1.0f,
            0.0f, 1.0f, 0.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f,
            0.0f, 1.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f
    };

    //
    // OpenGL-related members
    //
    /** Model matrix for screen */
    private float[] mModelScreen;
    /** ModelViewProjection matrix */
    private float[] mModelViewProjection;
    /** Buffer for screen vertices */
    private FloatBuffer mScreenVertBuf;
    /** Buffer for screen texture coordinates */
    private FloatBuffer mScreenTexBuf;
    /** Program using screen shaders */
    private int mScreenProgram;
    /** Attribute location for screen position */
    private int mScreenPositionParam;
    /** Attribute location for screen texture */
    private int mScreenTextureParam;
    /** Attribute location for ModelViewProjection matrix */
    private int mScreenModelViewProjectionParam;
    /** ID of screen texture that holds camera feed */
    private int mScreenTextureID;
    /** GLUtil instance */
    private GLUtil glutil;

    /**
     * Creates a new StereoScreenRenderer.
     * @param activity the Activity used to access resources like shaders.
     */
    public StereoScreenRenderer(Activity activity) {
        mModelScreen = new float[16];
        mModelViewProjection = new float[16];
        glutil = new GLUtil(activity.getResources());
    }

    @Override
    public void init() {
        // make buffer for screen vertices
        ByteBuffer bbScreenVertices = ByteBuffer.allocateDirect(SCREEN_COORDS.length * BYTES_PER_FLOAT);
        bbScreenVertices.order(ByteOrder.nativeOrder());
        mScreenVertBuf = bbScreenVertices.asFloatBuffer();
        mScreenVertBuf.put(SCREEN_COORDS);
        mScreenVertBuf.position(0);
        // make buffer for screen texture coordinates
        ByteBuffer bbScreenTex = ByteBuffer.allocateDirect(SCREEN_TEX_COORDS.length * BYTES_PER_FLOAT);
        bbScreenTex.order(ByteOrder.nativeOrder());
        mScreenTexBuf = bbScreenTex.asFloatBuffer();
        mScreenTexBuf.put(SCREEN_TEX_COORDS);
        mScreenTexBuf.position(0);

        // TODO don't need this texture here, just need to take in the screen and 3d objects tex
        // make camera texture
        int[] textures = new int[1];
        // Generate the texture to where android view will be rendered
        GLES20.glGenTextures(1, textures, 0);
        glutil.checkGLError("Texture generate");
        mScreenTextureID = textures[0];

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mScreenTextureID);
        glutil.checkGLError("Texture bind");

        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER,GL10.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        int vertexShader = glutil.loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.screen_vert);
        int fragmentShader = glutil.loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.screen_frag);

        mScreenProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mScreenProgram, vertexShader);
        GLES20.glAttachShader(mScreenProgram, fragmentShader);

        mScreenPositionParam = 0;
        GLES20.glBindAttribLocation(mScreenProgram, mScreenPositionParam, "a_Position");
        Log.d("position", String.valueOf(mScreenPositionParam));
        mScreenTextureParam = 1;
        GLES20.glBindAttribLocation(mScreenProgram, mScreenTextureParam, "a_TexCoordinate");
        Log.d("texture", String.valueOf(mScreenTextureParam));

        GLES20.glLinkProgram(mScreenProgram);
        GLES20.glUseProgram(mScreenProgram);

        glutil.checkGLError("Screen program");

        mScreenModelViewProjectionParam = GLES20.glGetUniformLocation(mScreenProgram, "u_MVP");

        glutil.checkGLError("screen program params");
    }

    @Override
    public void update(HeadTransform headTransform) {
        float[] forwardVec = new float[3];
        float[] upVec = new float[3];
        float[] rightVec = new float[3];
        headTransform.getForwardVector(forwardVec, 0);
        headTransform.getUpVector(upVec, 0);
        headTransform.getRightVector(rightVec, 0);

        // create m in column major order
        // invert forward vec because in opengl -z is forward
        float[] m = new float[] {
                rightVec[0], upVec[0], -forwardVec[0], 0.0f,
                rightVec[1], upVec[1], -forwardVec[1], 0.0f,
                rightVec[2], upVec[2], -forwardVec[2], 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f
        };
        // a = M^T * b where b is vector representation in cardboard basis
        float[] mt = new float[16];
        Matrix.transposeM(mt, 0, m, 0);
        mModelScreen = mt;
    }

    @Override
    public void draw(float[] view, float[] perspective) {
        // get modelview matrix
        float [] modelView = new float[16];
        Matrix.multiplyMM(modelView, 0, view, 0, mModelScreen, 0);
        // get MVP
        Matrix.multiplyMM(mModelViewProjection, 0, perspective, 0, modelView, 0);

        GLES20.glUseProgram(mScreenProgram);
        glutil.checkGLError("using screen program");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mScreenTextureID);
        glutil.checkGLError("binding uniform texture");

        // TODO what to do with this?
        //mSurfaceTexture.updateTexImage();

        // Set the position of the screen
        GLES20.glVertexAttribPointer(mScreenPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, mScreenVertBuf);
        glutil.checkGLError("set screen pos pointer");

        // Set the texture coords for the screen
        GLES20.glVertexAttribPointer(mScreenTextureParam, 4, GLES20.GL_FLOAT, false, 0, mScreenTexBuf);
        glutil.checkGLError("setting texture attribute pointers");

        // Set the ModelViewProjection matrix in the shader.
        GLES20.glUniformMatrix4fv(mScreenModelViewProjectionParam, 1, false, mModelViewProjection, 0);
        glutil.checkGLError("set modelviewprojection uniform");

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(mScreenPositionParam);
        GLES20.glEnableVertexAttribArray(mScreenTextureParam);

        // actually draw
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        glutil.checkGLError("Drawing bill");

        // free texture
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
    }
}
