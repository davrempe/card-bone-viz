package com.informatics.lehigh.cardboneviz;


import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.util.Size;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;

/**
 * Class for constantly processing incoming image data in order to track
 * marker on ultrasound wand. It assumes that OpenCV has already been loaded.
 */
public class UltrasoundTracker implements Runnable {

    /** Image reader used to access current camera image */
    private ImageReader mImgReader;
    /** The translation vector from the most recently processed frame */
    private volatile float tvec[] = {0.0f, 0.0f, 0.0f};
    /** The rotation vector from the most recently processed frame */
    private volatile float rvec[] = {0.0f, 0.0f, 0.0f};
    /** True if the run function should be running, false otherwise */
    private volatile boolean running = true;

    public UltrasoundTracker(ImageReader imgReader) {
        mImgReader = imgReader;
    }

    /** Safely terminates the tracking process */
    public void terminate() {
        running = false;
    }

    /**
     * @return The translation vector from the most recently processed frame.
     */
    public float[] getTvec() {
        return tvec;
    }

    /**
     * @return The rotation vector from the most recently processed frame.
     */
    public float[] getRvec() {
        return rvec;
    }

    @Override
    public void run() {
        while (running) {
            // get the currently captured image
            Image curImg = mImgReader.acquireLatestImage();
            if (curImg != null) {
                Mat rgba = getCvColorImage(curImg);

                // TODO implement actual processing

                // release
                curImg.close();
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
