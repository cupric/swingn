//
// SwingN - swing-like utilities and widgets for use with tripleplay
// Copyright (c) 2014, Cupric - All rights reserved.
// http://github.com/cupric/swingn/blob/master/LICENSE

package swingn.ui;

import playn.core.Layer;
import playn.core.Mouse;
import playn.core.PlayN;
import pythagoras.f.Point;
import samson.Interval;
import tripleplay.ui.Element;
import tripleplay.ui.MenuHost;
import tripleplay.util.Tapper;

/**
 * Manages showing tooltips for elements.
 */
public final class TipManager
{
    /**
     * Gets the singleton tip manager.
     */
    public static TipManager get ()
    {
        if (_instance == null) {
            _instance = new TipManager();
        }
        return _instance;
    }

    /**
     * Sets up a tooltip for the given element. When the trigger is moused over or tapped, a tip
     * will be created using the given source and shown next to the trigger.
     */
    public void setTip (Element<?> trigger, Tip.Source source)
    {
        new Handler(trigger, source).attach();
    }

    /**
     * Creates a new tip manager.
     */
    private TipManager ()
    {
    }

    /**
     * Shows the tip for the given handler, after a delay. Called when the mouse enters the
     * trigger of the handler.
     */
    protected void entered (Handler handler)
    {
        _over = handler;
        _show.schedule(_immediateMode ? 0 : MOUSE_DELAY);
        _resetMode.cancel();
    }

    /**
     * Hides the current tip and/or cancels the previously scheduled tip. Called when the mouse
     * exits trigger of the handler.
     */
    protected void exited (Handler handler)
    {
        _show.cancel();
        hide();
        _resetMode.schedule(IMMEDIATE_DELAY);
    }

    /**
     * Displays a menu tip when the trigger of the given handler is tapped. Also hides the current
     * tip and/or cancels the previously scheduled tip.
     */
    protected void tapped (Handler handler)
    {
        _show.cancel();
        hide();

        MenuHost.Pop pop = handler.createPop(true);
        if (pop == null) {
            return;
        }
        pop.popup();
    }

    /**
     * Hides the current tip. Doesn't affect the menu tip, since that will be self-hiding.
     */
    protected void hide ()
    {
        if (_host != null && _host.activePop() == _active) {
            _host.deactivate();
        }
        _active = null;
        _over = null;
    }

    /**
     * Binds an element and tip source and provides appropriate listeners for the element's layer
     * to show the tip.
     */
    protected class Handler extends Tapper
    {
        /**
         * Creates a new handler, not active until {@link #attach()} is called.
         */
        public Handler (Element<?> trigger, Tip.Source source)
        {
            _source = source;
            _trigger = trigger;
        }

        /**
         * Sets the trigger hit tester and attaches the necessary listeners.
         */
        public void attach ()
        {
            // set up the top right default trigger point
            _trigger.addStyles(Tip.STANDARD_TRIGGER);

            // detect hits on the element (usually tips are on non-interactive widgets)
            _trigger.layer.setHitTester(new Layer.HitTester() {
                @Override public Layer hitTest (Layer layer, Point p) {
                    return p.x >= 0 && p.x < _trigger.size().width() &&
                            p.y >= 0 && p.y < _trigger.size().height() ? layer : null;
                }
            });

            if (PlayN.mouse().hasMouse()) {
                // rollover listener
                _trigger.layer.addListener(new Mouse.LayerAdapter() {
                    @Override
                    public void onMouseOver (Mouse.MotionEvent event) {
                        entered(Handler.this);
                    }

                    @Override
                    public void onMouseOut (Mouse.MotionEvent event) {
                        exited(Handler.this);
                    }
                });
            }

            // tap listener
            _trigger.layer.addListener(this);
        }

        @Override
        public void onTap ()
        {
            tapped(this);
        }

        /**
         * Gets the trigger.
         */
        public Element<?> getTrigger ()
        {
            return _trigger;
        }

        /**
         * Gets the tip source.
         */
        public Tip.Source getSource ()
        {
            return _source;
        }

        /**
         * Creates the pop that will be used to show the tip.
         */
        protected MenuHost.Pop createPop (boolean menu)
        {
            Tip tip = _source.createTip(menu);
            if (tip == null) {
                return null;
            }
            tip.setMenu(menu);
            // we don't need to pass a pointer event because the positioning is always relative
            // to the trigger
            return new MenuHost.Pop(_trigger, tip, null);
        }

        /** The source of the tip. */
        protected final Tip.Source _source;

        /** The element that triggers the tip. */
        protected final Element<?> _trigger;
    }

    /** Shows a tip for the currently moused over handler. */
    protected Interval _show = new Interval() {
        @Override
        public void expired () {
            if (_over == null || !_over.getTrigger().isAdded()) {
                return;
            }
            _active = _over.createPop(false);
            if (_active == null) {
                return;
            }
            _host = _active.popup();
            _immediateMode = true;
        }
    };

    /** Clears the immediate mode. */
    protected Interval _resetMode = new Interval() {
        @Override
        public void expired () {
            _immediateMode = false;
        }
    };

    /** The handler whose trigger is currently under the mouse, if any. */
    protected Handler _over;

    /** The Pop for the currently displaying tip. */
    protected MenuHost.Pop _active;

    /** The host used to popup the active Pop. */
    protected MenuHost _host;

    /** Whether the next tip should be shown immediately. */
    protected boolean _immediateMode;

    /** Time the mouse must be over the trigger before a tip is popped up. */
    protected static int MOUSE_DELAY = 700;

    /** Time after hiding a tip that we remain in "immediate" mode. */
    protected static int IMMEDIATE_DELAY = 500;

    /** Singleton instance. */
    protected static TipManager _instance;
}
