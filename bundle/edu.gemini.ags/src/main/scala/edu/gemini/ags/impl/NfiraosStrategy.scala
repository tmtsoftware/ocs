package edu.gemini.ags.impl

import edu.gemini.spModel.guide.OrderGuideGroup
import edu.gemini.ags.api.{AgsAnalysis, AgsMagnitude, AgsStrategy}
import edu.gemini.ags.api.AgsStrategy.{Assignment, Estimate, Selection}
import edu.gemini.ags.nfiraos._
import edu.gemini.catalog.api._
import edu.gemini.catalog.votable._
import edu.gemini.pot.sp.SPComponentType
import edu.gemini.pot.ModelConverters._
import edu.gemini.spModel.core.SiderealTarget
import edu.gemini.spModel.ags.AgsStrategyKey.NfiraosKey
import edu.gemini.spModel.gemini.flamingos2.{Flamingos2, Flamingos2OiwfsGuideProbe}
import edu.gemini.spModel.gemini.nfiraos.{NfiraosOiwfs, NfiraosInstrument}
import edu.gemini.spModel.gemini.iris.{Iris, IrisOdgw}
import edu.gemini.spModel.nfiraos.{NfiraosGuideProbeGroup, NfiraosTipTiltMode}
import edu.gemini.spModel.obs.context.ObsContext
import edu.gemini.spModel.rich.shared.immutable._

import scala.collection.JavaConverters._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import edu.gemini.ags.api.AgsMagnitude.{MagnitudeCalc, MagnitudeTable}
import edu.gemini.spModel.guide.{GuideProbe, GuideProbeGroup, ValidatableGuideProbe}
import edu.gemini.spModel.core._
import edu.gemini.spModel.telescope.PosAngleConstraint

import scalaz._
import Scalaz._

trait NfiraosStrategy extends AgsStrategy {
  // By default use the remote backend but it can be overriden in tests
  private [impl] def backend:VoTableBackend

  override def key = NfiraosKey

  // Since the constraints are run in parallel, we need a way to identify them after
  // they are done, so we create IDs for each. This is a pretty nasty way to do things, but
  // since we cannot predict in what order the results will return, we need to be able
  // to pick them out somehow.
  private val IrisOiwfsTipTiltId = 0
  private val OdgwFlexureId    = 1

  override def magnitudes(ctx: ObsContext, mt: MagnitudeTable): List[(GuideProbe, MagnitudeCalc)] = {
    val cans = NfiraosOiwfs.Wfs.values().map { oiwfs => mt(ctx, oiwfs).map(oiwfs -> _) }.toList.flatten
    val odgw = IrisOdgw.values().map { odgw => mt(ctx, odgw).map(odgw -> _) }.toList.flatten
    cans ++ odgw
  }

  override def analyze(ctx: ObsContext, mt: MagnitudeTable, guideProbe: ValidatableGuideProbe, guideStar: SiderealTarget): Option[AgsAnalysis] =
    AgsAnalysis.analysis(ctx, mt, guideProbe, guideStar)

  override def analyze(ctx: ObsContext, mt: MagnitudeTable): List[AgsAnalysis] = {
    import AgsAnalysis._

    def mapGroup(grp: GuideProbeGroup): List[AgsAnalysis] = {
      def hasGuideStarForProbe(a: AgsAnalysis): Boolean = a match {
        case NoGuideStarForProbe(_) => false
        case _                      => true
      }

      val probeAnalysis = grp.getMembers.asScala.toList.flatMap { p => analysis(ctx, mt, p) }
      probeAnalysis.filter(hasGuideStarForProbe) match {
        case Nil =>
          // Pick the first guide probe as representative, since we are called with either Nfiraos or IrisOdwg
          ~grp.getMembers.asScala.headOption.map {gp => List(NoGuideStarForGroup(grp))}
        case lst => lst
      }
    }

    mapGroup(NfiraosOiwfs.Wfs.Group.instance) // TODO: REL-2941 ++ mapGroup(IrisOdgw.Group.instance)
  }

  override def candidates(ctx: ObsContext, mt: MagnitudeTable)(ec: ExecutionContext): Future[List[(GuideProbe, List[SiderealTarget])]] = {

    // Extract something we can understand from the NfiraosCatalogSearchResults.
    def simplifiedResult(results: List[NfiraosCatalogSearchResults]): List[(GuideProbe, List[SiderealTarget])] =
      results.flatMap { result =>
        val so = result.results  // extract the sky objects from this thing
        // For each guide probe associated with these sky objects, add a tuple
        // (guide probe, sky object list) to the results
        result.criterion.key.group.getMembers.asScala.toList.map { guideProbe =>
          (guideProbe, so)
        }
      }

    // why do we need multiple position angles?  catalog results are given in
    // a ring (limited by radius limits) around a base position ... confusion
    val posAngles   = (ctx.getPositionAngle :: (0 until 360 by 90).map(Angle.fromDegrees(_)).toList).toSet
    search(NfiraosTipTiltMode.nfiraos, ctx, posAngles, None)(ec).map(simplifiedResult)
  }

  override def estimate(ctx: ObsContext, mt: MagnitudeTable)(ec: ExecutionContext): Future[Estimate] = {
    // Create a set of the angles to try.
    val anglesToTry = (0 until 360 by 90).map(Angle.fromDegrees(_)).toSet

    // Get the query results and convert them to Nfiraos-specific ones.
    val results = search(NfiraosTipTiltMode.nfiraos, ctx, anglesToTry, None)(ec)

    // Iterate over 90 degree position angles if no 3-star asterism is found at PA = 0.
    val nfiraosCatalogResults = results.map(result => NfiraosResultsAnalyzer.analyzeGoodEnough(ctx, anglesToTry, result, _.stars.size < 3))

    // We only want Nfiraos targets, so filter to those and then determine if the asterisms are big enough.
    nfiraosCatalogResults.map { ggsLst =>
      val largestAsterism = ggsLst.map(_.guideGroup.grp.toManualGroup.targetMap.keySet.intersection(NfiraosStrategy.irisOiwfsProbes).size).fold(0)(math.max)
      AgsStrategy.Estimate.toEstimate(largestAsterism / 3.0)
    }
  }

  protected [impl] def search(tipTiltMode: NfiraosTipTiltMode, ctx: ObsContext, posAngles: Set[Angle], nirBand: Option[MagnitudeBand])(ec: ExecutionContext): Future[List[NfiraosCatalogSearchResults]] =
    ctx.getBaseCoordinates.asScalaOpt.fold(Future.successful(List.empty[NfiraosCatalogSearchResults])) { base =>
      // Get the instrument: F2 or IRIS?
      val nfiraosInstrument =
        (ctx.getInstrument.getType == SPComponentType.INSTRUMENT_IRIS) ? NfiraosInstrument.iris | NfiraosInstrument.flamingos2
      // Search options
      val nfiraosOptions = new NfiraosGuideStarSearchOptions(nfiraosInstrument, tipTiltMode, posAngles.asJava)

      // Perform the catalog search, using NfiraosStrategy's backend
      val results = NfiraosVoTableCatalog(backend, UCAC4).search(ctx, base.toNewModel, nfiraosOptions, nirBand)(ec)

      // Now check that the results are valid: there must be a valid tip-tilt and flexure star each.
      results.map { r =>
        val AllKeys:List[NfiraosGuideProbeGroup] = List(NfiraosOiwfs.Wfs.Group.instance, IrisOdgw.Group.instance)
        val containedKeys = r.map(_.criterion.key.group)
        // Return a list only if both guide probes returned a value
        ~(containedKeys.forall(AllKeys.contains) option r)
      }
    }

  private def findGuideStars(ctx: ObsContext, posAngles: Set[Angle], results: List[NfiraosCatalogSearchResults]): Option[NfiraosGuideStars] = {
    // Passing in null to say we don't want a ProgressMeter.
    val nfiraosResults = NfiraosResultsAnalyzer.analyze(ctx, posAngles.asJava, results.asJava, None).asScala
    nfiraosResults.headOption
  }

  override def select(ctx: ObsContext, mt: MagnitudeTable)(ec: ExecutionContext): Future[Option[Selection]] = {
    val posAngles = ctx.getInstrument.getType match {
      case SPComponentType.INSTRUMENT_IRIS if ctx.getInstrument.asInstanceOf[Iris].getPosAngleConstraint == PosAngleConstraint.FIXED =>
        Set(ctx.getPositionAngle)
      case SPComponentType.INSTRUMENT_FLAMINGOS2 if ctx.getInstrument.asInstanceOf[Flamingos2].getPosAngleConstraint == PosAngleConstraint.FIXED =>
        Set(ctx.getPositionAngle)
      case _ =>
        Set(ctx.getPositionAngle, Angle.zero, Angle.fromDegrees(90), Angle.fromDegrees(180), Angle.fromDegrees(270))
    }

    val results = search(NfiraosTipTiltMode.nfiraos, ctx, posAngles, None)(ec)
    results.map { r =>
      val nfiraosGuideStars = findGuideStars(ctx, posAngles, r)

      // Now we must convert from an Option[NfiraosGuideStars] to a Selection.
      nfiraosGuideStars.map { x =>
        val assignments = x.guideGroup.getAll.asScalaList.filter(_.getGuider.getGroup.contains(NfiraosOiwfs.Wfs.Group.instance)).flatMap(targets => {
          val guider = targets.getGuider
          targets.getTargets.asScalaList.map(target => Assignment(guider, target.toSiderealTarget(ctx.getSchedulingBlockStart)))
        })
        Selection(x.pa, assignments)
      }
    }
  }

  override def catalogQueries(ctx: ObsContext, mt: MagnitudeTable): List[CatalogQuery] =
    ctx.getBaseCoordinates.asScalaOpt.fold(List.empty[CatalogQuery]) { base =>
      import AgsMagnitude._
      val cond = ctx.getConditions
      val mags = magnitudes(ctx, mt).toMap

      def lim(gp: GuideProbe): Option[MagnitudeConstraints] = autoSearchConstraints(mags(gp), cond)

      val odgwMagLimits = (lim(IrisOdgw.odgw1) /: IrisOdgw.values().drop(1)) { (ml, odgw) =>
        (ml |@| lim(odgw))(_ union _).flatten
      }
      val canMagLimits = (lim(NfiraosOiwfs.Wfs.oiwfs1) /: NfiraosOiwfs.Wfs.values().drop(1)) { (ml, can) =>
        (ml |@| lim(can))(_ union _).flatten
      }

      val irisOiwfsConstraint = canMagLimits.map(c => CatalogQuery(IrisOiwfsTipTiltId, base.toNewModel, RadiusConstraint.between(Angle.zero, NfiraosOiwfs.Wfs.Group.instance.getRadiusLimits.toNewModel), List(ctx.getConditions.adjust(c)), UCAC4))
      val odgwConstraint    = odgwMagLimits.map(c => CatalogQuery(OdgwFlexureId,   base.toNewModel, RadiusConstraint.between(Angle.zero, IrisOdgw.Group.instance.getRadiusLimits.toNewModel), List(ctx.getConditions.adjust(c)), UCAC4))
      List(irisOiwfsConstraint, odgwConstraint).flatten
    }

  override val probeBands = RBandsList

  // Return the band used for each probe
  // TODO Delegate to NfiraosMagnitudeTable
  private def probeBands(guideProbe: GuideProbe): BandsList = if (NfiraosOiwfs.Wfs.Group.instance.getMembers.contains(guideProbe)) RBandsList else SingleBand(MagnitudeBand.H)

  override val guideProbes: List[GuideProbe] =
    Flamingos2OiwfsGuideProbe.instance :: (IrisOdgw.values() ++ NfiraosOiwfs.Wfs.values()).toList
}

object NfiraosStrategy extends NfiraosStrategy {
  override private [impl] val backend = ConeSearchBackend

  private [impl] lazy val irisOiwfsProbes: ISet[GuideProbe] = ISet.fromList(List(NfiraosOiwfs.Wfs.oiwfs1, NfiraosOiwfs.Wfs.oiwfs2, NfiraosOiwfs.Wfs.oiwfs3))
}