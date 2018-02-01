package jsky.app.ot.tpe.nfiraos;

import edu.gemini.ags.nfiraos.NfiraosGuideStarSearchOptions.*;
import edu.gemini.ags.nfiraos.NfiraosCatalogSearchResults;
import edu.gemini.ags.nfiraos.NfiraosGuideStars;
import edu.gemini.shared.util.immutable.ImOption;
import edu.gemini.shared.util.immutable.Option;
import edu.gemini.spModel.core.SiderealTarget;

import java.util.List;

/**
 * OT-111: model for NfiraosGuideStarSearchDialog
 */
class NfiraosGuideStarSearchModel {

    private CatalogChoice _catalog;
    private NirBandChoice _band;
    private AnalyseChoice _analyseChoice;
    private boolean _reviewCandidatesBeforeSearch;
    private boolean _allowPosAngleAdjustments;
    private List<NfiraosCatalogSearchResults> _nfiraosCatalogSearchResults;
    private List<NfiraosGuideStars> _nfiraosGuideStars;

    public CatalogChoice getCatalog() {
        return _catalog;
    }

    public void setCatalog(final CatalogChoice catalog) {
        _catalog = catalog;
    }

    public NirBandChoice getBand() {
        return _band;
    }

    public void setBand(final NirBandChoice band) {
        _band = band;
    }

    AnalyseChoice getAnalyseChoice() {
        return _analyseChoice;
    }

    void setAnalyseChoice(final AnalyseChoice analyseChoice) {
        _analyseChoice = analyseChoice;
    }

    void setReviewCandidatesBeforeSearch(final boolean reviewCanditatesBeforeSearch) {
        _reviewCandidatesBeforeSearch = reviewCanditatesBeforeSearch;
    }

    boolean isReviewCandidatesBeforeSearch() {
        return _reviewCandidatesBeforeSearch;
    }

    void setAllowPosAngleAdjustments(final boolean allowPosAngleAdjustments) {
        _allowPosAngleAdjustments = allowPosAngleAdjustments;
    }

    boolean isAllowPosAngleAdjustments() {
        return _allowPosAngleAdjustments;
    }

    List<NfiraosCatalogSearchResults> getNfiraosCatalogSearchResults() {
        return _nfiraosCatalogSearchResults;
    }

    void setNfiraosCatalogSearchResults(final List<NfiraosCatalogSearchResults> nfiraosCatalogSearchResults) {
        _nfiraosCatalogSearchResults = nfiraosCatalogSearchResults;
    }

    List<NfiraosGuideStars> getNfiraosGuideStars() {
        return _nfiraosGuideStars;
    }

    void setNfiraosGuideStars(final List<NfiraosGuideStars> nfiraosGuideStars) {
        _nfiraosGuideStars = nfiraosGuideStars;
    }

    Option<SiderealTarget> targetAt(final int i) {
        return ImOption.apply(_nfiraosCatalogSearchResults.get(0)).flatMap(c -> c.targetAt(i));
    }
}
