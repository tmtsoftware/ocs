package edu.gemini.itc.baseline.util

import edu.gemini.itc.shared._
import edu.gemini.spModel.gemini.obscomp.SPSiteQuality.CloudCover
import edu.gemini.spModel.target._

// TEMPORARY helper
// All input objects will become immutable data only objects (probably Scala case classes).
// For now we need a workaround for missing hash functions on the existing Java objects.
object Hash {

  def calc(ip: InstrumentDetails): Int = ip match {
    case p: AcquisitionCamParameters  => calc(p)
    case p: Flamingos2Parameters      => calc(p)
    case p: GmosParameters            => calc(p)
    case p: GnirsParameters           => calc(p)
    case p: GsaoiParameters           => calc(p)
    case p: MichelleParameters        => calc(p)
    case p: NifsParameters            => calc(p)
    case p: NiriParameters            => calc(p)
    case p: TRecsParameters           => calc(p)
    case _                            => throw new Exception("no hash function available")
  }

  def calc(p: GmosParameters): Int =
    hash(
      p.filter.name,
      p.ccdType.name,
      p.fpMask.name,
      p.grating.name,
      p.ifuMethod.toString,
      f"${p.centralWavelength.toNanometers}%.0f",
      p.site.name,
      p.spatialBinning,
      p.spectralBinning
    )

  def calc(p: GnirsParameters): Int =
    hash(
      p.grating.name,
      p.pixelScale.name,
      p.crossDispersed.name,
      p.readMode.name,
      f"${p.centralWavelength.toNanometers}%.0f",
      p.slitWidth.name
    )

  def calc(p: GsaoiParameters): Int =
    hash(
      p.filter.name,
      p.readMode.name,
      calc(p.gems)
    )

  def calc(p: MichelleParameters): Int =
    hash(
      p.filter.name,
      p.mask.name,
      p.grating.name,
      f"${p.centralWavelength.toNanometers}%.0f",
      p.polarimetry.name
    )

  def calc(p: NifsParameters): Int =
    hash(
      p.filter.name,
      p.grating.name,
      p.readMode.name,
      f"${p.centralWavelength.toNanometers}%.0f",
      p.ifuMethod,
      calc(p.altair)
    )

  def calc(p: NiriParameters): Int =
    hash(
      p.camera.name,
      p.filter.name,
      p.mask.name,
      p.grism.name,
      p.readMode.name,
      p.wellDepth.name,
      calc(p.altair)
    )

  def calc(p: TRecsParameters): Int =
    hash(
      p.filter.name,
      p.mask.name,
      p.grating.name,
      f"${p.centralWavelength.toNanometers}%.0f",
      p.instrumentWindow.name
    )

  def calc(p: AcquisitionCamParameters): Int =
    hash(
      p.colorFilter.name,
      p.ndFilter.name
    )

  def calc(p: Flamingos2Parameters): Int =
    hash(
      p.filter.name,
      p.mask.name,
      p.grism.name,
      p.readMode.name
    )

  def calc(odp: ObservationDetails): Int =
    hash(
      odp.isAutoAperture,
      odp.getMethod.isS2N,
      odp.getMethod.isImaging,
      f"${odp.getExposureTime}%.2f",
      odp.getNumExposures,
      f"${odp.getApertureDiameter}%.2f",
      f"${odp.getSkyApertureDiameter}%.2f",
      f"${odp.getSNRatio}%.2f",
      f"${odp.getSourceFraction}%.2f"
    )

  def calc(src: SourceDefinition): Int =
    hash(
      src.profile match {
        case PointSource              => "POINT"
        case UniformSource            => "UNIFORM"
        case GaussianSource(_)        => "GAUSSIAN"
      },
      src.distribution match {
        case l: LibraryStar           => "LIBRARY_STAR"
        case l: LibraryNonStar        => "LIBRARY_NON_STAR"
        case BlackBody(_)             => "BBODY"
        case EmissionLine(_, _, _, _) => "ELINE"
        case PowerLaw(_)              => "PLAW"
        case UserDefined(_)           => "USER_DEFINED"
      },
      src.profile,
      src.distribution match {
        case BlackBody(t)             => f"$t%.2f"
        case PowerLaw(i)              => f"$i%.2f"
        case EmissionLine(w, s, f, c) => f"${w.toNanometers}%.0f ${s.toKilometersPerSecond}%.2f ${f.toWattsPerSquareMeter}%.4e ${c.toWattsPerSquareMeterPerMicron}%.4e"
        case UserDefined(s)           => s
        case l: Library               => l.sedSpectrum
      },
      src.norm,               // this is the magnitude value
      src.normBand.name,      // this is the magnitude band name
      src.redshift
    )

  def calc(tp: TelescopeDetails): Int =
    hash(
      tp.getInstrumentPort.name,
      tp.getMirrorCoating.name,
      tp.getWFS.name
    )

  def calc(ocp: ObservingConditions): Int =
    hash(
      ocp.airmass,
      ocp.iq.name,
      ocp.cc.name,
      ocp.wv.name,
      ocp.sb.name
    )

  def calc(alt: Option[AltairParameters]): Int = alt match {
    case Some(altair) => calc(altair)
    case None         => 0
  }

  def calc(alt: AltairParameters): Int =
    hash (
      f"${alt.guideStarMagnitude}%.2f",
      f"${alt.guideStarSeparation}%.2f",
      alt.fieldLens.name,
      alt.wfsMode.name
    )

  def calc(alt: GemsParameters): Int =
    hash(
      f"${alt.avgStrehl}%.2f",
      alt.strehlBand
    )

  def calc(pdp: PlottingDetails): Int =
    hash(
      f"${pdp.getPlotWaveL}%.2f",
      f"${pdp.getPlotWaveU}%.2f"
    )

  private def hash(values: Any*) =
    values.
      filter(_ != null).
      map(_.hashCode).
      foldLeft(17)((acc, h) => 37*acc + h)

}

