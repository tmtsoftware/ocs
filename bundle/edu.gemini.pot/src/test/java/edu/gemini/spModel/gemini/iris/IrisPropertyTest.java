package edu.gemini.spModel.gemini.iris;

import edu.gemini.shared.util.immutable.DefaultImList;
import edu.gemini.shared.util.immutable.ImList;
import edu.gemini.spModel.data.ISPDataObject;
import edu.gemini.spModel.gemini.BeanPropertyTestBase;
import static edu.gemini.spModel.gemini.iris.Iris.*;
import edu.gemini.spModel.obscomp.InstConstants;
import edu.gemini.spModel.telescope.IssPort;
import edu.gemini.spModel.telescope.PosAngleConstraint;

/**
 * Test cases for {@link Iris} properties.
 */
@SuppressWarnings("rawtypes")
public class IrisPropertyTest extends BeanPropertyTestBase {

    private final ImList<PropertyTest> tests = DefaultImList.create(
        new PropertyTest(FILTER_PROP, Filter.DEFAULT, Filter.H2, Filter.CO),
        new PropertyTest(READ_MODE_PROP, Filter.DEFAULT.readMode(), ReadMode.BRIGHT),
        new PropertyTest(PORT_PROP, IssPort.UP_LOOKING, IssPort.SIDE_LOOKING),

        new PropertyTest(COADDS_PROP, InstConstants.DEF_COADDS, 42),
        new PropertyTest(POS_ANGLE_PROP, 0.0, 10.0),
        new PropertyTest(POS_ANGLE_CONSTRAINT_PROP, PosAngleConstraint.UNBOUNDED, PosAngleConstraint.FIXED),
        new PropertyTest(EXPOSURE_TIME_PROP, 60.0 , 123.0) // REL-445
    );

    protected final ISPDataObject createBean() {
        return new Iris();
    }

    public void testGetSet() throws Exception {
        testGetSet(tests);
    }

    public void testParamSet() throws Exception {
        testParamSet(tests);
    }

    public void testSysConfig() throws Exception {
        testSysConfig(tests);
    }
}
