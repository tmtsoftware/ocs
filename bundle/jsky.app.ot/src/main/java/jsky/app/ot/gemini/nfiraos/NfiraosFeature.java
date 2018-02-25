package jsky.app.ot.gemini.nfiraos;

import edu.gemini.shared.util.immutable.Option;
import edu.gemini.shared.util.immutable.Some;
import edu.gemini.shared.util.immutable.None;
import edu.gemini.skycalc.Angle;
import edu.gemini.skycalc.Offset;
import edu.gemini.spModel.gemini.nfiraos.NfiraosOiwfs;
import edu.gemini.spModel.guide.GuideProbe;
import edu.gemini.spModel.obs.context.ObsContext;
import edu.gemini.spModel.obscomp.SPInstObsComp;
import edu.gemini.spModel.target.SPTarget;
import edu.gemini.spModel.target.WatchablePos;
import edu.gemini.spModel.target.env.GuideProbeTargets;
import edu.gemini.spModel.target.env.TargetEnvironment;
import edu.gemini.spModel.target.env.TargetEnvironmentDiff;
import edu.gemini.spModel.target.offset.OffsetPosBase;
import jsky.app.ot.gemini.inst.OiwfsPlotFeature$;
import jsky.app.ot.tpe.*;
import jsky.app.ot.tpe.feat.TpeGuidePosCreatableItem;
import jsky.app.ot.util.BasicPropertyList;
import jsky.app.ot.util.OtColor;
import jsky.app.ot.util.PropertyWatcher;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.Collections;


/**
 * Draws the Nfiraos AO field of view and probe ranges.
 */
public final class NfiraosFeature extends TpeImageFeature implements PropertyWatcher, TpeModeSensitive, TpeDragSensitive {

  private AffineTransform trans;
  private boolean isEmpty;

  // Color for AO WFS limit.
  private static final Color AO_FOV_COLOR = Color.RED;
  private static final Color PROBE_RANGE_COLOR = OtColor.SALMON;

  // Composite used for drawing items that block the view
  private static final Composite BLOCKED = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5F);

  // Property used to control drawing of the probe ranges.
  private static final BasicPropertyList props = new BasicPropertyList(NfiraosFeature.class.getName());
  private static final String PROP_SHOW_RANGES = "Show Probe Ranges";

  static {
    props.registerBooleanProperty(PROP_SHOW_RANGES, true);
  }

//    // XXX TODO FIXME: Is this needed?
    private enum RangeDisplayMode {
        probe1(true, false, false),
        probe2(false, true, false),
        probe3(false, false, true),
        all(true, true, true);

        private final boolean show1;
        private final boolean show2;
        private final boolean show3;

        RangeDisplayMode(boolean show1, boolean show2, boolean show3) {
            this.show1 = show1;
            this.show2 = show2;
            this.show3 = show3;
        }

        boolean show1() { return show1; }
        boolean show2() { return show2; }
        boolean show3() { return show3; }
    }

    private RangeDisplayMode rangeMode = RangeDisplayMode.all;


  /**
   * Construct the feature with its name and description.
   */
  public NfiraosFeature() {
    super("Nfiraos", "Show the field of view of the Nfiraos WFS probes.");
  }

  /**
   * The position angle has changed.
   */
  public void posAngleUpdate(TpeImageInfo tii) {
    reinit();
  }

  /**
   * A property has changed.
   *
   * @see PropertyWatcher
   */
  public void propertyChange(String propName) {
    _iw.repaint();
  }

  /**
   * Override getProperties to return the properties supported by this
   * feature.
   */
  @Override
  public BasicPropertyList getProperties() {
    return props;
  }

  /**
   * Turn on/off the drawing of probe ranges.
   */
  public void setDrawProbeRanges(boolean draw) {
    props.setBoolean(PROP_SHOW_RANGES, draw);
  }

  /**
   * Gets the drawing of probe ranges.
   */
  private boolean getDrawProbeRanges() {
    return props.getBoolean(PROP_SHOW_RANGES, true);
  }

  private PropertyChangeListener selListener = evt -> _redraw();

  /**
   * Reinitialize (recalculate the positions and redraw).
   */
  public void reinit(TpeImageWidget iw, TpeImageInfo tii) {
    _stopMonitorOffsetSelections(selListener);

    super.reinit(iw, tii);

    props.addWatcher(this);

    SPInstObsComp inst = _iw.getInstObsComp();
    if (inst == null) return;

    // arrange to be notified if telescope positions are added, removed, or selected
    _monitorPosList();

    // Monitor the selections of offset positions, since that affects the positions drawn
    _monitorOffsetSelections(selListener);

    double ppa = tii.getPixelsPerArcsec();

    // Use selected offset position, rotated by position angle, if defined
    double posAngle = tii.getCorrectedPosAngleRadians();
    double pixelsPerArcsec = tii.getPixelsPerArcsec();
    Point2D.Double baseScreenPos = tii.getBaseScreenPos();
    TpeContext ctx = _iw.getContext();
    OffsetPosBase selectedOffsetPos = ctx.offsets().selectedPosOrNull();
    double basePosX = baseScreenPos.x, basePosY = baseScreenPos.y;
    if (selectedOffsetPos != null) {
      // Get offset from base pos in pixels
      // Note that the offset positions rotate with the instrument.
      Point2D.Double p = new Point2D.Double(
          selectedOffsetPos.getXaxis() * pixelsPerArcsec,
          selectedOffsetPos.getYaxis() * pixelsPerArcsec);
      edu.gemini.spModel.util.Angle.rotatePoint(p, posAngle);
      double offsetX = p.x;
      double offsetY = p.y;
      basePosX -= offsetX;
      basePosY -= offsetY;
    }
    Point2D.Double offsetBaseScreenPos = new Point2D.Double(basePosX, basePosY);

    trans = new AffineTransform();
    trans.translate(offsetBaseScreenPos.x, offsetBaseScreenPos.y);
    // The model already used the position angle, so just rotate by the difference between north and up in the image
    trans.rotate(-tii.getTheta());
    trans.scale(ppa, ppa);
  }

  @Override
  public void unloaded() {
    props.deleteWatcher(this);
    super.unloaded();
  }

  /**
   * Implements the TelescopePosWatcher interface.
   */
  public void telescopePosLocationUpdate(WatchablePos tp) {
    _redraw();
  }

  /**
   * Implements the TelescopePosWatcher interface.
   */
  public void telescopePosGenericUpdate(WatchablePos tp) {
    _redraw();
  }

  protected void handleTargetEnvironmentUpdate(TargetEnvironmentDiff diff) {
    _redraw();
  }

  /**
   * Schedule a redraw of the image feature.
   */
  private void _redraw() {
    if (_iw != null) _iw.repaint();
  }


  // Slant of the lines drawn in the probe 1 and 2 ranges.  Slant at a
  // 45 degree angle either falling (down) \ or rising (up) /
  private enum Orientation {
    vertical,
    horizontal,
  }

  // Creates a Paint that is used for filling the probe 1 and 2 ranges.
  private static Paint createProbeRangePaint(Graphics2D g2d, Orientation s, Color color) {
    return createProbeRangePaint(g2d, s, 16, 0.16, 0.4, color);
  }

  private static Paint createProbeRangeKeyPaint(Graphics2D g2d, Orientation s, Color color) {
    return createProbeRangePaint(g2d, s, 8, 0.32, 0.8, color);
  }

  private static Paint createProbeRangePaint(Graphics2D g2d, Orientation s, int skip,
                                             double alphaBg, double alphaLine, Color color) {
    final int size = skip * 2;

    final Rectangle2D.Double rec = new Rectangle2D.Double(0, 0, size, size);

    // Get a buffered image capable of being transparent.
    BufferedImage bim = g2d.getDeviceConfiguration().createCompatibleImage(size, size, Transparency.TRANSLUCENT);
    Graphics2D bimg = bim.createGraphics();

    // Shade it with a light red color almost completely transparent.
    bimg.setColor(OtColor.makeTransparent(color, alphaBg));
    bimg.setComposite(AlphaComposite.Src);
    bimg.fill(rec);

    // Now draw the slanting lines, which are also pretty transparent
    // though not quite as much as the background.
    bimg.setClip(0, 0, size, size);
    bimg.setColor(OtColor.makeTransparent(color, alphaLine));

    if (s == Orientation.vertical) {
      for (int x = 0; x < size; x += skip) {
        bimg.drawLine(x, 0, x, size);
      }
    } else {
      for (int y = 0; y < size; y += skip) {
        bimg.drawLine(0, y, size, y);
      }
    }
    bimg.dispose();

    return new TexturePaint(bim, rec);
  }

  // If _flipRA is -1, flip the RA axis of the area
  private Area flipArea(Area a) {
    if (_flipRA == -1) {
      a = a.createTransformedArea(AffineTransform.getScaleInstance(_flipRA, 1.0));
    }
    return a;
  }

  // Rotate the selected offset position by the pos angle (Must be a better way to do this...).
  private Offset rotateByPosAngle(ObsContext ctx, OffsetPosBase selectedOffsetPos) {
    if (selectedOffsetPos == null) return new Offset(Angle.ANGLE_0DEGREES, Angle.ANGLE_0DEGREES);
    Offset offset = selectedOffsetPos.toSkycalcOffset();
    double p = offset.p().toArcsecs().getMagnitude();
    double q = offset.q().toArcsecs().getMagnitude();
    double a = ctx.getPositionAngle().toRadians();
    Point2D.Double pd = new Point2D.Double(p, q);
    edu.gemini.spModel.util.Angle.rotatePoint(pd, a);
    return new Offset(new Angle(pd.x, Angle.Unit.ARCSECS), new Angle(pd.y, Angle.Unit.ARCSECS));
  }

  /**
   * Draw the feature.
   */
  public void draw(Graphics g, TpeImageInfo tii) {
    TpeContext tpeCtx = _iw.getContext();

    if (!isEnabled(tpeCtx)) return;
    if (trans == null) return;

    Graphics2D g2d = (Graphics2D) g;
    Color oldColor = g2d.getColor();

    Option<ObsContext> ctxOpt = _iw.getObsContext();
    if (ctxOpt.isEmpty()) return;
    ObsContext ctx = ctxOpt.getValue();

    // Draw the AO window itself.  A circle.
    Area a = new Area(NfiraosOiwfs.AO_BOUNDS);
    isEmpty = a.isEmpty();
    if (isEmpty) return;

    // If aan offset pos is selected, use it
    Offset offset = rotateByPosAngle(ctx, tpeCtx.offsets().selectedPosOrNull());

    Shape s = trans.createTransformedShape(flipArea(a));
    g2d.setColor(AO_FOV_COLOR);
    g2d.draw(s);

    // Draw the probe ranges.
    if (getDrawProbeRanges()) {
      Area a1 = new Area(flipArea(NfiraosOiwfs.Wfs.oiwfs1.probeRange(ctx, Offset.ZERO_OFFSET))).createTransformedArea(trans);
      Area a2 = new Area(flipArea(NfiraosOiwfs.Wfs.oiwfs2.probeRange(ctx, Offset.ZERO_OFFSET))).createTransformedArea(trans);
      Area a3 = new Area(flipArea(NfiraosOiwfs.Wfs.oiwfs3.probeRange(ctx, Offset.ZERO_OFFSET))).createTransformedArea(trans);

      Stroke oldStroke = g2d.getStroke();
      g2d.setStroke(OiwfsPlotFeature$.MODULE$.ThickDashedStroke());

      g2d.setColor(OtColor.makeTransparent(AO_FOV_COLOR, 0.7));
      g2d.draw(a1);

      g2d.setColor(OtColor.makeTransparent(AO_FOV_COLOR, 0.7));
      g2d.draw(a2);

      g2d.setColor(OtColor.makeTransparent(AO_FOV_COLOR, 0.7));
      g2d.draw(a3);

//      Paint p = g2d.getPaint();
//      g2d.setPaint(createProbeRangePaint(g2d, Orientation.horizontal, PROBE_RANGE_COLOR));
//      g2d.fill(a1);
//
//      g2d.setPaint(createProbeRangePaint(g2d, Orientation.vertical, PROBE_RANGE_COLOR));
//      g2d.fill(a2);
//
//      g2d.setPaint(createProbeRangePaint(g2d, Orientation.vertical, PROBE_RANGE_COLOR));
//      g2d.fill(a3);

//      g2d.setPaint(p);

      g2d.setStroke(oldStroke);
    }

    drawProbeArm(g2d, ctx, NfiraosOiwfs.Wfs.oiwfs1, offset);
    drawProbeArm(g2d, ctx, NfiraosOiwfs.Wfs.oiwfs2, offset);
    drawProbeArm(g2d, ctx, NfiraosOiwfs.Wfs.oiwfs3, offset);

    g2d.setColor(oldColor);
  }

  // draw the probe arm for the given wfs
  private void drawProbeArm(Graphics2D g2d, ObsContext ctx, NfiraosOiwfs.Wfs wfs, Offset offset) {
    wfs.probeArm(ctx, true, offset).foreach(a -> {
      if (a != null) {
        Shape s = trans.createTransformedShape(flipArea(a));
        g2d.setColor(AO_FOV_COLOR);
        g2d.draw(s);
        Composite c = g2d.getComposite();
        g2d.setComposite(BLOCKED);
        g2d.fill(s);
        g2d.setComposite(c);
      }
    });
  }

  @Override
  public boolean isEnabled(TpeContext ctx) {
    return super.isEnabled(ctx) && ctx.nfiraos().isDefined();
  }

  private void setRangeDisplayMode(RangeDisplayMode mode) {
    if (rangeMode == mode) return;
    rangeMode = mode;
    _redraw();
  }

  public void handleModeChange(TpeMode mode, Option<Object> arg) {
    if ((mode != TpeMode.CREATE) || arg.isEmpty()) {
      setRangeDisplayMode(RangeDisplayMode.all);
      return;
    }

    Object value = arg.getValue();
    if (!(value instanceof TpeGuidePosCreatableItem)) {
      setRangeDisplayMode(RangeDisplayMode.all);
      return;
    }

    TpeGuidePosCreatableItem item = (TpeGuidePosCreatableItem) value;
    GuideProbe guider = item.getGuideProbe();
    if (guider == NfiraosOiwfs.Wfs.oiwfs1) {
      setRangeDisplayMode(RangeDisplayMode.probe1);
    } else if (guider == NfiraosOiwfs.Wfs.oiwfs2) {
      setRangeDisplayMode(RangeDisplayMode.probe2);
    } else {
      setRangeDisplayMode(RangeDisplayMode.all);
    }
  }

  private static boolean containsTarget(TargetEnvironment env, GuideProbe guider, SPTarget target) {
    final Option<GuideProbeTargets> gtOpt = env.getPrimaryGuideProbeTargets(guider);
    return gtOpt.exists(gt -> gt.containsTarget(target));
  }

  public void handleDragStarted(Object dragObject, ObsContext context) {
    if (!(dragObject instanceof SPTarget)) return;

    SPTarget target = (SPTarget) dragObject;
    TargetEnvironment env = context.getTargets();
    if (env == null) {
      setRangeDisplayMode(RangeDisplayMode.all);
      return;
    }

    if (containsTarget(env, NfiraosOiwfs.Wfs.oiwfs1, target)) {
      setRangeDisplayMode(RangeDisplayMode.probe1);
    } else if (containsTarget(env, NfiraosOiwfs.Wfs.oiwfs2, target)) {
      setRangeDisplayMode(RangeDisplayMode.probe2);
    } else {
      setRangeDisplayMode(RangeDisplayMode.all);
    }
  }

  public void handleDragStopped(ObsContext context) {
    setRangeDisplayMode(RangeDisplayMode.all);
  }

  private static class ProbeRangeIcon implements Icon {
    private final Orientation[] slants;

    ProbeRangeIcon(Orientation... slants) {
      this.slants = slants;
    }

    public int getIconWidth() {
      return 18;
    }

    public int getIconHeight() {
      return 18;
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2d = (Graphics2D) g;
      g2d.setColor(Color.black);
      g2d.fill(new Rectangle2D.Double(1, 1, 16, 16));

      Paint origPaint = g2d.getPaint();
      for (Orientation slant : slants) {
        Paint p = createProbeRangeKeyPaint(g2d, slant, PROBE_RANGE_COLOR);
        g2d.setPaint(p);
        g2d.fill(new Rectangle2D.Double(1, 1, 16, 16));
      }
      g2d.setPaint(origPaint);
    }
  }

  @Override
  public Option<Component> getKey() {
    JPanel pan = new JPanel(new GridBagLayout());

    pan.add(new JLabel("OIWFS1", new ProbeRangeIcon(Orientation.horizontal), JLabel.LEFT) {{
              setForeground(Color.black);
            }},
        new GridBagConstraints() {{
          gridx = 0;
          gridy = 0;
          anchor = WEST;
          fill = HORIZONTAL;
        }}
    );
    pan.add(new JLabel("OIWFS2", new ProbeRangeIcon(Orientation.vertical), JLabel.LEFT) {{
              setForeground(Color.black);
            }},
        new GridBagConstraints() {{
          gridx = 1;
          gridy = 0;
          anchor = WEST;
          fill = HORIZONTAL;
        }}
    );
    pan.add(new JLabel("Both", new ProbeRangeIcon(Orientation.horizontal, Orientation.vertical), JLabel.LEFT) {{
              setForeground(Color.black);
            }},
        new GridBagConstraints() {{
          gridx = 2;
          gridy = 0;
          anchor = WEST;
          fill = HORIZONTAL;
        }}
    );

    return new Some<>(pan);
  }

  public TpeImageFeatureCategory getCategory() {
    return TpeImageFeatureCategory.fieldOfView;
  }

  private static final TpeMessage WARNING = TpeMessage.warningMessage(
      "No valid region for OIWFS stars.  Check offset positions.");

  public Option<Collection<TpeMessage>> getMessages() {
    if (!isEmpty) return None.instance();
    return new Some<>(Collections.singletonList(WARNING));
  }

}