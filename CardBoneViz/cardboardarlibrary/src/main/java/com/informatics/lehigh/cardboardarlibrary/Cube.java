package com.informatics.lehigh.cardboardarlibrary;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import java.util.Vector;

import es.ava.aruco.CameraParameters;
import es.ava.aruco.Marker;
import es.ava.aruco.Utils;
import min3d.core.Object3d;

import com.informatics.lehigh.cardboardarlibrary.CubeDetector;
import com.informatics.lehigh.cardboardarlibrary.CubeConfiguration;

/**
 * Created by Josiah Smith on 6/30/2016.
 */
public class Cube extends Vector<Marker> {
    //fields
    protected CubeConfiguration conf;
    protected Mat Rvec, Tvec;
    protected float markerSizeMeters;
    protected float paddingSizeMeters;
    ////private Object3d object;

    // constructor
    public Cube() {
        Rvec = new Mat(3,1, CvType.CV_64FC1);
        Tvec = new Mat(3,1,CvType.CV_64FC1);
        markerSizeMeters = -1;
        paddingSizeMeters = -1;
    }

    // other methods

    public Mat createCubeImage(int markerSize, CubeConfiguration conf) {
        int sizeY = markerSize*3;
        int sizeX = markerSize*4;
        Mat cubeImage = new Mat(sizeY,sizeX,CvType.CV_8UC1);
        for (int i = 0; i < 6; i++) {
            if (i == 0) {
                Mat subRect = cubeImage.submat(2 * markerSize, (2 * markerSize) + markerSize, 0, markerSize);
            }
//            } else if (i == 5) {
//                Mat subRect = cubeImage.submat(2*markerSize, ())
//            }

        }
        return null;
    }

    public Mat createCubeImage(int markerSize, int[] conf) {
        CubeConfiguration cubeConf = new CubeConfiguration(conf);
        return createCubeImage(markerSize, cubeConf);
    }

    public Mat getRvec() {
        return Rvec;
    }

    public Mat getTvec() {
        return Tvec;
    }

//    public void set3dObject(Object3d object) {
//        this.object = object;
//        double[] matrix = new double[16];
//        Utils.glGetModelViewMatrix(matrix,Rvec,Tvec);
//        this.object.setModelViewMatrix(matrix);
//    }

//    public void draw3dAxis(Mat frame, CameraParameters cp, Scalar color) {
//        Utils.draw3dAxis(frame, cp, color, 2*this.get(0).size, Rvec, Tvec);
//    }
}
