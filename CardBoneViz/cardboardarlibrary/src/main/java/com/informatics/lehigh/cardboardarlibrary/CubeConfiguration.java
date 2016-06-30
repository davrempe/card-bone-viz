package com.informatics.lehigh.cardboardarlibrary;

import org.opencv.core.Mat;

import java.util.HashMap;
import java.util.Vector;

import es.ava.aruco.CameraParameters;
import es.ava.aruco.Marker;
import es.ava.aruco.MarkerDetector;
import com.informatics.lehigh.cardboardarlibrary.Cube;
import com.informatics.lehigh.cardboardarlibrary.CubeDetector;

/**
 * Created by Josiah Smith on 6/30/2016.
 */
public class CubeConfiguration {

    protected HashMap<Integer, Integer> cubeLayout;

    //////////////////////////////////////////////////////
    //                                                  //
    //                       ////////////               //
    //                       //        //               //
    //                       //    0   //               //
    //                       //        //               //
    //   //////////////////////////////////////////     //
    //   //        //        //        //        //     //
    //   //    1   //   2    //    3   //    4   //     //
    //   //        //        //        //        //     //
    //   //////////////////////////////////////////     //
    //                       //        //               //
    //                       //    5   //               //
    //                       //        //               //
    //                       ////////////               //
    //                                                  //
    //////////////////////////////////////////////////////

    //Takes in an array of marker ids and creates a map of the id to the position they reside in
    public CubeConfiguration(int[] cubeLayout) {
        //TODO check that all ids are distinct and valid
        for (int i = 0; i < cubeLayout.length; i++) {
            this.cubeLayout.put(cubeLayout[i], i);
        }

    }

    public HashMap<Integer, Integer> getCubeLayout() {
        return cubeLayout;
    }
}