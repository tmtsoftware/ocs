package edu.gemini.wdba.tcc;

import edu.gemini.spModel.gemini.iris.Iris;
import edu.gemini.spModel.gemini.iris.IrisOdgw;
import edu.gemini.spModel.telescope.IssPort;

public class IrisSupport implements ITccInstrumentSupport {

    private ObservationEnvironment _oe;

    private IrisSupport(ObservationEnvironment oe) {
        if (oe == null) throw new NullPointerException("observation environment can not be null");
        _oe = oe;
    }

    /**
     * Factory for creating a new Iris instrument.
     *
     * @param oe The current ObservationEnvironment
     *
     * @return A new instance
     *
     * @throws NullPointerException returned if the ObservationEnvironment is
     * null.
     */
    public static ITccInstrumentSupport create(ObservationEnvironment oe) throws NullPointerException {
        return new IrisSupport(oe);
    }

    /**
     * Returns the appropriate wavelength for the observation for the TCC
     * config.
     *
     * @return Appropriate guide wavelength
     */
    public String getWavelength() {
        Iris inst = (Iris) _oe.getInstrument();
        return String.valueOf(inst.getFilter().wavelength());
    }

    public String getPositionAngle() {
        Iris inst = (Iris) _oe.getInstrument();
        return inst.getPosAngleDegreesStr();
    }

    public String getTccConfigInstrument() {
        // Return the name of the IRIS config
        Iris inst = (Iris) _oe.getInstrument();
        return (inst.getIssPort() == IssPort.SIDE_LOOKING) ? "IRIS5" : "IRIS";
    }

    /**
     * Support for instrument origins.
     *
     * @return String that is the name of a TCC config file.  See WDBA-5.
     */
    public String getTccConfigInstrumentOrigin() {
        return "lgs2iris";
    }

    /**
     * Return true if the instrument is using a fixed rotator position.  In
     * this case the pos angle is used in a special rotator config
     *
     * @return String value that is the name of the fixed rotator config or
     * null if no special name is needed
     */
    public String getFixedRotatorConfigName() {
        return null;
    }

    /**
     * Add the OIWFS wavelength.
     */
    public void addGuideDetails(ParamSet guideConfig) {
        boolean contains = false;
        for (IrisOdgw odgw : IrisOdgw.values()) {
            if (_oe.containsTargets(odgw)) {
                contains = true;
                break;
            }
        }

        if (contains) {
            guideConfig.putParameter(TccNames.OIWFSWAVELENGTH, "IRIS OIWFS");
        }
    }

    /**
     * Returns the TCC chop parameter value.
     *
     * @return Chop value or null if there is no chop parameter for this
     * instrument
     */
    public String getChopState() {
        return TccNames.NOCHOP;
    }
}
