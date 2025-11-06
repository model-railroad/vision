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

import javax.swing.JPanel;
import java.awt.GridLayout;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayersView extends JPanel {
    private static final String TAG = PlayersView.class.getSimpleName();

    private final ILogger mLogger;
    private final KioskController.Callbacks mCallbacks;
    private final List<VlcMediaComponent> mCameraPlayers = new ArrayList<>();
    private EmbeddedMediaPlayerComponent mMainPlayer;
    private boolean mPlayerZoomed;

    public PlayersView(
            ILogger logger,
            KioskController.Callbacks callbacks) {
        super(new GridLayout(2, 2), /*isDoubleBuffered=*/ false);
        setBackground(KioskView.BG_COLOR);
        mLogger = logger;
        mCallbacks = callbacks;

        mMainPlayer = new EmbeddedMediaPlayerComponent();
        mMainPlayer.setBackground(KioskView.BG_COLOR);
// TBD or REMOVE        mMainPlayer.setBounds(0, 0, width, height); // matches initial frame
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

    public void setMediaPlayerSize(int width, int height) {
// TBD or REMOVE        mMainPlayer.setBounds(0, 0, width, height);
// TBD or REMOVE        mMainPlayer.revalidate();
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

    public void resizeVideoCanvases(int width, int height) {
        synchronized (mCameraPlayers) {
            for (VlcMediaComponent canvas : mCameraPlayers) {
// TBD or REMOVE                canvas.computeAbsolutePosition(width, height);
            }
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
            synchronized (mCameraPlayers) {
                for (VlcMediaComponent canvas : mCameraPlayers) {
                    canvas.setVisible(!playerZoomed);
                }
            }
        }
    }
}
