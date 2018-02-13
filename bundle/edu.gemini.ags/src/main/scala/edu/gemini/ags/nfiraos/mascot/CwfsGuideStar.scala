package edu.gemini.ags.nfiraos.mascot

import edu.gemini.spModel.obs.context.ObsContext
import edu.gemini.spModel.gemini.nfiraos.NfiraosOiwfs
import edu.gemini.skycalc.Coordinates

/**
 * Defines the default bandpass and filter for OIWFS
 */
class OiwfsGuideStar extends GuideStarType {
  def filter(ctx: ObsContext, magLimits: MagLimits, star: Star): Boolean = {
    val coordinates = new Coordinates(star.target.coordinates.ra.toAngle.toDegrees, star.target.coordinates.dec.toDegrees)
    !NfiraosOiwfs.instance.getProbesInRange(coordinates, ctx).isEmpty && magLimits.filter(star)
  }
}
