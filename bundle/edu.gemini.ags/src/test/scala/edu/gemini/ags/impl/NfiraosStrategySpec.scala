package edu.gemini.ags.impl

import edu.gemini.ags.api.{AgsAnalysis, AgsGuideQuality, AgsStrategy}
import edu.gemini.ags.api.AgsStrategy.Estimate
import edu.gemini.ags.conf.ProbeLimitsTable
import edu.gemini.ags.nfiraos.{CatalogSearchCriterion, NfiraosCatalogSearchCriterion, NfiraosCatalogSearchKey}
import edu.gemini.catalog.api._
import edu.gemini.catalog.votable.TestVoTableBackend
import edu.gemini.shared.util.immutable.{None => JNone, Some => JSome}
import edu.gemini.spModel.core._
import edu.gemini.spModel.core.AngleSyntax._
import edu.gemini.pot.ModelConverters._
import edu.gemini.spModel.gemini.nfiraos.NfiraosOiwfs.Wfs
import edu.gemini.spModel.gemini.nfiraos.{Nfiraos, Nfiraos}
import edu.gemini.spModel.gemini.iris.{Iris, IrisOdgw}
import edu.gemini.spModel.gemini.obscomp.SPSiteQuality
import edu.gemini.spModel.nfiraos.{NfiraosGuideStarType, NfiraosTipTiltMode}
import edu.gemini.spModel.guide.GuideSpeed
import edu.gemini.spModel.obs.context.ObsContext
import edu.gemini.spModel.target.SPTarget
import edu.gemini.spModel.target.env.TargetEnvironment
import edu.gemini.spModel.target.obsComp.PwfsGuideProbe
import edu.gemini.spModel.telescope.IssPort
import org.specs2.mutable.Specification
import AlmostEqual.AlmostEqualOps
import edu.gemini.spModel.gemini.obscomp.SPSiteQuality.{CloudCover, ImageQuality, SkyBackground, WaterVapor}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scalaz._
import Scalaz._

case class TestNfiraosStrategy(file: String) extends NfiraosStrategy {
  override val backend = TestVoTableBackend(file)
}

class NfiraosStrategySpec extends Specification {

  "NfiraosStrategy" should {
    "support estimate" in {
      val ra = Angle.fromHMS(3, 19, 48.2341).getOrElse(Angle.zero)
      val dec = Angle.fromDMS(41, 30, 42.078).getOrElse(Angle.zero)
      val target = new SPTarget(ra.toDegrees, dec.toDegrees)
      val env = TargetEnvironment.create(target)
      val inst = new Iris <| {_.setPosAngle(0.0)} <| {_.setIssPort(IssPort.UP_LOOKING)}
      val iris = IrisOdgw.values().toList
      val nfiraos = NfiraosOiwfs.Wfs.values().toList
      val pwfs1 = List(PwfsGuideProbe.pwfs1)

      val ctx = ObsContext.create(env, inst, new JSome(Site.GS), SPSiteQuality.Conditions.BEST, null, new Nfiraos, JNone.instance())

      val estimate = TestNfiraosStrategy("/nfiraosstrategyquery.xml").estimate(ctx, ProbeLimitsTable.loadOrThrow())(implicitly)
      Await.result(estimate, 1.minute) should beEqualTo(Estimate.GuaranteedSuccess)
    }
    "support search" in {
      val ra = Angle.fromHMS(3, 19, 48.2341).getOrElse(Angle.zero)
      val dec = Angle.fromDMS(41, 30, 42.078).getOrElse(Angle.zero)
      val target = new SPTarget(ra.toDegrees, dec.toDegrees)
      val env = TargetEnvironment.create(target)
      val inst = new Iris <| {_.setPosAngle(0.0)} <| {_.setIssPort(IssPort.UP_LOOKING)}
      val conditions = SPSiteQuality.Conditions.NOMINAL.sb(SPSiteQuality.SkyBackground.ANY)
      val ctx = ObsContext.create(env, inst, new JSome(Site.GS), conditions , null, new Nfiraos, JNone.instance())
      val tipTiltMode = NfiraosTipTiltMode.instrument

      val posAngles = Set.empty[Angle]

      val results = Await.result(TestNfiraosStrategy("/nfiraosstrategyquery.xml").search(tipTiltMode, ctx, posAngles, scala.None)(implicitly), 1.minute)
      results should be size 2

      results.head.criterion should beEqualTo(NfiraosCatalogSearchCriterion(NfiraosCatalogSearchKey(NfiraosGuideStarType.tiptilt, IrisOdgw.Group.instance), CatalogSearchCriterion("On-detector Guide Window tiptilt", RadiusConstraint.between(Angle.zero, Angle.fromDegrees(0.01666666666665151)), MagnitudeConstraints(SingleBand(MagnitudeBand.H), FaintnessConstraint(14.5), scala.Option(SaturationConstraint(7.3))), scala.Option(Offset(0.0014984027777700248.degrees[OffsetP], 0.0014984027777700248.degrees[OffsetQ])), scala.None)))
      results(1).criterion should beEqualTo(NfiraosCatalogSearchCriterion(NfiraosCatalogSearchKey(NfiraosGuideStarType.flexure, Wfs.Group.instance), CatalogSearchCriterion("Nfiraos Wave Front Sensor flexure", RadiusConstraint.between(Angle.zero, Angle.fromDegrees(0.01666666666665151)), MagnitudeConstraints(RBandsList, FaintnessConstraint(15.8), scala.Option(SaturationConstraint(8.3))), scala.Option(Offset(0.0014984027777700248.degrees[OffsetP], 0.0014984027777700248.degrees[OffsetQ])), scala.None)))
      results.head.results should be size 5
      results(1).results should be size 4
    }
    "support search/select and analyze on Pal 1 gives guide stars on the northern hemisphere" in {
      val ra = Angle.fromHMS(3, 33, 20.040).getOrElse(Angle.zero)
      val dec = Angle.fromDMS(79, 34, 51.80).getOrElse(Angle.zero)
      val target = new SPTarget(ra.toDegrees, dec.toDegrees)
      val env = TargetEnvironment.create(target)
      val inst = new Iris <| {_.setPosAngle(0.0)} <| {_.setIssPort(IssPort.UP_LOOKING)}
      val conditions = SPSiteQuality.Conditions.NOMINAL
      val ctx = ObsContext.create(env, inst, new JSome(Site.GS), conditions, null, new Nfiraos, JNone.instance())
      val tipTiltMode = NfiraosTipTiltMode.nfiraos

      val posAngles = Set(ctx.getPositionAngle, Angle.zero, Angle.fromDegrees(90), Angle.fromDegrees(180), Angle.fromDegrees(270))

      testSearchOnNominal("/nfiraos_pal1.xml", ctx, tipTiltMode, posAngles, 2, 2)

      val nfiraosStrategy = TestNfiraosStrategy("/nfiraos_pal1.xml")
      val selection = Await.result(nfiraosStrategy.select(ctx, ProbeLimitsTable.loadOrThrow())(implicitly), 5.minutes)
      selection.map(_.posAngle) should beSome(Angle.zero)
      val assignments = ~selection.map(_.assignments)
      assignments should be size 2

      assignments.exists(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs2) should beTrue
      val oiwfs2 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs2).map(_.guideStar)
      assignments.exists(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs3) should beTrue
      val oiwfs3 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs3).map(_.guideStar)

      // Check coordinates
      oiwfs2.map(_.name) should beSome("848-004584")
      oiwfs3.map(_.name) should beSome("848-004582")

      val oiwfs2x = Coordinates(RightAscension.fromAngle(Angle.fromHMS(3, 33, 21.838).getOrElse(Angle.zero)), Declination.fromAngle(Angle.fromDMS(79, 35, 38.20).getOrElse(Angle.zero)).getOrElse(Declination.zero))
      oiwfs2.map(_.coordinates ~= oiwfs2x) should beSome(true)
      val oiwfs3x = Coordinates(RightAscension.fromAngle(Angle.fromHMS(3, 33, 20.936).getOrElse(Angle.zero)), Declination.fromAngle(Angle.fromDMS(79, 34, 56.93).getOrElse(Angle.zero)).getOrElse(Declination.zero))
      oiwfs3.map(_.coordinates ~= oiwfs3x) should beSome(true)

      // Check magnitudes are sorted correctly
      val mag2 = oiwfs2.flatMap(RBandsList.extract).map(_.value)
      val mag3 = oiwfs3.flatMap(RBandsList.extract).map(_.value)
      (mag3 < mag2) should beTrue

      // Analyze as a whole
      val newCtx = selection.map(_.applyTo(ctx)).getOrElse(ctx)
      val analysis = nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow())
      analysis.collect {
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs2, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs2 => NfiraosOiwfs.Wfs.oiwfs2
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs3, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs3 => NfiraosOiwfs.Wfs.oiwfs3
      } should beEqualTo(List(NfiraosOiwfs.Wfs.oiwfs2, NfiraosOiwfs.Wfs.oiwfs3))

      // Analyze per probe
      oiwfs2.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs2, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs2, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)
      oiwfs3.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs3, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs3, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)

      // Test estimate: 2-star asterism grants 2/3 chance of success.
      val estimate = nfiraosStrategy.estimate(ctx, ProbeLimitsTable.loadOrThrow())(implicitly)
      val result = Await.result(estimate, 1.minute).probability
      math.abs(result - 2.0 / 3.0) should beLessThan(1e-4)
    }
    "find an asterism of size 1" in {
      val ra = Angle.fromHMS(0, 0, 9.392).getOrElse(Angle.zero)
      val dec = Angle.zero - Angle.fromDMS(0, 0, 7.76).getOrElse(Angle.zero)
      val target = new SPTarget(ra.toDegrees, dec.toDegrees)
      val env = TargetEnvironment.create(target)
      val inst = new Iris <| {_.setPosAngle(90.0)} <| {_.setIssPort(IssPort.UP_LOOKING)} <| {_.setFilter(Iris.Filter.H)}
      val conditions = new SPSiteQuality.Conditions(CloudCover.PERCENT_50, ImageQuality.PERCENT_85, SkyBackground.ANY, WaterVapor.ANY)
      val ctx = ObsContext.create(env, inst, new JSome(Site.GS), conditions, null, new Nfiraos, JNone.instance())
      val tipTiltMode = NfiraosTipTiltMode.nfiraos

      val posAngles = Set(ctx.getPositionAngle, Angle.zero, Angle.fromDegrees(90), Angle.fromDegrees(180), Angle.fromDegrees(270))

      val nfiraosStrategy = TestNfiraosStrategy("/nfiraos_rel2941.xml")
      val selection = Await.result(nfiraosStrategy.select(ctx, ProbeLimitsTable.loadOrThrow())(implicitly), 5.minutes)
      selection.map(_.posAngle) should beSome(Angle.fromDegrees(90))
      val assignments = ~selection.map(_.assignments)
      assignments should be size 1

      assignments.exists(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs3) should beTrue
      val oiwfs3 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs3).map(_.guideStar)

      // Check coordinates
      oiwfs3.map(_.name) should beSome("450-000011")
      val oiwfs3x = Coordinates(
        RightAscension.fromAngle(Angle.fromHMS(0, 0, 11.370).getOrElse(Angle.zero)),
        Declination.fromAngle(Angle.zero - Angle.fromDMS(0, 0, 24.18).getOrElse(Angle.zero)).getOrElse(Declination.zero)
      )
      oiwfs3.map(_.coordinates ~= oiwfs3x) should beSome(true)

      // Analyze as a whole
      val newCtx = selection.map(_.applyTo(ctx)).getOrElse(ctx)
      val analysis = nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow())
      analysis.collect {
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs3, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs3 => NfiraosOiwfs.Wfs.oiwfs3
      } should beEqualTo(List(NfiraosOiwfs.Wfs.oiwfs3))

      // Analyze per probe
      oiwfs3.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs3, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs3, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)

      // Test estimate: 1-star asterism grants 1/3 chance of success.
      val estimate = nfiraosStrategy.estimate(ctx, ProbeLimitsTable.loadOrThrow())(implicitly)
      val result = Await.result(estimate, 1.minute).probability
      math.abs(result - 1.0 / 3.0) should beLessThan(1e-4)
    }
    "find an asterism of size 3" in {
      val ra = Angle.fromHMS(0, 0, 7.808).getOrElse(Angle.zero)
      val dec = Angle.zero - Angle.fromDMS(0, 3, 16.13).getOrElse(Angle.zero)
      val target = new SPTarget(ra.toDegrees, dec.toDegrees)
      val env = TargetEnvironment.create(target)
      val inst = new Iris <| {_.setPosAngle(90.0)} <| {_.setIssPort(IssPort.UP_LOOKING)}
      val ctx = ObsContext.create(env, inst, new JSome(Site.GS), SPSiteQuality.Conditions.BEST, null, new Nfiraos, JNone.instance())
      val tipTiltMode = NfiraosTipTiltMode.nfiraos

      val posAngles = Set(ctx.getPositionAngle, Angle.zero, Angle.fromDegrees(90), Angle.fromDegrees(180), Angle.fromDegrees(270))

      val nfiraosStrategy = TestNfiraosStrategy("/nfiraos_rel2941_2.xml")
      val selection = Await.result(nfiraosStrategy.select(ctx, ProbeLimitsTable.loadOrThrow())(implicitly), 5.minutes)
      selection.map(_.posAngle) should beSome(Angle.fromDegrees(90))
      val assignments = ~selection.map(_.assignments)
      assignments should be size 3

      assignments.exists(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs1) should beTrue
      val oiwfs1 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs1).map(_.guideStar)
      assignments.exists(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs2) should beTrue
      val oiwfs2 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs2).map(_.guideStar)
      assignments.exists(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs3) should beTrue
      val oiwfs3 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs3).map(_.guideStar)

      // Check coordinates
      oiwfs1.map(_.name) should beSome("450-000005")
      val oiwfs1x = Coordinates(
        RightAscension.fromAngle(Angle.fromHMS(0, 0, 6.159).getOrElse(Angle.zero)),
        Declination.fromAngle(Angle.zero - Angle.fromDMS(0, 2, 47.04).getOrElse(Angle.zero)).getOrElse(Declination.zero)
      )
      oiwfs1.map(_.coordinates ~= oiwfs1x) should beSome(true)

      oiwfs2.map(_.name) should beSome("450-000010")
      val oiwfs2x = Coordinates(
        RightAscension.fromAngle(Angle.fromHMS(0, 0, 9.519).getOrElse(Angle.zero)),
        Declination.fromAngle(Angle.zero - Angle.fromDMS(0, 3, 52.62).getOrElse(Angle.zero)).getOrElse(Declination.zero)
      )
      oiwfs2.map(_.coordinates ~= oiwfs2x) should beSome(true)

      oiwfs3.map(_.name) should beSome("450-000009")
      val oiwfs3x = Coordinates(
        RightAscension.fromAngle(Angle.fromHMS(0, 0, 8.983).getOrElse(Angle.zero)),
        Declination.fromAngle(Angle.zero - Angle.fromDMS(0, 3, 53.32).getOrElse(Angle.zero)).getOrElse(Declination.zero)
      )
      oiwfs3.map(_.coordinates ~= oiwfs3x) should beSome(true)

      // Analyze as a whole
      val newCtx = selection.map(_.applyTo(ctx)).getOrElse(ctx)
      val analysis = nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow())
      analysis.collect {
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs1, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs1 => NfiraosOiwfs.Wfs.oiwfs1
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs2, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs2 => NfiraosOiwfs.Wfs.oiwfs2
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs3, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs3 => NfiraosOiwfs.Wfs.oiwfs3
      } should beEqualTo(List(NfiraosOiwfs.Wfs.oiwfs1, NfiraosOiwfs.Wfs.oiwfs2, NfiraosOiwfs.Wfs.oiwfs3))

      // Analyze per probe
      oiwfs1.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs1, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs1, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)
      oiwfs2.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs2, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs2, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)
      oiwfs3.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs3, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs3, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)

      // Test estimate: 3-star asterism grants guaranteed success.
      val estimate = nfiraosStrategy.estimate(ctx, ProbeLimitsTable.loadOrThrow())(implicitly)
      Await.result(estimate, 1.minute) should beEqualTo(Estimate.GuaranteedSuccess)
    }
    "support search/select and analyze on SN-1987A" in {
      val ra = Angle.fromHMS(5, 35, 28.020).getOrElse(Angle.zero)
      val dec = Angle.zero - Angle.fromDMS(69, 16, 11.07).getOrElse(Angle.zero)
      val target = new SPTarget(ra.toDegrees, dec.toDegrees)
      val env = TargetEnvironment.create(target)
      val inst = new Iris <| {_.setPosAngle(0.0)} <| {_.setIssPort(IssPort.UP_LOOKING)}
      val conditions = SPSiteQuality.Conditions.NOMINAL.sb(SPSiteQuality.SkyBackground.ANY)
      val ctx = ObsContext.create(env, inst, new JSome(Site.GS), conditions, null, new Nfiraos, JNone.instance())
      val tipTiltMode = NfiraosTipTiltMode.nfiraos

      val posAngles = Set(ctx.getPositionAngle, Angle.zero, Angle.fromDegrees(90), Angle.fromDegrees(180), Angle.fromDegrees(270))

      testSearchOnStandardConditions("/nfiraos_sn1987A.xml", ctx, tipTiltMode, posAngles, 9, 9)

      val nfiraosStrategy = TestNfiraosStrategy("/nfiraos_sn1987A.xml")
      val selection = Await.result(nfiraosStrategy.select(ctx, ProbeLimitsTable.loadOrThrow())(implicitly), 5.minutes)
      selection.map(_.posAngle) should beSome(Angle.zero)
      val assignments = ~selection.map(_.assignments)
      assignments should be size 3

      assignments.exists(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs1) should beTrue
      val oiwfs1 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs1).map(_.guideStar)
      assignments.exists(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs2) should beTrue
      val oiwfs2 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs2).map(_.guideStar)
      assignments.exists(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs3) should beTrue
      val oiwfs3 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs3).map(_.guideStar)

      // Check coordinates
      oiwfs1.map(_.name) should beSome("104-014597")
      oiwfs2.map(_.name) should beSome("104-014608")
      oiwfs3.map(_.name) should beSome("104-014547")

      val oiwfs1x = Coordinates(RightAscension.fromAngle(Angle.fromHMS(5, 35, 32.630).getOrElse(Angle.zero)), Declination.fromAngle(Angle.zero - Angle.fromDMS(69, 15, 48.64).getOrElse(Angle.zero)).getOrElse(Declination.zero))
      oiwfs1.map(_.coordinates ~= oiwfs1x) should beSome(true)
      val oiwfs2x = Coordinates(RightAscension.fromAngle(Angle.fromHMS(5, 35, 36.409).getOrElse(Angle.zero)), Declination.fromAngle(Angle.zero - Angle.fromDMS(69, 16, 24.17).getOrElse(Angle.zero)).getOrElse(Declination.zero))
      oiwfs2.map(_.coordinates ~= oiwfs2x) should beSome(true)
      val oiwfs3x = Coordinates(RightAscension.fromAngle(Angle.fromHMS(5, 35, 18.423).getOrElse(Angle.zero)), Declination.fromAngle(Angle.zero - Angle.fromDMS(69, 16, 30.67).getOrElse(Angle.zero)).getOrElse(Declination.zero))
      oiwfs3.map(_.coordinates ~= oiwfs3x) should beSome(true)

      // Check magnitudes are sorted correctly
      val mag1 = oiwfs1.flatMap(RBandsList.extract).map(_.value)
      val mag2 = oiwfs2.flatMap(RBandsList.extract).map(_.value)
      val mag3 = oiwfs3.flatMap(RBandsList.extract).map(_.value)
      (mag3 < mag1 && mag2 < mag1) should beTrue

      // Analyze as a whole
      val newCtx = selection.map(_.applyTo(ctx)).getOrElse(ctx)
      val analysis = nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow())
      analysis.collect {
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs1, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs1 => NfiraosOiwfs.Wfs.oiwfs1
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs2, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs2 => NfiraosOiwfs.Wfs.oiwfs2
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs3, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs3 => NfiraosOiwfs.Wfs.oiwfs3
      } should beEqualTo(List(NfiraosOiwfs.Wfs.oiwfs1, NfiraosOiwfs.Wfs.oiwfs2, NfiraosOiwfs.Wfs.oiwfs3))

      // Analyze per probe
      oiwfs1.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs1, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs1, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)
      oiwfs2.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs2, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs2, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)
      oiwfs3.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs3, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs3, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)

      // Test estimate
      val estimate = nfiraosStrategy.estimate(ctx, ProbeLimitsTable.loadOrThrow())(implicitly)
      Await.result(estimate, 1.minute) should beEqualTo(Estimate.GuaranteedSuccess)
    }
    "support search/select and analyze on SN-1987A part 2" in {
      val ra = Angle.fromHMS(5, 35, 28.020).getOrElse(Angle.zero)
      val dec = Angle.zero - Angle.fromDMS(69, 16, 11.07).getOrElse(Angle.zero)
      val target = new SPTarget(ra.toDegrees, dec.toDegrees)
      val env = TargetEnvironment.create(target)
      val inst = new Iris <| {_.setPosAngle(0.0)} <| {_.setIssPort(IssPort.UP_LOOKING)}
      val conditions = SPSiteQuality.Conditions.BEST.cc(SPSiteQuality.CloudCover.PERCENT_50)
      val ctx = ObsContext.create(env, inst, new JSome(Site.GS), conditions, null, new Nfiraos, JNone.instance())
      val tipTiltMode = NfiraosTipTiltMode.nfiraos

      val posAngles = Set(ctx.getPositionAngle, Angle.zero, Angle.fromDegrees(90), Angle.fromDegrees(180), Angle.fromDegrees(270))

      testSearchOnBestConditions("/nfiraos_sn1987A.xml", ctx, tipTiltMode, posAngles, 12, 9)

      val nfiraosStrategy = TestNfiraosStrategy("/nfiraos_sn1987A.xml")
      val selection = Await.result(nfiraosStrategy.select(ctx, ProbeLimitsTable.loadOrThrow())(implicitly), 5.minutes)
      selection.map(_.posAngle) should beSome(Angle.zero)
      val assignments = ~selection.map(_.assignments)
      assignments should be size 3

      val oiwfs1 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs1).map(_.guideStar)
      val oiwfs2 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs2).map(_.guideStar)
      val oiwfs3 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs3).map(_.guideStar)
      oiwfs1.map(_.name) should beSome("104-014597")
      oiwfs2.map(_.name) should beSome("104-014608")
      oiwfs3.map(_.name) should beSome("104-014547")

      val oiwfs1x = Coordinates(RightAscension.fromAngle(Angle.fromHMS(5, 35, 32.630).getOrElse(Angle.zero)), Declination.fromAngle(Angle.zero - Angle.fromDMS(69, 15, 48.64).getOrElse(Angle.zero)).getOrElse(Declination.zero))
      oiwfs1.map(_.coordinates ~= oiwfs1x) should beSome(true)
      val oiwfs2x = Coordinates(RightAscension.fromAngle(Angle.fromHMS(5, 35, 36.409).getOrElse(Angle.zero)), Declination.fromAngle(Angle.zero - Angle.fromDMS(69, 16, 24.17).getOrElse(Angle.zero)).getOrElse(Declination.zero))
      oiwfs2.map(_.coordinates ~= oiwfs2x) should beSome(true)
      val oiwfs3x = Coordinates(RightAscension.fromAngle(Angle.fromHMS(5, 35, 18.423).getOrElse(Angle.zero)), Declination.fromAngle(Angle.zero - Angle.fromDMS(69, 16, 30.67).getOrElse(Angle.zero)).getOrElse(Declination.zero))
      oiwfs3.map(_.coordinates ~= oiwfs3x) should beSome(true)

      val newCtx = selection.map(_.applyTo(ctx)).getOrElse(ctx)
      // Analyze as a whole
      val analysis = nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow())
      analysis.collect {
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs1, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs1 => NfiraosOiwfs.Wfs.oiwfs1
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs2, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs2 => NfiraosOiwfs.Wfs.oiwfs2
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs3, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs3 => NfiraosOiwfs.Wfs.oiwfs3
      } should beEqualTo(List(NfiraosOiwfs.Wfs.oiwfs1, NfiraosOiwfs.Wfs.oiwfs2, NfiraosOiwfs.Wfs.oiwfs3))
      // Analyze per probe
      oiwfs1.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs1, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs1, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)
      oiwfs2.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs2, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs2, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)
      oiwfs3.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs3, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs3, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)

      // Test estimate
      val estimate = nfiraosStrategy.estimate(ctx, ProbeLimitsTable.loadOrThrow())(implicitly)
      Await.result(estimate, 1.minute) should beEqualTo(Estimate.GuaranteedSuccess)
    }
    "support search/select and analyze on TYC 8345-1155-1" in {
      val ra = Angle.fromHMS(17, 25, 27.529).getOrElse(Angle.zero)
      val dec = Angle.zero - Angle.fromDMS(48, 27, 24.02).getOrElse(Angle.zero)
      val target = new SPTarget(ra.toDegrees, dec.toDegrees)
      val env = TargetEnvironment.create(target)
      val inst = new Iris <| {_.setPosAngle(0.0)} <| {_.setIssPort(IssPort.UP_LOOKING)}
      val conditions = SPSiteQuality.Conditions.NOMINAL.sb(SPSiteQuality.SkyBackground.ANY).wv(SPSiteQuality.WaterVapor.ANY)
      val ctx = ObsContext.create(env, inst, new JSome(Site.GS), conditions, null, new Nfiraos, JNone.instance())
      val tipTiltMode = NfiraosTipTiltMode.nfiraos

      val posAngles = Set(ctx.getPositionAngle, Angle.zero, Angle.fromDegrees(90), Angle.fromDegrees(180), Angle.fromDegrees(270))

      testSearchOnStandardConditions("/nfiraos_TYC_8345_1155_1.xml", ctx, tipTiltMode, posAngles, 25, 28)

      val nfiraosStrategy = TestNfiraosStrategy("/nfiraos_TYC_8345_1155_1.xml")
      val selection = Await.result(nfiraosStrategy.select(ctx, ProbeLimitsTable.loadOrThrow())(implicitly), 5.minutes)
      selection.map(_.posAngle) should beSome(Angle.zero)
      val assignments = ~selection.map(_.assignments)
      assignments should be size 3

      assignments.exists(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs1) should beTrue
      val oiwfs1 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs1).map(_.guideStar)
      assignments.exists(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs2) should beTrue
      val oiwfs2 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs2).map(_.guideStar)
      assignments.exists(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs3) should beTrue
      val oiwfs3 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs3).map(_.guideStar)
      oiwfs1.map(_.name) should beSome("208-152095")
      oiwfs2.map(_.name) should beSome("208-152215")
      oiwfs3.map(_.name) should beSome("208-152039")

      val oiwfs1x = Coordinates(RightAscension.fromAngle(Angle.fromHMS(17, 25, 27.151).getOrElse(Angle.zero)), Declination.fromAngle(Angle.zero - Angle.fromDMS(48, 28, 07.67).getOrElse(Angle.zero)).getOrElse(Declination.zero))
      oiwfs1.map(_.coordinates ~= oiwfs1x) should beSome(true)
      val oiwfs2x = Coordinates(RightAscension.fromAngle(Angle.fromHMS(17, 25, 32.541).getOrElse(Angle.zero)), Declination.fromAngle(Angle.zero - Angle.fromDMS(48, 27, 30.06).getOrElse(Angle.zero)).getOrElse(Declination.zero))
      oiwfs2.map(_.coordinates ~= oiwfs2x) should beSome(true)
      val oiwfs3x = Coordinates(RightAscension.fromAngle(Angle.fromHMS(17, 25, 24.719).getOrElse(Angle.zero)), Declination.fromAngle(Angle.zero - Angle.fromDMS(48, 26, 58.00).getOrElse(Angle.zero)).getOrElse(Declination.zero))
      oiwfs3.map(_.coordinates ~= oiwfs3x) should beSome(true)

      // Check magnitudes are sorted correctly
      val mag1 = oiwfs1.flatMap(RBandsList.extract).map(_.value)
      val mag2 = oiwfs2.flatMap(RBandsList.extract).map(_.value)
      val mag3 = oiwfs3.flatMap(RBandsList.extract).map(_.value)
      (mag3 < mag1 && mag2 < mag1) should beTrue

      // Analyze as a whole
      val newCtx = selection.map(_.applyTo(ctx)).getOrElse(ctx)
      val analysis = nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow())
      analysis.collect {
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs1, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs1 => NfiraosOiwfs.Wfs.oiwfs1
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs2, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs2 => NfiraosOiwfs.Wfs.oiwfs2
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs3, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs3 => NfiraosOiwfs.Wfs.oiwfs3
      } should beEqualTo(List(NfiraosOiwfs.Wfs.oiwfs1, NfiraosOiwfs.Wfs.oiwfs2, NfiraosOiwfs.Wfs.oiwfs3))
      // Analyze per probe
      oiwfs1.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs1, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs1, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)
      oiwfs2.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs2, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs2, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)
      oiwfs3.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs3, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs3, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)

      // Test estimate
      val estimate = nfiraosStrategy.estimate(ctx, ProbeLimitsTable.loadOrThrow())(implicitly)
      Await.result(estimate, 1.minute) should beEqualTo(Estimate.GuaranteedSuccess)
    }
    "support search/select and analyze on M6" in {
      val ra = Angle.fromHMS(17, 40, 20.0).getOrElse(Angle.zero)
      val dec = Angle.zero - Angle.fromDMS(32, 15, 12.0).getOrElse(Angle.zero)
      val target = new SPTarget(ra.toDegrees, dec.toDegrees)
      val env = TargetEnvironment.create(target)
      val inst = new Iris <| {_.setPosAngle(0.0)} <| {_.setIssPort(IssPort.UP_LOOKING)}
      val conditions = SPSiteQuality.Conditions.NOMINAL.sb(SPSiteQuality.SkyBackground.ANY).wv(SPSiteQuality.WaterVapor.ANY)
      val ctx = ObsContext.create(env, inst, new JSome(Site.GS), conditions, null, new Nfiraos, JNone.instance())
      val tipTiltMode = NfiraosTipTiltMode.nfiraos

      val posAngles = Set(ctx.getPositionAngle, Angle.zero, Angle.fromDegrees(90), Angle.fromDegrees(180), Angle.fromDegrees(270))

      testSearchOnStandardConditions("/nfiraos_m6.xml", ctx, tipTiltMode, posAngles, 5, 7)

      val nfiraosStrategy = TestNfiraosStrategy("/nfiraos_m6.xml")
      val selection = Await.result(nfiraosStrategy.select(ctx, ProbeLimitsTable.loadOrThrow())(implicitly), 5.minutes)
      selection.map(_.posAngle) should beSome(Angle.fromDegrees(90))
      val assignments = ~selection.map(_.assignments)
      assignments should be size 3

      assignments.exists(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs1) should beTrue
      val oiwfs1 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs1).map(_.guideStar)
      assignments.exists(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs2) should beTrue
      val oiwfs2 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs2).map(_.guideStar)
      assignments.exists(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs3) should beTrue
      val oiwfs3 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs3).map(_.guideStar)
      oiwfs1.map(_.name) should beSome("289-128909")
      oiwfs2.map(_.name) should beSome("289-128878")
      oiwfs3.map(_.name) should beSome("289-128908")

      val oiwfs1x = Coordinates(RightAscension.fromAngle(Angle.fromHMS(17, 40, 21.743).getOrElse(Angle.zero)), Declination.fromAngle(Angle.zero - Angle.fromDMS(32, 14, 54.04).getOrElse(Angle.zero)).getOrElse(Declination.zero))
      oiwfs1.map(_.coordinates ~= oiwfs1x) should beSome(true)
      val oiwfs2x = Coordinates(RightAscension.fromAngle(Angle.fromHMS(17, 40, 16.855).getOrElse(Angle.zero)), Declination.fromAngle(Angle.zero - Angle.fromDMS(32, 15, 55.83).getOrElse(Angle.zero)).getOrElse(Declination.zero))
      oiwfs2.map(_.coordinates ~= oiwfs2x) should beSome(true)
      val oiwfs3x = Coordinates(RightAscension.fromAngle(Angle.fromHMS(17, 40, 21.594).getOrElse(Angle.zero)), Declination.fromAngle(Angle.zero - Angle.fromDMS(32, 15, 50.38).getOrElse(Angle.zero)).getOrElse(Declination.zero))
      oiwfs3.map(_.coordinates ~= oiwfs3x) should beSome(true)

      // Check magnitudes are sorted correctly
      val mag1 = oiwfs1.flatMap(RBandsList.extract).map(_.value)
      val mag2 = oiwfs2.flatMap(RBandsList.extract).map(_.value)
      val mag3 = oiwfs3.flatMap(RBandsList.extract).map(_.value)
      (mag3 < mag1 && mag2 < mag1) should beTrue

      // Analyze as a whole
      val newCtx = selection.map(_.applyTo(ctx)).getOrElse(ctx)
      val analysis = nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow())
      analysis.collect {
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs1, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs1 => NfiraosOiwfs.Wfs.oiwfs1
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs2, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs2 => NfiraosOiwfs.Wfs.oiwfs2
        case AgsAnalysis.NotReachable(NfiraosOiwfs.Wfs.oiwfs3, st) if st.some == oiwfs3                                                 => NfiraosOiwfs.Wfs.oiwfs3
      } should beEqualTo(List(NfiraosOiwfs.Wfs.oiwfs1, NfiraosOiwfs.Wfs.oiwfs2, NfiraosOiwfs.Wfs.oiwfs3))

      // Analyze per probe
      oiwfs1.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs1, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs1, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)
      oiwfs2.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs2, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs2, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)
      oiwfs3.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs3, s).contains(AgsAnalysis.NotReachable(NfiraosOiwfs.Wfs.oiwfs3, s))
      } should beSome(true)

      // Test estimate
      val estimate = nfiraosStrategy.estimate(ctx, ProbeLimitsTable.loadOrThrow())(implicitly)
      Await.result(estimate, 1.minute) should beEqualTo(Estimate.GuaranteedSuccess)
    }
    "support search/select and analyze on BPM 37093" in {
      val ra = Angle.fromHMS(12, 38, 49.820).getOrElse(Angle.zero)
      val dec = Angle.zero - Angle.fromDMS(49, 48, 0.20).getOrElse(Angle.zero)
      val target = new SPTarget(ra.toDegrees, dec.toDegrees)
      val env = TargetEnvironment.create(target)
      val inst = new Iris <| {_.setPosAngle(0.0)} <| {_.setIssPort(IssPort.UP_LOOKING)}
      val conditions = SPSiteQuality.Conditions.NOMINAL.sb(SPSiteQuality.SkyBackground.ANY)
      val ctx = ObsContext.create(env, inst, new JSome(Site.GS), conditions, null, new Nfiraos, JNone.instance())
      val tipTiltMode = NfiraosTipTiltMode.nfiraos

      val posAngles = Set(ctx.getPositionAngle, Angle.zero, Angle.fromDegrees(90), Angle.fromDegrees(180), Angle.fromDegrees(270))

      testSearchOnStandardConditions("/nfiraos_bpm_37093.xml", ctx, tipTiltMode, posAngles, 4, 5)

      val nfiraosStrategy = TestNfiraosStrategy("/nfiraos_bpm_37093.xml")
      val selection = Await.result(nfiraosStrategy.select(ctx, ProbeLimitsTable.loadOrThrow())(implicitly), 5.minutes)
      selection.map(_.posAngle) should beSome(Angle.zero)
      val assignments = ~selection.map(_.assignments)
      assignments should be size 3

      val oiwfs1 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs1).map(_.guideStar)
      val oiwfs2 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs2).map(_.guideStar)
      val oiwfs3 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs3).map(_.guideStar)
      oiwfs1.map(_.name) should beSome("202-067216")
      oiwfs2.map(_.name) should beSome("202-067200")
      oiwfs3.map(_.name) should beSome("201-071218")

      val oiwfs1x = Coordinates(RightAscension.fromAngle(Angle.fromHMS(12, 38, 50.130).getOrElse(Angle.zero)), Declination.fromAngle(Angle.zero - Angle.fromDMS(49, 47, 38.07).getOrElse(Angle.zero)).getOrElse(Declination.zero))
      oiwfs1.map(_.coordinates ~= oiwfs1x) should beSome(true)
      val oiwfs2x = Coordinates(RightAscension.fromAngle(Angle.fromHMS(12, 38, 44.500).getOrElse(Angle.zero)), Declination.fromAngle(Angle.zero - Angle.fromDMS(49, 47, 58.38).getOrElse(Angle.zero)).getOrElse(Declination.zero))
      oiwfs2.map(_.coordinates ~= oiwfs2x) should beSome(true)
      val oiwfs3x = Coordinates(RightAscension.fromAngle(Angle.fromHMS(12, 38, 50.005).getOrElse(Angle.zero)), Declination.fromAngle(Angle.zero - Angle.fromDMS(49, 48, 00.89).getOrElse(Angle.zero)).getOrElse(Declination.zero))
      oiwfs3.map(_.coordinates ~= oiwfs3x) should beSome(true)

      val newCtx = selection.map(_.applyTo(ctx)).getOrElse(ctx)
      // Analyze all the probes at once
      nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow()).collect {
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs1, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs1 => NfiraosOiwfs.Wfs.oiwfs1
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs2, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs2 => NfiraosOiwfs.Wfs.oiwfs2
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs3, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs3 => NfiraosOiwfs.Wfs.oiwfs3
      } should beEqualTo(List(NfiraosOiwfs.Wfs.oiwfs1, NfiraosOiwfs.Wfs.oiwfs2, NfiraosOiwfs.Wfs.oiwfs3))
      // Analyze per probe
      oiwfs1.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs1, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs1, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)
      oiwfs2.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs2, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs2, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)
      oiwfs3.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs3, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs3, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)

      // Test estimate
      val estimate = nfiraosStrategy.estimate(ctx, ProbeLimitsTable.loadOrThrow())(implicitly)
      Await.result(estimate, 1.minute) should beEqualTo(Estimate.GuaranteedSuccess)
    }
    "support search/select and analyze on BPM 37093 part 2" in {
      val ra = Angle.fromHMS(12, 38, 49.820).getOrElse(Angle.zero)
      val dec = Angle.zero - Angle.fromDMS(49, 48, 0.20).getOrElse(Angle.zero)
      val target = new SPTarget(ra.toDegrees, dec.toDegrees)
      val env = TargetEnvironment.create(target)
      val inst = new Iris <| {_.setPosAngle(0.0)} <| {_.setIssPort(IssPort.UP_LOOKING)}
      val conditions = SPSiteQuality.Conditions.BEST.cc(SPSiteQuality.CloudCover.PERCENT_50)
      val ctx = ObsContext.create(env, inst, new JSome(Site.GS), conditions, null, new Nfiraos, JNone.instance())
      val tipTiltMode = NfiraosTipTiltMode.nfiraos

      val posAngles = Set(ctx.getPositionAngle, Angle.zero, Angle.fromDegrees(90), Angle.fromDegrees(180), Angle.fromDegrees(270))

      testSearchOnBestConditions("/nfiraos_bpm_37093.xml", ctx, tipTiltMode, posAngles, 5, 5)

      val nfiraosStrategy = TestNfiraosStrategy("/nfiraos_bpm_37093.xml")
      val selection = Await.result(nfiraosStrategy.select(ctx, ProbeLimitsTable.loadOrThrow())(implicitly), 5.minutes)
      selection.map(_.posAngle) should beSome(Angle.zero)
      val assignments = ~selection.map(_.assignments)
      assignments should be size 3

      val oiwfs1 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs1).map(_.guideStar)
      val oiwfs2 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs2).map(_.guideStar)
      val oiwfs3 = assignments.find(_.guideProbe == NfiraosOiwfs.Wfs.oiwfs3).map(_.guideStar)
      oiwfs1.map(_.name) should beSome("202-067216")
      oiwfs2.map(_.name) should beSome("202-067200")
      oiwfs3.map(_.name) should beSome("201-071218")

      val oiwfs1x = Coordinates(RightAscension.fromAngle(Angle.fromHMS(12, 38, 50.130).getOrElse(Angle.zero)), Declination.fromAngle(Angle.zero - Angle.fromDMS(49, 47, 38.07).getOrElse(Angle.zero)).getOrElse(Declination.zero))
      oiwfs1.map(_.coordinates ~= oiwfs1x) should beSome(true)
      val oiwfs2x = Coordinates(RightAscension.fromAngle(Angle.fromHMS(12, 38, 44.500).getOrElse(Angle.zero)), Declination.fromAngle(Angle.zero - Angle.fromDMS(49, 47, 58.38).getOrElse(Angle.zero)).getOrElse(Declination.zero))
      oiwfs2.map(_.coordinates ~= oiwfs2x) should beSome(true)
      val oiwfs3x = Coordinates(RightAscension.fromAngle(Angle.fromHMS(12, 38, 50.005).getOrElse(Angle.zero)), Declination.fromAngle(Angle.zero - Angle.fromDMS(49, 48, 00.89).getOrElse(Angle.zero)).getOrElse(Declination.zero))
      oiwfs3.map(_.coordinates ~= oiwfs3x) should beSome(true)

      val newCtx = selection.map(_.applyTo(ctx)).getOrElse(ctx)
      // Analyze all the probes at once
      nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow()).collect {
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs1, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs1 => NfiraosOiwfs.Wfs.oiwfs1
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs2, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs2 => NfiraosOiwfs.Wfs.oiwfs2
        case AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs3, st, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq) if st.some == oiwfs3 => NfiraosOiwfs.Wfs.oiwfs3
      } should beEqualTo(List(NfiraosOiwfs.Wfs.oiwfs1, NfiraosOiwfs.Wfs.oiwfs2, NfiraosOiwfs.Wfs.oiwfs3))
      // Analyze per probe
      oiwfs1.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs1, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs1, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)
      oiwfs2.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs2, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs2, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)
      oiwfs3.map { s =>
        nfiraosStrategy.analyze(newCtx, ProbeLimitsTable.loadOrThrow(), NfiraosOiwfs.Wfs.oiwfs3, s).contains(AgsAnalysis.Usable(NfiraosOiwfs.Wfs.oiwfs3, s, GuideSpeed.FAST, AgsGuideQuality.DeliversRequestedIq))
      } should beSome(true)

      // Test estimate
      val estimate = nfiraosStrategy.estimate(ctx, ProbeLimitsTable.loadOrThrow())(implicitly)
      Await.result(estimate, 1.minute) should beEqualTo(Estimate.GuaranteedSuccess)
    }
  }

  def testSearchOnStandardConditions(file: String, ctx: ObsContext, tipTiltMode: NfiraosTipTiltMode, posAngles: Set[Angle], expectedTipTiltResultsCount: Int, expectedFlexureResultsCount: Int): Unit = {
    val results = Await.result(TestNfiraosStrategy(file).search(tipTiltMode, ctx, posAngles, scala.None)(implicitly), 1.minute)
    results should be size 2

    results.head.criterion.key should beEqualTo(NfiraosCatalogSearchKey(NfiraosGuideStarType.tiptilt, Wfs.Group.instance))
    results.head.criterion should beEqualTo(NfiraosCatalogSearchCriterion(NfiraosCatalogSearchKey(NfiraosGuideStarType.tiptilt, Wfs.Group.instance), CatalogSearchCriterion("Nfiraos Wave Front Sensor tiptilt", RadiusConstraint.between(Angle.zero, Angle.fromDegrees(0.01666666666665151)), MagnitudeConstraints(RBandsList, FaintnessConstraint(15.8), scala.Option(SaturationConstraint(8.3))), scala.Option(Offset(0.0014984027777700248.degrees[OffsetP], 0.0014984027777700248.degrees[OffsetQ])), scala.Some(Angle.zero))))
    results(1).criterion.key should beEqualTo(NfiraosCatalogSearchKey(NfiraosGuideStarType.flexure, IrisOdgw.Group.instance))
    results(1).criterion should beEqualTo(NfiraosCatalogSearchCriterion(NfiraosCatalogSearchKey(NfiraosGuideStarType.flexure, IrisOdgw.Group.instance), CatalogSearchCriterion("On-detector Guide Window flexure", RadiusConstraint.between(Angle.zero, Angle.fromDegrees(0.01666666666665151)), MagnitudeConstraints(SingleBand(MagnitudeBand.H), FaintnessConstraint(17.0), scala.Option(SaturationConstraint(8.0))), scala.Option(Offset(0.0014984027777700248.degrees[OffsetP], 0.0014984027777700248.degrees[OffsetQ])), scala.Some(Angle.zero))))
    results.head.results should be size expectedTipTiltResultsCount
    results(1).results should be size expectedFlexureResultsCount
  }

  def testSearchOnNominal(file: String, ctx: ObsContext, tipTiltMode: NfiraosTipTiltMode, posAngles: Set[Angle], expectedTipTiltResultsCount: Int, expectedFlexureResultsCount: Int): Unit = {
    val results = Await.result(TestNfiraosStrategy(file).search(tipTiltMode, ctx, posAngles, scala.None)(implicitly), 1.minute)
    results should be size 2

    results.head.criterion.key should beEqualTo(NfiraosCatalogSearchKey(NfiraosGuideStarType.tiptilt, Wfs.Group.instance))
    results.head.criterion should beEqualTo(NfiraosCatalogSearchCriterion(NfiraosCatalogSearchKey(NfiraosGuideStarType.tiptilt, Wfs.Group.instance), CatalogSearchCriterion("Nfiraos Wave Front Sensor tiptilt", RadiusConstraint.between(Angle.zero, Angle.fromDegrees(0.01666666666665151)), MagnitudeConstraints(RBandsList, FaintnessConstraint(16.3), scala.Option(SaturationConstraint(8.8))), scala.Option(Offset(0.0014984027777700248.degrees[OffsetP], 0.0014984027777700248.degrees[OffsetQ])), scala.Some(Angle.zero))))
    results(1).criterion.key should beEqualTo(NfiraosCatalogSearchKey(NfiraosGuideStarType.flexure, IrisOdgw.Group.instance))
    results(1).criterion should beEqualTo(NfiraosCatalogSearchCriterion(NfiraosCatalogSearchKey(NfiraosGuideStarType.flexure, IrisOdgw.Group.instance), CatalogSearchCriterion("On-detector Guide Window flexure", RadiusConstraint.between(Angle.zero, Angle.fromDegrees(0.01666666666665151)), MagnitudeConstraints(SingleBand(MagnitudeBand.H), FaintnessConstraint(17.0), scala.Option(SaturationConstraint(8.0))), scala.Option(Offset(0.0014984027777700248.degrees[OffsetP], 0.0014984027777700248.degrees[OffsetQ])), scala.Some(Angle.zero))))
    results.head.results should be size expectedTipTiltResultsCount
    results(1).results should be size expectedFlexureResultsCount
  }

  def testSearchOnBestConditions(file: String, ctx: ObsContext, tipTiltMode: NfiraosTipTiltMode, posAngles: Set[Angle], expectedTipTiltResultsCount: Int, expectedFlexureResultsCount: Int): Unit = {
    val results = Await.result(TestNfiraosStrategy(file).search(tipTiltMode, ctx, posAngles, scala.None)(implicitly), 1.minute)
    results should be size 2

    results.head.criterion.key should beEqualTo(NfiraosCatalogSearchKey(NfiraosGuideStarType.tiptilt, Wfs.Group.instance))
    results.head.criterion should beEqualTo(NfiraosCatalogSearchCriterion(NfiraosCatalogSearchKey(NfiraosGuideStarType.tiptilt, Wfs.Group.instance), CatalogSearchCriterion("Nfiraos Wave Front Sensor tiptilt", RadiusConstraint.between(Angle.zero, Angle.fromDegrees(0.01666666666665151)), MagnitudeConstraints(RBandsList, FaintnessConstraint(16.8), scala.Option(SaturationConstraint(9.3))), scala.Option(Offset(0.0014984027777700248.degrees[OffsetP], 0.0014984027777700248.degrees[OffsetQ])), scala.Some(Angle.zero))))
    results(1).criterion.key should beEqualTo(NfiraosCatalogSearchKey(NfiraosGuideStarType.flexure, IrisOdgw.Group.instance))
    results(1).criterion should beEqualTo(NfiraosCatalogSearchCriterion(NfiraosCatalogSearchKey(NfiraosGuideStarType.flexure, IrisOdgw.Group.instance), CatalogSearchCriterion("On-detector Guide Window flexure", RadiusConstraint.between(Angle.zero, Angle.fromDegrees(0.01666666666665151)), MagnitudeConstraints(SingleBand(MagnitudeBand.H), FaintnessConstraint(17.0), scala.Option(SaturationConstraint(8.0))), scala.Option(Offset(0.0014984027777700248.degrees[OffsetP], 0.0014984027777700248.degrees[OffsetQ])), scala.Some(Angle.zero))))
    results.head.results should be size expectedTipTiltResultsCount
    results(1).results should be size expectedFlexureResultsCount
  }
}