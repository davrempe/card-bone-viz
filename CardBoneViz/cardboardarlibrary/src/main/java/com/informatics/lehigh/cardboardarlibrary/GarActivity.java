package com.informatics.lehigh.cardboardarlibrary;

import android.content.Context;
import android.graphics.ImageFormat;
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
import android.media.ImageReader;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import java.util.ArrayList;
import java.util.List;

public abstract class GarActivity extends GvrActivity implements GvrView.StereoRenderer {

    protected static final String TAG = "GarActivity";

    //
    // CONSTANTS
    //
    private static final float CAMERA_Z = 0.01f;
    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100.0f;

    //
    // Physical camera access related members
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
    /** ImageReader used to access camera feed for image processing */
    private ImageReader mProcessingReader;
    /** Surface from ImageReader to be drawn to for camera image processing */
    private Surface mProcessingSurface;
    /** Number of images that the processing reader is able to hold simultaneously */
    private int mProcessingReaderBufferSize = 5;

    //
    // OpenGL-related members
    //
    /** Camera matrix */
    private float[] mCamera;
    /** Frame buffer for camera feed */
    private int mFboIdCamera;
    /** Frame buffer for 3d scene */
    private int mFboIdObjects;
    /** ID of texture that holds camera feed from SurfaceTexture */
    private int mCameraTextureID;
    /** ID of screen texture that holds camera feed */
    private int mScreenCameraTextureID;
    /** ID of screen texture that holds overlaid 3D scene */
    private int mScreenObjectsTextureID;
    /** GLUtil instance */
    private GLUtil glutil;

    //
    // Renderers
    //
    CameraTextureRenderer camTexRenderer;
    StereoScreenRenderer screenRenderer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //
        // Setup Renderers
        //
        camTexRenderer = new CameraTextureRenderer(this);
        screenRenderer = new StereoScreenRenderer(this);

        //
        // Setup OpenGL-related things
        //
        mCamera = new float[16];
        glutil = new GLUtil(getResources());

        // Initialize physical camera-related things
        initializePhysicalCamera();
        // Initialize Google VR View
        initializeGvrView();
        // Get surfaces to write physical camera images to
        List<Surface> surfaces = setupCaptureSurfaces();
        // Opens the camera
        openPhysicalCamera(surfaces);

    }

    /**
     * Initializes physical-camera related things like the camera
     * manager and image size.
     */
    private void initializePhysicalCamera() {
        try {
            mCamManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
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
     * Opens the front facing camera device
     * and binds its data to a GL texture, as well as any additional
     * Surfaces provided. By default, captured images default to auto-focus and white-balance, but
     * this may be changed by using {@link #setCaptureParam setCaptureParam}.
     * @param addSurfaces - Additional surfaces to draw the camera image to. You must include ALL surfaces you plan
     *                    to draw the camera image to, even if it's not every frame. This may be any Surface detailed
     *                    in {@link android.hardware.camera2.CameraDevice#createCaptureSession createCaptureSession}.
     */
    private void openPhysicalCamera(final List<Surface> addSurfaces) {
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
                // (this is called after onSurfaceCreated so texture should be available)
                // TODO possibly loop until not null in case this doesn't hold
                mSurfaceTexture = new SurfaceTexture(mCameraTextureID);
                mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                mGlSurface = new Surface(mSurfaceTexture);

                try {
                    mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                } catch (CameraAccessException e){
                    e.printStackTrace();
                }

                // Create list of all possible surfaces we may draw to
                List<Surface> allSurfaces = new ArrayList<Surface>(addSurfaces);
                allSurfaces.add(mGlSurface);
                try {
                    mCameraDevice.createCaptureSession(allSurfaces, ccCall, null);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

                // add surfaces to builder
                for (int i = 0; i < allSurfaces.size(); i++) {
                    mPreviewBuilder.addTarget(allSurfaces.get(i));
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

        try {
            mCamManager.openCamera(mCameraID, cdCall, null);
        } catch (CameraAccessException cae) {
            Log.e(TAG, "COULD NOT ACCESS CAMERA");
        }
    }

    private void initializeGvrView() {
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

    /**
     * Sets up and returns the Surfaces to be drawn to by the back-facing
     * camera capture besides the one rendered in front of the viewer. This function
     * must return ALL surfaces you plan to draw to, whether that will
     * happen every frame or not. This may be any Surface detailed
     *                    in {@link android.hardware.camera2.CameraDevice#createCaptureSession createCaptureSession}.
     * <br></br><br></br>
     * By default, a single Surface is created from an ImageReader in
     * YUV_420_888 format that holds 5 images, meant to be used for image processing purposes.
     * To create additional surfaces that should receive the camera feed
     * simply override this function. If you want to disable or enable drawing to any surfaces
     * at any time use {@link #disableSurfaces disableSurfaces} and {@link #enableSurfaces enableSurfaces}.<br></br><br></br>
     * To access the default processing ImageReader and Surface, call
     * {@link #getProcessingReader getProcessingReader} and
     * {@link #getProcessingSurface getProcessingSurface}, respectively. To edit the
     * processing reader buffer size use {@link #setProcessingReaderBufferSize  setProcessingReaderBufferSize}.
     * <br></br><br></br>
     * By default, captured images default to auto-focus and white-balance, but
     * this may be changed by using {@link #setCaptureParam setCaptureParam}.
     * @return
     */
    protected List<Surface> setupCaptureSurfaces() {
        // We give it 5 max images by default so stack can fill while processing top image,
        // this allows StereoScreenRenderer to keep rendering at a high frame rate since
        // the capture waits to finish until the images on the ImageReader stack
        // are all closed.
        mProcessingReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                ImageFormat.YUV_420_888, mProcessingReaderBufferSize);
        mProcessingSurface = mProcessingReader.getSurface();
        ArrayList<Surface> surfList = new ArrayList<Surface>();
        surfList.add(mProcessingSurface);

        return surfList;
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
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
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        // We will render both the camera feed and 3D scene to be augmented
        // from the user to different textures using framebuffers. Then
        // render those textures to the stereo screen.
        int[] fbos = new int[2];
        GLES20.glGenFramebuffers(2, fbos, 0);
        mFboIdCamera = fbos[0];
        mFboIdObjects = fbos[1];

        int[] textures = new int[2];
        GLES20.glGenTextures(2, textures, 0);
        mScreenCameraTextureID = textures[0];
        mScreenObjectsTextureID = textures[1];

        //
        // First set up framebuffer for the camera feed texture
        //
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFboIdCamera);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mScreenCameraTextureID);
        // want same width and height as image form camera
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mPreviewSize.getWidth(),
                mPreviewSize.getHeight(), 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER,GL10.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        // bind to framebuffer
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, mScreenCameraTextureID, 0);
        // cleanup
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        //
        // Next set up framebuffer for the 3D objects texture, need depth component
        //
        int[] renderBufArr = new int[1];
        GLES20.glGenRenderbuffers(1, renderBufArr, 0);
        int renderBuffID = renderBufArr[0];

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFboIdObjects);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mScreenObjectsTextureID);
        // want same width and height as image form camera
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mPreviewSize.getWidth(),
                mPreviewSize.getHeight(), 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER,GL10.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        // set up renderbuffer
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, renderBuffID);
        // TODO GL_DEPTH_COMPONENT may be an issue
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT,
                mPreviewSize.getWidth(), mPreviewSize.getHeight());
        // bind to framebuffer
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, mScreenObjectsTextureID, 0);
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
                GLES20.GL_RENDERBUFFER, renderBuffID);
        // cleanup
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        // Initialize renderers
        camTexRenderer.init();
        // get texture to create SurfaceTexture
        mCameraTextureID = camTexRenderer.getCameraTexture();
        screenRenderer.init();
        // give textures to use for rendering screen
        screenRenderer.setCameraTexture(mScreenCameraTextureID);
        screenRenderer.setObjectsTexture(mScreenObjectsTextureID);

        glutil.checkGLError("initRenderers");

    }

    @Override
    public void onNewFrame(HeadTransform headTransform) {
        // capture image to use
        try {
            mCameraCaptureSession.capture(mPreviewBuilder.build(), mCapCall, new Handler(Looper.getMainLooper()));
        } catch (RuntimeException | CameraAccessException ex) {
            Log.e(TAG, "Error capturing: " + ex.getMessage());
        }

        // update the camera surface texture with the new image
        mSurfaceTexture.updateTexImage();

        // Update the renderers
        camTexRenderer.update(headTransform);
        screenRenderer.update(headTransform);

        // Draw current camera view to texture through frame buffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFboIdCamera);
        GLES20.glViewport(0, 0, mPreviewSize.getWidth(), mPreviewSize.getHeight());

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        float[] iden = new float[16];
        Matrix.setIdentityM(iden, 0);
        // don't need view or perspective so just pass in identity
        camTexRenderer.draw(iden, iden);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        // Build the camera matrix.
        Matrix.setLookAtM(mCamera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

        glutil.checkGLError("onReadyToDraw");
    }

    @Override
    public void onDrawEye(Eye eye) {
        // Apply the eye transformation to the camera.
        float[] viewMat = new float[16];
        Matrix.multiplyMM(viewMat, 0, eye.getEyeView(), 0, mCamera, 0);
        // Get the perspective matrix for bone model
        float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);

        // First draw object scene texture through frame buffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFboIdObjects);
        GLES20.glViewport(0, 0, mPreviewSize.getWidth(), mPreviewSize.getHeight());

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        drawObjects(viewMat, perspective);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        // Now draw actual screen for cardboard viewer
        // TODO did the eye viewport get changed by changing above?
        eye.getViewport().setGLViewport();
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        screenRenderer.draw(viewMat, perspective);
    }

    /**
     * Draws the 3D scene to be laid over the current back-facing camera view.
     * These are the augmented portions of the application. This will be called
     * twice per frame, one for the left eye, and again for the right. Anything
     * drawn with an alpha value of 0.0 will be treated as background and filtered
     * from the final render to show the camera view in the background.
     * @param view The view matrix to use for eye being drawn.
     * @param perspective The perspective matrix to use for the eye being drawn.
     */
    abstract protected void drawObjects(float[] view, float[] perspective);

    @Override
    public void onFinishFrame(Viewport viewport) {}

    /**
     * Called when the Cardboard trigger is pulled.
     */
    @Override
    public void onCardboardTrigger() {
        Log.i(TAG, "onCardboardTrigger");
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

    /**
     * Sets the number of images that the ImageReader for image processing
     * is able to hold. A larger number will take up more memory, but allow
     * more time for image processing without affecting the stereo display
     * frame rate.
     * @param numImages number of images for the buffer to hold
     */
    public void setProcessingReaderBufferSize(int numImages) {
        mProcessingReaderBufferSize = numImages;
        // reset image reader and surface
        mProcessingReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                ImageFormat.YUV_420_888, mProcessingReaderBufferSize);
        mProcessingSurface = mProcessingReader.getSurface();
    }

    /**
     * @return the ImageReader containing the back-facing camera feed
     */
    public ImageReader getProcessingReader() {
        return mProcessingReader;
    }

    /**
     * @return the processing Surface containing the back-facing camera feed
     */
    public Surface getProcessingSurface() {
        return mProcessingSurface;
    }

    /**
     * Disables the surfaces in the given list from being drawn to
     * by the back-facing camera capture.
     * @param surfaces
     */
    public void disableSurfaces(List<Surface> surfaces) {
        for (Surface surface : surfaces) {
            mPreviewBuilder.removeTarget(surface);
        }
    }

    /**
     * Enables the surfaces in the given list to be drawn to
     * by the back-facing camera capture.
     * @param surfaces
     */
    public void enableSurfaces(List<Surface> surfaces) {
        for (Surface surface : surfaces) {
            mPreviewBuilder.addTarget(surface);
        }
    }
}

