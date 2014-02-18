//
// SwingN - swing-like utilities and widgets for use with tripleplay
// Copyright (c) 2014, Cupric - All rights reserved.
// http://github.com/cupric/swingn/blob/master/LICENSE

package swingn.ui;

import java.util.List;

import com.google.common.collect.Lists;

import react.Signal;

/**
 * Abstraction for the items in a UI list and the propagation of changes to the items.
 */
public interface ListModel
{
    /**
     * The ways in which the content of a list may change.
     */
    enum ChangeType
    {
        CONTENTS_CHANGED, INTERVAL_ADDED, INTERVAL_REMOVED
    }

    /**
     * Describes a change that may occur to a list.
     */
    public class Event
    {
        public ListModel getSource ()
        {
            return _source;
        }

        public ChangeType getType ()
        {
            return _type;
        }

        public int getIndex0 ()
        {
            return _index0;
        }

        public int getIndex1 ()
        {
            return _index1;
        }

        public Event (ListModel source, ChangeType type, int index0, int index1)
        {
            _source = source;
            _type = type;
            _index0 = index0;
            _index1 = index1;
        }

        private final ListModel _source;
        private final ChangeType _type;
        private final int _index0;
        private final int _index1;
    }

    /**
     * A list model with an {@code Event} signal and some methods for emitting events on the
     * signal. Leaves the implementation of the data access methods to subclasses.
     */
    public static abstract class Default implements ListModel
    {
        public final Signal<Event> onChange = Signal.create();

        @Override
        public Signal<Event> onChange ()
        {
            return onChange;
        }

        protected void fireContentsChanged (int index0, int index1)
        {
            onChange.emit(new Event(this, ChangeType.CONTENTS_CHANGED, index0, index1));
        }

        protected void fireIntervalAdded (int index0, int index1)
        {
            onChange.emit(new Event(this, ChangeType.INTERVAL_ADDED, index0, index1));
        }

        protected void fireIntervalRemoved (int index0, int index1)
        {
            onChange.emit(new Event(this, ChangeType.INTERVAL_REMOVED, index0, index1));
        }
    }

    /**
     * A list model using a simple backing array list for the data.
     */
    public static class Generic<T> extends Default
    {
        @Override
        public int getSize ()
        {
            return _list.size();
        }

        @Override
        public Object getElementAt (int index)
        {
            return _list.get(index);
        }

        public T get (int idx)
        {
            return _list.get(idx);
        }

        public void add (T elem)
        {
            int size = _list.size();
            _list.add(elem);
            fireIntervalAdded(size, size);
        }

        public void remove (int idx)
        {
            _list.remove(idx);
            fireIntervalRemoved(idx, idx);
        }

        public void remove (T name)
        {
            remove(_list.indexOf(name));
        }

        public void clear ()
        {
            int size = _list.size();
            _list.clear();
            if (size > 0) {
                fireIntervalRemoved(0, size - 1);
            }
        }

        protected List<T> _list = Lists.newArrayList();
    }

    /**
     * Returns the current size of the backing list data.
     */
    int getSize();

    /**
     * Returns the element from the backing list data at the given index.
     */
    Object getElementAt(int index);

    /**
     * Returns the signal that will be dispatched whenever changes occur to the backing list data.
     */
    Signal<Event> onChange ();
}
