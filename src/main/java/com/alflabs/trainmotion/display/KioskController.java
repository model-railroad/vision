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
import com.alflabs.trainmotion.cam.CamInfo;
import com.alflabs.trainmotion.cam.Cameras;
import com.alflabs.trainmotion.util.Analytics;
import com.alflabs.trainmotion.util.ILogger;
import com.alflabs.trainmotion.util.IStartStop;
import com.alflabs.utils.IClock;
import com.google.common.base.Preconditions;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Kiosk Display is split in 2 parts: a KioskView class encapsulates all the Swing-related APIs,
 * and this controller contains all the "business" logic. This makes it possible to test the
 * controller using a mock UI that does not use any actual views.
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
    private final Playlist mMainPlaylist;
    private final ConfigIni mConfigIni;
    private final Analytics mAnalytics;
    private final ConsoleTask mConsoleTask;
    private final KioskView mView;
    private final Map<CamInfo, CameraPlaylist> mCameraPlaylist = new HashMap<>();

    /** Force zoom: 0=default, 1=main always zoomed, 2=main never zoomed. */
    private int mForceZoom;
    private boolean mPlayerMuted;
    private boolean mToggleMask;
    private boolean mDisplayOn = true;
    private int mPlayerDefaultVolume = PLAYER_VOLUME_DEFAULT;
    private long mPlayerZoomEndTS;

    public interface Callbacks {
        void onWindowClosing();
        void onFrameResized();
        boolean onProcessKey(char keyChar);
        void onRepaintTimerTick();
        void onMainPlayerFinished();
        void onMainPlayerError();
        void onCameraPlayerFinished(@Nonnull CamInfo camInfo);
        void onCameraPlayerError(@Nonnull CamInfo camInfo);
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
            KioskView kioskView) {
        mClock = clock;
        mLogger = logger;
        mCameras = cameras;
        mMainPlaylist = playlist;
        mConfigIni = configIni;
        mAnalytics = analytics;
        mConsoleTask = consoleTask;
        mView = kioskView;
    }

    @Override
    public void start() throws Exception {
        mView.create(
                800, 600,
                64, 64,
                DISPLAY_FPS,
                mConfigIni.getWindowTitle("Train Motion"),
                mConfigIni.getWindowMaximize(),
                mCallbacks
        );
    }

    public void initialize() {
        mDisplayOn = true;
        // Start shuffled
        mMainPlaylist.setShuffle(true);
        // Get desired volume
        mPlayerDefaultVolume = mConfigIni.getVolumePct(PLAYER_VOLUME_DEFAULT);

        mView.startTimer();
        mView.setMainPlayerMute(false);
        playNextMain();
        mCameras.forEachCamera(this::playNextCamera);
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
            return mConsoleTask.processKey(keyChar);
        }

        @Override
        public void onRepaintTimerTick() {
            if (mConsoleTask.isQuitRequested()) {
                return;
            }

            mView.setBottomLabelText(mConsoleTask.computeLineInfo());

            boolean hasHighlight = mView.updateAllHighlights();

            // frame (window) size
            mView.computeLayout();
            final int fw = mView.getContentWidth();
            final int fh = mView.getContentHeight();
            // target size for media player
            int tw = fw, th = fh;
            if (mForceZoom == 2 || (hasHighlight && mForceZoom == 0)) {
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
        public void onMainPlayerFinished() {
            mLogger.log(TAG, "Media Finished for main player");
            playNextMain();
        }

        @Override
        public void onMainPlayerError() {
            mLogger.log(TAG, "Media Error for main player");
            playNextMain();
        }

        @Override
        public void onCameraPlayerFinished(@Nonnull CamInfo camInfo) {
            mLogger.log(TAG, "Media Finished for Cam " + camInfo.getIndex());
            playNextCamera(camInfo);
        }

        @Override
        public void onCameraPlayerError(@Nonnull CamInfo camInfo) {
            mLogger.log(TAG, "Media Error for Cam " + camInfo.getIndex());
            playNextCamera(camInfo);
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


    /** Invoked async from DisplayController's thread. */
    public void onDisplayOnChanged(boolean displayOn) {
        mDisplayOn = displayOn;
        mView.invokeLater(() -> {
            if (mConsoleTask.isQuitRequested()) {
                return;
            }
            mLogger.log(TAG, "Display on state changed to " + mDisplayOn);
            if (mDisplayOn) {
                playNextMain();
                mCameras.forEachCamera(KioskController.this::playNextCamera);
            } else {
                mView.stopMainPlayer();
                mCameras.forEachCamera(KioskController.this::stopCamera);
            }
        });
    }

    public boolean processKey(char c) {
        // Keys handled by the ConsoleTask: esc, q=quit // ?, h=help, o=display off.
        // Keys handled by KioskController: f=fullscreen, s=sound, u=shuffle, n=next, m=mask.
        // mLogger.log(TAG, "Process key: " + c); // DEBUG
        switch (c) {
        case 'f':
            // Toggle Fullscreen zoom
            mPlayerZoomEndTS = 0;
            mForceZoom = (mForceZoom + 1) % 3;
            return true;
        case 's':
            // Toggle mute Sound
            // Note mMediaPlayer.mediaPlayer().audio().setMute(!muted) seems to work in reverse
            // (and/or differently per platform) so let's avoid it. Just control volume.
            mPlayerMuted = !mPlayerMuted;
            mView.setMainPlayerVolume(mPlayerMuted ? 0 : mPlayerDefaultVolume);
            mLogger.log(TAG, "Audio: volume " + mView.getMainPlayerVolume() + "%");
            return true;
        case 'u':
            // Toggle shUffle
            mMainPlaylist.setShuffle(!mMainPlaylist.isShuffle());
            return true;
        case 'n':
            playNextMain();
            return true;
        case 'm':
            // Toggle Mask
            mToggleMask = !mToggleMask;
            mLogger.log(TAG, "Mask toggled " + (mToggleMask ? "on" : "off"));
            return true;
        }

        return false; // not consumed
    }

    public void playNextMain() {
        mView.invokeLater(() -> {
            if (!mDisplayOn) {
                mLogger.log(TAG, "Play next request ignore: display off");
                return;
            }

            if (mConsoleTask.isQuitRequested()) {
                return;
            }

            Optional<File> next = mMainPlaylist.getNext();
            if (next.isPresent()) {
                File file = next.get();
                mLogger.log(TAG, "MAIN Player file = " + file.getAbsolutePath());
                mAnalytics.sendEvent("PlayVideo", file.getName());
                mConsoleTask.updateLineInfo(/* F */ "9v", " | " + file.getName().replace(".mp4", ""));

                int volume = mPlayerDefaultVolume;
                Optional<Playlist.FileProperties> props = mMainPlaylist.getProperties(file);
                if (props.isPresent()) {
                    int v = props.get().getVolume();
                    if (v >= 0) {
                        volume = Math.min(v, PLAYER_VOLUME_MAX);
                    }
                }

                mView.setMainPlayerVolume(mPlayerMuted ? 0 : volume);
                mView.startMainPlayer(file);
            }
        });
    }

    public void playNextCamera(@Nonnull CamInfo camInfo) {
        mView.invokeLater(() -> {
            if (mConsoleTask.isQuitRequested()) {
                return;
            }

            CameraPlaylist playlist = mCameraPlaylist.computeIfAbsent(camInfo, this::createCameraPlaylist);
            Optional<String> next = playlist.getNext();
            next.ifPresent(media -> {
                mLogger.log(TAG, "Start Camera " + camInfo.getIndex() + " Player media = " + media);
                mView.startCameraPlayer(camInfo, media);
            });
        });
    }

    public void stopCamera(@Nonnull CamInfo camInfo) {
        mView.invokeLater(() -> {
            if (mConsoleTask.isQuitRequested()) {
                return;
            }

            mLogger.log(TAG, "Stop Camera " + camInfo.getIndex());
            mView.stopCameraPlayer(camInfo);
        });
    }

    @Nonnull
    private CameraPlaylist createCameraPlaylist(@Nonnull CamInfo camInfo) {
        return new CameraPlaylist(camInfo);
    }

    private class CameraPlaylist {
        private final List<String> mMedias = new ArrayList<>();
        private int mIndex = -1;

        public CameraPlaylist(@Nonnull CamInfo camInfo) {
            int index = camInfo.getIndex();
            Optional<String> camUrl = mConfigIni.getCamUrlN(index);
            if (camUrl.isPresent()) {
                for (String uri : camUrl.get().split(",")) {
                    if (uri.contains("://")) {
                        mMedias.add(uri);
                    } else {
                        String path = uri.replace('/', File.separatorChar);
                        Preconditions.checkState(
                                new File(path).exists(),
                                "Invalid file for cam" + index + "_url in config.ini: " + path);
                        mMedias.add(path);
                    }
                }
            } else if (index == 1) {
                throw new IllegalArgumentException("Error: Missing cam" + index + "_url in config.ini");
            } else {
                mLogger.log(TAG, "Warning: Missing cam" + index + "_url in config.ini");
            }
        }

        @Nonnull
        public Optional<String> getNext() {
            if (!mMedias.isEmpty()) {
                mIndex = (mIndex + 1) % mMedias.size();
                return Optional.of(mMedias.get(mIndex));
            }
            return Optional.empty();
        }
    }

}
