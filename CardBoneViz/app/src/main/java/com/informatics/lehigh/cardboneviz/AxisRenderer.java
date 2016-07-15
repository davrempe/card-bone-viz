package com.informatics.lehigh.cardboneviz;

import android.app.Activity;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.informatics.lehigh.cardboardarlibrary.GLRenderer;
import com.informatics.lehigh.cardboardarlibrary.GarUtil;
import com.informatics.lehigh.cardboardarlibrary.StereoScreenRenderer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class AxisRenderer implements GLRenderer {

    private static final String TAG = "AxisRenderer";

    private static final int BYTES_PER_FLOAT = 4;
    private static final float PADDING_SIZE = 0.005f;
    private static final float MARKER_SIZE = 0.035f;

    // Axis drawing properties
    //
    // CHANGE DRAW AXIS SETTING HERE
    //
    /** Elements in position data passed to shaders */
    private static final int ELEMENTS_PER_POSITION = 4;
    private static final int ELEMENTS_PER_COLOR = 4;
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
    // Draw axis-related members
    //
    /** Model matrix for axes */
    private float [] mModelAxis;
    /** ModelViewProjection matrix for axes */
    private float [] mModelViewProjectionAxis;
    /** Transform from center of cube tracker to world space */
    private float[] mCenterCubeTransform;
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
    /** GarUtil instance */
    private GarUtil garutil;

    public AxisRenderer(Activity activity) {
        mModelAxis = new float[16];
        mModelViewProjectionAxis = new float[16];
        Matrix.setIdentityM(mModelAxis, 0);

        garutil = new GarUtil(activity.getResources());
    }


    /**
        * Initialize all GL elements of the renderer like buffers, textures,
        * and shaders. Should be called from
        * {@link GvrView.StereoRenderer#onSurfaceCreated onSurfaceCreated()}.
     */
    @Override
    public void init() {
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

        garutil.checkGLError("bindingAxisBuffers");

        // create and link shaders
        int vertexShader = garutil.loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.axis_vert);
        int fragmentShader = garutil.loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.axis_frag);

        mAxisProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mAxisProgram, vertexShader);
        GLES20.glAttachShader(mAxisProgram, fragmentShader);

        // MUST BIND BEFORE LINKING SHADERS
        mAxisPositionParam = 0;
        GLES20.glBindAttribLocation(mAxisProgram, mAxisPositionParam, "a_Position");
        mAxisColorParam = 1;
        GLES20.glBindAttribLocation(mAxisProgram, mAxisColorParam, "a_Color");
        garutil.checkGLError("binding axis attributes");

        GLES20.glLinkProgram(mAxisProgram);
        GLES20.glUseProgram(mAxisProgram);
        garutil.checkGLError("Link axis program");

        mAxisModelViewProjectionParam = GLES20.glGetUniformLocation(mAxisProgram, "u_MVP");
        garutil.checkGLError("binding uniforms");
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
        // want to translate axis so sitting on top marker
        // because location is in center of marker cube
        // find vector to top marker surface
        float[] aboveSurfaceVec = new float[] {0.0f, 0.0f, (AXIS_LENGTH / 2.0f) + PADDING_SIZE, 1.0f};
        // apply full transformation
        float[] transAxesVec = new float[4];
        Matrix.multiplyMV(transAxesVec, 0, mCenterCubeTransform, 0, aboveSurfaceVec, 0);

        // Subtract to get vector from center of cube to top surface
        float[] translateVec = new float[] {transAxesVec[0] - originVec[0],
                                            transAxesVec[1] - originVec[1],
                                            transAxesVec[2] - originVec[2]};

        // Build additional translation matrix
        float transAxes[] = new float[16];
        Matrix.setIdentityM(transAxes, 0);
        Matrix.translateM(transAxes, 0, translateVec[0], translateVec[1], translateVec[2]);

        float axisTransform[] = new float [16];
        Matrix.setIdentityM(axisTransform, 0);
        Matrix.multiplyMM(axisTransform, 0, transAxes, 0, mCenterCubeTransform, 0);

        // update model matrix
        mModelAxis = axisTransform;
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
        GLES20.glLineWidth(5.0f);
        // set up matrices for axes
        float [] axisMV = new float [16];
        Matrix.multiplyMM(axisMV, 0, view, 0, mModelAxis, 0);
        Matrix.multiplyMM(mModelViewProjectionAxis, 0, perspective, 0, axisMV, 0);

        // now actually draw
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

        garutil.checkGLError("Drawing axes");
    }

    /**
     * Sets the matrix that transforms from cube tracker coordinates to the
     * world space. This will be used to render the axes on top of the cube marker.
     * @param transform The 4x4 transformation matrix.
     */
    public void setCenterCubeTransform(float[] transform) {
        mCenterCubeTransform = transform;
    }
}
