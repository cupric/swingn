//
// SwingN - swing-like utilities and widgets for use with tripleplay
// Copyright (c) 2014, Shamtasm - All rights reserved.
// http://github.com/shamtasm/swingn/blob/master/LICENSE

package swingn.ui;

import java.util.Arrays;
import java.util.List;

import playn.core.Pointer;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import react.Connection;
import react.Slot;
import react.Value;
import react.ValueView;
import tripleplay.ui.Behavior;
import tripleplay.ui.Button;
import tripleplay.ui.Composite;
import tripleplay.ui.Element;
import tripleplay.ui.Elements;
import tripleplay.ui.Field;
import tripleplay.ui.Icon;
import tripleplay.ui.Menu;
import tripleplay.ui.MenuHost;
import tripleplay.ui.MenuItem;
import tripleplay.ui.Style;
import tripleplay.ui.layout.AxisLayout;

/**
 * A combo box is a group with a button and an optional text field. When pressed, the button shows
 * a menu containing a list of options. If an option is selected, it becomes the text of the
 * button.
 * </pre></code>
 * The layout of the combo is pretty simple, there are two possible configurations:<p><ol><li>
 *
 * Editable. <p>The field is to the left of the button and stretched. When a menu selection is made,
 * the field's text is updated to the text of the selected item. The button text is not affected
 * by menu selection.</p><p><code><pre>
 * ---------------------------------------
 * |  Field                     | Button |
 * ---------------------------------------
 * </pre></code></p></li><li>
 *
 * Fixed choices. <p>The button is stretched and attempts to size its width to the width of the
 * {@link #menu}. When a menu selection is made, the button's text field is updated to the the text
 * of the selected item.</p><p><code><pre>
 * ------------------------
 * | Button               |
 * ------------------------
 * </pre></code></p></li></ol></p>
 */
public class ComboBox extends Composite<ComboBox>
{
    /**
     * Implemented by a {@link Menu} that keeps its items in some child group, not (necessarily)
     * in itself. For example, a paged menu could have some paging buttons and a separate group
     * that contains the menu items.
     */
    public interface ComplexMenu
    {
        /** Gets the parent for menu items. The combo box will add all resolved menu items to
         * this container. */
        Elements<?> getItemParent ();
    }

    /**
     * Creates a simple vertical menu suitable for use with a ComboBox.
     */
    public static Menu createDefaultMenu ()
    {
        return new Menu(AxisLayout.vertical().offStretch().gap(1), Style.HALIGN.left);
    }

    /** The menu of options, triggered when the button is clicked. */
    public final Menu menu;

    /** The layout container for the items, which may be different from the {@link #menu} if
     * {@link ComplexMenu} is implemented. */
    public final Elements<?> menuItems;

    /** The button of the combo box, triggers the menu. If our choices are fixed, the text reflects
     * the current selection. */
    public final Button button;

    /**
     * Creates a new empty combo box with a simple vertical menu.
     */
    public ComboBox ()
    {
        this(createDefaultMenu());
    }

    /**
     * Creates a new empty combo box with the given menu.
     */
    public ComboBox (Menu menu)
    {
        setLayout(AxisLayout.horizontal());
        addStyles(Style.VALIGN.center);

        _choices = Lists.newArrayList();

        this.menu = menu;
        menu.itemTriggered().connect(new Slot<MenuItem>() {
            @Override public void onEmit (MenuItem event) {
                for (int ii = 0, ll = menuItems.childCount(); ii < ll; ii++) {
                    if (menuItems.childAt(ii) == event) {
                        select(ii);
                    }
                }
            }
        });

        button = new MenuButton();
        button.addStyles(Behavior.Click.DEBOUNCE_DELAY.is(0));
        initChildren(button.setConstraint(AxisLayout.stretched()));

        menuItems = menu instanceof ComplexMenu ? ((ComplexMenu)menu).getItemParent() : menu;
    }

    /**
     * Creates a new combo box with the given choices.
     */
    public ComboBox (List<String> choices)
    {
        this();
        Preconditions.checkArgument(!choices.isEmpty());
        addChoices(choices);
    }

    /**
     * Sets the combo box's to use the icon of the selected item. Note that this will override
     * any icon marker that a menu is present (the right pointing triangle).
     */
    public ComboBox useIconInButton ()
    {
        _useIconInButton = true;
        return this;
    }

    /**
     * Convenience method to create a new {@link Field} and call {@link #setEditable(Field)}.
     */
    public ComboBox setEditable ()
    {
        return setEditable(new Field());
    }

    /**
     * Sets the combo box to be editable, using the given field for the editing widget. This may
     * only be called once, prior to adding.
     */
    public ComboBox setEditable (Field field)
    {
        Preconditions.checkState(!isEditable(), "Already editable");
        setChildren(Arrays.<Element<?>>asList(
            field.setConstraint(AxisLayout.stretched()), button), false);
        button.setConstraint(AxisLayout.fixed());
        return this;
    }

    /**
     * Tests if this combo box has an edit field.
     */
    public boolean isEditable ()
    {
        return childCount() == 2;
    }

    /**
     * Gets the test field for this combo box, or null of it isn't editable.
     */
    public Field field ()
    {
        return isEditable() ? (Field)childAt(0) : null;
    }

    /**
     * The index of the selected item in the combo box. This gets updated to the index of the
     * currently selected choice, or -1 if there is no current selection, or the edit field has
     * a text value that is not in the list.
     */
    public ValueView<Integer> selectedIdx ()
    {
        return _selectedIndex;
    }

    /**
     * The selected item in the combo box. This gets updated to the currently selected choice, or
     * {@code null} if there is no current selection, or the edit field has a text value that is
     * not in the list.
     */
    public ValueView<String> selectedValue ()
    {
        return _selectedValue;
    }

    /**
     * The text in the combo box. This is the field text for editable boxes, or the button text
     * for fixed choices.
     * @return
     */
    public ValueView<String> text ()
    {
        return isEditable() ? field().text : button.text;
    }

    /**
     * Selected the choice with the given index.
     */
    public ComboBox select (int idx)
    {
        String choice = idx == -1 ? null : _choices.get(idx);
        (isEditable() ? field().text : button.text).update(choice);
        if (_useIconInButton) {
            button.icon.update(widget(idx).icon.get());
        }
        _selectedIndex.update(idx);
        _selectedValue.update(choice);
        return this;
    }

    /**
     * Selects the choice with the given value. Throws a runtime exception if the choice is not
     * in the list of choices.
     */
    public ComboBox select (String choice)
    {
        return select(_choices.indexOf(choice));
    }

    /**
     * Adds a new choice to the menu.
     */
    public ComboBox addChoice (String choice)
    {
        return addChoice(choice, null);
    }

    /**
     * Adds a new choice to the menu, with an icon.
     */
    public ComboBox addChoice (String choice, Icon icon)
    {
        _choices.add(choice);
        menuItems.add(new MenuItem(choice, icon));
        return this;
    }

    /**
     * Adds a new icon to the menu, with no text.
     */
    public ComboBox addChoice (Icon icon)
    {
        return addChoice("", icon);
    }

    /**
     * Adds each of the given choices to the menu.
     */
    public ComboBox addChoices (Iterable<String> choices)
    {
        for (String choice : choices) {
            addChoice(choice);
        }
        return this;
    }

    public int choiceCount ()
    {
        return _choices.size();
    }

    /**
     * Gets the menu item widget for the previously added choice of the given index, or null if
     * the index is -1.
     */
    public MenuItem widget (int index)
    {
        return index == -1 ? null : ((MenuItem)menuItems.childAt(index));
    }

    /**
     * Gets the menu item widget for the given previously added choice, or null if there is no
     * such choice.
     */
    public MenuItem widget (String choice)
    {
        return widget(_choices.indexOf(choice));
    }

    /**
     * Removes the choice with the given index from the menu. If this is the current selection,
     * then the current selection is cleared.
     */
    public ComboBox removeChoice (int idx)
    {
        _choices.remove(idx);
        menuItems.removeAt(idx);
        if (idx == _selectedIndex.get()) {
            _selectedIndex.update(-1);
            _selectedValue.update(null);
        }
        return this;
    }

    /**
     * Removes all menu items from the combo box.
     */
    public ComboBox removeAllChoices ()
    {
        _choices.clear();
        menuItems.removeAll();
        _selectedIndex.update(-1);
        _selectedValue.update(null);
        return this;
    }

    /**
     * Removes the given choice from the menu. If the choice is not in the menu, has no effect.
     */
    public ComboBox removeChoice (String choice)
    {
        int idx = _choices.indexOf(choice);
        if (idx >= 0) {
            removeChoice(idx);
        }
        return this;
    }

    @Override
    protected Class<?> getStyleClass ()
    {
        return ComboBox.class;
    }

    /** A button for combo boxes. TODO: layout so that width is that of the menu. */
    protected class MenuButton extends Button
    {
        @Override
        protected Behavior<Button> createBehavior () {
            return new Behavior.Click<Button> (this) {
                @Override
                protected void onPress (Pointer.Event event) {
                    new MenuHost.Pop(MenuButton.this, menu, event).
                        retainMenu().relayEvents(layer).popup();
                    super.onPress(event);
                }
            };
        }
    }

    protected Value<Integer> _selectedIndex = Value.create(-1);
    protected Value<String> _selectedValue = Value.create(null);
    protected List<String> _choices;
    protected Connection _hostConnection;
    protected boolean _useIconInButton;
}
