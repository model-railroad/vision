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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteSource;
import com.google.common.io.LineProcessor;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;

/**
 * Parses the video playlist.
 * <p/>
 * WARNING: Do NOT invoke the getters from dagger constructors! It's too early and the playlist
 * has not been parsed yet.
 */
@Singleton
public class Playlist {
    private static final String TAG = Playlist.class.getSimpleName();
    static final String INDEX = "_index.txt";
    static final String PROPS = "_props.json";

    private final List<File> mVideos = new ArrayList<>();
    private final List<File> mNext = new ArrayList<>();
    private final FilesProperties mProps = new FilesProperties();
    private final ILogger mLogger;
    private final FileOps mFileOps;
    private final Random mRandom;
    private final ObjectMapper mJsonMapper;

    private File mPlaylistDir;
    private boolean mShuffle;

    @Inject
    public Playlist(
            ILogger logger,
            FileOps fileOps,
            Random random,
            ObjectMapper jsonMapper) {
        mLogger = logger;
        mFileOps = fileOps;
        mRandom = random;
        mJsonMapper = jsonMapper;
    }

    public void initialize(@Nonnull String playlistDir) throws IOException {
        mPlaylistDir = new File(playlistDir);
        mVideos.addAll(readIndexFile());
        mProps.addAll(readPropertiesFile());
        mLogger.log(TAG, "Found " + mVideos.size() + " videos");
    }

    @Nonnull
    private List<File> readIndexFile() throws IOException {
        List<File> videos = new ArrayList<>();
        File indexFile = new File(mPlaylistDir, INDEX);
        if (!mFileOps.isFile(indexFile)) {
            mLogger.log(TAG, "Missing playlist index: " + indexFile.getAbsolutePath());
            return videos;
        }

        // Read the index
        mLogger.log(TAG, "Reading playlist index: " + indexFile.getAbsolutePath());
        byte[] content = mFileOps.readBytes(indexFile);
        //noinspection UnstableApiUsage
        ByteSource.wrap(content).asCharSource(StandardCharsets.UTF_8).readLines(new LineProcessor<Void>() {
            @Override
            public boolean processLine(String line) throws IOException {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    return true; // continue to next line
                }

                File videoFile = new File(mPlaylistDir, line);
                if (mFileOps.isFile(videoFile)) {
                    videos.add(videoFile);
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
        return videos;
    }

    @VisibleForTesting
    protected TreeMap<String, FileProperties> readPropertiesFile() {
        File propsFile = new File(mPlaylistDir, PROPS);
        mLogger.log(TAG, "Reading playlist properties: " + propsFile.getAbsolutePath());
        if (mFileOps.isFile(propsFile)) {
            try {
                byte[] content = mFileOps.readBytes(propsFile);

                TypeReference<TreeMap<String, FileProperties>> mapTypeRef =
                        new TypeReference<TreeMap<String, FileProperties>>() { };

                return mJsonMapper.readValue(content, mapTypeRef);
            } catch (IOException e) {
                mLogger.log(TAG, "Invalid JSON syntax in properties: " + propsFile.getAbsolutePath());
            }
        }

        return new TreeMap<>();
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

    @Nonnull
    public Optional<FileProperties> getProperties(@Nonnull File file) {
        return mProps.get(file);
    }

    static class FilesProperties {
        private final Map<String, FileProperties> mProperties = new HashMap<>();
        public FilesProperties() {}

        public void addAll(Map<String, FileProperties> props) {
            for (Map.Entry<String, FileProperties> entry : props.entrySet()) {
                mProperties.put(
                        entry.getKey()
                                .trim()
                                .toLowerCase(Locale.US)
                                .replace(".mp4", ""),
                        entry.getValue());
            }
        }

        public Optional<FileProperties> get(@Nonnull File file) {
            String name = file.getName().trim().toLowerCase(Locale.US);
            FileProperties property = mProperties.get(name);
            if (property == null) {
                name = name.replace(".mp4", "");
                property = mProperties.get(name);
            }
            return Optional.ofNullable(property);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FileProperties {
        /** Number of seconds to play before switching to the next video. -1 if unspecified. */
        private int seconds = -1;           // JSON field name
        /** Volume override for video, in 0-100 percentage. -1 to use the default. */
        private int volume  = -1;           // JSON field name

        @SuppressWarnings("unused")
        public FileProperties() {}          // JSON constructor

        public FileProperties(int seconds, int volume) {
            this.seconds = seconds;
            this.volume = volume;
        }

        /** Number of seconds to play before switching to the next video. -1 if unspecified. */
        public int getSeconds() {
            return seconds;
        }

        /** Volume override for video, in 0-100 percentage. -1 to use the default. */
        public int getVolume() {
            return volume;
        }
    }
}
