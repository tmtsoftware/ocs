//
// $
//

package edu.gemini.spModel.gemini.iris;

import edu.gemini.pot.sp.ISPFactory;
import edu.gemini.pot.sp.ISPNodeInitializer;
import edu.gemini.pot.sp.ISPNode;
import edu.gemini.pot.sp.ISPSeqComponent;
import edu.gemini.spModel.config.IConfigBuilder;



/**
 * Initializes IrisSeqConfig nodes.
 */
public final class IrisSeqConfigNI implements ISPNodeInitializer {
    public void initNode(ISPFactory factory, ISPNode node)  {
        ISPSeqComponent castNode = (ISPSeqComponent) node;
        if (!castNode.getType().equals(IrisSeqConfig.SP_TYPE)) throw new InternalError();

        castNode.setDataObject(new IrisSeqConfig());
        updateNode(node);

    }

    public void updateNode(ISPNode node)  {
        node.putClientData(IConfigBuilder.USER_OBJ_KEY,
                           new IrisSeqConfigCB((ISPSeqComponent) node));
    }
}
