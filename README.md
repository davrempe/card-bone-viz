# card-bone-viz
A Google Cardboard Android application to visualize the alignment of bone models from MRI and Ultrasound scans.

To run, simply the clone the repo, then open the CardBoneViz directory as a project in Android Studio. Everything should be properly configured and you can run it right away on a device.

For the calibration activity, you will need to print out [this calibration pattern](https://raw.githubusercontent.com/LongerVision/OpenCV_Examples/master/markers/pattern_acircles.png).

This project inspired the creation of, and heavily uses, the [GAR Library](https://github.com/davrempe/cardboardAR-lib). Check it out if you're interested in creating Android AR apps for the Google Cardboard. 

Currently this application is capable of calibrating the device camera, rendering the back-facing camera view in stereo, tracking a marker cube, importing a bone model from a SURF file, and drawing both axes and a bone model on the tracked markers.
