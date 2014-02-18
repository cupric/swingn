//
// SwingN - swing-like utilities and widgets for use with tripleplay
// Copyright (c) 2014, Cupric - All rights reserved.
// http://github.com/cupric/swingn/blob/master/LICENSE

package swingn.ui;

import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import playn.core.Connection;
import playn.core.Events;
import playn.core.ImmediateLayer;
import playn.core.Layer;
import playn.core.PlayN;
import playn.core.Surface;
import pythagoras.f.Dimension;
import pythagoras.f.IDimension;
import pythagoras.i.Point;
import react.Value;
import react.ValueView;
import tripleplay.ui.Composite;
import tripleplay.ui.Container;
import tripleplay.ui.Element;
import tripleplay.ui.Elements;
import tripleplay.ui.Group;
import tripleplay.ui.Layout;
import tripleplay.ui.Scroller;
import tripleplay.ui.Style;
import tripleplay.util.Tapper;

/**
 * Widget for displaying other widgets in a columnar layout. Has a header at the top and a
 * scrolling vertical group of rows below it.
 *
 * <p>NOTE: this is similar to swing's JTable, but does not adapt directly to a {@code TableModel}.
 * Instead, {@code Table} is fairly simple and may be used without any reference to models,
 * similarly to a {@link Group}. If a model connection is desired, one can be hooked up using
 * {@link TableView}.</p>
 */
public class Table extends Composite<Table>
{
    /** Configures the color of the grid lines in a table. */
    public static Style<Integer> GRID = Style.newStyle(true, 0);

    /** Default column instance, from which clones can be made using the copier methods. */
    public static Column COL = new Column(Style.HAlign.CENTER, false, 10, 1e4f, 150, 1);

    /**
     * A row in the table.
     */
    public class Row extends Group
    {
        public Row ()
        {
            super(_rowLayout);
        }

        /**
         * Shortcut for updating {@link Table#selectedRow} with this row's index.
         */
        public void select ()
        {
            selectedRow.update(getIndex());
        }

        /**
         * Returns the index of this row in the table, or -1 if it is not in the table.
         */
        public int getIndex ()
        {
            for (int ii = 0, nn = getRowCount(); ii < nn; ii++) {
                if (getRow(ii) == this) {
                    return ii;
                }
            }
            return -1;
        }

        @Override
        protected Class<?> getStyleClass ()
        {
            return Row.class;
        }

        void setSelected (boolean sel)
        {
            if (sel != isSelected()) {
                set(Flag.SELECTED, sel);
                invalidate();
            }
        }

        @Override
        protected void invalidate () {
            // Overriding & just calling super so this is visible to Table.Ext
            super.invalidate();
        }

        float laidOutHeight;
    }

    /**
     * Defines the immutable configuration for a table column.
     */
    public static class Column
    {
        public Column (Style.HAlign halign, boolean stretch, float min, float max,
                float preferred, float weight)
        {
            _halign = halign;
            _stretch = stretch;
            _min = min;
            _max = max;
            _preferred = preferred;
            _weight = weight;
        }

        /**
         * Makes a new copy of this column
         */
        @Override
        public Column clone ()
        {
            return new Column(_halign, _stretch, _min, _max, _preferred, _weight);
        }

        /**
         * Returns a copy of this column set to align elements on the left.
         */
        public Column alignLeft ()
        {
            return new Column(Style.HAlign.LEFT, _stretch, _min, _max, _preferred, _weight);
        }

        /**
         * Returns a copy of this column set to align elements on the right.
         */
        public Column alignRight ()
        {
            return new Column(Style.HAlign.RIGHT, _stretch, _min, _max, _preferred, _weight);
        }

        /**
         * Returns a copy of this column set to align elements on the center.
         */
        public Column alignCenter ()
        {
            return new Column(Style.HAlign.CENTER, _stretch, _min, _max, _preferred, _weight);
        }

        /**
         * Returns a copy of this column using a fixed width.
         */
        public Column fixedWidth (float width)
        {
            return new Column(_halign, _stretch, width, width, width, _weight);
        }

        /**
         * Returns a copy of this column that will stretch elements to the width of the column. By
         * default, elements are configured to their preferred width.
         */
        public Column stretch ()
        {
            return new Column(_halign, true, _min, _max, _preferred, _weight);
        }

        /**
         * Returns a copy of this column with variable width. This column will not be allowed to
         * shrink below its minimum width or stretch larger than its maximum.
         */
        public Column variableWidth (float min, float max, float preferred)
        {
            return new Column(_halign, _stretch, min, max, preferred, _weight);
        }

        /**
         * Returns a copy of this column with the given preferred width.
         */
        public Column preferredWidth (float preferred)
        {
            return new Column(_halign, _stretch, _min, _max, preferred, _weight);
        }

        /**
         * Returns a copy of this column with the given weight, for distribution of extra space.
         */
        public Column weight (float weight)
        {
            return new Column(_halign, _stretch, _min, _max, _preferred, weight);
        }

        protected boolean fixed ()
        {
            return _min == _max;
        }

        protected final Style.HAlign _halign;
        protected final boolean _stretch;
        protected final float _min, _max, _preferred;
        protected final float _weight;
    }

    /**
     * Extension facet for less common or expensive operations.
     */
    public class Ext
    {
        /**
         * Adds a column as in {@link Table#addColumn(Column,Element)}, but also sets up the rows
         * with the given new cells.
         */
        public void addColumn (Column column, List<Element<?>> cells)
        {
            insertColumn(column, _columns.size(), cells);
        }

        /**
         * Inserts a column to a table as in {@link Table#insertColumn(Column,Element)}, but also
         * sets up the rows with the given new cells.
         */
        public void insertColumn (Column column, int index, List<Element<?>> cells)
        {
            int rowCount = getRowCount();
            Preconditions.checkArgument(cells.size() - 1 == rowCount,
                "% cells provided, need %s", cells.size(), rowCount);

            Table.this.insertColumn(column, index, cells.get(0));
            for (int row = 0; row < rowCount; ++row) {
                getRow(row).add(index, cells.get(row + 1));
            }
        }

        /**
         * Removes a column from a table and updates the header and rows.
         * @return the elements that were in the column, including the header
         */
        public List<Element<?>> removeColumn (int index)
        {
            List<Element<?>> result = Lists.newArrayList();
            remove(header, index, result);
            for (Row row : getRows()) {
                remove(row, index, result);
            }
            _columns.remove(index);
            return result;
        }

        /**
         * Removes a column from a table and destroys the associated elements in the header and
         * rows.
         */
        public void destroyColumn (int index)
        {
            header.destroyAt(index);
            for (Row row : getRows()) {
                row.destroyAt(index);
            }
            _columns.remove(index);
        }

        /**
         * Sets a column in the table.
         */
        public void setColumn (int index, Column newColumn) {
            _columns.set(index, new LayoutColumn(newColumn));
            invalidate();
        }

        /**
         * Disallows row selection.
         */
        public void disallowRowSelection ()
        {
            _rowSelListener.disconnect();
            selectedRow.update(null);
        }

        /**
         * Finds the index of the clicked row, given a y coordinate.
         * @param y vertical offset, relative to {@link Table#rows}
         * @return the index of the row, or -1 if not in bounds
         */
        public int findRowForClick (float y)
        {
            return Table.this.findRowForClick(y);
        }

        /**
         * Finds the index of the clicked column, given an x coordinate.
         * @param x horizontal offset, relative to {@link Table#rows}
         */
        public int findColumnForClick (float x)
        {
            // linear search rows by col width
            for (int ii = 0, nn = _columns.size(); ii < nn; ii++) {
                float wid = _columns.get(ii).width;
                if (x < wid) {
                    return ii;
                }
                x -= wid;
            }
            return -1;
        }

        public Point findCell (float localX, float localY)
        {
            int row = findRowForClick(localY);
            if (row == -1) {
                return null;
            }

            int col = findColumnForClick(localX);
            if (col == -1) {
                return null;
            }
            return new Point(col, row);
        }
    }

    /** Row of elements across the top of the table, added via {@link #addColumn()}. */
    public final Row header;

    /** Container for the table's rows. Each child is a <code>Row</code>, added via
     * {@link #addRow()}. */
    public final Group rows;

    /** The scrolling group, whose content is {@link #rows}. Note this gets set to behavior
     * {@link Scroller.Behavior#VERTICAL}. Callers can override if they also want horizontal
     * scrolling. */
    public final Scroller rowArea;

    /** The index of the row currently selected, or null if no row is selected. This may be
     * updated manually or automatically as a result of input events. */
    public final Value<Integer> selectedRow = Value.create(null);

    /**
     * Creates a new table.
     */
    public Table ()
    {
        setLayout(new TableLayout());
        initChildren(
            header = new Row(),
            rowArea = new Scroller(rows = new Group(new ContentLayout())).
                setBehavior(Scroller.Behavior.VERTICAL));
        ImmediateLayer lines = PlayN.graphics().createImmediateLayer(new ImmediateLayer.Renderer() {
            @Override
            public void render (Surface surface)
            {
                drawGridLines(surface);
            }
        });
        lines.setDepth(1);
        rows.layer.add(lines);

        // set up an optimized hit tested for rows
        rows.layer.setHitTester(new Layer.HitTester() {
            @Override
            public Layer hitTest (Layer layer, pythagoras.f.Point p) {
                int row = findRowForClick(p.y);
                if (row == -1) {
                    return null;
                }
                Row hit = getRow(row);
                p.y -= hit.y();
                Layer cell = hit.layer.hitTest(p);
                return cell == null ? hit.layer : cell;
            }
        });

        _rowSelListener = rows.layer.addListener(new Tapper() {
            @Override
            public void onTap (Events.Position event) {
                pythagoras.f.Point rowPt = Layer.Util.layerToParent(
                    event.hit(), rows.layer, event.localX(), event.localY());
                int row = findRowForClick(rowPt.y);
                selectedRow.update(row == -1 ? null : Integer.valueOf(row));
            }
        });

        // deal with row selection
        selectedRow.connect(new ValueView.Listener<Integer>() {
            @Override
            public void onChange (Integer nrow, Integer orow) {
                if (orow != null) {
                    getRow(orow).setSelected(false);
                }
                if (nrow != null) {
                    getRow(nrow).setSelected(true);
                }
            }
        });
    }

    /**
     * Sets the gap between rows and columns. Note that this is most useful for transulcent rows
     * and cells because the table's (or its ancestors') background shows between gaps.
     */
    public Table setGaps (float rowGap, float colGap)
    {
        _rowgap = rowGap;
        _colgap = colGap;
        invalidate();
        return this;
    }

    public Table rowVAlign (Style.VAlign valign)
    {
        _rowVAlign = valign;
        return this;
    }

    /**
     * Adds a new column with the given configuration and header. Note that the caller must ensure
     * that all table rows are updated such that each has the correct number of cells. A basic
     * solution for this can be found in {@link Ext}.
     */
    public Table addColumn (Column column, Element<?> headerWidget)
    {
        return insertColumn(column, _columns.size(), headerWidget);
    }

    /**
     * Inserts a new column with the given configuration and header. Note that the caller must
     * ensure that all table rows are updated such that each has the correct number of cells.
     * A basic solution for this can be found in {@link Ext}.
     */
    public Table insertColumn (Column column, int index, Element<?> headerWidget) {
        _columns.add(index, new LayoutColumn(column));
        header.add(index, headerWidget);
        return this;
    }

    /**
     * Gets all previously added columns (read-only).
     */
    public Iterable<Column> getColumns ()
    {
        return Iterables.transform(_columns, TO_COLUMN);
    }

    /**
     * Adds a new row.
     */
    public Row addRow ()
    {
        Row row = new Row();
        rows.add(row);
        return row;
    }

    /**
     * Adds a new row at the given index.
     */
    public Row addRow (int rowIdx)
    {
        Row row = new Row();
        rows.add(rowIdx, row);
        return row;
    }

    /**
     * Removes the given row.
     */
    public void removeRow (Row row)
    {
        // for simplicity, just deselect. we could do better if needed
        selectedRow.update(null);
        rows.remove(row);
    }

    /**
     * Removes and destroys the given row.
     */
    public void destroyRow (Row row)
    {
        // for simplicity, just deselect. we could do better if needed
        selectedRow.update(null);
        rows.destroy(row);
    }

    /**
     * Gets all previously added rows (read-only).
     */
    public Iterable<Row> getRows ()
    {
        return Iterables.transform(rows, TO_ROW);
    }

    /**
     * Gets the previously added row at the given position.
     */
    public Row getRow (int ii)
    {
        return (Row)rows.childAt(ii);
    }

    /**
     * Gets the number of rows in this table.
     */
    public int getRowCount ()
    {
        return rows.childCount();
    }

    public Ext ext ()
    {
        if (_ext == null) {
            _ext = new Ext();
        }
        return _ext;
    }

    @Override
    public void invalidate () {
        boolean wasValid = isSet(Flag.VALID);
        super.invalidate();
        if (wasValid) {
            header.invalidate();
        }
    }

    protected void drawGridLines (Surface surface)
    {
        if (_grid == null) {
            return;
        }
        int color = _grid.intValue();
        if (color == 0) {
            return;
        }
        surface.save();
        surface.setFillColor(color);
        float w = rows.size().width(), h = rows.size().height(), x = 0, y = 0;

        for (int colIdx = 0, numCols = _columns.size(); colIdx < numCols; colIdx++) {
            surface.drawLine(x, 0, x, h, 1);
            x += _columns.get(colIdx).width + _colgap;
        }
        surface.drawLine(x, 0, x, h, 1);

        for (int rowIdx = 0, numRows = getRowCount(); rowIdx < numRows; rowIdx++) {
            surface.drawLine(0, y, w, y, 1);
            y += getRow(rowIdx).laidOutHeight + _rowgap;
        }
        surface.drawLine(0, y, w, y, 1);

        surface.restore();
    }

    @Override
    protected Class<?> getStyleClass ()
    {
        return Table.class;
    }

    @Override
    protected LayoutData createLayoutData (float hintX, float hintY)
    {
        return new CompositeLayoutData() {
            @Override public void layout (float left, float top, float width, float height) {
                super.layout(left, top, width, height);
                _grid = resolveStyle(GRID);
            }
        };
    }

    protected float freeWeight ()
    {
        float freeWeight = 0;
        for (LayoutColumn lcol : _columns) {
            if (!lcol.column.fixed()) {
                freeWeight += lcol.column._weight;
            }
        }
        return freeWeight;
    }

    protected int findRowForClick (float y)
    {
        // binary search rows by y coordinate
        int low = 0, high = getRowCount() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            float cmp = y - getRow(mid).y();
            if (cmp > 0) {
                low = mid + 1;
            } else if (cmp < 0) {
                high = mid - 1;
            } else {
                low = mid + 1; // key found
                break;
            }
        }

        int row = low - 1;

        // don't go off the end
        if (row >= 0 && row == getRowCount() - 1) {
            Row last = getRow(row);
            if (y >= last.y() + last.size().height()) {
                row = -1;
            }
        }

        return row;
    }

    protected static final float sum (float[] values)
    {
        float total = 0;
        for (float value : values) {
            total += value;
        }
        return total;
    }

    protected static void remove (Elements<?> parent, int index, List<Element<?>> result)
    {
        result.add(parent.childAt(index));
        parent.removeAt(index);
    }

    /**
     * Layout for the Table itself. When computing size, takes into account the cells in
     * <code>header</code> and in <code>rows</code>, even though they are distant cousins.
     */
    protected class TableLayout extends Layout
    {
        @Override
        public Dimension computeSize (Container<?> elems, float hintX, float hintY)
        {
            Preconditions.checkArgument(elems == Table.this);
            Metrics m = computeMetrics(hintX, hintY);
            return new Dimension(m.totalWidth(_colgap), m.totalHeight(_rowgap));
        }

        @Override
        public void layout (Container<?> elems, float left, float top, float width, float height)
        {
            Preconditions.checkArgument(elems == Table.this);
            Metrics m = computeMetrics(width, height);

            // calculate the total width of the columns
            int columnCount = _columns.size();
            float naturalWidth = _colgap * (columnCount - 1);
            for (int cc = 0; cc < columnCount; cc++) {
                LayoutColumn lcol = _columns.get(cc);
                Column col = lcol.column;
                lcol.width = Math.max(col._min, Math.min(col._max,
                    Math.max(m.columnWidths[cc], col._preferred)));
                naturalWidth += lcol.width;
            }

            float freeExtraUnit = (width - naturalWidth) / freeWeight();

            // freeExtraUnit may end up negative; if our natural width is too wide

            // distribute any extra width
            for (int cc = 0; cc < columnCount; cc++) {
                LayoutColumn lcol = _columns.get(cc);
                Column col = lcol.column;
                lcol.width += col.fixed() ? 0 : freeExtraUnit * col._weight;
                lcol.width = Math.max(col._min, Math.min(col._max, lcol.width));
            }

            // store the row heights for use during child layouts
            header.laidOutHeight = m.rowHeights[0];
            for (int rr = 0, rowCount = rows.childCount(); rr < rowCount; rr++) {
                Row row = getRow(rr);
                row.laidOutHeight = m.rowHeights[rr + 1];
            }

            // set the header position on top
            setBounds(header, left, top, width, header.laidOutHeight);

            // and the row area on bottom
            float contentOffY = header.laidOutHeight + _rowgap;
            setBounds(rowArea, left, top + contentOffY, width, height - contentOffY);
        }

        protected Metrics computeMetrics (float hintX, float hintY)
        {
            Metrics metrics = new Metrics();
            metrics.hintX = hintX;
            metrics.hintY = hintY;

            int columns = _columns.size();
            metrics.columnWidths = new float[columns];
            metrics.rowHeights = new float[rows.childCount() + 1];

            // TODO: worry about invisible rows?

            // for fixed columns, calculate the widths and contribution to row heights
            accumFixed(metrics, header, 0);
            for (int cellRow = 0, rowCount = rows.childCount(); cellRow < rowCount; cellRow++) {
                accumFixed(metrics, getRow(cellRow), cellRow + 1);
            }

            // determine the total width needed by the fixed columns, then compute the hint given
            // to free columns based on the remaining space
            float fixedWidth = _colgap * (columns - 1); // start with gaps, add fixed col widths
            for (int cc = 0; cc < columns; cc++) {
                if (_columns.get(cc).column.fixed()) {
                    fixedWidth += metrics.columnWidths[cc];
                }
            }

            // now distribute remaining space amongst free columns and adjust row heights
            accumFree(metrics, header, fixedWidth, 0);
            for (int cellRow = 0, rowCount = rows.childCount(); cellRow < rowCount; cellRow++) {
                accumFree(metrics, getRow(cellRow), fixedWidth, cellRow + 1);
            }
            return metrics;
        }

        protected void accumFixed (Metrics metrics, Elements<?> cells, int row)
        {
            int columns = _columns.size();
            Preconditions.checkState(cells.childCount() == columns,
                "Table row %s has %s children, expected %s", row, cells.childCount(), columns);

            // compute the preferred size of the fixed columns
            float adjustedHintX = metrics.hintX;
            for (int col = 0; col < columns; col++) {
                Element<?> cell = cells.childAt(col);
                if (cell.isVisible() && _columns.get(col).column.fixed()) {
                    IDimension psize = preferredSize(cell, adjustedHintX, metrics.hintY);
                    metrics.rowHeights[row] = Math.max(metrics.rowHeights[row], psize.height());
                    metrics.columnWidths[col] = Math.max(metrics.columnWidths[col], psize.width());
                    if (adjustedHintX > 0) {
                        adjustedHintX = Math.max(1, adjustedHintX - psize.width() - _colgap);
                    }
                }
            }
        }

        protected void accumFree (Metrics metrics, Elements<?> cells, float fixedWidth, int row)
        {
            float freeUnitHintX = (metrics.hintX - fixedWidth) / freeWeight();

            int columnCount = _columns.size();
            for (int col = 0; col < columnCount; col++) {
                Element<?> cell = cells.childAt(col);
                Column column = _columns.get(col).column;
                if (cell.isVisible() && !column.fixed()) {
                    // TODO: supply sane y hint?
                    IDimension psize = preferredSize(
                        cell, freeUnitHintX * column._weight, metrics.hintY);
                    metrics.rowHeights[row] = Math.max(metrics.rowHeights[row], psize.height());
                    metrics.columnWidths[col] = Math.max(metrics.columnWidths[col], psize.width());
                }
            }
        }
    }

    /**
     * Data for TableLayout's size computation and layout.
     */
    protected static class Metrics
    {
        public float hintX, hintY;
        public float[] columnWidths;
        public float[] rowHeights;

        public float totalWidth (float gap)
        {
            return sum(columnWidths) + gap * (columnWidths.length - 1);
        }

        public float totalHeight (float gap)
        {
            return sum(rowHeights) + gap * (rowHeights.length - 1);
        }
    }

    /**
     * Bind an immutable column with our mutable width calculated during layout.
     */
    protected static class LayoutColumn
    {
        public final Column column;
        public float width;

        public LayoutColumn (Column column) {
            this.column = column;
        }
    }

    /**
     * Layout for the vertical stack of rows. This relies on the parent table being validated
     * and laid out, which is normally the case.
     */
    protected class ContentLayout extends Layout
    {
        @Override
        public Dimension computeSize (Container<?> elems, float hintX, float hintY)
        {
            Preconditions.checkArgument(elems == rows);
            Dimension size = new Dimension();

            // add height of each row
            for (Element<?> elem : elems) {
                Row row = (Row)elem;
                size.height += row.laidOutHeight;
            }

            // add width of each column
            for (LayoutColumn col : _columns) {
                size.width += col.width;
            }

            // take into account gaps
            size.height += (elems.childCount() - 1) * _rowgap;
            size.width += (_columns.size() - 1) * _colgap;
            return size;
        }

        @Override
        public void layout (Container<?> elems, float left, float top, float width, float height)
        {
            Preconditions.checkArgument(elems == rows);
            float y = top;
            for (int ii = 0, ll = elems.childCount(); ii < ll; ii++) {
                Row row = (Row)elems.childAt(ii);
                setBounds(row, left, y, width, row.laidOutHeight);
                y += row.laidOutHeight + _rowgap;
            }
        }
    }

    /**
     * Layout for <code>Row</code>. An instance of this is shared across all rows.
     */
    protected class RowLayout extends Layout
    {
        @Override
        public Dimension computeSize (Container<?> elems, float hintX, float hintY)
        {
            // ContentLayout doesn't allow this to be called
            throw new RuntimeException("Argh, computeSize is called?!");
        }

        @Override
        public void layout (Container<?> elems, float left, float top, float width, float height)
        {
            boolean header = elems == Table.this.header;
            for (int cc = 0, ll = _columns.size(); cc < ll; cc++) {
                LayoutColumn lcol = _columns.get(cc);
                Column col = lcol.column;
                float colWidth = lcol.width;
                Element<?> cell = elems.childAt(cc);

                if (colWidth > 0 && cell.isVisible()) {
                    IDimension psize = preferredSize(cell, 0, 0); // will be cached, hints ignored
                    float elemWidth = (col._stretch || header) ? colWidth : Math.min(psize.width(), colWidth);
                    float elemHeight = header ? height : Math.min(psize.height(), height);
                    setBounds(cell, left + col._halign.offset(elemWidth, colWidth),
                        top + _rowVAlign.offset(elemHeight, height), elemWidth, elemHeight);
                }

                left += colWidth + _colgap;
            }
        }
    }

    protected Connection _rowSelListener;
    protected final RowLayout _rowLayout = new RowLayout();
    protected final List<LayoutColumn> _columns = Lists.newArrayList();
    protected float _rowgap, _colgap;
    protected Style.VAlign _rowVAlign = Style.VAlign.CENTER;
    protected Ext _ext;
    protected Integer _grid;

    protected Function<Element<?>, Row> TO_ROW = new Function<Element<?>, Row>() {
        @Override public Row apply (Element<?> val) {
            return (Row)val;
        }
    };

    protected Function<LayoutColumn, Column> TO_COLUMN = new Function<LayoutColumn, Column>() {
        @Override public Column apply (LayoutColumn val) {
            return val.column;
        }
    };
}
