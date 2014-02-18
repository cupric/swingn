//
// SwingN - swing-like utilities and widgets for use with tripleplay
// Copyright (c) 2014, Cupric - All rights reserved.
// http://github.com/cupric/swingn/blob/master/LICENSE

package swingn.ui;

import playn.core.Connection;
import react.SignalView;
import react.Value;
import tripleplay.ui.Behavior;
import tripleplay.ui.Clickable;
import tripleplay.ui.Elements;
import tripleplay.ui.Layout;
import tripleplay.ui.Togglable;
import tripleplay.util.Layers;

/**
 * An {@code Elements} implementation that is interactive and handles pointer events using a
 * {@link Behavior} instance. This is similar to a {@link tripleplay.ui.Widget}, but also
 * contains other elements. Unlike {@code Widget}, the behavior may be set publicly, after
 * construction.
 */
public abstract class ElementsWidget<T extends Elements<T>> extends Elements<T>
{
    public static class Button extends ElementsWidget<Button>
        implements Clickable<Button>
    {
        public Button (Layout layout)
        {
            super(layout);
            setBehavior(new Behavior.Click<Button>(asT()));
        }

        @Override
        public SignalView<Button> clicked ()
        {
            return asClick().clicked;
        }

        @Override
        public void click ()
        {
            asClick().click();
        }

        @Override
        protected Class<?> getStyleClass ()
        {
            return Button.class;
        }

        protected Behavior.Click<Button> asClick ()
        {
            return (Behavior.Click<Button>)_behave;
        }
    }

    public static class Toggle extends ElementsWidget<Toggle>
        implements Togglable<Toggle>
    {
        public Toggle (Layout layout)
        {
            super(layout);
            setBehavior(new Behavior.Toggle<Toggle>(asT()));
        }

        @Override
        public SignalView<Toggle> clicked ()
        {
            return asToggle().clicked;
        }

        @Override
        public void click ()
        {
            asToggle().click();
        }

        @Override
        public Value<Boolean> selected ()
        {
            return asToggle().selected;
        }

        @Override
        protected Class<?> getStyleClass ()
        {
            return Toggle.class;
        }

        protected Behavior.Toggle<Toggle> asToggle ()
        {
            return (Behavior.Toggle<Toggle>)_behave;
        }
    }

    /**
     * Creates a new elements widget with the given layout.
     */
    public ElementsWidget (Layout layout)
    {
        super(layout);
        set(Flag.HIT_DESCEND, true);
        set(Flag.HIT_ABSORB, true);
    }

    public void setBehavior (Behavior<T> behave)
    {
        _bconn.disconnect();
        _bconn = layer.addListener(_behave = behave);
    }

    @Override
    protected void layout ()
    {
        super.layout();
        _behave.layout();
    }

    protected Behavior<T> _behave;
    protected Connection _bconn = Layers.NOT_LISTENING;
}
