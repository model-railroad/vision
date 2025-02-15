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

import com.alflabs.trainmotion.dagger.DaggerITrainMotionTestComponent;
import com.alflabs.trainmotion.dagger.ITrainMotionTestComponent;
import com.alflabs.utils.FileOps;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;

import static com.alflabs.trainmotion.Playlist.INDEX;
import static com.alflabs.trainmotion.Playlist.PROPS;
import static com.google.common.truth.Truth.assertThat;

public class PlaylistTest {

    @Inject Playlist mPlaylist;
    @Inject FileOps mFileOps;
    @Inject Random mRandom;
    @Inject ObjectMapper mJsonMapper;

    public interface _injector {
        void inject(PlaylistTest test);
    }

    @Before
    public void setUp() {
        ITrainMotionTestComponent component = DaggerITrainMotionTestComponent.factory().createComponent();
        component.inject(this);
    }

    @Test
    public void testEmpty() throws IOException {
        mPlaylist.initialize("/tmp/empty_dir");
        assertThat(mPlaylist.isShuffle()).isFalse();
        assertThat(mPlaylist.getNext().isPresent()).isFalse();
        assertThat(mPlaylist.getNext().isPresent()).isFalse();
    }

    @Test
    public void testPlay_NoShuffle() throws IOException {
        final String dir = "/tmp/media_dir";
        File index = setupPlaylist3Files(dir);

        mPlaylist.initialize(index.getParent());
        assertThat(mPlaylist.isShuffle()).isFalse();
        assertThat(mPlaylist.getNext().get()).isEqualTo(new File(dir, "file1"));
        assertThat(mPlaylist.getNext().get()).isEqualTo(new File(dir, "file2"));
        assertThat(mPlaylist.getNext().get()).isEqualTo(new File(dir, "file4"));
        // loops back to beginning
        assertThat(mPlaylist.getNext().get()).isEqualTo(new File(dir, "file1"));
        assertThat(mPlaylist.getNext().get()).isEqualTo(new File(dir, "file2"));
        assertThat(mPlaylist.getNext().get()).isEqualTo(new File(dir, "file4"));
    }

    @Test
    public void testPlay_WithShuffle() throws IOException {
        final String dir = "/tmp/media_dir";
        File index = setupPlaylist3Files(dir);

        mPlaylist.initialize(index.getParent());
        mPlaylist.setShuffle(true);
        assertThat(mPlaylist.isShuffle()).isTrue();
        assertThat(mPlaylist.getNext().get()).isEqualTo(new File(dir, "file4"));
        assertThat(mPlaylist.getNext().get()).isEqualTo(new File(dir, "file2"));
        assertThat(mPlaylist.getNext().get()).isEqualTo(new File(dir, "file1"));
        // loops back to beginning
        assertThat(mPlaylist.getNext().get()).isEqualTo(new File(dir, "file4"));
        assertThat(mPlaylist.getNext().get()).isEqualTo(new File(dir, "file2"));
        assertThat(mPlaylist.getNext().get()).isEqualTo(new File(dir, "file1"));
    }

    @Test
    public void testGetProperties() throws IOException {
        final String dir = "/tmp/media_dir";
        File props = new File(dir, PROPS);
        mFileOps.writeBytes(
                ("{ \"FooBar.mp4\": { \"volume\": 25, \"seconds\": 314 }," +
                        "\"no-volume\": { \"seconds\": 314 }," +
                        "\"no-seconds\": { \"volume\": 25 }, " +
                        "\"invalid\": { \"something\": \"unrelated\" } } "
                ).getBytes(StandardCharsets.UTF_8),
                props);
        mPlaylist.initialize(props.getParent());

        File f_foo = new File(dir, "foobar");
        assertThat(mPlaylist.getProperties(f_foo).isPresent()).isTrue();
        assertThat(mPlaylist.getProperties(f_foo).get().getVolume()).isEqualTo(25);
        assertThat(mPlaylist.getProperties(f_foo).get().getSeconds()).isEqualTo(314);

        File f_no_vol = new File(dir, "no-volume");
        assertThat(mPlaylist.getProperties(f_no_vol).isPresent()).isTrue();
        assertThat(mPlaylist.getProperties(f_no_vol).get().getVolume()).isEqualTo(-1);
        assertThat(mPlaylist.getProperties(f_no_vol).get().getSeconds()).isEqualTo(314);

        File f_no_sec = new File(dir, "no-seconds");
        assertThat(mPlaylist.getProperties(f_no_sec).isPresent()).isTrue();
        assertThat(mPlaylist.getProperties(f_no_sec).get().getVolume()).isEqualTo(25);
        assertThat(mPlaylist.getProperties(f_no_sec).get().getSeconds()).isEqualTo(-1);

        File f_inv = new File(dir, "invalid");
        assertThat(mPlaylist.getProperties(f_inv).isPresent()).isTrue();
        assertThat(mPlaylist.getProperties(f_inv).get().getVolume()).isEqualTo(-1);
        assertThat(mPlaylist.getProperties(f_inv).get().getSeconds()).isEqualTo(-1);
    }

    @Test
    public void testGetProperties_invalidProps() throws IOException {
        final String dir = "/tmp/media_dir";
        File props = new File(dir, PROPS);
        mFileOps.writeBytes(
                ("{ \"FooBar.mp4\": { \"blah\": 1, \"volume\": 25, \"seconds\": 314 } } "
                ).getBytes(StandardCharsets.UTF_8),
                props);
        mPlaylist.initialize(props.getParent());

        Optional<Playlist.FileProperties> fp = mPlaylist.getProperties(new File(dir, "foobar"));
        assertThat(fp.isPresent()).isTrue();
        assertThat(fp.get().getSeconds()).isEqualTo(314);
        assertThat(fp.get().getVolume()).isEqualTo(25);
    }

    @Test
    public void testGetProperties_invalidSyntaxOrDataType() throws IOException {
        final String dir = "/tmp/media_dir";
        File props = new File(dir, PROPS);
        mFileOps.writeBytes(
                ("{ \"whatever\" : \"unexpected data type\" } "
                ).getBytes(StandardCharsets.UTF_8),
                props);
        mPlaylist.initialize(props.getParent());

        Optional<Playlist.FileProperties> fp = mPlaylist.getProperties(new File(dir, "whatever"));
        assertThat(fp.isPresent()).isFalse();
    }

    @Test
    public void testGenerateProperties() throws JsonProcessingException {
        Playlist.FileProperties fp = new Playlist.FileProperties(/* seconds */ 12, /* volume */ 25);
        Map<String, Playlist.FileProperties> map = new TreeMap<>();
        map.put("foobar", fp);

        String json = mJsonMapper.writeValueAsString(map);
        assertThat(json.replaceAll("[\r\n ]+", " ")).isEqualTo(
                "{ \"foobar\" : { \"seconds\" : 12, \"volume\" : 25 } }");
    }

    private File setupPlaylist3Files(String dir) throws IOException {
        File index = new File(dir, INDEX);
        mFileOps.writeBytes(
                ("file1\n" +
                 "  # comment \n" +
                 "  file2 \n" +
                 "  non-existing-file3 \n" +
                 "file4  ").getBytes(StandardCharsets.UTF_8),
                index);
        mFileOps.writeBytes("content".getBytes(StandardCharsets.UTF_8), new File(dir, "file1"));
        mFileOps.writeBytes("content".getBytes(StandardCharsets.UTF_8), new File(dir, "file2"));
        mFileOps.writeBytes("content".getBytes(StandardCharsets.UTF_8), new File(dir, "file4"));
        return index;
    }
}
