//
// $
//

package edu.gemini.spModel.gemini.nfiraos;

import edu.gemini.pot.sp.ISPFactory;
import edu.gemini.pot.sp.ISPNode;
import edu.gemini.pot.sp.ISPNodeInitializer;
import edu.gemini.pot.sp.ISPObsComponent;
import edu.gemini.spModel.config.IConfigBuilder;


import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Initializes {@link edu.gemini.pot.sp.ISPObsComponent} node of type
 * {@link Nfiraos}.
 */
public final class NfiraosNI implements ISPNodeInitializer {
    private static final Logger LOG = Logger.getLogger(NfiraosNI.class.getName());

    public void initNode(ISPFactory factory, ISPNode node)  {
        ISPObsComponent castNode = (ISPObsComponent) node;

        if (!castNode.getType().equals(Nfiraos.SP_TYPE)) {
            throw new InternalError();
        }

        try {
            node.setDataObject(new Nfiraos());
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Could not set the Nfiraos data object", ex);
            throw new InternalError();
        }

        updateNode(node);
    }

    public void updateNode(ISPNode node)  {
        node.putClientData(IConfigBuilder.USER_OBJ_KEY, new NfiraosCB((ISPObsComponent) node));
    }
}
