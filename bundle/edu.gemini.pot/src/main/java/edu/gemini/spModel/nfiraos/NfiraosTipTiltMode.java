package edu.gemini.spModel.nfiraos;

/**
 * The user can constrain the search for tip tilt correction (mascot asterisms) to
 * use only Nfiraos OIWFS, IRIS, or both.
 *
 * See OT-21
 */
public enum NfiraosTipTiltMode {
    nfiraos, instrument, both
}
