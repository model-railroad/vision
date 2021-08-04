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

package com.alflabs.trainmotion;

import com.alflabs.trainmotion.util.ILogger;
import com.alflabs.utils.FileOps;
import com.google.common.io.ByteSource;
import com.google.common.io.LineProcessor;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Parses the video playlist.
 * <p/>
 * WARNING: Do NOT invoke the getters from dagger constructors! It's too early and the playlist
 * has not been parsed yet.
 */
@Singleton
public class Playlist {
    private static final String TAG = Playlist.class.getSimpleName();
    private static final String INDEX = "_index.txt";

    private final List<File> mVideos = new ArrayList<>();
    private final List<File> mNext = new ArrayList<>();
    private final ILogger mLogger;
    private final FileOps mFileOps;
    private final Random mRandom;

    private File mPlaylistDir;
    private boolean mShuffle;

    @Inject
    public Playlist(ILogger logger, FileOps fileOps, Random random) {
        mLogger = logger;
        mFileOps = fileOps;
        mRandom = random;
    }

    public void initialize(@Nonnull String playlistDir) throws IOException {
        mPlaylistDir = new File(playlistDir);
        if (!mFileOps.isFile(mPlaylistDir)) {
            mLogger.log(TAG, "Missing playlist dir: " + playlistDir);
            return;
        }

        // Read the index
        File indexFile = new File(mPlaylistDir, INDEX);
        byte[] content = mFileOps.readBytes(indexFile);
        ByteSource.wrap(content).asCharSource(StandardCharsets.UTF_8).readLines(new LineProcessor<Void>() {
            @Override
            public boolean processLine(String line) throws IOException {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    return true; // continue to next line
                }

                File videoFile = new File(mPlaylistDir, line);
                if (mFileOps.isFile(videoFile)) {
                    mVideos.add(videoFile);
                } else {
                    mLogger.log(TAG, "Ignore missing file '" + line + "' from index.");
                }
                return true;
            }

            @Override
            public Void getResult() {
                return null;
            }
        });
        mLogger.log(TAG, "Found " + mVideos.size() + " videos at " + indexFile.getAbsolutePath());
    }

    public void setShuffle(boolean shuffle) {
        mShuffle = shuffle;
    }

    public boolean isShuffle() {
        return mShuffle;
    }

    @Nonnull
    public Optional<File> getNext() {
        if (mNext.isEmpty()) {
            mNext.addAll(mVideos);
        }

        if (mNext.isEmpty()) {
            return Optional.empty();
        }

        int index = 0;
        if (mShuffle) {
            index = mRandom.nextInt(mNext.size());
        }
        return Optional.of(mNext.remove(index));
    }
}
