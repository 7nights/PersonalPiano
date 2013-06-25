package com.personalpiano;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TimerTask;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Timer;

import com.example.personalpiano.R;
import com.personalpiano.PlaybackService.MusicInfo;
import com.personalpiano.PlaybackService.PlaybackListener;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class PlaybackService extends Service{
	public static final int LIST_ID_RANDOM = -1;
	public static final int LIST_ID_NORMAL = -2;
	private final PlaybackBinder mBinder = new PlaybackBinder();
	private final MediaPlayer mMediaPlayer = new MediaPlayer();
	private Cursor mMediaCursor = null;
	private int[] randomArr = null;
	private int randomIndex = 0;
	private int normalIndex = 0;
	private int playMode = 1;
	private HashMap<String, PlaybackListener> playbackListeners = new HashMap<String, PlaybackListener>();
	private MusicInfo currentMusicInfo = null;
	private Timer timeUpdater = null;
	private short listMode = LIST_MODE_DEFAULT;
	private boolean notificationShown = false;
	private boolean musicPrepared = false;
	public static final short PLAYBACK_SERVICE_NOTIFICATION_ID = 1;
	public NotificationCompat.Builder mBuilder = null;
	public static final short PLAY_MODE_NORMAL = 0x0000;
	public static final short PLAY_MODE_RANDOM = 0x0001;
	public static final short PLAY_MODE_DEFAULT = 0x0001;
	public static final short PLAY_MODE_SINGLE_REPEAT = 0x0002;
	public static final short LIST_MODE_DEFAULT = 0x0000;
	public static final short LIST_MODE_CUSTOM = 0x0001;
	public static final String ACTION_START = "com.personalpiano.action.START";
	
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return mBinder;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO Auto-generated method stub
		return super.onStartCommand(intent, flags, startId);
	}
	
	@Override
	public void onCreate() {
		// 从系统获取歌曲列表
		getWholeMusicList();
		// 重设随机列表
		resetRandomOrder();
		// 注册播放完成事件
		mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
			
			@Override
			public void onCompletion(MediaPlayer mp) {
				musicPrepared = false;
				handlePlaybackListener("end");
				playMusic();
			}
		});
		super.onCreate();
	}
	
	public class PlaybackBinder extends Binder{
		PlaybackService getService(){
			return PlaybackService.this;
		}
	}
	
	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		Log.v("Service", "Service Destroying...");
		super.onDestroy();
	}
	
	public interface PlaybackListener{
		public boolean onTimeUpdate(int current, int duration, MusicInfo info);
		public boolean onEnd(MusicInfo info);
		public boolean onMusicUpdate(MusicInfo info);
		//public boolean onPause(int current, MusicInfo info);
	}
	
	private int getWholeMusicList(){
		ContentResolver cr = getContentResolver();
		mMediaCursor = cr.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, 
				new String[] {
					MediaStore.Audio.Media.TITLE,
					MediaStore.Audio.Media.ARTIST,
					MediaStore.Audio.Media.DURATION,
					MediaStore.Audio.Media._ID,
					MediaStore.Audio.Media.ALBUM,
					MediaStore.Audio.Media.ALBUM_ID,
					MediaStore.Audio.Media.DISPLAY_NAME,
					MediaStore.Audio.Media.DATA,
				}, MediaStore.Audio.Media.IS_MUSIC + " = 1", null, MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
		return mMediaCursor.getCount();
	}
	
	public boolean isPlaying(){
		return mMediaPlayer.isPlaying();
	}
	public void resetRandomOrder(){
		randomArr = new int[mMediaCursor.getCount()];
		randomIndex = 0;
		
		for(int i = randomArr.length; i-- != 0;){
			randomArr[i] = i;
		}
		
		for(int i = 0, length = randomArr.length; i < length; i++){
			int index = (int) (Math.random() * (length - i) + i);
			int temp = randomArr[index];
			randomArr[index] = randomArr[i];
			randomArr[i] = temp;
		}

	}
	public static class MusicInfo{
		public Uri uri;
		public String title;
		public String artist;
		public String album;
		public int album_id;
		public int duration;
		public String display_name;
		public Bitmap album_art = null;
		
		public MusicInfo(Uri _uri, String _title, String _artist, String _album, int _album_id, int _duration, String _display_name){
			uri = _uri;
			title = _title;
			artist = _artist;
			album = _album;
			album_id = _album_id;
			duration = _duration;
			display_name = _display_name;
		}
		
		public Bitmap getAlbumArt(PlaybackService s){
			if(album_art != null)	return album_art;
			Bitmap bm = s.getAlbumArt(album_id);
			album_art = bm;
			return album_art;
		}
	}
	
	private Bitmap getAlbumArt(int albumId){
		Uri albumsUri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
		String[] projection = new String[]{"album_art"};
		Cursor cur = this.getContentResolver().query(Uri.withAppendedPath(albumsUri, Integer.toString(albumId)), projection, null, null, null);
		
		Bitmap bm = null;
		if(cur.getCount() > 0 && cur.getColumnCount() > 0){
			cur.moveToNext();
			if(cur.getString(0) == null){
				Log.v("ALBUM_ART", "null");
			} else {
				bm = BitmapFactory.decodeFile(cur.getString(0));
			}

		}
		return bm;
	}
	
	private void play(MusicInfo musicInfo){
		play(musicInfo, 0);
	}
	private void play(MusicInfo musicInfo, int lastTime){
		// 设置当前播放的音乐
		currentMusicInfo = musicInfo;

		mMediaPlayer.reset();
		mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		
		try {
			mMediaPlayer.setDataSource(this, musicInfo.uri);
			mMediaPlayer.prepare();
			mMediaPlayer.start();
			
			// 播放成功, 开启timeUpdater
			timeUpdater = new Timer();
			timeUpdater.schedule(new TimerTask() {
				
				@Override
				public void run() {
					handlePlaybackListener("timeUpdate");
				}
			}, 0, 1000);
			// 播放成功, 触发musicUpdate事件
			handlePlaybackListener("musicUpdate");
			// 播放成功，更新notification
			updateNotification();
			
			musicPrepared = true;
			
			if(lastTime != 0){
				mMediaPlayer.seekTo(lastTime);
			}
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	private MusicInfo getMusicFromCursor(int position){
		if(!mMediaCursor.moveToPosition(position)) return null;
		return new MusicInfo(Uri.parse(mMediaCursor.getString(7)), mMediaCursor.getString(0), mMediaCursor.getString(1), mMediaCursor.getString(4), mMediaCursor.getInt(5), mMediaCursor.getInt(2), mMediaCursor.getString(6));
	}
	/**
	 * 播放当前列表所处位置的歌曲，如果不是单曲循环模式就把位置移动向下一个
	 * @return {MusicInfo} 当前播放的音乐信息
	 */
	public MusicInfo playMusic(){
		if(musicPrepared && !isPlaying()){
			mMediaPlayer.start();
			return currentMusicInfo;
		}
		MusicInfo info = null;
		// 播放当前列表所处的音乐
		if(playMode == PLAY_MODE_RANDOM){
			// 随机播放
			if(randomIndex == randomArr.length){
				resetRandomOrder();
			}
			int position = randomArr[randomIndex];
			randomIndex++;
			info = getMusicFromCursor(position);
			play(info);
		} else if(playMode == PLAY_MODE_SINGLE_REPEAT){
			// 单曲循环
			play(currentMusicInfo);
		} else if(playMode == PLAY_MODE_NORMAL){
			if(normalIndex == mMediaCursor.getCount()){
				normalIndex = 0;
			}
			info = getMusicFromCursor(normalIndex);
			normalIndex++;
			play(info);
		}
		return info;
	}
	private void handlePlaybackListener(String type){
		Set<Entry<String, PlaybackListener>> entrySet = playbackListeners.entrySet();
		Iterator<Entry<String, PlaybackListener>> iterator = entrySet.iterator();
		while(iterator.hasNext()){
			Entry<String, PlaybackListener> entry = iterator.next();
			if(type.equals("end")){
				entry.getValue().onEnd(currentMusicInfo);
			} else if(type.equals("timeUpdate")){
				if(mMediaPlayer.isPlaying()){
					entry.getValue().onTimeUpdate(mMediaPlayer.getCurrentPosition(), mMediaPlayer.getDuration(), 
						currentMusicInfo);
				}
			} else if(type.equals("musicUpdate")){
				Log.v("MusicUpdate", "onMusicUpdate");
				entry.getValue().onMusicUpdate(currentMusicInfo);
			}
		}
	}
	
	public void setPlaybackListener(String id, PlaybackListener pl){
		playbackListeners.put(id, pl);
	}
	public void removePlaybackListener(String id){
		playbackListeners.remove(id);
	}
	private void updateNotification(){
		PendingIntent mPi = PendingIntent.getActivity(getApplicationContext(), 0,
				new Intent(getApplicationContext(), HomeScreen.class), 
				PendingIntent.FLAG_UPDATE_CURRENT);
		mBuilder = new NotificationCompat.Builder(this)
		.setContentTitle(currentMusicInfo.title)
		.setContentText(currentMusicInfo.album + " - " + currentMusicInfo.artist)
		.setContentIntent(mPi);
		
		if(currentMusicInfo.album_art != null || currentMusicInfo.getAlbumArt(this) != null){
			mBuilder.setLargeIcon(currentMusicInfo.album_art);
		}
		mBuilder.setSmallIcon(R.drawable.ic_launcher);
		
		if(notificationShown){
			NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
			nm.notify(PLAYBACK_SERVICE_NOTIFICATION_ID, mBuilder.build());
		}
		
	}
	public void showNotification(){
		startForeground(PLAYBACK_SERVICE_NOTIFICATION_ID, mBuilder.build());
		notificationShown = true;
	}
	public void hideNotification(){
		if(notificationShown){
			notificationShown = false;
			stopForeground(true);
		}
	}
	public void stopAndExit(){
		if(isPlaying()){
			mMediaPlayer.reset();
		}
		// TODO 保存当前播放的状态
		mMediaPlayer.release();
		stopSelf();
	}
	public MusicInfo getCurrentMusic(){
		return currentMusicInfo;
	}
	public void pauseMusic(){
		mMediaPlayer.pause();
	}
	public void setPlayMode(int val){
		int temp = playMode;
		playMode = val;
		if(currentMusicInfo == null || temp != val){
			if(playMode == PLAY_MODE_RANDOM){
				currentMusicInfo = getMusicFromCursor(randomArr[randomIndex]);
			} else if(playMode == PLAY_MODE_NORMAL){
				currentMusicInfo = getMusicFromCursor(normalIndex);
			}
		}
		if(temp != val){
			musicPrepared = false;
			if(isPlaying() && val != PLAY_MODE_SINGLE_REPEAT){
				playMusic();
			}
		}
		handlePlaybackListener("musicUpdate");
	}
	public int getListPosition(){
		if(playMode == PLAY_MODE_NORMAL){
			return normalIndex;
		} else if(playMode == PLAY_MODE_RANDOM){
			return randomIndex;
		}
		return -1;
	}
	public int setListPosition(int pos){
		if(pos < 0){
			pos = 0;
		}
		if(playMode == PLAY_MODE_NORMAL){
			normalIndex = pos;
		} else if(playMode == PLAY_MODE_RANDOM){
			randomIndex = pos;
		}
		return -1;
	}
	public int getCount(){
		return mMediaCursor.getCount();
	}
	public MusicInfo getMusicInList(int list_id, int position){
		if(list_id == LIST_ID_RANDOM){
			return getMusicFromCursor(randomArr[position]);
		} else if(list_id == LIST_ID_NORMAL){
			return getMusicFromCursor(position);
		}
		return null;
	}
	public int getCurrentPlayMode(){
		return playMode;
	}
	
}
