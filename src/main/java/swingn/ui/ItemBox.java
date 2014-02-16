//
// SwingN - swing-like utilities and widgets for use with tripleplay
// Copyright (c) 2014, Shamtasm - All rights reserved.
// http://github.com/shamtasm/swingn/blob/master/LICENSE

package swingn.ui;

import java.util.List;

import react.Slot;
import react.Value;
import react.ValueView;
import tripleplay.ui.Menu;
import tripleplay.ui.MenuItem;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Lists;

public class ItemBox<T> extends ComboBox
{
    public ItemBox ()
    {
        this(Functions.toStringFunction());
    }

    public ItemBox (Function<? super T, String> labeller)
    {
        this(labeller, ComboBox.createDefaultMenu());
    }

    public ItemBox (Menu menu)
    {
        this(Functions.toStringFunction(), menu);
    }

    public ItemBox (Function<? super T, String> labeller, Menu menu)
    {
        super(menu);
        _labeller = labeller;
        selectedIdx().connect(new Slot<Integer>() {
            @Override public void onEmit (Integer event) {
                _selectedItem.update(event == -1 ? null : _items.get(event));
            }
        });
    }

    public ItemBox (List<T> items)
    {
        this();
        addItems(items);
    }

    public ValueView<T> selectedItem ()
    {
        return _selectedItem;
    }

    @Override
    public ItemBox<T> addChoice (String choice)
    {
        super.addChoice(choice);
        _items.add(null);
        return this;
    }

    @Override
    public ItemBox<T> removeChoice (int idx)
    {
        _items.remove(idx);
        super.removeChoice(idx);
        return this;
    }

    public ItemBox<T> addItem (T item)
    {
        _items.add(item);
        super.addChoice(_labeller.apply(item));
        return this;
    }

    public void removeItem(T item)
    {
        int idx = _items.indexOf(item);
        if (idx != -1) {
            removeChoice(idx);
        }
    }

    @Override
    public ItemBox<T> removeAllChoices ()
    {
        _items.clear();
        super.removeAllChoices();
        return this;
    }

    public void removeAllItems ()
    {
        removeAllChoices();
    }

    public ItemBox<T> addItems (Iterable<? extends T> items)
    {
        for (T item : items) {
            addItem(item);
        }
        return this;
    }

    /**
     * Gets the number of items in this box.
     */
    public int itemCount ()
    {
        return _items.size();
    }

    /**
     * Gets the menu item widget for the given previously added item, or null if there is no such item.
     */
    public MenuItem widget (T item)
    {
        return widget(_items.indexOf(item));
    }

    public ItemBox<T> selectItem (T item)
    {
        int index = _items.indexOf(item);
        if (index == -1) {
            if (isEditable()) {
                field().text.update(_labeller.apply(item));
            }
        } else {
            select(index);
        }
        return this;
    }

    @Override
    protected Class<?> getStyleClass ()
    {
        return getClass().getSuperclass();
    }

    protected List<T> _items = Lists.newArrayList();
    protected Function<? super T, String> _labeller;
    protected Value<T> _selectedItem = Value.create(null);
}
