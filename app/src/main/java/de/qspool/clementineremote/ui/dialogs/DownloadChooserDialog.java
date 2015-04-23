package de.qspool.clementineremote.ui.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

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
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

        builder.setTitle(R.string.player_download_what);
        builder.setNegativeButton(R.string.dialog_close, null);
        builder.setItems(R.array.player_download_list, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
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
