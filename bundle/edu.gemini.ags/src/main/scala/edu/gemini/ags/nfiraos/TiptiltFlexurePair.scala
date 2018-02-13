package edu.gemini.ags.nfiraos

import scala.collection.JavaConverters._
import scalaz._
import Scalaz._
import edu.gemini.spModel.nfiraos.NfiraosGuideStarType

case class TiptiltFlexurePair(tiptiltResults: NfiraosCatalogSearchResults, flexureResults: NfiraosCatalogSearchResults)

/**
 * Groups a pair of NfiraosCatalogSearchResults to be used for tiptilt and flexure stars.
 * The two results must be from different guide probe groups (Nfiraos, IRIS, etc).
 */
object TiptiltFlexurePair {
  private case class TipTiltFlexure(tt: Option[NfiraosCatalogSearchResults], flex: Option[NfiraosCatalogSearchResults])

  def pairs(results: List[NfiraosCatalogSearchResults]): List[TiptiltFlexurePair] = {
    val pairs = (TipTiltFlexure(None, None), TipTiltFlexure(None, None))
    // Go Over the results and assign them to buckets, it will keep the last result for each subgroup
    val resultPair = results.foldLeft(pairs) { (p, v) =>
      v.criterion.key match {
        case k if k.starType == NfiraosGuideStarType.tiptilt && k.group.getKey == "OIWFS"                                    => (p._1.copy(tt = v.some), p._2)
        case k if k.starType == NfiraosGuideStarType.tiptilt && k.group.getKey == "ODGW"                                    => (p._1, p._2.copy(tt = v.some))
        case k if k.starType == NfiraosGuideStarType.flexure && (k.group.getKey == "ODGW" || k.group.getKey == "FII OIWFS") => (p._1.copy(flex = v.some), p._2)
        case k if k.starType == NfiraosGuideStarType.flexure && k.group.getKey == "OIWFS"                                    => (p._1, p._2.copy(flex = v.some))
      }
    }
    // If they have valid results convert them to pairs
    List(resultPair._1, resultPair._2).collect {
      case TipTiltFlexure(Some(tt), Some(flex)) => TiptiltFlexurePair(tt, flex)
    }
  }
}
