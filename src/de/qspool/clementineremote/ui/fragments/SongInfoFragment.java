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

package de.qspool.clementineremote.ui.fragments;

import android.os.Bundle;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.view.MenuItem;

import de.qspool.clementineremote.App;
import de.qspool.clementineremote.R;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineMessageFactory;
import de.qspool.clementineremote.backend.player.MySong;
import de.qspool.clementineremote.utils.Utilities;

public class SongInfoFragment extends AbstractDrawerFragment {
	private ImageView iv_art;
	
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
    
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
		      Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.songinfo_fragment,
				container, false);
		
		iv_art = (ImageView) view.findViewById(R.id.si_art);
		
		tv_artist = (TextView) view.findViewById(R.id.si_artist);
		tv_title  = (TextView) view.findViewById(R.id.si_title);
		tv_album  = (TextView) view.findViewById(R.id.si_album);
		tv_genre  = (TextView) view.findViewById(R.id.si_genre);
		tv_year  = (TextView) view.findViewById(R.id.si_year);
		tv_track  = (TextView) view.findViewById(R.id.si_track);
		tv_disc  = (TextView) view.findViewById(R.id.si_disc);
		tv_playcount  = (TextView) view.findViewById(R.id.si_playcount);
		tv_length  = (TextView) view.findViewById(R.id.si_length);
		tv_size  = (TextView) view.findViewById(R.id.si_size);
		tv_filename  = (TextView) view.findViewById(R.id.si_filename);
		
		rb_rating = (RatingBar) view.findViewById(R.id.si_rating);
		rb_rating.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
			
			@Override
			public void onRatingChanged(RatingBar ratingBar, float rating,
					boolean fromUser) {
				if (fromUser) {
					// Send the rat	ing message to Clementine
					Message msg = Message.obtain();
					msg.obj = ClementineMessageFactory.buildRateTrack(rating / 5);
					App.mClementineConnection.mHandler.sendMessage(msg);
					
					// Show a toast
					String toast = getString(R.string.song_info_rated);
					Toast.makeText(getActivity(), toast.replace("$stars$", Float.toString(rating)), Toast.LENGTH_SHORT).show();
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
	public boolean onOptionsItemSelected(MenuItem item) {

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
  
    	} else {
    		tv_artist.setText(currentSong.getArtist());
    		tv_title.setText(currentSong.getTitle());
    		tv_album.setText(currentSong.getAlbum());
    		tv_genre.setText(currentSong.getGenre());
    		tv_year.setText(currentSong.getYear());
    		tv_track.setText(String.valueOf(currentSong.getTrack()));
    		tv_disc.setText(String.valueOf(currentSong.getDisc()));
    		tv_playcount.setText(String.valueOf(currentSong.getPlaycount()));
    		tv_length.setText(currentSong.getPrettyLength());
    		tv_size.setText(Utilities.humanReadableBytes(currentSong.getSize(), true));
    		tv_filename.setText(currentSong.getFilename());
    		
    		rb_rating.setRating(currentSong.getRating() * 5);
    		
    		if (currentSong.getArt() != null) {
	    		iv_art.setImageBitmap(currentSong.getArt());
	    	}
    	}
    }
}
