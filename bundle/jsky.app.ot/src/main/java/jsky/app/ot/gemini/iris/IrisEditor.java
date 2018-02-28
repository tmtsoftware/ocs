package jsky.app.ot.gemini.iris;

import com.jgoodies.forms.factories.DefaultComponentFactory;
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
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

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
//        final Filter f = evt.getNewValue();
//        if (f == null) return;
//
//        final ReadMode readMode = f.readMode();
//        if (readMode == null) return;
//        getDataObject().setReadMode(readMode);
    };

    private final EditListener<Iris, Iris.Grating> gratingChangeListener = evt -> {
    };

    private final EditListener<Iris, Iris.Detector> detectorChangeListener = evt -> {
        boolean wasIfs = evt.getOldValue() != Detector.IMAGER_ONLY;
        boolean isIfs = evt.getNewValue() != Detector.IMAGER_ONLY;
        if (wasIfs != isIfs) setIfsEnabled(isIfs);
    };

    // If isIfs is true, enable the IFS controls and disable the imager controls, otherwise do the opposite.
    private void setIfsEnabled(boolean isIfs) {
        scaleCtrl.getComponent().setEnabled(isIfs);
        gratingCtrl.getComponent().setEnabled(isIfs);
        ifsExposureTimeCtrl.getComponent().setEnabled(isIfs);
        ifsCoaddsCtrl.getComponent().setEnabled(isIfs);
        updateEnabledState(ifsReadModeCtrl.getComponent().getComponents(), isIfs);

        imagerExposureTimeCtrl.getComponent().setEnabled(!isIfs);
        imagerCoaddsCtrl.getComponent().setEnabled(!isIfs);
        updateEnabledState(imagerReadModeCtrl.getComponent().getComponents(), !isIfs);
    }

    private final EditListener<Iris, Iris.Scale> scaleChangeListener = evt -> {
    };

    private final class ImagerExposureTimeMessageUpdater implements EditListener<Iris, Double>, PropertyChangeListener {
        private final JLabel label;

        ImagerExposureTimeMessageUpdater() {
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
//                final double max = iris.getFilter().exposureTimeHalfWellSecs();

                if (val < min) {
                    fg = FATAL_FG_COLOR;
                    txt = String.format("Below minimum (%.1f sec).", min);
                } else if (val < rec) {
                    fg = WARNING_FG_COLOR;
                    txt = String.format("Below recommendation (%.1f sec).", rec);
                }
//                else if ((val > max) && (max > 0)) {
//                    fg = WARNING_FG_COLOR;
//                    txt = String.format("Very long exp. time (%d sec max).", Math.round(max));
//                }
            }

            label.setText(txt);
            label.setForeground(fg);
        }
    }

    private final class IfsExposureTimeMessageUpdater implements EditListener<Iris, Double>, PropertyChangeListener {
        private final JLabel label;

        IfsExposureTimeMessageUpdater() {
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
//                final double max = iris.getFilter().exposureTimeHalfWellSecs();

                if (val < min) {
                    fg = FATAL_FG_COLOR;
                    txt = String.format("Below minimum (%.1f sec).", min);
                } else if (val < rec) {
                    fg = WARNING_FG_COLOR;
                    txt = String.format("Below recommendation (%.1f sec).", rec);
                }
//                else if ((val > max) && (max > 0)) {
//                    fg = WARNING_FG_COLOR;
//                    txt = String.format("Very long exp. time (%d sec max).", Math.round(max));
//                }
            }

            label.setText(txt);
            label.setForeground(fg);
        }
    }

    private final class ImagerCoaddsMessageUpdater implements EditListener<Iris, Integer>, PropertyChangeListener {
        private final JLabel label;

        ImagerCoaddsMessageUpdater() {
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

    private final class IfsCoaddsMessageUpdater implements EditListener<Iris, Integer>, PropertyChangeListener {
        private final JLabel label;

        IfsCoaddsMessageUpdater() {
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
    private final DefaultComponentFactory compFactory;

    private final ComboPropertyCtrl<Iris, Filter> filterCtrl;
    private final ComboPropertyCtrl<Iris, Grating> gratingCtrl;
    private final ComboPropertyCtrl<Iris, Detector> detectorCtrl;
    private final ComboPropertyCtrl<Iris, Scale> scaleCtrl;
    private final RadioPropertyCtrl<Iris, IssPort> portCtrl;
    private final RadioPropertyCtrl<Iris, ReadMode> imagerReadModeCtrl;
    private final RadioPropertyCtrl<Iris, ReadMode> ifsReadModeCtrl;
    private final ComboPropertyCtrl<Iris, OdgwSize> odgwSizeCtrl;
    private final ComboPropertyCtrl<Iris, UtilityWheel> utilWheelCtrl;
    private final ComboPropertyCtrl<Iris, Roi> roiCtrl;


    private final TextFieldPropertyCtrl<Iris, Double> posAngleCtrl;
    private final CheckboxEnumPropertyCtrl<Iris, PosAngleConstraint> posAngleConstraintCtrl;
    private final CheckboxEnumPropertyCtrl<Iris, Adc> adcCtrl;

    private final JComponent imagerSeparator;
    private final JComponent ifsSeparator;

    private final TextFieldPropertyCtrl<Iris, Double> imagerExposureTimeCtrl;
    private final ImagerExposureTimeMessageUpdater imagerExposureTimeMessageUpdater;
    private final TextFieldPropertyCtrl<Iris, Integer> imagerCoaddsCtrl;
    private final ImagerCoaddsMessageUpdater imagerCoaddsMessageUpdater;
    private final ReadModeMessagePanel imagerMsgPanel = new ReadModeMessagePanel();

    private final TextFieldPropertyCtrl<Iris, Double> ifsExposureTimeCtrl;
    private final IfsExposureTimeMessageUpdater ifsExposureTimeMessageUpdater;
    private final TextFieldPropertyCtrl<Iris, Integer> ifsCoaddsCtrl;
    private final IfsCoaddsMessageUpdater ifsCoaddsMessageUpdater;
    private final ReadModeMessagePanel ifsMsgPanel = new ReadModeMessagePanel();

    public IrisEditor() {
        filterCtrl   = ComboPropertyCtrl.enumInstance(FILTER_PROP);
        gratingCtrl   = ComboPropertyCtrl.enumInstance(GRATING_PROP);
        detectorCtrl = ComboPropertyCtrl.enumInstance(DETECTOR_PROP);
        scaleCtrl = ComboPropertyCtrl.enumInstance(SCALE_PROP);
        portCtrl     = new RadioPropertyCtrl<>(PORT_PROP);
        imagerReadModeCtrl = new RadioPropertyCtrl<>(READ_MODE_PROP);
        ifsReadModeCtrl = new RadioPropertyCtrl<>(IFS_READ_MODE_PROP);
        odgwSizeCtrl = ComboPropertyCtrl.enumInstance(ODGW_SIZE_PROP);
        utilWheelCtrl= ComboPropertyCtrl.enumInstance(UTILITY_WHEEL_PROP);
        roiCtrl= ComboPropertyCtrl.enumInstance(ROI_PROP);
        compFactory = DefaultComponentFactory.getInstance();

        // Position Angle
        posAngleCtrl = TextFieldPropertyCtrl.createDoubleInstance(Iris.POS_ANGLE_PROP, 1);

        posAngleConstraintCtrl = new CheckboxEnumPropertyCtrl<>("Allow Auto Guide Search to select PA",
                POS_ANGLE_CONSTRAINT_PROP, PosAngleConstraint.UNBOUNDED, PosAngleConstraint.FIXED);

        adcCtrl = new CheckboxEnumPropertyCtrl<>("Atmospheric Dispersion Corrector",
                ADC_PROP, Adc.ON, Adc.OFF);

        imagerSeparator = compFactory.createSeparator("Imager");
        ifsSeparator = compFactory.createSeparator("IFS");


        // Exposure Time
        imagerExposureTimeMessageUpdater = new ImagerExposureTimeMessageUpdater();
        imagerExposureTimeCtrl = TextFieldPropertyCtrl.createDoubleInstance(Iris.EXPOSURE_TIME_PROP, 1);
        imagerExposureTimeCtrl.addEditListener(imagerExposureTimeMessageUpdater);

        ifsExposureTimeMessageUpdater = new IfsExposureTimeMessageUpdater();
        ifsExposureTimeCtrl = TextFieldPropertyCtrl.createDoubleInstance(Iris.IFS_EXPOSURE_TIME_PROP, 1);
        ifsExposureTimeCtrl.addEditListener(ifsExposureTimeMessageUpdater);

        // Cooads
        imagerCoaddsMessageUpdater = new ImagerCoaddsMessageUpdater();
        imagerCoaddsCtrl = TextFieldPropertyCtrl.createIntegerInstance(Iris.COADDS_PROP);
        imagerCoaddsCtrl.addEditListener(imagerCoaddsMessageUpdater);

        ifsCoaddsMessageUpdater = new IfsCoaddsMessageUpdater();
        ifsCoaddsCtrl = TextFieldPropertyCtrl.createIntegerInstance(Iris.IFS_COADDS_PROP);
        ifsCoaddsCtrl.addEditListener(ifsCoaddsMessageUpdater);

        pan = new JPanel(new GridBagLayout());
        initEditorPanel(pan);
    }


    private void initEditorPanel(JPanel pan) {
        int row = 0;
        int col = 0;
        pan.setBorder(PANEL_BORDER);
        addCtrl(pan, col, row, filterCtrl);
        pan.add(adcCtrl.getComponent(), propWidgetGbc(6, row));
        row++;

        posAngleCtrl.setColumns(4);
        addCtrl(pan, col, row, posAngleCtrl, "deg E of N");
        // Column Gap
        pan.add(new JPanel(), colGapGbc(col+3, row));
        pan.add(posAngleConstraintCtrl.getComponent(), propWidgetGbc(6, row));
        row++;

        addCtrl(pan, col, row, detectorCtrl);
        row++;

        // ------ Separator --------
        pan.add(new JSeparator(JSeparator.HORIZONTAL), separatorGbc(col, row, 8));
        pan.add(new JPanel(), colGapGbc(col+4, row));
        row++;

        pan.add(imagerSeparator, separatorGbc(col, row, 3));
        pan.add(ifsSeparator, separatorGbc(col+5, row, 4));
        row++;

        final int imagerIfsStartRow = row;

        // ------ Imager --------
        row++;  // Stay on same row as IFS exposure time
        row++;

        imagerExposureTimeCtrl.setColumns(4);
        pan.add(new JLabel("Exp Time"), propLabelGbc(col, row));
        pan.add(imagerExposureTimeCtrl.getComponent(), propWidgetGbc(col+1, row));
        pan.add(new JLabel("sec"), propUnitsGbc(col+2, row));
        row++;

        pan.add(imagerExposureTimeMessageUpdater.getLabel(), warningLabelGbc(col, row, 3));
        row++;

        imagerCoaddsCtrl.setColumns(3);
        addCtrl(pan, col, row, imagerCoaddsCtrl, "exp/obs");
        row++;

        final JLabel imagerCoaddsWarning = imagerCoaddsMessageUpdater.getLabel();
        imagerCoaddsWarning.setForeground(WARNING_FG_COLOR);
        pan.add(imagerCoaddsWarning, warningLabelGbc(col, row, 3));
        row++;

        final JTabbedPane imagerTabPane = new JTabbedPane();
        imagerTabPane.addTab("Read Mode", getTabPanel(imagerReadModeCtrl.getComponent()));
//        imagerTabPane.addTab("ISS Port", getTabPanel(portCtrl.getComponent()));

        // Tab Pane
        final int imagerTabRow = row;
        final int imagerTabCol = col;
        pan.add(imagerTabPane, new GridBagConstraints(){{
            gridx     = imagerTabCol;    gridy      = imagerTabRow;
            gridwidth = 3;    gridheight = 1;
            weightx   = 1.0;  weighty    = 0;
            anchor    = WEST; fill       = HORIZONTAL;
            insets    = new Insets(PROPERTY_ROW_GAP, 0, 0, 0);
        }});
        row++;

        // Message panel.
        final int imagerMsgRow = row;
        final int imagerMsgCol = col;
        imagerMsgPanel.setBorder(BorderFactory.createCompoundBorder(new ThinBorder(BevelBorder.RAISED),
            BorderFactory.createEmptyBorder(5, 15, 5, 5)));
        pan.add(imagerMsgPanel, new GridBagConstraints(){{
            gridx     = imagerMsgCol;    gridy      = imagerMsgRow;
            gridwidth = 3;    gridheight = 1;
            weightx   = 1.0;  weighty    = 0;
            anchor    = WEST; fill       = HORIZONTAL;
            insets    = new Insets(2, 0, 0, 0);
        }});


        // ------ IFS --------
        row = imagerIfsStartRow;
        col = 5;

        addCtrl(pan, col, row, scaleCtrl);
        row++;

        addCtrl(pan, col, row, gratingCtrl);
        row++;

        ifsExposureTimeCtrl.setColumns(4);
        pan.add(new JLabel("Exp Time"), propLabelGbc(col, row));
        pan.add(ifsExposureTimeCtrl.getComponent(), propWidgetGbc(col+1, row));
        pan.add(new JLabel("sec"), propUnitsGbc(col+2, row));
        row++;

        pan.add(ifsExposureTimeMessageUpdater.getLabel(), warningLabelGbc(col, row, 3));
        row++;

        ifsCoaddsCtrl.setColumns(3);
        addCtrl(pan, col, row, ifsCoaddsCtrl, "exp/obs");
        row++;

        final JLabel ifsCoaddsWarning = ifsCoaddsMessageUpdater.getLabel();
        ifsCoaddsWarning.setForeground(WARNING_FG_COLOR);
        pan.add(ifsCoaddsWarning, warningLabelGbc(col, row, 3));
        row++;

        final JTabbedPane ifsTabPane = new JTabbedPane();
        ifsTabPane.addTab("Read Mode", getTabPanel(ifsReadModeCtrl.getComponent()));
//        ifsTabPane.addTab("ISS Port", getTabPanel(portCtrl.getComponent()));

        // Tab Pane
        final int ifsTabRow = row;
        final int ifsTabCol = col;
        pan.add(ifsTabPane, new GridBagConstraints(){{
            gridx     = ifsTabCol;    gridy      = ifsTabRow;
            gridwidth = 3;    gridheight = 1;
            weightx   = 1.0;  weighty    = 0;
            anchor    = WEST; fill       = HORIZONTAL;
            insets    = new Insets(PROPERTY_ROW_GAP, 0, 0, 0);
        }});
        row++;

        // Message panel.
        final int ifsMsgRow = row;
        final int ifsMsgCol = col;
        ifsMsgPanel.setBorder(BorderFactory.createCompoundBorder(new ThinBorder(BevelBorder.RAISED),
            BorderFactory.createEmptyBorder(5, 15, 5, 5)));
        pan.add(ifsMsgPanel, new GridBagConstraints(){{
            gridx     = ifsMsgCol;    gridy      = ifsMsgRow;
            gridwidth = 3;    gridheight = 1;
            weightx   = 1.0;  weighty    = 0;
            anchor    = WEST; fill       = HORIZONTAL;
            insets    = new Insets(2, 0, 0, 0);
        }});
        row++;

        // Push everything to the top left.
        pan.add(new JPanel(), pushGbc(10, row));
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
        iris.removePropertyChangeListener(FILTER_PROP.getName(), imagerMsgPanel);
        iris.removePropertyChangeListener(GRATING_PROP.getName(), imagerMsgPanel);
        iris.removePropertyChangeListener(READ_MODE_PROP.getName(), imagerMsgPanel);
        iris.removePropertyChangeListener(IFS_READ_MODE_PROP.getName(), ifsMsgPanel);
        iris.removePropertyChangeListener(FILTER_PROP.getName(), imagerExposureTimeMessageUpdater);
        iris.removePropertyChangeListener(READ_MODE_PROP.getName(), imagerExposureTimeMessageUpdater);
        iris.removePropertyChangeListener(IFS_READ_MODE_PROP.getName(), ifsExposureTimeMessageUpdater);
    }

    @Override
    protected void handlePostDataObjectUpdate(final Iris iris) {
        filterCtrl.removeEditListener(filterChangeListener);
        gratingCtrl.removeEditListener(gratingChangeListener);
        detectorCtrl.removeEditListener(detectorChangeListener);
        scaleCtrl.removeEditListener(scaleChangeListener);

        posAngleCtrl.setBean(iris);
        posAngleConstraintCtrl.setBean(iris);
        adcCtrl.setBean(iris);

        imagerExposureTimeCtrl.setBean(iris);
        imagerCoaddsCtrl.setBean(iris);

        ifsExposureTimeCtrl.setBean(iris);
        ifsCoaddsCtrl.setBean(iris);

        filterCtrl.setBean(iris);
        gratingCtrl.setBean(iris);
        detectorCtrl.setBean(iris);
        scaleCtrl.setBean(iris);
        portCtrl.setBean(iris);

        odgwSizeCtrl.setBean(iris);
        utilWheelCtrl.setBean(iris);
        roiCtrl.setBean(iris);

        imagerReadModeCtrl.setBean(iris);
        ifsReadModeCtrl.setBean(iris);

        filterCtrl.addEditListener(filterChangeListener);
        gratingCtrl.addEditListener(gratingChangeListener);
        detectorCtrl.addEditListener(detectorChangeListener);
        scaleCtrl.addEditListener(scaleChangeListener);

        // XXX TODO: Add grating listener for ifs...
        iris.addPropertyChangeListener(FILTER_PROP.getName(), imagerMsgPanel);
        iris.addPropertyChangeListener(READ_MODE_PROP.getName(), imagerMsgPanel);
        iris.addPropertyChangeListener(IFS_READ_MODE_PROP.getName(), ifsMsgPanel);
        iris.addPropertyChangeListener(FILTER_PROP.getName(), imagerExposureTimeMessageUpdater);
        iris.addPropertyChangeListener(READ_MODE_PROP.getName(), imagerExposureTimeMessageUpdater);
        iris.addPropertyChangeListener(IFS_READ_MODE_PROP.getName(), ifsExposureTimeMessageUpdater);
        imagerMsgPanel.update();
        imagerExposureTimeMessageUpdater.update();

        setIfsEnabled(iris.getDetector() != Detector.IMAGER_ONLY);
    }
}
