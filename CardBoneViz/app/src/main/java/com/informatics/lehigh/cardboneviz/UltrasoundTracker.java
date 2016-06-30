package com.informatics.lehigh.cardboneviz;


import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.util.Vector;

import es.ava.aruco.CameraParameters;
import es.ava.aruco.Marker;
import es.ava.aruco.MarkerDetector;

/**
 * Class for constantly processing incoming image data in order to track
 * marker on ultrasound wand. It should be run as a thread. It assumes that OpenCV has already been loaded.
 */
public class UltrasoundTracker implements Runnable {

    private static final String TAG = "UltrasoundTracker";

    /** Image reader used to access current camera image */
    private ImageReader mImgReader;
    /** The size of the marker to track in meters */
    private float mMarkerSize;
    /** The translation vector from the most recently processed frame */
    private volatile Mat tvec;
    /** The rotation vector from the most recently processed frame */
    private volatile Mat rvec;
    /** True if the run function should be running, false otherwise */
    private volatile boolean running = true;
    /** True if there is new marker information available */
    private volatile boolean newMarker = false;
    /** True if there the last frame processed detected a marker */
    private volatile boolean markerDetected = false;

    /**
     * Creates a new UltrasoundTracker
     * @param imgReader - the image reader that will be used to get images from. This should be
     *                  an incoming stream from a camera on the device.
     * @param markerSizeMeters - the size, in meters, of the Aruco marker to be tracked.
     */
    public UltrasoundTracker(ImageReader imgReader, float markerSizeMeters) {
        mImgReader = imgReader;
        mMarkerSize = markerSizeMeters;
    }

    /** Safely terminates the tracking process */
    public void terminate() {
        running = false;
    }

    /**
     * @return The translation vector from the most recently processed frame or
     * null if no frame has been processed yet.
     */
    public Mat getTvec() {
        return tvec;
    }

    /**
     * @return The rotation vector from the most recently processed frame or
     * null if no frame has been processed yet.
     */
    public Mat getRvec() {
        return rvec;
    }

    /**
     * @return true if in the last frame processed, there was a marker detected
     */
    public boolean isMarkerDetected() {
        return markerDetected;
    }

    /**
     * @return true if there is new marker information available that has not already
     * been requested through {@link #getMarkerParams getMarkerParams}.
     */
    public boolean isNewMarkerAvailable() { return newMarker; }

    /**
     * Returns an array of both the translation and rotation vectors
     * {tvec, rvec}
     * @return
     */
    public Mat[] getMarkerParams() {
        newMarker = false;
        return new Mat [] {tvec, rvec};
    }

    @Override
    public void run() {
        while (running) {
            // get the currently captured image
            Image curImg = mImgReader.acquireLatestImage();
            if (curImg != null) {
                //Log.d(TAG, "PROCESSING FRAME");
                Mat rgba = getCvColorImage(curImg);
                curImg.close();

                MarkerDetector markerDetector = new MarkerDetector();
                Vector<Marker> detectedMarkers = new Vector<>();
                CameraParameters camParams = new CameraParameters();
                String externalDir = Environment.getExternalStorageDirectory().toString();
                camParams.readFromFile(externalDir + "/camCalib/camCalibData.csv");
                markerDetector.detect(rgba, detectedMarkers, camParams, mMarkerSize);
                if (detectedMarkers.size() != 0) {
                    int testRot = detectedMarkers.get(0).getRotations();
                    Log.d(TAG, "ROTATION ID: " + String.valueOf(testRot));
                    rvec = detectedMarkers.get(0).getRvec();
                    tvec = detectedMarkers.get(0).getTvec();
                    newMarker = true;
                    markerDetected = true;
                    Log.i(TAG, "MARKER DETECTED");
                } else {
                    markerDetected = false;
                }

            }
        }
    }

     /**
     * Converts the given image to a CV RGBA Mat.
     * @param img
     * @return
     */
    private Mat getCvColorImage(Image img) {
        // convert image to cv matrix
        Mat yuv = imageToMat(img);
        // convert yuv mat to rgba
        Mat rgba = new Mat(img.getHeight(), img.getWidth(), CvType.CV_8UC4);
        Imgproc.cvtColor(yuv, rgba, Imgproc.COLOR_YUV2RGBA_I420);

        return rgba;
    }

    /**
     * Takes an Android Image in the YUV_420_888 format and returns an OpenCV Mat.
     * From: https://gist.github.com/camdenfullmer/dfd83dfb0973663a7974
     * @param image Image in the YUV_420_888 format.
     * @return OpenCV Mat.
     */
    public static Mat imageToMat(Image image) {
        ByteBuffer buffer;
        int rowStride;
        int pixelStride;
        int width = image.getWidth();
        int height = image.getHeight();
        int offset = 0;

        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[image.getWidth() * image.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];

        for (int i = 0; i < planes.length; i++) {
            buffer = planes[i].getBuffer();
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            int w = (i == 0) ? width : width / 2;
            int h = (i == 0) ? height : height / 2;
            for (int row = 0; row < h; row++) {
                int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
                if (pixelStride == bytesPerPixel) {
                    int length = w * bytesPerPixel;
                    buffer.get(data, offset, length);

                    // Advance buffer the remainder of the row stride, unless on the last row.
                    // Otherwise, this will throw an IllegalArgumentException because the buffer
                    // doesn't include the last padding.
                    if (h - row != 1) {
                        buffer.position(buffer.position() + rowStride - length);
                    }
                    offset += length;
                } else {

                    // On the last row only read the width of the image minus the pixel stride
                    // plus one. Otherwise, this will throw a BufferUnderflowException because the
                    // buffer doesn't include the last padding.
                    if (h - row == 1) {
                        buffer.get(rowData, 0, width - pixelStride + 1);
                    } else {
                        buffer.get(rowData, 0, rowStride);
                    }

                    for (int col = 0; col < w; col++) {
                        data[offset++] = rowData[col * pixelStride];
                    }
                }
            }
        }

        // Finally, create the Mat.
        Mat mat = new Mat(height + height / 2, width, CvType.CV_8UC1);
        mat.put(0, 0, data);

        return mat;
    }
}
