//
// $
//

package edu.gemini.spModel.gemini.iris;

import edu.gemini.pot.sp.ISPSeqComponent;
import edu.gemini.spModel.config.HelperSeqCompCB;
import edu.gemini.spModel.data.config.IConfig;
import edu.gemini.spModel.data.config.StringParameter;
import edu.gemini.spModel.obscomp.InstConstants;
import edu.gemini.spModel.seqcomp.SeqConfigNames;

import java.util.Map;

/**
 * Configuration builder for the IRIS iterator.
 */
public final class IrisSeqConfigCB extends HelperSeqCompCB {

    public IrisSeqConfigCB(ISPSeqComponent seqComp) {
        super(seqComp);
    }

    public Object clone() {
        return super.clone();
    }

    protected void thisApplyNext(IConfig config, IConfig prevFull) {
        super.thisApplyNext(config, prevFull);
        config.putParameter(SeqConfigNames.INSTRUMENT_CONFIG_NAME,
                StringParameter.getInstance(InstConstants.INSTRUMENT_NAME_PROP,
                        Iris.SP_TYPE.narrowType));

        Iris.WAVELENGTH_INJECTOR.inject(config, prevFull);
    }

    @Override
    public void thisReset(Map<String, Object> options) {
        super.thisReset(options);
    }
}
