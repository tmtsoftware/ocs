package edu.gemini.ags.nfiraos

import java.util.logging.Logger

import edu.gemini.ags.nfiraos.mascot.{MascotCat, MascotProgress, Strehl}
import edu.gemini.catalog.api.MagnitudeConstraints
import edu.gemini.spModel.core._
import edu.gemini.spModel.gemini.nfiraos.NfiraosOiwfs
import edu.gemini.spModel.gemini.iris.{Iris, IrisOdgw}
import edu.gemini.spModel.gemini.obscomp.SPSiteQuality
import edu.gemini.spModel.guide.{GuideProbe, GuideStarValidation, ValidatableGuideProbe}
import edu.gemini.spModel.obs.context.ObsContext
import edu.gemini.shared.util.immutable.ScalaConverters._
import edu.gemini.shared.util.immutable.{None => JNone, Option => JOption}
import edu.gemini.pot.ModelConverters._
import edu.gemini.spModel.obscomp.SPInstObsComp
import edu.gemini.spModel.target.SPTarget
import edu.gemini.spModel.target.env.{GuideGroup, GuideProbeTargets}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scalaz._
import Scalaz._
import edu.gemini.spModel.nfiraos.NfiraosGuideProbeGroup

case class AnalysisStage(stars: List[NfiraosGuideStars], continue: Boolean)

object NfiraosResultsAnalyzer {
  val instance = this
  val Log = Logger.getLogger(NfiraosResultsAnalyzer.getClass.getSimpleName)

  // sort by value only
  private val MagnitudeValueOrdering: scala.math.Ordering[Magnitude] = scala.math.Ordering.by(_.value)

  // comparison on Option[Magnitude] that reverses the way that None is treated, i.e. None is always > Some(Magnitude).
  // Comparison of RLike bands is done by value alone
  private val MagnitudeOptionOrdering: scala.math.Ordering[Option[Magnitude]] = new scala.math.Ordering[Option[Magnitude]] {
    override def compare(x: Option[Magnitude], y: Option[Magnitude]): Int = (x,y) match {
      case (Some(m1), Some(m2)) if List(m1.band, m2.band).forall(RBandsList.bandSupported) => MagnitudeValueOrdering.compare(m1, m2)
      case (Some(m1), Some(m2))                                                            => Magnitude.MagnitudeOrdering.compare(m1, m2) // Magnitude.MagnitudeOrdering is probably incorrect, you cannot sort on different bands
      case (None,     None)                                                                => 0
      case (_,        None)                                                                => -1
      case (None,     _)                                                                   => 1
    }
  }

  // Java interfacing methods
  // ========================
  def getStrehlFactor(obsContext: JOption[ObsContext]): Double = strehlFactor(obsContext.asScalaOpt)

  /**
   * Analyze the given position angles and search results to select tip tilt asterisms and flexure stars.
   *
   * @param obsContext observation context
   * @param posAngles position angles to try (should contain at least one element: the current pos angle)
   * @param catalogSearch results of catalog search
   * @param mascotProgress used to report progress of Mascot Strehl calculations and interrupt if requested
   * @return a sorted List of NfiraosGuideStars
   */
  def analyze(obsContext: ObsContext, posAngles: java.util.Set[Angle], catalogSearch: java.util.List[NfiraosCatalogSearchResults], mascotProgress: Option[MascotProgress]): java.util.List[NfiraosGuideStars] = {
    obsContext.getBaseCoordinates.asScalaOpt.map(_.toNewModel).map { base =>
      // nfiraosGuideStars needs to be mutable to support updates from inside mascot
      val nfiraosGuideStars = TiptiltFlexurePair.pairs(catalogSearch.asScala.toList).foldLeft(List.empty[NfiraosGuideStars]) { (nfiraosGuideStars, pair) =>
        val tiptiltGroup = pair.tiptiltResults.criterion.key.group
        val flexureGroup = pair.flexureResults.criterion.key.group
        val tiptiltTargetsList = filter(obsContext, pair.tiptiltResults.results, tiptiltGroup, posAngles.asScala.toSet)
        val flexureTargetsList = filter(obsContext, pair.flexureResults.results, flexureGroup, posAngles.asScala.toSet)

        if (tiptiltTargetsList.nonEmpty && flexureTargetsList.nonEmpty) {
          // tell the UI to update
          mascotProgress.foreach {
            _.setProgressTitle(s"Finding asterisms for ${tiptiltGroup.getKey}")
          }
          val band = bandpass(tiptiltGroup, obsContext.getInstrument)
          val factor = strehlFactor(new Some[ObsContext](obsContext))
          val asterisms = MascotCat.findBestAsterismInTargetsList(tiptiltTargetsList, base.ra.toAngle.toDegrees, base.dec.toDegrees, band, factor, mascotProgress)
          val analyzedStars = asterisms.strehlList.map(analyzeAtAngles(obsContext, posAngles.asScala.toSet, _, flexureTargetsList, flexureGroup, tiptiltGroup))
          nfiraosGuideStars ::: analyzedStars.flatten
        } else {
          nfiraosGuideStars
        }
      }
      sortResultsByRanking(nfiraosGuideStars).asJava
    }.getOrElse(List.empty[NfiraosGuideStars].asJava)
  }

  // Scala interfacing methods
  // =========================
  /**
   * Analyze the given position angles and search results to select tip tilt asterisms and flexure stars.
   * This version allows the progress argument to stop the strehl algorithm when a "good enough"
   * asterism has been found and use the results up until that point.
   *
   * @param obsContext observation context
   * @param posAngles position angles to try (should contain at least one element: 0. deg)
   * @param catalogSearch results of catalog search
   * @param shouldContinue function to determine if the search should continue: early termination possible if sufficient asterism found
   * @return a sorted List of NfiraosGuideStars
   */
  def analyzeGoodEnough(obsContext: ObsContext, posAngles: Set[Angle], catalogSearch: List[NfiraosCatalogSearchResults], shouldContinue: Strehl => Boolean): List[NfiraosGuideStars] = {
    obsContext.getBaseCoordinates.asScalaOpt.map(_.toNewModel).foldMap { base =>

      @tailrec
      def go(stars: List[NfiraosGuideStars], pairs: List[TiptiltFlexurePair]): List[NfiraosGuideStars] = {
        def check(pair: TiptiltFlexurePair): List[NfiraosGuideStars] = {
          val tiptiltGroup = pair.tiptiltResults.criterion.key.group
          val flexureGroup = pair.flexureResults.criterion.key.group
          val tiptiltTargetsList = filter(obsContext, pair.tiptiltResults.results, tiptiltGroup, posAngles)
          val flexureTargetsList = filter(obsContext, pair.flexureResults.results, flexureGroup, posAngles)

          if (tiptiltTargetsList.nonEmpty && flexureTargetsList.nonEmpty) {
            // Find asterisms with Mascot
            val band = bandpass(tiptiltGroup, obsContext.getInstrument)
            val factor = strehlFactor(obsContext.some)

            // tiptiltTargetsList should only contain Nfiraos WFS stars, so asterisms should only consist of these.
            // Thus shouldContinue will only be called for Nfiraos WFS stars.
            val asterisms = MascotCat.findBestAsterismInTargetsList(tiptiltTargetsList, base.ra.toAngle.toDegrees, base.dec.toDegrees, band, factor, shouldContinue)
            val analyzedStars = asterisms.strehlList.map(analyzeAtAngles(obsContext, posAngles, _, flexureTargetsList, flexureGroup, tiptiltGroup))
            stars ::: analyzedStars.flatten
          } else {
            stars
          }
        }

        pairs match {
          case Nil       => Nil
          case x :: Nil  => check(x)
          case x :: tail => go(check(x), tail)
        }
      }
      val nfiraosGuideStars = go(Nil, TiptiltFlexurePair.pairs(catalogSearch))
      sortResultsByRanking(nfiraosGuideStars)
    }
  }

  private def printResults(result: List[NfiraosGuideStars]) {
    Log.fine("Results:")
    result.zipWithIndex.foreach { case (s, i) =>
      Log.fine(s"result #$i : $s")
    }
  }

  private def sortResultsByRanking(list: List[NfiraosGuideStars]): List[NfiraosGuideStars] = {
    // Sorry to fall back to java, but scala's tree set works differently
    val set: java.util.Set[NfiraosGuideStars] = new java.util.TreeSet[NfiraosGuideStars](list.asJava)
    val result: java.util.List[NfiraosGuideStars] = new java.util.ArrayList[NfiraosGuideStars](set)
    java.util.Collections.reverse(result)
    // Do we really need to print?
    printResults(result.asScala.toList)
    result.asScala.toList
  }

  // Tries the given asterism and flexure star at the given position angles and returns a list of
  // combinations that work.
  private def analyzeAtAngles(obsContext: ObsContext, posAngles: Set[Angle], strehl: Strehl, flexureSkyObjectList: List[SiderealTarget], flexureGroup: NfiraosGuideProbeGroup, tiptiltGroup: NfiraosGuideProbeGroup): List[NfiraosGuideStars] =
    posAngles.foldLeft(List.empty[NfiraosGuideStars]) { (stars, posAngle) =>
      val flexureList = filter(obsContext, flexureSkyObjectList, flexureGroup, posAngle)
      val flexureStars = sortTargetsByBrightness(flexureList)

      val guideStars = if ("OIWFS" == tiptiltGroup.getKey) {
        // try different order of oiwfs1 and oiwfs2
        NfiraosResultsAnalyzer.instance.analyzeStrehl(obsContext, strehl, posAngle, tiptiltGroup, flexureGroup, flexureStars, reverseOrder = true) ::: NfiraosResultsAnalyzer.instance.analyzeStrehl(obsContext, strehl, posAngle, tiptiltGroup, flexureGroup, flexureStars, reverseOrder = false)
      } else {
        NfiraosResultsAnalyzer.instance.analyzeStrehl(obsContext, strehl, posAngle, tiptiltGroup, flexureGroup, flexureStars, reverseOrder = true)
      }
      stars ::: guideStars
    }

  // Analyzes the given strehl object at the given position angle and returns a list of
  // NfiraosGuideStars objects, each containing a 1 to 3 star asterism from the given tiptiltGroup group and
  // one star from the flexure group. If any of the stars in the asterism is not valid at the position
  // angle or if no flexure star can be found, an empty list is returned.
  //
  // If reverseOrder is true, reverse the order in which guide probes are tried (to make sure to get all
  // combinations of oiwfs1 and oiwfs2, since oiwfs3 is otherwise fixed)
  private def analyzeStrehl(obsContext: ObsContext, strehl: Strehl, posAngle: Angle, tiptiltGroup: NfiraosGuideProbeGroup, flexureGroup: NfiraosGuideProbeGroup, flexureStars: List[SiderealTarget], reverseOrder: Boolean): List[NfiraosGuideStars] = {
    val tiptiltTargetList = targetListFromStrehl(strehl)
    // XXX The TPE assumes nfiraos tiptilt if there are only 2 stars (one of each ODGW and OIWFS),
    // So don't add any items to the list that have only 2 stars and IRIS as tiptilt.
    (tiptiltGroup, tiptiltTargetList) match {
      case (IrisOdgw.Group.instance, _ :: Nil)                                                  =>
        Nil
      case _ if areAllTargetsValidInGroup(obsContext, tiptiltTargetList, tiptiltGroup, posAngle) =>
        val guideProbeTargets = assignGuideProbeTargets(obsContext, posAngle, tiptiltGroup, tiptiltTargetList, flexureGroup, flexureStars, reverseOrder)
        guideProbeTargets.headOption.map { _ =>
          val guideGroup = GuideGroup.create(JNone.instance[String], guideProbeTargets.asImList)
          val nfiraosStrehl = NfiraosStrehl(strehl.avgstrehl, strehl.rmsstrehl, strehl.minstrehl, strehl.maxstrehl)
          NfiraosGuideStars(posAngle, tiptiltGroup, nfiraosStrehl, guideGroup)
        }.toList
      case _                                                                                     =>
        Nil
    }
  }

  // Returns a list of GuideProbeTargets for the given tiptilt targets and flexure star.
  //
  // If reverseOrder is true, reverse the order in which guide probes are tried (to make sure to get all
  // combinations of oiwfs1 and oiwfs2, since oiwfs3 is otherwise fixed)
  private def assignGuideProbeTargets(obsContext: ObsContext, posAngle: Angle, tiptiltGroup: NfiraosGuideProbeGroup, tiptiltTargetList: List[SiderealTarget], flexureGroup: NfiraosGuideProbeGroup, flexureStars: List[SiderealTarget], reverseOrder: Boolean): List[GuideProbeTargets] = {
    // assign guide probes for tiptilt asterism
    def addTipTiltGuideProbeTargets(targets: List[SiderealTarget], result: List[GuideProbeTargets], obsContext: ObsContext):(ObsContext, List[GuideProbeTargets]) = targets match {
      case Nil  =>
        (obsContext, result)
      case x :: Nil  =>
        val gpt = assignGuideProbeTarget(obsContext, posAngle, tiptiltGroup, x, tiptiltGroup, result, tiptiltTargetList, reverseOrder)
        // Update the ObsContext, since validation of the following targets may depend on it
        gpt.map(x => (obsContext.withTargets(obsContext.getTargets.putPrimaryGuideProbeTargets(x)), (x :: result).reverse)).getOrElse((obsContext, Nil))
      case x :: tail =>
        val gpt = assignGuideProbeTarget(obsContext, posAngle, tiptiltGroup, x, tiptiltGroup, result, tiptiltTargetList, reverseOrder)
        gpt.map(x => addTipTiltGuideProbeTargets(tail, (x :: result).reverse, obsContext.withTargets(obsContext.getTargets.putPrimaryGuideProbeTargets(x)))).getOrElse((obsContext, Nil))
    }

    // TODO: REL-2941
    // addTipTiltGuideProbeTargets(tiptiltTargetList, Nil, obsContext)._2
    //
    // Remove everything below replacing it with the above line.  Get rid of
    // "tiptiltGroup" since it is always IrisOiwfsWfs.group.instance.  Git rid
    // of flexureGroup, flexureStars  Propagate changes ...
    val tipTiltGuideProbeTargets = addTipTiltGuideProbeTargets(tiptiltTargetList, Nil, obsContext)

    // assign guide probe for flexure star
    def addFlexureGuideProbeTargets(targets: List[SiderealTarget], result: List[GuideProbeTargets], obsContext: ObsContext):List[GuideProbeTargets] = targets match {
      case Nil  =>
        result
      case x :: Nil  =>
        val gpt = assignGuideProbeTarget(obsContext, posAngle, flexureGroup, x, tiptiltGroup, result, tiptiltTargetList, reverseOrder = false)
        gpt.map(x => (x :: result).reverse).getOrElse(result)
      case x :: tail =>
        val gpt = assignGuideProbeTarget(obsContext, posAngle, flexureGroup, x, tiptiltGroup, result, tiptiltTargetList, reverseOrder = false)
        gpt.map(x => (x :: result).reverse).getOrElse(addFlexureGuideProbeTargets(tail, result, obsContext))
    }
    val flexureGuideProbeTargets = addFlexureGuideProbeTargets(flexureStars, Nil, tipTiltGuideProbeTargets._1)

    val result:List[GuideProbeTargets] = tipTiltGuideProbeTargets._2 ::: flexureGuideProbeTargets
    if (result.length == tiptiltTargetList.size + 1) result else Nil
  }

  // Returns the GuideProbeTargets object for the given tiptilt target.
  //
  // If reverseOrder is true, reverse the order in which guide probes are tried (to make sure to get all
  // combinations of oiwfs1 and oiwfs2, since oiwfs3 is otherwise fixed)
  private def assignGuideProbeTarget(obsContext: ObsContext, posAngle: Angle, group: NfiraosGuideProbeGroup, target: SiderealTarget, tiptiltGroup: NfiraosGuideProbeGroup, otherTargets: List[GuideProbeTargets], tiptiltTargetList: List[SiderealTarget], reverseOrder: Boolean): Option[GuideProbeTargets] = {
    // First try to assign oiwfs3 to the brightest star, if applicable (assignOiwfs3ToBrightest arg = true)
    val probe = guideProbe(obsContext, target, group, posAngle, tiptiltGroup, otherTargets, tiptiltTargetList, assignOiwfs3ToBrightest = true, reverseOrder = reverseOrder)
    val gp = probe match {
      case None if "OIWFS" == tiptiltGroup.getKey =>
        // if that didn't work, try to assign oiwfs3 to the second brightest star (assignOiwfs3ToBrightest arg = false)
        guideProbe(obsContext, target, group, posAngle, tiptiltGroup, otherTargets, tiptiltTargetList, assignOiwfs3ToBrightest = false, reverseOrder = reverseOrder)
      case                                     _ =>
        probe
    }
    gp.map(GuideProbeTargets.create(_, toSPTarget(target)))
  }

  // Returns the given targets list with any objects removed that are not valid in at least one of the
  // given position angles.
  private def filter(obsContext: ObsContext, targetsList: List[SiderealTarget], group: NfiraosGuideProbeGroup, posAngles: Set[Angle]): List[SiderealTarget] =
    posAngles.foldLeft(List.empty[SiderealTarget]) {(t, a) => t ++ filter(obsContext, targetsList, group, a)}

  // Returns the given targets list with any objects removed that are not valid in the
  // given position angle.
  private def filter(obsContext: ObsContext, targetsList: List[SiderealTarget], group: NfiraosGuideProbeGroup, posAngle: Angle): List[SiderealTarget] =
    targetsList.filter(isTargetValidInGroup(obsContext, _, group, posAngle))

  // Returns true if all the stars in the given target list are valid for the given group
  private def areAllTargetsValidInGroup(obsContext: ObsContext, targetList: List[SiderealTarget], group: NfiraosGuideProbeGroup, posAngle: Angle): Boolean =
    targetList.forall(isTargetValidInGroup(obsContext, _, group, posAngle))

  // Returns true if the given target is valid for the given group
  private def isTargetValidInGroup(obsContext: ObsContext, target: SiderealTarget, group: NfiraosGuideProbeGroup, posAngle: Angle): Boolean = {
    val ctx = obsContext.withPositionAngle(posAngle)
    val st = toSPTarget(target)
    group.getMembers.asScala.exists(_.validate(st, ctx) == GuideStarValidation.VALID)
  }

  // Returns the first valid guide probe for the given target in the given guide probe group at the given
  // position angle. Note that if tiptiltGroup != group, we're looking for a flexure star, otherwise a
  // tiptilt star.
  // If assignOiwfs3ToBrightest is true, the brightest star (in tiptiltTargetList) is assigned to oiwfs3,
  // otherwise the second brightest (OT-27).
  // If reverseOrder is true, reverse the order in which guide probes are tried (to make sure to get all
  // combinations of oiwfs1 and oiwfs2, since oiwfs3 is otherwise fixed)
  private def guideProbe(obsContext: ObsContext, target: SiderealTarget, group: NfiraosGuideProbeGroup, posAngle: Angle, tiptiltGroup: NfiraosGuideProbeGroup, otherTargets: List[GuideProbeTargets], tiptiltTargetList: List[SiderealTarget], assignOiwfs3ToBrightest: Boolean, reverseOrder: Boolean): Option[ValidatableGuideProbe] = {
    val ctx = obsContext.withPositionAngle(posAngle)
    val isFlexure = tiptiltGroup != group
    val isTiptilt = !isFlexure

    def isValidGuideProbe(guideProbe: GuideProbe):Boolean = {
      val valid = validate(ctx, target, guideProbe)
      val otherTargetsValid = checkOtherTargets(guideProbe, otherTargets)
      if (valid && otherTargetsValid && isTiptilt && "OIWFS" == tiptiltGroup.getKey) {
        checkOiwfs3Rule(guideProbe, target, tiptiltTargetList, assignOiwfs3ToBrightest)
      } else if (valid && isTiptilt) {
        otherTargetsValid
      } else {
        valid
      }
    }

    // Special case:
    // If the tip tilt asterism is assigned to the IRIS ODGW group, then the flexure star must be assigned to OIWFS3.
    if (isFlexure && ("ODGW" == tiptiltGroup.getKey) && NfiraosOiwfs.Wfs.oiwfs3.validate(toSPTarget(target), ctx) == GuideStarValidation.VALID) {
      NfiraosOiwfs.Wfs.oiwfs3.some
    } else {
      val members = if (reverseOrder) group.getMembers.asScala.toList.reverse else group.getMembers.asScala.toList
      members.find(isValidGuideProbe)
    }
  }

  // Returns true if the given target is valid for the given guide probe
  private def validate(ctx: ObsContext, target: SiderealTarget, guideProbe: GuideProbe): Boolean =
    guideProbe match {
      case wfs: NfiraosOiwfs.Wfs          =>
        // Additional check for mag range (for oiwfs1 and oiwfs2, since different than oiwfs3 and group range)
        val irisOiwfsWfsCalculator = NfiraosMagnitudeTable.IrisOiwfsWfsMagnitudeLimitsCalculator
        wfs.validate(toSPTarget(target), ctx) == GuideStarValidation.VALID && containsMagnitudeInLimits(target, irisOiwfsWfsCalculator.getNominalMagnitudeConstraints(wfs))
      case vp: ValidatableGuideProbe =>
        vp.validate(toSPTarget(target), ctx) == GuideStarValidation.VALID
      case _                         =>
        true
    }

  // Returns true if the given oiwfs guide probe can be assigned to the given target according to the rules in OT-27.
  // If assignOiwfs3ToBrightest is true, the brightest star in the asterism (in tiptiltTargetList) is assigned to oiwfs3,
  // otherwise the second brightest (OT-27).
  private def checkOiwfs3Rule(guideProbe: GuideProbe, target: SiderealTarget, tiptiltTargetList: List[SiderealTarget], assignOiwfs3ToBrightest: Boolean): Boolean = {
    val isOiwfs3 = guideProbe == NfiraosOiwfs.Wfs.oiwfs3
    // sort, put brightest stars first
    val targets = sortTargetsByBrightness(tiptiltTargetList)
    targets match {
      case Nil                                                                      =>
        isOiwfs3 // no asterism
      case _ :: Nil                                                                 =>
        isOiwfs3 // single star asterism must be oiwfs3
      case brightest :: secondBrightest :: _ if isOiwfs3 && assignOiwfs3ToBrightest   =>
        brightest == target
      case brightest :: secondBrightest :: _ if isOiwfs3 && !assignOiwfs3ToBrightest  =>
        secondBrightest == target
      case brightest :: secondBrightest :: _ if !isOiwfs3 && assignOiwfs3ToBrightest  =>
        brightest != target
      case brightest :: secondBrightest :: _ if !isOiwfs3 && !assignOiwfs3ToBrightest =>
        secondBrightest != target
    }
  }

  // Returns true if none of the other targets are assigned the given guide probe.
  //
  // From OT-27: Only one star per IRIS ODGW is allowed -- for example, if an asterism is formed
  // of two guide stars destined for ODGW2, then it cannot be used.
  //
  // Also for Nfiraos: only assign one star per oiwfs
  private def checkOtherTargets(guideProbe: GuideProbe, otherTargets: List[GuideProbeTargets]): Boolean =
    !otherTargets.exists(_.getGuider == guideProbe)

  // Returns the stars in the given asterism as a SPTarget list, sorted by R mag, brightest first.
  private def targetListFromStrehl(strehl: Strehl): List[SiderealTarget] =
    sortTargetsByBrightness(strehl.stars.map(_.target))

  // OT-33: If the asterism is a Nfiraos asterism, use R. If an ODGW asterism,
  // see OT-22 for a mapping of IRIS filters to J, H, and K.
  // If iterating over filters, I think we can assume the filter in
  // the static component as a first pass at least.
  private def bandpass(group: NfiraosGuideProbeGroup, inst: SPInstObsComp): BandsList =
    (group, inst) match {
      case (IrisOdgw.Group.instance, iris: Iris) =>
        iris.getFilter.getCatalogBand.asScalaOpt.getOrElse(RBandsList)
      case _ =>
        RBandsList
    }

  // REL-426: Multiply the average, min, and max Strehl values reported by Mascot by the following scale
  // factors depending on the filter used in the instrument component of the observation (IRIS, F2 in the future):
  //   0.2 in J,
  //   0.3 in H,
  //   0.4 in K
  // See OT-22 for the mapping of IRIS filters to JHK equivalent
  //
  // Update for REL-1321:
  // Multiply the average, min, and max Strehl values reported by Mascot by the following scale factors depending
  // on the filter used in the instrument component of the observation (IRIS, F2 and GMOS-S in the future) and
  // the conditions:
  //  J: IQ20=0.12 IQ70=0.06 IQ85=0.024 IQAny=0.01
  //  H: IQ20=0.18 IQ70=0.14 IQ85=0.06 IQAny=0.01
  //  K: IQ20=0.35 IQ70=0.18 IQ85=0.12 IQAny=0.01
  private def strehlFactor(obsContext: Option[ObsContext]): Double = {
    obsContext.map(o => (o, o.getInstrument)).collect {
      case (ctx, iris: Iris) =>
        val band = iris.getFilter.getCatalogBand.asScalaOpt
        val iq = Option(ctx.getConditions).map(_.iq)
        (band, iq) match {
          case (Some(SingleBand(MagnitudeBand.J)), Some(SPSiteQuality.ImageQuality.PERCENT_20)) => 0.12
          case (Some(SingleBand(MagnitudeBand.J)), Some(SPSiteQuality.ImageQuality.PERCENT_70)) => 0.06
          case (Some(SingleBand(MagnitudeBand.J)), Some(SPSiteQuality.ImageQuality.PERCENT_85)) => 0.024
          case (Some(SingleBand(MagnitudeBand.J)), None)                                        => 0.01
          case (Some(SingleBand(MagnitudeBand.H)), Some(SPSiteQuality.ImageQuality.PERCENT_20)) => 0.18
          case (Some(SingleBand(MagnitudeBand.H)), Some(SPSiteQuality.ImageQuality.PERCENT_70)) => 0.14
          case (Some(SingleBand(MagnitudeBand.H)), Some(SPSiteQuality.ImageQuality.PERCENT_85)) => 0.06
          case (Some(SingleBand(MagnitudeBand.H)), None)                                        => 0.01
          case (Some(SingleBand(MagnitudeBand.K)), Some(SPSiteQuality.ImageQuality.PERCENT_20)) => 0.35
          case (Some(SingleBand(MagnitudeBand.K)), Some(SPSiteQuality.ImageQuality.PERCENT_70)) => 0.18
          case (Some(SingleBand(MagnitudeBand.K)), Some(SPSiteQuality.ImageQuality.PERCENT_85)) => 0.12
          case (Some(SingleBand(MagnitudeBand.K)), None)                                        => 0.01
          case _                                                                    => 0.3
        }
    }.getOrElse(0.3)
  }

  /**
   * Sorts the targets list, putting the brightest stars first and returns the sorted array.
   */
  protected [ags] def sortTargetsByBrightness(targetsList: List[SiderealTarget]): List[SiderealTarget] =
    targetsList.sortBy(RBandsList.extract)(MagnitudeOptionOrdering)

  // Returns true if the target magnitude is within the given limits
  def containsMagnitudeInLimits(target: SiderealTarget, magLimits: MagnitudeConstraints): Boolean =
    // The true default is suspicious but changing it to false breaks backwards compatibility
    magLimits.searchBands.extract(target).forall(magLimits.contains)

  def toSPTarget(siderealTarget: SiderealTarget):SPTarget = new SPTarget(siderealTarget)

}


