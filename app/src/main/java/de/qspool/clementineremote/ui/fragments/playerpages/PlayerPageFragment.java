/* This file is part of the Android Clementine Remote.
 * Copyright (C) 2013, Andreas Muttscheller <asfa194@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package de.qspool.clementineremote.ui.fragments.playerpages;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.downloader.DownloadManager;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.DownloadItem;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.player.LyricsProvider;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.ui.interfaces.BackPressHandleable;
import de.qspool.clementineremote.ui.interfaces.NameableTitle;
import de.qspool.clementineremote.ui.interfaces.RemoteDataReceiver;
import de.qspool.clementineremote.utils.Utilities;

public class PlayerPageFragment extends Fragment
        implements BackPressHandleable, RemoteDataReceiver, NameableTitle {

    private final String TAG = "PlayerPageFragment";

    private final static int ANIMATION_DURATION = 750;

    private TextView mTvArtist;

    private TextView mTvTitle;

    private TextView mTvAlbum;

    private TextView mTvGenre;

    private TextView mTvYear;

    private TextView mTvLength;

    private SeekBar mSbPosition;

    private ImageView mImgArt;

    private AlphaAnimation mAlphaDown;

    private AlphaAnimation mAlphaUp;

    private boolean mCoverUpdated = false;

    MenuItem mMenuRepeat;

    MenuItem mMenuShuffle;

    private Toast mToast;

    private boolean mFirstCall = true;

    ProgressDialog mPdDownloadLyrics;

    private MySong mCurrentSong = new MySong();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_player_control,
                container, false);

        // Get the Views
        mTvArtist = (TextView) view.findViewById(R.id.tvArtist);
        mTvTitle = (TextView) view.findViewById(R.id.tvTitle);
        mTvAlbum = (TextView) view.findViewById(R.id.tvAlbum);

        mTvGenre = (TextView) view.findViewById(R.id.tvGenre);
        mTvYear = (TextView) view.findViewById(R.id.tvYear);
        mTvLength = (TextView) view.findViewById(R.id.tvLength);

        mSbPosition = (SeekBar) view.findViewById(R.id.sbPosition);

        mImgArt = (ImageView) view.findViewById(R.id.imgArt);

        mImgArt.setOnClickListener(oclControl);

        mSbPosition.setOnSeekBarChangeListener(onSeekBarChanged);

        // Animation for track change
        mAlphaDown = new AlphaAnimation(1.0f, 0.0f);
        mAlphaUp = new AlphaAnimation(0.0f, 1.0f);
        mAlphaDown.setDuration(ANIMATION_DURATION);
        mAlphaUp.setDuration(ANIMATION_DURATION);
        mAlphaDown.setFillAfter(true);
        mAlphaUp.setFillAfter(true);
        mAlphaUp.setAnimationListener(mAnimationListener);
        mAlphaDown.setAnimationListener(mAnimationListener);
        mAlphaDown.setInterpolator(new AccelerateInterpolator());
        mAlphaUp.setInterpolator(new AccelerateInterpolator());

        mPdDownloadLyrics = new ProgressDialog(getActivity());
        mPdDownloadLyrics.setMessage(getString(R.string.player_download_lyrics));
        mPdDownloadLyrics.setCancelable(true);

        // Initialize interface
        updateTrackMetadata();
        updateTrackPosition();

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.player_menu, menu);

        mMenuRepeat = menu.findItem(R.id.repeat);
        mMenuShuffle = menu.findItem(R.id.shuffle);

        updateShuffleIcon();
        updateRepeatIcon();

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onResume() {
        super.onResume();

        mCurrentSong = new MySong();
        mFirstCall = true;
        updateTrackMetadata();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.shuffle:
                doShuffle();
                break;
            case R.id.repeat:
                doRepeat();
                break;
            case R.id.download_song:
                if (App.Clementine.getCurrentSong() == null) {
                    Toast.makeText(getActivity(), R.string.player_nosong, Toast.LENGTH_LONG).show();
                    break;
                }
                if (App.Clementine.getCurrentSong().isLocal()) {
                    DownloadManager.getInstance().addJob(ClementineMessageFactory
                            .buildDownloadSongsMessage(-1, DownloadItem.CurrentItem));
                } else {
                    Toast.makeText(getActivity(), R.string.player_song_is_stream, Toast.LENGTH_LONG)
                            .show();
                }
                break;
            case R.id.download_album:
                if (App.Clementine.getCurrentSong() == null) {
                    Toast.makeText(getActivity(), R.string.player_nosong, Toast.LENGTH_LONG).show();
                    break;
                }
                if (App.Clementine.getCurrentSong().isLocal()) {
                    DownloadManager.getInstance().addJob(ClementineMessageFactory
                            .buildDownloadSongsMessage(-1, DownloadItem.ItemAlbum));
                } else {
                    Toast.makeText(getActivity(), R.string.player_song_is_stream, Toast.LENGTH_LONG)
                            .show();
                }
                break;
            case R.id.stop:
                Message msg = Message.obtain();
                msg.obj = ClementineMessage.getMessage(MsgType.STOP);
                App.ClementineConnection.mHandler.sendMessage(msg);
                break;
            default:
                return false;
        }
        return true;
    }

    @Override
    public void MessageFromClementine(ClementineMessage clementineMessage) {
        switch (clementineMessage.getMessageType()) {
            case UPDATE_TRACK_POSITION:
            case STOP:
                updateTrackPosition();
                break;
            case CURRENT_METAINFO:
                updateTrackMetadata();
                break;
            case SHUFFLE:
                updateShuffleIcon();
                break;
            case REPEAT:
                updateRepeatIcon();
                break;
            case LYRICS:
                showLyricsDialog();
                break;
            default:
                break;
        }
    }

    /**
     * Update the track position. This method updates the seekbar and the time printed on the right
     * hand side.
     */
    private void updateTrackPosition() {
        mSbPosition.setEnabled(true);
        mSbPosition.setMax(mCurrentSong.getLength());
        if (App.Clementine.getState() == Clementine.State.STOP) {
            mSbPosition.setProgress(0);
        } else {
            mSbPosition.setProgress(App.Clementine.getSongPosition());
        }

        mTvLength.setText(buildTrackPosition());
    }

    /**
     * The track changed. Update the metadata shown on the user interface
     */
    @SuppressLint("NewApi")
    public void updateTrackMetadata() {
        // Get the currently played song
        MySong currentSong = App.Clementine.getCurrentSong();
        if (currentSong == null) {
            // If none is played right now, show a text and the clementine icon
            mTvArtist.setText(getString(R.string.player_nosong));
            mTvTitle.setText("");
            mTvAlbum.setText("");

            mTvGenre.setText("");
            mTvYear.setText("");
            mTvLength.setText("");

            mSbPosition.setEnabled(false);

            mImgArt.setImageResource(R.drawable.icon_large);
        } else {
            mTvArtist.setText(currentSong.getArtist());
            mTvTitle.setText(currentSong.getTitle());
            mTvAlbum.setText(currentSong.getAlbum());

            mTvGenre.setText(currentSong.getGenre());
            mTvYear.setText(currentSong.getYear());

            // Check if a coverart is valid
            Bitmap newArt = currentSong.getArt();
            Bitmap oldArt = mCurrentSong.getArt();

            if (newArt == null) {
                mImgArt.setImageResource(R.drawable.icon_large);
            } else if (oldArt == null
                    || !oldArt.sameAs(newArt)) {
                // Transit only if the cover changed
                if (mFirstCall) {
                    mImgArt.setImageBitmap(newArt);
                } else {
                    mImgArt.startAnimation(mAlphaDown);
                }
            }
            mCurrentSong = currentSong;
        }

        mFirstCall = false;
    }

    /**
     * Change shuffle mode and update view
     */
    private void doShuffle() {
        Message msg = Message.obtain();
        App.Clementine.nextShuffleMode();
        msg.obj = ClementineMessageFactory.buildShuffle();
        App.ClementineConnection.mHandler.sendMessage(msg);

        switch (App.Clementine.getShuffleMode()) {
            case OFF:
                makeToast(R.string.shuffle_off, Toast.LENGTH_SHORT);
                break;
            case ALL:
                makeToast(R.string.shuffle_all, Toast.LENGTH_SHORT);
                break;
            case INSIDE_ALBUM:
                makeToast(R.string.shuffle_inside_album, Toast.LENGTH_SHORT);
                break;
            case ALBUMS:
                makeToast(R.string.shuffle_albums, Toast.LENGTH_SHORT);
                break;
        }

        updateShuffleIcon();
    }

    /**
     * Update the shuffle icon in the actionbar
     */
    private void updateShuffleIcon() {
        // Make sure the menu item is already there. Since this is called from the backend
        // mMenuShuffle might be null. It will be set correctly once onCreateOptionsMenu is called
        if (mMenuShuffle == null)
            return;

        switch (App.Clementine.getShuffleMode()) {
            case OFF:
                mMenuShuffle.setIcon(R.drawable.ab_shuffle_off);
                break;
            case ALL:
                mMenuShuffle.setIcon(R.drawable.ab_shuffle);
                break;
            case INSIDE_ALBUM:
                mMenuShuffle.setIcon(R.drawable.ab_shuffle_album);
                break;
            case ALBUMS:
                mMenuShuffle.setIcon(R.drawable.ab_shuffle_albums);
                break;
        }
    }

    /**
     * Change repeat mode and update view
     */
    public void doRepeat() {
        Message msg = Message.obtain();

        App.Clementine.nextRepeatMode();
        msg.obj = ClementineMessageFactory.buildRepeat();
        App.ClementineConnection.mHandler.sendMessage(msg);

        switch (App.Clementine.getRepeatMode()) {
            case OFF:
                makeToast(R.string.repeat_off, Toast.LENGTH_SHORT);
                break;
            case TRACK:
                makeToast(R.string.repeat_track, Toast.LENGTH_SHORT);
                break;
            case ALBUM:
                makeToast(R.string.repeat_album, Toast.LENGTH_SHORT);
                break;
            case PLAYLIST:
                makeToast(R.string.repeat_playlist, Toast.LENGTH_SHORT);
                break;
        }

        updateRepeatIcon();
    }

    /**
     * Update the repeat icon in the actionbar
     */
    private void updateRepeatIcon() {
        // Make sure the menu item is already there. Since this is called from the backend
        // mMenuShuffle might be null. It will be set correctly once onCreateOptionsMenu is called
        if (mMenuRepeat == null)
            return;

        switch (App.Clementine.getRepeatMode()) {
            case OFF:
                mMenuRepeat.setIcon(R.drawable.ab_repeat_off);
                break;
            case TRACK:
                mMenuRepeat.setIcon(R.drawable.ab_repeat_track);
                break;
            case ALBUM:
                mMenuRepeat.setIcon(R.drawable.ab_repeat_album);
                break;
            case PLAYLIST:
                mMenuRepeat.setIcon(R.drawable.ab_repeat_playlist);
                break;
        }
    }

    /**
     * Build the current track position. Format: "01:30/3:33"
     *
     * @return The current and total track position as a string
     */
    private String buildTrackPosition() {
        StringBuilder sb = new StringBuilder();

        if (!mCurrentSong.isLocal()) {
            sb.append(getString(R.string.player_stream));
            sb.append(" ");
        }

        if (mCurrentSong.getLength() == 0) {
            sb.append(Utilities.PrettyTime(App.Clementine.getSongPosition()));
        } else {
            if (App.Clementine.getState() == Clementine.State.STOP) {
                sb.append(Utilities.PrettyTime(0));
            } else {
                sb.append(Utilities.PrettyTime(App.Clementine.getSongPosition()));
            }
            sb.append("/");
            sb.append(Utilities.PrettyTime(mCurrentSong.getLength()));
        }

        return sb.toString();
    }

    /**
     * Opens a dialog to show the lyrics
     */
    public void showLyricsDialog() {
        // Only show lyrics dialog, if the user is still waiting for it
        if (!mPdDownloadLyrics.isShowing()) {
            return;
        }

        // Dismiss the dialog
        mPdDownloadLyrics.dismiss();

        // Check for a valid lyric
        if (mCurrentSong.getLyricsProvider().isEmpty()) {
            Toast.makeText(getActivity(), R.string.player_no_lyrics, Toast.LENGTH_SHORT).show();
            return;
        }

        // Receive the provider and show the dialog
        LyricsProvider provider = getBestLyricsProvider(mCurrentSong.getLyricsProvider());

        // Show the dialog
        Utilities.ShowMessageDialog(getActivity(), provider.getTitle(), provider.getContent(),
                false);
    }

    public ImageView getImageArt() {
        return mImgArt;
    }

    /**
     * Get the best lyrics provider for this song (currently the one with the most characters
     *
     * @param providers A list of lyrics providers
     * @return The best possible provider
     */
    private LyricsProvider getBestLyricsProvider(List<LyricsProvider> providers) {
        LyricsProvider bestProvider = providers.get(0);
        for (LyricsProvider lyric : providers) {
            // For now the provider with the longest lyrics wins
            if (lyric.getContent().length() > bestProvider.getContent().length()) {
                bestProvider = lyric;
            }
        }
        return bestProvider;
    }

    /**
     * Show text in a toast. Cancels previous toast
     *
     * @param resId  The resource id
     * @param length length
     */
    private void makeToast(int resId, int length) {
        makeToast(getString(resId), length);
    }

    /**
     * Show text in a toast. Cancels previous toast
     *
     * @param text   The text to show
     * @param length length
     */
    private void makeToast(String text, int length) {
        if (mToast != null) {
            mToast.cancel();
        }
        mToast = Toast.makeText(getActivity(), text, length);
        mToast.show();
    }

    private OnClickListener oclControl = new OnClickListener() {

        @Override
        public void onClick(View v) {
            Message msg = Message.obtain();

            switch (v.getId()) {
                case R.id.imgArt:
                    // Shall we download the lyrics or do we have them already downloaded?
                    mPdDownloadLyrics.show();
                    if (mCurrentSong.getLyricsProvider().isEmpty()) {
                        msg.obj = ClementineMessage.getMessage(MsgType.GET_LYRICS);
                    } else {
                        showLyricsDialog();
                    }
                    break;
                default:
                    break;
            }
            // Send the request to the thread
            if (msg.obj != null) {
                App.ClementineConnection.mHandler.sendMessage(msg);
            }
        }
    };

    private OnSeekBarChangeListener onSeekBarChanged = new OnSeekBarChangeListener() {

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress,
                boolean fromUser) {
            // If the user changed the position, send a request to Clementine
            if (fromUser) {
                Message msg = Message.obtain();
                msg.obj = ClementineMessageFactory.buildTrackPosition(progress);
                App.ClementineConnection.mHandler.sendMessage(msg);

                App.Clementine.setSongPosition(progress);
            }
        }
    };

    private AnimationListener mAnimationListener = new AnimationListener() {
        @Override
        public void onAnimationEnd(Animation animation) {
            if (!mCoverUpdated) {
                mImgArt.setImageBitmap(App.Clementine.getCurrentSong().getArt());
                mImgArt.startAnimation(mAlphaUp);
            }
            mCoverUpdated = !mCoverUpdated;
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        @Override
        public void onAnimationStart(Animation animation) {
        }

    };

    @Override
    public boolean onBackPressed() {
        return false;
    }

    @Override
    public int getTitleId() {
        return R.string.fragment_title_player;
    }
}
