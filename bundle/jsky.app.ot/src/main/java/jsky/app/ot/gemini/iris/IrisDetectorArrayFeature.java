package jsky.app.ot.gemini.iris;

import edu.gemini.spModel.gemini.iris.Iris;
import edu.gemini.spModel.gemini.iris.IrisDetectorArray;
import static edu.gemini.spModel.gemini.iris.IrisDetectorArray.Quadrant;

import edu.gemini.spModel.telescope.IssPort;
import jsky.app.ot.gemini.inst.SciAreaFeatureBase;
import jsky.app.ot.tpe.TpeImageInfo;

import java.awt.*;
import java.awt.font.TextLayout;
import java.awt.geom.*;

/**
 * Draws the Science Area, the detector or slit.
 */
public final class IrisDetectorArrayFeature extends SciAreaFeatureBase {

    private AffineTransform trans;

    private Iris getIris() {
        return _iw.getContext().instrument().orNull(Iris.SP_TYPE);
    }

    @Override
    protected Point2D.Double _getTickMarkOffset() {
        AffineTransform trans = new AffineTransform();
        trans.translate(_baseScreenPos.x, _baseScreenPos.y);
        trans.scale(_tii.getPixelsPerArcsec(), _tii.getPixelsPerArcsec());

        Point2D.Double p = new Point2D.Double(0.0,
                - (IrisDetectorArray.DETECTOR_GAP_ARCSEC + IrisDetectorArray.DETECTOR_SIZE_ARCSEC)
                        + IrisDetectorArray.ODGW_HOTSPOT_OFFSET);
        return (Point2D.Double) trans.transform(p, p);
    }

    @Override
    protected boolean _calc(TpeImageInfo tii)  {
        if (!super._calc(tii)) return false;

        trans = new AffineTransform();
        trans.concatenate(_posAngleTrans);
        trans.translate(_baseScreenPos.x, _baseScreenPos.y);
        trans.scale(tii.getPixelsPerArcsec(), tii.getPixelsPerArcsec());
        return true;
    }

    // If _flipRA is -1, flip the RA axis of the area
    private Area flipArea(Area a) {
        if (_flipRA == -1) {
            a = a.createTransformedArea(AffineTransform.getScaleInstance(_flipRA, 1.0));
        }
        return a;
    }


    protected Shape getShape() {
        Area a = new Area(IrisDetectorArray.instance.shape());
        return flipArea(a).createTransformedArea(trans);
    }

    private Shape getIfsDetectorShape(Iris.Scale scale) {
        double w = scale.getWidth();
        double h = scale.getHeight(); // arcsecs
        Rectangle2D.Double rect = new Rectangle2D.Double(-w/2, -h/2, w, h);

        return trans.createTransformedShape(flipArea(new Area(rect)));
    }

    private void drawLabels(Graphics2D g2d) {
        Color origColor = g2d.getColor();
        Font  origFont  = g2d.getFont();

        Font font = FONT.deriveFont(Font.PLAIN);

        g2d.setColor(Color.red);
        g2d.setFont(font);

        for (Quadrant q : Quadrant.values()) drawLabel(g2d, q);

        g2d.setColor(origColor);
        g2d.setFont(origFont);
    }

    private IssPort getPort() {
        return _iw.getContext().instrument().issPortOrDefault();
    }

    private void drawLabel(Graphics2D g2d, Quadrant q) {
        final String idStr = q.id(getPort()).toString();
        final TextLayout  layout    = new TextLayout(idStr, g2d.getFont(), g2d.getFontRenderContext());
        final Rectangle2D strBounds = layout.getBounds();

        final int padding = 8;
        final double offset = ((strBounds.getHeight() + padding)/_tii.getPixelsPerArcsec())/2;

        final Rectangle2D bnds = q.shape().getBounds2D();
        final double x = (bnds.getX() < 0) ? bnds.getX() + offset : bnds.getMaxX() - offset;
        final double y = (bnds.getY() < 0) ? bnds.getY() + offset : bnds.getMaxY() - offset;
        final Point2D origPoint = new Point2D.Double(x*_flipRA, y); // flip RA if needed
        final Point2D destPoint = new Point2D.Double();
        trans.transform(origPoint, destPoint);

        final double textX = destPoint.getX() - strBounds.getWidth()/2.0;
        final double textY = destPoint.getY() + strBounds.getHeight()/2.0 + 0.5;
        layout.draw(g2d, (float) textX, (float) textY);
    }



    // Grim, but the super class is so poor that we basically have to handle
    // drawing from scratch.  Override and don't bother with the super class.
    @Override
    public void draw(Graphics g, TpeImageInfo tii) {
        Graphics2D g2d = (Graphics2D) g;

        if (!_calc(tii)) return;

        g2d.setColor(getFovColor());
        g2d.draw(getShape());
        drawLabels(g2d);
        drawDragItem(g2d);

        Iris inst = getIris();
        if (inst != null) {
            if (inst.getDetector() != Iris.Detector.IMAGER_ONLY) {
                drawIfsDetector(g2d, inst.getScale());
            }
        }
    }

    private void drawIfsDetector(Graphics2D g2d, Iris.Scale scale) {
        Shape s = getIfsDetectorShape(scale);
        Color oldColor = g2d.getColor();
        Stroke oldStroke = g2d.getStroke();
        g2d.setColor(Color.magenta);
        g2d.setStroke(new BasicStroke(3.0F));
        g2d.draw(s);
        g2d.setColor(oldColor);
        g2d.setStroke(oldStroke);
    }

    /**
     * Draw the science area at the given x,y (screen coordinate) offset position.
     */
    @Override
    public void drawAtOffsetPos(Graphics g, TpeImageInfo tii, double x, double y) {
        Graphics2D g2d = (Graphics2D) g;
        if (!_calc(tii)) return;
        AffineTransform saveAT = g2d.getTransform();
        try {
            g2d.translate(x - _baseScreenPos.x, y - _baseScreenPos.y);
            g2d.draw(getShape());

            Iris inst = getIris();
            if (inst != null) {
                if (inst.getDetector() != Iris.Detector.IMAGER_ONLY) {
                    g2d.draw(getIfsDetectorShape(inst.getScale()));
                }
            }
        } finally {
            g2d.setTransform(saveAT);
        }
    }

}
