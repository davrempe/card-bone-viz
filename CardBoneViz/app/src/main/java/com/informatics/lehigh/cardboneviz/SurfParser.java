package com.informatics.lehigh.cardboneviz;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Parser for reading in data from .SURF files.
 *
 * Allows a caller to access the vertices, normals, and tris that make up a SURF model meant to
 * be drawn with GL_TRIANGLES.
 */
public class SurfParser {
    private static final String TAG = "SurfParser";

    private InputStream surfStream;

    private final int COORDS_PER_VERTEX = 3;
    private int nVerts;
    private int nTris;

    private float [] vertices;
    private float [] normals;
    private int [] indices;

    /**
     * Constructor for SURF parser.
     * @param surfStream stream from resource file holding SURF data. Use
     *                   getResources().openRawResource() to do this.
     */
    public SurfParser(InputStream surfStream) {
        this.surfStream = surfStream;
    }

    /**
     * Parses the loaded SURF file and stores vertices, normals, and indices in this
     * parser instance.
     */
    public void parse() {
        BufferedReader br = null;

        try {
            InputStreamReader inputReader = new InputStreamReader(surfStream);
            // open reader for surf file
            br = new BufferedReader(inputReader);
            // read line by line
            String curLine = "";
            while ((curLine = br.readLine()) != null) {
                if (!curLine.isEmpty()) {
                    // Pass if it's a comment
                    if (curLine.charAt(0) == '#') {
                        continue;
                    }

                    // check if it's GEOMETRY or TOPOLOGY section start
                    String[] tokens = curLine.split(":");
                    if (tokens.length > 0) {
                        // is start of a section
                        if (tokens[0].trim().equals("GEOMETRY")) {
                            nVerts = Integer.parseInt(tokens[1].trim());
                            vertices = new float[COORDS_PER_VERTEX * nVerts];
                            normals = new float[COORDS_PER_VERTEX * nVerts];
                            // parse the geometry data
                            this.readGeometry(br, nVerts);
                        } else if (tokens[0].trim().equals("TOPOLOGY")) {
                            nTris = Integer.parseInt(tokens[1].trim());
                            indices = new int[3 * nTris];
                            // parse the topology data
                            this.readTopology(br, nTris);
                        }
                    } else {
                        // It's not a line we care about
                        continue;
                    }
                }
            }
        } catch(IOException e) {
            Log.e(TAG, "ERROR READING SURF FILE: " + e.getMessage());
        } finally {
            try {
                // free resources
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "ERROR CLOSING SURF READER: " + e.getMessage());
            }
        }
    }

    /**
     * Reads the geometry section of a SURF file and writes the data
     * to vertices[] and normals[]
     * @param br  the reader currently at start of geometry section
     * @param numLines number of lines that make up the section. This function will only
     *                 read this many lines from file before returning.
     */
    private void readGeometry(BufferedReader br, int numLines) {
        try {
            for (int i = 0; i < numLines; i++) {
                String line = br.readLine();
                String [] tokens = line.split(" ");
                // get vertices on this line
                float [] v = {Float.parseFloat(tokens[0]), Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2])};
                // now normals
                float [] n = {Float.parseFloat(tokens[3]), Float.parseFloat(tokens[4]), Float.parseFloat(tokens[5])};
                // add it to all vertices and normals
                vertices[i * COORDS_PER_VERTEX] = v[0];
                vertices[i * COORDS_PER_VERTEX + 1] = v[1];
                vertices[i * COORDS_PER_VERTEX + 2] = v[2];
                normals[i * COORDS_PER_VERTEX] = n[0];
                normals[i * COORDS_PER_VERTEX + 1] = n[1];
                normals[i * COORDS_PER_VERTEX + 2] = n[2];
            }

            return;
        } catch (IOException e) {
            Log.e(TAG, "ERROR PARSING GEOMETRY");
        }

    }

    /**
     * Reads the topology section of a SURF file and writes the data
     * to indices[].
     * @param br the reader currently at start of topology section
     * @param numLines number of lines that make up the section. This function will only
     *                 read this many lines from file before returning.
     */
    private void readTopology(BufferedReader br, int numLines) {
        try {
            for (int i = 0; i < numLines; i++) {
                String line = br.readLine();
                String [] tokens = line.split(" ");
                // get indices on this line
                int [] ind = {Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2])};
                // add it to all vertices and normals
                indices[i * 3] = ind[0];
                indices[i * 3 + 1] = ind[1];
                indices[i * 3 + 2] = ind[2];
            }

            return;
        } catch (IOException e) {
            Log.e(TAG, "ERROR PARSING TOPOLOGY");
        }
    }

    public int getNumVerts() {
        return nVerts;
    }

    public int getNumTris() {
        return nTris;
    }

    public float[] getVertices() {
        return vertices;
    }

    public float[] getNormals() {
        return normals;
    }

    public int[] getIndices() {
        return indices;
    }
}
