//
// SwingN - swing-like utilities and widgets for use with tripleplay
// Copyright (c) 2014, Cupric - All rights reserved.
// http://github.com/cupric/swingn/blob/master/LICENSE

package swingn.ui;

import java.util.Arrays;
import java.util.Map;

import pythagoras.f.Dimension;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import react.Connection;
import react.Slot;
import react.Value;
import swingn.ui.ListModel.Event;
import tripleplay.ui.Element;
import tripleplay.ui.Elements;
import tripleplay.ui.Layout;
import tripleplay.ui.Selector;
import tripleplay.ui.ToggleButton;
import tripleplay.ui.layout.AxisLayout;
import tripleplay.util.Objects;

/**
 * Simple {@code Elements} implementation associating each of its children with a provided object.
 * For example, each widget in a list could be associated with a command object that is dispatched
 * to a game control mechanism.
 * <p>{@link ToggleButton} is the canonical list box widget. Unlike swing, the individual widgets
 * contained in the list box perform their own pointer handling and selection logic. Furthermore,
 * scrolling is not handled at all within this implementation. Typically, a {@code ListBox} is
 * set as the content of a {@link tripleplay.ui.Scroller} instance, but this is not assumed
 * or required.</p>
 * TODO: integrate with scrolling somehow
 * TODO: optimize allocation of elements (for large lists)
 * TODO: multiple selection?
 * TODO: drag outside elements to change selection
 * TODO: special pointer/mouse support
 * @param <I> the type of object associated with each list box child element
 */
public class ListBox<I> extends Elements<ListBox<I>>
{
    /**
     * Creates an item renderer that, for a source item, produces a {@link ToggleButton} with text
     * set to the result of the given function call on the item.
     */
    public static <T> Renderer<T> labels (final Function<? super T, String> fn)
    {
        return new Renderer<T>() {
            public Element<?> create (Element<?> previous, T item) {
                if (previous == null) {
                    previous = new ToggleButton(fn.apply(item));
                } else {
                    ((ToggleButton)previous).text.update(fn.apply(item));
                }
                return previous;
            }
        };
    }

    /**
     * Creates an item renderer that, for a source item, produces a {@link ToggleButton} with text
     * set to the result of the source item's {@code toString()} method.
     */
    public static <T> Renderer<T> labels ()
    {
        return ListBox.<T>labels(Functions.toStringFunction());
    }

    /**
     * Creates a list box of {@link ToggleButton} elements, each of which is associated with
     * its own text.
     */
    public static ListBox<String> ofStrings (Layout layout)
    {
        Renderer<String> labels = labels();
        return new ListBox<String>(labels, layout);
    }

    /**
     * Creates a new list box with the given item renderer and a vertical layout.
     */
    public static <I> ListBox<I> create (Renderer<I> renderer)
    {
        return new ListBox<I>(renderer);
    }

    /**
     * Creates a new list box with the given item renderer and layout.
     */
    public static <I> ListBox<I> create (Renderer<I> renderer, Layout layout)
    {
        return new ListBox<I>(renderer, layout);
    }

    public final Selector selector = new Selector(this, null);

    /**
     * Renders a list box item into an {@code Element} instance.
     * @param <I> the type of the item the renderer handles
     */
    public interface Renderer<I>
    {
        /**
         * Creates a new element from the given item. If {@code previous} is not null, the
         * implementation may choose to repopulate and return it rather than creating a new
         * element.
         * <p>NOTE: the returned element must provide its own pointer handling and selection
         * logic, so that it may be connected properly to the {@link Selector}. See
         * {@link ToggleButton} for how this may be achieved.</p>
         * @param item the object to be rendered
         * @param previous a previously created element no longer in the list box, may be null
         * @return an {@code Element} configured to display the item, or a repopulated
         * {@code previous} if appropriate
         */
        Element<?> create (Element<?> previous, I item);
    }

    /**
     * Creates a new list box with the given item renderer and a vertical layout with no gaps.
     */
    public ListBox (Renderer<I> renderer)
    {
        this(renderer, AxisLayout.vertical().gap(0));
    }

    /**
     * Creates a new list box with the given item renderer and layout.
     */
    public ListBox (Renderer<I> renderer, Layout layout)
    {
        super(layout);
        _renderer = Preconditions.checkNotNull(renderer);
        selector.selected.connect(new Slot<Element<?>>() {
            @Override public void onEmit (Element<?> event) {
                _selectedItem.update(_itemMap.get(event));
            }
        });
        _selectedItem.connect(new Slot<I>() {
            @Override public void onEmit (I event) {
                Element<?> sel = null;
                if (event != null) {
                    for (Element<?> child : ListBox.this) {
                        if (Objects.equal(_itemMap.get(child), event)) {
                            sel = child;
                        }
                    }
                }
                selector.selected.update(sel);
            }
        });
    }

    /**
     * Sets up this list box to track the contents of the given model. Note that any subsequent
     * attempts to modify the list box contents will have undefined effects.
     */
    public void connect (final ListModel model)
    {
        // TODO: does ListModel need to be more fleshed out as a member?
        // TODO: allow garbage collection of the list box sooner
        final Connection[] conn = {null};

        final Slot<ListModel.Event> eventSlot = new Slot<Event>() {
            @Override public void onEmit (Event event) {
                if (!isAdded()) {
                    // if we are no longer in the hierarchy and get a model change, disconnect
                    // the signal so the list box can be garbage collected
                    // TODO: allow GC sooner?
                    if (conn[0] != null) {
                        conn[0].disconnect();
                        conn[0] = null;
                        return;
                    }
                }
                updateItems(event);
            }
        };

        Slot<Boolean> addRemove = new Slot<Boolean>() {
            @Override public void onEmit (Boolean event) {
                if (event) {
                    if (conn[0] == null) {
                        setItems(model);
                        conn[0] = model.onChange().connect(eventSlot);
                    }
                }
            }
        };

        hierarchyChanged().connect(addRemove);
        addRemove.onEmit(isAdded());
    }

    /**
     * Gets the index of an item in the list box.
     */
    public int indexOf (I item)
    {
        for (int ii = 0, nn = childCount(); ii < nn; ii++) {
            if (_itemMap.get(childAt(ii)).equals(item)) {
                return ii;
            }
        }
        return -1;
    }

    /**
     * Gets the item at the given position.
     */
    public I itemAt (int index)
    {
        return _itemMap.get(childAt(index));
    }

    /**
     * Gets the renderer used for this list box.
     */
    public Renderer<I> renderer ()
    {
        return _renderer;
    }

    /**
     * Gets the value containing the currently selected item. This will be updated when some child
     * element is selected with the child's associated item.
     */
    public Value<I> selected ()
    {
        return _selectedItem;
    }

    /**
     * Clears the selection such that {@code selected().get()} is null.
     */
    public void clearSelection ()
    {
        selector.selected.update(null);
    }

    /**
     * Sets the content of the list to the given items, rendering if appropriate.
     */
    public void setItems (I... items)
    {
        setItems(Arrays.asList(items));
    }

    /**
     * Sets the content of the list to the given items, rendering if appropriate.
     */
    public void setItems (Iterable<I> items)
    {
        removeAll();
        Preconditions.checkState(_itemMap.isEmpty());
        for (I item : items) {
            Element<?> elem = _renderer.create(null, item);
            add(elem);
            _itemMap.put(elem,  item);
        }
    }

    /**
     * Adds an item to the list.
     */
    public void addItem (I item)
    {
        Element<?> elem = _renderer.create(null, Preconditions.checkNotNull(item));
        add(elem);
        _itemMap.put(elem,  item);
    }

    /**
     * Inserts an item into the list at a given index.
     */
    public void addItem (int idx, I item)
    {
        Element<?> elem = _renderer.create(null, Preconditions.checkNotNull(item));
        add(idx, elem);
        _itemMap.put(elem,  item);
    }

    /**
     * Removes the item at the given index.
     */
    public void removeItem (int idx)
    {
        Element<?> elem = childAt(idx);
        if (selector.selected.get() == elem) {
            selector.selected.update(null);
        }
        destroyAt(idx);
    }

    /**
     * Provides a minimum width to be used when laying out the list box.
     */
    public ListBox<I> setMinWidth (float minWidth)
    {
        _minWidth = minWidth;
        invalidate();
        return this;
    }

    @Override
    protected LayoutData createLayoutData (float hintX, float hintY)
    {
        return new SizableLayoutData(super.createLayoutData(hintX, hintY),
            new Dimension(_minWidth != null ? _minWidth : 0, 0)).forWidth(Take.MAX);
    }

    @Override
    protected Class<?> getStyleClass ()
    {
        return ListBox.class;
    }

    @Override
    protected void didRemove (Element<?> child, boolean destroy)
    {
        super.didRemove(child, destroy);
        _itemMap.remove(child);
    }

    protected void setItems (ListModel model)
    {
        destroyAll();
        for (int ii = 0, nn = model.getSize(); ii < nn; ii++) {
            @SuppressWarnings("unchecked")
            I obj = (I)model.getElementAt(ii);
            addItem(obj);
        }
    }

    protected void updateItems (ListModel.Event event)
    {
        int i0 = event.getIndex0(), i1 = event.getIndex1();
        switch (event.getType()) {
        case CONTENTS_CHANGED:
            for (int idx = i0; idx <= i1; idx++) {
                @SuppressWarnings("unchecked")
                I obj = (I)event.getSource().getElementAt(idx);
                Element<?> elem = childAt(idx);
                _renderer.create(elem, obj);
                _itemMap.put(elem, obj);
            }
            break;
        case INTERVAL_ADDED:
            for (int idx = i0; idx <= i1; idx++) {
                @SuppressWarnings("unchecked")
                I obj = (I)event.getSource().getElementAt(idx);
                addItem(idx, obj);
            }
            break;
        case INTERVAL_REMOVED:
            for (int idx = i1; idx >= i0; idx--) {
                removeItem(idx);
            }
            break;
        }
    }

    protected final Renderer<I> _renderer;
    protected final Value<I> _selectedItem = Value.create(null);
    protected final Map<Element<?>, I> _itemMap = Maps.newHashMap();
    protected Float _minWidth;
}
