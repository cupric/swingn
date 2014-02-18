//
// SwingN - swing-like utilities and widgets for use with tripleplay
// Copyright (c) 2014, Cupric - All rights reserved.
// http://github.com/cupric/swingn/blob/master/LICENSE

package swingn.ui;

import react.Signal;

/**
 * Describes data with a tabular structure and provides a means of communicating changes.
 */
public interface TableModel
{
    /**
     * Ways in which the backing data may change.
     */
    public static enum ChangeType {
        /** The columns or their ordering has been updated. */
        STRUCTURE,

        /** The table data has changed completely, or at least so much that there's no point in
         * performing an incremental update. */
        ALL_DATA,

        /** A contiguous span of one or more rows has been added. */
        ROWS_ADDED,

        /** A contiguous span of one or more rows has been deleted. */
        ROWS_DELETED,

        /** A contiguous span of one or more rows has changed, either in one column or all columns.
         * (To indicate multiple columns, multiple events are sent.) */
        CELLS_UPDATED
    }

    /**
     * Encapsulates a single change to the backing data.
     */
    public static class Event
    {
        /** The type of change, or null for a structural change. */
        public final ChangeType type;

        /** The row span that has changed. If both fields are -1, all rows have changed. If
         * the last is less than the first, no rows have changed.*/
        public final int firstRow, lastRow;

        /** The column that has changed, or -1 to indicate all columns. */
        public final int column;

        /**
         * Creates a new event of the given type, covering the entire table.
         */
        public Event (ChangeType type)
        {
            this(type, -1, -1, -1);
        }

        /**
         * Creates a new event with the specified type and coverage of the table.
         * @param type type type of change
         * @param firstRow the first row affected, or -1 for all rows
         * @param lastRow the last row affected, or -1 for all rows
         * @param column the column affected, or -1 for all columns
         */
        public Event (ChangeType type, int firstRow, int lastRow, int column)
        {
            this.type = type;
            this.firstRow = firstRow;
            this.lastRow = lastRow;
            this.column = column;
        }

        /**
         * Tests if this event applies to all rows.
         */
        public boolean isAllRows ()
        {
            return firstRow == -1 && lastRow == -1;
        }

        /**
         * Gets the first affected row.
         */
        public int resolveFirstRow ()
        {
            return isAllRows() ? 0 : firstRow;
        }

        /**
         * Gets the last affected row.
         */
        public int resolveLastRow (TableModel model)
        {
            return isAllRows() ? model.getRowCount() - 1 : lastRow;
        }
    }

    /**
     * Partially implements {@code TableModel}, providing the event signal and some convenience
     * methods for emitting events.
     */
    public static abstract class Default implements TableModel
    {
        /** Emits change events. */
        public final Signal<Event> onChange = Signal.create();

        @Override
        public Signal<Event> onChange ()
        {
            return onChange;
        }

        protected void fireTableStructureChanged ()
        {
            onChange.emit(new Event(ChangeType.STRUCTURE));
        }

        protected void fireTableDataChanged ()
        {
            onChange.emit(new Event(ChangeType.ALL_DATA));
        }

        protected void fireTableRowsInserted (int from, int to)
        {
            onChange.emit(new Event(ChangeType.ROWS_ADDED, from, to, -1));
        }

        protected void fireTableRowsDeleted (int from, int to)
        {
            onChange.emit(new Event(ChangeType.ROWS_DELETED, from, to, -1));
        }

        protected void fireTableRowsUpdated (int from, int to)
        {
            fireTableCellsUpdated(from, to, -1);
        }

        protected void fireTableCellsUpdated (int from, int to, int column)
        {
            onChange.emit(new Event(ChangeType.CELLS_UPDATED, from, to, column));
        }
    }

    /**
     * An extension to the table model to allow editing.
     */
    public interface Editable extends TableModel
    {
        /**
         * Tests if the backing data may be edited at the given cell.
         */
        boolean isCellEditable (int rowIndex, int columnIndex);

        /**
         * Sets the value in the backing data at the given cell to the given value.
         */
        void setValueAt (Object aValue, int rowIndex, int columnIndex);
    }

    /**
     * Gets the number of rows in the data.
     */
    int getRowCount ();

    /**
     * Gets the number of columns in the data.
     */
    int getColumnCount();

    /**
     * Gets the value from the data at the given cell.
     */
    Object getValueAt (int rowIndex, int columnIndex);

    /**
     * Gets the name of a column.
     */
    String getColumnName (int columnIndex);

    /**
     * Gets the class of the given column. This is used to determine how to display the values of
     * cells in that column.
     */
    Class<?> getColumnClass (int columnIndex);

    /**
     * A signal emitted when the backing data changes. For example, in response to network events,
     * or editing via {@link Editable#setValueAt(Object, int, int)}.
     */
    Signal<Event> onChange ();
}
