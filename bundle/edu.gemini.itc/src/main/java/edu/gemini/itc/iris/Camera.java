package edu.gemini.itc.iris;

import edu.gemini.itc.base.Instrument;
import edu.gemini.itc.base.TransmissionElement;

/**
 * This represents the transmission of the optics native to the camera.
 */
public class Camera extends TransmissionElement {
    private static final String FILENAME = Iris.getPrefix() +
            "camera" + Instrument.getSuffix();

    public Camera(String directory) {
        super(directory + FILENAME);
    }

    public String toString() {
        return "Camera: 0.02\"/pix";
    }
}
