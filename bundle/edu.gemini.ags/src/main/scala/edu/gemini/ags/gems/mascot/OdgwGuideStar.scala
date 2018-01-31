package edu.gemini.ags.gems.mascot

import edu.gemini.spModel.obs.context.ObsContext
import edu.gemini.spModel.gemini.iris.IrisDetectorArray
import edu.gemini.skycalc.Coordinates

class OdgwGuideStar extends GuideStarType {
  def filter(ctx: ObsContext, magLimits: MagLimits, star: Star): Boolean = {
    val coords = new Coordinates(star.target.coordinates.ra.toAngle.toDegrees, star.target.coordinates.dec.toAngle.toDegrees)
    val idOpt = IrisDetectorArray.instance.getId(coords, ctx)
    !idOpt.isEmpty && magLimits.filter(star)
  }
}

