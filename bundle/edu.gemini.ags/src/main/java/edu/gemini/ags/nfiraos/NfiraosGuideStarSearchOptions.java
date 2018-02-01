package edu.gemini.ags.nfiraos;

import edu.gemini.catalog.api.CatalogName;
import edu.gemini.catalog.api.PPMXL$;
import edu.gemini.catalog.api.UCAC4$;
import edu.gemini.catalog.api.MagnitudeConstraints;
import edu.gemini.spModel.core.Angle;
import edu.gemini.spModel.core.MagnitudeBand;
import edu.gemini.spModel.gemini.nfiraos.Canopus;
import edu.gemini.spModel.gemini.nfiraos.NfiraosInstrument;
import edu.gemini.spModel.nfiraos.NfiraosGuideProbeGroup;
import edu.gemini.spModel.nfiraos.NfiraosGuideStarType;
import edu.gemini.spModel.nfiraos.NfiraosTipTiltMode;
import edu.gemini.spModel.obs.context.ObsContext;

import java.util.*;

/**
 * An immutable class specifying the Nfiraos guide star search options.
 * An instance of this class will be created by the UI or other client
 * and used to control the search process.
 *
 * See OT-25
 */
public class NfiraosGuideStarSearchOptions {

    public enum CatalogChoice {
        PPMXL_GEMINI("PPMXL@Gemini", "PPMXL at Gemini"),
        UCAC4_GEMINI("UCAC4", "UCAC4 at Gemini"),
        ;

        public static CatalogChoice DEFAULT = UCAC4_GEMINI;

        private String _displayValue;
        private String _catalogName;

        CatalogChoice(String catalogName, String displayValue) {
            _displayValue = displayValue;
            _catalogName = catalogName;
        }

        public String displayValue() {
            return _displayValue;
        }

        public CatalogName catalog() {
            if (this == PPMXL_GEMINI) {
                return PPMXL$.MODULE$;
            } else {
                return UCAC4$.MODULE$;
            }
        }

        public String catalogName() {
            return _catalogName;
        }

        public String toString() {
            return displayValue();
        }
    }


    public enum NirBandChoice {
        J(MagnitudeBand.J$.MODULE$),
        H(MagnitudeBand.H$.MODULE$),
        K(MagnitudeBand.K$.MODULE$),
        ;

        public static NirBandChoice DEFAULT = H;

        private MagnitudeBand _band;

        NirBandChoice(MagnitudeBand band) {
            _band = band;
        }

        public MagnitudeBand getBand() {
            return _band;
        }

        public String displayValue() {
            return _band.name();
        }

        public String toString() {
            return displayValue();
        }
    }


    public enum AnalyseChoice {
        BOTH("Canopus and IRIS", NfiraosTipTiltMode.both),
        CANOPUS("Canopus", NfiraosTipTiltMode.canopus),
        IRIS("IRIS", NfiraosTipTiltMode.instrument),
        ;

        public static AnalyseChoice DEFAULT = CANOPUS; // REL-604

        private String _displayValue;
        private NfiraosTipTiltMode _nfiraosTipTiltMode;

        AnalyseChoice(String name, NfiraosTipTiltMode nfiraosTipTiltMode) {
            _displayValue = name;
            _nfiraosTipTiltMode = nfiraosTipTiltMode;
        }

        public String displayValue() {
            return _displayValue;
        }

        public String toString() {
            return displayValue();
        }

        public NfiraosTipTiltMode getNfiraosTipTiltMode() {
            return _nfiraosTipTiltMode;
        }
    }

    private final NfiraosInstrument instrument;
    private final NfiraosTipTiltMode tipTiltMode;
    private final Set<Angle> posAngles;
    public static final CatalogChoice DEFAULT = CatalogChoice.DEFAULT;

    public NfiraosGuideStarSearchOptions(final NfiraosInstrument instrument,
                                         final NfiraosTipTiltMode tipTiltMode, final Set<Angle> posAngles) {
        this.instrument = instrument;
        if (instrument == NfiraosInstrument.flamingos2) {
            // Flamingos 2 OIWFS can only ever be used for the flexure star.
            this.tipTiltMode = NfiraosTipTiltMode.canopus;
        } else {
            this.tipTiltMode = tipTiltMode;
        }
        this.posAngles = posAngles;
    }

    public NfiraosInstrument getInstrument() {
        return instrument;
    }

    /**
     * @param nirBand      optional NIR magnitude band (default is H)
     * @return all relevant CatalogSearchCriterion instances
     */
    public List<NfiraosCatalogSearchCriterion> searchCriteria(final ObsContext obsContext, final scala.Option<MagnitudeBand> nirBand) {
        switch(tipTiltMode) {
            case canopus: return Arrays.asList(
                    canopusCriterion(obsContext, NfiraosGuideStarType.tiptilt),
                    instrumentCriterion(obsContext, NfiraosGuideStarType.flexure, nirBand));
            case instrument: return Arrays.asList(
                    instrumentCriterion(obsContext, NfiraosGuideStarType.tiptilt, nirBand),
                    canopusCriterion(obsContext, NfiraosGuideStarType.flexure));
            default:
            case both: return Arrays.asList(
                    canopusCriterion(obsContext, NfiraosGuideStarType.tiptilt),
                    instrumentCriterion(obsContext, NfiraosGuideStarType.flexure, nirBand),
                    instrumentCriterion(obsContext, NfiraosGuideStarType.tiptilt, nirBand),
                    canopusCriterion(obsContext, NfiraosGuideStarType.flexure)
            );
        }
    }

    private NfiraosCatalogSearchCriterion canopusCriterion(final ObsContext obsContext, final NfiraosGuideStarType ggst) {
        final NfiraosMagnitudeTable.LimitsCalculator calculator = NfiraosMagnitudeTable.CanopusWfsMagnitudeLimitsCalculator();
        // Ugly hack for
        return searchCriterion(obsContext, Canopus.Wfs.Group.instance, calculator, ggst, scala.Option.<MagnitudeBand>empty());
    }

    private NfiraosCatalogSearchCriterion instrumentCriterion(final ObsContext obsContext, final NfiraosGuideStarType ggst, final scala.Option<MagnitudeBand> nirBand) {
        final NfiraosMagnitudeTable.LimitsCalculator calculator = NfiraosMagnitudeTable.NfiraosInstrumentToMagnitudeLimitsCalculator().apply(instrument);
        return searchCriterion(obsContext, instrument.getGuiders(), calculator, ggst, nirBand);
    }

    private NfiraosCatalogSearchCriterion searchCriterion(final ObsContext obsContext,
                                                          final NfiraosGuideProbeGroup gGroup,
                                                          final NfiraosMagnitudeTable.LimitsCalculator calculator,
                                                          final NfiraosGuideStarType gType,
                                                          final scala.Option<MagnitudeBand> nirBand) {
        final String name = String.format("%s %s", gGroup.getDisplayName(), gType.name());

        // Adjust the mag limits for the worst conditions (as is done in the ags servlet)
        final MagnitudeConstraints magConstraints = calculator.adjustNfiraosMagnitudeConstraintForJava(gType, nirBand, obsContext.getConditions());

        final CatalogSearchCriterion criterion = calculator.searchCriterionBuilder(name, gGroup.getRadiusLimits(), instrument, magConstraints, posAngles);
        final NfiraosCatalogSearchKey key = new NfiraosCatalogSearchKey(gType, gGroup);
        return new NfiraosCatalogSearchCriterion(key, criterion);
    }

}