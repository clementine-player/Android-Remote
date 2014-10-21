package de.qspool.clementineremote.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.qspool.clementineremote.R;

public class FileDialog {

    private static final String PARENT_DIR = "..";

    private final String TAG = getClass().getSimpleName();

    private String[] fileList;

    private File currentPath;

    private boolean checkIfWritable = false;

    public interface FileSelectedListener {

        void fileSelected(File file);
    }

    public interface DirectorySelectedListener {

        void directorySelected(File directory);
    }

    private ListenerList<FileSelectedListener> fileListenerList
            = new ListenerList<>();

    private ListenerList<DirectorySelectedListener> dirListenerList
            = new ListenerList<>();

    private final Activity activity;

    private boolean selectDirectoryOption;

    private String fileEndsWith;

    public FileDialog(Activity activity, File path) {
        this.activity = activity;
        if (!path.exists()) {
            path = Environment.getExternalStorageDirectory();
        }
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            loadFileList(path);
        }
    }

    /**
     * @return file dialog
     */
    public Dialog createFileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        builder.setTitle(currentPath.getPath());
        if (selectDirectoryOption) {
            builder.setPositiveButton(activity.getString(R.string.file_dialog_set_dir),
                    new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) { }
                    });

            builder.setNegativeButton(activity.getString(R.string.dialog_close),
                    new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    });
        }

        builder.setItems(fileList, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String fileChosen = fileList[which];
                File chosenFile = getChosenFile(fileChosen);
                if (chosenFile.isDirectory()) {
                    loadFileList(chosenFile);
                    dialog.cancel();
                    dialog.dismiss();
                    showDialog();
                } else {
                    fireFileSelectedEvent(chosenFile);
                }
            }
        });

        final AlertDialog dialog = builder.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (isCheckIfWritable()) {
                            // Check the external store state
                            File checkFile = new File(currentPath.getAbsolutePath() + "/ClementineTestFile.CheckIfWritable");
                            try {
                                if (checkFile.createNewFile()) checkFile.delete();

                                Log.d(TAG, checkFile.getAbsolutePath() + " is writable");
                                dialog.dismiss();
                                fireDirectorySelectedEvent(currentPath);
                            } catch (IOException e) {
                                Toast.makeText(activity, R.string.file_dialog_not_writable, Toast.LENGTH_LONG).show();
                            }
                        } else {
                            fireDirectorySelectedEvent(currentPath);
                            dialog.dismiss();
                        }

                    }
                });

        return dialog;
    }


    public void addFileListener(FileSelectedListener listener) {
        fileListenerList.add(listener);
    }

    public void removeFileListener(FileSelectedListener listener) {
        fileListenerList.remove(listener);
    }

    public void setSelectDirectoryOption(boolean selectDirectoryOption) {
        this.selectDirectoryOption = selectDirectoryOption;
    }

    public void addDirectoryListener(DirectorySelectedListener listener) {
        dirListenerList.add(listener);
    }

    public void removeDirectoryListener(DirectorySelectedListener listener) {
        dirListenerList.remove(listener);
    }

    /**
     * Show file dialog
     */
    public void showDialog() {
        createFileDialog();
    }

    private void fireFileSelectedEvent(final File file) {
        fileListenerList.fireEvent(new FireHandler<FileDialog.FileSelectedListener>() {
            public void fireEvent(FileSelectedListener listener) {
                listener.fileSelected(file);
            }
        });
    }

    private void fireDirectorySelectedEvent(final File directory) {
        dirListenerList.fireEvent(new FireHandler<FileDialog.DirectorySelectedListener>() {
            public void fireEvent(DirectorySelectedListener listener) {
                listener.directorySelected(directory);
            }
        });
    }

    private void loadFileList(File path) {
        this.currentPath = path;
        List<String> r = new ArrayList<>();
        if (path.exists()) {
            if (path.getParentFile() != null) {
                r.add(PARENT_DIR);
            }
            FilenameFilter filter = new FilenameFilter() {
                public boolean accept(File dir, String filename) {
                    File sel = new File(dir, filename);
                    if (!sel.canRead()) {
                        return false;
                    }
                    if (selectDirectoryOption) {
                        return sel.isDirectory();
                    } else {
                        boolean endsWith = fileEndsWith == null || filename
                                .toLowerCase(Locale.getDefault()).endsWith(fileEndsWith);
                        return endsWith || sel.isDirectory();
                    }
                }
            };
            String[] fileList1 = path.list(filter);
            if (fileList1 != null) {
                Collections.addAll(r, fileList1);
            }
        }
        Collections.sort(r);
        fileList = r.toArray(new String[]{});
    }

    private File getChosenFile(String fileChosen) {
        if (fileChosen.equals(PARENT_DIR)) {
            return currentPath.getParentFile();
        } else {
            return new File(currentPath, fileChosen);
        }
    }

    public void setFileEndsWith(String fileEndsWith) {
        this.fileEndsWith = fileEndsWith != null ? fileEndsWith.toLowerCase(Locale.getDefault())
                : fileEndsWith;
    }

    public boolean isCheckIfWritable() {
        return checkIfWritable;
    }

    public void setCheckIfWritable(boolean checkIfWritable) {
        this.checkIfWritable = checkIfWritable;
    }

    public interface FireHandler<L> {
        void fireEvent(L listener);
    }

    class ListenerList<L> {

        private List<L> listenerList = new ArrayList<>();

        public void add(L listener) {
            listenerList.add(listener);
        }

        public void fireEvent(FireHandler<L> fireHandler) {
            List<L> copy = new ArrayList<>(listenerList);
            for (L l : copy) {
                fireHandler.fireEvent(l);
            }
        }

        public void remove(L listener) {
            listenerList.remove(listener);
        }

        public List<L> getListenerList() {
            return listenerList;
        }
    }
}

