package edu.gemini.ags.nfiraos

import edu.gemini.spModel.core.{Angle, RBandsList}
import edu.gemini.spModel.gemini.nfiraos.NfiraosOiwfs
import edu.gemini.spModel.gemini.iris.IrisOdgw
import edu.gemini.spModel.target.env.GuideGroup
import edu.gemini.spModel.target.env.GuideProbeTargets
import edu.gemini.shared.util.immutable.ScalaConverters._
import edu.gemini.shared.util.immutable.{None => JNone}
import edu.gemini.pot.ModelConverters._

import scala.annotation.tailrec
import scalaz._
import Scalaz._
import edu.gemini.spModel.nfiraos.NfiraosGuideProbeGroup

case class NfiraosStrehl(avg: Double = .0, rms: Double = .0, min: Double = .0, max: Double = .0)

/**
 * The guideGroup should contain the designation of guide stars to guiders for the tip tilt
 * asterism computed by mascot and the flexure star.
 * There must be 1 to 3 tip tilt guide stars all designated for guiders in the same group
 * (e.g., all Nfiraos or all IRIS On Detector Guide Window).
 * There must be one flexure star taken from the opposite group. For example, if a Nfiraos
 * asterism is used for tiptilt, then the flexure star is a IRIS ODGW star if using IRIS
 * or an F2 OIWFS star if using Flamingos2.
 * If the tip tilt asterism is assigned to the IRIS ODGW group, then the flexure star must
 * be assigned to OIWFS3.
 * Flamingos 2 OIWFS can only ever be used for the flexure star.
 * The NfiraosCatalogSearchCriterion available in the input will contain all the options that
 * need be considered (i.e., the F2 OIWFS "group" will never appear with "tiptilt" type).
 *
 * @param pa position angle that applies to the result
 * @param tiptiltGroup the guide group used for tip tilt correction
 * @param strehl calculated by the mascot algorithm
 * @param guideGroup guide group Contents
 */
case class NfiraosGuideStars(pa: Angle, tiptiltGroup: NfiraosGuideProbeGroup, strehl: NfiraosStrehl, guideGroup: GuideGroup) extends Comparable[NfiraosGuideStars] {

  /**
   * From OT-27: Ranking Results
   *
   * The first order ranking of results is by best (highest) average Strehl ratio. In addition:
   *
   * When searching ODGW asterisms over different PAs, among configurations that
   * give equivalent average Strehls ratios the ones that include ODGW1 stars must
   * excluded. IRIS detector 1 has many bad pixels and must be avoided when possible.
   *
   * When searching over different PAs preference must be given to orientations along
   * the cardinal directions (PA=0,90,180,270). If all orientations are equivalent
   * then PA=0 must be selected. It is more important however to avoid ODGW1.
   *
   * In these rules, an "equivalent" average strehl is defined as anything within 2% average strehl.
   */
  override def compareTo(that: NfiraosGuideStars): Int = {
    val thisContainsOdgw1 = this.guideGroup.contains(IrisOdgw.odgw1)
    val thatContainsOdgw1 = that.guideGroup.contains(IrisOdgw.odgw1)
    if (thisContainsOdgw1 == thatContainsOdgw1) {
      val thisStrel  = this.getAverageStrehlForCompare
      val thatStrehl = that.getAverageStrehlForCompare

      val strehlDifference = (Math.abs(thisStrel - thatStrehl) / ((thisStrel + thatStrehl) / 2.0)) * 100
      if (strehlDifference > 2) {
        thisStrel.compareTo(thatStrehl)
      } else {
        val thisPa = this.pa
        val thatPa = that.pa

        @tailrec
        def matchesCardinalDirection(d: List[Angle]): Int = d match {
          case Nil                       => thisStrel.compareTo(thatStrehl)
          case x :: tail if thisPa === x => 1
          case x :: tail if thatPa === x => -1
          case x :: tail                 => matchesCardinalDirection(tail)
        }

        if (thisPa =/= thatPa) {
          matchesCardinalDirection(NfiraosGuideStars.cardinalDirections)
        } else {
          thisStrel.compareTo(thatStrehl)
        }
      }
    } else {
      thatContainsOdgw1.compareTo(thisContainsOdgw1)
    }
  }


  // Returns the average Strehl value, minus 20% if it is a Nfiraos asterism and oiwfs3 is not assigned to the
  // brightest star.
  //
  // OT-27:
  // When using Nfiraos for tiptilt correction, the brightest star of the asterism (in R)
  // must be assigned to OIWFS3 unless by not doing so the average Strehl can be improved
  // by more than 20%. In this case, the second brightest star should be assigned to OIWFS3
  // as long as the faintness limit (R=17.5) is met.
  protected def getAverageStrehlForCompare: Double = {
    val avg = strehl.avg
    if ("OIWFS" == tiptiltGroup.getKey && !oiwfs3IsBrightest) {
      // Previous code ensures that oiwfs3 is either the brightest or second brightest
      // so this assumes oiwfs3 is the second brightest
      avg - avg * 0.2
    } else {
      avg
    }
  }

  // Returns true if oiwfs3 is the brightest star in the Caanopus asterism
  private def oiwfs3IsBrightest: Boolean = {
    val oiwfs1 = getRLikeMag(guideGroup.get(NfiraosOiwfs.Wfs.oiwfs1).asScalaOpt)
    val oiwfs2 = getRLikeMag(guideGroup.get(NfiraosOiwfs.Wfs.oiwfs2).asScalaOpt)
    val oiwfs3 = getRLikeMag(guideGroup.get(NfiraosOiwfs.Wfs.oiwfs3).asScalaOpt)
    oiwfs3 < oiwfs2 && oiwfs3 < oiwfs1
  }

  /**
   * Find on the guide probe's primary target the value of the R-like magnitude
   */
  private def getRLikeMag(gp: Option[GuideProbeTargets]): Double = {
    val r = for {
      g <- gp
      p <- g.getPrimary.asScalaOpt
      m <- RBandsList.extract(p.toSiderealTarget(None))
    } yield m.value
    r.getOrElse(99.0)
  }

  override def toString: String = {
    import scala.collection.JavaConverters._

    val NoTime = JNone.instance[java.lang.Long]

    val guiders = guideGroup.getReferencedGuiders.asScala.map { gp =>
      val target = guideGroup.get(gp).getValue.getPrimary.getValue
      s"$gp[${target.getRaString(NoTime)},${target.getRaString(NoTime)}]"
    }
    s"NfiraosGuideStars{pa=$pa, tiptilt=${tiptiltGroup.getKey}, avg Strehl=${strehl.avg * 100}, guiders=${guiders.mkString(" ")}}"
  }

}
object NfiraosGuideStars {
  val cardinalDirections = List(Angle.zero, Angle.fromDegrees(90.0), Angle.fromDegrees(180.0), Angle.fromDegrees(270.0))

  implicit val NfiraosGuideStarsOrdering:scala.Ordering[NfiraosGuideStars] = new scala.math.Ordering[NfiraosGuideStars] {
    override def compare(x: NfiraosGuideStars, y: NfiraosGuideStars) = x.compareTo(y)
  }
}