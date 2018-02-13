package edu.gemini.ags.nfiraos

import edu.gemini.catalog.api._
import edu.gemini.shared.util.immutable.{None => JNone}
import edu.gemini.spModel.core._
import edu.gemini.spModel.gemini.nfiraos.{Nfiraos, NfiraosInstrument}
import edu.gemini.spModel.gemini.iris.{IrisOdgw, Iris}
import edu.gemini.spModel.gemini.obscomp.SPSiteQuality
import edu.gemini.spModel.nfiraos.{NfiraosGuideStarType, NfiraosTipTiltMode}
import edu.gemini.spModel.obs.context.ObsContext
import edu.gemini.spModel.target.SPTarget
import edu.gemini.spModel.target.env.TargetEnvironment
import edu.gemini.spModel.telescope.IssPort
import org.specs2.mutable.Specification
import scala.collection.JavaConverters._

import scalaz._
import Scalaz._

class NfiraosGuideSearchOptionsSpec extends Specification {
  val ra = Angle.fromHMS(3, 19, 48.2341).getOrElse(Angle.zero)
  val dec = Angle.fromDMS(41, 30, 42.078).getOrElse(Angle.zero)
  val target = new SPTarget(ra.toDegrees, dec.toDegrees)
  val targetEnvironment = TargetEnvironment.create(target)
  val inst = new Iris
  inst.setPosAngle(0.0)
  inst.setIssPort(IssPort.SIDE_LOOKING)
  val ctx = ObsContext.create(targetEnvironment, inst, JNone.instance[Site], SPSiteQuality.Conditions.BEST, null, null, JNone.instance())
  val posAngles = new java.util.HashSet[Angle]()

  "NfiraosGuideSearchOptions" should {
    "provide search options for iris in instrument tip tilt mode" in {
      val instrument = NfiraosInstrument.iris
      val tipTiltMode = NfiraosTipTiltMode.instrument

      val options = new NfiraosGuideStarSearchOptions(instrument, tipTiltMode, posAngles)
      val criteria = options.searchCriteria(ctx, None).asScala

      criteria should be size 2
      criteria.head.key should beEqualTo(NfiraosCatalogSearchKey(NfiraosGuideStarType.tiptilt, IrisOdgw.Group.instance))
      criteria.head.criterion.magConstraint should beEqualTo(MagnitudeConstraints(SingleBand(MagnitudeBand.H), FaintnessConstraint(14.5), Some(SaturationConstraint(7.3))))
      criteria(1).key should beEqualTo(NfiraosCatalogSearchKey(NfiraosGuideStarType.flexure, NfiraosOiwfs.Wfs.Group.instance))
      criteria(1).criterion.magConstraint should beEqualTo(MagnitudeConstraints(RBandsList, FaintnessConstraint(16.8), Some(SaturationConstraint(9.3))))
    }
    "provide search options for iris in nfiraos tip tilt mode" in {
      val instrument = NfiraosInstrument.iris
      val tipTiltMode = NfiraosTipTiltMode.nfiraos

      val options = new NfiraosGuideStarSearchOptions(instrument, tipTiltMode, posAngles)
      val criteria = options.searchCriteria(ctx, None).asScala

      criteria should be size 2
      criteria.head.key should beEqualTo(NfiraosCatalogSearchKey(NfiraosGuideStarType.tiptilt, NfiraosOiwfs.Wfs.Group.instance))
      criteria.head.criterion.magConstraint should beEqualTo(MagnitudeConstraints(RBandsList, FaintnessConstraint(16.8), Some(SaturationConstraint(9.3))))
      criteria(1).key should beEqualTo(NfiraosCatalogSearchKey(NfiraosGuideStarType.flexure, IrisOdgw.Group.instance))
      criteria(1).criterion.magConstraint should beEqualTo(MagnitudeConstraints(SingleBand(MagnitudeBand.H), FaintnessConstraint(17.0), Some(SaturationConstraint(8))))
    }
    "provide search options for iris in both tip tilt modes" in {
      val ctx = ObsContext.create(targetEnvironment, inst, JNone.instance[Site], SPSiteQuality.Conditions.BEST, null, null, JNone.instance())
      val instrument = NfiraosInstrument.iris
      val tipTiltMode = NfiraosTipTiltMode.both

      val options = new NfiraosGuideStarSearchOptions(instrument, tipTiltMode, posAngles)
      val criteria = options.searchCriteria(ctx, None).asScala

      criteria should be size 4
      criteria.head.key should beEqualTo(NfiraosCatalogSearchKey(NfiraosGuideStarType.tiptilt, NfiraosOiwfs.Wfs.Group.instance))
      criteria.head.criterion.magConstraint should beEqualTo(MagnitudeConstraints(RBandsList, FaintnessConstraint(16.8), Some(SaturationConstraint(9.3))))
      criteria(1).key should beEqualTo(NfiraosCatalogSearchKey(NfiraosGuideStarType.flexure, IrisOdgw.Group.instance))
      criteria(1).criterion.magConstraint should beEqualTo(MagnitudeConstraints(SingleBand(MagnitudeBand.H), FaintnessConstraint(17.0), Some(SaturationConstraint(8))))
      criteria(2).key should beEqualTo(NfiraosCatalogSearchKey(NfiraosGuideStarType.tiptilt, IrisOdgw.Group.instance))
      criteria(2).criterion.magConstraint should beEqualTo(MagnitudeConstraints(SingleBand(MagnitudeBand.H), FaintnessConstraint(14.5), Some(SaturationConstraint(7.3))))
      criteria(3).key should beEqualTo(NfiraosCatalogSearchKey(NfiraosGuideStarType.flexure, NfiraosOiwfs.Wfs.Group.instance))
      criteria(3).criterion.magConstraint should beEqualTo(MagnitudeConstraints(RBandsList, FaintnessConstraint(16.8), Some(SaturationConstraint(9.3))))
    }
  }
}
