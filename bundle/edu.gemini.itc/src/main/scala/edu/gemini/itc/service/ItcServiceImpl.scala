package edu.gemini.itc.service

import edu.gemini.auxfile.client.AuxFileClient
import edu.gemini.itc.acqcam.AcqCamRecipe
import edu.gemini.itc.base._
import edu.gemini.itc.flamingos2.Flamingos2Recipe
import edu.gemini.itc.gmos.GmosRecipe
import edu.gemini.itc.gnirs.GnirsRecipe
import edu.gemini.itc.iris.IrisRecipe
import edu.gemini.itc.nifs.NifsRecipe
import edu.gemini.itc.niri.NiriRecipe
import edu.gemini.itc.shared._
import edu.gemini.spModel.core.{AuxFileSpectrum, UserDefinedSpectrum, SpectralDistribution, SPProgramID}

import scalaz._
import Scalaz._

/**
 * The ITC service implementation.
 *
 * Note that all results are repacked in simplified Scala case classes in order not to leak out any of the
 * implementation details of the underlying ITC functionality.
 */
class ItcServiceImpl extends ItcService {

  import ItcService._

  def calculate(p: ItcParameters): Result = try {

    // update parameters sent from client with stuff that needs to be done on the server
    val updatedParams: ItcParameters = {

      // Get the SED data from an aux file. For now we can assume that the ITC service is running on the same
      // machine as the database (localhost). In case this setup changes, we need to change this here, too.
      def readAuxFile(id: String, name: String): SpectralDistribution = {
        val programId     = SPProgramID.toProgramID(id)
        val spectrumBytes = new AuxFileClient("localhost", 8443).fetchToMemory(programId, name)
        val spectrum      = new String(spectrumBytes)
        UserDefinedSpectrum(name, spectrum)
      }

      // if a user defined source distribution is involved we need to read the aux file and update the parameters accordingly
      def updatedSrc(s: SourceDefinition) = s.distribution match {
        case AuxFileSpectrum.Undefined    => throw new RuntimeException("The user SED is undefined.")   // "User Defined", but no SED file was available
        case AuxFileSpectrum(anId, aName) => s.copy(distribution = readAuxFile(anId, aName))            // "User Defined", we need to replace placeholder with aux file
        case _                            => s                                                          // for all other cases we can use what's there
      }

      // do any updates necessary
      p.copy(source = updatedSrc(p.source))
    }

    // execute ITC service call with updated parameters
    updatedParams.observation.calculationMethod match {
      case _: Imaging       => calculateImaging(updatedParams)
      case _: Spectroscopy  => calculateSpectroscopy(updatedParams)
    }

  } catch {
    case e: Throwable => ItcResult.forException(e)
  }

  // === Imaging

  private def calculateImaging(p: ItcParameters): Result =
    p.instrument match {
      case _: MichelleParameters          => ItcResult.forMessage ("Imaging not implemented.")
      case _: TRecsParameters             => ItcResult.forMessage ("Imaging not implemented.")
      case i: AcquisitionCamParameters    => imagingResult        (new AcqCamRecipe(p, i))
      case i: Flamingos2Parameters        => imagingResult        (new Flamingos2Recipe(p, i))
      case i: GmosParameters              => imagingResult        (new GmosRecipe(p, i))
      case i: IrisParameters             => imagingResult        (new IrisRecipe(p, i))
      case i: NiriParameters              => imagingResult        (new NiriRecipe(p, i))
      case i: GnirsParameters             => imagingResult        (new GnirsRecipe(p, i))
      case _                              => ItcResult.forMessage ("Imaging with this instrument is not supported by ITC.")
    }

  private def imagingResult(recipe: ImagingRecipe): Result = {
    val r = recipe.calculateImaging()
    val s = recipe.serviceResult(r)
    ItcResult.forResult(s)
  }

  private def imagingResult(recipe: ImagingArrayRecipe): Result = {
    val r = recipe.calculateImaging()
    val s = recipe.serviceResult(r)
    ItcResult.forResult(s)
  }


  // === Spectroscopy

  private def calculateSpectroscopy(p: ItcParameters): Result =
    p.instrument match {
      case i: MichelleParameters          => ItcResult.forMessage ("Spectroscopy not implemented.")
      case i: TRecsParameters             => ItcResult.forMessage ("Spectroscopy not implemented.")
      case i: Flamingos2Parameters        => spectroscopyResult   (new Flamingos2Recipe(p, i))
      case i: GmosParameters              => spectroscopyResult   (new GmosRecipe(p, i))
      case i: GnirsParameters             => spectroscopyResult   (new GnirsRecipe(p, i))
      case i: NifsParameters              => spectroscopyResult   (new NifsRecipe(p, i))
      case i: NiriParameters              => spectroscopyResult   (new NiriRecipe(p, i))
      case _                              => ItcResult.forMessage ("Spectroscopy with this instrument is not supported by ITC.")

    }

  private def spectroscopyResult(recipe: SpectroscopyRecipe): Result = {
    val r = recipe.calculateSpectroscopy()
    val s = recipe.serviceResult(r)
    ItcResult.forResult(s)
  }

  private def spectroscopyResult(recipe: SpectroscopyArrayRecipe): Result = {
    val r = recipe.calculateSpectroscopy()
    val s = recipe.serviceResult(r)
    ItcResult.forResult(s)
  }



}
