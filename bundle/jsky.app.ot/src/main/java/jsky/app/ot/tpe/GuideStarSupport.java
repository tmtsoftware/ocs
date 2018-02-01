package jsky.app.ot.tpe;

import edu.gemini.ags.api.AgsRegistrar;
import edu.gemini.pot.sp.ISPNode;
import edu.gemini.spModel.gemini.gpi.Gpi;
import edu.gemini.spModel.obs.context.ObsContext;

/**
 * Support methods for the tpe guide star dialogs
 */
public class GuideStarSupport {
    public static boolean supportsAutoGuideStarSelection(ISPNode node) {
        return supportsAutoGuideStarSelection(TpeContext.apply(node));
    }

    static boolean supportsAutoGuideStarSelection(TpeContext ctx) {
        if (ctx.isEmpty() || ctx.instrument().isEmpty()) return false;

        //Special handling for NFIRAOS
        if (hasNfiraosComponent(ctx)) return true;

        if (!ctx.instrument().isDefined()) return false;

        ObsContext obsCtx = ctx.obsContextJava().getOrNull();
        return obsCtx != null && AgsRegistrar.defaultStrategy(obsCtx).isDefined();
    }

    // Returns true if the instrument supports manual guide star selection
    public static boolean supportsManualGuideStarSelection(ISPNode node) {
        return supportsManualGuideStarSelection(TpeContext.apply(node));
    }

    static boolean supportsManualGuideStarSelection(TpeContext ctx) {
        return !ctx.instrument().is(Gpi.SP_TYPE);
    }

    // Returns true if the observation has a nfiraos component
    public static boolean hasNfiraosComponent(ISPNode node) {
        return hasNfiraosComponent(TpeContext.apply(node));
    }

    static boolean hasNfiraosComponent(TpeContext ctx) {
        return ctx.nfiraos().isDefined();
    }

}
