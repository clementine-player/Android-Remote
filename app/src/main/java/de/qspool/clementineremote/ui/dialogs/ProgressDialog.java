package de.qspool.clementineremote.ui.dialogs;

import android.content.Context;
import android.content.DialogInterface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

/**
 * A dialog with a message and a progress bar, either indeterminate or counting up to a
 * maximum. Replaces the progress dialogs of the material-dialogs library.
 */
public class ProgressDialog {

    private final AlertDialog mDialog;

    private final ProgressBar mProgressBar;

    private final TextView mContent;

    private ProgressDialog(Context context, @StringRes int title, @StringRes int content,
            boolean indeterminate, int max, boolean cancelable,
            DialogInterface.OnCancelListener cancelListener) {
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24,
                context.getResources().getDisplayMetrics());

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(indeterminate ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(padding, padding, padding, padding / 2);

        mProgressBar = indeterminate
                ? new ProgressBar(context)
                : new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        mProgressBar.setIndeterminate(indeterminate);
        mProgressBar.setMax(max);

        mContent = new TextView(context);
        mContent.setText(content);
        if (indeterminate) {
            mContent.setPadding(padding, 0, 0, 0);
            layout.addView(mProgressBar);
            layout.addView(mContent);
        } else {
            layout.addView(mContent);
            layout.addView(mProgressBar, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setView(layout)
                .setCancelable(cancelable)
                .setOnCancelListener(cancelListener);
        if (title != 0) {
            builder.setTitle(title);
        }
        mDialog = builder.show();
    }

    /** Shows a dialog with a spinning, indeterminate progress indicator. */
    public static ProgressDialog showIndeterminate(Context context, @StringRes int title,
            @StringRes int content, boolean cancelable,
            DialogInterface.OnCancelListener cancelListener) {
        return new ProgressDialog(context, title, content, true, 0, cancelable, cancelListener);
    }

    /** Shows a dialog with a progress bar from 0 to {@code max}. */
    public static ProgressDialog showDeterminate(Context context, @StringRes int title,
            @StringRes int content, int max, boolean cancelable) {
        return new ProgressDialog(context, title, content, false, max, cancelable, null);
    }

    public void setContent(CharSequence content) {
        mContent.setText(content);
    }

    public void setMaxProgress(int max) {
        mProgressBar.setMax(max);
    }

    public void setProgress(int progress) {
        mProgressBar.setProgress(progress);
    }

    public void incrementProgress(int by) {
        mProgressBar.incrementProgressBy(by);
    }

    public boolean isShowing() {
        return mDialog.isShowing();
    }

    public void dismiss() {
        mDialog.dismiss();
    }
}
