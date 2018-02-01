package edu.gemini.itc.nfiraos;

import edu.gemini.itc.base.ITCConstants;
import edu.gemini.itc.base.TransmissionElement;

/**
 * The NfiraosTransmissionVisitor is designed to adjust the SED for the
 * Tranmsission of the Nfiraos optics.
 */
public final class NfiraosTransmissionVisitor extends TransmissionElement {

    /**
     * The NfiraosTrans constructor
     */
    public NfiraosTransmissionVisitor() {

        super(Nfiraos.NFIRAOS_LIB + "/" +
                Nfiraos.NFIRAOS_PREFIX +
                Nfiraos.NFIRAOS_TRANSMISSION_FILENAME +
                ITCConstants.DATA_SUFFIX);
    }

    public String toString() {
        return ("NfiraosTransmissionVisitor");
    }
}
