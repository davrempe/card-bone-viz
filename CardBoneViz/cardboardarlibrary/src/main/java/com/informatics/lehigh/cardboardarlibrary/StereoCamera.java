package com.informatics.lehigh.cardboardarlibrary;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

import javax.microedition.khronos.opengles.GL10;

/**
 * Allows easy access to rendering the back-facing camera view to a Google Cardboard stereo view.
 * Should be used with applications built similarly to TreasureHuntActivity Sample provided by Google.
 *
 * Simply uses OpenGL to render a plane in front of the user that is textured with the current
 * camera view.
 */
public class StereoCamera {

    private final String TAG = "StereoCamera";

    //
    // OpenGL-related members
    //
    /** Model matrix for screen */
    private float[] modelScreen;
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
    /** Attribute location for MovelViewProjection matrix */
    private int screenModelViewProjectionParam;
    /** ID of screen texture that holds camera feed */
    private int screenTextureID;
    /** GLUtil instance */
    private GLUtil glutil;


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
    private CameraCaptureSession.CaptureCallback mCapCall;

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
     * Use {@link #initCamera(Activity activity) initCamera} and {@link #initRenderer() initRenderer} to initialize the stereo camera.
     */
    public StereoCamera(Resources res) {
        modelScreen = new float[16];
        modelViewProjection = new float[16];
        glutil = new GLUtil(res);
    }

  /**
   * Initilaizes the stereo camera by connecting to the front facing camera device
   * and binding it's data to a GL plane facing the viewer.
   * @param activity - Activity used to access camera.
   */
  public void initCamera(Activity activity) throws CameraAccessException{
        //
        // First initialize all callback functions
        //
        mCapCall = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                Log.d(TAG, "CAPTURED FRAME " + String.valueOf(result.getFrameNumber()));
            }

            @Override
            public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                Log.e(TAG, "CAPTURE FAILED " + String.valueOf(failure.getReason()));
            }
        };

        final CameraCaptureSession.StateCallback ccCall = new CameraCaptureSession.StateCallback() {

            @Override
            public void onConfigured(CameraCaptureSession session) {
                Log.d(TAG, "CAMERA CAPTURE SESSION CONFIGURED");

                mCameraCaptureSession = session;
                // automatically focus and white-balance
                mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

                // repeatedly capture current frame as app runs
//        try {
//          mCameraCaptureSession.setRepeatingRequest(mPreviewBuilder.build(),capCall, null);
//        } catch(Exception e) {
//          Log.e("Error", "capturing");
//        }
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession session) {
                Log.e(TAG, "CAMERA CAP SESSION CONFIG FAILURE");
            }
        };

        CameraDevice.StateCallback cdCall =  new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                Log.d(TAG, "CAMERA DEVICE OPENED SUCCESSFULLY");
                mCameraDevice = camera;

                // create SurfaceTexture to store images
                mSurfaceTexture = new SurfaceTexture(screenTextureID);
                mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                mSurface = new Surface(mSurfaceTexture);

                try {
                    mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                } catch (CameraAccessException e){
                    e.printStackTrace();
                }

                mPreviewBuilder.addTarget(mSurface);

                try {
                    mCameraDevice.createCaptureSession(Arrays.asList(mSurface), ccCall, null);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onDisconnected(CameraDevice camera) {
                Log.e(TAG, "CAMERA DEVICE DISCONNECTED");
            }

            @Override
            public void onError(CameraDevice camera, int error) {
                Log.e(TAG, "CAMERA DEVICE ERROR");
            }
        };

        // initialize camera related things
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        String [] cameraIDs = manager.getCameraIdList();
        String cameraID = "";
        // find back-facing camera
        for (String id : cameraIDs) {
            CameraCharacteristics chars = manager.getCameraCharacteristics(id);
            if (chars.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK) {
                cameraID = id;
            }
        }

        CameraCharacteristics camChars = manager.getCameraCharacteristics(cameraID);
        StreamConfigurationMap streamMap = camChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size [] sizes = streamMap.getOutputSizes(SurfaceTexture.class);
        // get the output resolution from camera
        mPreviewSize = sizes[0];
        manager.openCamera(cameraID, cdCall, null);
    }

  /**
   * Initializes rendering portion of StereoCamera. This is just a plane in 3-space called the screen
   * which is made up of two triangles. This screen is textures with the data stream from the back-facing
   * device camera.
   */
  public void initRenderer() {
      // make buffer for screen vertices
      ByteBuffer bbScreenVertices = ByteBuffer.allocateDirect(this.SCREEN_COORDS.length * 4);
      bbScreenVertices.order(ByteOrder.nativeOrder());
      this.screenVertBuf = bbScreenVertices.asFloatBuffer();
      this.screenVertBuf.put(this.SCREEN_COORDS);
      this.screenVertBuf.position(0);
      // make buffer for screen texture coordinates
      ByteBuffer bbScreenTex = ByteBuffer.allocateDirect(this.SCREEN_TEX_COORDS.length * 4);
      bbScreenTex.order(ByteOrder.nativeOrder());
      this.screenTexBuf = bbScreenTex.asFloatBuffer();
      this.screenTexBuf.put(this.SCREEN_TEX_COORDS);
      this.screenTexBuf.position(0);

      // make camera texture
      int[] textures = new int[1];
      // Generate the texture to where android view will be rendered
      GLES20.glGenTextures(1, textures, 0);
      glutil.checkGLError("Texture generate");
      this.screenTextureID = textures[0];

      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, this.screenTextureID);
      glutil.checkGLError("Texture bind");

      GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER,GL10.GL_NEAREST);
      GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
      GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

      int vertexShader = glutil.loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.screen_vert);
      int fragmentShader = glutil.loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.screen_frag);

      this.screenProgram = GLES20.glCreateProgram();
      GLES20.glAttachShader(this.screenProgram, vertexShader);
      GLES20.glAttachShader(this.screenProgram, fragmentShader);

      this.screenPositionParam = 0;
      GLES20.glBindAttribLocation(this.screenProgram, this.screenPositionParam, "a_Position");
      Log.d("position", String.valueOf(this.screenPositionParam));
      this.screenTextureParam = 1;
      GLES20.glBindAttribLocation(this.screenProgram, this.screenTextureParam, "a_TexCoordinate");
      Log.d("texture", String.valueOf(this.screenTextureParam));

      GLES20.glLinkProgram(this.screenProgram);
      GLES20.glUseProgram(this.screenProgram);

      glutil.checkGLError("Screen program");

      this.screenModelViewProjectionParam = GLES20.glGetUniformLocation(this.screenProgram, "u_MVP");

      glutil.checkGLError("screen program params");
    }

  /**
   * Updates the stereo view with the current camera view based on the given cardboard vector parameters.
   * @param rightVec - right vector obtained from Google's HeadTransform.getRightVector()
   * @param upVec - up vector obtained from Google's HeadTransform.getUpVector()
   * @param forwardVec - forward vector obtained from Google's HeadTransform.getForwardVector()
   */
    public void updateStereoView(float [] rightVec, float [] upVec, float [] forwardVec) throws CameraAccessException {

        // capture image to use
        try {
            mCameraCaptureSession.capture(mPreviewBuilder.build(),mCapCall, new Handler(Looper.getMainLooper()));
        } catch(IllegalStateException ise) {
            Log.e(TAG, "Error capturing: " + ise.getMessage());
        } catch(IllegalArgumentException iae) {
            Log.e(TAG, "Error capturing: " + iae.getMessage());
        }

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
        this.modelScreen = mt;
    }

  /**
   * Draws the plane with the stereo camera view in front of user
   * @param view - the view matrix
   * @param perspective - the perpective matrix
   */
  public void drawStereoView(float [] view, float [] perspective){
      // get modelview matrix
      float [] modelView = new float[16];
      Matrix.multiplyMM(modelView, 0, view, 0, this.modelScreen, 0);
      // get MVP
      Matrix.multiplyMM(this.modelViewProjection, 0, perspective, 0, modelView, 0);

      GLES20.glUseProgram(this.screenProgram);
      this.glutil.checkGLError("using screen program");

      GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, this.screenTextureID);
      this.glutil.checkGLError("binding uniform texture");

      mSurfaceTexture.updateTexImage();

      // Set the position of the screen
      GLES20.glVertexAttribPointer(this.screenPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, this.screenVertBuf);
      this.glutil.checkGLError("set screen pos pointer");

      // Set the texture coords for the screen
      GLES20.glVertexAttribPointer(this.screenTextureParam, 4, GLES20.GL_FLOAT, false, 0, this.screenTexBuf);
      this.glutil.checkGLError("setting texture attribute pointers");

      // Set the ModelViewProjection matrix in the shader.
      GLES20.glUniformMatrix4fv(this.screenModelViewProjectionParam, 1, false, modelViewProjection, 0);
      this.glutil.checkGLError("set modelviewprojection uniform");

      // Enable vertex arrays
      GLES20.glEnableVertexAttribArray(this.screenPositionParam);
      GLES20.glEnableVertexAttribArray(this.screenTextureParam);

      // actually draw
      GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
      this.glutil.checkGLError("Drawing bill");

      // free texture
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
    }

}
