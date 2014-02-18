//
// SwingN - swing-like utilities and widgets for use with tripleplay
// Copyright (c) 2014, Cupric - All rights reserved.
// http://github.com/cupric/swingn/blob/master/LICENSE

package swingn.ui;

import com.google.common.base.Preconditions;

import tripleplay.ui.Element;
import tripleplay.ui.Label;
import tripleplay.ui.Layout;
import tripleplay.ui.Menu;
import tripleplay.ui.MenuHost;
import tripleplay.ui.Style;
import tripleplay.ui.TextWidget;
import tripleplay.ui.layout.AxisLayout;
import tripleplay.ui.util.BoxPoint;

/**
 * Widget for displaying tooltips on elements, and static management and utility methods.
 */
public class Tip extends Menu
{
    public static Style.Binding<MenuHost.TriggerPoint> STANDARD_TRIGGER =
            MenuHost.TRIGGER_POINT.is(MenuHost.relative(BoxPoint.TR));

    /**
     * Provides a tip.
     */
    public interface Source
    {
        /**
         * Creates a tip, or returns null to indicate no tip should be shown. Called when an
         * element is tapped or moused over.
         * @param menu if the trigger was tapped. If set, the generated tip should
         * include any selectable options
         */
        Tip createTip (boolean menu);
    }

    /**
     * Sets the tooltip for an element that is also a {@link Source}.
     */
    public static <T extends Element<?> & Source> void setToolTip (T elem)
    {
        setToolTip(elem, elem);
    }

    /**
     * Sets the tooltip for an element using the tip from the given source.
     */
    public static void setToolTip (Element<?> elem, Source source)
    {
        boolean isInteractive = elem instanceof TextWidget &&
                ((TextWidget<?>)elem).layer.interactive();

        // Tapping on a Button, CheckBox, etc, cannot show a tip, for obvious reasons.
        // This *could* allow tips if a mouse is present, but instead, be consistent, for easier
        // testing.
        if (isInteractive) {
            return;
        }

        TipManager.get().setTip(elem, source);
    }

    /**
     * Creates a new source for a simple, textual tooltip.
     */
    public static Source textOnly (final String text)
    {
        return new Source() {
            @Override public Tip createTip (boolean menu) {
                Tip tip = new Tip(AxisLayout.horizontal());
                tip.add(new Label(text));
                return tip;
            }
        };
    }

    /**
     * Sets the tooltip for an element to the given text.
     */
    public static void setToolTipText (Element<?> elem, String text)
    {
        setToolTip(elem, textOnly(text));
    }

    /**
     * Wraps a widget inside a tip, for use by {@link Source} implementations.
     */
    public static Tip wrap (Element<?> elem)
    {
        Tip tip = new Tip(AxisLayout.vertical());
        tip.add(elem);
        return tip;
    }

    /** Optional title of the tooltip; this may have a custom style. */
    public final Label title;

    /**
     * Creates a new tip with the given layout and no title.
     */
    public Tip (Layout layout)
    {
        this(layout, null);
    }

    /**
     * Creates a new tip with the given layout and title.
     */
    public Tip (Layout layout, String title)
    {
        super(layout);
        this.title = title == null ? null : new Label(title);
    }

    protected void setMenu (boolean menu)
    {
        Preconditions.checkState(_menu == null);
        _menu = menu;
    }

    @Override
    protected boolean absorbHits ()
    {
        return _menu != null && _menu;
    }

    @Override
    protected Class<?> getStyleClass ()
    {
        return Tip.class;
    }

    /** Whether this tip stays open, absorbs hits and allows item selection. */
    protected Boolean _menu;
}
