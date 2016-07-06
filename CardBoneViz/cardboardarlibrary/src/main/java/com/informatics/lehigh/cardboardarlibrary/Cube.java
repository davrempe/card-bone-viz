package com.informatics.lehigh.cardboardarlibrary;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import java.util.HashMap;
import java.util.Map;
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
        cubeImage.setTo(new Scalar(255));

        HashMap<Integer,Integer> cubeLayout = conf.getCubeLayout();
        for (Map.Entry<Integer, Integer> entry : cubeLayout.entrySet()) {
            if (entry.getValue() == 0) {
                Mat subRect = cubeImage.submat(2 * markerSize, 3 * markerSize, 0, markerSize);
                Mat marker = Marker.createMarkerImage(entry.getKey(), markerSize);
                marker.copyTo(subRect);
            } else if (entry.getValue() == 1) {
                Mat subRect = cubeImage.submat(0, markerSize, markerSize, 2 * markerSize);
                Mat marker = Marker.createMarkerImage(entry.getKey(), markerSize);
                marker.copyTo(subRect);
            } else if (entry.getValue() == 2) {
                Mat subRect = cubeImage.submat(markerSize, 2 * markerSize, markerSize, 2 * markerSize);
                Mat marker = Marker.createMarkerImage(entry.getKey(), markerSize);
                marker.copyTo(subRect);
            } else if (entry.getValue() == 3) {
                Mat subRect = cubeImage.submat(2 * markerSize, 3 * markerSize, markerSize, 2 * markerSize);
                Mat marker = Marker.createMarkerImage(entry.getKey(), markerSize);
                marker.copyTo(subRect);
            } else if (entry.getValue() == 4) {
                Mat subRect = cubeImage.submat(3 * markerSize, 4 * markerSize, markerSize, 2 * markerSize);
                Mat marker = Marker.createMarkerImage(entry.getKey(), markerSize);
                marker.copyTo(subRect);
            } else {
                Mat subRect = cubeImage.submat(2 * markerSize, 3 * markerSize, 2 * markerSize, 3 * markerSize );
                Mat marker = Marker.createMarkerImage(entry.getKey(), markerSize);
                marker.copyTo(subRect);
            }

        }
        return cubeImage;
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
