//
// SwingN - swing-like utilities and widgets for use with tripleplay
// Copyright (c) 2014, Cupric - All rights reserved.
// http://github.com/cupric/swingn/blob/master/LICENSE

package swingn.ui.table;

import playn.core.Events;
import react.UnitSlot;
import swingn.ui.IntField;
import swingn.ui.TableModel;
import tripleplay.ui.Element;
import tripleplay.ui.Group;
import tripleplay.ui.Label;
import tripleplay.ui.Styles;
import tripleplay.ui.layout.AxisLayout;

/**
 * Various abstractions for rendering tabular information.
 */
public class Renderers
{
    /**
     * Provides information about the table when performing some rendering operations.
     */
    public interface Provider
    {
        /**
         * For the given cell, create a new slot that will update the model. The slot is intended
         * to be used by the cell and emitted whenever widgets are updated. To allow simple usage
         * with bi-directional values such as {@link tripleplay.ui.Field#text}, the slot ignores
         * emissions that occur while the table is in the process of rendering. Otherwise, the new
         * model value is obtained via {@link Cell#parse()} and the model is updated.
         */
        UnitSlot cellChanger (Element<?> cell);

        /**
         * Tests if the cell at the given coordinates is editable, according to the model.
         */
        boolean isCellEditable (int row, int col);

        /**
         * Gets the table's model.
         */
        TableModel getModel ();
    }

    /**
     * Defines how table header elements will be created when populating columns.
     */
    public static abstract class Header
    {
        /**
         * Creates a header with the given label and index.
         */
        public abstract Element<?> createHeader (TableModel model, int columnIdx);

        /**
         * When a header is tapped, a tool tip is shown. This builds the tip.
         */
        public void buildHeaderTip (Group tip, Element<?> header, TableModel model, int colIdx)
        {
            tip.add(new Label(model.getColumnName(colIdx)));
        }
    }

    /**
     * Defines how the table will request elements when populating its cells. This is a sort of
     * rendering process, so shares the name of swing's JTable equivalent, but the process is
     * simpler and geared towards lower performance requirements (small number of rows).
     */
    public static abstract class Cell
    {
        /**
         * Renders the given model value as an element instance. If previous is not null, the
         * caller should modify it to reflect the given value. Row and col are provided for
         * convenience.
         * <p>For cells that are editable, the returned element is expected to do all of its
         * own input handling and signal the slot given by {@link Provider#cellChanger(Element)}.
         * </p>
         */
        public abstract Element<?> render (
            Provider view, Element<?> previous, Object value, int row, int col);

        /**
         * Produces a value for the model from the given cell. The row and column are provided for
         * convenience. This implementation returns null, which means the cell is not editable.
         * To connect cell editing, the cell's editable values (e.g. {@link IntField#value()}
         * must be connected to {@link Provider#cellChanger(Element)}.
         */
        public Object parse (Provider view, Element<?> cell, int row, int col) {
            return null;
        }

        /**
         * Produces a tool tip for the given cell. This is called when the player taps a cell.
         * This implementation returns null, which means there is no tool tip for the cell.
         */
        public Element<?> createTip (Provider view, Object value, int row, int col) {
            return null;
        }
    }

    /**
     * Defines how rows may be customized for their content.
     */
    public interface Row
    {
        /**
         * This is called each time a row is added or updated. Usually, implementations are geared
         * towards a specific model and can cast {@link Provider#getModel()} in order to obtain
         * information about rowIdx.
         */
        void update (Provider view, Group row, int rowIdx);
    }

    /**
     * The default renderer, shows a {@link Label} for each cell with text set to
     * <code>value.toString()</code>.
     */
    public static class DefaultCellRenderer extends Cell
    {
        @Override
        public Element<?> render (Provider view, Element<?> previous, Object value,
                int row, int col) {
            Label label = (Label)previous;
            if (label == null) {
                label = new Label();
            }
            label.text.update(value == null ? "" : value.toString());
            return label;
        }
    }

    /**
     * Information about a header being tapped to see a menu or tooltip.
     */
    public static class HeaderTip extends Group
    {
        public final Label header;
        public final int columnIdx;
        public final Events.Position event;

        public HeaderTip (Label header, int columnIdx, Events.Position event)
        {
            super(AxisLayout.vertical().gap(2));
            this.header = header;
            this.columnIdx = columnIdx;
            this.event = event;
        }
    }

    /**
     * Implements a data cell holding an integer that can be edited using {@link IntField}.
     */
    public static class IntEditor extends Cell {
        public IntEditor (int max) {
            this(max, 0);
        }

        public IntEditor (int max, int min) {
            _max = max;
            _min = min;
        }

        // TODO: bump this up to super, it isn't really specific to IntEditor
        public IntEditor setStyles (Styles styles) {
            _styles = styles;
            return this;
        }

        @Override
        public Element<?> render (Provider view, Element<?> previous, Object value,
                int row, int col) {
            IntField field = (IntField)previous;
            if (field == null) {
                field = new IntField(_min, _max);
                field.state().connect(view.cellChanger(field));
                if (_styles != null) {
                    field.addStyles(_styles);
                }
            }
            int intValue = Math.min(_max, Math.max(_min, ((Number)value).intValue()));
            field.setValue(intValue);
            field.setEnabled(view.isCellEditable(row, col));
            return field;
        }

        @Override
        public Object parse (Provider view, Element<?> cell, int row, int col) {
            IntField field = (IntField)cell;
            return field.getValue();
        }

        protected int _max, _min;
        protected Styles _styles;
    }

    public static final Header DEFAULT_HEADER = new Header() {
        @Override
        public Element<?> createHeader (TableModel model, int columnIdx) {
            return new Label(model.getColumnName(columnIdx));
        }
    };
    public static final Cell DEFAULT = new DefaultCellRenderer();
    public static final Renderers.Row DEFAULT_ROW = new Renderers.Row() {
        @Override
        public void update (Provider view, Group row, int rowIdx)
        {
        }
    };

    public static final Cell SHORT_EDITOR = new IntEditor(Short.MAX_VALUE);
}
