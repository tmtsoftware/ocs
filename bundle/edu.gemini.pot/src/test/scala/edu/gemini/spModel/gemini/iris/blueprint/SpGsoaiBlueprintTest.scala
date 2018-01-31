package edu.gemini.spModel.gemini.iris.blueprint

import org.junit.Test
import org.junit.Assert._
import scala.collection.JavaConversions._
import edu.gemini.spModel.gemini.iris.Iris
import edu.gemini.spModel.core.Site
import edu.gemini.spModel.pio.xml.PioXmlFactory

class SpGsoaiBlueprintTest {
  @Test
  def sanityTests() {
    // Check filters are preserved
    val blueprint = new SpIrisBlueprint(List(Iris.Filter.FE_II))
    assertEquals(Iris.Filter.FE_II :: Nil, blueprint.filters.toList)

    assertEquals(1, blueprint.toParamSet(new PioXmlFactory).getParams(SpIrisBlueprint.FILTERS_PARAM_NAME).size())
  }
}
