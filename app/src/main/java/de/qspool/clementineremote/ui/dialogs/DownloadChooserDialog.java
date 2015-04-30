package de.qspool.clementineremote.ui.dialogs;

import com.afollestad.materialdialogs.MaterialDialog;

import android.content.Context;
import android.view.View;

import de.qspool.clementineremote.R;

public class DownloadChooserDialog {
    public static enum Type {
        SONG,
        ALBUM,
        PLAYLIST
    }

    public static interface Callback {
        public void onItemClick(Type type);
    }

    private Context mContext;

    private Callback mCallback;

    public DownloadChooserDialog(Context context) {
        mContext = context;
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public void showDialog() {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(mContext);

        builder.title(R.string.player_download_what);
        builder.negativeText(R.string.dialog_close);
        builder.items(R.array.player_download_list);
        builder.itemsCallback(new MaterialDialog.ListCallback() {
            @Override
            public void onSelection(MaterialDialog materialDialog, View view, int i,
                    CharSequence charSequence) {
                switch (i) {
                    case 0:
                        mCallback.onItemClick(Type.SONG);
                        break;
                    case 1:
                        mCallback.onItemClick(Type.ALBUM);
                        break;
                    case 2:
                        mCallback.onItemClick(Type.PLAYLIST);
                        break;
                    default:
                        break;
                }
            }
        });

        builder.show();
    }

}
