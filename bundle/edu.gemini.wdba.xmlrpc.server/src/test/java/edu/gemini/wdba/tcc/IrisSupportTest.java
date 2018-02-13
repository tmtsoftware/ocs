package edu.gemini.wdba.tcc;

import edu.gemini.shared.util.immutable.*;
import edu.gemini.spModel.ext.ObservationNode;
import edu.gemini.spModel.ext.TargetNode;
import edu.gemini.spModel.gemini.nfiraos.NfiraosOiwfs;
import edu.gemini.spModel.gemini.iris.Iris;
import edu.gemini.spModel.gemini.iris.IrisOdgw;
import edu.gemini.spModel.guide.GuideProbe;
import edu.gemini.spModel.target.SPTarget;
import edu.gemini.spModel.target.env.GuideProbeTargets;
import edu.gemini.spModel.target.env.TargetEnvironment;
import edu.gemini.spModel.target.env.UserTarget;
import edu.gemini.spModel.target.obsComp.PwfsGuideProbe;
import edu.gemini.spModel.target.obsComp.TargetObsComp;
import edu.gemini.spModel.telescope.IssPort;
import org.dom4j.Document;
import org.dom4j.Element;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

/**
 * Test cases for {@link IrisSupport}.
 */
public final class IrisSupportTest extends InstrumentSupportTestBase<Iris> {

    private final SPTarget base;

    public IrisSupportTest() throws Exception {
        super(Iris.SP_TYPE);

        base = new SPTarget();
        base.setName("Base Pos");
    }

    @Before public void setUp() throws Exception {
        super.setUp();
    }

    private static GuideProbeTargets createGuideTargets(final GuideProbe probe) {
        final SPTarget target = new SPTarget();
        return GuideProbeTargets.create(probe, target);
    }

    private static ImList<GuideProbeTargets> createGuideTargetsList(final GuideProbe... probes) {
        List<GuideProbeTargets> res = new ArrayList<>();
        for (final GuideProbe probe : probes) {
            res.add(createGuideTargets(probe));
        }
        return DefaultImList.create(res);
    }

    private TargetEnvironment create(final GuideProbe... probes) {
        final ImList<GuideProbeTargets> gtCollection = createGuideTargetsList(probes);
        final ImList<UserTarget>         userTargets = ImCollections.emptyList();
        return TargetEnvironment.create(base).setAllPrimaryGuideProbeTargets(gtCollection).setUserTargets(userTargets);
    }

    private void setTargetEnv(final GuideProbe... probes) throws Exception {
        final TargetEnvironment env = create(probes);

        // Store the target environment.
        final ObservationNode obsNode = getObsNode();
        final TargetNode targetNode = obsNode.getTarget();

        final TargetObsComp obsComp = targetNode.getDataObject();
        obsComp.setTargetEnvironment(env);
        targetNode.getRemoteNode().setDataObject(obsComp);
    }

    private void setOdgw(final Iris.OdgwSize size) throws Exception {
        final Iris iris = (Iris) obsComp.getDataObject();
        iris.setOdgwSize(size);
        obsComp.setDataObject(iris);
    }

    private Option<String> getOdgwSize(final Document doc) throws Exception {
        final Element tccFieldConfig = getTccFieldConfig(doc);
        if (tccFieldConfig == null) fail("no tcc_tcs_config_file element");

        final Element pset = (Element) tccFieldConfig.selectSingleNode("//paramset[@name='guideConfig']");
        if (pset == null) fail("missing 'guideConfig' paramset");

        final Element nfiraos = (Element) pset.selectSingleNode("paramset[@name='Nfiraos']");
        if (nfiraos == null) return None.STRING;

        final Element odgw = (Element) nfiraos.selectSingleNode("paramset[@name='odgw']");
        if (odgw == null) return None.STRING;

        final Element size = (Element) odgw.selectSingleNode("param[@name='size']");
        return ImOption.apply(size.attributeValue("value"));
    }

    private void verify(final Option<Iris.OdgwSize> size) throws Exception {
        final Document doc = getSouthResults();
        final Option<String> expectOpt = size.map(Iris.OdgwSize::displayValue);
        assertEquals(expectOpt, getOdgwSize(doc));
    }

    private void verify(final Iris.OdgwSize size) throws Exception {
        verify(new Some<>(size));
    }

    @Test public void testDefaultGuideConfig() throws Exception {
        setTargetEnv(NfiraosOiwfs.Wfs.oiwfs1, IrisOdgw.odgw1);
        verify(Iris.OdgwSize.DEFAULT);
    }

    @Test public void testExplicitGuideConfig() throws Exception {
        setTargetEnv(NfiraosOiwfs.Wfs.oiwfs1, IrisOdgw.odgw1);
        setOdgw(Iris.OdgwSize.SIZE_8);
        verify(Iris.OdgwSize.SIZE_8);
    }

    @Test public void testNoIris() throws Exception {
        setTargetEnv(NfiraosOiwfs.Wfs.oiwfs1);
        final Option<Iris.OdgwSize> none = None.instance();
        verify(none);
    }

    @Test public void testNotNfiraos() throws Exception {
        setTargetEnv(PwfsGuideProbe.pwfs1);
        final Option<Iris.OdgwSize> none = None.instance();
        verify(none);
    }

    @Test public void testPointOrig() throws Exception {
        verifyPointOrig(getSouthResults(), "lgs2iris");
    }

    @Test public void testConfig() throws Exception {
        final Iris iris = getInstrument();

        iris.setIssPort(IssPort.SIDE_LOOKING);
        setInstrument(iris);
        verifyInstrumentConfig(getSouthResults(), "IRIS5");

        iris.setIssPort(IssPort.UP_LOOKING);
        setInstrument(iris);
        verifyInstrumentConfig(getSouthResults(), "IRIS");
    }
}
