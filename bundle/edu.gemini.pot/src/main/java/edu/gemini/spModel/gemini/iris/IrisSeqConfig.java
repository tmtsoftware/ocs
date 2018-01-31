//
// $
//

package edu.gemini.spModel.gemini.iris;

import edu.gemini.pot.sp.SPComponentType;
import edu.gemini.spModel.data.property.PropertyFilter;
import edu.gemini.spModel.data.property.PropertyProvider;
import edu.gemini.spModel.data.property.PropertySupport;
import edu.gemini.spModel.seqcomp.SeqConfigNames;
import edu.gemini.spModel.seqcomp.SeqConfigObsBase;

import java.beans.PropertyDescriptor;
import java.util.Collections;
import java.util.Map;

/**
 * The IRIS configuration builder.
 */
public final class IrisSeqConfig extends SeqConfigObsBase implements PropertyProvider {

    public static final SPComponentType SP_TYPE =
            SPComponentType.ITERATOR_IRIS;

    public static final String SYSTEM_NAME = SeqConfigNames.INSTRUMENT_CONFIG_NAME;
    public static final String INSTRUMENT_NAME = "IRIS";

    public static final Map<String, PropertyDescriptor> PROPERTY_MAP =
            Collections.unmodifiableMap(
                PropertySupport.filter(PropertyFilter.ITERABLE_FILTER, Iris.PROPERTY_MAP)
            );

    public IrisSeqConfig() {
        super(SP_TYPE, SYSTEM_NAME);
    }

    public Map<String, PropertyDescriptor> getProperties() {
        return PROPERTY_MAP;
    }
}
