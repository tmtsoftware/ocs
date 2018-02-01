package edu.gemini.spModel.nfiraos;

import edu.gemini.skycalc.Angle;
import edu.gemini.spModel.guide.GuideProbeGroup;

/**
 * Defines key aspects of the catalog search.
 *
 * See OT-21
 */
public interface NfiraosGuideProbeGroup extends GuideProbeGroup {
    Angle getRadiusLimits();
}
