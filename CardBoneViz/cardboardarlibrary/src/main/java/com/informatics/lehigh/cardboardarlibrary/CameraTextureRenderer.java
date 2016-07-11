package com.informatics.lehigh.cardboardarlibrary;

import android.app.Activity;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;

import javax.microedition.khronos.opengles.GL10;

public class CameraTextureRenderer implements GLRenderer {

    /** ID of screen texture that holds camera feed */
    private int mScreenTextureID;
    /** GLUtil instance */
    private GLUtil glutil;

    public CameraTextureRenderer(Activity activity) {
        glutil = new GLUtil(activity.getResources());
    }

    /**
     * Initialize all GL elements of the renderer like buffers, textures,
     * and shaders. Should be called from
     * {@link GvrView.StereoRenderer#onSurfaceCreated onSurfaceCreated()}.
     */
    @Override
    public void init() {
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
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mScreenTextureID);
        glutil.checkGLError("binding uniform texture");
    }

    /**
     * @return the ID of the texture that camera feed should be drawn to using
     * a SurfaceTexture.
     */
    public int getCameraTexture() {
        return mScreenTextureID;
    }
}
