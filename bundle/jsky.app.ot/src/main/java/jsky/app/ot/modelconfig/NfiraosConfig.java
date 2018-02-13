package jsky.app.ot.modelconfig;

import edu.gemini.skycalc.Angle;
import edu.gemini.shared.util.immutable.MapOp;
import edu.gemini.shared.util.immutable.None;
import edu.gemini.shared.util.immutable.Option;
import edu.gemini.shared.util.immutable.Some;
import edu.gemini.spModel.gemini.nfiraos.NfiraosOiwfs;
import edu.gemini.spModel.gemini.iris.IrisDetectorArray;
import edu.gemini.spModel.gemini.iris.IrisDetectorArray.Config.Direction;
import edu.gemini.spModel.telescope.IssPort;

import static edu.gemini.spModel.gemini.iris.IrisDetectorArray.Config.Direction.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Applies Nfiraos (Nfiraos and IRIS) configuration options.
 */
public enum NfiraosConfig implements ConfigApply {
    instance;

    private static final Logger LOG = Logger.getLogger(NfiraosConfig.class.getName());

    public static final String IrisOiwfs_UP         = "irisOiwfsUp";
    public static final String IrisOiwfs_SIDE       = "irisOiwfsSide";
    public static final String IrisOiwfs_ROT        = "rotation";

    private static Option<Angle> parseAngle(Double d) {
        if ((d == null) || d.isInfinite() || d.isNaN()) {
            return None.instance();
        } else {
            return new Some<Angle>(new Angle(d, Angle.Unit.DEGREES));
        }
    }

    private static Option<Angle> getAngle(String key, ModelConfig config) {
        Option<Double> dOpt = config.getDouble(key + "." + IrisOiwfs_ROT);
        return dOpt.flatMap(new MapOp<Double, Option<Angle>>() {
            @Override public Option<Angle> apply(Double d) {
                return parseAngle(d);
            }
        });
    }

    private void setIrisOiwfs(String key, ModelConfig config) {
        Option<Angle> angleOpt = getAngle(key, config);
        if (angleOpt.isEmpty()) return;

        Angle angle = angleOpt.getValue();

        IssPort port = (IrisOiwfs_SIDE == key) ? IssPort.SIDE_LOOKING : IssPort.UP_LOOKING;
        String msg  = String.format("Set Nfiraos %s: rotation = %s", port.displayValue(), angle);
        LOG.log(Level.INFO, msg);

        NfiraosOiwfs.setRotationConfig(port, angle);
    }


    public static final String ODGW_UP            = "odgwUp";
    public static final String ODGW_SIDE          = "odgwSide";
    public static final String ODGW_DIRECTION_KEY = "direction";
    public static final String ODGW_TOP_LEFT_KEY  = "topLeft";

    private static final Map<String, Direction> m;

    static {
        Map<String, IrisDetectorArray.Config.Direction> t = new HashMap<String, Direction>();
        t.put("clockwise", clockwise);
        t.put("counterclockwise", counterClockwise);
        t.put("cw", clockwise);
        t.put("cc", counterClockwise);
        t.put("counter", counterClockwise);
        t.put("counter clockwise", counterClockwise);
        m = Collections.unmodifiableMap(t);
    }

    private static Option<Direction> parseDirection(String str) {
        str = str.toLowerCase();
        Direction d = m.get(str);
        Option<Direction> none = None.instance();
        return (d == null) ? none : new Some<Direction>(d);
    }

    private static Option<Direction> getDirection(String key, ModelConfig config) {
        Option<String> dirOpt = config.get(key + "." + ODGW_DIRECTION_KEY);
        return dirOpt.flatMap(new MapOp<String, Option<Direction>>() {
            @Override public Option<Direction> apply(String s) {
                return parseDirection(s);
            }
        });
    }

    private static Option<IrisDetectorArray.Id> parseId(Integer i) {
        for (IrisDetectorArray.Id id : IrisDetectorArray.Id.values()) {
            if (id.index() == i) return new Some<IrisDetectorArray.Id>(id);
        }
        return None.instance();
    }

    private static Option<IrisDetectorArray.Id> getId(String key, ModelConfig config) {
        Option<Integer> idOpt = config.getInteger(key + "." + ODGW_TOP_LEFT_KEY);
        return idOpt.flatMap(new MapOp<Integer, Option<IrisDetectorArray.Id>>() {
            @Override public Option<IrisDetectorArray.Id> apply(Integer i) {
                return parseId(i);
            }
        });
    }
    private void setOdgw(String key, ModelConfig config) {
        Option<Direction> dirOpt = getDirection(key, config);
        if (dirOpt.isEmpty()) return;
        Option<IrisDetectorArray.Id> idOpt = getId(key, config);
        if (idOpt.isEmpty()) return;

        Direction dir = dirOpt.getValue();
        IrisDetectorArray.Id id = idOpt.getValue();

        IssPort port = (ODGW_SIDE == key) ? IssPort.SIDE_LOOKING : IssPort.UP_LOOKING;
        String msg  = String.format("Set ODGW %s: top left = %d, direction = %s", port.displayValue(), id.index(), dir.name());
        LOG.log(Level.INFO, msg);

        IrisDetectorArray.Config c = new IrisDetectorArray.Config(id, dir);
        IrisDetectorArray.setQuadrantConfig(port, c);
    }

    @Override public void apply(ModelConfig config) {
        setIrisOiwfs(IrisOiwfs_SIDE, config);
        setIrisOiwfs(IrisOiwfs_UP,   config);
        setOdgw(ODGW_SIDE, config);
        setOdgw(ODGW_UP,   config);
    }
}