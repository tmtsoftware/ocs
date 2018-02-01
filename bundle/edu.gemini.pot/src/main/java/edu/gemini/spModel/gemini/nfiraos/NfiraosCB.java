package edu.gemini.spModel.gemini.nfiraos;

import edu.gemini.pot.sp.ISPObsComponent;
import edu.gemini.spModel.ao.AOConstants;
import edu.gemini.spModel.config.AbstractObsComponentCB;
import edu.gemini.spModel.data.config.*;


import java.util.Collection;
import java.util.Map;

/**
 * The {@link edu.gemini.spModel.config.IConfigBuilder configuration builder}
 * for Nfiraos. Responsible for adding bits of configuration information into
 * the sequence.
 */
public final class NfiraosCB extends AbstractObsComponentCB {

    private transient ISysConfig sysConfig;

    public NfiraosCB(ISPObsComponent obsComp) {
        super(obsComp);
    }

    public Object clone() {
        NfiraosCB res = (NfiraosCB) super.clone();
        res.sysConfig = null;
        return res;
    }

    @Override
    protected void thisReset(Map<String, Object> options) {
        Nfiraos dataObj = (Nfiraos) getDataObject();
        if (dataObj == null) throw new RuntimeException("Missing NfiraosCB data object");
        sysConfig = dataObj.getSysConfig();
    }

    protected boolean thisHasConfiguration() {
        return (sysConfig != null) && (sysConfig.getParameterCount() > 0);
    }

    protected void thisApplyNext(IConfig config, IConfig prevFull)  {
        String sysName = sysConfig.getSystemName();
        Collection<IParameter> params = sysConfig.getParameters();

        for (IParameter param : params) {
            config.putParameter(sysName, DefaultParameter.getInstance(param.getName(), param.getValue()));
        }
        config.putParameter(sysName, StringParameter.getInstance(AOConstants.AO_SYSTEM_PROP, Nfiraos.SYSTEM_NAME));
    }
}
