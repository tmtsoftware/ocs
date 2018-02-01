package edu.gemini.itc.nfiraos;

import edu.gemini.itc.base.*;

/**
 * The NfiraosBackgroundVisitor class is designed to adjust the SED for the
 * background given off by nfiraos.
 */
public class NfiraosBackgroundVisitor implements SampledSpectrumVisitor {

    private ArraySpectrum _nfiraosBack = null;

    /**
     * Constructs NfiraosBackgroundVisitor.
     */
    public NfiraosBackgroundVisitor() {

        _nfiraosBack = new DefaultArraySpectrum(
                Nfiraos.NFIRAOS_LIB + "/" +
                        Nfiraos.NFIRAOS_PREFIX +
                        Nfiraos.NFIRAOS_BACKGROUND_FILENAME +
                        ITCConstants.DATA_SUFFIX);
    }


    /**
     * Implements the SampledSpectrumVisitor interface
     */
    public void visit(SampledSpectrum sed) {
        for (int i = 0; i < sed.getLength(); i++) {
            sed.setY(i, _nfiraosBack.getY(sed.getX(i)) + sed.getY(i));
        }
    }


    public String toString() {
        return "NfiraosBackgroundVisitor ";
    }
}
