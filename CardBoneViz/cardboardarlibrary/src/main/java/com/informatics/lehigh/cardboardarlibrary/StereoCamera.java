package com.informatics.lehigh.cardboardarlibrary;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.util.Size;
import android.view.Surface;

import java.nio.FloatBuffer;

/**
 * Allows easy access to rendering the front-facing camera view to a Google Cardboard stereo view.
 * Should be used with applications built similarly to TreasureHuntActivity Sample provided by Google.
 *
 * Simply uses OpenGL to render a plane in front of the user that is textured with the current
 * camera view.
 */
public class StereoCamera {

    //
    // OpenGL-related members
    //
    /** Model matrix for screen */
    private float[] modelScreen;
    /** View matrix */
    private float[] view;
    /** ModelView matrix */
    private float[] modelView;
    /** ModelViewProjection matrix */
    private float[] modelViewProjection;
    /** Buffer for screen vertices */
    private FloatBuffer screenVertBuf;
    /** Buffer for screen texture coordinates */
    private FloatBuffer screenTexBuf;
    /** Program using screen shaders */
    private int screenProgram;
    /** Attribute location for screen position */
    private int screenPositionParam;
    /** Attribute location for screen texture */
    private int screenTextureParam;
    //** ID of screen texture that holds camera feed */
    private int screenTextureID;


    //
    // Camera access related members
    //
    /** The front-facing camera */
    private CameraDevice mCameraDevice;
    /** Capture session related to front-facing camera */
    private CameraCaptureSession mCameraCaptureSession;
    /** Builder for capture requests with preview quality */
    private CaptureRequest.Builder mPreviewBuilder;
    /** Size of the preview image captured */
    private Size mPreviewSize;
    /** Surface texture attached to GL screen */
    private SurfaceTexture mSurfaceTexture;
    /** Surface holding screen SurfaceTexture */
    private Surface mSurface;
    /** Callback function for camera capture */
    private CameraCaptureSession.CaptureCallback capCall;

    //
    // CONSTANTS
    //
    /** Number of coordinates per screen vertex */
    private static final int COORDS_PER_VERTEX = 3;
    /** Vertices making up screen (just a plane of 2 triangles) */
    private final float[] SCREEN_COORDS = new float[] {
            -1.78f, 1.0f, -2.25f,
            -1.78f, -1.0f, -2.25f,
            1.78f, 1.0f, -2.25f,
            -1.78f, -1.0f, -2.25f,
            1.78f, -1.0f, -2.25f,
            1.78f, 1.0f, -2.25f
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

    /**
     * Creates a new StereoCamera object.
     * Use {@link #init() init} to initialize the stereo camera.
     */
    public StereoCamera() {
        modelScreen = new float[16];
        view = new float[16];
        modelView = new float[16];
        modelViewProjection = new float[16];
    }

    public void init() {
        
    }

}
