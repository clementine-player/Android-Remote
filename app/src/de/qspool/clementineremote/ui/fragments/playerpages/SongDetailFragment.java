/* T
his file is part of the Android Clementine Remote.
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.ActionBar;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.SharedPreferencesKeys;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.ui.fragments.AbstractDrawerFragment;
import de.qspool.clementineremote.utils.Utilities;

public class SongDetailFragment extends AbstractDrawerFragment {

    private FrameLayout mContainer;

    private ImageButton iv_art;

    private ImageView iv_large_art;

    private TextView tv_artist;

    private TextView tv_title;

    private TextView tv_album;

    private TextView tv_genre;

    private TextView tv_year;

    private TextView tv_track;

    private TextView tv_disc;

    private TextView tv_playcount;

    private TextView tv_length;

    private TextView tv_size;

    private TextView tv_filename;

    private RatingBar rb_rating;

    // Hold a reference to the current animator,
    // so that it can be canceled mid-way.
    private Animator mCurrentAnimator;

    // The system "short" animation time duration, in milliseconds. This
    // duration is ideal for subtle animations or animations that occur
    // very frequently.
    private int mShortAnimationDuration;

    private MySong mCurrentSong;

    private SharedPreferences mSharedPref;

    private ActionBar mActionBar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the shared preferences
        mSharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_player_songdetail,
                container, false);

        mActionBar = getActivity().getActionBar();

        mContainer = (FrameLayout) view.findViewById(R.id.si_container);

        iv_art = (ImageButton) view.findViewById(R.id.si_art);
        iv_large_art = (ImageView) view.findViewById(R.id.si_large_art);

        iv_art.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                zoomImageFromThumb(iv_art);
            }
        });

        // Retrieve and cache the system's default "short" animation time.
        mShortAnimationDuration = getResources().getInteger(
                android.R.integer.config_shortAnimTime);

        tv_artist = (TextView) view.findViewById(R.id.si_artist);
        tv_title = (TextView) view.findViewById(R.id.si_title);
        tv_album = (TextView) view.findViewById(R.id.si_album);
        tv_genre = (TextView) view.findViewById(R.id.si_genre);
        tv_year = (TextView) view.findViewById(R.id.si_year);
        tv_track = (TextView) view.findViewById(R.id.si_track);
        tv_disc = (TextView) view.findViewById(R.id.si_disc);
        tv_playcount = (TextView) view.findViewById(R.id.si_playcount);
        tv_length = (TextView) view.findViewById(R.id.si_length);
        tv_size = (TextView) view.findViewById(R.id.si_size);
        tv_filename = (TextView) view.findViewById(R.id.si_filename);

        rb_rating = (RatingBar) view.findViewById(R.id.si_rating);
        rb_rating.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {

            @Override
            public void onRatingChanged(RatingBar ratingBar, float rating,
                    boolean fromUser) {
                if (fromUser && App.mClementine.getCurrentSong() != null) {
                    // Send the rat	ing message to Clementine
                    Message msg = Message.obtain();
                    msg.obj = ClementineMessageFactory.buildRateTrack(rating / 5);
                    App.mClementineConnection.mHandler.sendMessage(msg);

                    // Show a toast
                    String toast = getString(R.string.song_info_rated);
                    Toast.makeText(getActivity(), toast.replace("$stars$", Float.toString(rating)),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateTrackMetadata();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.song_info_menu, menu);

        // Shall we show the lastfm buttons?
        boolean showLastFm = mSharedPref.getBoolean(SharedPreferencesKeys.SP_LASTFM, true);
        menu.findItem(R.id.love).setVisible(showLastFm);
        menu.findItem(R.id.ban).setVisible(showLastFm);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.love:
                if (App.mClementine.getCurrentSong() != null
                        && !App.mClementine.getCurrentSong().isLoved()) {
                    // You can love only one
                    Message msg = Message.obtain();
                    msg.obj = ClementineMessage.getMessage(MsgType.LOVE);
                    App.mClementineConnection.mHandler.sendMessage(msg);
                    App.mClementine.getCurrentSong().setLoved(true);
                }
                Toast.makeText(getActivity(), R.string.track_loved, Toast.LENGTH_SHORT).show();
                ;
                break;
            case R.id.ban:
                Message msg = Message.obtain();
                msg.obj = ClementineMessage.getMessage(MsgType.BAN);
                App.mClementineConnection.mHandler.sendMessage(msg);
                Toast.makeText(getActivity(), R.string.track_banned, Toast.LENGTH_SHORT).show();
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public void MessageFromClementine(ClementineMessage clementineMessage) {
        switch (clementineMessage.getMessageType()) {
            case CURRENT_METAINFO:
                updateTrackMetadata();
                break;
            default:
                break;
        }
    }

    /**
     * The track changed. Update the metadata shown on the user interface
     */
    public void updateTrackMetadata() {
        // Get the currently played song
        MySong currentSong = App.mClementine.getCurrentSong();
        if (currentSong == null) {
            tv_artist.setText("");
            tv_title.setText(getString(R.string.player_nosong));
            tv_album.setText("");
            tv_genre.setText("");
            tv_year.setText("");
            tv_track.setText("");
            tv_disc.setText("");
            tv_playcount.setText("");
            tv_length.setText("");
            tv_size.setText("");
            tv_filename.setText("");

            rb_rating.setRating(0);
        } else {
            tv_artist.setText(currentSong.getArtist());
            tv_title.setText(currentSong.getTitle());
            tv_album.setText(currentSong.getAlbum());
            tv_genre.setText(currentSong.getGenre());
            tv_year.setText(currentSong.getYear());
            tv_track.setText(currentSong.getTrack() != -1 ? String.valueOf(currentSong.getTrack()) : "");
            tv_disc.setText(currentSong.getDisc() != -1 ? String.valueOf(currentSong.getDisc()) : "");
            tv_playcount.setText(String.valueOf(currentSong.getPlaycount()));
            tv_length.setText(currentSong.getPrettyLength());
            tv_size.setText(Utilities.humanReadableBytes(currentSong.getSize(), true));
            tv_filename.setText(currentSong.getFilename());

            rb_rating.setRating(currentSong.getRating() * 5);

            if (currentSong.getArt() != null) {
                iv_art.setImageBitmap(currentSong.getArt());
            }
        }

        mCurrentSong = currentSong;
    }

    private void zoomImageFromThumb(final View thumbView) {
        // If there's an animation in progress, cancel it
        // immediately and proceed with this one.
        if (mCurrentAnimator != null) {
            mCurrentAnimator.cancel();
        }

        // If we don't have an image, do not zoom!
        if (mCurrentSong == null || mCurrentSong.getArt() == null) {
            return;
        }

        // Load the high-resolution "zoomed-in" image.
        iv_large_art.setImageBitmap(mCurrentSong.getArt());

        // Calculate the starting and ending bounds for the zoomed-in image.
        // This step involves lots of math. Yay, math.
        final Rect startBounds = new Rect();
        final Rect finalBounds = new Rect();
        final Point globalOffset = new Point();

        // The start bounds are the global visible rectangle of the thumbnail,
        // and the final bounds are the global visible rectangle of the container
        // view. Also set the container view's offset as the origin for the
        // bounds, since that's the origin for the positioning animation
        // properties (X, Y).
        thumbView.getGlobalVisibleRect(startBounds);
        mContainer.getGlobalVisibleRect(finalBounds, globalOffset);
        startBounds.offset(-globalOffset.x, -globalOffset.y);
        finalBounds.offset(-globalOffset.x, -globalOffset.y);

        // Adjust the start bounds to be the same aspect ratio as the final
        // bounds using the "center crop" technique. This prevents undesirable
        // stretching during the animation. Also calculate the start scaling
        // factor (the end scaling factor is always 1.0).
        float startScale;
        if ((float) finalBounds.width() / finalBounds.height()
                > (float) startBounds.width() / startBounds.height()) {
            // Extend start bounds horizontally
            startScale = (float) startBounds.height() / finalBounds.height();
            float startWidth = startScale * finalBounds.width();
            float deltaWidth = (startWidth - startBounds.width()) / 2;
            startBounds.left -= deltaWidth;
            startBounds.right += deltaWidth;
        } else {
            // Extend start bounds vertically
            startScale = (float) startBounds.width() / finalBounds.width();
            float startHeight = startScale * finalBounds.height();
            float deltaHeight = (startHeight - startBounds.height()) / 2;
            startBounds.top -= deltaHeight;
            startBounds.bottom += deltaHeight;
        }

        // Hide the thumbnail and show the zoomed-in view. When the animation
        // begins, it will position the zoomed-in view in the place of the
        // thumbnail.
        thumbView.setAlpha(0f);
        iv_large_art.setVisibility(View.VISIBLE);

        // Set the pivot point for SCALE_X and SCALE_Y transformations
        // to the top-left corner of the zoomed-in view (the default
        // is the center of the view).
        iv_large_art.setPivotX(0f);
        iv_large_art.setPivotY(0f);

        // Construct and run the parallel animation of the four translation and
        // scale properties (X, Y, SCALE_X, and SCALE_Y).
        AnimatorSet set = new AnimatorSet();
        set
                .play(ObjectAnimator.ofFloat(iv_large_art, "x",
                        startBounds.left, finalBounds.left))
                .with(ObjectAnimator.ofFloat(iv_large_art, "y",
                        startBounds.top, finalBounds.top))
                .with(ObjectAnimator.ofFloat(iv_large_art, "scaleX",
                        startScale, 1f)).with(ObjectAnimator.ofFloat(iv_large_art,
                "scaleY", startScale, 1f));
        set.setDuration(mShortAnimationDuration);
        set.setInterpolator(new DecelerateInterpolator());
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mCurrentAnimator = null;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mCurrentAnimator = null;
            }
        });
        set.start();
        mCurrentAnimator = set;

        // Upon clicking the zoomed-in image, it should zoom back down
        // to the original bounds and show the thumbnail instead of
        // the expanded image.
        final float startScaleFinal = startScale;
        iv_large_art.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCurrentAnimator != null) {
                    mCurrentAnimator.cancel();
                }

                // Animate the four positioning/sizing properties in parallel,
                // back to their original values.
                AnimatorSet set = new AnimatorSet();
                set.play(ObjectAnimator
                        .ofFloat(iv_large_art, "x", startBounds.left))
                        .with(ObjectAnimator
                                .ofFloat(iv_large_art,
                                        "y", startBounds.top))
                        .with(ObjectAnimator
                                .ofFloat(iv_large_art,
                                        "scaleX", startScaleFinal))
                        .with(ObjectAnimator
                                .ofFloat(iv_large_art,
                                        "scaleY", startScaleFinal));
                set.setDuration(mShortAnimationDuration);
                set.setInterpolator(new DecelerateInterpolator());
                set.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        thumbView.setAlpha(1f);
                        iv_large_art.setVisibility(View.GONE);
                        mCurrentAnimator = null;
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        thumbView.setAlpha(1f);
                        iv_large_art.setVisibility(View.GONE);
                        mCurrentAnimator = null;
                    }
                });
                set.start();
                mCurrentAnimator = set;
            }
        });
    }

    @Override
    public boolean onBackPressed() {
        return false;
    }
}
