//
// SwingN - swing-like utilities and widgets for use with tripleplay
// Copyright (c) 2014, Cupric - All rights reserved.
// http://github.com/cupric/swingn/blob/master/LICENSE

package swingn.ui;

import playn.core.Keyboard.TextType;
import pythagoras.f.Dimension;
import react.Slot;
import react.UnitSlot;
import react.Value;
import react.ValueView;
import samson.Samson;
import tripleplay.ui.Field;
import tripleplay.ui.Style;
import tripleplay.ui.Styles;
import tripleplay.util.StyledText;

/**
 * A text field that will only allow editing of integer values within the specified range.
 */
public class IntField extends Field
{
    /**
     * Possibles states of the text in the field, when it is different from the value.
     */
    public enum TextState
    {
        /** Text parses and is in range. */
        GOOD,

        /** Text parses and is below the minimum. */
        TOO_LOW,

        /** Text parses and is above the maximum. */
        TOO_HIGH,

        /** Text does not parse. */
        NOT_A_NUMBER;

        /** Checks if this state is good. */
        public boolean isGood () {
            return this == GOOD;
        }
    }

    /**
     * Represents the state of the field.
     */
    public static class State
    {
        /** The value at the time the keyboard popped. */
        public final int originalValue;

        /** The last good value. */
        public final int lastGood;

        /** The state of the text. If {@link TextState#isGood()}, then {@link IntField#lastGood}
         * is always the parsed value. */
        public final TextState textState;

        /** Creates a new state with the given field values. */
        public State (int originalValue, int lastGood, TextState textState) {
            this.originalValue = originalValue;
            this.lastGood = lastGood;
            this.textState = textState;
        }

        @Override
        public int hashCode () {
            return originalValue ^ lastGood ^ textState.hashCode();
        }

        @Override
        public boolean equals (Object obj) {
            if (!(obj instanceof State)) {
                return false;
            }
            State that = (State)obj;
            return originalValue == that.originalValue && lastGood == that.lastGood &&
                    textState == that.textState;
        }
    }

    /**
     * Creates an IntField with the range 0 - Integer.MAX_VALUE, with 0 as the initial value.
     */
    public IntField ()
    {
        this(Integer.MAX_VALUE);
    }

    /**
     * Creates an IntField with the specified maximum, and a minimum and initial value of zero.
     */
    public IntField (int maxValue)
    {
        this(0, maxValue);
    }

    /**
     * Creates an IntField with the specified minimum and maximum, with the minimum value initially
     * displayed.
     */
    public IntField (int minValue, int maxValue)
    {
        this(minValue, minValue, maxValue);
    }

    /**
     * Creates an IntField with the specified initial, minimum, and maximum values. An exception
     * is thrown if {@code minValue <= initial <= maxValue} does not hold.
     */
    public IntField (int initial, int minValue, int maxValue)
    {
        super("", Styles.make(TEXT_TYPE.is(TextType.NUMBER), FULLTIME_NATIVE_FIELD.is(false),
            Style.HALIGN.right));

        // NOTE: we account for the fixed column count of "5" below in our layout data

        validateMinMax(minValue, maxValue);
        validateMinMax(initial, maxValue);
        validateMinMax(minValue, initial);

        _min = minValue;
        _max = maxValue;

        // we know everything is good, set the state and text to match
        _state = Value.create(new State(initial, initial, TextState.GOOD));
        text.update(Samson.numberFormat().integer(getValue()));

        // update the state whenever the text changes
        text.connect(new Slot<String>() {
            @Override public void onEmit (String nval) {
                setStateFromText(nval);
            }
        });

        // rest the text to the last good value when editing is complete
        finishedEditing().connect(new UnitSlot() {
            @Override public void onEmit () {
                // Clean up text and set the state to GOOD
                // TODO: this is oriented towards on-the-spot feedback and editing, which
                // seems generally most appropriate, but we could also have something more
                // form-centric
                State state = _state.get();
                setValue(state.textState.isGood() ? getValue() : state.originalValue);
            }
        });
    }

    /**
     * Gets the value for the current state of this field.
     */
    public ValueView<State> state ()
    {
        return _state;
    }

    /**
     * Gets the minimum value for this field.
     */
    public int getMinValue ()
    {
        return _min;
    }

    /**
     * Gets the maximum value for this field.
     */
    public int getMaxValue ()
    {
        return _max;
    }

    /**
     * Sets the value for this field. An exception is thrown if the value is out of range.
     * Overwrites any user changes to the text.
     */
    public void setValue (int value)
    {
        validateMinMax(value, _max);
        validateMinMax(_min, value);
        _state.update(new State(value, value, TextState.GOOD));
        text.update(Samson.numberFormat().integer(getValue()));
    }

    /**
     * Gets the last good value. This is the same as the text parsed as an integer, unless the
     * text is currently not good.
     */
    public int getValue ()
    {
        return _state.get().lastGood;
    }

    /**
     * Sets the minimum value and clamps the current value.
     */
    public IntField setMinValue (int minValue)
    {
        validateMinMax(minValue, _max);
        _min = minValue;
        setValue(Math.max(_min, getValue()));
        return this;
    }

    /**
     * Sets the maximum value and clamps the current value.
     */
    public IntField setMaxValue (int maxValue)
    {
        validateMinMax(_min, maxValue);
        _max = maxValue;
        setValue(Math.min(_max, getValue()));
        return this;
    }

    @Override
    protected String transformText (String text)
    {
        // if the text is a valid value...
        if (setStateFromText(text)) {
            // go ahead and transform it to have commas and skip leading zeroes
            return Samson.numberFormat().integer(getValue());
        }

        // otherwise leave it alone so they can keep typing
        return text;
    }

    /** Throws an illegal argument exception if the given min is not <= the max.
     */
    protected static void validateMinMax (int minValue, int maxValue)
    {
        if (maxValue < minValue) {
            throw new IllegalArgumentException(
                "min/max out of order: " + maxValue + " " + minValue);
        }
    }

    /**
     * Updates the state based on the current text value and returns true if the new state
     * is good. Note that we can't also update the text here because this is used for transforming
     * text too.
     */
    protected boolean setStateFromText (String text)
    {
        int original = _state.get().originalValue;
        try {
            int val = Samson.numberFormat().parseInteger(text);
            if (val < _min) {
                _state.update(new State(original, getValue(), TextState.TOO_LOW));

            } else if (val > _max) {
                _state.update(new State(original, getValue(), TextState.TOO_HIGH));

            } else {
                _state.update(new State(original, val, TextState.GOOD));
                return true;
            }

        } catch (Throwable ex) {
            _state.update(new State(original, getValue(), TextState.NOT_A_NUMBER));
        }
        return false;
    }

    @Override
    protected boolean textIsValid (String text)
    {
        if (!super.textIsValid(text)) {
            return false;
        }
        // We don't want to do number parsing here because otherwise the user won't be able
        // to type a partial number; so just check characters
        for (int ii = 0, ll = text.length(); ii < ll; ii++) {
            switch (text.charAt(ii)) {
            case ',': case '.': case '+': case '-':
            case '0': case '1': case '2': case '3': case '4':
            case '5': case '6': case '7': case '8': case '9':
                break;
            default:
                return false;
            }
        }
        return true;
    }

    @Override
    protected LayoutData createLayoutData (float hintX, float hintY)
    {
        StyledText styled = StyledText.span("00000", Style.createTextStyle(this));
        return new SizableLayoutData(super.createLayoutData(hintX, hintY),
            new Dimension(styled.width(), styled.height()));
    }

    /** The state of the field. */
    protected final Value<State> _state;

    /** min/max */
    protected int _min, _max;
}
