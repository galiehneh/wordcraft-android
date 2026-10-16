package com.galiehneh.wordcraft.ui;

import android.content.Context;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowInsets;

/** Pads the root view for navigation bar and display cutout insets. */
public final class InsetsController {
    private InsetsController() {}

    public static void apply(View view) {
        final Context context = view.getContext();
        final int navHeight = navigationBarHeight(context);
        view.setPadding(0, 0, 0, navHeight);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.navigationBars() | WindowInsets.Type.displayCutout());
                int bottom = bars.bottom > 0 ? bars.bottom : navHeight;
                v.setPadding(bars.left, 0, bars.right, bottom);
                return insets;
            });
        } else {
            view.setOnApplyWindowInsetsListener((v, insets) -> {
                int bottom = insets.getSystemWindowInsetBottom();
                v.setPadding(insets.getSystemWindowInsetLeft(), 0,
                        insets.getSystemWindowInsetRight(), bottom > 0 ? bottom : navHeight);
                return insets;
            });
        }
    }

    private static int navigationBarHeight(Context context) {
        int resId = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resId > 0) {
            return context.getResources().getDimensionPixelSize(resId);
        }
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 48, context.getResources().getDisplayMetrics()));
    }
}
