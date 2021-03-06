package com.hochan.dragtofloatvideoview.video.player;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.text.TextUtils;
import android.view.Surface;
import android.widget.Toast;

import com.hochan.dragtofloatvideoview.video.videolayout.VideoPlayLayout;

import java.lang.ref.WeakReference;

/**
 * .
 * Created by hochan on 2018/1/25.
 */

public class NativeVideoPlayer extends VideoPlayer implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener,
		MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnErrorListener,
		MediaPlayer.OnInfoListener, MediaPlayer.OnVideoSizeChangedListener {

	private MediaPlayer mMediaPlayer;

	protected static NativeVideoPlayer getInstance() {
		return Holder.INSTANCE;
	}

	private static final class Holder {
		private static final NativeVideoPlayer INSTANCE = new NativeVideoPlayer();
	}

	/**
	 * @return 是否正在播放
	 */
	public boolean isPlaying() {
		return mMediaPlayer != null && mMediaPlayer.isPlaying();
	}

	@Override
	protected void playUrl(final VideoPlayLayout videoPlayLayout) {
		try {
			if (mMediaPlayer.isPlaying()) {
				mMediaPlayer.stop();
			}
			mMediaPlayer.reset();
			mMediaPlayer.setDataSource(videoPlayLayout.mVideoUrl);
			mMediaPlayer.prepareAsync();
			mMediaPlayer.setOnCompletionListener(null);
			mPlayLayoutWeakReference = new WeakReference<>(videoPlayLayout);
			mPlayingUrl = videoPlayLayout.mVideoUrl;
			videoPlayLayout.attachToVideoPlayer();
			mCurrentState = STATE_PREPARING;
			notifyPlayStateChange();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		mCurrentState = STATE_PREPARED;
		start();
	}

	@Override
	protected void createMediaPlayerIfNecessary(Context context) {
		if (mMediaPlayer == null) {
			mMediaPlayer = new MediaPlayer();
			mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mMediaPlayer.setScreenOnWhilePlaying(true);
			mMediaPlayer.setOnPreparedListener(this);
			mMediaPlayer.setOnBufferingUpdateListener(this);
			mMediaPlayer.setOnSeekCompleteListener(this);
			mMediaPlayer.setOnErrorListener(this);
			mMediaPlayer.setOnInfoListener(this);
			mMediaPlayer.setOnVideoSizeChangedListener(this);
			mCurrentState = STATE_IDLE;
		}
	}

	@Override
	public void start() {
		if (mMediaPlayer != null) {
			mMediaPlayer.start();
			if (mCurrentState == STATE_BUFFERING_PAUSED) {
				mCurrentState = STATE_BUFFERING_PLAYING;
			} else if (mCurrentState == STATE_PAUSED) {
				mCurrentState = STATE_PLAYING;
			} else {
				mCurrentState = STATE_PLAYING;
			}
			Integer lastPosition = mVideoPositionLRUCache.get(mPlayingUrl);
			if (lastPosition != null) {
				mMediaPlayer.seekTo(mVideoPositionLRUCache.get(mPlayingUrl));
			}
			mMediaPlayer.setOnCompletionListener(this);
			notifyPlayStateChange();
		}
	}

	@Override
	public void pause() {
		if (mMediaPlayer == null) {
			return;
		}
		mMediaPlayer.pause();
		mCurrentPosition = getCurrentPosition();
		if (!TextUtils.isEmpty(mPlayingUrl)) {
			mVideoPositionLRUCache.put(mPlayingUrl, (int) mCurrentPosition);
		}
		if (mCurrentState == STATE_PLAYING) {
			mCurrentState = STATE_PAUSED;
		} else if (mCurrentState == STATE_BUFFERING_PLAYING) {
			mCurrentState = STATE_BUFFERING_PAUSED;
		} else {
			mCurrentState = STATE_PAUSED;
		}
		notifyPlayStateChange();
	}

	@Override
	public void seekTo(int position) {
		if (mMediaPlayer != null) {
			mMediaPlayer.seekTo(position);
		}
	}

	protected void setSurfaceToPlayer(Surface surface) {
		mMediaPlayer.setSurface(surface);
	}

	@Override
	public int getDuration() {
		return mMediaPlayer != null && mCurrentState >= STATE_PREPARED ? mMediaPlayer.getDuration() : 0;
	}

	@Override
	public int getCurrentPosition() {
		return mMediaPlayer != null && mCurrentState >= STATE_PREPARED ? mMediaPlayer.getCurrentPosition() : 0;
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		mCurrentState = STATE_COMPLETED;
		notifyPlayStateChange();
		mVideoPositionLRUCache.remove(mPlayingUrl);
		mMediaPlayer.setOnCompletionListener(null);
	}

	@Override
	public void onBufferingUpdate(MediaPlayer mp, int percent) {

	}

	@Override
	public void onSeekComplete(MediaPlayer mp) {
		mCurrentPosition = getCurrentPosition();
		if (!mMediaPlayer.isPlaying()) {
			mMediaPlayer.start();
		}
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		if (mPlayLayoutWeakReference.get() != null) {
			Toast.makeText(mPlayLayoutWeakReference.get().getContext(), "" + what + " " + extra, Toast.LENGTH_SHORT).show();
		}
		mCurrentState = STATE_ERROR;
		notifyPlayStateChange();
		mMediaPlayer.setOnCompletionListener(null);
		return false;
	}

	@Override
	public int getCurrentState() {
		return mMediaPlayer != null ? mCurrentState : STATE_NONE;
	}

	@Override
	public boolean onInfo(MediaPlayer mp, int what, int extra) {
		if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
			// 播放器渲染第一帧
			mCurrentState = STATE_PLAYING;
			mMediaPlayer.setOnCompletionListener(this);
		} else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
			// MediaPlayer暂时不播放，以缓冲更多的数据
			if (mCurrentState == STATE_PAUSED || mCurrentState == STATE_BUFFERING_PAUSED) {
				mCurrentState = STATE_BUFFERING_PAUSED;
			} else {
				mCurrentState = STATE_BUFFERING_PLAYING;
			}
		} else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
			// 填充缓冲区后，MediaPlayer恢复播放/暂停
			if (mCurrentState == STATE_BUFFERING_PLAYING) {
				mCurrentState = STATE_PLAYING;
			}
			if (mCurrentState == STATE_BUFFERING_PAUSED) {
				mCurrentState = STATE_PAUSED;
			}
		}
		notifyPlayStateChange();
		return true;
	}

	@Override
	public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
		if (mPlayLayoutWeakReference != null && mPlayLayoutWeakReference.get() != null) {
			mPlayLayoutWeakReference.get().onVideoSizeChange(width, height);
		}
	}


}
