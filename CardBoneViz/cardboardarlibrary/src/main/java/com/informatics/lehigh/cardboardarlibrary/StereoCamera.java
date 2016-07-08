package com.informatics.lehigh.cardboardarlibrary;

import android.app.Activity;
import android.content.Context;
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
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;

/**
 * Allows easy access to rendering the back-facing camera view to a Google Cardboard stereo view.
 *
 * Simply uses OpenGL to render a plane in front of the user that is textured with the current
 * camera view.
 */
public class StereoCamera {

    private static final String TAG = "StereoCamera";

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


    //
    // Camera access related members
    //
    /** The camera manager */
    private CameraManager mCamManager;
    /** ID of the front facing camera */
    private String mCameraID = "";
    /** The front-facing camera */
    private CameraDevice mCameraDevice;
    /** Capture session related to front-facing camera */
    private CameraCaptureSession mCameraCaptureSession;
    /** Builder for capture requests with preview quality */
    private CaptureRequest.Builder mPreviewBuilder;
    /** Size of the preview image captured */
    private Size mPreviewSize = new Size(-1, -1);
    /** Surface texture attached to GL screen */
    private SurfaceTexture mSurfaceTexture;
    /** Callback function for camera capture */
    private CameraCaptureSession.CaptureCallback mCapCall;
    /** The GL texture surface to draw camera view to */
    private Surface mGlSurface;
    /** List of possible additional surfaces to draw to besides GL surface */
    private List<Surface> mPossibleAddSurfaces;

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

    /**
     * Creates a new StereoCamera object.
     * Use {@link #initRenderer initRenderer} then {@link #initCamera initCamera} to initialize the stereo camera.
     * @param activity - Activity used to access camera services.
     */
    public StereoCamera(Activity activity) {
        mModelScreen = new float[16];
        mModelViewProjection = new float[16];
        glutil = new GLUtil(activity.getResources());

        // initialize camera related things
        try {
            mCamManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            String[] cameraIDs = mCamManager.getCameraIdList();
            // find back-facing camera
            for (String id : cameraIDs) {
                CameraCharacteristics chars = mCamManager.getCameraCharacteristics(id);
                if (chars.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK) {
                    mCameraID = id;
                }
            }

            // throw an error if there is not a back-facing camera
            if (mCameraID.isEmpty()) {
                throw new CameraAccessException(CameraAccessException.CAMERA_DISCONNECTED, "There is no back-facing camera connected to device!");
            }

            CameraCharacteristics camChars = mCamManager.getCameraCharacteristics(mCameraID);
            StreamConfigurationMap streamMap = camChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = streamMap.getOutputSizes(SurfaceTexture.class);
            // go through available output sizes and choose highest one that is 16:9
            for (int i = 0; i < sizes.length; i++) {
                Size curSize = sizes[i];
                if (curSize.getWidth() / 16 == curSize.getHeight() / 9) {
                    // get the output resolution from camera
                    mPreviewSize = sizes[i];
                    break;
                }
            }

            // make sure we found one
            if (mPreviewSize.getHeight() == -1) {
                throw new RuntimeException("Camera does not have a 16:9 resolution!");
            }

        } catch (CameraAccessException cae) {
            Log.e(TAG, "COULD NOT ACCESS CAMERA");
        }
    }

  /**
   * Initilaizes the stereo camera by opening the front facing camera device
   * and binding it's data to a GL plane facing the viewer, as well as any additional
   * Surfaces provided. By default, captured images default to auto-focus and white-balance, but
   * this may be changed by using {@link #setCaptureParam setCaptureParam}.
   * @param addSurfaces - Additional surfaces to draw the camera image to. You must include ALL surfaces you plan
   *                    to draw the camera image to even if you won't do it every frame. This may be any Surface detailed
   *                    in {@link android.hardware.camera2.CameraDevice#createCaptureSession createCaptureSession}.
   */
  public void initCamera(List<Surface> addSurfaces) throws CameraAccessException {
       mPossibleAddSurfaces = addSurfaces;

        //
        // First initialize all callback functions
        //
        mCapCall = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                //Log.d(TAG, "CAPTURED FRAME " + String.valueOf(result.getFrameNumber()));
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
                mSurfaceTexture = new SurfaceTexture(mScreenTextureID);
                mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                mGlSurface = new Surface(mSurfaceTexture);

                try {
                    mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                } catch (CameraAccessException e){
                    e.printStackTrace();
                }

                // Create list of all possible surfaces we may draw to
                List<Surface> allSurfaces = new ArrayList<Surface>(mPossibleAddSurfaces);
                allSurfaces.add(mGlSurface);
                try {
                    mCameraDevice.createCaptureSession(allSurfaces, ccCall, null);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

                // add our gl texture target because we always draw there
                mPreviewBuilder.addTarget(mGlSurface);
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

        mCamManager.openCamera(mCameraID, cdCall, null);
    }

  /**
   * Initializes rendering portion of StereoCamera. This should be called within onSurfaceCreated with other
   * OpenGL setup required for the application. The StereoView is rendered using a plane in 3-space called the screen
   * which is made up of two triangles. This screen is textured with the data stream from the back-facing
   * device camera.
   */
  public void initRenderer() {
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

  /**
   * Updates the stereo view and any given surfaces with the current camera view based on the given cardboard vector parameters.
   * @param addSurfaces - additional surfaces to draw the current camera view to in addition to the default the stereo view. This MUST
   *                    be one of the surfaces passed in when {@link #initCamera initCamera} was called.
   * @param rightVec - right vector obtained from Google's HeadTransform.getRightVector()
   * @param upVec - up vector obtained from Google's HeadTransform.getUpVector()
   * @param forwardVec - forward vector obtained from Google's HeadTransform.getForwardVector()
   */
    public void updateStereoView(List<Surface> addSurfaces, float [] rightVec, float [] upVec, float [] forwardVec) throws CameraAccessException, IllegalArgumentException {

        // clear previous surfaces
        for (int i = 0; i < mPossibleAddSurfaces.size(); i++) {
            mPreviewBuilder.removeTarget(mPossibleAddSurfaces.get(i));
        }
        // add current frame surfaces
        // don't need to do GL, should already be there
        for (int i = 0; i < addSurfaces.size(); i++) {
            Surface toAdd = addSurfaces.get(i);
            if (mPossibleAddSurfaces.contains(toAdd)) {
                mPreviewBuilder.addTarget(toAdd);
            } else {
                // not in the list of possible
                throw new IllegalArgumentException("Surface given is not in list of possible surfaces!");
            }
        }

        // capture image to use
        try {
            mCameraCaptureSession.capture(mPreviewBuilder.build(), mCapCall, new Handler(Looper.getMainLooper()));
        } catch (RuntimeException ise) {
            Log.e(TAG, "Error capturing: " + ise.getMessage());
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
        mModelScreen = mt;
    }

  /**
   * Draws the plane with the stereo camera view in front of user. This
   * should be called every time you would like the stereo view to be re-rendered. Both
   * matrix arguments can be found using the Eye object passed to the onDrawEye() method
   * of the GVR activity.
   * @param view - the view matrix
   * @param perspective - the perpective matrix
   */
  public void drawStereoView(float [] view, float [] perspective){
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

      mSurfaceTexture.updateTexImage();

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

    /**
     * @return the size of the image being captured from the back-facing camera
     */
    public Size getCameraImageSize() {
        return mPreviewSize;
    }

    /**
     * Specifies a parameter to be used when capturing from the camera. This simply
     * sets a capture request field to a value. The field definitions can be
     * found in {@link CaptureRequest}.
     * @param key The metadata field to write.
     * @param value The value to set the field to, which must be of a matching
     * type to the key.
     */
    public <T> void setCaptureParam(@NonNull CaptureRequest.Key<T> key, T value) {
        mPreviewBuilder.set(key, value);
    }

}
