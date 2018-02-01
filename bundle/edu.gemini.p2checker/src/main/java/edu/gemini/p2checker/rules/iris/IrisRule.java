package edu.gemini.p2checker.rules.iris;

import edu.gemini.p2checker.api.*;
import edu.gemini.p2checker.util.SequenceRule;
import edu.gemini.p2checker.rules.nfiraos.NfiraosGuideStarRule;
import edu.gemini.spModel.config2.Config;
import edu.gemini.spModel.gemini.iris.Iris;


import java.util.ArrayList;
import java.util.Collection;

/**
 *  IRIS Rule set
 */
public class IrisRule implements IRule {
    private static final String PREFIX = "IrisRule_";
    private static final Collection<IConfigRule> IRIS_RULES = new ArrayList<>();

    private static IConfigRule SHORT_EXPOSURE_TIME_RULE = new IConfigRule() {

        private static final String MESSAGE =
                "Exposure time (%.1f sec) is shorter than the minimum (%.1f sec) for read mode '%s'";

        public Problem check(Config config, int step, ObservationElements elems, Object state) {
            Double expTime = SequenceRule.getExposureTime(config);
            if (expTime == null) return null;

            Iris.ReadMode readMode = (Iris.ReadMode)SequenceRule.getInstrumentItem(config, Iris.READ_MODE_PROP);
            if (readMode == null) return null;

            Double minTime = Iris.getMinimumExposureTimeSecs(readMode);

            if (expTime < minTime) {
                String msg = String.format(MESSAGE, expTime, minTime, readMode.displayValue());
                return new Problem(ERROR, PREFIX + "SHORT_EXPOSURE_TIME_RULE", msg, SequenceRule.getInstrumentOrSequenceNode(step, elems, config));
            }

            return null;
        }

        public IConfigMatcher getMatcher() {
            return SequenceRule.SCIENCE_MATCHER;
        }
    };

    private static IConfigRule LONG_EXPOSURE_TIME_RULE = new IConfigRule() {

        private static final String MESSAGE =
                "Exposure time (%.1f sec) may result in detector wells more than 50%% full (max for %s is %.0f sec)";

        public Problem check(Config config, int step, ObservationElements elems, Object state) {
            Double expTime = SequenceRule.getExposureTime(config);
            if (expTime == null) return null;

            Iris.Filter filter = (Iris.Filter) SequenceRule.getInstrumentItem(config, Iris.FILTER_PROP);
            if (filter == null) return null;

            double maxTime = filter.exposureTimeHalfWellSecs();
            if (maxTime <= 0) return null;

            if (expTime > maxTime) {
                String msg = String.format(MESSAGE, expTime, filter.logValue(), maxTime);
                return new Problem(WARNING, PREFIX + "LONG_EXPOSURE_TIME_RULE", msg, SequenceRule.getInstrumentOrSequenceNode(step, elems, config));
            }
            return null;
        }

        public IConfigMatcher getMatcher() {
            return SequenceRule.SCIENCE_MATCHER;
        }
    };

    /*
     * Register all the IRIS rules to apply
     */
    static {
        IRIS_RULES.add(SHORT_EXPOSURE_TIME_RULE);
        IRIS_RULES.add(LONG_EXPOSURE_TIME_RULE);
    }

    public IP2Problems check(ObservationElements elems)  {
        return (new CompositeRule(
            new IRule[] {
                new NfiraosGuideStarRule(),
                new SequenceRule(IRIS_RULES, null),
            },
            CompositeRule.Type.all
        )).check(elems);
    }
}
