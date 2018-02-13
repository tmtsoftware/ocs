package edu.gemini.spModel.gemini.nfiraos;

import edu.gemini.skycalc.Angle;
import edu.gemini.skycalc.Offset;
import edu.gemini.shared.util.immutable.None;
import edu.gemini.shared.util.immutable.Option;
import edu.gemini.shared.util.immutable.Some;
import edu.gemini.spModel.gemini.flamingos2.Flamingos2OiwfsGuideProbe;
import edu.gemini.spModel.gemini.iris.IrisDetectorArray;
import edu.gemini.spModel.gemini.iris.IrisOdgw;
import edu.gemini.spModel.nfiraos.NfiraosGuideProbeGroup;
import edu.gemini.spModel.nfiraos.NfiraosTipTiltMode;

import java.util.Arrays;
import java.util.List;

/**
 * Creates an enum that describes the two instruments available for use with
 * Nfiraos from the point of view of guide star searches.
 * <p/>
 * See OT-21
 */
public enum NfiraosInstrument {
    iris() {
        public Option<Offset> getOffset() {
            Angle a = new Angle(4 + IrisDetectorArray.DETECTOR_GAP_ARCSEC / 2, Angle.Unit.ARCSECS);
            return new Some<>(new Offset(a, a));
        }

        public NfiraosGuideProbeGroup getGuiders() {
            return IrisOdgw.Group.instance;
        }

        public List<NfiraosTipTiltMode> getTipTiltOptions() {
            return Arrays.asList(NfiraosTipTiltMode.nfiraos, NfiraosTipTiltMode.instrument, NfiraosTipTiltMode.both);
        }
    },

    flamingos2() {
        public Option<Offset> getOffset() {
            return None.instance();
        }

        public NfiraosGuideProbeGroup getGuiders() {
            return Flamingos2OiwfsGuideProbe.Group.instance;
        }

        public List<NfiraosTipTiltMode> getTipTiltOptions() {
            return Arrays.asList(NfiraosTipTiltMode.nfiraos);
        }
    };

    // Offset that usage of the instrument introduces to guide star searches.
    public abstract Option<Offset> getOffset();

    public abstract NfiraosGuideProbeGroup getGuiders();

    public abstract List<NfiraosTipTiltMode> getTipTiltOptions();
}
