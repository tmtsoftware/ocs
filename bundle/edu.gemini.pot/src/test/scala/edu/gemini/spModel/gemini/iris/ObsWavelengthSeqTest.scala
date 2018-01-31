package edu.gemini.spModel.gemini.iris

import Iris.UtilityWheel
import Iris.Filter

import edu.gemini.spModel.config.injector.ObsWavelengthTestBase
import edu.gemini.spModel.data.config.DefaultParameter
import edu.gemini.spModel.test.{InstrumentSequenceTestBase => Base}

import org.junit.Test

/**
 * Wavelength sequence tests for IRIS.
 */
class ObsWavelengthSeqTest extends ObsWavelengthTestBase[Iris, IrisSeqConfig] {
  private def utilityWheelParam(u: UtilityWheel*): DefaultParameter = param(Iris.UTILITY_WHEEL_PROP, u: _*)
  private def filterParam(f: Filter*): DefaultParameter = param(Iris.FILTER_PROP, f: _*)

  private def verifyFilters(filters: Filter*) {
    verifyWavelength(filters.map(_.formattedWavelength): _*)
  }

  def getObsCompSpType = Iris.SP_TYPE
  def getSeqCompSpType = IrisSeqConfig.SP_TYPE

  @Test def testStatic() {
    getInstDataObj.setFilter(Filter.CO)
    storeStaticUpdates

    val sc = Base.createSysConfig
    sc.putParameter(utilityWheelParam(UtilityWheel.CLEAR, UtilityWheel.DEFAULT))
    setSysConfig(sc)

    verifyFilters(Filter.CO, Filter.CO)
  }

  @Test def testIterateFilter() {
    val sc = Base.createSysConfig
    sc.putParameter(filterParam(Filter.CO, Filter.H20_ICE))
    setSysConfig(sc)

    verifyFilters(Filter.CO, Filter.H20_ICE)
  }
}