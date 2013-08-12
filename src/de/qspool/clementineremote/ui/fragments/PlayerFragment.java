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

package de.qspool.clementineremote.ui.fragments;

import java.util.List;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.actionbarsherlock.app.SherlockFragment;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.Clementine;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementinePbCreator;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.player.LyricsProvider;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.utils.Utilities;

public class PlayerFragment extends SherlockFragment {
	private final static int ANIMATION_DURATION = 750;
	private TextView mTvArtist;
	private TextView mTvTitle;
	private TextView mTvAlbum;
	
	private TextView mTvGenre;
	private TextView mTvYear;
	private TextView mTvLength;
	
	private SeekBar mSbPosition;
	
	private ImageButton mBtnNext;
	private ImageButton mBtnPrev;
	private ImageButton mBtnPlayPause;
	
	private ImageView mImgArt;
	
    private AlphaAnimation mAlphaDown; 
    private AlphaAnimation mAlphaUp;
    private boolean mCoverUpdated = false;
    
    private boolean mFirstCall = true;
    
    ProgressDialog mPdDownloadLyrics;
    
    private MySong mCurrentSong = new MySong();

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
		      Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.player_fragment,
				container, false);
    	
	    // Get the Views
	    mTvArtist = (TextView) view.findViewById(R.id.tvArtist);
	    mTvTitle  = (TextView) view.findViewById(R.id.tvTitle);
	    mTvAlbum  = (TextView) view.findViewById(R.id.tvAlbum);
	    
	    mTvGenre  = (TextView) view.findViewById(R.id.tvGenre);
	    mTvYear   = (TextView) view.findViewById(R.id.tvYear);
	    mTvLength = (TextView) view.findViewById(R.id.tvLength);
	    
	    mSbPosition = (SeekBar) view.findViewById(R.id.sbPosition);
	    
	    mBtnNext  = (ImageButton) view.findViewById(R.id.btnNext);
	    mBtnPrev  = (ImageButton) view.findViewById(R.id.btnPrev);
	    mBtnPlayPause  = (ImageButton) view.findViewById(R.id.btnPlaypause);
	    
	    mImgArt = (ImageView) view.findViewById(R.id.imgArt);

	    // Set the onclicklistener for the buttons
	    mBtnNext.setOnClickListener(oclControl);
	    mBtnPrev.setOnClickListener(oclControl);
	    mBtnPlayPause.setOnClickListener(oclControl);
	    mImgArt.setOnClickListener(oclControl);
	    mBtnPlayPause.setOnLongClickListener(olclControl);
	    
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
	    
	    reloadInfo();
	    
	    return view;
	}
    
	/**
	 * Reload the player ui
	 */
	public void reloadInfo() {
    	// display play / pause image
    	if (App.mClementine.getState() == Clementine.State.PLAY) {
    		mBtnPlayPause.setImageDrawable(getResources().getDrawable(R.drawable.ic_media_pause));
    	} else {
    		mBtnPlayPause.setImageDrawable(getResources().getDrawable(R.drawable.ic_media_play));
    	}
    	
    	// Get the currently played song
    	MySong currentSong = App.mClementine.getCurrentSong();
    	if (currentSong == null) {
    		// If none is played right now, show a text and the clementine icon
    		mTvArtist.setText(getString(R.string.player_nosong));
	    	mTvTitle. setText("");
	    	mTvAlbum. setText("");
	    	
	    	mTvGenre. setText("");
	    	mTvYear.  setText("");
	    	mTvLength.setText("");
	    	
	    	mSbPosition.setEnabled(false);
	    	
    		mImgArt.setImageResource(R.drawable.icon_large);
    	} else if (!currentSong.equals(mCurrentSong)) {
	    	mTvArtist.setText(currentSong.getArtist());
	    	mTvTitle. setText(currentSong.getTitle());
	    	mTvAlbum. setText(currentSong.getAlbum());
	    	
	    	mTvGenre. setText(currentSong.getGenre());
	    	mTvYear.  setText(currentSong.getYear());
	    	
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
    	} else {
    		mTvLength.setText(buildTrackPosition());
    		
    		mSbPosition.setEnabled(true);
	    	mSbPosition.setMax(currentSong.getLength());
	    	mSbPosition.setProgress(App.mClementine.getSongPosition());
    	}
    	mFirstCall = false;
    }
    
	/**
	 * Build the current track position. Format: "01:30/3:33"
	 * @return The current and total track position as a string
	 */
    private String buildTrackPosition() {
    	StringBuilder sb = new StringBuilder();
    	sb.append(Utilities.PrettyTime(App.mClementine.getSongPosition()));
    	sb.append("/");
    	sb.append(Utilities.PrettyTime(App.mClementine.getCurrentSong().getLength()));
    	
    	return sb.toString();
    }
    
	/**
	 * Opens a dialog to show the lyrics
	 */
	public void showLyricsDialog() {
		// Only show lyrics dialog, if the user is still waiting for it
		if (!mPdDownloadLyrics.isShowing())
			return;
		
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
		Utilities.ShowMessageDialog(getActivity(), provider.getTitle(), provider.getContent(), false);
	}
	
	/**
	 * Get the best lyrics provider for this song (currently the one with the most characters
	 * @param providers A list of lyrics providers
	 * @return The best possible provider
	 */
	private LyricsProvider getBestLyricsProvider(List<LyricsProvider> providers) {		
		LyricsProvider bestProvider = providers.get(0);
		for (LyricsProvider lyric : providers) {
			// For now the provider with the longest lyrics wins
			if (lyric.getContent().length() > bestProvider.getContent().length())
				bestProvider = lyric;
		}
		return bestProvider;
	}
	
	private OnClickListener oclControl = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			Message msg = Message.obtain();
			
			switch(v.getId()) {
			case R.id.btnNext: 
				msg.obj = ClementineMessage.getMessage(MsgType.NEXT);
				break;
			case R.id.btnPrev: 
				msg.obj = ClementineMessage.getMessage(MsgType.PREVIOUS);
				break;
			case R.id.btnPlaypause: 
				msg.obj = ClementineMessage.getMessage(MsgType.PLAYPAUSE);
				break;
			case R.id.imgArt:
				// Shall we download the lyrics or do we have them already downloaded?
				mPdDownloadLyrics.show();
				if (mCurrentSong.getLyricsProvider().isEmpty()) {
					msg.obj = ClementineMessage.getMessage(MsgType.GET_LYRICS);
				} else {
					showLyricsDialog();
				}
				break;
		    default: break;
			}
			// Send the request to the thread
			App.mClementineConnection.mHandler.sendMessage(msg);
		}
	};
	
	private OnLongClickListener olclControl = new OnLongClickListener() {
		
		@Override
		public boolean onLongClick(View v) {
			boolean ret = false;
			Message msg = Message.obtain();
			
			switch(v.getId()) {
			case R.id.btnPlaypause:
				Toast.makeText(getActivity(), R.string.player_stop_after_current, Toast.LENGTH_SHORT).show();
				msg.obj = ClementineMessage.getMessage(MsgType.STOP_AFTER);
				ret = true;
				break;
			default:
				break;
			}
			
			App.mClementineConnection.mHandler.sendMessage(msg);
			return ret;
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
				msg.obj = ClementinePbCreator.buildTrackPosition(progress);
				App.mClementineConnection.mHandler.sendMessage(msg);
				
				App.mClementine.setSongPosition(progress);
			}
		}
	};
	
	private AnimationListener mAnimationListener = new AnimationListener() {
		@Override
		public void onAnimationEnd(Animation animation) {
			if (!mCoverUpdated) {
				mImgArt.setImageBitmap(App.mClementine.getCurrentSong().getArt());
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
}
