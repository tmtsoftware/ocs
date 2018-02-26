package edu.gemini.spModel.gemini.iris;

import edu.gemini.pot.sp.ISPObservation;
import edu.gemini.pot.sp.SPComponentType;
import edu.gemini.shared.util.immutable.*;
import edu.gemini.skycalc.Angle;
import edu.gemini.spModel.config.injector.ConfigInjector;
import edu.gemini.spModel.config.injector.obswavelength.ObsWavelengthCalc1;
import edu.gemini.spModel.config2.Config;
import edu.gemini.spModel.config2.ItemKey;
import edu.gemini.spModel.core.BandsList;
import edu.gemini.spModel.core.MagnitudeBand;
import edu.gemini.spModel.core.SingleBand;
import edu.gemini.spModel.core.Site;
import edu.gemini.spModel.data.ISPDataObject;
import edu.gemini.spModel.data.config.DefaultParameter;
import edu.gemini.spModel.data.config.DefaultSysConfig;
import edu.gemini.spModel.data.config.ISysConfig;
import edu.gemini.spModel.data.config.StringParameter;
import edu.gemini.spModel.data.property.PropertyProvider;
import edu.gemini.spModel.data.property.PropertySupport;
import edu.gemini.spModel.gemini.nfiraos.NfiraosOiwfs;
import edu.gemini.spModel.guide.GuideOption;
import edu.gemini.spModel.guide.GuideProbe;
import edu.gemini.spModel.guide.GuideProbeProvider;
import edu.gemini.spModel.guide.GuideProbeUtil;
import edu.gemini.spModel.obs.plannedtime.CommonStepCalculator;
import edu.gemini.spModel.obs.plannedtime.ExposureCalculator;
import edu.gemini.spModel.obs.plannedtime.OffsetOverheadCalculator;
import edu.gemini.spModel.obs.plannedtime.PlannedTime;
import edu.gemini.spModel.obs.plannedtime.PlannedTime.CategorizedTime;
import edu.gemini.spModel.obs.plannedtime.PlannedTime.CategorizedTimeGroup;
import edu.gemini.spModel.obs.plannedtime.PlannedTime.Category;
import edu.gemini.spModel.obs.plannedtime.PlannedTime.StepCalculator;
import edu.gemini.spModel.obscomp.InstConfigInfo;
import edu.gemini.spModel.obscomp.SPInstObsComp;
import edu.gemini.spModel.pio.ParamSet;
import edu.gemini.spModel.pio.Pio;
import edu.gemini.spModel.pio.PioFactory;
import edu.gemini.spModel.seqcomp.SeqConfigNames;
import edu.gemini.spModel.target.offset.OffsetPosBase;
import edu.gemini.spModel.telescope.IssPort;
import edu.gemini.spModel.telescope.IssPortProvider;
import edu.gemini.spModel.telescope.PosAngleConstraint;
import edu.gemini.spModel.telescope.PosAngleConstraintAware;
import edu.gemini.spModel.type.DisplayableSpType;
import edu.gemini.spModel.type.LoggableSpType;
import edu.gemini.spModel.type.SequenceableSpType;
import edu.gemini.spModel.type.SpTypeUtil;

import java.beans.PropertyDescriptor;
import java.util.*;

import static edu.gemini.spModel.seqcomp.SeqConfigNames.INSTRUMENT_KEY;

/**
 * This class defines the IRIS instrument.
 */
public final class Iris extends SPInstObsComp
    implements PropertyProvider, GuideProbeProvider, IssPortProvider, StepCalculator, PosAngleConstraintAware {

  public enum ReadMode implements DisplayableSpType, SequenceableSpType, LoggableSpType {
    // Updated for REL-439
    BRIGHT("Bright Objects", "Bright", 2, 28, 5.3, 10),
    FAINT("Faint Objects / Broad-band Imaging", "Faint", 8, 13, 21.5, 26),
    VERY_FAINT("Very Faint Objects / Narrow-band Imaging", "V. Faint", 16, 10, 42.5, 48),;

    public static final ReadMode DEFAULT = ReadMode.BRIGHT;
    public static final ItemKey KEY = new ItemKey(INSTRUMENT_KEY, "readMode");

    private final String displayValue;
    private final String logValue;
    private final int ndr;
    private final int readNoise;
    private final double minExposureTime; // seconds
    private final int overhead; // seconds

    ReadMode(String displayValue, String logValue, int ndr, int readNoise, double minExposureTime,
             int overhead) {
      this.displayValue = displayValue;
      this.logValue = logValue;
      this.ndr = ndr;
      this.readNoise = readNoise;
      this.minExposureTime = minExposureTime;
      this.overhead = overhead;
    }

    public String displayValue() {
      return displayValue;
    }

    public String logValue() {
      return logValue;
    }

    public String sequenceValue() {
      return name();
    }

    public int ndr() {
      return ndr;
    }

    public int readNoise() {
      return readNoise;
    }

    public double minExposureTimeSecs() {
      return minExposureTime;
    }

    public int overhead() {
      return overhead;
    }

    public String toString() {
      return displayValue;
    }

    /**
     * Returns the read mode matching the given name by searching through
     * the known types.  If not found, nvalue is returned.
     */
    public static ReadMode valueOf(String name, ReadMode nvalue) {
      return SpTypeUtil.oldValueOf(ReadMode.class, name, nvalue);
    }

  }

  // REL-445: Updated using the new 50/50 times below
  public enum Filter implements DisplayableSpType, SequenceableSpType, LoggableSpType {
    Zbb("Zbb (0.928 um)", "Zbb", 0.928, ReadMode.FAINT, new SingleBand((MagnitudeBand.J$.MODULE$))),
    Ybb("Ybb (1.092 um)", "Ybb", 1.092, ReadMode.FAINT),
    Jbb("Jbb (1.27 um)", "Jbb", 1.27, ReadMode.FAINT, new SingleBand((MagnitudeBand.J$.MODULE$))),
    Hbb("Hbb (1.629 um)", "Hbb", 1.629, ReadMode.FAINT, new SingleBand((MagnitudeBand.H$.MODULE$))),

    Z("Z (0.876 um)", "Z", 0.876, ReadMode.FAINT, new SingleBand((MagnitudeBand.J$.MODULE$))),
    Y("Y (1.019 um)", "Y", 1.019, ReadMode.FAINT),
    J("J (1.245 um)", "J", 1.245, ReadMode.FAINT, new SingleBand((MagnitudeBand.J$.MODULE$))),
    H("H (1.626 um)", "H", 1.626, ReadMode.BRIGHT, new SingleBand((MagnitudeBand.H$.MODULE$))),
    Ks("Ks (2.139 um)", "Ks", 2.139, ReadMode.BRIGHT, new SingleBand((MagnitudeBand.K$.MODULE$))),
    K("K (2.191 um)", "K", 2.191, ReadMode.BRIGHT, new SingleBand((MagnitudeBand.K$.MODULE$))),
//        HKnotch("H+K notch (Two bandpasses)", "H+K notch", ???), // XXX TODO FIXME

    ZN1("ZN1 (0.8613 um)", "ZN1", 0.8613, ReadMode.FAINT, new SingleBand((MagnitudeBand.J$.MODULE$))),
    ZN2("ZN2 (0.8963 um)", "ZN2", 0.8963, ReadMode.FAINT, new SingleBand((MagnitudeBand.J$.MODULE$))),
    ZN3("ZN3 (0.9328 um)", "ZN3", 0.9328, ReadMode.FAINT, new SingleBand((MagnitudeBand.J$.MODULE$))),
    ZN4("ZN4 (0.9708 um)", "ZN4", 0.9708, ReadMode.FAINT, new SingleBand((MagnitudeBand.J$.MODULE$))),

    YN1("YN1 (1.0104 um)", "YN1", 1.0104, ReadMode.FAINT, new SingleBand((MagnitudeBand.Y$.MODULE$))),
    YN2("YN2 (1.0515 um)", "YN2", 1.0515, ReadMode.FAINT, new SingleBand((MagnitudeBand.Y$.MODULE$))),
    YN3("YN3 (1.0943 um)", "YN3", 1.0943, ReadMode.FAINT, new SingleBand((MagnitudeBand.Y$.MODULE$))),
    YN4("YN4 (1.1389 um)", "YN4", 1.1389, ReadMode.FAINT, new SingleBand((MagnitudeBand.Y$.MODULE$))),

    JN1("JN1 (1.1853 um)", "JN1", 1.1853, ReadMode.FAINT, new SingleBand((MagnitudeBand.J$.MODULE$))),
    JN2("JN2 (1.2336 um)", "JN2", 1.2336, ReadMode.FAINT, new SingleBand((MagnitudeBand.J$.MODULE$))),
    JN3("JN3 (1.2838 um)", "JN3", 1.2838, ReadMode.FAINT, new SingleBand((MagnitudeBand.J$.MODULE$))),
    JN4("JN4 (1.3361 um)", "JN4", 1.3361, ReadMode.FAINT, new SingleBand((MagnitudeBand.J$.MODULE$))),

    HN1("HN1 (1.5113 um)", "HN1", 1.5113, ReadMode.BRIGHT, new SingleBand((MagnitudeBand.H$.MODULE$))),
    HN2("HN2 (1.5728 um)", "HN2", 1.5728, ReadMode.BRIGHT, new SingleBand((MagnitudeBand.H$.MODULE$))),
    HN3("HN3 (1.6369 um)", "HN3", 1.6369, ReadMode.BRIGHT, new SingleBand((MagnitudeBand.H$.MODULE$))),
    HN4("HN4 (1.7036 um)", "HN4", 1.7036, ReadMode.BRIGHT, new SingleBand((MagnitudeBand.H$.MODULE$))),
    HN5("HN5 (1.773 um)", "HN5", 1.773, ReadMode.BRIGHT, new SingleBand((MagnitudeBand.H$.MODULE$))),

    KN1("KN1 (2.0096 um)", "KN1", 2.0096, ReadMode.FAINT, new SingleBand((MagnitudeBand.K$.MODULE$))),
    KN2("KN2 (2.0914 um)", "KN2", 2.0914, ReadMode.FAINT, new SingleBand((MagnitudeBand.K$.MODULE$))),
    KN3("KN3 (2.1766 um)", "KN3", 2.1766, ReadMode.FAINT, new SingleBand((MagnitudeBand.K$.MODULE$))),
    KN4("KN4 (2.2653 um)", "KN4", 2.2653, ReadMode.FAINT, new SingleBand((MagnitudeBand.K$.MODULE$))),
    KN4_5("KN4.5 (2.33 um)", "KN4.5", 2.33, ReadMode.FAINT, new SingleBand((MagnitudeBand.K$.MODULE$))),
    KN5("KN5 (2.3575 um)", "KN5", 2.3575, ReadMode.FAINT, new SingleBand((MagnitudeBand.K$.MODULE$))),

    CaII_Trip("CaII Trip (0.850,0.854,0.866 um)", "CaII Trip", 0.854, ReadMode.FAINT), // XXX TODO FIXME
    HeI("HeI (1.0830 um)", "HeI", 1.0830, ReadMode.FAINT, new SingleBand((MagnitudeBand.J$.MODULE$))),
    Pa_Beta("Pa-Beta (1.2818 um)", "Pa-Beta", 1.2818, ReadMode.FAINT),
    FeII("FeII (1.6455 um)", "FeII", 1.6455, ReadMode.FAINT, new SingleBand((MagnitudeBand.H$.MODULE$))),
    Br_Gamma("Br-Gamma (2.166 um)", "Br-Gamma", 2.166, ReadMode.FAINT, new SingleBand((MagnitudeBand.K$.MODULE$))),
    CO("CO (2.290 um)", "CO", 2.290, ReadMode.FAINT, new SingleBand((MagnitudeBand.K$.MODULE$))),

    J_Cont("J Cont (1.2132 um)", "J Cont", 1.2132, ReadMode.VERY_FAINT, new SingleBand((MagnitudeBand.J$.MODULE$))),
    Pa_Pi("Pa-Pi (1.2818 um)", "Pa-Pi", 1.2818, ReadMode.FAINT),
    H_Cont("H Cont (1.5804 um)", "H Cont", 1.5804, ReadMode.FAINT, new SingleBand((MagnitudeBand.H$.MODULE$))),
    H2("H2 (2.121 um)", "H2", 2.121, ReadMode.FAINT, new SingleBand((MagnitudeBand.K$.MODULE$))),
    K_Cont("K Cont (2.143 um)", "K Cont", 2.143, ReadMode.FAINT, new SingleBand((MagnitudeBand.K$.MODULE$))),;


    public static Filter DEFAULT = Z;
    public static final ItemKey KEY = new ItemKey(INSTRUMENT_KEY, "filter");

    private final String displayValue;
    private final String logValue;
    private final double wavelength;
    private final ReadMode readMode;
    private final Option<BandsList> catalogBand;

    Filter(String displayValue, String logValue, double wavelength, ReadMode readMode, BandsList catalogBand) {
      this.displayValue = displayValue;
      this.logValue = logValue;
      this.wavelength = wavelength;
      this.readMode = readMode;
      this.catalogBand = new Some<>(catalogBand);
    }

    Filter(String displayValue, String logValue, double wavelength, ReadMode readMode) {
      this.displayValue = displayValue;
      this.logValue = logValue;
      this.wavelength = wavelength;
      this.readMode = readMode;
      this.catalogBand = None.instance();
    }

    public String displayValue() {
      return displayValue;
    }

    public String logValue() {
      return logValue;
    }

    public double wavelength() {
      return wavelength;
    }

    public String formattedWavelength() {
      return String.format("%.3f", wavelength);
    }

    public ReadMode readMode() {
      return readMode;
    }

    public String sequenceValue() {
      return name();
    }

    public String toString() {
      return displayValue;
    }

    /**
     * Returns the filter matching the given name by searching through the
     * known types.  If not found, nvalue is returned.
     */
    public static Filter valueOf(String name, Filter nvalue) {
      return SpTypeUtil.oldValueOf(Filter.class, name, nvalue);
    }

    /**
     * Returns the filter matching the given magnitude band by searching through the
     * known types.  If not found, nvalue is returned.
     */
    public static Filter getFilter(MagnitudeBand band, Filter nvalue) {
      for (Filter filter : values()) {
        if (!filter.catalogBand.isEmpty() && filter.catalogBand.getValue().bandSupported(band)) {
          return filter;
        }
      }
      return nvalue;
    }

    public Option<BandsList> getCatalogBand() {
      return catalogBand;
    }
  }


  public enum Detector implements DisplayableSpType, SequenceableSpType, LoggableSpType {
    IMAGER_ONLY("Imager Only", "imagerOnly"),
    IMAGER_WITH_IFS("Imager with IFS", "imagerWithIfs"),
    IFS_WITH_IMAGER("IFS with Imager", "ifsWithImager"),
    IFS_ONLY("IFS Only", "ifsOnly");

    public static Detector DEFAULT = IMAGER_ONLY;
    public static final ItemKey KEY = new ItemKey(INSTRUMENT_KEY, "detector");

    private final String displayValue;
    private final String logValue;

    Detector(String displayValue, String logValue) {
      this.displayValue = displayValue;
      this.logValue = logValue;
    }

    public String displayValue() {
      return displayValue;
    }

    public String logValue() {
      return logValue;
    }

    public String sequenceValue() {
      return name();
    }

    public String toString() {
      return displayValue;
    }

    /**
     * Returns the detector matching the given name by searching through the
     * known types.  If not found, nvalue is returned.
     */
    public static Detector valueOf(String name, Detector nvalue) {
      return SpTypeUtil.oldValueOf(Detector.class, name, nvalue);
    }
  }


  /**
   * IRIS Dispersers.
   */
  public enum Disperser implements DisplayableSpType, SequenceableSpType, LoggableSpType {

    NONE("None", "none", None.DOUBLE),
    R1200JH("R=1200 (J + H) grism", "R1200JH", new Some<>(1.39)),
    R1200HK("R=1200 (H + K) grism", "R1200HK", new Some<>(1.871)),
    R3000("R=3000 (J or H or K) grism", "R3000", new Some<>(1.65)),;

    /**
     * The default Disperser value
     **/
    public static final Disperser DEFAULT = NONE;
    public static final ItemKey KEY = new ItemKey(INSTRUMENT_KEY, "disperser");

    private final String _displayName;
    private final String _logValue;
    private final Option<Double> _wavelength;  // in um

    Disperser(String name, String logValue, Option<Double> wavelength) {
      _displayName = name;
      _logValue = logValue;
      _wavelength = wavelength;
    }

    public String displayValue() {
      return _displayName;
    }

    public Option<Double> getWavelength() {
      return _wavelength;
    }

    public String sequenceValue() {
      return name();
    }

    public String logValue() {
      return _logValue;
    }

    public String toString() {
      return displayValue();
    }

    // REL-1522
    public static Option<Disperser> byName(String name) {
      for (Disperser m : values()) {
        if (m.displayValue().equals(name)) {
          return new Some<>(m);
        }
      }
      return None.instance();
    }
  }

  public enum UtilityWheel implements DisplayableSpType, SequenceableSpType, LoggableSpType {
    EXTRAFOCAL_LENS_1("Extra-focal lens 1", "xf 1"),
    EXTRAFOCAL_LENS_2("Extra-focal lens 2", "xf 2"),
    PUPIL_IMAGER("Pupil Imager", "pupil"),
    CLEAR("Clear", "clear"),;

    public static UtilityWheel DEFAULT = CLEAR;
    public static ItemKey KEY = new ItemKey(INSTRUMENT_KEY, "utilityWheel");

    private final String displayValue;
    private final String logValue;

    UtilityWheel(String displayValue, String logValue) {
      this.displayValue = displayValue;
      this.logValue = logValue;
    }

    public String displayValue() {
      return displayValue;
    }

    public String logValue() {
      return logValue;
    }

    public String sequenceValue() {
      return name();
    }

    public String toString() {
      return displayValue;
    }

    public static UtilityWheel valueOf(String name, UtilityWheel nvalue) {
      return SpTypeUtil.oldValueOf(UtilityWheel.class, name, nvalue);
    }
  }

  public enum Roi implements DisplayableSpType, SequenceableSpType, LoggableSpType {
    FULL_ARRAY("Full Array", "Full Array"),
    ARRAY_64("Array 64", "Array64x64"),
    ARRAY_128("Array 128", "Array128x128"),
    ARRAY_256("Array 256", "Array256x256"),
    ARRAY_512("Array 512", "Array512x512"),
    ARRAY_1K("Array 1K", "Array1kx1k"),
    CENTRAL_64("Central 64", "Det64x64"),
    CENTRAL_128("Central 128", "Det128x128"),
    CENTRAL_256("Central 256", "Det256x256"),
    CENTRAL_512("Central 512", "Det512x512"),
    CENTRAL_1K("Central 1K", "Det1kx1k"),
    CENTRAL_2K("Central 2K", "Det2kx2k");

    public static Roi DEFAULT = FULL_ARRAY;

    private final String displayValue;
    private final String logValue;

    Roi(String displayValue, String logValue) {
      this.displayValue = displayValue;
      this.logValue = logValue;
    }

    public String displayValue() {
      return displayValue;
    }

    public String logValue() {
      return logValue;
    }

    public String sequenceValue() {
      return logValue;
    }

    public String toString() {
      return displayValue;
    }

    public static Roi valueOf(String name, Roi nvalue) {
      return SpTypeUtil.oldValueOf(Roi.class, name, nvalue);
    }
  }

  public enum OdgwSize implements DisplayableSpType, SequenceableSpType, LoggableSpType {
    SIZE_4(4),
    SIZE_6(6),
    SIZE_8(8),
    SIZE_16(16),
    SIZE_32(32),
    SIZE_64(64);

    public static OdgwSize DEFAULT = SIZE_64;

    private final int size;

    OdgwSize(int size) {
      this.size = size;
    }

    public int getSize() {
      return size;
    }

    public String displayValue() {
      return String.valueOf(size);
    }

    public String sequenceValue() {
      return displayValue();
    }

    public String logValue() {
      return displayValue();
    }

    public String toString() {
      return displayValue();
    }

    public static OdgwSize valueOf(String name, OdgwSize nvalue) {
      return SpTypeUtil.oldValueOf(OdgwSize.class, name, nvalue);
    }

  }

  private static final String VERSION = "2009A-1";

  public static final SPComponentType SP_TYPE =
      SPComponentType.INSTRUMENT_IRIS;

  public static final String INSTRUMENT_NAME_PROP = "IRIS";

  // REL-2645 offset overhead is 15 secs
  static final double GUIDED_OFFSET_OVERHEAD = 15.0; // sec
  private static final int MCAO_SETUP_TIME = 30; // min
  private static final int IRIS_REACQUISITION_TIME = 10; // min

  private static final CategorizedTime GUIDED_OFFSET_OVERHEAD_CATEGORIZED_TIME =
      CategorizedTime.fromSeconds(Category.CONFIG_CHANGE, GUIDED_OFFSET_OVERHEAD, OffsetOverheadCalculator.DETAIL);

  public static final PropertyDescriptor FILTER_PROP;
  public static final PropertyDescriptor DETECTOR_PROP;
  public static final PropertyDescriptor READ_MODE_PROP;
  public static final PropertyDescriptor PORT_PROP;
  public static final PropertyDescriptor EXPOSURE_TIME_PROP;
  public static final PropertyDescriptor COADDS_PROP;
  public static final PropertyDescriptor POS_ANGLE_PROP;
  public static final PropertyDescriptor POS_ANGLE_CONSTRAINT_PROP;
  public static final PropertyDescriptor UTILITY_WHEEL_PROP;
  public static final PropertyDescriptor ROI_PROP;
  public static final PropertyDescriptor ODGW_SIZE_PROP;

  private static final Map<String, PropertyDescriptor> PRIVATE_PROP_MAP = new TreeMap<>();
  public static final Map<String, PropertyDescriptor> PROPERTY_MAP = Collections.unmodifiableMap(PRIVATE_PROP_MAP);

  private static PropertyDescriptor initProp(String propName, boolean query, boolean iter) {
    PropertyDescriptor pd;
    pd = PropertySupport.init(propName, Iris.class, query, iter);
    PRIVATE_PROP_MAP.put(pd.getName(), pd);
    return pd;
  }

  static {
    boolean query_yes = true;
    boolean iter_yes = true;
    boolean query_no = false;
    boolean iter_no = false;

    FILTER_PROP = initProp(Filter.KEY.getName(), query_yes, iter_yes);
    DETECTOR_PROP = initProp(Detector.KEY.getName(), query_yes, iter_yes);
    READ_MODE_PROP = initProp(ReadMode.KEY.getName(), query_yes, iter_yes);
    PORT_PROP = initProp("issPort", query_no, iter_no);
    EXPOSURE_TIME_PROP = initProp("exposureTime", query_no, iter_yes);
    COADDS_PROP = initProp("coadds", query_no, iter_yes);
    POS_ANGLE_PROP = initProp("posAngle", query_no, iter_no);
    POS_ANGLE_CONSTRAINT_PROP = initProp("posAngleConstraint", query_no, iter_no);

    UTILITY_WHEEL_PROP = initProp(UtilityWheel.KEY.getName(), query_no, iter_yes);
    UTILITY_WHEEL_PROP.setExpert(true);
    PropertySupport.setWrappedType(UTILITY_WHEEL_PROP, UtilityWheel.class);

    ODGW_SIZE_PROP = initProp("odgwSize", query_no, iter_yes);
    ODGW_SIZE_PROP.setExpert(true);
    ODGW_SIZE_PROP.setDisplayName("ODGW Size");
    PropertySupport.setWrappedType(ODGW_SIZE_PROP, OdgwSize.class);

    ROI_PROP = initProp("roi", query_no, iter_yes);
    ROI_PROP.setExpert(true);
    ROI_PROP.setDisplayName("Region of Interest");
    PropertySupport.setWrappedType(ROI_PROP, Roi.class);
  }

  private PosAngleConstraint _posAngleConstraint = PosAngleConstraint.UNBOUNDED;

  private Filter filter = Filter.DEFAULT;
  private Detector detector = Detector.DEFAULT;
  private ReadMode readMode;
  private IssPort port = IssPort.UP_LOOKING;

  private UtilityWheel utilityWheel = UtilityWheel.DEFAULT;
  private OdgwSize odgwSize = OdgwSize.DEFAULT;
  private Roi roi = Roi.DEFAULT;

  public Iris() {
    super(SP_TYPE);
    setVersion(VERSION);
//        readMode = filter.readMode();
    setExposureTime(60); // REL-445
  }

  public Map<String, PropertyDescriptor> getProperties() {
    return PROPERTY_MAP;
  }

  @Override
  public Set<Site> getSite() {
    return Site.SET_GS;
  }

  @Override
  public double[] getScienceArea() {
    return new double[]{85.0, 85.0};
  }

  public String getPhaseIResourceName() {
    return "gemIRIS";
  }

  public Filter getFilter() {
    return filter;
  }

  public void setFilter(Filter newValue) {
    Filter oldValue = getFilter();
    if (oldValue != newValue) {
      filter = newValue;
      firePropertyChange(FILTER_PROP.getName(), oldValue, newValue);
    }
  }

  public Detector getDetector() {
    return detector;
  }

  public void setDetector(Detector newValue) {
    Detector oldValue = getDetector();
    if (oldValue != newValue) {
      detector = newValue;
      firePropertyChange(DETECTOR_PROP.getName(), oldValue, newValue);
    }
  }

  public UtilityWheel getUtilityWheel() {
    return utilityWheel;
  }

  public void setUtilityWheel(UtilityWheel newValue) {
    UtilityWheel oldValue = getUtilityWheel();
    if (!oldValue.equals(newValue)) {
      utilityWheel = newValue;
      firePropertyChange(UTILITY_WHEEL_PROP.getName(), oldValue, newValue);
    }
  }

  public Roi getRoi() {
    return roi;
  }

  public void setRoi(Roi newValue) {
    Roi oldValue = getRoi();
    if (!oldValue.equals(newValue)) {
      roi = newValue;
      firePropertyChange(ROI_PROP.getName(), oldValue, newValue);
    }
  }

  public OdgwSize getOdgwSize() {
    return odgwSize;
  }

  public void setOdgwSize(OdgwSize newValue) {
    OdgwSize oldValue = getOdgwSize();
    if (!oldValue.equals(newValue)) {
      odgwSize = newValue;
      firePropertyChange(ODGW_SIZE_PROP.getName(), oldValue, newValue);
    }
  }


  public ReadMode getReadMode() {
    return readMode;
  }

  public void setReadMode(ReadMode newValue) {
    ReadMode oldValue = getReadMode();
    if (oldValue != newValue) {
      readMode = newValue;
      firePropertyChange(READ_MODE_PROP.getName(), oldValue, newValue);
    }
  }

  public IssPort getIssPort() {
    return port;
  }

  public void setIssPort(IssPort newValue) {
    IssPort oldValue = getIssPort();
    if (oldValue != newValue) {
      port = newValue;
      firePropertyChange(PORT_PROP.getName(), oldValue, newValue);
    }
  }

  public double getRecommendedExposureTimeSecs() {
//        return getRecommendedExposureTimeSecs(getFilter(), getReadMode());
    return 0.0; // Allan: XXX TODO FIXME: How to calculate this for IRIS?
  }

//    public static double getRecommendedExposureTimeSecs(Filter filter, ReadMode readMode) {
//        double min = getMinimumExposureTimeSecs(readMode);
//        if (filter == null) return min;
//        double res = 3 * filter.exposureTime5050Secs();
//        return (res < min) ? min : res;
//    }

  public double getMinimumExposureTimeSecs() {
    return getMinimumExposureTimeSecs(getReadMode());
  }

  public static double getMinimumExposureTimeSecs(ReadMode readMode) {
    if (readMode == null) return 0;
    return readMode.minExposureTimeSecs();
  }

  public int getNonDestructiveReads() {
    ReadMode readMode = getReadMode();
    if (readMode == null) return 0;
    return readMode.ndr();
  }

  public int getReadNoise() {
    ReadMode readMode = getReadMode();
    if (readMode == null) return 0;
    return readMode.readNoise();
  }

  /**
   * Time needed to setup the instrument before the Observation
   *
   * @param obs the observation for which the setup time is wanted
   * @return time in seconds
   */
  public double getSetupTime(ISPObservation obs) {
    return MCAO_SETUP_TIME * 60;//MCAO setup time: 30m
  }

  /**
   * Time needed to re-setup the instrument before the Observation following a previous full setup.
   *
   * @param obs the observation for which the setup time is wanted
   * @return time in seconds
   */
  public double getReacquisitionTime(ISPObservation obs) {
    return IRIS_REACQUISITION_TIME * 60; // 10 mins as defined in REL-1346
  }

  public static CategorizedTime getWheelMoveOverhead() {
    // REL-1103 - 15 seconds for wheel move overhead
    return CategorizedTime.apply(Category.CONFIG_CHANGE, 15000, "Instrument");
  }

  // Predicate that leaves all CategorizedTime except for the offset overhead.
  private static final PredicateOp<CategorizedTime> RM_OFFSET_OVERHEAD = ct -> !((ct.category == Category.CONFIG_CHANGE) &&
      (ct.detail.equals(OffsetOverheadCalculator.DETAIL)));

  private static double getOffsetArcsec(Config c, ItemKey k) {
    final String d = (String) c.getItemValue(k); // yes a string :/
    return (d == null) ? 0.0 : Double.parseDouble(d);
  }

  private static boolean isOffset(Config c, Option<Config> prev) {
    final double p1 = getOffsetArcsec(c, OffsetPosBase.TEL_P_KEY);
    final double q1 = getOffsetArcsec(c, OffsetPosBase.TEL_Q_KEY);

    final double p0, q0;
    if (prev.isEmpty()) {
      p0 = 0.0;
      q0 = 0.0;
    } else {
      p0 = getOffsetArcsec(prev.getValue(), OffsetPosBase.TEL_P_KEY);
      q0 = getOffsetArcsec(prev.getValue(), OffsetPosBase.TEL_Q_KEY);
    }
    return (p0 != p1) || (q0 != q1);
  }

  private static boolean isActive(Config c, String prop) {
    final ItemKey k = new ItemKey(SeqConfigNames.TELESCOPE_KEY, prop);
    final GuideOption go = (GuideOption) c.getItemValue(k);
    return (go != null) && go.isActive();
  }

  private static boolean isGuided(Config c) {
    for (final IrisOdgw odgw : IrisOdgw.values()) {
      if (isActive(c, odgw.getSequenceProp())) return true;
    }
    for (final NfiraosOiwfs.Wfs wfs : NfiraosOiwfs.Wfs.values()) {
      if (isActive(c, wfs.getSequenceProp())) return true;
    }
    return false;
  }

  private static boolean isGuided(Option<Config> c) {
    return !c.isEmpty() && isGuided(c.getValue());
  }

  private static boolean isExpensiveOffset(Config cur, Option<Config> prev) {
    if (!isOffset(cur, prev)) return false;

    final boolean curGuided = isGuided(cur);
    return curGuided || isGuided(prev);

  }

  // REL-1103
  // Get correct offset overhead in the common group.  If a guided offset
  // or a switch from guided to non-guided, it is expensive.  If going from
  // a sky position to another sky position, it counts as a normal offset.
  private CategorizedTimeGroup commonGroup(Config cur, Option<Config> prev) {
    CategorizedTimeGroup ctg = CommonStepCalculator.instance.calc(cur, prev);
    return (isExpensiveOffset(cur, prev)) ?
        ctg.filter(RM_OFFSET_OVERHEAD).add(GUIDED_OFFSET_OVERHEAD_CATEGORIZED_TIME) :
        ctg;
  }


  @Override
  public CategorizedTimeGroup calc(Config cur, Option<Config> prev) {
    // Add wheel move overhead
    Collection<CategorizedTime> times = new ArrayList<>();
    if (PlannedTime.isUpdated(cur, prev, Filter.KEY, UtilityWheel.KEY)) {
      times.add(getWheelMoveOverhead());
    }

    // Add exposure time
    double exposureTime = ExposureCalculator.instance.exposureTimeSec(cur);
    int coadds = ExposureCalculator.instance.coadds(cur);
    times.add(CategorizedTime.fromSeconds(Category.EXPOSURE, exposureTime * coadds));

    // Add readout overhead
    int lowNoiseReads = getNonDestructiveReads();
    double readout = 21 + 2.8 * lowNoiseReads * coadds + 6.5 * (coadds - 1); // REL-445
    times.add(CategorizedTime.fromSeconds(Category.READOUT, readout).add(-Category.DHS_OVERHEAD.time)); // REL-1678
    times.add(Category.DHS_OVERHEAD); // REL-1678

    return commonGroup(cur, prev).addAll(times);
  }

  public ParamSet getParamSet(PioFactory factory) {
    ParamSet paramSet = super.getParamSet(factory);

    Pio.addParam(factory, paramSet, FILTER_PROP.getName(), filter.name());
    Pio.addParam(factory, paramSet, DETECTOR_PROP.getName(), detector.name());
    Pio.addParam(factory, paramSet, READ_MODE_PROP.getName(), readMode.name());
    Pio.addParam(factory, paramSet, PORT_PROP.getName(), port.name());
    Pio.addParam(factory, paramSet, POS_ANGLE_CONSTRAINT_PROP.getName(), getPosAngleConstraint().name());
    Pio.addParam(factory, paramSet, UTILITY_WHEEL_PROP.getName(), utilityWheel.name());
    Pio.addParam(factory, paramSet, ODGW_SIZE_PROP.getName(), odgwSize.name());
    Pio.addParam(factory, paramSet, ROI_PROP.getName(), roi.name());

    return paramSet;
  }

  @Override
  public boolean hasOIWFS() {
    // No OIWFS -- there is a on-detector guide window
    return false;
  }

  public void setParamSet(ParamSet paramSet) {
    super.setParamSet(paramSet);

    String v;
    v = Pio.getValue(paramSet, FILTER_PROP.getName());
    if (v != null) setFilter(Filter.valueOf(v, getFilter()));

    v = Pio.getValue(paramSet, DETECTOR_PROP.getName());
    if (v != null) setDetector(Detector.valueOf(v, getDetector()));

    v = Pio.getValue(paramSet, READ_MODE_PROP.getName());
    if (v != null) setReadMode(ReadMode.valueOf(v, getReadMode()));

    v = Pio.getValue(paramSet, PORT_PROP.getName());
    if (v != null) setIssPort(IssPort.valueOf(v));

    v = Pio.getValue(paramSet, POS_ANGLE_CONSTRAINT_PROP.getName());
    if (v != null) setPosAngleConstraint(PosAngleConstraint.valueOf(v));

    v = Pio.getValue(paramSet, UTILITY_WHEEL_PROP.getName());
    if (v != null) setUtilityWheel(UtilityWheel.valueOf(v, getUtilityWheel()));

    v = Pio.getValue(paramSet, ODGW_SIZE_PROP.getName());
    if (v != null) setOdgwSize(OdgwSize.valueOf(v, getOdgwSize()));

    v = Pio.getValue(paramSet, ROI_PROP.getName());
    if (v != null) setRoi(Roi.valueOf(v, getRoi()));

  }

  public ISysConfig getSysConfig() {
    ISysConfig sc = new DefaultSysConfig(SeqConfigNames.INSTRUMENT_CONFIG_NAME);

    sc.putParameter(StringParameter.getInstance(ISPDataObject.VERSION_PROP, getVersion()));
    sc.putParameter(DefaultParameter.getInstance(FILTER_PROP.getName(), getFilter()));
    sc.putParameter(DefaultParameter.getInstance(DETECTOR_PROP.getName(), getDetector()));
    sc.putParameter(DefaultParameter.getInstance(READ_MODE_PROP.getName(), getReadMode()));
    sc.putParameter(DefaultParameter.getInstance(PORT_PROP, getIssPort()));
    sc.putParameter(DefaultParameter.getInstance(UTILITY_WHEEL_PROP.getName(), getUtilityWheel()));
    sc.putParameter(DefaultParameter.getInstance(ODGW_SIZE_PROP.getName(), getOdgwSize()));
    sc.putParameter(DefaultParameter.getInstance(ROI_PROP.getName(), getRoi()));
    sc.putParameter(DefaultParameter.getInstance(EXPOSURE_TIME_PROP.getName(), getExposureTime()));
    sc.putParameter(DefaultParameter.getInstance(POS_ANGLE_PROP.getName(), getPosAngleDegrees()));
    sc.putParameter(DefaultParameter.getInstance(POS_ANGLE_CONSTRAINT_PROP.getName(), getPosAngleConstraint()));
    sc.putParameter(DefaultParameter.getInstance(COADDS_PROP.getName(), getCoadds()));

    return sc;
  }

  public static List<InstConfigInfo> getInstConfigInfo() {
    List<InstConfigInfo> configInfo = new LinkedList<>();
    configInfo.add(new InstConfigInfo(FILTER_PROP));
    configInfo.add(new InstConfigInfo(DETECTOR_PROP));
    configInfo.add(new InstConfigInfo(READ_MODE_PROP));
    return configInfo;
  }

  private static final Collection<GuideProbe> GUIDERS = GuideProbeUtil.instance.createCollection(IrisOdgw.values());

  public Collection<GuideProbe> getGuideProbes() {
    return GUIDERS;
  }

  public static final ConfigInjector<String> WAVELENGTH_INJECTOR = ConfigInjector.create(
      new ObsWavelengthCalc1<Filter>() {
        @Override
        public PropertyDescriptor descriptor1() {
          return FILTER_PROP;
        }

        @Override
        public String calcWavelength(Filter f) {
          return f.formattedWavelength();
        }
      }
  );

  private static final Angle PWFS1_VIG = Angle.arcmins(5.8);

  @Override
  public Angle pwfs1VignettingClearance() {
    return PWFS1_VIG;
  }

  @Override
  public PosAngleConstraint getPosAngleConstraint() {
    return (_posAngleConstraint == null) ? PosAngleConstraint.UNBOUNDED : _posAngleConstraint;
  }

  @Override
  public void setPosAngleConstraint(PosAngleConstraint newValue) {
    if (getSupportedPosAngleConstraints().contains(newValue)) { // Ignore unknown values
      PosAngleConstraint oldValue = getPosAngleConstraint();
      if (oldValue != newValue) {
        _posAngleConstraint = newValue;
        firePropertyChange(POS_ANGLE_CONSTRAINT_PROP.getName(), oldValue, newValue);
      }
    }
  }

  @Override
  public ImList<PosAngleConstraint> getSupportedPosAngleConstraints() {
    return DefaultImList.create(PosAngleConstraint.FIXED,
        PosAngleConstraint.UNBOUNDED);
  }

  @Override
  public boolean allowUnboundedPositionAngle() {
    return true;
  }

}
