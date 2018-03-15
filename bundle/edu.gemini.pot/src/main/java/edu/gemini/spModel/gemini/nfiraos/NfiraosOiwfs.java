package edu.gemini.spModel.gemini.nfiraos;

import edu.gemini.shared.util.immutable.*;
import edu.gemini.skycalc.Angle;
import edu.gemini.skycalc.CoordinateDiff;
import edu.gemini.skycalc.Coordinates;
import edu.gemini.skycalc.Offset;
import edu.gemini.spModel.core.BandsList;
import edu.gemini.spModel.core.RBandsList;
import edu.gemini.spModel.inst.FeatureGeometry;
import edu.gemini.spModel.nfiraos.NfiraosGuideProbeGroup;
import edu.gemini.spModel.guide.*;
import edu.gemini.spModel.obs.context.ObsContext;
import edu.gemini.spModel.target.SPTarget;
import edu.gemini.spModel.target.env.Asterism;
import edu.gemini.spModel.target.env.GuideProbeTargets;
import edu.gemini.spModel.target.env.TargetEnvironment;
import edu.gemini.spModel.target.offset.OffsetPosBase;
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
      public Area probeRange(ObsContext ctx, Offset offset) {
        return NfiraosOiwfs.instance.probeRange1(ctx, offset);
      }

      @Override
      public Option<Area> probeArm(ObsContext ctx, boolean validate, Offset offset) {
        return NfiraosOiwfs.instance.probeArm1(ctx, validate, offset);
      }

      @Override
      public GuideStarValidation validate(SPTarget guideStar, ObsContext ctx, Offset offset) {
        return super.validate(guideStar, ctx, offset)
            .and(validateVignetting(oiwfs2, guideStar, ctx, offset))
            .and(validateVignetting(oiwfs3, guideStar, ctx, offset))
            .and(validateVignetting(oiwfs1, oiwfs2, ctx, offset))
            .and(validateVignetting(oiwfs1, oiwfs3, ctx, offset))
            ;
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
      public Area probeRange(ObsContext ctx, Offset offset) {
        return NfiraosOiwfs.instance.probeRange2(ctx, offset);
      }

      @Override
      public Option<Area> probeArm(ObsContext ctx, boolean validate, Offset offset) {
        return NfiraosOiwfs.instance.probeArm2(ctx, validate, offset);
      }

      @Override
      public GuideStarValidation validate(SPTarget guideStar, ObsContext ctx, Offset offset) {
        return super.validate(guideStar, ctx, offset)
            .and(validateVignetting(oiwfs1, guideStar, ctx, offset))
            .and(validateVignetting(oiwfs3, guideStar, ctx, offset))
            .and(validateVignetting(oiwfs2, oiwfs1, ctx, offset))
            .and(validateVignetting(oiwfs2, oiwfs3, ctx, offset));
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
      public Area probeRange(ObsContext ctx, Offset offset) {
        return NfiraosOiwfs.instance.probeRange3(ctx, offset);
      }

      @Override
      public Option<Area> probeArm(ObsContext ctx, boolean validate, Offset offset) {
        return NfiraosOiwfs.instance.probeArm3(ctx, validate, offset);
      }

      @Override
      protected double getArmAngle(ObsContext ctx) {
        return Math.toRadians(330.0);
      }

      @Override
      public GuideStarValidation validate(SPTarget guideStar, ObsContext ctx, Offset offset) {
        return super.validate(guideStar, ctx, offset)
            .and(validateVignetting(oiwfs1, guideStar, ctx, offset))
            .and(validateVignetting(oiwfs2, guideStar, ctx, offset))
            .and(validateVignetting(oiwfs3, oiwfs1, ctx, offset))
            .and(validateVignetting(oiwfs3, oiwfs2, ctx, offset));
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
     * @param offset offset from base pos to use
     * @return Shape describing the range of the guide probe
     */
    public abstract Area probeRange(ObsContext ctx, Offset offset);

    /**
     * Gets the shape of the Nfiraos guide probe arm, which is dependent
     * upon the location of the OIWFS guide star
     *
     * @param ctx      context of the given observation
     * @param validate if true, calls validate() to check that the probe is in range and the arm is not vignetted
     * @param offset   the offset from (0, 0) to use for calculations (already rotated by pos angle)
     * @return Shape describing the guide probe arm, if ctx target coordinates are known
     */
    public abstract Option<Area> probeArm(ObsContext ctx, boolean validate, Offset offset);

    public GuideStarValidation validate(SPTarget guideStar, ObsContext ctx, Offset offset) {
      final Option<Long> when = ctx.getSchedulingBlockStart();
      final Wfs self = this;
      return guideStar.getSkycalcCoordinates(when).map(coords ->
          NfiraosOiwfs.instance.getProbesInRange(coords, ctx, offset).contains(self) ?
              GuideStarValidation.VALID : GuideStarValidation.INVALID
      ).getOrElse(GuideStarValidation.UNDEFINED);
    }

    private static GuideStarValidation validateVignetting(Wfs wfs, SPTarget guideStar, ObsContext ctx, Offset offset) {
      return isVignetted(wfs, guideStar, ctx, offset).map(b ->
          b ? GuideStarValidation.INVALID : GuideStarValidation.VALID
      ).getOrElse(GuideStarValidation.UNDEFINED);
    }

    private static GuideStarValidation validateVignetting(Wfs wfs1, Wfs wfs2, ObsContext ctx, Offset offset) {
      return isVignetted(wfs1, wfs2, ctx, offset).map(b ->
          b ? GuideStarValidation.INVALID : GuideStarValidation.VALID
      ).getOrElse(GuideStarValidation.UNDEFINED);
    }

    // Returns true if the area of the probe arm for the given wfs contains the
    // coordinates of the given guide star
    // (i.e.: The guide star is vignetted by the wfs probe arm)
    private static Option<Boolean> isVignetted(Wfs wfs, SPTarget guideStar, ObsContext ctx, Offset offset) {
      return guideStar.getSkycalcCoordinates(ctx.getSchedulingBlockStart()).flatMap(gscoords ->
          ctx.getBaseCoordinates().flatMap(baseCoords ->
              wfs.probeArm(ctx, false, offset).map(a -> {
                if (a == null) return false;
                // Use offset position if defined instead of base pos
                Coordinates coords = new Coordinates(baseCoords.getRa().add(offset.p()), baseCoords.getDec().add(offset.q()));
                CoordinateDiff diff = new CoordinateDiff(coords, gscoords);
                Offset dis = diff.getOffset();
                double p = -dis.p().toArcsecs().getMagnitude();
                double q = -dis.q().toArcsecs().getMagnitude();
                return a.contains(p, q);
              })));
    }

    // Returns true if the probe arms cross
    private static Option<Boolean> isVignetted(Wfs wfs1, Wfs wfs2, ObsContext ctx, Offset offset) {
      Option<Area> p1 = wfs1.probeArm(ctx, false, offset);
      Option<Area> p2 = wfs2.probeArm(ctx, false, offset);
      if (p1 == null || p2 == null || p1.isEmpty() || p2.isEmpty() || p1.getValue() == null || p2.getValue() == null) {
        return new Some<>(Boolean.FALSE);
      }
      Area a1 = p1.getValue();
      Area a2 = p2.getValue();
      a1.intersect(a2);
      return new Some<>(!a1.isEmpty());
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

  // Radius of the FOV
  private static final double RADIUS_ARCSEC = 60.0;

  // Shape for FOV (from center (0,0))
  private static final Ellipse2D AO_PORT = new Ellipse2D.Double(-RADIUS_ARCSEC, -RADIUS_ARCSEC, RADIUS_ARCSEC * 2, RADIUS_ARCSEC * 2);
  public static Area AO_BOUNDS = new Area(AO_PORT);

  // These were copied from Ed Chapin's oiwfs_sim.py
  private static final int R_MAX = 300;                      // maximum extension of probes (mm)
  private static final int R_OVERSHOOT = 20;                 // distance by which probes overshoot centre (mm)
  private static final int R_ORIGIN = R_MAX - R_OVERSHOOT;   // distance of probe origin from centre (mm)
  private static final double R_HEAD = 25 / 2.0;             // radius of probe head (mm)
  private static final double PLATE_SCALE = 2.182;           // plate scale in OIWFS plane (mm/arcsec)
  private static final double PROBE_ARM_WIDTH = 3.0;         // Width of OIWFS probe arm in arcsec (XXX: Allan: Just guessing here)

  // Gets the primary OIWFS 3 guide star, if any.
  private Option<SPTarget> getPrimaryOiwfs3(ObsContext ctx) {
    TargetEnvironment env = ctx.getTargets();
    if (env == null) return None.instance();

    Option<GuideProbeTargets> gtOpt = env.getPrimaryGuideProbeTargets(NfiraosOiwfs.Wfs.oiwfs3);
    if (gtOpt.isEmpty()) return None.instance();
    return gtOpt.getValue().getPrimary();
  }

//  // Gets the coordinates of the primary OIWFS guide star relative to the
//  // base position, translated to screen coordinates.
//  private Option<Point2D> getPrimaryOiwfs3Offset(ObsContext ctx) {
//    Option<SPTarget> spTargetOpt = getPrimaryOiwfs3(ctx);
//    if (spTargetOpt.isEmpty()) return None.instance();
//
//    Asterism asterism = ctx.getTargets().getAsterism();
//    SPTarget target = spTargetOpt.getValue();
//
//    final Option<Long> when = ctx.getSchedulingBlockStart();
//
//    return
//        asterism.getSkycalcCoordinates(when).flatMap(bc ->
//            target.getSkycalcCoordinates(when).map(tc -> {
//              CoordinateDiff diff = new CoordinateDiff(bc, tc);
//              Offset o = diff.getOffset();
//              double p = -o.p().toArcsecs().getMagnitude();
//              double q = -o.q().toArcsecs().getMagnitude();
//              return new Point2D.Double(p, q);
//            }));
//  }

  private static final Angle[] rotation = new Angle[IssPort.values().length];

  static {
    // REL-286: port orientation
    rotation[IssPort.SIDE_LOOKING.ordinal()] = new Angle(90.0, Angle.Unit.DEGREES);
    rotation[IssPort.UP_LOOKING.ordinal()] = new Angle(0.0, Angle.Unit.DEGREES);
  }

  public synchronized static Angle getRotationConfig(IssPort port) {
    return rotation[port.ordinal()]; // XXX TODO: FIXME: Remove IssPort port for NFIRAOS?
  }

  public synchronized static void setRotationConfig(IssPort port, Angle rotation) {
    NfiraosOiwfs.rotation[port.ordinal()] = rotation;
  }

  // Returns an area indicating the total range of the given probe arm (in arcsecs)
  private Area probeDependentRange(ObsContext ctx, Wfs oiwfs) {
    double phi = oiwfs.getArmAngle(ctx);      // base arm angle in radians
    double t = ctx.getPositionAngle().toRadians();
    double x0 = R_ORIGIN * Math.cos(phi - t); //  x-coordinate of arm origin
    double y0 = R_ORIGIN * Math.sin(phi - t); // y-coordinate of arm origin
    double x = -x0 / PLATE_SCALE;
    double y = -y0 / PLATE_SCALE;
    double size = R_MAX * 2 / PLATE_SCALE;
    Area range = new Area(new Ellipse2D.Double(x - size / 2, y - size / 2, size, size));
    range.intersect(AO_BOUNDS);
    return range;
  }

  public Area probeRange1(ObsContext ctx, Offset offset) {
    return probeDependentRange(ctx, Wfs.oiwfs1);
  }

  public Area probeRange2(ObsContext ctx, Offset offset) {
    return probeDependentRange(ctx, Wfs.oiwfs2);
  }

  public Area probeRange3(ObsContext ctx, Offset offset) {
    return probeDependentRange(ctx, Wfs.oiwfs3);
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
      Area cur = AO_BOUNDS;

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

  public Option<Area> probeArm1(ObsContext ctx, boolean validate, Offset offset) {
    return probeArm(ctx, Wfs.oiwfs1, validate, offset);
  }

  public Option<Area> probeArm2(ObsContext ctx, boolean validate, Offset offset) {
    return probeArm(ctx, Wfs.oiwfs2, validate, offset);
  }

  public Option<Area> probeArm3(ObsContext ctx, boolean validate, Offset offset) {
    return probeArm(ctx, Wfs.oiwfs3, validate, offset);
  }


  /**
   * Returns a shape describing the probe arm for the given OIWFS.
   *
   * @param ctx      the context
   * @param oiwfs    one of oiwfs1, oiwfs2 or oiwfs3
   * @param validate if true, call validate to check if probe is in range and not vignetted
   * @param offset   offset position to use for calculations in place of base position (or (0,0) if no offset pos selected)
   * @return the shape in arcsec relative to the base position, or null if the probe is not
   * in range or is vignetted
   */
  public Option<Area> probeArm(ObsContext ctx, Wfs oiwfs, boolean validate, Offset offset) {
    double phi = oiwfs.getArmAngle(ctx);      // base arm angle in radians
    double t = ctx.getPositionAngle().toRadians();
    double x0 = R_ORIGIN * Math.cos(phi - t); //  x-coordinate of arm origin
    double y0 = R_ORIGIN * Math.sin(phi - t); // y-coordinate of arm origin

    return ctx.getBaseCoordinates().map(baseCoords -> {

      // Use offset position if defined instead of base pos
      Coordinates coords = new Coordinates(baseCoords.getRa().add(offset.p()), baseCoords.getDec().add(offset.q()));

      GuideProbeTargets targets = ctx.getTargets().getPrimaryGuideGroup().get(oiwfs).getOrNull();
      if (targets != null) {
        SPTarget target = targets.getPrimary().getOrNull();
        if (target != null && (!validate || oiwfs.validate(target, ctx, offset) == GuideStarValidation.VALID)) {
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

            // Draw the probe arm from the origin (x, y) to the guide star (p, q) with width headWidth.
            // XXX TODO: Make probe arm thicker at base and thinner at the guide star
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
  public Set<Wfs> getProbesInRange(Coordinates coords, ObsContext ctx, Offset offset) {
    Set<Wfs> res = new HashSet<>();

    ctx.getBaseCoordinates().foreach(baseCoords -> {
      // Use offset position if defined instead of base pos
      Coordinates bcs = new Coordinates(baseCoords.getRa().add(offset.p()), baseCoords.getDec().add(offset.q()));

      // Calculate the difference between the coordinate and the observation's
      // base position.
      CoordinateDiff diff;
      diff = new CoordinateDiff(bcs, coords);

      // Get offset and switch it to be defined in the same coordinate
      // system as the shape.
      Offset dis = diff.getOffset();
      double p = -dis.p().toArcsecs().getMagnitude();
      double q = -dis.q().toArcsecs().getMagnitude();

      if (probeRange1(ctx, offset).contains(p, q)) res.add(Wfs.oiwfs1);
      if (probeRange2(ctx, offset).contains(p, q)) res.add(Wfs.oiwfs2);
      if (probeRange3(ctx, offset).contains(p, q)) res.add(Wfs.oiwfs3);
    });

    return res;
  }
}