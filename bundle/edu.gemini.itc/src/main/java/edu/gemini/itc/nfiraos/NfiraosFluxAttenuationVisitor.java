package edu.gemini.itc.nfiraos;

import edu.gemini.itc.base.SampledSpectrum;
import edu.gemini.itc.base.SampledSpectrumVisitor;

/**
 * The NfiraosFluxAttenuationVisitor class is designed to adjust the SED for the
 * by the FluxAttenuation factor of nfiraos.
 */
public class NfiraosFluxAttenuationVisitor implements SampledSpectrumVisitor {

    private double fluxAttenuationFactor;


    /**
     * Constructs NfiraosBackgroundVisitor.
     */
    public NfiraosFluxAttenuationVisitor(double fluxAttenuationFactor) {

        this.fluxAttenuationFactor = fluxAttenuationFactor;
    }


    /**
     * Implements the SampledSpectrumVisitor interface
     */
    public void visit(SampledSpectrum sed) {
        //use the sed provided rescale Y instead of above equivalent algorithm
        sed.rescaleY(fluxAttenuationFactor);
    }


    public String toString() {
        return "NfiraosFluxAttenuationVisitor :" + fluxAttenuationFactor;
    }
}
