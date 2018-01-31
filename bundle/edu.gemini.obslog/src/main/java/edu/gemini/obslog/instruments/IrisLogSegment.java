package edu.gemini.obslog.instruments;

import edu.gemini.obslog.config.model.OlLogItem;
import edu.gemini.obslog.core.OlSegmentType;
import edu.gemini.obslog.obslog.ConfigMap;
import edu.gemini.obslog.obslog.InstrumentLogSegment;
import edu.gemini.obslog.obslog.OlLogOptions;
import edu.gemini.spModel.gemini.iris.Iris;
import edu.gemini.spModel.type.LoggableSpType;

import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IrisLogSegment extends InstrumentLogSegment {
    private static final Logger LOG = Logger.getLogger(IrisLogSegment.class.getName());

    private static final String NARROW_TYPE = "IRIS";
    public static final OlSegmentType SEG_TYPE = new OlSegmentType(NARROW_TYPE);

    private static final String SEGMENT_CAPTION = "IRIS Observing Log";

    /*
    <logEntry key="IRIS">
        <entry key="observationid"/>
        <entry key="datasetut"/>
        <entry key="datalabels"/>
        <entry key="filename"/>
        <entry key="targetname"/>
        <entry key="iris_filter"/>
        <entry key="observe_exposuretime"/>
        <entry key="iris_readmode"/>
        <entry key="iris_coadds"/>
        <entry key="comment"/>
        <entry key="observe_status"/>
        <entry key="datasetcomments"/>
    </logEntry>
    */

    // must match ObsLogConfig.xml
    public static final String FILTER_KEY    = "filter";
    public static final String READ_MODE_KEY = "readMode";
    public static final String COADDS_KEY    = "coadds";

    public IrisLogSegment(List<OlLogItem> logItems, OlLogOptions obsLogOptions) {
        super(SEG_TYPE, logItems, obsLogOptions);
    }

    /**
     * Given an ObservationData object, create its possibly specialized bean
     * data.  This method is a factory for the particular segments type of bean
     * data.
     *
     * @param map <code>UniqueConfigMap</code>
     */
    public void decorateObservationData(ConfigMap map) {
        decorateVal(map, FILTER_KEY, Iris.Filter.class);
        decorateVal(map, READ_MODE_KEY, Iris.ReadMode.class);
    }

    private void decorateVal(ConfigMap map, String key, Class c) {
        if (map == null) return;

        String strValue = map.sget(key);
        if (strValue == null) return;

        LoggableSpType val = null;
        try {
            Method m = c.getMethod("valueOf", String.class, c);
            val = (LoggableSpType) m.invoke(null, strValue, null);
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, ex.getMessage(), ex);
        }
        if (val == null) return;

        map.put(key, val.logValue());
    }

    /**
     * Return the segment caption.
     *
     * @return The caption.
     */
    public String getSegmentCaption() {
        return SEGMENT_CAPTION;
    }
}
