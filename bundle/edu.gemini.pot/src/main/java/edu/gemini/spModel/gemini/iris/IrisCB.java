package edu.gemini.spModel.gemini.iris;

import edu.gemini.pot.sp.ISPObsComponent;
import edu.gemini.spModel.config.AbstractObsComponentCB;
import edu.gemini.spModel.data.config.*;
import edu.gemini.spModel.obscomp.InstConstants;


import java.util.Collection;
import java.util.Map;

/**
 * Configuration Builder for IRIS.
 */
public final class IrisCB extends AbstractObsComponentCB {

    private transient ISysConfig sysConfig;

    public IrisCB(ISPObsComponent obsComp) {
        super(obsComp);
    }

    public Object clone() {
        IrisCB result = (IrisCB) super.clone();
        result.sysConfig = null;
        return result;
    }

    @Override
    protected void thisReset(Map<String, Object> options)  {
        Iris dataObj = (Iris) getDataObject();
        if (dataObj == null) {
            throw new RuntimeException("Iris data object is null");
        }
        sysConfig = dataObj.getSysConfig();
    }

    protected boolean thisHasConfiguration() {
        return sysConfig != null && (sysConfig.getParameterCount() > 0);
    }

    protected void thisApplyNext(IConfig config, IConfig prevFull)  {
        String systemName = sysConfig.getSystemName();

        Collection<IParameter> params = sysConfig.getParameters();
        for (IParameter param : params) {
            config.putParameter(systemName,
                    DefaultParameter.getInstance(param.getName(), param.getValue()));
        }
        config.putParameter(systemName,
                StringParameter.getInstance(InstConstants.INSTRUMENT_NAME_PROP, Iris.SP_TYPE.narrowType));

        Iris.WAVELENGTH_INJECTOR.inject(config, prevFull);
    }
}
