package jsky.app.ot.tpe.nfiraos;

import edu.gemini.catalog.ui.tpe.CatalogImageDisplay;
import edu.gemini.pot.ModelConverters;
import edu.gemini.skycalc.Angle;
import edu.gemini.spModel.core.MagnitudeBand;
import edu.gemini.spModel.core.SiderealTarget;
import edu.gemini.spModel.nfiraos.NfiraosTipTiltMode;
import edu.gemini.ags.nfiraos.NfiraosCatalogSearchResults;
import edu.gemini.ags.nfiraos.NfiraosGuideStars;
import edu.gemini.spModel.obs.context.ObsContext;
import jsky.app.ot.tpe.NfiraosGuideStarWorker;
import jsky.app.ot.tpe.TpeImageWidget;
import jsky.app.ot.tpe.TpeManager;
import jsky.coords.WorldCoords;
import jsky.util.gui.DialogUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * OT-111: Controller for NfiraosGuideStarSearchDialog
 */
class NfiraosGuideStarSearchController {
    private final NfiraosGuideStarSearchModel _model;
    private final NfiraosGuideStarWorker _worker;
    private final NfiraosGuideStarSearchDialog _dialog;
    private final CatalogImageDisplay _tpe;

    /**
     * Constructor
     * @param model the overall GUI model
     * @param worker does the background tasks like query, analyze
     * @param dialog the main dialog
     * @param imageDisplay the Tpe reference
     */
    NfiraosGuideStarSearchController(final NfiraosGuideStarSearchModel model, final NfiraosGuideStarWorker worker,
                                  final NfiraosGuideStarSearchDialog dialog, final CatalogImageDisplay imageDisplay) {
        _model = model;
        _worker = worker;
        _dialog = dialog;
        _tpe = imageDisplay;
    }

    /**
     * Searches for guide star candidates and saves the results in the model
     */
    public void query(final scala.concurrent.ExecutionContext ec) throws Exception {
        final WorldCoords basePos = _tpe.getBasePos();
        final ObsContext obsContext = _worker.getObsContext(basePos.getRaDeg(), basePos.getDecDeg());
        final Set<edu.gemini.spModel.core.Angle> posAngles = getPosAngles(obsContext);

        final MagnitudeBand nirBand = _model.getBand().getBand();

        final NfiraosTipTiltMode tipTiltMode = _model.getAnalyseChoice().getNfiraosTipTiltMode();
        List<NfiraosCatalogSearchResults> results;
        try {
            results = _worker.search(_model.getCatalog(), tipTiltMode, obsContext, posAngles,
                    new scala.Some<>(nirBand), ec);
        } catch(Exception e) {
            DialogUtil.error(_dialog, e);
            results = new ArrayList<>();
            _dialog.setState(NfiraosGuideStarSearchDialog.State.PRE_QUERY);
        }

        if (_model.isReviewCandidatesBeforeSearch()) {
            _model.setNfiraosCatalogSearchResults(results);
        } else {
            _model.setNfiraosCatalogSearchResults(results);
            _model.setNfiraosGuideStars(_worker.findAllGuideStars(obsContext, posAngles, results));
        }
    }

    private Set<edu.gemini.spModel.core.Angle> getPosAngles(final ObsContext obsContext) {
        final Set<edu.gemini.spModel.core.Angle> posAngles = new HashSet<>();
        posAngles.add(obsContext.getPositionAngle());
        if (_model.isAllowPosAngleAdjustments()) {
            posAngles.add(ModelConverters.toNewAngle(new Angle(0., Angle.Unit.DEGREES)));
            posAngles.add(ModelConverters.toNewAngle(new Angle(90., Angle.Unit.DEGREES)));
            posAngles.add(ModelConverters.toNewAngle(new Angle(180., Angle.Unit.DEGREES)));
            posAngles.add(ModelConverters.toNewAngle(new Angle(270., Angle.Unit.DEGREES)));
        }
        return posAngles;
    }

    /**
     * Analyzes the search results and saves a list of possible guide star configurations to the model.
     * @param excludeCandidates list of SkyObjects to exclude
     */
    // Called from the TPE
    void analyze(final List<SiderealTarget> excludeCandidates) throws Exception {
        final TpeImageWidget tpe = TpeManager.create().getImageWidget();
        final WorldCoords basePos = tpe.getBasePos();
        final ObsContext obsContext = _worker.getObsContext(basePos.getRaDeg(), basePos.getDecDeg());
        final Set<edu.gemini.spModel.core.Angle> posAngles = getPosAngles(obsContext);
        _model.setNfiraosGuideStars(_worker.findAllGuideStars(obsContext, posAngles,
                filter(excludeCandidates, _model.getNfiraosCatalogSearchResults())));
    }

    // Returns a list of the given nfiraosCatalogSearchResults, with any SiderealTargets removed that are not
    // in the candidates list.
    private List<NfiraosCatalogSearchResults> filter(final List<SiderealTarget> excludeCandidates,
                                                     final List<NfiraosCatalogSearchResults> nfiraosCatalogSearchResults) {
        final List<NfiraosCatalogSearchResults> results = new ArrayList<>();
        for (NfiraosCatalogSearchResults in : nfiraosCatalogSearchResults) {
            List<SiderealTarget> siderealTargets =new ArrayList<>(in.results().size());
            siderealTargets.addAll(in.resultsAsJava());
            siderealTargets = removeAll(siderealTargets, excludeCandidates);
            if (!siderealTargets.isEmpty()) {
                final NfiraosCatalogSearchResults out = new NfiraosCatalogSearchResults(siderealTargets, in.criterion());
                results.add(out);
            }
        }
        return results;
    }

    // Removes all the objects in the targets list that are also in the excludeCandidates list by comparing names
    private List<SiderealTarget> removeAll(final List<SiderealTarget> targets, final List<SiderealTarget> excludeCandidates) {
        return targets.stream().filter(siderealTarget -> !contains(excludeCandidates, siderealTarget)).collect(Collectors.toList());
    }

    // Returns true if a SkyObject with the same name is in the list
    private boolean contains(final List<SiderealTarget> targets, final SiderealTarget target) {
        final String name = target.name();
        if (name != null) {
            for (SiderealTarget s : targets) {
                if (name.equals(s.name())) return true;
            }
        }
        return false;
    }

    /**
     * Adds the given asterisms as guide groups to the current observation's target list.
     * @param selectedAsterisms information used to create the guide groups
     * @param primaryIndex if more than one item in list, the index of the primary guide group
     */
    public void add(final List<NfiraosGuideStars> selectedAsterisms, final int primaryIndex) {
        _worker.applyResults(selectedAsterisms, primaryIndex);
    }
}
