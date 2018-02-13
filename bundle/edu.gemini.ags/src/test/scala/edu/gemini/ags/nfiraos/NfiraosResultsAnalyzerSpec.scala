package edu.gemini.ags.nfiraos

import edu.gemini.ags.TargetsHelper
import edu.gemini.ags.nfiraos.mascot.MascotProgress
import edu.gemini.ags.nfiraos.mascot.Strehl
import edu.gemini.catalog.votable.TestVoTableBackend
import edu.gemini.shared.skyobject.coords.HmsDegCoordinates
import edu.gemini.shared.util.immutable.{None => JNone}
import edu.gemini.skycalc.Coordinates
import edu.gemini.skycalc.Offset
import edu.gemini.pot.ModelConverters._
import edu.gemini.spModel.core.Magnitude
import edu.gemini.spModel.core._
import edu.gemini.spModel.gemini.flamingos2.Flamingos2
import edu.gemini.spModel.gemini.nfiraos.{Nfiraos, Nfiraos, NfiraosInstrument}
import edu.gemini.spModel.gemini.iris.Iris
import edu.gemini.spModel.gemini.iris.IrisOdgw
import edu.gemini.spModel.gemini.obscomp.SPSiteQuality
import edu.gemini.spModel.gemini.obscomp.SPSiteQuality.Conditions
import edu.gemini.spModel.obs.context.ObsContext
import edu.gemini.spModel.obscomp.SPInstObsComp
import edu.gemini.spModel.target.SPTarget
import edu.gemini.spModel.target.env.TargetEnvironment
import edu.gemini.spModel.telescope.IssPort
import jsky.coords.WorldCoords
import java.util.logging.Logger

import scala.concurrent.duration._
import org.specs2.mutable.SpecificationLike
import AlmostEqual.AlmostEqualOps

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scalaz._
import Scalaz._
import edu.gemini.spModel.nfiraos.NfiraosTipTiltMode

/**
  * See OT-27
  */
class NfiraosResultsAnalyzerSpec extends MascotProgress with SpecificationLike with TargetsHelper {

  private val LOGGER = Logger.getLogger(classOf[NfiraosResultsAnalyzerSpec].getName)

  class TestNfiraosVoTableCatalog(file: String) extends NfiraosVoTableCatalog {
    override val backend = TestVoTableBackend(file)
  }

  val NoTime = JNone.instance[java.lang.Long]

  "NfiraosCatalogResultsSpec" should {
    "support Iris Search on TYC 8345-1155-1" in {
      val base = new WorldCoords("17:25:27.529", "-48:27:24.02")
      val inst = new Iris <| {_.setPosAngle(0.0)} <| {_.setIssPort(IssPort.UP_LOOKING)}
      val tipTiltMode = NfiraosTipTiltMode.nfiraos

      val conditions = SPSiteQuality.Conditions.NOMINAL.sb(SPSiteQuality.SkyBackground.ANY).wv(SPSiteQuality.WaterVapor.ANY)
      val (results, nfiraosGuideStars) = search(inst, base.getRA.toString, base.getDec.toString, tipTiltMode, conditions, new TestNfiraosVoTableCatalog("/nfiraos_TYC_8345_1155_1.xml"))

      val expectedResults = if (tipTiltMode == NfiraosTipTiltMode.both) 4 else 2
      results should have size expectedResults

      results.zipWithIndex.foreach { case (r, i) =>
        LOGGER.info("Result #" + i)
        LOGGER.info(" Criteria:" + r.criterion)
        LOGGER.info(" Results size:" + r.results.size)
      }

      LOGGER.info("nfiraos results: size = " + nfiraosGuideStars.size)
      nfiraosGuideStars should have size 247

      val result = nfiraosGuideStars.head
      result.pa.toDegrees should beCloseTo(0, 0.0001)

      val group = result.guideGroup
      val set = group.getReferencedGuiders
      // Found a star on OIWFS1, OIWFS2, OIWFS3 and ODWG3
      set.contains(NfiraosOiwfs.Wfs.oiwfs1) should beTrue
      set.contains(NfiraosOiwfs.Wfs.oiwfs2) should beTrue
      set.contains(NfiraosOiwfs.Wfs.oiwfs3) should beTrue
      set.contains(IrisOdgw.odgw1) should beFalse
      set.contains(IrisOdgw.odgw2) should beFalse
      set.contains(IrisOdgw.odgw3) should beFalse
      set.contains(IrisOdgw.odgw4) should beTrue

      val oiwfs1 = group.get(NfiraosOiwfs.Wfs.oiwfs1).getValue.getPrimary.getValue
      val oiwfs2 = group.get(NfiraosOiwfs.Wfs.oiwfs2).getValue.getPrimary.getValue
      val oiwfs3 = group.get(NfiraosOiwfs.Wfs.oiwfs3).getValue.getPrimary.getValue
      val odgw4 = group.get(IrisOdgw.odgw4).getValue.getPrimary.getValue
      oiwfs1.getName must beEqualTo("208-152095")
      oiwfs2.getName must beEqualTo("208-152215")
      oiwfs3.getName must beEqualTo("208-152039")
      odgw4.getName must beEqualTo("208-152102")

      val oiwfs1x = Coordinates.create("17:25:27.151", "-48:28:07.67")
      val oiwfs2x = Coordinates.create("17:25:32.541", "-48:27:30.06")
      val oiwfs3x = Coordinates.create("17:25:24.719", "-48:26:58.00")
      val odgw4x = Coordinates.create("17:25:27.552", "-48:27:23.86")

      (Angle.fromDegrees(oiwfs1x.getRaDeg)  ~= Angle.fromDegrees(oiwfs1.getSkycalcCoordinates(NoTime).getValue.getRaDeg)) should beTrue
      (Angle.fromDegrees(oiwfs1x.getDecDeg) ~= Angle.fromDegrees(oiwfs1.getSkycalcCoordinates(NoTime).getValue.getDecDeg)) should beTrue
      (Angle.fromDegrees(oiwfs2x.getRaDeg)  ~= Angle.fromDegrees(oiwfs2.getSkycalcCoordinates(NoTime).getValue.getRaDeg)) should beTrue
      (Angle.fromDegrees(oiwfs2x.getDecDeg) ~= Angle.fromDegrees(oiwfs2.getSkycalcCoordinates(NoTime).getValue.getDecDeg)) should beTrue
      (Angle.fromDegrees(oiwfs3x.getRaDeg)  ~= Angle.fromDegrees(oiwfs3.getSkycalcCoordinates(NoTime).getValue.getRaDeg)) should beTrue
      (Angle.fromDegrees(oiwfs3x.getDecDeg) ~= Angle.fromDegrees(oiwfs3.getSkycalcCoordinates(NoTime).getValue.getDecDeg)) should beTrue
      (Angle.fromDegrees(odgw4x.getRaDeg)  ~= Angle.fromDegrees(odgw4.getSkycalcCoordinates(NoTime).getValue.getRaDeg)) should beTrue
      (Angle.fromDegrees(odgw4x.getDecDeg) ~= Angle.fromDegrees(odgw4.getSkycalcCoordinates(NoTime).getValue.getDecDeg)) should beTrue

      val oiwfs1Mag = oiwfs1.getMagnitude(MagnitudeBand._r).map(_.value).get
      val oiwfs2Mag = oiwfs2.getMagnitude(MagnitudeBand.UC).map(_.value).get
      val oiwfs3Mag = oiwfs3.getMagnitude(MagnitudeBand._r).map(_.value).get
      oiwfs3Mag < oiwfs1Mag && oiwfs2Mag < oiwfs1Mag should beTrue
    }
    "support Iris Search on SN-1987A" in {
      val base = new WorldCoords("05:35:28.020", "-69:16:11.07")
      val inst = new Iris <| {_.setPosAngle(0.0)} <| {_.setIssPort(IssPort.UP_LOOKING)}
      val tipTiltMode = NfiraosTipTiltMode.nfiraos

      val (results, nfiraosGuideStars) = search(inst, base.getRA.toString, base.getDec.toString, tipTiltMode, SPSiteQuality.Conditions.NOMINAL.sb(SPSiteQuality.SkyBackground.ANY), new TestNfiraosVoTableCatalog("/nfiraos_sn1987A.xml"))

      val expectedResults = if (tipTiltMode == NfiraosTipTiltMode.both) 4 else 2
      results should have size expectedResults

      results.zipWithIndex.foreach { case (r, i) =>
        LOGGER.info("Result #" + i)
        LOGGER.info(" Criteria:" + r.criterion)
        LOGGER.info(" Results size:" + r.results.size)
      }

      LOGGER.info("nfiraos results: size = " + nfiraosGuideStars.size)
      nfiraosGuideStars should have size 135

      val result = nfiraosGuideStars.head
      result.pa.toDegrees should beCloseTo(0, 0.0001)

      val group = result.guideGroup
      val set = group.getReferencedGuiders
      // Found a star on OIWFS1, OIWFS2, OIWFS3 and ODWG2
      set.contains(NfiraosOiwfs.Wfs.oiwfs1) should beTrue
      set.contains(NfiraosOiwfs.Wfs.oiwfs2) should beTrue
      set.contains(NfiraosOiwfs.Wfs.oiwfs3) should beTrue
      set.contains(IrisOdgw.odgw1) should beFalse
      set.contains(IrisOdgw.odgw2) should beTrue
      set.contains(IrisOdgw.odgw3) should beFalse
      set.contains(IrisOdgw.odgw4) should beFalse

      val oiwfs1 = group.get(NfiraosOiwfs.Wfs.oiwfs1).getValue.getPrimary.getValue
      val oiwfs2 = group.get(NfiraosOiwfs.Wfs.oiwfs2).getValue.getPrimary.getValue
      val oiwfs3 = group.get(NfiraosOiwfs.Wfs.oiwfs3).getValue.getPrimary.getValue
      val odgw2 = group.get(IrisOdgw.odgw2)  .getValue.getPrimary.getValue
      oiwfs1.getName must beEqualTo("104-014597")
      oiwfs2.getName must beEqualTo("104-014608")
      oiwfs3.getName must beEqualTo("104-014547")
      odgw2.getName must beEqualTo("104-014556")

      val oiwfs1x = Coordinates.create("05:35:32.630", "-69:15:48.64")
      val oiwfs2x = Coordinates.create("05:35:36.409", "-69:16:24.17")
      val oiwfs3x = Coordinates.create("05:35:18.423", "-69:16:30.67")
      val odgw2x = Coordinates.create("05:35:23.887", "-69:16:18.20")

      (Angle.fromDegrees(oiwfs1x.getRaDeg)  ~= Angle.fromDegrees(oiwfs1.getSkycalcCoordinates(NoTime).getValue.getRaDeg)) should beTrue
      (Angle.fromDegrees(oiwfs1x.getDecDeg) ~= Angle.fromDegrees(oiwfs1.getSkycalcCoordinates(NoTime).getValue.getDecDeg)) should beTrue
      (Angle.fromDegrees(oiwfs2x.getRaDeg)  ~= Angle.fromDegrees(oiwfs2.getSkycalcCoordinates(NoTime).getValue.getRaDeg)) should beTrue
      (Angle.fromDegrees(oiwfs2x.getDecDeg) ~= Angle.fromDegrees(oiwfs2.getSkycalcCoordinates(NoTime).getValue.getDecDeg)) should beTrue
      (Angle.fromDegrees(oiwfs3x.getRaDeg)  ~= Angle.fromDegrees(oiwfs3.getSkycalcCoordinates(NoTime).getValue.getRaDeg)) should beTrue
      (Angle.fromDegrees(oiwfs3x.getDecDeg) ~= Angle.fromDegrees(oiwfs3.getSkycalcCoordinates(NoTime).getValue.getDecDeg)) should beTrue
      (Angle.fromDegrees(odgw2x.getRaDeg)  ~= Angle.fromDegrees(odgw2.getSkycalcCoordinates(NoTime).getValue.getRaDeg)) should beTrue
      (Angle.fromDegrees(odgw2x.getDecDeg) ~= Angle.fromDegrees(odgw2.getSkycalcCoordinates(NoTime).getValue.getDecDeg)) should beTrue

      val oiwfs1Mag = oiwfs1.getMagnitude(MagnitudeBand.UC).map(_.value).get
      val oiwfs2Mag = oiwfs2.getMagnitude(MagnitudeBand.UC).map(_.value).get
      val oiwfs3Mag = oiwfs3.getMagnitude(MagnitudeBand.UC).map(_.value).get
      oiwfs3Mag < oiwfs1Mag && oiwfs2Mag < oiwfs1Mag should beTrue
    }
    "support Iris Search on M6" in {
      val base = new WorldCoords("17:40:20.000", "-32:15:12.00")
      val inst = new Iris
      val tipTiltMode = NfiraosTipTiltMode.nfiraos

      val (results, nfiraosGuideStars) = search(inst, base.getRA.toString, base.getDec.toString, tipTiltMode, SPSiteQuality.Conditions.NOMINAL.sb(SPSiteQuality.SkyBackground.ANY), new TestNfiraosVoTableCatalog("/nfiraos_m6.xml"))

      val expectedResults = if (tipTiltMode == NfiraosTipTiltMode.both) 4 else 2
      results should have size expectedResults

      results.zipWithIndex.foreach { case (r, i) =>
        LOGGER.info("Result #" + i)
        LOGGER.info(" Criteria:" + r.criterion)
        LOGGER.info(" Results size:" + r.results.size)
      }

      LOGGER.info("nfiraos results: size = " + nfiraosGuideStars.size)
      nfiraosGuideStars should have size 98

      val result = nfiraosGuideStars.head
      result.pa.toDegrees should beCloseTo(90, 0.0001)

      val group = result.guideGroup
      val set = group.getReferencedGuiders
      // Found a star on OIWFS1, OIWFS2, OIWFS3 and ODWG2
      set.contains(NfiraosOiwfs.Wfs.oiwfs1) should beTrue
      set.contains(NfiraosOiwfs.Wfs.oiwfs2) should beTrue
      set.contains(NfiraosOiwfs.Wfs.oiwfs3) should beTrue
      set.contains(IrisOdgw.odgw1) should beFalse
      set.contains(IrisOdgw.odgw2) should beTrue
      set.contains(IrisOdgw.odgw3) should beFalse
      set.contains(IrisOdgw.odgw4) should beFalse

      val oiwfs1 = group.get(NfiraosOiwfs.Wfs.oiwfs1).getValue.getPrimary.getValue
      val oiwfs2 = group.get(NfiraosOiwfs.Wfs.oiwfs2).getValue.getPrimary.getValue
      val oiwfs3 = group.get(NfiraosOiwfs.Wfs.oiwfs3).getValue.getPrimary.getValue
      val odgw2 = group.get(IrisOdgw.odgw2)  .getValue.getPrimary.getValue
      oiwfs1.getName must beEqualTo("289-128909")
      oiwfs2.getName must beEqualTo("289-128878")
      oiwfs3.getName must beEqualTo("289-128908")
      odgw2.getName must beEqualTo("289-128891")

      val oiwfs1x = Coordinates.create("17:40:21.743", "-32:14:54.04")
      val oiwfs2x = Coordinates.create("17:40:16.855", "-32:15:55.83")
      val oiwfs3x = Coordinates.create("17:40:21.594", "-32:15:50.38")
      val odgw2x = Coordinates.create("17:40:19.295", "-32:14:58.34")

      (Angle.fromDegrees(oiwfs1x.getRaDeg)  ~= Angle.fromDegrees(oiwfs1.getSkycalcCoordinates(NoTime).getValue.getRaDeg)) should beTrue
      (Angle.fromDegrees(oiwfs1x.getDecDeg) ~= Angle.fromDegrees(oiwfs1.getSkycalcCoordinates(NoTime).getValue.getDecDeg)) should beTrue
      (Angle.fromDegrees(oiwfs2x.getRaDeg)  ~= Angle.fromDegrees(oiwfs2.getSkycalcCoordinates(NoTime).getValue.getRaDeg)) should beTrue
      (Angle.fromDegrees(oiwfs2x.getDecDeg) ~= Angle.fromDegrees(oiwfs2.getSkycalcCoordinates(NoTime).getValue.getDecDeg)) should beTrue
      (Angle.fromDegrees(oiwfs3x.getRaDeg)  ~= Angle.fromDegrees(oiwfs3.getSkycalcCoordinates(NoTime).getValue.getRaDeg)) should beTrue
      (Angle.fromDegrees(oiwfs3x.getDecDeg) ~= Angle.fromDegrees(oiwfs3.getSkycalcCoordinates(NoTime).getValue.getDecDeg)) should beTrue
      (Angle.fromDegrees(odgw2x.getRaDeg)  ~= Angle.fromDegrees(odgw2.getSkycalcCoordinates(NoTime).getValue.getRaDeg)) should beTrue
      (Angle.fromDegrees(odgw2x.getDecDeg) ~= Angle.fromDegrees(odgw2.getSkycalcCoordinates(NoTime).getValue.getDecDeg)) should beTrue

      val oiwfs1Mag = oiwfs1.getMagnitude(MagnitudeBand.UC).map(_.value).get
      val oiwfs2Mag = oiwfs2.getMagnitude(MagnitudeBand.UC).map(_.value).get
      val oiwfs3Mag = oiwfs3.getMagnitude(MagnitudeBand.UC).map(_.value).get
      oiwfs3Mag < oiwfs1Mag && oiwfs2Mag < oiwfs1Mag should beTrue
    }
    "support Iris Search on BPM 37093" in {
      val base = new WorldCoords("12:38:49.820", "-49:48:00.20")
      val inst = new Iris
      val tipTiltMode = NfiraosTipTiltMode.nfiraos

      val (results, nfiraosGuideStars) = search(inst, base.getRA.toString, base.getDec.toString, tipTiltMode, SPSiteQuality.Conditions.NOMINAL.sb(SPSiteQuality.SkyBackground.ANY), new TestNfiraosVoTableCatalog("/nfiraos_bpm_37093.xml"))

      val expectedResults = if (tipTiltMode == NfiraosTipTiltMode.both) 4 else 2
      results should have size expectedResults

      results.zipWithIndex.foreach { case (r, i) =>
        LOGGER.info("Result #" + i)
        LOGGER.info(" Criteria:" + r.criterion)
        LOGGER.info(" Results size:" + r.results.size)
      }

      LOGGER.info("nfiraos results: size = " + nfiraosGuideStars.size)
      nfiraosGuideStars should have size 54

      val result = nfiraosGuideStars.head
      result.pa.toDegrees should beCloseTo(0, 0.0001)

      val group = result.guideGroup
      val set = group.getReferencedGuiders
      // Found a star on OIWFS1, OIWFS2, OIWFS3 and ODWG4
      set.contains(NfiraosOiwfs.Wfs.oiwfs1) should beTrue
      set.contains(NfiraosOiwfs.Wfs.oiwfs2) should beTrue
      set.contains(NfiraosOiwfs.Wfs.oiwfs3) should beTrue
      set.contains(IrisOdgw.odgw1) should beFalse
      set.contains(IrisOdgw.odgw2) should beFalse
      set.contains(IrisOdgw.odgw3) should beFalse
      set.contains(IrisOdgw.odgw4) should beTrue

      val oiwfs2 = group.get(NfiraosOiwfs.Wfs.oiwfs2).getValue.getPrimary.getValue.getSkycalcCoordinates(NoTime).getValue
      val oiwfs3 = group.get(NfiraosOiwfs.Wfs.oiwfs3).getValue.getPrimary.getValue.getSkycalcCoordinates(NoTime).getValue
      val odgw4 = group.get(IrisOdgw.odgw4)  .getValue.getPrimary.getValue.getSkycalcCoordinates(NoTime).getValue

      val oiwfs2x = Coordinates.create("12:38:44.500", "-49:47:58.38")
      val oiwfs3x = Coordinates.create("12:38:50.005", "-49:48:00.89")
      val odgw4x = Coordinates.create("12:38:50.005", "-49:48:00.89")

      (Angle.fromDegrees(oiwfs2x.getRaDeg) ~= Angle.fromDegrees(oiwfs2.getRaDeg)) should beTrue
      (Angle.fromDegrees(oiwfs2x.getDecDeg) ~= Angle.fromDegrees(oiwfs2.getDecDeg)) should beTrue
      (Angle.fromDegrees(oiwfs3x.getRaDeg) ~= Angle.fromDegrees(oiwfs3.getRaDeg)) should beTrue
      (Angle.fromDegrees(oiwfs3x.getDecDeg) ~= Angle.fromDegrees(oiwfs3.getDecDeg)) should beTrue
      (Angle.fromDegrees(odgw4x.getRaDeg) ~= Angle.fromDegrees(odgw4.getRaDeg)) should beTrue
      (Angle.fromDegrees(odgw4x.getDecDeg) ~= Angle.fromDegrees(odgw4.getDecDeg)) should beTrue
    }
    "sort targets by R magnitude" in {
      val st1 = target("n", edu.gemini.spModel.core.Coordinates.zero, List(new Magnitude(10.0, MagnitudeBand.J)))
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1)).head should beEqualTo(st1)

      val st2 = target("n", edu.gemini.spModel.core.Coordinates.zero, List(new Magnitude(15.0, MagnitudeBand.J)))
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2)).head should beEqualTo(st1)
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2))(1) should beEqualTo(st2)

      val st3 = target("n", edu.gemini.spModel.core.Coordinates.zero, List(new Magnitude(15.0, MagnitudeBand.R)))
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3)).head should beEqualTo(st3)
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3))(1) should beEqualTo(st1)
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3))(2) should beEqualTo(st2)

      val st4 = target("n", edu.gemini.spModel.core.Coordinates.zero, List(new Magnitude(9.0, MagnitudeBand.R)))
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3, st4)).head should beEqualTo(st4)
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3, st4))(1) should beEqualTo(st3)
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3, st4))(2) should beEqualTo(st1)
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3, st4))(3) should beEqualTo(st2)

      val st5 = target("n", edu.gemini.spModel.core.Coordinates.zero, List(new Magnitude(19.0, MagnitudeBand.R)))
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3, st4, st5)).head should beEqualTo(st4)
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3, st4, st5))(1) should beEqualTo(st3)
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3, st4, st5))(2) should beEqualTo(st5)
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3, st4, st5))(3) should beEqualTo(st1)
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3, st4, st5))(4) should beEqualTo(st2)
    }
    "sort targets by R-like magnitude" in {
      val st1 = target("n", edu.gemini.spModel.core.Coordinates.zero, List(new Magnitude(10.0, MagnitudeBand.J)))
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1)).head should beEqualTo(st1)

      val st2 = target("n", edu.gemini.spModel.core.Coordinates.zero, List(new Magnitude(15.0, MagnitudeBand.J)))
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2)).head should beEqualTo(st1)
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2))(1) should beEqualTo(st2)

      val st3 = target("n", edu.gemini.spModel.core.Coordinates.zero, List(new Magnitude(15.0, MagnitudeBand.R)))
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3)).head should beEqualTo(st3)
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3))(1) should beEqualTo(st1)
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3))(2) should beEqualTo(st2)

      val st4 = target("n", edu.gemini.spModel.core.Coordinates.zero, List(new Magnitude(9.0, MagnitudeBand._r)))
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3, st4)).head should beEqualTo(st4)
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3, st4))(1) should beEqualTo(st3)
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3, st4))(2) should beEqualTo(st1)
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3, st4))(3) should beEqualTo(st2)

      val st5 = target("n", edu.gemini.spModel.core.Coordinates.zero, List(new Magnitude(19.0, MagnitudeBand.UC)))
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3, st4, st5)).head should beEqualTo(st4)
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3, st4, st5))(1) should beEqualTo(st3)
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3, st4, st5))(2) should beEqualTo(st5)
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3, st4, st5))(3) should beEqualTo(st1)
      NfiraosResultsAnalyzer.sortTargetsByBrightness(List(st1, st2, st3, st4, st5))(4) should beEqualTo(st2)
    }

  }

  def search(inst: SPInstObsComp, raStr: String, decStr: String, tipTiltMode: NfiraosTipTiltMode, conditions: Conditions, catalog: TestNfiraosVoTableCatalog): (List[NfiraosCatalogSearchResults], List[NfiraosGuideStars]) = {
    import scala.collection.JavaConverters._

    val coords = new WorldCoords(raStr, decStr)
    val baseTarget = new SPTarget(coords.getRaDeg, coords.getDecDeg)
    val env = TargetEnvironment.create(baseTarget)
    val offsets = new java.util.HashSet[Offset]
    val obsContext = ObsContext.create(env, inst, JNone.instance[Site], conditions, offsets, new Nfiraos, JNone.instance())
    val baseRA = Angle.fromDegrees(coords.getRaDeg)
    val baseDec = Angle.fromDegrees(coords.getDecDeg)
    val base = new HmsDegCoordinates.Builder(baseRA.toOldModel, baseDec.toOldModel).build
    val instrument = if (inst.isInstanceOf[Flamingos2]) NfiraosInstrument.flamingos2 else NfiraosInstrument.iris

    val posAngles = Set(Angle.zero, Angle.fromDegrees(90), Angle.fromDegrees(180), Angle.fromDegrees(270)).asJava

    val options = new NfiraosGuideStarSearchOptions(instrument, tipTiltMode, posAngles)

    val results = Await.result(catalog.search(obsContext, base.toNewModel, options, scala.None)(implicitly), 5.seconds)
    val nfiraosResults = NfiraosResultsAnalyzer.analyze(obsContext, posAngles, results.asJava, scala.None)
    (results, nfiraosResults.asScala.toList)
  }

  def progress(s: Strehl, count: Int, total: Int, usable: Boolean): Boolean = true

  def setProgressTitle(s: String) {
    LOGGER.info(s)
  }
}