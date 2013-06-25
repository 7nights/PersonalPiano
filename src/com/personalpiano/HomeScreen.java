package com.personalpiano;


import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import com.example.personalpiano.R;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import com.personalpiano.MinutesPickerFragment.MinutesPickerListener;
import com.personalpiano.PlaybackService.MusicInfo;
import com.personalpiano.PlaybackService.PlaybackListener;

import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.app.Activity;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RotateDrawable;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

public class HomeScreen extends Activity implements MinutesPickerListener, OnTouchListener{
	
	private boolean stopService = false;
	private int savedCursorIndex = -1;
	private PlaybackService playbackService = null;
	private boolean isPaused = true;
	// 正在播放的歌曲是否是默认cover, 这将影响到歌曲切换时的动画
	//private boolean lastCover = false;
	private Animation lastAnimation = null;
	private boolean listenerAdded = false;
	private Menu actionBarMenu = null;
	private String lastMusicDisplayName = null;
	final String PREF_INITIALIZED = "initialized";
	private SlidingMenu slidingMenu;
	private Timer sleepTimer = null;
	private int sleepLeft = -1;
	private MotionEvent lastTouchDownEvent = null;
	private float lastTouchDownX = -1;
	private int lastTouchDownTime = 0;
	private int screenWidth = 1;

	@Override
	protected void onStart(){
		super.onStart();
	}
	
	@Override
	protected void onResume(){
		super.onResume();
		
		if(playbackService != null){
			playbackService.hideNotification();
			if(!listenerAdded){
				playbackService.setPlaybackListener(".HomeScreen", playbackListener);
			}
			if(actionBarMenu != null){
				MenuItem btn_actionPlay = actionBarMenu.findItem(R.id.action_play);
				if(playbackService.isPlaying()){
					btn_actionPlay.setIcon(R.drawable.pause);
				} else {
					btn_actionPlay.setIcon(R.drawable.play);
				}
			}
			playbackListener.onMusicUpdate(playbackService.getCurrentMusic(), false);
		}
		
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_home_screen);
		if(savedInstanceState != null ){
			savedCursorIndex = savedInstanceState.getInt("SavedCursorIndex");
			Log.v("SAVEINSTANCE", "true");
		}
		Rect outSize = new Rect();
		getWindowManager().getDefaultDisplay().getRectSize(outSize);
		screenWidth = outSize.width();
		
		getActionBar().setDisplayHomeAsUpEnabled(true);
		getActionBar().setDisplayShowHomeEnabled(false);

		SlidingMenu menu = new SlidingMenu(this);
		menu.setMode(SlidingMenu.LEFT);
	    menu.setTouchModeAbove(SlidingMenu.TOUCHMODE_MARGIN);
	    menu.setShadowWidthRes(R.dimen.shadow_width);
	    menu.setShadowDrawable(R.drawable.shadow);
	    menu.setBehindOffsetRes(R.dimen.slidingmenu_offset);
	    menu.setFadeDegree(0.35f);
	    menu.attachToActivity(this, SlidingMenu.SLIDING_CONTENT);
	    menu.setMenu(R.layout.sliding_menu);
	    slidingMenu = menu;
	    
	    // Spinner设置
	    initSpinner();
	    initPreference();
	    reloadPreference();
	    
	    // 退出按钮
	    findViewById(R.id.bottom_button).setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				stopService = true;
				if(playbackService != null && playbackService.isPlaying()){
					playbackService.pauseMusic();
				}
				finish();
			}
		});
	    
	    // 睡眠模式
	    findViewById(R.id.set_sleep).setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				MinutesPickerFragment mp = new MinutesPickerFragment();
				mp.show(getFragmentManager(), "minutesPicker");
			}
		});
	    
	    // 横扫屏幕下一首歌
	    findViewById(R.id.cover_image).setOnTouchListener(this);
	    
	    Intent intent = new Intent();
	    intent.setClass(this, PlaybackService.class);
	    intent.setAction(PlaybackService.ACTION_START);
	    startService(intent);

	    intent = new Intent();
	    intent.setClass(this, PlaybackService.class);
	    intent.setAction(PlaybackService.ACTION_START);
	    bindService(intent, conn, BIND_AUTO_CREATE);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.home_screen, menu);
		actionBarMenu = menu;
		MenuItem btn_actionPlay = menu.findItem(R.id.action_play);
		if(playbackService != null && playbackService.isPlaying()){
			btn_actionPlay.setIcon(R.drawable.pause);
		} else {
			btn_actionPlay.setIcon(R.drawable.play);
		}
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// TODO Auto-generated method stub
		switch(item.getItemId()){
			case android.R.id.home:
				slidingMenu.toggle(true);
				break;
			case R.id.action_play:
				if(savedCursorIndex != -1){
					//TODO 移动游标到保存的位置
				}
				if(playbackService != null){
					if(isPaused){
						playbackService.playMusic();
						isPaused = false;
						item.setIcon(R.drawable.pause);
					} else {
						playbackService.pauseMusic();
						isPaused = true;
						item.setIcon(R.drawable.play);
					}
				}
				break;
		}
		return true;
	}
	private void reloadPreference(){
		SharedPreferences prefs = getPreferences(MODE_PRIVATE);
		// play mode
		int play_mode = prefs.getInt(Util.getStringFromResource(this, R.string.pref_play_mode), PlaybackService.PLAY_MODE_DEFAULT);
		
		((Spinner)findViewById(R.id.play_mode)).setSelection(play_mode);
	}
	private void initPreference(){
		SharedPreferences prefs = getPreferences(MODE_PRIVATE);
		prefs.registerOnSharedPreferenceChangeListener(new OnSharedPreferenceChangeListener() {
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
					String key) {
				reloadPreference();
			}
		});
	}
	private boolean applyPrefToService(){
		if(playbackService != null){
			SharedPreferences prefs = getPreferences(MODE_PRIVATE);
			playbackService.setPlayMode(prefs.getInt(Util.getStringFromResource(this, R.string.pref_play_mode), PlaybackService.PLAY_MODE_DEFAULT));
		}
		return false;
	}
	
	private void initSpinner(){
		Spinner playModeSpinner = (Spinner)findViewById(R.id.play_mode);
	    ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.play_mode_array, R.layout.my_spinner);
	    playModeSpinner.setAdapter(adapter);
	    playModeSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int pos, long id) {
				SharedPreferences prefs = getPreferences(MODE_PRIVATE);
				Editor e = prefs.edit();
				e.putInt(Util.getStringFromResource(HomeScreen.this, R.string.pref_play_mode), pos);
				e.commit();
				reloadPreference();
				applyPrefToService();
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {}
		});
	   
	}
	
	private class FadeOutAnimation extends Animation implements Animation.AnimationListener{
		public FadeOutAnimation(){
			setAnimationListener(this);
		}
		@Override
		public void initialize(int width, int height, int parentWidth,
				int parentHeight) {
			// TODO Auto-generated method stub
			super.initialize(width, height, parentWidth, parentHeight);
			setDuration(300);
			setFillAfter(true);
			setInterpolator(new AccelerateInterpolator());
		}
		@Override
		protected void applyTransformation(float interpolatedTime,
				Transformation t) {
			// TODO Auto-generated method stub
			super.applyTransformation(interpolatedTime, t);
			t.setAlpha(1 - interpolatedTime);
		}
		@Override
		public void onAnimationEnd(Animation animation) {
			//((ImageView) findViewById(R.id.cover_image)).setImageDrawable(getResources().getDrawable(R.drawable.music));
		}
		@Override
		public void onAnimationRepeat(Animation animation) {
			// TODO Auto-generated method stub
			
		}
		@Override
		public void onAnimationStart(Animation animation) {
			// TODO Auto-generated method stub
			
		}
	}
	
	public class RotateYAnimation extends Animation implements Animation.AnimationListener{
		private float centerX, centerY;
		private Camera camera = new Camera();
		private boolean inverse;
		public onAnimationEndListener onEndListener = null;
		public boolean isCanceled = false;
		public HashMap data = new HashMap();
		public boolean endCalled = false;
		
		public RotateYAnimation( boolean _inverse){
			inverse = _inverse;
			setAnimationListener(this);
		}
		
		@Override
		public void initialize(int width, int height, int parentWidth,
				int parentHeight) {
			// TODO Auto-generated method stub
			super.initialize(width, height, parentWidth, parentHeight);
			setDuration(400);
			setFillAfter(true);
			if(!inverse){
				setInterpolator(new AccelerateInterpolator());
			} else {
				setInterpolator(new DecelerateInterpolator());
			}
			centerX = width / 2;
			centerY = height / 2;
		}
		@Override
		protected void applyTransformation(float interpolatedTime,
				Transformation t) {
			final Matrix matrix = t.getMatrix();
			if(interpolatedTime == 1){
				return;
			}
			camera.save();
			
			if(!inverse){
				camera.translate(0.0f, 0.0f, 400 * interpolatedTime);
				camera.rotateY(-90 * interpolatedTime);
			} else {
				camera.translate(0.0f, 0.0f, 400 * (1 - interpolatedTime));
				camera.rotateY(-270 + -90 * interpolatedTime);
			}
			camera.getMatrix(matrix);
			
			matrix.preTranslate(-centerX, -centerY);
			matrix.postTranslate(centerX,  centerY);
			
			camera.restore();
		}
		@Override
		public void cancel(){
			isCanceled = true;
			super.cancel();
		}
		
		public void setOnEndListener(onAnimationEndListener listener){
			onEndListener = listener;
		}

		@Override
		public void onAnimationEnd(Animation animation) {
			// TODO Auto-generated method stub
			Log.v("DEBUG", "animation end");
			if(onEndListener != null && !endCalled){
				endCalled = true;
				onEndListener.onAnimationEnd(animation);
			}
		}

		@Override
		public void onAnimationRepeat(Animation animation) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onAnimationStart(Animation animation) {
			// TODO Auto-generated method stub
			
		}

	}
	
	public interface onAnimationEndListener{
		public void onAnimationEnd(Animation animation);
	}
	public class myPlaybackListener implements PlaybackListener{
		@Override
		public boolean onTimeUpdate(int current, int duration, MusicInfo info) {
			ProgressBar pb = (ProgressBar)findViewById(R.id.progressBar);
			pb.setProgress((int)((Float.valueOf(current) / duration) * 1000));
			return false;
		}
		public boolean onMusicUpdate(MusicInfo info, boolean doCoverAnimation){
			if(playbackService.isPlaying()){
				MenuItem btn_actionPlay = actionBarMenu.findItem(R.id.action_play);
				isPaused = false;
				btn_actionPlay.setIcon(R.drawable.pause);
			}
			TextView title = (TextView)findViewById(R.id.songName);
			TextView artist = (TextView)findViewById(R.id.singer);
			TextView album = (TextView)findViewById(R.id.albumName);
			TextView duration = (TextView)findViewById(R.id.totalTime);

			final ImageView cover = (ImageView) findViewById(R.id.cover_image);

			if(info == null){
				title.setText("");
				artist.setText("");
				album.setText("");
				duration.setText("0:00");
				//cover.setImageAlpha(0);
				return false;
			}
			title.setText(info.title);
			artist.setText(" - " + info.artist);
			album.setText(info.album);
			
			int sec = info.duration / 1000 % 60;
			duration.setText(Integer.toString(info.duration / 60000) + ":" + (sec<10?"0"+Integer.toString(sec):sec));
			
			onTimeUpdate(0, info.duration, info);
			
			// 播放动画
			if(!doCoverAnimation){
				cover.setImageBitmap(info.getAlbumArt(playbackService));
				return false;
			}
			
			RotateYAnimation ra = new RotateYAnimation(false);
			ra.data.put("bitmap", info.getAlbumArt(playbackService));

			ra.setOnEndListener(new onAnimationEndListener() {
				@Override
				public void onAnimationEnd(Animation animation) {
					RotateYAnimation _animation = (RotateYAnimation) animation;
					cover.setImageBitmap((Bitmap)_animation.data.get("bitmap"));
					if(!_animation.isCanceled){
						RotateYAnimation ra = new RotateYAnimation(true);
						lastAnimation = ra;
						cover.startAnimation(ra);
					}

				}
			});
			cover.startAnimation(ra);
			lastAnimation = ra;
			
			return false;
		}
		@Override
		public boolean onMusicUpdate(MusicInfo info) {
			
			if(lastMusicDisplayName != null && lastMusicDisplayName.equals(info.display_name)){
				return false;
			}
			if(info != null){
				lastMusicDisplayName = info.display_name;
			}
			return onMusicUpdate(info, true);
		}
		
		@Override
		public boolean onEnd(MusicInfo info) {
			// TODO Auto-generated method stub
			return false;
		}
	}
	public myPlaybackListener playbackListener = new myPlaybackListener();
	public ServiceConnection conn = new ServiceConnection(){
		@Override
		public void onServiceConnected(ComponentName name, IBinder service){
			playbackService = ((PlaybackService.PlaybackBinder) service).getService();
			
			// 注册播放事件监听器
			playbackService.setPlaybackListener(".HomeScreen", playbackListener);
			listenerAdded = true;
			
			playbackListener.onMusicUpdate(playbackService.getCurrentMusic());
			
			// 注册歌曲列表按钮
			ImageButton musicList = (ImageButton)findViewById(R.id.list);
			musicList.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent intent = new Intent();
					intent.setClass(HomeScreen.this, ListViewer.class);
					intent.setAction(ListViewer.ACTION_VIEW_LIST);
					int mode = playbackService.getCurrentPlayMode();
					if(mode == playbackService.PLAY_MODE_RANDOM){
						intent.putExtra("list_id", playbackService.LIST_ID_RANDOM);
					} else if(mode == playbackService.PLAY_MODE_NORMAL){
						intent.putExtra("list_id", playbackService.LIST_ID_NORMAL);
					}
					startActivity(intent);
				}
			});
			
			// 注册下一首按钮 和 上一首
			ImageButton nextMusic = (ImageButton)findViewById(R.id.nextMusic);
			nextMusic.setOnClickListener(new View.OnClickListener() {
				
				@Override
				public void onClick(View v) {
					if(isPaused){
						isPaused = false;
						actionBarMenu.findItem(R.id.action_play).setIcon(R.drawable.pause);
						playbackService.playMusic();
					}
					playbackService.playMusic();
					
				}
			});
			ImageButton backMusic = (ImageButton)findViewById(R.id.backMusic);
			backMusic.setOnClickListener(new View.OnClickListener() {
				
				@Override
				public void onClick(View v) {
					int pos = playbackService.getListPosition();
					if(pos == -1){
						return;
					}
					playbackService.setListPosition(pos - 2);
					if(isPaused){
						isPaused = false;
						actionBarMenu.findItem(R.id.action_play).setIcon(R.drawable.pause);
					}
					playbackService.playMusic();
				}
			});
			
			
			// 根据歌曲状态修改按钮
			if(actionBarMenu != null){
				MenuItem btn_actionPlay = actionBarMenu.findItem(R.id.action_play);
				if(playbackService.isPlaying()){
					isPaused = false;
					btn_actionPlay.setIcon(R.drawable.pause);
				} else {
					isPaused = true;
					btn_actionPlay.setIcon(R.drawable.play);
				}
			}
			playbackService.hideNotification();
			
			// 如果没有正在播放，就应pref
			if(!playbackService.isPlaying()){
				applyPrefToService();
			}
		}
		
		@Override
		public void onServiceDisconnected(ComponentName name){
			Log.v("SERVICE", "disconnected");
		}
	};
	
	@Override
	protected void onStop() {
		super.onStop();
		if(playbackService != null && playbackService.isPlaying() && stopService != true){
			Log.v("HomeScreen:312", "Show Notification");
			playbackService.showNotification();
		} else {
			//Log.v("HomeScreen:315", Boolean.toString(playbackService.isPlaying()));
		}
		playbackService.removePlaybackListener(".HomeScreen");
		listenerAdded = false;
	};
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(conn);
		if(stopService){
			Log.v("stopService", "true");
			playbackService.stopAndExit();
		}
		Log.v("Activity", "Destroying");
	};
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		Log.v("Activity", "save instance state");
		// TODO Auto-generated method stub
		super.onSaveInstanceState(outState);
	}
	
	public class setSleepLeftTextHandler extends Handler{
		@Override
		public void handleMessage(Message msg){
			TextView v = (TextView)findViewById(R.id.sleep_left);
			v.setText(sleepLeft + " min(s)");
		}
	}

	@Override
	public void onSelect(int minute) {
		TextView v = (TextView)findViewById(R.id.sleep_left);
		v.setText(minute + " min(s)");
		sleepLeft = minute;
		if(sleepTimer != null){
			
		} else {
			final setSleepLeftTextHandler myHandler = new setSleepLeftTextHandler();
			sleepTimer = new Timer();
			sleepTimer.schedule(new TimerTask() {
				
				@Override
				public void run() {
					sleepLeft--;
					if(sleepLeft <= 0){
						stopService = true;
						finish();
						this.cancel();
					}
					myHandler.sendEmptyMessage(0);
				}
			}, 60000, 60000);
		}
	}

	@Override
	public void onCancel(DialogFragment dialog) {
		TextView v = (TextView)findViewById(R.id.sleep_left);
		v.setText(Util.getStringFromResource(this, R.string.disabled));
		if(sleepTimer != null){
			sleepTimer.cancel();
			sleepTimer = null;
			sleepLeft = -1;
		}
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {

		if(event.getAction() == MotionEvent.ACTION_DOWN){
			lastTouchDownX = event.getX();
		} else if(event.getAction() == MotionEvent.ACTION_UP){
			if((event.getX() - lastTouchDownX) / (event.getEventTime() - event.getDownTime()) * 800 < (0 - screenWidth)){
				playbackService.playMusic();
				isPaused = false;
				MenuItem btn_actionPlay = actionBarMenu.findItem(R.id.action_play);
				btn_actionPlay.setIcon(R.drawable.pause);
			} else {
				Log.v("distance", "distance: " + (event.getX() - lastTouchDownX));
				Log.v("distance", "distance%: " + (event.getX() - lastTouchDownX)/screenWidth);
				Log.v("distance", "time: " + event.getDownTime());
			}
		}
		return true;
	}
	

}
