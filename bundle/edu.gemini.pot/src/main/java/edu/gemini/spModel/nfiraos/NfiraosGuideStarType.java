package edu.gemini.spModel.nfiraos;

/**
 * There are two types of guide stars, flexure correction and tiptilt correction.
 * The search algorithm will find 1 flexure star and 1 to 3 tiptilt stars (using mascot).
 * 
 * See OT-21
 */
public enum NfiraosGuideStarType {
    flexure, tiptilt
}
