package edu.gemini.itc.baseline

import edu.gemini.itc.baseline.util._
import edu.gemini.itc.shared.{IrisParameters, GemsParameters}
import edu.gemini.spModel.gemini.iris.Iris

/**
 * IRIS baseline test fixtures.
 */
object BaselineIris  {

  lazy val Fixtures = KBandImaging ++ RBandImaging

  private lazy val KBandImaging = Fixture.kBandImgFixtures(List(
    new IrisParameters(
      Iris.Filter.Z,
      Iris.ReadMode.BRIGHT,
      Gems
    )
  ))

  private lazy val RBandImaging = Fixture.rBandImgFixtures(List(
    new IrisParameters(
      Iris.Filter.J,
      Iris.ReadMode.FAINT,
      Gems
    )
  ))

  private lazy val Gems = new GemsParameters(0.3, "K")

}
