package edu.gemini.itc.base;

/**
 * Common interface for AO systems (Nfiraos and Altair).
 */
public interface AOSystem {

    SampledSpectrumVisitor getBackgroundVisitor();
    SampledSpectrumVisitor getTransmissionVisitor();
    SampledSpectrumVisitor getFluxAttenuationVisitor();
    SampledSpectrumVisitor getHaloFluxAttenuationVisitor();
    double getAOCorrectedFWHM();

}
