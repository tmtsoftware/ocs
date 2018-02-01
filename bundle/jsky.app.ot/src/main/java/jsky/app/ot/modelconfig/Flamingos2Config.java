//
// $
//

package jsky.app.ot.modelconfig;

import edu.gemini.skycalc.Angle;
import edu.gemini.shared.util.immutable.MapOp;
import edu.gemini.shared.util.immutable.None;
import edu.gemini.shared.util.immutable.Option;
import edu.gemini.shared.util.immutable.Some;
import edu.gemini.spModel.gemini.flamingos2.Flamingos2;
import edu.gemini.spModel.telescope.IssPort;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Applies Flamingos2 configuration options.
 */
public enum Flamingos2Config implements ConfigApply {
    instance;

    private static final Logger LOG = Logger.getLogger(Flamingos2Config.class.getName());


    public static final String FLAMINGOS2_UP_NFIRAOS = "flamingos2UpNfiraos";
    public static final String FLAMINGOS2_SIDE_NFIRAOS = "flamingos2SideNfiraos";
    public static final String FLAMINGOS2_UP_NONFIRAOS = "flamingos2UpNoNfiraos";
    public static final String FLAMINGOS2_SIDE_NONFIRAOS = "flamingos2SideNoNfiraos";
    public static final String FLAMINGOS2_ROTATION = "rotation";
    public static final String FLAMINGOS2_FLIP = "flip";


    private static Option<Boolean> parseFlip(String str) {
        Boolean f = Boolean.parseBoolean(str);//this can be expanded to take a wider range of arguments
        Option<Boolean> none = None.instance();
        return (f == null) ? none : new Some<Boolean>(f);
    }

    private static Option<Boolean> getFlip(String key, ModelConfig config) {
        Option<String> flipOpt = config.get(key + "." + FLAMINGOS2_FLIP);
        return flipOpt.flatMap(new MapOp<String, Option<Boolean>>() {
            @Override
            public Option<Boolean> apply(String s) {
                return parseFlip(s);
            }
        });
    }

    private static Option<Angle> parseAngle(Double d) {
        if ((d == null) || d.isInfinite() || d.isNaN()) {
            return None.instance();
        } else {
            return new Some<Angle>(new Angle(d, Angle.Unit.DEGREES));
        }
    }

    private static Option<Angle> getAngle(String key, ModelConfig config) {
        Option<Double> dOpt = config.getDouble(key + "." + FLAMINGOS2_ROTATION);
        return dOpt.flatMap(new MapOp<Double, Option<Angle>>() {
            @Override
            public Option<Angle> apply(Double d) {
                return parseAngle(d);
            }
        });
    }

    private void setFlamingos(String key, ModelConfig config) {
        Option<Boolean> flipOpt = getFlip(key, config);
        if (flipOpt.isEmpty()) return;
        Option<Angle> angleOpt = getAngle(key, config);
        if (angleOpt.isEmpty()) return;

        Boolean flip = flipOpt.getValue();
        Angle angle = angleOpt.getValue();


        IssPort port = (FLAMINGOS2_SIDE_NONFIRAOS == key) || (FLAMINGOS2_SIDE_NFIRAOS == key) ? IssPort.SIDE_LOOKING : IssPort.UP_LOOKING;
        if ((FLAMINGOS2_SIDE_NFIRAOS == key) || (FLAMINGOS2_UP_NFIRAOS == key)) {
            LOG.log(Level.INFO, "Set Flamingos2 " + port.displayValue() + " with Nfiraos: rotation = " + angle + ", p/q flip = " + flip);
            Flamingos2.setFlipRotationConfig(port, true, angle, flip);
        } else {
            LOG.log(Level.INFO, "Set Flamingos2 " + port.displayValue() + " without Nfiraos: rotation = " + angle + ", p/q flip = " + flip);
            Flamingos2.setFlipRotationConfig(port, false, angle, flip);
        }
    }

    @Override
    public void apply(ModelConfig config) {
        setFlamingos(FLAMINGOS2_UP_NFIRAOS, config);
        setFlamingos(FLAMINGOS2_SIDE_NFIRAOS, config);
        setFlamingos(FLAMINGOS2_UP_NONFIRAOS, config);
        setFlamingos(FLAMINGOS2_SIDE_NONFIRAOS, config);
    }
}
