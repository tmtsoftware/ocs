//$Id$
package edu.gemini.p2checker.rules.flamingos2;

import edu.gemini.p2checker.api.*;
import edu.gemini.p2checker.rules.nfiraos.NfiraosGuideStarRule;
import edu.gemini.p2checker.util.AbstractConfigRule;
import edu.gemini.p2checker.util.MdfConfigRule;
import edu.gemini.p2checker.util.NoPOffsetWithSlitRule;
import edu.gemini.p2checker.util.SequenceRule;
import edu.gemini.spModel.config2.Config;
import edu.gemini.spModel.gemini.flamingos2.Flamingos2;
import edu.gemini.spModel.obscomp.SPInstObsComp;
import scala.Option;
import scala.runtime.AbstractFunction2;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Flamingos 2 rule.
 */
public final class Flamingos2Rule implements IRule {
    private static final Collection<IConfigRule> FLAM2_RULES = new ArrayList<IConfigRule>();
    private static final String PREFIX = "Flamingos2Rule_";

    private static IConfigRule EXPOSURE_TIME_RULE = new IConfigRule() {

        private static final String MIN_MESSAGE =
                "Exposure time (%.1f) below minimum for read mode (%.1f).";

        private static final String MILLISEC_MESSAGE =
                "Millisecond percision exposure times are only allowed for sub " +
                      Flamingos2.FRACTIONAL_EXP_TIME_MAX + " second exposures.";

        public Problem check(Config config, int step, ObservationElements elems, Object state) {
            Double expTime = SequenceRule.getExposureTime(config);
            if (expTime == null) return null;

            Flamingos2.ReadMode readMode;
            readMode = (Flamingos2.ReadMode) SequenceRule.getInstrumentItem(config, Flamingos2.READMODE_PROP);

            if (readMode != null) {
                double min = readMode.minimumExpTimeSec();
                if (expTime < min) {
                    String msg = String.format(MIN_MESSAGE, expTime, min);
                    return new Problem(ERROR, PREFIX+"EXPOSURE_TIME_RULE_MIN_MESSAGE", msg,
                            SequenceRule.getInstrumentOrSequenceNode(step, elems, config));
                }
            }

            if ((expTime > Flamingos2.FRACTIONAL_EXP_TIME_MAX) && (expTime != Math.floor(expTime))) {
                return new Problem(ERROR, PREFIX+"EXPOSURE_TIME_RULE_MILLISEC_MESSAGE", MILLISEC_MESSAGE,
                        SequenceRule.getInstrumentOrSequenceNode(step, elems, config));
            }

            return null;
        }

        public IConfigMatcher getMatcher() {
            // REL-1853: Previously, we only performed this check for science observations
            // (SequenceRule.SCIENCE_MATCHER). We now perform this check for all obs classes.
            return IConfigMatcher.ALWAYS;
        }
    };

    private static class MdfMaskNameRule extends AbstractConfigRule {
        private final Problem.Type problemType;

        public MdfMaskNameRule(Problem.Type type) {
            this.problemType = type;
        }

        @Override
        public Problem check(Config config, int step, ObservationElements elems, Object state) {
            SPInstObsComp spInstObsComp = elems.getInstrument();
            if (spInstObsComp.getType() == Flamingos2.SP_TYPE) {
                Option<Problem> problemOption = MdfConfigRule.checkMaskName(Flamingos2.FPUnit.CUSTOM_MASK, config, step, elems, state);
                if (problemOption.isDefined() && problemOption.get().getType() == problemType)
                    return problemOption.get();
                else
                    return null;
            }
            return null;
        }
    }

    /**
     * REL-1811: Warn if there are P-offsets for a slit spectroscopy observation.
     * Warn for FPU = (*arcsec or Custom Mask)
     */
    private static IConfigRule NO_P_OFFSETS_WITH_SLIT_SPECTROSCOPY_RULE = new NoPOffsetWithSlitRule(
        PREFIX,
        new AbstractFunction2<Config, ObservationElements, Boolean>() {
            public Boolean apply(Config config, ObservationElements elems) {
                final Flamingos2.FPUnit fpu = (Flamingos2.FPUnit) SequenceRule.getInstrumentItem(config, Flamingos2.FPU_PROP);
                return fpu.isLongslit() || fpu == Flamingos2.FPUnit.CUSTOM_MASK;
            }
        }
    );

    /**
     * Register all the Flamingos 2 rules to apply.
     */
    static {
        FLAM2_RULES.add(EXPOSURE_TIME_RULE);
        FLAM2_RULES.add(NO_P_OFFSETS_WITH_SLIT_SPECTROSCOPY_RULE);
        FLAM2_RULES.add(new MdfMaskNameRule(Problem.Type.ERROR));
        FLAM2_RULES.add(new MdfMaskNameRule(Problem.Type.WARNING));
    }


    public IP2Problems check(ObservationElements elements)  {
        return (new CompositeRule(
            new IRule[] {
                new NfiraosGuideStarRule(),
                new SequenceRule(FLAM2_RULES, null),
            },
            CompositeRule.Type.all
        )).check(elements);
    }
}
