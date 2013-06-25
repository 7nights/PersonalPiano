package com.personalpiano;

import java.util.ArrayList;
import java.util.HashMap;

import com.example.personalpiano.R;
import com.personalpiano.PlaybackService.MusicInfo;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnScrollChangedListener;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class ListViewer extends Activity implements OnScrollListener{
	public static final String ACTION_VIEW_LIST = "com.personalpiano.action.VIEW_LIST";
	public static final int LIST_ID_RANDOM = -1;
	public static final int LIST_ID_NORMAL = -2;
	public static final int MAX_CACHED_ART = 15;
	private boolean mBusy = false;
	
	private PlaybackService playbackService = null;
	private int list_id = 0;

	private MusicInfo[] cachedInfo = null;
	private ArrayList<WrappedMusicInfo> cachedArtInfo = new ArrayList<WrappedMusicInfo>();

	private ListView list;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent initIntent = getIntent();
		if(initIntent.getAction().equals(ACTION_VIEW_LIST)){
			list_id = initIntent.getIntExtra("list_id", LIST_ID_NORMAL);
			Log.v("ListId", "" + list_id);
			Intent intent = new Intent();
		    intent.setClass(this, PlaybackService.class);
		    intent.setAction(PlaybackService.ACTION_START);
		    bindService(intent, conn, BIND_AUTO_CREATE);
		}
		setContentView(R.layout.list_viewer);
		list = (ListView)findViewById(R.id.list_viewer_list);
		list.setOnScrollListener(this);
	}
	
	public ServiceConnection conn = new ServiceConnection() {
		
		@Override
		public void onServiceDisconnected(ComponentName name) {
			// TODO Auto-generated method stub
			
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			playbackService = ((PlaybackService.PlaybackBinder) service).getService();
			ListView list = (ListView)findViewById(R.id.list_viewer_list);
			list.setAdapter(new selfAdapter(ListViewer.this));
			cachedInfo = new MusicInfo[playbackService.getCount()];
		}
	};
	
	public class selfAdapter extends BaseAdapter{
		private LayoutInflater mLayoutInflater;
		
		public selfAdapter(Context ctx){
			this.mLayoutInflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		@Override
		public int getCount() {
			if(list_id == -1 || list_id == -2){
				return playbackService.getCount();
			}
			return 0;
		}

		@Override
		public Object getItem(int position) {
			//MusicInfo info = playbackService.getMusicInList(list_id, position);
			return null;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			if(convertView == null){
				convertView = mLayoutInflater.inflate(R.layout.card_list_item, null);
				ViewHolder holder = new ViewHolder();
				TextView title = (TextView)convertView.findViewById(R.id.list_song_name);
				holder.title = title;
				ImageView iv = (ImageView)convertView.findViewById(R.id.list_album_art);
				holder.position = position;
				holder.album_art = iv;
				convertView.setTag(holder);
				convertView.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View v) {
						playbackService.setListPosition(((ViewHolder)v.getTag()).position);
						if(!playbackService.isPlaying()){
							playbackService.pauseMusic();
						}
						playbackService.playMusic();
						finish();
					}
				});
			}
			if(((AbsListView)parent).getFirstVisiblePosition() > (position + 1)){
				Log.e("Error", "try to get an invisible item: " + position);
				return convertView;
			}
			MusicInfo info;
			ViewHolder holder = (ViewHolder)convertView.getTag();
			if(holder.imageSet == true){
				holder.imageSet = false;
			}
			holder.album_art.setImageResource(R.drawable.music);
			if(cachedInfo[position] == null){
				info = playbackService.getMusicInList(list_id, position);
				cachedInfo[position] = info;
			} else {
				info = cachedInfo[position];
			}

			if(info.album_art != null){
				holder.imageSet = true;
				holder.album_art.setImageBitmap(info.album_art);
				putIntoCache(position, info);
			} else if(!mBusy){
				SuperViewHolder svh = new SuperViewHolder();
    			svh.position = position;
    			Log.v("init", "position: " + position);
    			svh.album_art = holder.album_art;
    			svh.title = holder.title;
    			svh.info = info;
    			new GetAlbumArt().execute(svh);
    			svh = null;
				holder.imageSet = true;
			}
			holder.position = position;
			holder.title.setText(info.title);
			return convertView;
		}
		
		
	}
	
	public void putIntoCache(int pos, MusicInfo info){
		boolean inserted = false;
		WrappedMusicInfo wInfo = new WrappedMusicInfo();
		wInfo.position = pos;
		wInfo.info = info;
		for(int i = 0; i < cachedArtInfo.size(); i++){
			if(pos == cachedArtInfo.get(i).position){
				inserted = true;
				break;
			}
			if(pos < cachedArtInfo.get(i).position){
				inserted = true;
				if(cachedArtInfo.size() >= MAX_CACHED_ART){
					if(pos <= cachedArtInfo.get((MAX_CACHED_ART-1)/2).position){
						Log.v("recycle for: " + pos, "up, head: " + cachedArtInfo.get(0).position + "; tail: " + cachedArtInfo.get(cachedArtInfo.size()- 1).position);
						int temp = cachedArtInfo.size() - 1;
						if(cachedArtInfo.get(temp).info.album_art != null){
							cachedArtInfo.get(temp).info.album_art.recycle();
							cachedArtInfo.get(temp).info.album_art = null;
						}
						cachedArtInfo.remove(temp);
					} else {
						Log.v("recycle", "down");
						if(cachedArtInfo.get(0).info.album_art != null){
							cachedArtInfo.get(0).info.album_art.recycle();
							cachedArtInfo.get(0).info.album_art = null;
						}
						cachedArtInfo.remove(0);
					}
				}
				cachedArtInfo.add(i, wInfo);
				break;
			}
		}
		if(!inserted){
			if(cachedArtInfo.size() >= MAX_CACHED_ART){
				if(cachedArtInfo.get(0).info.album_art != null){
					cachedArtInfo.get(0).info.album_art.recycle();
					cachedArtInfo.get(0).info.album_art = null;
				}
				cachedArtInfo.remove(0);
			}
			cachedArtInfo.add(wInfo);
		}
		
	}
	
	public class WrappedMusicInfo{
		int position;
		MusicInfo info;
	}
	
	public class ViewHolder{
		TextView title;
		ImageView album_art;
		int position;
		boolean imageSet = false;
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem,
			int visibleItemCount, int totalItemCount) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		// TODO Auto-generated method stub
		switch (scrollState) {
        case OnScrollListener.SCROLL_STATE_IDLE:
            mBusy = false;
            
            int first = view.getFirstVisiblePosition();
            int count = view.getChildCount();
            
            for (int i=0; i<count; i++) {
            	View t = view.getChildAt(i);
            	
            	if(t != null){
            		ViewHolder vh = (ViewHolder)t.getTag();

            		if(vh.imageSet == false){
            			vh.imageSet = true;
            			MusicInfo info = cachedInfo[i + first];
            			/*
            			if(info.getAlbumArt(playbackService) == null){
            				vh.album_art.setImageResource(R.drawable.music);
            			} else {
            				putIntoCache(i + first, info);
            				vh.album_art.setImageBitmap(info.album_art);
            			}*/
            			SuperViewHolder svh = new SuperViewHolder();
            			svh.position = i + first;
            			svh.album_art = vh.album_art;
            			svh.title = vh.title;
            			svh.info = info;
            			new GetAlbumArt().execute(svh);
            			svh = null;
            			t.setTag(vh);
            		}
            		
            	}
            }
            
            break;
        case OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
            mBusy = true;
            break;
        case OnScrollListener.SCROLL_STATE_FLING:
            mBusy = true;
            break;
        }
	}
	
	public class SuperViewHolder extends ViewHolder{
		MusicInfo info;
		int position;
	}
	
	public class GetAlbumArt extends AsyncTask<SuperViewHolder, Void, Bitmap>{
		private SuperViewHolder v;
		@Override
		protected Bitmap doInBackground(SuperViewHolder... arg0) {
			v = arg0[0];
			return arg0[0].info.getAlbumArt(playbackService);
		}
		
		@Override
		protected void onPostExecute(Bitmap result) {
			super.onPostExecute(result);
			if(result != null){
				v.album_art.setImageBitmap(result);
				putIntoCache(v.position, v.info);
			}
		}
		
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		for(int i = cachedArtInfo.size(); i-- != 0;){
			if(cachedArtInfo.get(i).info.album_art != null){
				cachedArtInfo.get(i).info.album_art.recycle();
			}
		}
		cachedArtInfo = null;
		cachedInfo = null;
		playbackService = null;
		System.gc();
		unbindService(conn);
	}


}
