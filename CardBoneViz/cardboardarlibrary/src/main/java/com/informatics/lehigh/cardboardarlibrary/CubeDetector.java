package com.informatics.lehigh.cardboardarlibrary;

import android.util.Log;

import com.informatics.lehigh.cardboardarlibrary.Cube;
import com.informatics.lehigh.cardboardarlibrary.CubeConfiguration;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point3;

import java.util.HashMap;
import java.util.Vector;

import es.ava.aruco.CameraParameters;
import es.ava.aruco.Marker;
import es.ava.aruco.MarkerDetector;
import es.ava.aruco.Utils;

/**
 * Created by Josiah Smith on 6/30/2016.
 */
public class CubeDetector {
    private static final String TAG = "CubeDetector";

    public CubeDetector() {
    }

    /**
     * Method to find a marker cube in a given Mat frame.
     *
     * @param in               input color Mat to find cube in.
     * @param conf             The configuration of the cube.
     * @param cDetected        Output Vector with detected cubes
     * @param cp               The Camera Parameters
     * @param markerSizeMeters The marker size meters
     * @param paddingSizeMeters The size of the whitespace around each markers
     */
    public void detect(Mat in, CubeConfiguration conf, Vector<Cube> cDetected, CameraParameters cp, float markerSizeMeters, float paddingSizeMeters) {
        MarkerDetector mDetector = new MarkerDetector();
        Vector<Marker> detectedMarkers = new Vector<Marker>();
        mDetector.detect(in, detectedMarkers, cp, markerSizeMeters);
        if (detectedMarkers.size() != 0) {
            Log.d(TAG, "Cube detected with " + String.valueOf(detectedMarkers.size()) + " markers");
            Mat cubeTvec = new Mat();
            Mat cubeRvec = new Mat();

            cubeRvec = calculateCubeRvec(detectedMarkers, conf);
            cubeTvec = calculateCubeTvec(detectedMarkers, paddingSizeMeters);

            Cube detectedCube = new Cube();
            detectedCube.Tvec = cubeTvec;
            detectedCube.Rvec = cubeRvec;
            detectedCube.markerSizeMeters = markerSizeMeters;
            detectedCube.paddingSizeMeters = paddingSizeMeters;
            cDetected.add(detectedCube);
        }
    }

    /**
     * Calculates the CubeRvec based on the input detected markers
     * @param detectedMarkers  input vector of the detected markers
     * @param conf             the configuration of the cube to be detected
     * @return the Rvec of the cube
     */
    private static Mat calculateCubeRvec(Vector<Marker> detectedMarkers, CubeConfiguration conf) {
        HashMap<Integer, Integer> cubeLayout = conf.getCubeLayout();
        double[] totalQuat = {0,0,0,0};
        Mat tempRvec = new Mat();

        //Rotate Rvec based on marker position to align with the correct cubeRvec
        for (int i = 0; i < detectedMarkers.size(); i++) {
            detectedMarkers.get(i).getRvec().copyTo(tempRvec);
            if (cubeLayout.get(detectedMarkers.get(i).getMarkerId()) == 1) {
                rotateXAxis(tempRvec, 90);
                rotateYAxis(tempRvec, 180);
            } else if (cubeLayout.get(detectedMarkers.get(i).getMarkerId()) == 2) {
                rotateXAxis(tempRvec, -90);
                rotateZAxis(tempRvec, -90);
            } else if (cubeLayout.get(detectedMarkers.get(i).getMarkerId()) == 3) {
                rotateXAxis(tempRvec, -90);
                //rotateYAxis(tempRvec, 180);
                //rotateZAxis(tempRvec, 180);
            } else if (cubeLayout.get(detectedMarkers.get(i).getMarkerId()) == 4) {
                rotateXAxis(tempRvec, 90);
                rotateYAxis(tempRvec, 180);
                rotateZAxis(tempRvec, -90);
            } else if (cubeLayout.get(detectedMarkers.get(i).getMarkerId()) == 5) {
                rotateXAxis(tempRvec, 180);
            }

            //Get Angle-Axis Representation
            double x = tempRvec.get(0,0)[0];
            double y = tempRvec.get(1,0)[0];
            double z = tempRvec.get(2,0)[0];
            double a = Math.sqrt((x*x) + (y*y) + (z*z));

            //Normalize X,Y,Z
            x /= a;
            y /= a;
            z /= a;

            //Get Quaternions
            double qw = Math.cos(a/2);
            double qx = Math.sin(a/2)*x;
            double qy = Math.sin(a/2)*y;
            double qz = Math.sin(a/2)*z;

            //Add Marker Quaternion Values to Total Values
            totalQuat[0] += qw;
            totalQuat[1] += qx;
            totalQuat[2] += qy;
            totalQuat[3] += qz;
        }

        //Normalize Total
        double norm = Math.sqrt((totalQuat[0]*totalQuat[0]) + (totalQuat[1]*totalQuat[1]) + (totalQuat[2]*totalQuat[2]) + (totalQuat[3]*totalQuat[3]));
        for (int i = 0; i < totalQuat.length; i++) {
            totalQuat[i] /= norm;
        }

        //Convert Quaternions back to Axis-Angle
        double angle = 2* Math.acos(totalQuat[0]);
        double finalX = totalQuat[1] / Math.sqrt(1 - (totalQuat[0] * totalQuat[0]));
        double finalY = totalQuat[2] / Math.sqrt(1 - (totalQuat[0] * totalQuat[0]));
        double finalZ = totalQuat[3] / Math.sqrt(1 - (totalQuat[0] * totalQuat[0]));

        //Un-Normalize to Create cubeRvec
        finalX *= angle;
        finalY *= angle;
        finalZ *= angle;

        //Create, Populate, and return cubeRvec
        Mat cubeRvec = new Mat(3,1, CvType.CV_64FC1);
        cubeRvec.put(0,0,finalX);
        cubeRvec.put(1,0,finalY);
        cubeRvec.put(2,0,finalZ);

        return cubeRvec;
    }

    /**
     * Calculates the CubeTvec based on the input detected markers
     * @param detectedMarkers  input vector of the detected markers
     * @param paddingSize the size of the whitespace around the markers
     * @return the Tvec of the cube
     */
    private static Mat calculateCubeTvec(Vector<Marker> detectedMarkers, float paddingSize) {

        //The center will be 1/2 markerSize + padding below each marker (-Z)
        Mat centerPoint = new Mat(3,1,CvType.CV_64FC1);
        centerPoint.put(0, 0, 0);
        centerPoint.put(1, 0, 0);
        centerPoint.put(2, 0, ((detectedMarkers.get(0).getSize() / 2) + paddingSize));

        //Setup Matrices and Variables
        Mat result = new Mat();
        double x = 0;
        double y = 0;
        double z = 0;

        for (int i = 0; i < detectedMarkers.size(); i++) {
            Mat tvecTest = detectedMarkers.get(i).getTvec();
            float[] tvecCam = new float[3];
            tvecCam[0] = (float) tvecTest.get(0, 0)[0];
            tvecCam[1] = (float) tvecTest.get(1, 0)[0];
            tvecCam[2] = (float) tvecTest.get(2, 0)[0];

            //Get Marker Rotation Matrix
            Mat rMat = new Mat(3, 3, CvType.CV_64FC1);
            Calib3d.Rodrigues(detectedMarkers.get(i).getRvec(), rMat);

            //Get Marker Translation Vector
            Mat markerTvec = new Mat();
            detectedMarkers.get(i).getTvec().convertTo(markerTvec, CvType.CV_64FC1);

            //Transform CenterPoint to Cube Coordinates using Matrix Multiplication
            Core.gemm(rMat, centerPoint, 1, markerTvec, 1, result, 0);

            Log.d("MainActivity", "CENTER: (" + result.get(0, 0)[0] + ", " + result.get(1, 0)[0] + ", " + result.get(2, 0)[0] + ")");

            //Add Coordinate Values to Total
            x += result.get(0, 0)[0];
            y += result.get(1, 0)[0];
            z += result.get(2, 0)[0];
        }

        //Average Coordinate Values
        x /= detectedMarkers.size();
        y /= detectedMarkers.size();
        z /= detectedMarkers.size();

        //Create, Populate, and Return cubeTvec
        Mat cubeTvec = new Mat(3,1,CvType.CV_64FC1);
        cubeTvec.put(0,0,x);
        cubeTvec.put(1,0,y);
        cubeTvec.put(2,0,z);
        return cubeTvec;
    }


    //Helper Methods//

    /**
     * Rotates a matrix around the X axis by the desired amount
     * @param rotation      The matrix to be rotated
     * @param rotateDegrees The amount to rotate in degrees
     */
    private static void rotateXAxis(Mat rotation, double rotateDegrees) {
        // get the matrix corresponding to the rotation vector
        Mat R = new Mat(3, 3, CvType.CV_64FC1);
        Calib3d.Rodrigues(rotation, R);

        // create the matrix to rotate around the X axis
        // 1, 0, 0
        // 0 cos -sin
        // 0 sin cos
        double[] rot = {
                1, 0, 0,
                0, Math.cos(Math.toRadians(rotateDegrees)), -Math.sin(Math.toRadians(rotateDegrees)),
                0, Math.sin(Math.toRadians(rotateDegrees)), Math.cos(Math.toRadians(rotateDegrees))
        };
        // multiply both matrix
        Mat res = new Mat(3, 3, CvType.CV_64FC1);
        double[] prod = new double[9];
        double[] a = new double[9];
        R.get(0, 0, a);
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                prod[3 * i + j] = 0;
                for (int k = 0; k < 3; k++) {
                    prod[3 * i + j] += a[3 * i + k] * rot[3 * k + j];
                }
            }
        // convert the matrix to a vector with rodrigues back
        res.put(0, 0, prod);
        Calib3d.Rodrigues(res, rotation);
    }

    /**
     * Rotates a matrix around the Y axis by the desired amount
     * @param rotation      The matrix to be rotated
     * @param rotateDegrees The amount to rotate in degrees
     */
    private static void rotateYAxis(Mat rotation, double rotateDegrees) {
        // get the matrix corresponding to the rotation vector
        Mat R = new Mat(3,3,CvType.CV_64FC1);
        Calib3d.Rodrigues(rotation, R);

        // create the matrix to rotate around the Y axis
        // cos 0 sin
        // 0   1  0
        //-sin 0 cos
        double[] rot = {
                Math.cos(Math.toRadians(rotateDegrees)), 0, Math.sin(Math.toRadians(rotateDegrees)),
                0,1,0,
                -Math.sin(Math.toRadians(rotateDegrees)), 0, Math.cos(Math.toRadians(rotateDegrees))
        };
        // multiply both matrix
        Mat res = new Mat(3,3, CvType.CV_64FC1);
        double[] prod = new double[9];
        double[] a = new double[9];
        R.get(0, 0, a);
        for(int i=0;i<3;i++)
            for(int j=0;j<3;j++){
                prod[3*i+j] = 0;
                for(int k=0;k<3;k++){
                    prod[3*i+j] += a[3*i+k]*rot[3*k+j];
                }
            }
        // convert the matrix to a vector with rodrigues back
        res.put(0, 0, prod);
        Calib3d.Rodrigues(res, rotation);
    }

    /**
     * Rotates a matrix around the Z axis by the desired amount
     * @param rotation      The matrix to be rotated
     * @param rotateDegrees The amount to rotate in degrees
     */
    private static void rotateZAxis(Mat rotation, double rotateDegrees) {
        // get the matrix corresponding to the rotation vector
        Mat R = new Mat(3,3,CvType.CV_64FC1);
        Calib3d.Rodrigues(rotation, R);

        // create the matrix to rotate around the Z axis
        // cos -sin  0
        // sin  cos  0
        // 0    0    1
        double[] rot = {
                Math.cos(Math.toRadians(rotateDegrees)), -Math.sin(Math.toRadians(rotateDegrees)), 0,
                Math.sin(Math.toRadians(rotateDegrees)), Math.cos(Math.toRadians(rotateDegrees)), 0,
                0,0,1
        };
        // multiply both matrix
        Mat res = new Mat(3,3, CvType.CV_64FC1);
        double[] prod = new double[9];
        double[] a = new double[9];
        R.get(0, 0, a);
        for(int i=0;i<3;i++)
            for(int j=0;j<3;j++){
                prod[3*i+j] = 0;
                for(int k=0;k<3;k++){
                    prod[3*i+j] += a[3*i+k]*rot[3*k+j];
                }
            }
        // convert the matrix to a vector with rodrigues back
        res.put(0, 0, prod);
        Calib3d.Rodrigues(res, rotation);
    }

}