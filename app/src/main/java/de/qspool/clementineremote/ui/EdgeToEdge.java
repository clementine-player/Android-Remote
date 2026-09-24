package de.qspool.clementineremote.ui;

import android.app.Activity;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import de.qspool.clementineremote.R;

/**
 * Lays an activity out edge to edge, which Android 15+ enforces for apps targeting it:
 * the toolbar grows under the status bar (keeping its colour there) and the rest of the
 * content is padded clear of the navigation bar, display cutouts and the keyboard.
 */
public final class EdgeToEdge {

    private EdgeToEdge() {
    }

    /** Call after setContentView(). */
    public static void apply(Activity activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        // The toolbar is dark, so the status bar icons must be light.
        WindowCompat.getInsetsController(activity.getWindow(),
                activity.getWindow().getDecorView()).setAppearanceLightStatusBars(false);

        View content = activity.findViewById(android.R.id.content);
        final View toolbar = activity.findViewById(R.id.toolbar);
        final int toolbarPaddingTop = toolbar != null ? toolbar.getPaddingTop() : 0;
        final int toolbarMinHeight = toolbar != null ? toolbar.getMinimumHeight() : 0;

        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime());
            if (toolbar != null) {
                toolbar.setPadding(toolbar.getPaddingLeft(), toolbarPaddingTop + insets.top,
                        toolbar.getPaddingRight(), toolbar.getPaddingBottom());
                toolbar.setMinimumHeight(toolbarMinHeight + insets.top);
            }
            view.setPadding(insets.left, toolbar != null ? 0 : insets.top, insets.right,
                    insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
    }
}
