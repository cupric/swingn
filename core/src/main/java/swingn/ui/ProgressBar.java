//
// SwingN - swing-like utilities and widgets for use with tripleplay
// Copyright (c) 2014, Cupric - All rights reserved.
// http://github.com/cupric/swingn/blob/master/LICENSE

package swingn.ui;

import playn.core.Canvas;
import playn.core.Image;
import react.Value;
import tripleplay.ui.GlyphWidget;
import tripleplay.ui.Style;
import tripleplay.util.Colors;

/**
 * Displays a basic rectangular progress bar. The background and foreground colors are
 * configurable. The foreground may also be an image, stretched to cover the area of progress.
 * TODO: border style
 */
public class ProgressBar extends GlyphWidget<ProgressBar>
{
    /** Normalized progress value. Updating this will move the bar. */
    public Value<Float> progress = Value.create(0f);

    /** Assigns a color for the unfinished portion of the progress bar. This is different from the
     * background of the widget. A value of zero cause nothing to be drawn in the unused portion.
     * Instead, the widget's background will show through. */
    public static Style<Integer> BACKGROUND_COLOR = Style.newStyle(true, Colors.WHITE);

    /** Assigns a color for the finished portion of the progress. */
    public static Style<Integer> BAR_COLOR = Style.newStyle(true, Colors.BLUE);

    /** Assigns an image for the finished portion of the progress. If set, {@link #BAR_COLOR} will
     * be ignored. */
    public static Style<Image> BAR_IMAGE = Style.newStyle(true, null);

    /**
     * Creates a new progress bar.
     */
    public ProgressBar ()
    {
        progress.connect(renderSlot());
    }

    @Override
    protected Class<?> getStyleClass ()
    {
        return ProgressBar.class;
    }

    @Override
    protected BaseLayoutData createBaseLayoutData (float hintX, float hintY)
    {
        return new GlyphLayoutData() {
            @Override
            public void layout (float left, float top, float width, float height) {
                super.layout(left, top, width, height);
                _bgColor = resolveStyle(BACKGROUND_COLOR);
                _barColor = resolveStyle(BAR_COLOR);
                _barImage = resolveStyle(BAR_IMAGE);
            }
        };
    }

    @Override
    protected void paint (Canvas canvas)
    {
        float progress = Math.min(1, Math.max(this.progress.get(), 0));
        float w = _glyph.preparedWidth(), h = _glyph.preparedHeight();

        // bar
        if (_barImage == null) {
            canvas.setFillColor(_barColor);
            canvas.fillRect(0, 0, w * progress, h);
        } else {
            canvas.drawImage(_barImage, 0, 0, w * progress,  h);
        }

        // background
        if (_bgColor != 0) {
            canvas.setFillColor(_bgColor);
            canvas.fillRect(w * progress, 0, w, h);
        }

        // border
        canvas.setStrokeColor(Colors.BLACK);
        canvas.strokeRect(0, 0, w - 1, h - 1);
    }

    protected int _bgColor, _barColor;
    protected Image _barImage;
}
