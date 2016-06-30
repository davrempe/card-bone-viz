package com.informatics.lehigh.cardboardarlibrary;

import com.informatics.lehigh.cardboardarlibrary.Cube;
import com.informatics.lehigh.cardboardarlibrary.CubeConfiguration;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;

import java.util.HashMap;
import java.util.Vector;

import es.ava.aruco.CameraParameters;
import es.ava.aruco.Marker;
import es.ava.aruco.MarkerDetector;

/**
 * Created by Josiah Smith on 6/30/2016.
 */
public class CubeDetector {
    private enum thresSuppMethod {FIXED_THRES,ADPT_THRES,CANNY};

    private double thresParam1, thresParam2;
    private thresSuppMethod thresMethod;
    private Mat grey, thres, thres2, hierarchy2;
    private Vector<MatOfPoint> contours2;

    private final static double MIN_DISTANCE = 10;

    public CubeDetector() {
        thresParam1 = thresParam2 = 7;
        thresMethod = thresSuppMethod.ADPT_THRES;
        // TODO
        grey = new Mat();
        thres = new Mat();
        thres2 = new Mat();
        hierarchy2 = new Mat();
        contours2 = new Vector<MatOfPoint>();
    }
        /**
         *
         * Method to find a marker cube in a given Mat frame.
         * @param      in                input color Mat to find cube in.
         * @param      conf              The configuration of the cube.
         * @param      cDetected         Output Vector with detected cubes
         * @param      cp                The Camera Parameters
         * @param      markerSizeMeters  The marker size meters
         */
        public void detect(Mat in, CubeConfiguration conf, Vector<Cube> cDetected, CameraParameters cp, float markerSizeMeters, float paddingSizeMeters) {
            MarkerDetector mDetector = new MarkerDetector();
            Vector<Marker> detectedMarkers = new Vector<Marker>();
            mDetector.detect(in, detectedMarkers, cp, markerSizeMeters);
            if (detectedMarkers.size() != 0) {
                for (int i = 0; i < detectedMarkers.size(); i++) {
                    Marker currentMarker = detectedMarkers.get(i);
                    rotateToCube(currentMarker, conf);
                }
            }


        }

        public static void rotateToCube(Marker marker, CubeConfiguration conf) {
            HashMap<Integer, Integer> cubeLayout = conf.getCubeLayout();

            if (cubeLayout.get(marker.getMarkerId()) == 0) {

            } else if (cubeLayout.get(marker.getMarkerId()) == 1) {

            } else if (cubeLayout.get(marker.getMarkerId()) == 2) {

            } else if (cubeLayout.get(marker.getMarkerId()) == 3) {

            } else if (cubeLayout.get(marker.getMarkerId()) == 4) {

            } else if (cubeLayout.get(marker.getMarkerId()) == 5) {

            }
        }
}
