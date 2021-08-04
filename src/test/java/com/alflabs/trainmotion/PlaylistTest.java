package com.alflabs.trainmotion;

import com.alflabs.trainmotion.dagger.DaggerITrainMotionTestComponent;
import com.alflabs.trainmotion.dagger.ITrainMotionTestComponent;
import com.alflabs.utils.FileOps;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static com.alflabs.trainmotion.Playlist.INDEX;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

public class PlaylistTest {

    @Inject Playlist mPlaylist;
    @Inject FileOps mFileOps;
    @Inject Random mRandom;

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
