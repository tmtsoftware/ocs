package jsky.app.ot.gemini.nfiraos;

import edu.gemini.pot.sp.ISPObsComponent;
import edu.gemini.spModel.gemini.nfiraos.Nfiraos;
import edu.gemini.spModel.type.DisplayableSpType;
import jsky.app.ot.editor.OtItemEditor;

import javax.swing.*;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

/**
 * User interface and editor for Nfiraos.
 */
public final class NfiraosEditor extends OtItemEditor<ISPObsComponent, Nfiraos> {

    private final JPanel pan;
//    private final JCheckBox adcCheckbox;
    private final OptionsBox<Nfiraos.DichroicBeamsplitter> bsBox;
    private final OptionsBox<Nfiraos.AstrometricMode> amBox;

    public NfiraosEditor() {
        pan = new JPanel(new GridBagLayout());
        pan.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        final GridBagConstraints gbc = new GridBagConstraints();

        // Add the labels.
        gbc.gridx   = 0;
        gbc.anchor  = GridBagConstraints.NORTHEAST;
        gbc.insets  = new Insets(0, 0, 10, 10);
//        gbc.gridy = 0; pan.add(new JLabel("Atmospheric Dispersion Corrector"), gbc);
        gbc.gridy = 1; pan.add(new JLabel("Dichroic Beamsplitter"), gbc);
        gbc.gridy = 2; pan.add(new JLabel("Astrometric Mode"), gbc);


        // Add the widgets.
        gbc.gridx   = 1;
        gbc.anchor  = GridBagConstraints.NORTHWEST;
        gbc.insets  = new Insets(0, 0, 10, 0);
//        gbc.gridy = 0; pan.add(adcCheckbox = createAdc(), gbc);
        gbc.gridy = 1; pan.add((bsBox = createBeamsplitter()).getBox(), gbc);
        gbc.gridy = 2; pan.add((amBox = createAstrometricMode()).getBox(), gbc);

        // Push everything to the top left.
        gbc.fill    = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.gridx   = 2;
        gbc.gridy   = 3;
        pan.add(new JPanel(), gbc);
    }

//    private JCheckBox createAdc() {
//        final JCheckBox cb = new JCheckBox(Nfiraos.Adc.ON.displayValue());
//        cb.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                if (getDataObject() == null) return;
//                getDataObject().setAdc(cb.isSelected() ? Nfiraos.Adc.ON : Nfiraos.Adc.OFF);
//            }
//        });
//        return cb;
//    }

    private interface ListenerFactory<T extends DisplayableSpType> {
        ActionListener create(T t);
    }

    private static class OptionsBox<T extends DisplayableSpType> {
        private final Map<T, JRadioButton> btnMap = new HashMap<T, JRadioButton>();
        private final Box box = new Box(BoxLayout.PAGE_AXIS);
        private final ButtonGroup grp = new ButtonGroup();

        public Box getBox() {
            return box;
        }

        public void create(T t, ListenerFactory<T> fact) {
            final JRadioButton btn = new JRadioButton(t.displayValue());
            btn.addActionListener(fact.create(t));
            grp.add(btn);
            box.add(btn);
            btnMap.put(t, btn);
        }

        public JRadioButton get(T t) {
            return btnMap.get(t);
        }
    }

    private <T extends DisplayableSpType> OptionsBox<T> createOptionsBox(Class<T> tClass, ListenerFactory<T> fact) {
        final OptionsBox<T> box = new OptionsBox<T>();
        final T[] tArray = tClass.getEnumConstants();
        for (final T t : tArray) {
            box.create(t, fact);
        }
        return box;
    }

    private OptionsBox<Nfiraos.DichroicBeamsplitter> createBeamsplitter() {
        final Class<Nfiraos.DichroicBeamsplitter> c = Nfiraos.DichroicBeamsplitter.class;
        return createOptionsBox(c, new ListenerFactory<Nfiraos.DichroicBeamsplitter>() {
            public ActionListener create(final Nfiraos.DichroicBeamsplitter bs) {
                return new ActionListener() {
                    public void actionPerformed(ActionEvent evt) {
                        if (getDataObject() == null) return;
                        getDataObject().setDichroicBeamsplitter(bs);
                    }
                };
            }
        });
    }

    private OptionsBox<Nfiraos.AstrometricMode> createAstrometricMode() {
        final Class<Nfiraos.AstrometricMode> c = Nfiraos.AstrometricMode.class;
        return createOptionsBox(c, new ListenerFactory<Nfiraos.AstrometricMode>() {
            public ActionListener create(final Nfiraos.AstrometricMode am) {
                return new ActionListener() {
                    public void actionPerformed(ActionEvent evt) {
                        if (getDataObject() == null) return;
                        getDataObject().setAstrometricMode(am);
                    }
                };
            }
        });
    }

    public JPanel getWindow() {
        return pan;
    }

    public void init() {
//        adcCheckbox.setSelected(getDataObject().getAdc() == Nfiraos.Adc.ON);
        bsBox.get(getDataObject().getDichroicBeamsplitter()).setSelected(true);
        amBox.get(getDataObject().getAstrometricMode()).setSelected(true);
    }
}
