package jsky.app.ot.gemini.editor.targetComponent;

import jsky.app.ot.util.DnDUtils;

import java.awt.*;
import java.awt.dnd.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Objects;

// Drag & drop source for the position table widget.
class TelescopePosTableDragSource implements DragGestureListener, DragSourceListener {

    /** The pos table widget */
    private final TelescopePosTableWidget table;

    private boolean editable = false;

    /** Saved reference to drag object, for use in TelescopePosTableDropTarget */
    static TelescopePosTableDragDropObject _dragObject;

    /**
     * Constructor
     */
    public TelescopePosTableDragSource(final TelescopePosTableWidget tree) {
        table = Objects.requireNonNull(tree);

        // Create a DragGestureRecognizer and register as the listener.
        DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(table, DnDConstants.ACTION_COPY_OR_MOVE, this);
    }

    void setEditable(boolean editable) {
        this.editable = editable;
    }

    public void dragGestureRecognized(final DragGestureEvent dge) {
        if (!editable) return;

        // don't conflict with popup menus.
        final InputEvent e = dge.getTriggerEvent();
        if (e instanceof MouseEvent && ((MouseEvent) e).isPopupTrigger())
            return;

        // Get the mouse location and convert it to a row.
        final Point location = dge.getDragOrigin();
        final int rowIdx = table.rowAtPoint(location);

        if (table.isRowSelected(rowIdx)) {
            table.getSelectedNode().foreach(selRow -> {
                _dragObject = new TelescopePosTableDragDropObject(selRow, table);
                try {
                    dge.startDrag(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR), _dragObject, this);
                } catch (final Exception ex) {
                    DnDUtils.debugPrintln("SPTreeDragSource.dragGestureRecognized: " + ex);
                }
            });
        }
    }

    // Implementation of DragSourceListener interface
    public void dragEnter(final DragSourceDragEvent dsde) {
        DnDUtils.debugPrintln("Drag Source: dragEnter, drop action = " + DnDUtils.showActions(dsde.getDropAction()));
    }

    public void dragOver(final DragSourceDragEvent dsde) {
        DnDUtils.debugPrintln("Drag Source: dragOver, drop action = " + DnDUtils.showActions(dsde.getDropAction()));
    }

    public void dragExit(final DragSourceEvent dse) {
        DnDUtils.debugPrintln("Drag Source: dragExit");
    }

    public void dropActionChanged(final DragSourceDragEvent dsde) {
        DnDUtils.debugPrintln("Drag Source: dropActionChanged, drop action = " + DnDUtils.showActions(dsde.getDropAction()));
    }

    public void dragDropEnd(final DragSourceDropEvent dsde) {
        DnDUtils.debugPrintln("Drag Source: drop completed, drop action = "
                              + DnDUtils.showActions(dsde.getDropAction()) + ", success: " + dsde.getDropSuccess());
    }
}
