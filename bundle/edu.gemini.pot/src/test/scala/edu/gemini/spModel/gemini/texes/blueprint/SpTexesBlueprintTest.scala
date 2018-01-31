package edu.gemini.spModel.gemini.texes.blueprint

import org.junit.Test
import org.junit.Assert._
import edu.gemini.spModel.gemini.iris.Iris
import edu.gemini.spModel.core.Site
import edu.gemini.spModel.pio.xml.PioXmlFactory
import edu.gemini.spModel.gemini.iris.blueprint.SpIrisBlueprint
import edu.gemini.spModel.gemini.texes.{TexesParams, InstTexes}

class SpTexesBlueprintTest {
  @Test
  def sanityTests() {
    // Check filters are preserved
    val blueprint = new SpTexesBlueprint(TexesParams.Disperser.D_32_LMM)
    assertEquals(TexesParams.Disperser.D_32_LMM, blueprint.disperser)

    // Veriff Texes is in GN
    assertEquals(1, blueprint.toParamSet(new PioXmlFactory).getParams(SpTexesBlueprint.DISPERSER_PARAM_NAME).size())
  }
}
