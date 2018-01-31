//
// $
//

package edu.gemini.spModel.gemini.iris;

import edu.gemini.pot.sp.ISPObsComponent;
import edu.gemini.pot.sp.SPComponentType;
import edu.gemini.spModel.config.IConfigBuilder;
import edu.gemini.spModel.data.ISPDataObject;
import edu.gemini.spModel.gemini.gems.Gems;
import edu.gemini.spModel.gemini.inst.DefaultInstNodeInitializer;
import edu.gemini.spModel.obscomp.SPInstObsComp;

import java.util.Collection;
import java.util.Collections;

/**
 * Initializes {@link Iris} nodes.
 */
public final class IrisNI extends DefaultInstNodeInitializer {
    @Override public SPComponentType getType() { return Iris.SP_TYPE; }

    @Override protected IConfigBuilder createConfigBuilder(ISPObsComponent node) {
        return new IrisCB(node);
    }

    @Override public SPInstObsComp createDataObject() {
        return new Iris();
    }

    @Override public Collection<ISPDataObject> createFriends() {
        return Collections.<ISPDataObject>singletonList(new Gems());
    }
}
