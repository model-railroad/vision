/*
 * Project: Train-Motion
 * Copyright (C) 2025 alf.labs gmail com,
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

import com.alflabs.trainmotion.cam.CamInfo;
import com.alflabs.trainmotion.cam.Cameras;
import com.alflabs.trainmotion.util.FpsMeasurerFactory;
import com.alflabs.trainmotion.util.ILogger;
import com.alflabs.utils.IClock;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.max;

public class PlayersView extends JComponent {
    private static final String TAG = PlayersView.class.getSimpleName();

    private final ILogger mLogger;
    private final KioskController.Callbacks mCallbacks;
    private final List<VlcMediaComponent> mCameraPlayers = new ArrayList<>();
    private EmbeddedMediaPlayerComponent mMainPlayer;
    private boolean mPlayerZoomed;

    public PlayersView(
            ILogger logger,
            KioskController.Callbacks callbacks) {
        mLogger = logger;
        mCallbacks = callbacks;

        mMainPlayer = new EmbeddedMediaPlayerComponent();
        mMainPlayer.setBackground(KioskView.BG_COLOR);
        setupLogo();
        add(mMainPlayer);

        mMainPlayer.mediaPlayer().events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void finished(MediaPlayer mediaPlayer) {
                super.finished(mediaPlayer);
                mCallbacks.onMainPlayerFinished();
            }

            @Override
            public void error(MediaPlayer mediaPlayer) {
                super.error(mediaPlayer);
                mCallbacks.onMainPlayerError();
            }
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                super.componentResized(event);
                onComponentResized();
            }
        });
    }

    private void setupLogo() {
        try {
            URL resource = Resources.getResource("logo_youtube_75pct.png");
            mLogger.log(TAG, "Logo read: " + resource.getPath());
            File tmpFile = File.createTempFile("logo_youtube_", ".png");
            mLogger.log(TAG, "Logo write: " + tmpFile);
            Files.write(Resources.toByteArray(resource), tmpFile);
            tmpFile.deleteOnExit();

            mMainPlayer.mediaPlayer().logo().setFile(tmpFile.getPath());
            mMainPlayer.mediaPlayer().logo().setLocation(0, Integer.MAX_VALUE);
            mMainPlayer.mediaPlayer().logo().setOpacity(0.75f);
            mMainPlayer.mediaPlayer().logo().enable(true);
        } catch (Throwable t) {
            mLogger.log(TAG, "Error setting logo: " + t.getMessage());
        }
    }

    public int getMediaPlayerWidth() {
        return mMainPlayer.getWidth();
    }

    public int getMediaPlayerHeight() {
        return mMainPlayer.getHeight();
    }

    public void createVideoCanvases(
            IClock clock,
            ConsoleTask consoleTask,
            FpsMeasurerFactory fpsMeasurerFactory,
            HighlighterFactory highlighterFactory, Cameras cameras) {
        AtomicInteger posIndex = new AtomicInteger();
        synchronized (mCameraPlayers) {
            cameras.forEachCamera(camInfo -> {
                VlcMediaComponent canvas = new VlcMediaComponent(
                        clock,
                        mCallbacks,
                        consoleTask,
                        posIndex.incrementAndGet(),
                        camInfo,
                        fpsMeasurerFactory.create(),
                        highlighterFactory.create(
                                camInfo.getIndex(),
                                camInfo.getAnalyzer()));

                mCameraPlayers.add(canvas);
                add(canvas);
                canvas.initialize();
            });
        }
    }

    public boolean updateAllHighlights() {
        boolean hasHighlight = false;
        synchronized (mCameraPlayers) {
            for (VlcMediaComponent canvas : mCameraPlayers) {
                canvas.updateFrame();
                hasHighlight |= canvas.getHighlighter().isHighlighted();
            }
        }
        return hasHighlight;
    }

    public void releaseSync() {
        if (mMainPlayer != null) {
            mMainPlayer.mediaPlayer().controls().stop();
            mMainPlayer.release();
            mMainPlayer = null;
        }
        synchronized (mCameraPlayers) {
            for (VlcMediaComponent canvas : mCameraPlayers) {
                canvas.release();
            }
            mCameraPlayers.clear();
        }
    }

    public void setMainPlayerMute(boolean isMuted) {
        if (mMainPlayer != null) {
            mMainPlayer.mediaPlayer().audio().setMute(isMuted);
        }
    }

    public void setMainPlayerVolume(int percent) {
        if (mMainPlayer != null) {
            mMainPlayer.mediaPlayer().audio().setVolume(percent);
        }
    }

    public int getMainPlayerVolume() {
        return mMainPlayer == null ? -1 : mMainPlayer.mediaPlayer().audio().volume();
    }

    public void startMainPlayer(File media) {
        if (mMainPlayer != null) {
            mMainPlayer.setVisible(true);
            mMainPlayer.mediaPlayer().media().play(media.getAbsolutePath());
        }
    }

    public void stopMainPlayer() {
        if (mMainPlayer != null) {
            mMainPlayer.mediaPlayer().controls().stop();
            mMainPlayer.setVisible(false);
        }
    }

    public void startCameraPlayer(CamInfo camInfo, String media) {
        synchronized (mCameraPlayers) {
            for (VlcMediaComponent canvas : mCameraPlayers) {
                canvas.setVisible(true);
                if (canvas.getCamInfo() == camInfo) {
                    canvas.getPlayer().mediaPlayer().media().play(media);
                }
            }
        }
    }

    public void stopCameraPlayer(CamInfo camInfo) {
        synchronized (mCameraPlayers) {
            for (VlcMediaComponent canvas : mCameraPlayers) {
                if (canvas.getCamInfo() == camInfo) {
                    canvas.getPlayer().mediaPlayer().controls().stop();
                }
                canvas.setVisible(false);
            }
        }
    }

    public void setPlayerZoomed(boolean playerZoomed) {
        if (mPlayerZoomed != playerZoomed) {
            mPlayerZoomed = playerZoomed;
            System.out.println("@@ PV mPlayerZoomed = " + mPlayerZoomed);
            onComponentResized();
        }
    }

    private void onComponentResized() {
        int w = getWidth();
        int h = getHeight();
        System.out.println("@@ PV onComponentResized = " + w + "x" + h);

        setMediaPlayerSize(w, h);
        resizeVideoCanvases(w, h);
    }

    private void setMediaPlayerSize(int width, int height) {
        int split = mPlayerZoomed ? 1 : 2;
        mMainPlayer.setBounds(0, 0, width / split, height / split);
        mMainPlayer.revalidate();
        System.out.println("@@ PV mMainPlayer [" + width + "x" + height + "] ==> bounds = " + mMainPlayer.getBounds());
    }

    private void resizeVideoCanvases(int width, int height) {
        synchronized (mCameraPlayers) {
            for (VlcMediaComponent canvas : mCameraPlayers) {
                canvas.computeAbsolutePosition(width, height);
                System.out.println("@@ PV canvas [" + width + "x" + height + "] bounds = " + canvas.getBounds());
            }
        }
    }

    private int cachedPrefSizeW = 0;
    private int cachedPrefSizeH = 0;
    private Dimension cachedPrefSizeSz = null;

    @Override
    public Dimension getPreferredSize() {
        // Preferred Size is 16/9 of the current frame
        int prefW = max(getWidth(), 16);
        int prefH = max(getHeight(), 9);

        // This gets called repeatedly... use a cache to avoid recomputing all the time.
        if (prefW == cachedPrefSizeW && prefH == cachedPrefSizeH && cachedPrefSizeSz != null) {
            return cachedPrefSizeSz;
        }
        cachedPrefSizeW = prefW;
        cachedPrefSizeH = prefH;

        double w = prefW;
        double h = prefH;

        double desiredRatio = 16/9.;
        double currentRatio = w / h;

        if (currentRatio >= desiredRatio) {
            // Wider. Keep H, compute W.
            prefW = (int)(h * desiredRatio);
        } else {
            // Taller. Keep W, compute H.
            prefH = (int)(w / desiredRatio);
        }

        Dimension sz = new Dimension(prefW, prefH);
        cachedPrefSizeSz = sz;
        System.out.println("@@ PV getPreferredSize sz = " + sz);
        return sz;
    }
}
