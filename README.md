# Train Motion

## Description

Source: https://bitbucket.org/ralfoide/train-motion

This is a project created for the
[Randall Museum Model Railroad](http://ralf.alfray.com/trains/randall), which is an automated live
HO-sized model train exhibit in the [Randall Museum](https://randallmuseum.org/) in San Francisco.

__Train Motion__ runs full screen on a laptop and plays a series of Youtube videos of the
model railroad in a loop.
Three cameras point to the layout and, when activity is detected on these cameras,
the view changes to a matrix displaying both the Youtube video as well as the three
layout cameras, showing whatever train is going by. Once the train is gone, the
Youtube video plays full screen again.

Please
[visit this page for more information](http://ralf.alfray.com/trains/blog/randall/2020-06-14__train_motion_video_display__d300ac8f.html "Project Description")
on this project.

![Overview Architecture Graphic](http://ralf.alfray.com/trains/blog/randall/index_ccd987b50b5bb36d608ce3e10d9579132f79076fd.jpg)

As usual with my train-related projects, although this project has been designed for the needs of
the [Randall Museum Model Railroad](http://ralf.alfray.com/trains/randall), the project itself is
kept generic and customizable. It is made available open source with the goal that others pick it
up and use it too.

__Train Motion__ is designed to work under [Debian](https://www.debian.org/) Linux.
However development is also done under Windows with [Cygwin](https://www.cygwin.com/)
or [Raspbian](https://www.raspberrypi.org/software/) as needed.
The project is designed to be reasonably portable to any similar architecture. 


## Input

Live source video comes from live IP Security camera, either over wifi or ethernet. 
The FFMPEG library is used to decode the camera streams, which means it will support pretty much
any standard video stream, from MJPEG to MPEG h264 ot h265. Old-style pull mode (where JPEG frames
are pulled at periodic interval) is not supported; however most of these types of cameras also used
to support MJPEG mode. For performance reasons, h264 or h265 streams should be favored.

The player also plays non-live media. In the prototype v0.1 version, this was done by embedding
a YouTube player and showing a playlist in a loop, right over wifi. Although this was fine for the
v0.1 prototype, we do not want to rely on wifi ability for this to work in a museum exhibit. One
advantage is that this would allow us to update the playlist remotely.

So instead in the v0.2 version, videos are pulled offline into a local "media" directory, and
recorded videos are played directly off the disk. A script is provided that can fill such a directory
by automatically downloading YouTube videos off a playlist using _youtube-dl_ and caching them locally.
The goal is to them run that update script daily, which will offer us an easy way to update the
playlist without requiring direct intervention on the display computer.


## Output

In the prototype v0.1 version, the output was provided via an embedded web server.
A Chrome or Chromium browser running in kiosk mode (a.k.a. fullscreen) would display the recorded
playlist and well as the 3 live camera streams. JavaScript is then used to highlight motion and
toggle the playlist viewer between its full-size render or its quarter-size render.

In the v0.2 version, the display is handled by a native Java window showing the playlist viewer
and the cameras streams. This is offers better performance and control. 
The web browser output is still available as an alternative. 


## License

__Train Motion__ is licensed under the terms of the open source
[GNU General Public License version 3](https://opensource.org/licenses/GPL-3.0 "GPL v3").

    Copyright (C) 2020-2021 alf.labs gmail com,

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

The full GPL license is available in the file `LICENSE.txt`.


## Dependencies

The project is written in Java 8 (JDK or OpenJDK) and relies on the following external libraries:

* [Dagger](https://dagger.dev/).
* [AutoFactory](https://github.com/google/auto/tree/master/factory).
* [JavaCV](https://github.com/bytedeco/javacv), specifically the [OpenCV](https://opencv.org/) and [FFmpeg](https://ffmpeg.org/) APIs.
* [JavaCPP](https://github.com/bytedeco/javacpp), required by JavaCV.
* [VLC](https://www.videolan.org/) and [vlcj](https://github.com/caprica/vlcj).
* FasterXML [Jackson Databind](https://github.com/FasterXML/jackson-databind).
* Eclipse [Jetty](https://www.eclipse.org/jetty/).
* [LibUtils android-lib-v2](https://bitbucket.org/ralfoide/libutils/src/android-lib-v2/).

Rendering for v0.2 relies on VLC, found here:

* [VLC](https://www.videolan.org/vlc/) and [vlcj](https://github.com/caprica/vlcj).
* Side Note: __vlcj__ is GPL v3 and not LGPL as one would expect from a library.

For linux, install VLC on the system first:

`$ apt install vlc libvlc5`

For Windows, [VLC 3.0](https://www.videolan.org/vlc/) or more recent should be installed on the system.


Rendering for v0.1 relies on:

* [Debian](https://www.debian.org/) Linux.
* [Chromium](https://www.chromium.org/).
* [Youtube Player API](https://developers.google.com/youtube/iframe_api_reference).


## Source checkout and setup

The proper way to check out the source is:

    $ git clone https://bitbucket.org/ralfoide/train-motion.git
    $ ./_init.sh [-f]

The `_init.sh` script checks out the git sub-repository [LibUtils](https://bitbucket.org/ralfoide/libutils)
and then switches it to its [android-lib-v2](https://bitbucket.org/ralfoide/libutils/src/android-lib-v2/) branch.


## Building with IJ

Requirements:

* IJ Community 2020.1+
* JDK 1.8 or OpenJDK 8

IJ > Open project > Select top "_train-motion_" directory.

### Run Configuration

To create a run configuration, the easiest way is to open `src/main/java/com/alfray/trainmotion/Main.java`,
right click the Run action command, and then edit the Run Configuration:

* Template: `Application`
* Main Class: `com.alflabs.trainmotion.Main`
* Program Arguments: `--config config.ini -m /path/to/media/folder`
* Use classpath of module: `train-motion.main`
* JRE: `Default 1.8`

The first build/run will take about forever as the dependencies gets downloaded.

See below for an explanation of the command-line options.
 

### Test Configuration

To create a test configuration, the easiest with IJ is to select `test/java` in the
project list, right-click, and select the option `Run Tests in train-motion`.
This creates a gradle test config and runs it.

To create the same configuration manually:

* Edit Configuration > + > Gradle
* Run Cmd Line: `:test --tests *`
* Gradle Project: `train-motion`
* Environment: leave empty



## Building with Gradle

Requirements:

* JDK 1.8 or OpenJDK 8

From the command line:

`$ ./gradlew assemble fatJar`

The first build/run will take about forever as the dependencies gets downloaded.

To run it:

`$ java -jar build/libs/train-motion-0.5-SNAPSHOT-all.jar <command line options>`


## Configuration and Command-line Options for v0.5

Configuration is done using a _config.ini_ file.

An example is provided in `src/main/resources/config.ini`.

Key/values expected in the configuration file:

__Input camera streams__:
* `cam1_url`, `cam2_url`, `cam3_url`: The URL for the live cameras download stream.
  * The syntax varies by model/make. \
    For example for my Edimax cameras, the syntax is
    `rtsp://username:password@ipaddress:554/ipcam_h264.sdp`. \
    If you do not know the syntax for your camera model, google it or look up the documentation. \
    The path can use the magic variables $U, $P1, $P2, $P3 which are
    replaced by command-line arguments -u, -p1, -p2, -p3 respectively.
  * For testing, a comma-separated list of mp4 file paths can be provided instead.
    See `src/main/resources/config.ini` for examples of use.
  * The number of cameras that train-motion can handle is not limited.
    The key parameter is `camN_url` where N>=1.
    Right now the visual output is optimized for 3 cameras.
    At least one live camera is required.
* `cam1_threshold`, `cam2_threshold`, `cam3_threshold`: The threshold for
  detecting motion on each camera. Default is 0.3, which means 0.3% of pixels 
  change detected between frames.
  The key parameter is `camN_threshold` where N>=1.

__Local Media Playback__:
* `volume_pct`: The volume percentage when playing media videos. Default is 50%.

__Configuration shared with the `_sync_playlist.sh` script__:
* `playlist_dir`: The directory where the local media is located in v0.2
  * The directory must contain at least one media file to play.
  * The directory must contain an `_index.txt` file listing the filenames to play.
  * Overridden by the `--media` command-line argument if present.
* `playlist_id`: The YouTube playlist id (which starts with `PL`).
  * In v0.2 only, this is used by the `_sync_playlist.sh` script.
* `youtube_dl`: Optional path to the [youtube-dl](https://youtube-dl.org/) program.
  * In v0.2 only, this is used by the `_sync_playlist.sh` script.
 

__Main Command-line Options__:

* `-c,--config`:              Path to config.ini file (default is ./config.ini).
* `-m,--media`:               Path for playlist media directory (default: use config file playlist_dir).
* `-h,--help`:                Usage help.
* `-d,--debug`:               Debug Display.


__Optional, for cameras__:

* `-1,--pass1 <password-1>`:  Password $P1 if present on a camera URL.
* `-2,--pass2 <password-2>`:  Password $P2 if present on a camera URL
* `-3,--pass3 <password-3>`:  Password $P3 if present on a camera URL
* `-u,--user <username>`:     Default $U name if present on a camera URL

Note: these parameter become necessary only if the config.init uses the variables.


__Optional, for Web server__:

* `-p,--port <port>`:         Web server port (default is 8080)
* `-s,--size <pixels>`:       Size/width of the 16:9 camera feed (analysis+output, default 640)
* `-w,--web-root <path>`:     Absolute directory for web root (default: use jar embedded web root)

By default, a web server is created on port 8080, at [http://localhost:8080](http://localhost:8080).
This serves the main html & JS files. The port can be changed using the `-p` option.

The `-s` option exists to reduce the size of the camera feed to the given width.
Height is always center-cropped to a 16:9 ratio.
The reduced image size drastically reduces the motion detection cost and it is recommended to not
provide it, in order to use the default as the default web page player expects that size.

The `-w` option is only useful for development.
If given, it must point to the `src/main/resources/web` directory on the local path.
When `-w` is specified, Jetty will serve the actual file from the file system, which
allows the html / JS files to be refreshed live for development.
The default is to use the html / JS files from the JAR archive.

~~
