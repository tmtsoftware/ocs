package edu.gemini.spModel.guide;

import edu.gemini.skycalc.Offset;
import edu.gemini.spModel.obs.context.ObsContext;
import edu.gemini.spModel.target.SPTarget;

/**
 *
 */
public interface GuideStarValidator {
    /**
     * Determines whether the given target is valid in the given context.
     * Drawing tools, for example, can use this information to indicate the
     * status of the guide star appropriately.
     *
     * @param guideStar guide star position to validate
     * @param ctx context in which the guide star is validated
     * @param offset offset from base pos to use (set to 0,0 if not needed)
     *
     * @return a {@link edu.gemini.spModel.guide.GuideStarValidation}
     */
    GuideStarValidation validate(SPTarget guideStar, ObsContext ctx, Offset offset);

    /**
     * Determines whether the given target is valid in the given context.
     * Drawing tools, for example, can use this information to indicate the
     * status of the guide star appropriately.
     *
     * @param guideStar guide star position to validate
     * @param ctx context in which the guide star is validated
     *
     * @return a {@link edu.gemini.spModel.guide.GuideStarValidation}
     */
    default GuideStarValidation validate(SPTarget guideStar, ObsContext ctx) {
        return validate(guideStar, ctx, Offset.ZERO_OFFSET);
    }
}
