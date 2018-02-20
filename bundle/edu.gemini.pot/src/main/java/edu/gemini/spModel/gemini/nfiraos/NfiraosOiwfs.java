package edu.gemini.spModel.gemini.nfiraos;

import edu.gemini.shared.util.immutable.*;
import edu.gemini.skycalc.Angle;
import edu.gemini.skycalc.CoordinateDiff;
import edu.gemini.skycalc.Coordinates;
import edu.gemini.skycalc.Offset;
import edu.gemini.spModel.core.BandsList;
import edu.gemini.spModel.core.RBandsList;
import edu.gemini.spModel.nfiraos.NfiraosGuideProbeGroup;
import edu.gemini.spModel.guide.*;
import edu.gemini.spModel.obs.context.ObsContext;
import edu.gemini.spModel.target.SPTarget;
import edu.gemini.spModel.target.env.Asterism;
import edu.gemini.spModel.target.env.GuideProbeTargets;
import edu.gemini.spModel.target.env.TargetEnvironment;
import edu.gemini.spModel.telescope.IssPort;
import edu.gemini.shared.util.immutable.Pair;

import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/**
 * A description of the relevant features of Nfiraos.
 */
public enum NfiraosOiwfs {
  instance;

  /**
   * Nfiraos guide probe options.
   */
  public enum Wfs implements GuideProbe, ValidatableGuideProbe, OffsetValidatingGuideProbe {
    oiwfs1(1) {
      @Override
      public Area probeRange(ObsContext ctx) {
        return NfiraosOiwfs.instance.probeRange1(ctx);
      }

      @Override
      public Option<Area> probeArm(ObsContext ctx, boolean validate) {
        return NfiraosOiwfs.instance.probeArm1(ctx, validate);
      }

      @Override
      protected double getArmAngle(ObsContext ctx) {
        return Math.toRadians(90.0);
      }

      // not implemented yet, return an empty area
      @Override
      public PatrolField getPatrolField() {
        return new PatrolField(new Area());
      }

      @Override
      public Option<PatrolField> getCorrectedPatrolField(ObsContext ctx) {
        return correctedPatrolField(ctx);
      }

      @Override
      public BandsList getBands() {
        return RBandsList.instance();
      }
    },

    oiwfs2(2) {
      @Override
      public Area probeRange(ObsContext ctx) {
        return NfiraosOiwfs.instance.probeRange2(ctx);
      }

      @Override
      public Option<Area> probeArm(ObsContext ctx, boolean validate) {
        return NfiraosOiwfs.instance.probeArm2(ctx, validate);
      }

      @Override
      public GuideStarValidation validate(SPTarget guideStar, ObsContext ctx) {
        return super.validate(guideStar, ctx)
            .and(validateVignetting(oiwfs1, guideStar, ctx));
      }

      @Override
      protected double getArmAngle(ObsContext ctx) {
        return Math.toRadians(210.0);
      }

      // not implemented yet, return an empty area
      @Override
      public PatrolField getPatrolField() {
        return new PatrolField(new Area());
      }

      @Override
      public Option<PatrolField> getCorrectedPatrolField(ObsContext ctx) {
        return correctedPatrolField(ctx);
      }

      @Override
      public BandsList getBands() {
        return RBandsList.instance();
      }
    },

    oiwfs3(3) {
      @Override
      public Area probeRange(ObsContext ctx) {
        return NfiraosOiwfs.instance.probeRange3(ctx);
      }

      @Override
      public Option<Area> probeArm(ObsContext ctx, boolean validate) {
        return NfiraosOiwfs.instance.probeArm3(ctx, validate);
      }

      @Override
      protected double getArmAngle(ObsContext ctx) {
        return Math.toRadians(330.0);
      }

      @Override
      public GuideStarValidation validate(SPTarget guideStar, ObsContext ctx) {
        return super.validate(guideStar, ctx)
            .and(validateVignetting(oiwfs1, guideStar, ctx))
            .and(validateVignetting(oiwfs2, guideStar, ctx));
      }

      // not implemented yet, return an empty area
      @Override
      public PatrolField getPatrolField() {
        return new PatrolField(new Area());
      }

      @Override
      public Option<PatrolField> getCorrectedPatrolField(ObsContext ctx) {
        return correctedPatrolField(ctx);
      }

      @Override
      public BandsList getBands() {
        return RBandsList.instance();
      }
    };

    private static Option<PatrolField> correctedPatrolField(ObsContext ctx) {
      // Not implemented yet: return an empty area.
      return ctx.getAOComponent().filter(ado -> ado instanceof Nfiraos).map(a -> new PatrolField(new Area()));
    }

    /**
     * Probe arm starting angle. PI/2 means arm comes from the right.
     * -PI/2 means it comes from the left.
     * The final angle will depend on the position angle and the port setting (side or up looking)
     *
     * @return basic probe arm angle in radians.
     */
    protected abstract double getArmAngle(ObsContext ctx);

    /**
     * Gets the group of Nfiraos guide stars.
     * See OT-21.
     */
    public enum Group implements NfiraosGuideProbeGroup {
      instance;

      public Angle getRadiusLimits() {
        return new Angle(1, Angle.Unit.ARCMINS);
      }

      public String getKey() {
        return "OIWFS";
      }

      public String getDisplayName() {
        return "Nfiraos Wave Front Sensor";
      }

      public Collection<ValidatableGuideProbe> getMembers() {
        ValidatableGuideProbe[] vals = Wfs.values();
        return Arrays.asList(vals);
      }
    }

    private int index;

    Wfs(int index) {
      this.index = index;
    }

    public String getKey() {
      return "OIWFS" + index;
    }

    public String toString() {
      return getKey();
    }

    public Type getType() {
      return Type.OIWFS;
    }

    public String getDisplayName() {
      return "Nfiraos WFS " + index;
    }

    public int getIndex() {
      return index;
    }

    public String getSequenceProp() {
      return "guideWithOIWFS" + index;
    }

    public GuideOptions getGuideOptions() {
      return StandardGuideOptions.instance;
    }

    public Option<GuideProbeGroup> getGroup() {
      return new Some<>(Group.instance);
    }

    /**
     * Gets the shape of the Nfiraos guide probe range, which is dependent
     * upon the current choice of the primary OIWFS3 star and its position
     * relative to the base.
     *
     * @param ctx context of the given observation
     * @return Shape describing the range of the guide probe
     */
    public abstract Area probeRange(ObsContext ctx);

    /**
     * Gets the shape of the Nfiraos guide probe arm, which is dependent
     * upon the location of the OIWFS guide star
     *
     * @param ctx      context of the given observation
     * @param validate if true, calls validate() to check that the probe is in range and the arm is not vignetted
     * @return Shape describing the guide probe arm, if ctx target coordinates are known
     */
    public abstract Option<Area> probeArm(ObsContext ctx, boolean validate);

    public GuideStarValidation validate(SPTarget guideStar, ObsContext ctx) {
      final Option<Long> when = ctx.getSchedulingBlockStart();
      final Wfs self = this;
      return guideStar.getSkycalcCoordinates(when).map(coords ->
          NfiraosOiwfs.instance.getProbesInRange(coords, ctx).contains(self) ?
              GuideStarValidation.VALID : GuideStarValidation.INVALID
      ).getOrElse(GuideStarValidation.UNDEFINED);
    }

    private static GuideStarValidation validateVignetting(Wfs wfs, SPTarget guideStar, ObsContext ctx) {
      return isVignetted(wfs, guideStar, ctx).map(b ->
          b ? GuideStarValidation.INVALID : GuideStarValidation.VALID
      ).getOrElse(GuideStarValidation.UNDEFINED);
    }

    // Returns true if the area of the probe arm for the given wfs contains the
    // coordinates of the given guide star
    // (i.e.: The guide star is vignetted by the wfs probe arm)
    private static Option<Boolean> isVignetted(Wfs wfs, SPTarget guideStar, ObsContext ctx) {
      return guideStar.getSkycalcCoordinates(ctx.getSchedulingBlockStart()).flatMap(gscoords ->
          ctx.getBaseCoordinates().flatMap(coords ->
              wfs.probeArm(ctx, false).map(a -> {
                if (a == null) return false;
                CoordinateDiff diff = new CoordinateDiff(coords, gscoords);
                Offset dis = diff.getOffset();
                double p = -dis.p().toArcsecs().getMagnitude();
                double q = -dis.q().toArcsecs().getMagnitude();
                return a.contains(p, q);
              })));
    }

    /**
     * Check if the primary guide star is in range from the given offset
     *
     * @param ctx    ObsContext to get guide star and base coordinates from.
     * @param offset to check if the guide star is in range
     * @return true if guide star is in range from the given offset, false otherwise
     */
    public boolean inRange(ObsContext ctx, Offset offset) {
      Option<GuideProbeTargets> gptOpt = ctx.getTargets().getPrimaryGuideProbeTargets(this);
      if (gptOpt.isEmpty()) return true;
      GuideProbeTargets gpt = gptOpt.getValue();

      Option<SPTarget> guideStarOpt = gpt.getPrimary();
      if (guideStarOpt.isEmpty()) return true;
      SPTarget guideStar = guideStarOpt.getValue();

      // Calculate the difference between the coordinate and the observation's base position.
      return guideStar
          .getSkycalcCoordinates(ctx.getSchedulingBlockStart())
          .flatMap(gscoords -> ctx.getBaseCoordinates()
              .map(base -> {
                CoordinateDiff diff = new CoordinateDiff(base, gscoords);
                // Get offset and switch it to be defined in the same coordinate
                // system as the shape.
                Offset dis = diff.getOffset();
                double p = -dis.p().toArcsecs().getMagnitude();
                double q = -dis.q().toArcsecs().getMagnitude();
                Set<Offset> offsets = new TreeSet<>();
                offsets.add(offset);

                Area a = NfiraosOiwfs.instance.offsetIntersection(ctx, offsets);
                return a != null && a.contains(p, q);
              }))
          .getOrElse(false);
    }
  }

  public static final double RADIUS_ARCSEC = 60.0;

  private static final Point2D.Double PROBE1_CENTER = new Point2D.Double(0.11 * 60.0, 0.3 * 60.0);
  private static final Point2D.Double PROBE2_CENTER = new Point2D.Double(0.0, -0.33 * 60.0);
  private static final Tuple2<Double, Double> PROBE1_DIM = new Pair<>(3.7 * 60.0, 3.0 * 60.0);

  //     REL-1042: Update the Nfiraos probe limits in the OT
  private static final Tuple2<Double, Double> PROBE2_DIM = new Pair<>(4.2 * 60.0, 2.5 * 60.0);

  private static final Ellipse2D AO_PORT = new Ellipse2D.Double(-RADIUS_ARCSEC, -RADIUS_ARCSEC, RADIUS_ARCSEC * 2, RADIUS_ARCSEC * 2);

  // These were copied from Ed Chapin's oiwfs_sim.py
  private static final int R_MAX = 300;                      // maximum extension of probes (mm)
  private static final int R_OVERSHOOT = 20;                 // distance by which probes overshoot centre (mm)
  private static final int R_ORIGIN = R_MAX - R_OVERSHOOT;   // distance of probe origin from centre (mm)
  private static final double R_HEAD = 25 / 2.0;             // radius of probe head (mm)
  private static final double PLATE_SCALE = 2.182;           // plate scale in OIWFS plane (mm/arcsec)
  private static final double PROBE_ARM_WIDTH = 3.0;         // Width of OIWFS probe arm in arcsec (XXX: Allan: Just guessing here)

  /**
   * Returns a Shape that defines the AO port in arcsecs, centered at
   * <code>(0,0)</code>.
   */
  private static final Shape AO_PORT_SHAPE = new Ellipse2D.Double(AO_PORT.getX(), AO_PORT.getY(), AO_PORT.getWidth(), AO_PORT.getHeight());

  // Gets the primary OIWFS 3 guide star, if any.
  private Option<SPTarget> getPrimaryOiwfs3(ObsContext ctx) {
    TargetEnvironment env = ctx.getTargets();
    if (env == null) return None.instance();

    Option<GuideProbeTargets> gtOpt = env.getPrimaryGuideProbeTargets(NfiraosOiwfs.Wfs.oiwfs3);
    if (gtOpt.isEmpty()) return None.instance();
    return gtOpt.getValue().getPrimary();
  }

  // Gets the coordinates of the primary OIWFS guide star relative to the
  // base position, translated to screen coordinates.
  private Option<Point2D> getPrimaryOiwfs3Offset(ObsContext ctx) {
    Option<SPTarget> spTargetOpt = getPrimaryOiwfs3(ctx);
    if (spTargetOpt.isEmpty()) return None.instance();

    Asterism asterism = ctx.getTargets().getAsterism();
    SPTarget target = spTargetOpt.getValue();

    final Option<Long> when = ctx.getSchedulingBlockStart();

    return
        asterism.getSkycalcCoordinates(when).flatMap(bc ->
            target.getSkycalcCoordinates(when).map(tc -> {
              CoordinateDiff diff = new CoordinateDiff(bc, tc);
              Offset o = diff.getOffset();
              double p = -o.p().toArcsecs().getMagnitude();
              double q = -o.q().toArcsecs().getMagnitude();
              return new Point2D.Double(p, q);
            }));
  }

  private static final Angle[] rotation = new Angle[IssPort.values().length];

  static {
    // REL-286: port orientation
    rotation[IssPort.SIDE_LOOKING.ordinal()] = new Angle(90.0, Angle.Unit.DEGREES);
    rotation[IssPort.UP_LOOKING.ordinal()] = new Angle(0.0, Angle.Unit.DEGREES);
  }

  public synchronized static Angle getRotationConfig(IssPort port) {
    return rotation[port.ordinal()]; // XXX TODO: FIXME: Remove for NFIRAOS?
  }

  public synchronized static void setRotationConfig(IssPort port, Angle rotation) {
    NfiraosOiwfs.rotation[port.ordinal()] = rotation;
  }

  // Returns the probe range given the context, mid point and dimensions in arcsec
  private Area probe3DependentRange(ObsContext ctx, Point2D.Double mid, Tuple2<Double, Double> dim) {
    // Gets a rectangle that covers the whole AO port size in width, and
    // has the proper height.
    Area rect = new Area(new Rectangle2D.Double(
        mid.x - dim._1() / 2.,
        -mid.y - dim._2() / 2.,
        dim._1(), dim._2()));

    // Get the position angle and a transform that rotates in the direction
    // of the position angle, and one in the opposite direction.  Recall
    // that positive y is down and a positive rotation rotates the positive
    // x axis toward the positive y axis.  Position angle is expressed as
    // an angle east of north.
    double t = ctx.getPositionAngle().toRadians();
    t = t + getRotationConfig(ctx.getIssPort()).toRadians().getMagnitude();
    AffineTransform rotWithPosAngle = AffineTransform.getRotateInstance(-t);
    AffineTransform rotAgainstPosAngle = AffineTransform.getRotateInstance(t);

    // Get the valid range of the OIWFS 3 probe at all offset positions and
    // compute the center of that range.
    Area p3range = new Area(probeRange3(ctx));
    Rectangle2D b = p3range.getBounds2D();
    Point2D center = new Point2D.Double(b.getCenterX(), b.getCenterY());

    // We have to translate the rectangle.  If there is a primary OIWFS3
    // star, then we translate the rectangle to there.  Otherwise, we assume
    // the center of the probe 3 range computed above.
    Point2D xlat = new Point2D.Double(center.getX(), center.getY());

    // Find the oiwfs3 star, if any.
    Option<Point2D> oiwfs3Opt = getPrimaryOiwfs3Offset(ctx);
    if (!oiwfs3Opt.isEmpty()) {
      Point2D oiwfs3 = oiwfs3Opt.getValue();

      // Rotate both the oiwfs position and the center of the probe 3
      // range to 0 degrees.
      rotAgainstPosAngle.transform(center, center);
      rotAgainstPosAngle.transform(oiwfs3, oiwfs3);

      // Gets a point using the y value of the oiwfs star and x value of
      // the center position.  We could just translate to the oiwfs star
      // position, but then the width of the rectangle wouldn't be in
      // position to cover the probe 3 range.
//            xlat = new Point2D.Double(center.getX(), oiwfs3.getY());
      xlat = new Point2D.Double(oiwfs3.getX(), oiwfs3.getY());

      // Rotate this point to where it should be with the position angle.
      rotWithPosAngle.transform(xlat, xlat);
    }

    rect.transform(rotWithPosAngle);
    rect.transform(AffineTransform.getTranslateInstance(xlat.getX(), xlat.getY()));
    rect.intersect(p3range);
    return rect;
  }

  public Area probeRange1(ObsContext ctx) {
    return probe3DependentRange(ctx, PROBE1_CENTER, PROBE1_DIM);
  }

  public Area probeRange2(ObsContext ctx) {
    return probe3DependentRange(ctx, PROBE2_CENTER, PROBE2_DIM);
  }

  public Area probeRange3(ObsContext ctx) {
    return offsetIntersection(ctx, ctx.getSciencePositions());
  }

  /**
   * Gets the intersection of the FOV and the offsets.
   *
   * @param offsets positions to include in the intersection
   */
  public Area offsetIntersection(ObsContext ctx, Set<Offset> offsets) {
    Area res = null;

    double t = ctx.getPositionAngle().toRadians();

    for (Offset pos : offsets) {
      Area cur = new Area(AO_PORT_SHAPE);

      double p = pos.p().toArcsecs().getMagnitude();
      double q = pos.q().toArcsecs().getMagnitude();

      AffineTransform xform = new AffineTransform();
      if (t != 0.0) xform.rotate(-t);
      xform.translate(-p, -q);
      cur.transform(xform);

      if (res == null) {
        res = cur;
      } else {
        res.intersect(cur);
      }
    }

    return res;
  }

  public Option<Area> probeArm1(ObsContext ctx, boolean validate) {
    return probeArm(ctx, Wfs.oiwfs1, validate);
  }

  public Option<Area> probeArm2(ObsContext ctx, boolean validate) {
    return probeArm(ctx, Wfs.oiwfs2, validate);
  }

  public Option<Area> probeArm3(ObsContext ctx, boolean validate) {
    return probeArm(ctx, Wfs.oiwfs3, validate);
  }

  /**
   * Returns a shape describing the probe arm for the given OIWFS.
   *
   * @param ctx      the context
   * @param oiwfs    one of oiwfs1, oiwfs2 or oiwfs3
   * @param validate if true, call validate to check if probe is in range and not vignetted
   * @return the shape in arcsec relative to the base position, or null if the probe is not
   * in range or is vignetted
   */
  public Option<Area> probeArm(ObsContext ctx, Wfs oiwfs, boolean validate) {
    double phi = oiwfs.getArmAngle(ctx);      // base arm angle in radians
    double t = ctx.getPositionAngle().toRadians();
    double x0 = R_ORIGIN * Math.cos(phi - t); //  x-coordinate of arm origin
    double y0 = R_ORIGIN * Math.sin(phi - t); // y-coordinate of arm origin

    return ctx.getBaseCoordinates().map(coords -> {
      GuideProbeTargets targets = ctx.getTargets().getPrimaryGuideGroup().get(oiwfs).getOrNull();
      if (targets != null) {
        SPTarget target = targets.getPrimary().getOrNull();
        if (target != null && (!validate || oiwfs.validate(target, ctx) == GuideStarValidation.VALID)) {
          // Get offset from base position to oiwfs in arcsecs
          final Option<Coordinates> oc = target.getSkycalcCoordinates(ctx.getSchedulingBlockStart());
          if (oc.isDefined()) {
            CoordinateDiff diff = new CoordinateDiff(coords, oc.getValue());
            Offset dis = diff.getOffset();
            // Offset from base pos to oiwfs in arcsec
            double p = -dis.p().toArcsecs().getMagnitude();
            double q = -dis.q().toArcsecs().getMagnitude();

            // Offset from base pos to probe arm origin in arcsec
            double x = -x0 / PLATE_SCALE;
            double y = -y0 / PLATE_SCALE;
            double headWidth = R_HEAD / PLATE_SCALE;

            // Calculate the polygon for the probe arm
            // (Need to draw the rect at an angle between the guide star and the origin, so using a polygon)
            double px = y - q, py = -(x - p);
            double length = Math.hypot(px, py); // length of perpendicular for arm rect
            double nx = px / length;
            double ny = py / length; // normalized perpendicular
            double w = PROBE_ARM_WIDTH / 2;
            List<Pair<Double, Double>> points = new ArrayList<>();
            points.add(new Pair<>(x + nx * w, y + ny * w));
            points.add(new Pair<>(x - nx * w, y - ny * w));
            points.add(new Pair<>(p - nx * w, q - ny * w));
            points.add(new Pair<>(p + nx * w, q + ny * w));
            ImPolygon probeArm = ImPolygon.apply(points);
            Area res = new Area(probeArm);
            // Add a circle for the probe head
            res.add(new Area(new Ellipse2D.Double(p - headWidth / 2, q - headWidth / 2, headWidth, headWidth)));

            // Clip to Nfiraos range, taking offsets into account
//                        Area range = probeRange3(ctx);
//                        res.intersect(range);
            return res;
          }
        }
      }
      return null;
    });
  }

  /**
   * Gets the set of guide probes that can reach the provided coordinates
   * in the given observing context (if any).
   */
  public Set<Wfs> getProbesInRange(Coordinates coords, ObsContext ctx) {
    Set<Wfs> res = new HashSet<>();

    ctx.getBaseCoordinates().foreach(bcs -> {
      // Calculate the difference between the coordinate and the observation's
      // base position.
      CoordinateDiff diff;
      diff = new CoordinateDiff(bcs, coords);

      // Get offset and switch it to be defined in the same coordinate
      // system as the shape.
      Offset dis = diff.getOffset();
      double p = -dis.p().toArcsecs().getMagnitude();
      double q = -dis.q().toArcsecs().getMagnitude();

      if (probeRange1(ctx).contains(p, q)) res.add(Wfs.oiwfs1);
      if (probeRange2(ctx).contains(p, q)) res.add(Wfs.oiwfs2);
      if (probeRange3(ctx).contains(p, q)) res.add(Wfs.oiwfs3);
    });

    return res;
  }
}