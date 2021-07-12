package com.alfray.trainmotion;

import com.alfray.trainmotion.util.ILogger;
import com.google.common.io.Files;

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
    private final Random mRnd = new Random();
    private final ILogger mLogger;
    private File mPlaylistDir;
    private boolean mShuffle;

    @Inject
    public Playlist(ILogger logger) {
        mLogger = logger;
    }

    public void initialize(@Nonnull String playlistDir) throws IOException {
        mPlaylistDir = new File(playlistDir);
        if (!mPlaylistDir.isDirectory()) {
            mLogger.log(TAG, "Missing playlist dir: " + playlistDir);
            return;
        }

        // Read the index
        File indexFile = new File(mPlaylistDir, INDEX);
        for (String line : Files.readLines(indexFile, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            File videoFile = new File(mPlaylistDir, line);
            if (videoFile.isFile()) {
                mVideos.add(videoFile);
            } else {
                mLogger.log(TAG, "Ignore missing file '" + line + "' from index.");
            }
        }
        mLogger.log(TAG, "Found " + mVideos.size() + " videos at " + indexFile.getAbsolutePath());
    }

    public void setShuffle(boolean shuffle) {
        mShuffle = shuffle;
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
            index = mRnd.nextInt(mNext.size());
        }
        return Optional.of(mNext.remove(index));
    }
}
