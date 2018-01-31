package jsky.app.ot.gemini.iris;

import edu.gemini.pot.sp.ISPObsComponent;
import edu.gemini.shared.gui.ThinBorder;
import edu.gemini.shared.gui.bean.*;
import edu.gemini.spModel.gemini.iris.Iris;
import edu.gemini.spModel.telescope.IssPort;
import edu.gemini.spModel.telescope.PosAngleConstraint;
import jsky.app.ot.editor.eng.EngEditor;
import jsky.app.ot.gemini.editor.ComponentEditor;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyDescriptor;

import static edu.gemini.spModel.gemini.iris.Iris.*;

/**
 * User interface and editor for IRIS.
 */
public final class IrisEditor extends ComponentEditor<ISPObsComponent, Iris> implements EngEditor {

    private final class ReadModeMessagePanel extends JPanel implements PropertyChangeListener {
        private final JLabel minExposureLabel = new JLabel("0");
        private final JLabel recExposureLabel = new JLabel("0");
        private final JLabel lowNoiseReadsLabel = new JLabel("0");
        private final JLabel readNoiseLabel = new JLabel("0");


        ReadModeMessagePanel() {
            super(new GridBagLayout());

            final GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx  = 0;
            gbc.gridy  = 0;
            gbc.fill   = GridBagConstraints.NONE;

            setBackground(INFO_BG_COLOR);

            // Low Noise Reads
            gbc.anchor = GridBagConstraints.EAST;
            gbc.insets = new Insets(0, 0, 0, 0);
            gbc.gridwidth = 1;
            add(new JLabel("Low Noise Reads:"), gbc);

            lowNoiseReadsLabel.setForeground(Color.black);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(0, 7, 0, 0);
            ++gbc.gridx;
            gbc.gridwidth = 4;
            add(lowNoiseReadsLabel, gbc);


            // Read Noise
            gbc.anchor = GridBagConstraints.EAST;
            gbc.gridx = 0;
            ++gbc.gridy;
            gbc.insets = new Insets(2, 0, 0, 0);
            gbc.gridwidth = 1;
            add(new JLabel("Read Noise:"), gbc);

            readNoiseLabel.setForeground(Color.black);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(2, 7, 0, 0);
            ++gbc.gridx;
            gbc.gridwidth = 1;
            add(readNoiseLabel, gbc);


            // Exposure Time
            gbc.gridx = 0;
            ++gbc.gridy;
            gbc.gridwidth = 1;
            gbc.anchor = GridBagConstraints.EAST;
            add(new JLabel("Exposure Time:"), gbc);

            gbc.anchor = GridBagConstraints.WEST;
            ++gbc.gridx;
            gbc.insets = new Insets(2, 7, 0, 0);
            recExposureLabel.setForeground(Color.black);
            add(recExposureLabel,gbc);

            ++gbc.gridx;
            gbc.insets = new Insets(2, 3, 0, 0);
            add(new JLabel("sec (recommended)"), gbc);

            ++gbc.gridx;
            gbc.insets = new Insets(2, 7, 0, 0);
            minExposureLabel.setForeground(Color.black);
            add(minExposureLabel, gbc);

            ++gbc.gridx;
            gbc.insets = new Insets(2, 3, 0, 0);
            add(new JLabel("sec (min)"), gbc);


            gbc.gridx   = 10;
            gbc.weightx =  1.0;
            gbc.fill    = GridBagConstraints.HORIZONTAL;
            add(new JPanel() {{setOpaque(false);}}, gbc);
        }

        void update() {
            final Iris iris = getDataObject();
            if (iris == null) return;

            final double recExposureTime = iris.getRecommendedExposureTimeSecs();
            final double minExposureTime = iris.getMinimumExposureTimeSecs();

            final String recExpText = String.format(">%.1f", recExposureTime);
            recExposureLabel.setText(recExpText);
            final String minExpText = String.format("%.1f", minExposureTime);
            minExposureLabel.setText(minExpText);

            lowNoiseReadsLabel.setText(String.valueOf(iris.getNonDestructiveReads()));
            readNoiseLabel.setText(String.valueOf(iris.getReadNoise()) + "e-");
        }

        public void propertyChange(PropertyChangeEvent evt) {
            update();
        }
    }

    private final EditListener<Iris, Iris.Filter> filterChangeListener = evt -> {
        final Filter f = evt.getNewValue();
        if (f == null) return;

        final ReadMode readMode = f.readMode();
        if (readMode == null) return;
        getDataObject().setReadMode(readMode);
    };

    private final class ExposureTimeMessageUpdater implements EditListener<Iris, Double>, PropertyChangeListener {
        private final JLabel label;

        ExposureTimeMessageUpdater() {
            this.label = new JLabel("");
        }

        JLabel getLabel() {
            return label;
        }

        public void valueChanged(EditEvent<Iris, Double> event) {
            update(event.getNewValue());
        }

        public void propertyChange(PropertyChangeEvent evt) {
            update();
        }

        void update() {
            final Iris iris = getDataObject();
            update((iris == null) ? null : iris.getExposureTime());
        }

        void update(Double val) {
            final Iris iris = getDataObject();
            Color fg = Color.black;
            String txt = "";
            if ((iris != null) && (val != null)) {
                final double min = iris.getMinimumExposureTimeSecs();
                final double rec = iris.getRecommendedExposureTimeSecs();
                final double max = iris.getFilter().exposureTimeHalfWellSecs();

                if (val < min) {
                    fg = FATAL_FG_COLOR;
                    txt = String.format("Below minimum (%.1f sec).", min);
                } else if (val < rec) {
                    fg = WARNING_FG_COLOR;
                    txt = String.format("Below recommendation (%.1f sec).", rec);
                } else if ((val > max) && (max > 0)) {
                    fg = WARNING_FG_COLOR;
                    txt = String.format("Very long exp. time (%d sec max).", Math.round(max));
                }
            }

            label.setText(txt);
            label.setForeground(fg);
        }
    }

    private final class CoaddsMessageUpdater implements EditListener<Iris, Integer>, PropertyChangeListener {
        private final JLabel label;

        CoaddsMessageUpdater() {
            this.label = new JLabel("");
        }

        JLabel getLabel() {
            return label;
        }

        public void valueChanged(EditEvent<Iris, Integer> event) {
            update(event.getNewValue());
        }

        public void propertyChange(PropertyChangeEvent evt) {
            update();
        }

        void update() {
            final Iris iris = getDataObject();
            update((iris == null) ? null : iris.getCoadds());
        }

        void update(Integer val) {
            String txt = "";
            if (val != null) {
                if (val <= 0) txt = "Coadds must be greater than 0.";
            }
            label.setText(txt);
        }
    }

    private final JPanel pan;

    private final ComboPropertyCtrl<Iris, Filter> filterCtrl;
    private final RadioPropertyCtrl<Iris, IssPort> portCtrl;
    private final RadioPropertyCtrl<Iris, ReadMode> readModeCtrl;
    private final ComboPropertyCtrl<Iris, OdgwSize> odgwSizeCtrl;
    private final ComboPropertyCtrl<Iris, UtilityWheel> utilWheelCtrl;
    private final ComboPropertyCtrl<Iris, Roi> roiCtrl;


    private final TextFieldPropertyCtrl<Iris, Double> posAngleCtrl;
    private final CheckboxEnumPropertyCtrl<Iris, PosAngleConstraint> posAngleConstraintCtrl;
    private final TextFieldPropertyCtrl<Iris, Double> exposureTimeCtrl;
    private final ExposureTimeMessageUpdater exposureTimeMessageUpdater;
    private final TextFieldPropertyCtrl<Iris, Integer> coaddsCtrl;
    private final CoaddsMessageUpdater coaddsMessageUpdater;

    private final ReadModeMessagePanel msgPanel = new ReadModeMessagePanel();

    public IrisEditor() {

        filterCtrl   = ComboPropertyCtrl.enumInstance(FILTER_PROP);
        portCtrl     = new RadioPropertyCtrl<>(PORT_PROP);
        readModeCtrl = new RadioPropertyCtrl<>(READ_MODE_PROP);
        odgwSizeCtrl = ComboPropertyCtrl.enumInstance(ODGW_SIZE_PROP);
        utilWheelCtrl= ComboPropertyCtrl.enumInstance(UTILITY_WHEEL_PROP);
        roiCtrl= ComboPropertyCtrl.enumInstance(ROI_PROP);

        // Position Angle
        PropertyDescriptor pd = Iris.POS_ANGLE_PROP;
        posAngleCtrl = TextFieldPropertyCtrl.createDoubleInstance(pd, 1);

        posAngleConstraintCtrl = new CheckboxEnumPropertyCtrl<>("Allow Auto Guide Search to select PA",
                POS_ANGLE_CONSTRAINT_PROP, PosAngleConstraint.UNBOUNDED, PosAngleConstraint.FIXED);

        // Exposure Time
        pd = Iris.EXPOSURE_TIME_PROP;
        exposureTimeMessageUpdater = new ExposureTimeMessageUpdater();
        exposureTimeCtrl = TextFieldPropertyCtrl.createDoubleInstance(pd, 1);
        exposureTimeCtrl.addEditListener(exposureTimeMessageUpdater);

        // Cooads
        pd = Iris.COADDS_PROP;
        coaddsMessageUpdater = new CoaddsMessageUpdater();
        coaddsCtrl = TextFieldPropertyCtrl.createIntegerInstance(pd);
        coaddsCtrl.addEditListener(coaddsMessageUpdater);

        pan = new JPanel(new GridBagLayout());
        initEditorPanel(pan);
    }


    private void initEditorPanel(JPanel pan) {
        pan.setBorder(PANEL_BORDER);
        addCtrl(pan, 0, 0, filterCtrl);

        posAngleCtrl.setColumns(4);
        addCtrl(pan, 0, 1, posAngleCtrl, "deg E of N");
        // Column Gap
        pan.add(new JPanel(), colGapGbc(3, 1));

        pan.add(posAngleConstraintCtrl.getComponent(), propWidgetGbc(6, 1));

        // ------ Separator --------
        pan.add(new JSeparator(JSeparator.HORIZONTAL), separatorGbc(0, 3, 7));

        exposureTimeCtrl.setColumns(4);
        pan.add(new JLabel("Exp Time"), propLabelGbc(0, 4));
        pan.add(exposureTimeCtrl.getComponent(), propWidgetGbc(1, 4));
        pan.add(new JLabel("sec"), propUnitsGbc(2, 4));
        pan.add(exposureTimeMessageUpdater.getLabel(), warningLabelGbc(0, 5, 3));

        coaddsCtrl.setColumns(3);
        addCtrl(pan, 4, 4, coaddsCtrl, "exp/obs");

        final JLabel coaddsWarning = coaddsMessageUpdater.getLabel();
        coaddsWarning.setForeground(WARNING_FG_COLOR);
        pan.add(coaddsWarning, warningLabelGbc(4, 5, 3));

        final JTabbedPane tabPane = new JTabbedPane();
        tabPane.addTab("Read Mode", getTabPanel(readModeCtrl.getComponent()));
        tabPane.addTab("ISS Port", getTabPanel(portCtrl.getComponent()));

        // Tab Pane
        pan.add(tabPane, new GridBagConstraints(){{
            gridx     = 0;    gridy      = 6;
            gridwidth = 7;    gridheight = 1;
            weightx   = 1.0;  weighty    = 0;
            anchor    = WEST; fill       = HORIZONTAL;
            insets    = new Insets(PROPERTY_ROW_GAP, 0, 0, 0);
        }});

        // Message panel.
        final Border b = new ThinBorder(BevelBorder.RAISED);
        msgPanel.setBorder(BorderFactory.createCompoundBorder(b, BorderFactory.createEmptyBorder(5, 15, 5, 5)));
        pan.add(msgPanel, new GridBagConstraints(){{
            gridx     = 0;    gridy      = 7;
            gridwidth = 7;    gridheight = 1;
            weightx   = 1.0;  weighty    = 0;
            anchor    = WEST; fill       = HORIZONTAL;
            insets    = new Insets(2, 0, 0, 0);
        }});

        // Push everything to the top left.
        pan.add(new JPanel(), pushGbc(10, 10));
    }

    public Component getEngineeringComponent() {
        final JPanel pan = new JPanel(new GridBagLayout());
        addCtrl(pan, 0, 0, odgwSizeCtrl);
        addCtrl(pan, 0, 1, utilWheelCtrl);
        addCtrl(pan, 0, 2, roiCtrl);

        pan.add(new JPanel(), new GridBagConstraints(){{
            gridx = 0; gridy = 3; weighty = 1.0; fill = VERTICAL;
        }});

        return pan;
    }

    private static JPanel getTabPanel(JComponent comp) {
        final JPanel pan = new JPanel();
        pan.setBorder(TAB_PANEL_BORDER);
        pan.setLayout(new BoxLayout(pan, BoxLayout.PAGE_AXIS));
        pan.add(comp);
        pan.add(Box.createVerticalGlue());
        return pan;
    }

    public JPanel getWindow() {
        return pan;
    }

    @Override
    protected void handlePreDataObjectUpdate(Iris iris) {
        if (iris == null) return;
        iris.removePropertyChangeListener(FILTER_PROP.getName(), msgPanel);
        iris.removePropertyChangeListener(READ_MODE_PROP.getName(), msgPanel);
        iris.removePropertyChangeListener(FILTER_PROP.getName(), exposureTimeMessageUpdater);
        iris.removePropertyChangeListener(READ_MODE_PROP.getName(), exposureTimeMessageUpdater);
    }

    @Override
    protected void handlePostDataObjectUpdate(final Iris iris) {
        filterCtrl.removeEditListener(filterChangeListener);

        posAngleCtrl.setBean(iris);
        posAngleConstraintCtrl.setBean(iris);
        exposureTimeCtrl.setBean(iris);
        coaddsCtrl.setBean(iris);
        filterCtrl.setBean(iris);
        portCtrl.setBean(iris);

        odgwSizeCtrl.setBean(iris);
        utilWheelCtrl.setBean(iris);
        roiCtrl.setBean(iris);

        readModeCtrl.setBean(iris);

        filterCtrl.addEditListener(filterChangeListener);

        iris.addPropertyChangeListener(FILTER_PROP.getName(), msgPanel);
        iris.addPropertyChangeListener(READ_MODE_PROP.getName(), msgPanel);
        iris.addPropertyChangeListener(FILTER_PROP.getName(), exposureTimeMessageUpdater);
        iris.addPropertyChangeListener(READ_MODE_PROP.getName(), exposureTimeMessageUpdater);
        msgPanel.update();
        exposureTimeMessageUpdater.update();
    }
}
