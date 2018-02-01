//
// $
//

package edu.gemini.wdba.tcc;

import edu.gemini.pot.sp.ISPObsComponent;
import edu.gemini.pot.sp.SPComponentType;
import edu.gemini.shared.util.immutable.None;
import edu.gemini.shared.util.immutable.Option;
import edu.gemini.shared.util.immutable.Some;
import edu.gemini.spModel.gemini.altair.AltairParams;
import edu.gemini.spModel.gemini.altair.InstAltair;
import edu.gemini.spModel.gemini.nfiraos.Nfiraos;
import edu.gemini.spModel.gemini.iris.Iris;
import edu.gemini.spModel.gemini.niri.InstNIRI;
import org.dom4j.Document;
import org.dom4j.Element;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

/**
 * Test cases for RotatorConfig.  The logic is about as convoluted as the
 * class itself ...
 */
public final class AoConfigTest extends TestBase {

    private ISPObsComponent nfiraosObsComp;
    private ISPObsComponent altairObsComp;

    private ISPObsComponent addInstrument(SPComponentType type) throws Exception {
        final ISPObsComponent instObsComp = odb.getFactory().createObsComponent(prog, type, null);
        obs.addObsComponent(instObsComp);
        return instObsComp;
    }

    private Nfiraos addNfiraos() throws Exception {
        nfiraosObsComp = odb.getFactory().createObsComponent(prog, Nfiraos.SP_TYPE, null);
        obs.addObsComponent(nfiraosObsComp);
        return (Nfiraos) nfiraosObsComp.getDataObject();
    }

    private InstAltair addAltair() throws Exception {
        altairObsComp = odb.getFactory().createObsComponent(prog, InstAltair.SP_TYPE, null);
        obs.addObsComponent(altairObsComp);
        return (InstAltair) altairObsComp.getDataObject();
    }

    private Iris addIris() throws Exception {
        return (Iris) addInstrument(Iris.SP_TYPE).getDataObject();
    }

    private InstNIRI addNiri() throws Exception {
        return (InstNIRI) addInstrument(InstNIRI.SP_TYPE).getDataObject();
    }

    private enum Site {
        north() {
            Document getResults(TestBase base) throws Exception {
                return base.getNorthResults();
            }
        },
        south() {
            Document getResults(TestBase base) throws Exception {
                return base.getSouthResults();
            }
        },
        ;
        abstract Document getResults(TestBase base) throws Exception;
    }

    private abstract class AoConfigValidator {
        Site site;
        String name = TccNames.NO_AO;

        String getParam(Element element, String paramName) {
            List<Element> lst = (List<Element>) element.selectNodes(".//param[@name='" + paramName + "']");
            if ((lst == null) || (lst.size() == 0)) return null;
            return lst.get(0).attributeValue("value");
        }

        private String getGaosName(Element tcsConfig) {
            return getParam(tcsConfig, TccNames.GAOS);
        }

        Option<Element> getGaosElement(Document doc) {
            if (TccNames.NO_AO.equals(name)) return None.instance();

            String type = site == Site.north ? TccNames.GAOS : "nfiraos";
            Element e = getSubconfig(doc, type);
            if (e == null) return None.instance();
            return new Some<>(e);
        }


        void validate() throws Exception {
            Document res = site.getResults(AoConfigTest.this);

            Element tcsConfig = getTcsConfiguration(res);
            String gaosName = getGaosName(tcsConfig);
            assertEquals(name, gaosName);

            Option<Element> gaosElement = getGaosElement(res);
            if (gaosElement.isEmpty()) {
                assertEquals(TccNames.NO_AO, name);
                return;
            }

            validate(res, gaosElement.getValue());
        }

        protected abstract void validate(Document doc, Element gaosElement);
    }

    class AltairAoConfigValidator extends AoConfigValidator {
        InstAltair altair;

        protected void validate(Document doc, Element gaosElement) {
            // fill in Altair specific stuff here
            String fldLens = getParam(gaosElement, AOConfig.FLDLENS);
            assertEquals(altair.getFieldLens().sequenceValue(), fldLens);

            String ndFilter = getParam(gaosElement, AOConfig.NDFILTER);
            assertEquals(altair.getNdFilter().sequenceValue(), ndFilter);

            String wavelength = getParam(gaosElement, AOConfig.WAVELENGTH);
            assertEquals(altair.getWavelength().sequenceValue(), wavelength);
        }
    }

    class NfiraosAoConfigValidator extends AoConfigValidator {
        Nfiraos nfiraos;

        protected void validate(Document doc, Element gaosElement) {
            String adc = getParam(gaosElement, AOConfig.NFIRAOS_GAOS_ADC);
            assertEquals(nfiraos.getAdc().sequenceValue(), adc);

            String dich = getParam(gaosElement, AOConfig.NFIRAOS_GAOS_DICHROIC);
            assertEquals(nfiraos.getDichroicBeamsplitter().sequenceValue(), dich);

            String ast = getParam(gaosElement, AOConfig.NFIRAOS_GAOS_ASTROMETRIC);
            assertEquals(nfiraos.getAstrometricMode().sequenceValue(), ast);
        }
    }

    // Need tests for NGS/LGS etc.  Whee.
    private void testAltair(InstAltair altair) throws Exception {
        addNiri();
        addAltair();
        altairObsComp.setDataObject(altair);

        AltairAoConfigValidator val = new AltairAoConfigValidator();
        val.site  = Site.north;
        val.name  = "NGS";
        val.altair = altair;
        val.validate();
    }

    @Test public void testAltairDefault()  throws Exception {
        testAltair( new InstAltair() );
    }

    @Test public void testAltairNdFilter() throws Exception {
        InstAltair altair = new InstAltair();
        for(AltairParams.NdFilter ndFilter: AltairParams.NdFilter.values()) {
            altair.setNdFilter(ndFilter);
            testAltair(altair);
        }
    }

    @Test public void testAltairWavelength() throws Exception {
        InstAltair altair = new InstAltair();
        for(AltairParams.Wavelength wavelength: AltairParams.Wavelength.values()) {
            altair.setWavelength(wavelength);
            testAltair(altair);
        }
    }

    private void testNfiraos(Nfiraos nfiraos) throws Exception {
        addIris();
        addNfiraos();
        nfiraosObsComp.setDataObject(nfiraos);

        NfiraosAoConfigValidator val = new NfiraosAoConfigValidator();
        val.site  = Site.south;
        val.name  = TccNames.NFIRAOS_GAOS;
        val.nfiraos  = nfiraos;
        val.validate();
    }

    @Test public void testNfiraosDefault() throws Exception {
        testNfiraos(new Nfiraos());
    }

    @Test public void testNfiraosAdc() throws Exception {
        Nfiraos nfiraos = new Nfiraos();
        for (Nfiraos.Adc adc : Nfiraos.Adc.values()) {
            nfiraos.setAdc(adc);
            testNfiraos(nfiraos);
        }
    }

    @Test public void testNfiraosDichroic() throws Exception {
        Nfiraos nfiraos = new Nfiraos();
        for (Nfiraos.DichroicBeamsplitter bs : Nfiraos.DichroicBeamsplitter.values()) {
            nfiraos.setDichroicBeamsplitter(bs);
            testNfiraos(nfiraos);
        }
    }

    @Test public void testNfiraosAstrometric() throws Exception {
        Nfiraos nfiraos = new Nfiraos();
        for (Nfiraos.AstrometricMode am : Nfiraos.AstrometricMode.values()) {
            nfiraos.setAstrometricMode(am);
            testNfiraos(nfiraos);
        }
    }

    @Test public void testSouthNoNfiraos() throws Exception {
        addIris();

        AoConfigValidator val = new NfiraosAoConfigValidator();
        val.site  = Site.south;
        val.name  = TccNames.NO_AO;
        val.validate();
    }
}
