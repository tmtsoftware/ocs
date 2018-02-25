package edu.gemini.p2checker.rules.nfiraos;

import edu.gemini.p2checker.api.IP2Problems;
import edu.gemini.p2checker.api.IRule;
import edu.gemini.p2checker.api.ObservationElements;
import edu.gemini.p2checker.api.P2Problems;
import edu.gemini.p2checker.util.PositionOffsetChecker;
import edu.gemini.pot.sp.ISPProgramNode;
import edu.gemini.pot.sp.SPComponentType;
import edu.gemini.shared.util.immutable.Option;
import edu.gemini.skycalc.Angle;
import edu.gemini.skycalc.Offset;
import edu.gemini.spModel.gemini.flamingos2.Flamingos2;
import edu.gemini.spModel.gemini.flamingos2.Flamingos2OiwfsGuideProbe;
import edu.gemini.spModel.gemini.nfiraos.NfiraosOiwfs;
import edu.gemini.spModel.gemini.iris.Iris;
import edu.gemini.spModel.gemini.iris.IrisOdgw;
import edu.gemini.spModel.gemini.obscomp.SPSiteQuality;
import edu.gemini.spModel.guide.GuideStarValidation;
import edu.gemini.spModel.guide.ValidatableGuideProbe;
import edu.gemini.spModel.obs.ObsClassService;
import edu.gemini.spModel.obs.context.ObsContext;
import edu.gemini.spModel.obsclass.ObsClass;
import edu.gemini.spModel.obscomp.SPInstObsComp;
import edu.gemini.spModel.target.SPTarget;
import edu.gemini.spModel.target.env.GuideGroup;
import edu.gemini.spModel.target.env.GuideProbeTargets;
import edu.gemini.spModel.target.env.TargetEnvironment;

import static edu.gemini.skycalc.Offset.ZERO_OFFSET;

/**
 * A rule for checking Nfiraos guide star positions.
 */
public final class NfiraosGuideStarRule implements IRule {
    private static final String PREFIX = "NfiraosGuideStarRule_";
    private static final String ODGW = "The ODGW%d guide star falls out of the range of the detector";
    private static final String OIWFS = "The OIWFS%d guide star falls out of the range of the guide probe";
    private static final String ConfigError = "Configuration not supported. Please select 3 OIWFS + 1 ODGW or 1 OIWFS + 3 ODGW";
    private static final String TipTilt = "Less than 3 Nfiraos guide stars of the same origin. Tip-tilt correction will not be optimal.";
    private static final String SlowFocus = "Missing Slow-focus Sensor star. Slow Focus correction will be not be applied.";
// TODO: REL-2941   private static final String Flexure = "Missing flexure guide star. Flexure will not be compensated.";
    private static final String NoIqAny = "Nfiraos cannot be used in IQAny conditions.";
    private static final String NoCloudy = "Nfiraos cannot be used in cloudy conditions.";


    private boolean validateObs(ObservationElements elements) {
        // We only care about checking IRIS observations, or F2 with Nfiraos.
        SPInstObsComp inst = elements.getInstrument();
        if (inst == null) return false; // nothing to check

        SPComponentType type = inst.getType();
        if (Iris.SP_TYPE.equals(type)) return true;

        if (!Flamingos2.SP_TYPE.equals(type)) return false;

        if (elements.getAOComponent().isEmpty()) return false;
        return elements.hasNfiraos();
    }

    public IP2Problems check(ObservationElements elements)  {
        if (!validateObs(elements)) return null; // does not apply

        Option<ObsContext> opt = elements.getObsContext();
        if (opt.isEmpty()) return null;  // nothing to check

        ObsContext ctx = opt.getValue();
        TargetEnvironment env = ctx.getTargets();
        // REL-2098 Some p2 checks are not verified for day calibrations
        final boolean isDayCal = ObsClassService.lookupObsClass(elements.getObservationNode()) == ObsClass.DAY_CAL;

        P2Problems problems = new P2Problems();
        for (ISPProgramNode targetNode : elements.getTargetObsComponentNode()) {

            // Check the Iris guide stars.
            if (Iris.SP_TYPE.equals(elements.getInstrument().getType())) {
                for (IrisOdgw odgw : IrisOdgw.values()) {
                    if (!validate(odgw, ctx)) {
                        addError(problems, PREFIX + "ODGW", ODGW, ctx, odgw.getIndex(), targetNode);
                    }
                }
            }

            // Check the Nfiraos guide positions.
            if (elements.hasNfiraos()) {
                for (final NfiraosOiwfs.Wfs canwfs : NfiraosOiwfs.Wfs.values()) {
                    if (!validate(canwfs, ctx)) {
                        addError(problems, PREFIX + "OIWFS", OIWFS, ctx, canwfs.getIndex(), targetNode);
                    }
                }

                // REL-1321:
                // ERROR if Nfiraos component in observation AND IQ=Any, "Nfiraos cannot be used in IQAny conditions."
                // ERROR if Nfiraos component in observation AND CC>50, "Nfiraos cannot be used in cloudy conditions."
                if (!elements.getSiteQuality().isEmpty() && !isDayCal) {
                    SPSiteQuality q = elements.getSiteQuality().getValue();
                    if (q.getImageQuality() == SPSiteQuality.ImageQuality.ANY) {
                        problems.addError(PREFIX + "IQ", NoIqAny, targetNode);
                    }
                    if (q.getCloudCover().getPercentage() > 50) {
                        problems.addError(PREFIX + "CC", NoCloudy, targetNode);
                    }
                }
            }

            GuideGroup primaryGuideGroup = env.getPrimaryGuideGroup();
            // get # oiwfs
            int oiwfs = 0;
            for (final NfiraosOiwfs.Wfs canwfs : NfiraosOiwfs.Wfs.values()) {
                Option<GuideProbeTargets> gpt = primaryGuideGroup.get(canwfs);
                if (!gpt.isEmpty() && !gpt.getValue().getPrimary().isEmpty()) {
                    oiwfs++;
                }
            }

            if (Iris.SP_TYPE.equals(elements.getInstrument().getType())) {
                // get # odgws
                int odgws = 0;
                for (IrisOdgw odgw : IrisOdgw.values()) {
                    Option<GuideProbeTargets> gpt = primaryGuideGroup.get(odgw);
                    if (!gpt.isEmpty() && !gpt.getValue().getPrimary().isEmpty()) {
                        odgws++;
                    }
                }
                //if (3 OIWFS and >1 ODGW) or (2 OIWFS and 2 ODGW) or (4 ODGW)
                if ((oiwfs == 3 && odgws > 1) || (odgws == 2 && oiwfs == 2) || (odgws == 4) || (odgws == 3 && oiwfs > 1)) {
                    problems.addError(PREFIX + "ConfigError", ConfigError, targetNode);
                }
                //When using less than 3 of either Nfiraos OIWFS or ODGW when 1 of the complementary type (OIWFS3 or ODGW/F2 OIWFS)
                if (((odgws <= 1 && oiwfs < 3) || (oiwfs <= 1 && odgws < 3)) && !isDayCal) {
                    problems.addWarning(PREFIX + "TipTilt", TipTilt, targetNode);
                }
                //No Nfiraos Slow-focus Sensor guide star (OIWFS) when using 2 or 3 ODGW.
                if ((odgws == 3 || odgws == 2) && oiwfs == 0) {
                    problems.addWarning(PREFIX + "SlowFocus", SlowFocus, targetNode);
                }

                /* TODO: REL-2941
                //No flexure guide star (IRIS ODGW or Flamingos II OIWFS) when using 2 or 3 OIWFS.
                if ((oiwfs == 3 || oiwfs == 2) && odgws == 0) {
                    problems.addWarning(PREFIX + "Flexure", Flexure, targetNode);
                }
                */
            }

            if (Flamingos2.SP_TYPE.equals(elements.getInstrument().getType())) {
                //get F2 OIWFS
                boolean f2oiwfs = false;
                Option<GuideProbeTargets> gpt = primaryGuideGroup.get(Flamingos2OiwfsGuideProbe.instance);
                if (!gpt.isEmpty() && !gpt.getValue().getPrimary().isEmpty()) {
                    f2oiwfs = true;
                }
                /* TODO: REL-2941
                //No flexure guide star (IRIS ODGW or Flamingos II OIWFS) when using 2 or 3 OIWFS.
                if (!f2oiwfs && (oiwfs == 3 || oiwfs == 2)) {
                    if (gpt.isEmpty() || gpt.getValue().getPrimary().isEmpty()) {
                        problems.addWarning(PREFIX + "Flexure", Flexure, targetNode);
                    }
                }
                */
                //When using less than 3 of either Nfiraos OIWFS or ODGW when 1 of the complementary type (OIWFS3 or ODGW/F2 OIWFS)
                if ((f2oiwfs && oiwfs < 3) && !isDayCal) {
                    problems.addWarning(PREFIX + "TipTilt", TipTilt, targetNode);
                }
            }

            // REL-778: Check the position offsets. Maximum distance from base that is allowed is 5 arcmin.
            if (PositionOffsetChecker.hasBadOffsets(elements)) {
                problems.addError(PREFIX + PositionOffsetChecker.PROBLEM_CODE(), PositionOffsetChecker.PROBLEM_MESSAGE(), elements.getSeqComponentNode());
            }
        }
        return problems;
    }

    private boolean validate(ValidatableGuideProbe guider, ObsContext ctx)  {
        // Find the primary target of this type, if any.
        TargetEnvironment env = ctx.getTargets();
        Option<GuideProbeTargets> gtOpt = env.getPrimaryGuideProbeTargets(guider);
        if (gtOpt.isEmpty()) return true; // okay, no targets to check

        GuideProbeTargets gt = gtOpt.getValue();
        Option<SPTarget> primaryOpt = gt.getPrimary();
        if (primaryOpt.isEmpty()) return true; // okay, no target to check

        SPTarget primary = primaryOpt.getValue();
        // XXX TODO FIXME: Should include offset pos?
        return guider.validate(primary, ctx, ZERO_OFFSET) == GuideStarValidation.VALID;
    }

    private void addError(P2Problems problems, String id, String tmpl, ObsContext ctx, int index, ISPProgramNode node) {
        String msg = String.format(tmpl, index);
        if (ctx.getSciencePositions().size() > 1) {
            msg += " at one or more offset positions";
        }
        problems.addError(id, msg, node);
    }
}
