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

import com.alflabs.kv.IKeyValue;
import com.alflabs.manifest.Constants;
import com.alflabs.rx.IStream;
import com.alflabs.rx.ISubscriber;
import com.alflabs.trainmotion.cam.CamInfo;
import com.alflabs.trainmotion.cam.Cameras;
import com.alflabs.trainmotion.util.FpsMeasurerFactory;
import com.alflabs.trainmotion.util.ILogger;
import com.alflabs.trainmotion.util.KVController;
import com.alflabs.trainmotion.util.SwingUISchedulers;
import com.alflabs.utils.IClock;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Map;

import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;

/**
 * Kiosk Display is split in 2 parts: a KioskView class encapsulates all the Swing-related APIs,
 * and this controller contains all the "business" logic. This makes it possible to test the
 * controller using a mock UI that does not use any actual views.
 */
@Singleton
public class KioskView {
    private static final String TAG = KioskView.class.getSimpleName();

    static final Color BG_COLOR = Color.BLACK;

    private final ILogger mLogger;
    private final IClock mClock;

    private final Cameras mCameras;
    private final ConsoleTask mConsoleTask;
    private final KVController mKVController;
    private final HighlighterFactory mHighlighterFactory;
    private final FpsMeasurerFactory mFpsMeasurerFactory;

    private KioskController.Callbacks mCallbacks;
    private JFrame mFrame;
    private PlayersView mPlayersView;
    private StatusView mBottomStatus;
    private RtacPsaPanel mRtacPsaPanel;
    private RtacDataPanel mRtacDataPanel;
    private Timer mRepaintTimer;

    private final ISubscriber<String> mKeyChangedSubscriber = this::onReceiveKeyChanged;
    private final ISubscriber<Boolean> mConnectedSubscriber = this::onReceiveConnected;

    @Inject
    public KioskView(
            ILogger logger,
            IClock clock,
            Cameras cameras,
            ConsoleTask consoleTask,
            KVController kvController,
            HighlighterFactory highlighterFactory,
            FpsMeasurerFactory fpsMeasurerFactory) {
        mLogger = logger;
        mClock = clock;
        mCameras = cameras;
        mConsoleTask = consoleTask;
        mKVController = kvController;
        mHighlighterFactory = highlighterFactory;
        mFpsMeasurerFactory = fpsMeasurerFactory;
    }

    public void invokeLater(Runnable r) {
        SwingUtilities.invokeLater(r);
    }

    private GridBagConstraints constraint(
            int gridx, int gridy,
            int gridw, int gridh,
            int weightx, int weighty,
            int fill,
            int anchor) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = gridx;
        c.gridy = gridy;
        c.gridwidth = gridw;
        c.gridheight = gridh;
        c.weightx = weightx;
        c.weighty = weighty;
        c.fill = fill;
        c.anchor = anchor;
        return c;
    }

    private GridBagConstraints constraint(
            int gridx, int gridy,
            int gridw, int gridh,
            int weightx, int weighty,
            int fill) {
        return constraint(gridx, gridy, gridw, gridh, weightx, weighty, fill, GridBagConstraints.CENTER);
    }

    public void create(
            int width, int height,
            int minWidth, int minHeight,
            int displayFps,
            String windowTitle,
            boolean maximize,
            KioskController.Callbacks callbacks) throws Exception {
        mCallbacks = callbacks;

        mLogger.log(TAG, "Look and Feel: " + UIManager.getLookAndFeel().getName());

        mFrame = new JFrame(windowTitle);
        mFrame.setSize(width, height);
        mFrame.setMinimumSize(new Dimension(minWidth, minHeight));
        mFrame.setLayout(null);
        mFrame.setBackground(BG_COLOR);
        if (mFrame.getRootPane() != null && mFrame.getRootPane().getContentPane() != null) {
            mFrame.getRootPane().getContentPane().setBackground(BG_COLOR);
        }

        mFrame.setLayout(new GridBagLayout());

        mPlayersView = new PlayersView(
                mLogger,
                mCallbacks);
        mFrame.add(mPlayersView, constraint(1, 0, 1, 1, 1, 1, GridBagConstraints.BOTH));

        mRtacDataPanel = new RtacDataPanel(mLogger);
        mFrame.add(mRtacDataPanel, constraint(0, 0, 1, 1, 0, 0, GridBagConstraints.VERTICAL, GridBagConstraints.NORTHWEST));

        mRtacPsaPanel = new RtacPsaPanel(mLogger);
        mFrame.add(mRtacPsaPanel, constraint(0, 1, 2, 1, 0, 0, GridBagConstraints.HORIZONTAL));

        mBottomStatus = new StatusView(new StringInfo("Please wait, initializing camera streams..."));
        mBottomStatus.setDefaultLayout();
        mFrame.add(mBottomStatus, constraint(0, 2, 2, 1, 0, 0, GridBagConstraints.HORIZONTAL));

        // For debugging layout, add a border around views
        if (false) {
            mPlayersView.setBorder(BorderFactory.createLineBorder(Color.GREEN, 1));
            mRtacPsaPanel.setBorder(BorderFactory.createLineBorder(Color.RED, 1));
            mRtacDataPanel.setBorder(BorderFactory.createLineBorder(Color.BLUE, 1));
        }

        mFrame.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        mFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                super.windowClosing(windowEvent);
                mCallbacks.onWindowClosing();
            }
        });
        mFrame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent keyEvent) {
                if (mCallbacks.onProcessKey(keyEvent.getKeyChar())) {
                    keyEvent.consume();
                }
                super.keyPressed(keyEvent);
            }
        });

        // Create an "invalid" cursor to make the cursor transparent in the frame.
        try {
            Toolkit toolkit = mFrame.getToolkit();
            mFrame.setCursor(toolkit.createCustomCursor(toolkit.createImage(""), new Point(), "cursor"));
        } catch (Exception ignore) {
        }

        if (maximize) {
            // Remove window borders. Must be done before the setVisible call.
            mFrame.setUndecorated(true);
        }

        mFrame.setVisible(true);
        // Canvases use a "buffered strategy" (to have 2 buffers) and must be created
        // after the main frame is set visible.
        mPlayersView.createVideoCanvases(
                mClock,
                mConsoleTask,
                mFpsMeasurerFactory,
                mHighlighterFactory,
                mCameras);
        if (maximize) {
            mFrame.setExtendedState(mFrame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
        }

        mKVController.getKeyChangedStream().subscribe(SwingUISchedulers.swingInvokeLater(), mKeyChangedSubscriber);
        mKVController.getConnectedStream().subscribe(SwingUISchedulers.swingInvokeLater(), mConnectedSubscriber);

        mRepaintTimer = new Timer(1000 / displayFps, this::onRepaintTimerTick);
    }

    private void onRepaintTimerTick(ActionEvent event) {
        if (mFrame == null || mPlayersView == null) {
            return;
        }
        mCallbacks.onRepaintTimerTick();
    }

    public void setBottomStatus(Map<String, StringInfo> lineInfos) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (lineInfos) {
            for (Map.Entry<String, StringInfo> info : lineInfos.entrySet()) {
                mBottomStatus.setStatus(info.getKey(), info.getValue());
            }
        }
    }

    public boolean updateAllHighlights() {
        return mPlayersView.updateAllHighlights();
    }

    public void release() {
        SwingUtilities.invokeLater(() -> {
            mRepaintTimer.stop();
            mPlayersView.releaseSync();
            if (mFrame != null) {
                mFrame.dispose();
                mFrame = null;
            }
        });
    }

    public void startTimer() {
        // TODO does this really need SwingUtilities.invokeLater ?
        mRepaintTimer.start();
    }

    public void setMainPlayerMute(boolean isMuted) {
        mPlayersView.setMainPlayerMute(isMuted);
    }

    public void setMainPlayerVolume(int percent) {
        mPlayersView.setMainPlayerVolume(percent);
    }

    public int getMainPlayerVolume() {
        return mPlayersView.getMainPlayerVolume();
    }

    public void startMainPlayer(File media) {
        mPlayersView.startMainPlayer(media);
    }

    public void stopMainPlayer() {
        mPlayersView.stopMainPlayer();
    }

    public void startCameraPlayer(CamInfo camInfo, String media) {
        mPlayersView.startCameraPlayer(camInfo, media);
    }

    public void stopCameraPlayer(CamInfo camInfo) {
        mPlayersView.stopCameraPlayer(camInfo);
    }

    public void setPlayerZoomed(boolean playerZoomed) {
        mPlayersView.setPlayerZoomed(playerZoomed);
    }

    private void onReceiveKeyChanged(IStream<? extends String> stream, String key) {
        // This executes on the AWT UI Thread via SwingUtilities.invokeLater.
        IKeyValue kvClient = mKVController.getKeyValueClient();
        if (kvClient == null) return;
        String value = kvClient.getValue(key);

        if (Constants.RtacPsaText.equals(key)) {
            mRtacPsaPanel.updateText(value);
        } else if (Constants.RoutesKey.equals(key)) {
            mRtacDataPanel.initializeRoutes(kvClient, value);
        } else {
            mRtacDataPanel.onKVChanged(key, value);
        }
    }

    private void onReceiveConnected(IStream<? extends Boolean> stream, Boolean value) {
        // This executes on the AWT UI Thread via SwingUtilities.invokeLater.
        mRtacPsaPanel.updateText(null);
    }
}
