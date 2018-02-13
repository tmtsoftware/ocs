package edu.gemini.ags.nfiraos

import edu.gemini.ags.api.AgsMagnitude.{MagnitudeCalc, MagnitudeTable}
import edu.gemini.pot.ModelConverters._
import edu.gemini.catalog.api._
import edu.gemini.spModel.core._
import edu.gemini.spModel.gemini.nfiraos.{NfiraosOiwfs, NfiraosInstrument}
import edu.gemini.spModel.gemini.iris.{Iris, IrisOdgw}
import edu.gemini.spModel.guide.{GuideProbe, GuideSpeed}
import edu.gemini.spModel.obs.context.ObsContext
import edu.gemini.spModel.rich.shared.immutable._
import edu.gemini.spModel.gemini.obscomp.SPSiteQuality.Conditions
import edu.gemini.skycalc

import scala.collection.JavaConverters._
import scalaz._
import Scalaz._
import edu.gemini.spModel.nfiraos.NfiraosGuideStarType

/**
 * A magnitude table defined in the same way as we have done since before 2015A
 * (that is, with predefined magnitude limits).
 */
object NfiraosMagnitudeTable extends MagnitudeTable {

  private def magLimits(bands: BandsList, fl: Double, sl: Double): MagnitudeConstraints =
    MagnitudeConstraints(bands, FaintnessConstraint(fl), SaturationConstraint(sl).some)

  def apply(ctx: ObsContext, probe: GuideProbe): Option[MagnitudeCalc] = {
    def mc(nominalLimits: MagnitudeConstraints): MagnitudeCalc = new MagnitudeCalc() {
      def apply(conds: Conditions, speed: GuideSpeed): MagnitudeConstraints =
        conds.adjust(nominalLimits)
    }

    def lookup(site: Site): Option[MagnitudeCalc] =
      ((site, probe) match {
        case (Site.GS, odgw: IrisOdgw)  =>
          Some(IrisOdgwMagnitudeLimitsCalculator.nfiraosMagnitudeConstraint(NfiraosGuideStarType.flexure, MagnitudeBand.H.some))

        case (Site.GS, can: NfiraosOiwfs.Wfs) =>
          Some(IrisOiwfsWfsMagnitudeLimitsCalculator.nfiraosMagnitudeConstraint(NfiraosGuideStarType.tiptilt, MagnitudeBand.R.some))

        case _                           =>
          None
      }).map(mc)

    ctx.getSite.asScalaOpt.flatMap(lookup)
  }

  /**
   * IRIS, Nfiraos, and F2 require special handling for magnitude limits for Nfiraos.
   */
  trait LimitsCalculator {
    def nfiraosMagnitudeConstraint(starType: NfiraosGuideStarType, nirBand: Option[MagnitudeBand]): MagnitudeConstraints

    def adjustNfiraosMagnitudeConstraintForJava(starType: NfiraosGuideStarType, nirBand: Option[MagnitudeBand], conditions: Conditions): MagnitudeConstraints =
      conditions.adjust(nfiraosMagnitudeConstraint(starType, nirBand))

    def searchCriterionBuilder(name: String, radiusLimit: skycalc.Angle, instrument: NfiraosInstrument, magConstraint: MagnitudeConstraints, posAngles: java.util.Set[Angle]): CatalogSearchCriterion = {
      val radiusConstraint = RadiusConstraint.between(Angle.zero, radiusLimit.toNewModel)
      val searchOffset = instrument.getOffset.asScalaOpt.map(_.toNewModel)
      val searchPA = posAngles.asScala.headOption
      CatalogSearchCriterion(name, radiusConstraint, magConstraint, searchOffset, searchPA)
    }

  }

  /**
   * Unfortunately, we need a lookup table for the Mascot algorithm to map NfiraosInstruments to NfiraosMagnitudeLimitsCalculators.
   * We cannot include this in the NfiraosInstrument as this would cause dependency issues and we want to decouple these.
   */
  lazy val NfiraosInstrumentToMagnitudeLimitsCalculator = Map[NfiraosInstrument, LimitsCalculator](
    NfiraosInstrument.iris      -> IrisOdgwMagnitudeLimitsCalculator,
    NfiraosInstrument.flamingos2 -> Flamingos2OiwfsMagnitudeLimitsCalculator
  )

  lazy val IrisOdgwMagnitudeLimitsCalculator = new LimitsCalculator {
    /**
     * The map formerly in Iris.Filter.
     */
    private val MagnitudeLimitsMap = Map[Tuple2[NfiraosGuideStarType, BandsList], MagnitudeConstraints](
      (NfiraosGuideStarType.flexure, SingleBand(MagnitudeBand.J)) -> magLimits(SingleBand(MagnitudeBand.J), 17.2, 8.0),
      (NfiraosGuideStarType.flexure, SingleBand(MagnitudeBand.H)) -> magLimits(SingleBand(MagnitudeBand.H), 17.0, 8.0),
      (NfiraosGuideStarType.flexure, SingleBand(MagnitudeBand.K)) -> magLimits(SingleBand(MagnitudeBand.K), 18.2, 8.0),
      (NfiraosGuideStarType.tiptilt, SingleBand(MagnitudeBand.J)) -> magLimits(SingleBand(MagnitudeBand.J), 14.2, 7.1),
      (NfiraosGuideStarType.tiptilt, SingleBand(MagnitudeBand.H)) -> magLimits(SingleBand(MagnitudeBand.H), 14.5, 7.3),
      (NfiraosGuideStarType.tiptilt, SingleBand(MagnitudeBand.K)) -> magLimits(SingleBand(MagnitudeBand.K), 13.5, 6.5)
    )

    override def nfiraosMagnitudeConstraint(starType: NfiraosGuideStarType, nirBand: Option[MagnitudeBand]): MagnitudeConstraints= {
      val filter = nirBand.fold(Iris.Filter.H)(band => Iris.Filter.getFilter(band, Iris.Filter.H))
      MagnitudeLimitsMap((starType, filter.getCatalogBand.getValue))
    }
  }

  /**
   * Since Nfiraos is not explicitly listed in NfiraosInstrument, it must be visible outside of the table in order to
   * be used directly by Mascot, since it cannot be looked up through the NfiraosInstrumentToMagnitudeLimitsCalculator map.
   */
  trait IrisOiwfsWfsCalculator extends LimitsCalculator {
    def getNominalMagnitudeConstraints(oiwfs: NfiraosOiwfs.Wfs): MagnitudeConstraints
  }

  lazy val IrisOiwfsWfsMagnitudeLimitsCalculator = new IrisOiwfsWfsCalculator {
    override def nfiraosMagnitudeConstraint(starType: NfiraosGuideStarType, nirBand: Option[MagnitudeBand]) =
      magLimits(RBandsList, 16.3, 8.8)

    override def getNominalMagnitudeConstraints(oiwfs: NfiraosOiwfs.Wfs): MagnitudeConstraints =
      magLimits(RBandsList, 16.3, 8.8)
  }

  private lazy val Flamingos2OiwfsMagnitudeLimitsCalculator = new LimitsCalculator {
    override def nfiraosMagnitudeConstraint(starType: NfiraosGuideStarType, nirBand: Option[MagnitudeBand]) =
      magLimits(RBandsList, 18.0, 9.5)
  }
}