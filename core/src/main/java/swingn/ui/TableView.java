//
// SwingN - swing-like utilities and widgets for use with tripleplay
// Copyright (c) 2014, Cupric - All rights reserved.
// http://github.com/cupric/swingn/blob/master/LICENSE

package swingn.ui;

import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import playn.core.Events;
import playn.core.Layer;
import playn.core.Pointer;
import pythagoras.i.Point;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import react.Connection;
import react.Slot;
import react.UnitSlot;
import swingn.ui.table.Renderers;
import swingn.ui.table.Renderers.Cell;
import tripleplay.ui.Background;
import tripleplay.ui.Button;
import tripleplay.ui.Element;
import tripleplay.ui.Field;
import tripleplay.ui.Group;
import tripleplay.ui.Label;
import tripleplay.ui.MenuHost;
import tripleplay.ui.Style;
import tripleplay.ui.Styles;
import tripleplay.ui.Stylesheet;
import tripleplay.ui.layout.AxisLayout;
import tripleplay.util.Tapper;
import static swingn.Log.log;

/**
 * Populates and updates a {@link Table} using a {@link TableModel} instance. This is functionally
 * similar to swing's JTable.
 */
public class TableView
    implements Renderers.Provider
{
    /**
     * Creates a builder with header style classes bound to the given styles.
     */
    public static Stylesheet.Builder makeHeaderStyles (Styles styles)
    {
        return Stylesheet.builder().
                add(Label.class, styles).
                add(Button.class, styles).
                add(ElementsWidget.class, styles);
    }

    /**
     * Starts off a builder with some desirable bindings for table rows.
     */
    public static Stylesheet.Builder makeRowStyles ()
    {
        return Stylesheet.builder().
                add(Field.class, Style.BACKGROUND.is(Background.blank()));
    }

    /**
     * Pops up a tip by the given cell. This does some special things to account for the fact that
     * a {@code TipManager} is not running the show within tables (for performance reasons).
     */
    public static void popupCellTip (Element<?> cell, Element<?> tip, Events.Position event)
    {
        Tip wrapped = Tip.wrap(tip);

        // because the tip is not under the control of the TipManager, we need to flag
        // it as a menu so it will eat clicks and disappear on its own
        wrapped.setMenu(true);

        new MenuHost.Pop(cell.addStyles(Tip.STANDARD_TRIGGER), wrapped, event).popup();
    }

    /** The table that we are managing. */
    public final Table table;

    /**
     * Creates a new table view for the given model. The column headers are initialized during
     * construction, but row handling initiates when the {@link #table} member is added to a
     * display root.
     */
    public TableView (TableModel model)
    {
        table = new Table();
        _model = model;
        refreshColumns();

        table.hierarchyChanged().connect(new Slot<Boolean>() {
            @Override
            public void onEmit (Boolean added)
            {
                if (added) {
                    // start with a full refresh
                    if (_modelConn == null) {
                        refreshStructure();
                        _modelConn = _model.onChange().connect(_changeSlot);
                    }
                }
            }
        });
        table.rows.layer.addListener(_contentListener);
    }

    /**
     * Sets the given column to the new column. This is only for layout and does not update the
     * header cell contents.
     */
    public void setColumn (int colIdx, Table.Column newColumn)
    {
        table.ext().setColumn(colIdx, newColumn);
    }

    @Override
    public TableModel getModel ()
    {
        return _model;
    }

    /**
     * Gets the cell renderer for the given column.
     */
    public Renderers.Cell getColumnRenderer (int colIdx)
    {
        return _renderers[colIdx];
    }

    /**
     * Sets the column renderer for a column. If any rows are currently created, each of their
     * Nth children will be destroyed and re-created by the renderer.
     */
    public void setColumnRenderer (int colIdx, Renderers.Cell renderer)
    {
        _renderers[colIdx] = Preconditions.checkNotNull(renderer);
        recreateColumn(colIdx);
    }

    /**
     * Sets the renderer to use for column headers. Note that this should be called once when
     * setting up the table and view, prior to adding {@link #table} to the hierarchy.
     */
    public void setHeaderRenderer (Renderers.Header renderer)
    {
        _headerRenderer = renderer;
    }

    /**
     * Sets the renderer to use for all columns that do not have one set explicitly by a prior
     * call to {@link #setColumnRenderer(int, Cell)}.
     */
    public void setDefaultRenderer (Renderers.Cell renderer)
    {
        for (int colIdx = 0; colIdx < _renderers.length; colIdx++) {
            if (_renderers[colIdx] == _default) {
                setColumnRenderer(colIdx, renderer);
            }
        }
        _default = renderer;
    }

    /**
     * Gets the row renderer for this table view.
     */
    public Renderers.Row getRowRenderer ()
    {
        return _rowRenderer;
    }

    /**
     * Sets the row renderer for this table view.
     */
    public void setRowRenderer (Renderers.Row renderer)
    {
        _rowRenderer = renderer;
    }

    /**
     * Sets the height, in pixels, of a row as a hint to the table layout.
     */
    public void setRowHeight (int rowHeight)
    {
        // TODO: do we need fixed row height? currently preferred sizes are used
    }

    /**
     * Removes column cells from the underlying <code>Table</code>, but continues to access the
     * model for the remaining columns using the same indices.
     */
    public void removeColumn (int index)
    {
        table.ext().removeColumn(index);
        Renderers.Cell[] nrenderers = new Renderers.Cell[_renderers.length - 1];
        System.arraycopy(_renderers, 0, nrenderers, 0, index);
        System.arraycopy(_renderers, index + 1, nrenderers, index, nrenderers.length - index);
        _renderers = nrenderers;
        _removedColumns.add(modelColumnIndex(index));
    }

    @Override
    public boolean isCellEditable (int rowIdx, int colIdx)
    {
        return _model instanceof TableModel.Editable &&
                ((TableModel.Editable)_model).isCellEditable(rowIdx, colIdx);
    }

    @Override
    public UnitSlot cellChanger (final Element<?> cell)
    {
        return new UnitSlot() {
            @Override
            public void onEmit () {
                if (!_rendering) {
                    parse(cell);
                }
            }
        };
    }

    /**
     * Selects the given row when the table is added to a display hierarchy, which could
     * potentially be now. The row must be non-negative. To select a row normally, use
     * <code>this.table.selectedRow.update(row)</code>.
     */
    public void selectRowLater (int row)
    {
        Preconditions.checkArgument(row >= 0);
        if (_modelConn != null && row < table.getRowCount()) {
            table.selectedRow.update(row);
        } else {
            _pendingRowSelect = row;
        }
    }

    protected void popupHeaderTip (ElementsWidget<?> header, int colIdx, Events.Position event)
    {
        Group tip = new Group(AxisLayout.vertical());
        _headerRenderer.buildHeaderTip(tip, header.childAt(0), _model, modelColumnIndex(colIdx));
        popupCellTip(header, tip, event);
    }

    protected void parse (Element<?> cell)
    {
        Table.Row row = (Table.Row)cell.parent();
        int colIdx = Iterables.indexOf(row, Predicates.<Element<?>>equalTo(cell));
        int rowIdx = Iterables.indexOf(table.rows, Predicates.<Element<?>>equalTo(row));

        Renderers.Cell renderer = _renderers[colIdx];

        // TODO: should we catch exceptions here?
        Object value = renderer.parse(this, cell, rowIdx, colIdx);

        if (value == null) {
            // something went wrong, value couldn't be parsed
            // TODO: maybe refresh the cell here? show a warning box?
            return;
        }

        ((TableModel.Editable)_model).setValueAt(value, rowIdx, modelColumnIndex(colIdx));
    }

    protected void recreateColumn (int colIdx)
    {
        for (int rr = 0, nr = table.getRowCount(); rr < nr; rr++) {
            Table.Row row = table.getRow(rr);
            row.destroyAt(colIdx);
            row.add(colIdx, render(null, rr, colIdx));
        }
    }

    protected void refreshRow (int rowIdx)
    {
        Table.Row row = table.getRow(rowIdx);
        for (int colIdx = 0, ncols = viewColumnCount(); colIdx < ncols; colIdx++) {
            if (colIdx == row.childCount()) {
                row.add(render(null, rowIdx, colIdx));
            } else {
                render(row.childAt(colIdx), rowIdx, colIdx);
            }
        }
        _rowRenderer.update(this, row, rowIdx);

        if (_pendingRowSelect != null && rowIdx == _pendingRowSelect) {
            table.selectedRow.update(_pendingRowSelect);
            _pendingRowSelect = null;
        }
    }

    protected void refreshData ()
    {
        int nrows = _model.getRowCount();
        while (table.getRowCount() > nrows) {
            table.destroyRow(table.getRow(table.getRowCount() - 1));
        }
        for (int rowIdx = 0; rowIdx < nrows; rowIdx++) {
            if (rowIdx == table.getRowCount()) {
                table.addRow();
            }
            refreshRow(rowIdx);
        }
    }

    protected void refreshColumns ()
    {
        List<Table.Column> columns = Lists.newArrayList(table.getColumns());
        int ncols = viewColumnCount();
        // make sure we don't leave the old renderer lying around
        _renderers = Arrays.copyOf(_renderers, ncols);
        while (columns.size() > ncols) {
            table.ext().destroyColumn(columns.size() - 1);
        }
        for (int colIdx = 0; colIdx < ncols; colIdx++) {
            if (_renderers[colIdx] == null) {
                _renderers[colIdx] = _default;
            }
            ElementsWidget<?> header;
            if (colIdx >= columns.size()) {
                header = new ElementsWidget.Toggle(
                    AxisLayout.horizontal().stretchByDefault().offStretch());
                header.layer.addListener(_headerListener);
                table.addColumn(Table.COL.stretch(), header);
            } else {
                header = (ElementsWidget<?>)table.header.childAt(colIdx);
            }

            header.destroyAll();
            header.add(_headerRenderer.createHeader(_model, modelColumnIndex(colIdx)));
        }
    }

    protected void refreshStructure ()
    {
        refreshColumns();
        refreshData();
    }

    protected void handleChange (TableModel.Event event)
    {
        if (!table.isAdded()) {
            if (_modelConn != null) {
                _modelConn.disconnect();
                _modelConn = null;
            }
            return;
        }

        int first = event.resolveFirstRow(), last = event.resolveLastRow(_model);
        switch (event.type) {
        case STRUCTURE:
            // regenerate the whole shebang
            refreshStructure();
            break;
        case ROWS_DELETED:
            for (int rowIdx = last; rowIdx >= first; rowIdx--) {
                table.destroyRow(table.getRow(rowIdx));
            }
            break;
        case ROWS_ADDED:
            for (int rowIdx = first; rowIdx <= last; rowIdx++) {
                table.addRow(rowIdx);
                refreshRow(rowIdx);
            }
            break;
        case ALL_DATA:
            refreshData();
            break;
        case CELLS_UPDATED:
            if (event.column == -1) {
                for (int rowIdx = first; rowIdx <= last; rowIdx++) {
                    refreshRow(rowIdx);
                }
            } else {
                int colIdx = viewColumnIndex(event.column);
                for (int rowIdx = first; rowIdx <= last; rowIdx++) {
                    render(table.getRow(rowIdx).childAt(colIdx), rowIdx, colIdx);
                }
            }
            break;
        }
    }

    protected Element<?> render (Element<?> prev, int rowIdx, int colIdx)
    {
        _rendering = true;
        try {
            return getColumnRenderer(colIdx).render(this, prev,
                _model.getValueAt(rowIdx, modelColumnIndex(colIdx)), rowIdx, colIdx);
        } finally {
            _rendering = false;
        }
    }

    protected int viewColumnCount ()
    {
        return _model.getColumnCount() - _removedColumns.size();
    }

    /**
     * Returns the column index into the view for the given model index. The returned value may be
     * less than the passed in value if some columns have been removed from this view.
     */
    protected int viewColumnIndex (int colIdx)
    {
        int numRemovedBefore = 0;
        for (Integer removed : _removedColumns) {
            if (removed <= colIdx) {
                numRemovedBefore++;
            } else {
                break;
            }
        }
        return colIdx - numRemovedBefore;
    }

    /**
     * Returns the column index into our model for the given view index. The returned value may be
     * greater than the passed in value if some columns have been removed from this view.
     */
    protected int modelColumnIndex (int colIdx)
    {
        for (Integer removed : _removedColumns) {
            if (removed <= colIdx) {
                colIdx++;
            } else {
                break;
            }
        }
        return colIdx;
    }

    protected void showCellTip (Events.Position event, int rowIdx, int colIdx)
    {
        if (rowIdx < 0 || rowIdx >= table.getRowCount()) {
            log.warning("Uh oh, cell for tip out of range", "row", rowIdx, "col", colIdx);
            return;
        }
        Element<?> tip = getColumnRenderer(colIdx).createTip(
            this, _model.getValueAt(rowIdx, colIdx), rowIdx, colIdx);
        if (tip == null) {
            return;
        }

        popupCellTip(table.getRow(rowIdx).childAt(colIdx), tip, event);
    }

    protected Pointer.Listener _headerListener = new Tapper() {
        @Override
        public void onTap (Events.Position event) {
            for (int ii = 0, nn = table.header.childCount(); ii < nn; ii++) {
                ElementsWidget<?> header = (ElementsWidget<?>)table.header.childAt(ii);
                if (header.layer == event.hit()) {
                    /** todo: header tooltips? Originally had these for column names that
                     * are too short to see, since we don't do column resizing.
                    popupHeaderTip(header, ii, event); */
                    break;
                }
            }
        }
    };

    protected Pointer.Listener _contentListener = new Tapper() {
        @Override
        public void onTap (Events.Position event) {
            pythagoras.f.Point rowsPt = Layer.Util.layerToParent(event.hit(), table.rows.layer,
                event.localX(), event.localY());
            Point clicked = table.ext().findCell(rowsPt.x, rowsPt.y);
            if (clicked != null) {
                showCellTip(event, clicked.y, clicked.x);
            }
        }
    };

    protected final Slot<TableModel.Event> _changeSlot = new Slot<TableModel.Event>() {
        @Override public void onEmit (TableModel.Event event) {
            handleChange(event);
        }
    };

    protected TableModel _model;
    protected Connection _modelConn;
    protected Renderers.Cell _default = Renderers.DEFAULT;
    protected Renderers.Cell[] _renderers = {};
    protected Renderers.Header _headerRenderer = Renderers.DEFAULT_HEADER;
    protected Renderers.Row _rowRenderer = Renderers.DEFAULT_ROW;
    protected boolean _rendering;
    protected Integer _pendingRowSelect;
    protected SortedSet<Integer> _removedColumns = new TreeSet<Integer>();
}
