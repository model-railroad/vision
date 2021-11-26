/*
 * Project: Train-Motion
 * Copyright (C) 2021 alf.labs gmail com,
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.alflabs.trainmotion.display;

import com.alflabs.trainmotion.ConfigIni;
import com.alflabs.trainmotion.Playlist;
import com.alflabs.trainmotion.cam.Cameras;
import com.alflabs.trainmotion.util.Analytics;
import com.alflabs.trainmotion.util.ILogger;
import com.alflabs.trainmotion.util.IStartStop;
import com.alflabs.utils.IClock;
import com.google.common.base.Preconditions;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Kiosk Display is split in 2 parts: a KioskView class encapsulates all the Swing-related APIs,
 * and this controller contains all the "business" logic. This makes it possible to test the
 * controller using a mock UI that does not uses any actual views.
 */
@Singleton
public class KioskController implements IStartStop {
    private static final String TAG = KioskController.class.getSimpleName();

    // Approximage FPS to update the camera videos.
    private static final int DISPLAY_FPS = 15;

    // Player zoom minimum display duration
    private static final long PLAYER_ZOOM_MIN_DURATION_MS = 5*1000;
    // Player default volume percentage
    private static final int PLAYER_VOLUME_DEFAULT = 50;
    // Player max volume percentage
    private static final int PLAYER_VOLUME_MAX = 75;

    private final IClock mClock;
    private final ILogger mLogger;
    private final Cameras mCameras;
    private final Playlist mPlaylist;
    private final ConfigIni mConfigIni;
    private final Analytics mAnalytics;
    private final ConsoleTask mConsoleTask;
    private final HighlighterFactory mHighlighterFactory;
    private final KioskView mView;

    private boolean mForceZoom;
    private boolean mPlayerMuted;
    private boolean mToggleMask;
    private int mPlayerDefaultVolume = PLAYER_VOLUME_DEFAULT;
    private long mPlayerZoomEndTS;

    public interface Callbacks {
        void onWindowClosing();
        void onFrameResized();
        boolean onProcessKey(char keyChar);
        void onRepaintTimerTick();
        void onMediaPlayerFinished();
        void onMediaPlayerError();
        boolean showMask();
        long elapsedRealtime();
    }

    @Inject
    public KioskController(
            IClock clock,
            ILogger logger,
            Cameras cameras,
            Playlist playlist,
            ConfigIni configIni,
            Analytics analytics,
            ConsoleTask consoleTask,
            KioskView kioskView,
            HighlighterFactory highlighterFactory) {
        mClock = clock;
        mLogger = logger;
        mCameras = cameras;
        mPlaylist = playlist;
        mConfigIni = configIni;
        mAnalytics = analytics;
        mConsoleTask = consoleTask;
        mView = kioskView;
        mHighlighterFactory = highlighterFactory;
    }

    @Override
    public void start() throws Exception {
        mView.create(
                800, 600,
                64, 64,
                DISPLAY_FPS,
                mCameras,
                mHighlighterFactory,
                mConfigIni.getWindowTitle("Train Motion"),
                mConfigIni.getWindowMaximize(),
                mCallbacks
        );
    }

    public void initialize() throws Exception {
        // Start shuffled
        mPlaylist.setShuffle(true);
        // Get desired volume
        mPlayerDefaultVolume = mConfigIni.getVolumePct(PLAYER_VOLUME_DEFAULT);

        mView.startTimer();
        mView.setMediaPlayerMute(false);
        playNext();
        initPlayCanvasesHack();
    }

    @Override
    public void stop() throws Exception {
        mLogger.log(TAG, "Stop");
        mView.release();
    }

    private final Callbacks mCallbacks = new Callbacks() {
        @Override
        public void onWindowClosing() {
            mConsoleTask.requestQuit();
        }

        @Override
        public void onFrameResized() {
            mView.computeLayout();

            final int width = mView.getContentWidth();
            final int height = mView.getContentHeight();
            mLogger.log(TAG, String.format("onFrameResized --> %dx%d", width, height));

            mView.resizeVideoCanvases(width, height);
        }

        @Override
        public boolean onProcessKey(char keyChar) {
            return processKey(keyChar) || mConsoleTask.processKey(keyChar);
        }

        @Override
        public void onRepaintTimerTick() {
            if (mConsoleTask.isQuitRequested()) {
                return;
            }

            mView.setBottomLabelText(mConsoleTask.computeLineInfo());

            boolean hasHighlight = mView.getVideoCanvasesHighlight();

            // frame (window) size
            mView.computeLayout();
            final int fw = mView.getContentWidth();
            final int fh = mView.getContentHeight();
            // target size for media player
            int tw = fw, th = fh;
            if (true) { // -- DEBUG DEFORCE SPLIT -- hasHighlight && !mForceZoom) {
                // Desired player is half size screen
                tw = fw / 2;
                th = fh / 2;
            }
            // current player size -- only update if not matching the target.
            int pw = mView.getMediaPlayerWidth();
            int ph = mView.getMediaPlayerHeight();
            if (tw != pw || th != ph) {
                if (mPlayerZoomEndTS < mClock.elapsedRealtime()) { // don't change too fast
                    mView.setMediaPlayerSize(tw, th);
                    mPlayerZoomEndTS = mClock.elapsedRealtime() + PLAYER_ZOOM_MIN_DURATION_MS;
                }
            }
        }

        @Override
        public void onMediaPlayerFinished() {
            mLogger.log(TAG, "Media Finished");
            playNext();
        }

        @Override
        public void onMediaPlayerError() {
            mLogger.log(TAG, "Media Error");
            playNext();
        }

        @Override
        public boolean showMask() {
            return mToggleMask;
        }

        @Override
        public long elapsedRealtime() {
            return mClock.elapsedRealtime();
        }
    };

    public boolean processKey(char c) {
        // Keys handled by the ConsoleTask:
        // esc, q = quit // ?, h = help.
        // mLogger.log(TAG, "Process key: " + c); // DEBUG
        switch (c) {
        case 'f':
            // Toggle fullscreen zoom
            mPlayerZoomEndTS = 0;
            mForceZoom = !mForceZoom;
            return true;
        case 'm':
            // Toggle mute sound
            // Note mMediaPlayer.mediaPlayer().audio().setMute(!muted) seems to work in reverse
            // (and/or differently per platform) so let's avoid it. Just control volume.
            mPlayerMuted = !mPlayerMuted;
            mView.setMediaPlayerVolume(mPlayerMuted ? 0 : mPlayerDefaultVolume);
            mLogger.log(TAG, "Audio: volume " + mView.getMediaPlayerVolume() + "%");
            return true;
        case 's':
            // Toggle shuffle
            mPlaylist.setShuffle(!mPlaylist.isShuffle());
            return true;
        case 'n':
            playNext();
            return true;
        case 'k':
            mToggleMask = !mToggleMask;
            mLogger.log(TAG, "Mask toggled " + (mToggleMask ? "on" : "off"));
            return true;
        }

        return false; // not consumed
    }

    public void playNext() {
        mView.invokeLater(() -> {
            if (mConsoleTask.isQuitRequested()) {
                return;
            }

            Optional<File> next = mPlaylist.getNext();
            if (next.isPresent()) {
                File file = next.get();
                mLogger.log(TAG, "Player file = " + file.getAbsolutePath());
                mAnalytics.sendEvent("PlayVideo", file.getName());
                mConsoleTask.updateLineInfo("9f", " | " + file.getName().replace(".mp4", ""));

                int volume = mPlayerDefaultVolume;
                Optional<Playlist.FileProperties> props = mPlaylist.getProperties(file);
                if (props.isPresent()) {
                    int v = props.get().getVolume();
                    if (v >= 0) {
                        volume = Math.min(v, PLAYER_VOLUME_MAX);
                    }
                }

                mView.setMediaPlayerVolume(mPlayerMuted ? 0 : volume);
                mView.playMediaPlayer(file);
            }
        });
    }

    private void initPlayCanvasesHack() {
        final String[] filenames = new String[] { "cam_4.mp4", "cam_5.mp4", "cam_6.mp4", "cam_7.mp4"  };
        File dir = new File("src/test/resources/cam_records".replace('/', File.separatorChar));

        List<String> files = new ArrayList<>();
        files.add("rtsp://username:password@192.168.3.117:554/ipcam_h264.sdp");
        for (String filename : filenames) {
            File f = new File(dir, filename);
            Preconditions.checkState(f.exists(), f.getPath());
            files.add(f.getAbsolutePath());
        }

        mView.invokeLater(() -> {
            if (mConsoleTask.isQuitRequested()) {
                return;
            }

            mView.initPlayCanvasesHack(files);
        });
    }


}
