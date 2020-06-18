# Train Motion

## Description

Source: https://bitbucket.org/ralfoide/train-motion

This is a project created for the
[Randall Museum Model Railroad](http://ralf.alfray.com/trains/randall)
in San Francisco.

Train Motion runs full screen on a laptop and plays a series of Youtube videos of the
model railroad in a loop.
Three cameras point to the layout and, when activity is detected on these cameras,
the view changes to a matrix displaying both the Youtube video as well as the three
layout cameras, showing whatever train is going by. Once the train is gone, the
Youtube video plays full screen again.

Please
[visit this page for more information](http://ralf.alfray.com/trains/blog/randall/2020-06-14__train_motion_video_display__d300ac8f.html "Project Description")
on this project.

![Overview Architecture Graphic](http://ralf.alfray.com/trains/blog/randall/index_ccd987b50b5bb36d608ce3e10d9579132f79076fd.jpg)


## License

Train Motion is licensed under the terms of the open source [MIT License](https://opensource.org/licenses/MIT "MIT License").

The full text of the license is provided in the file `LICENSE.txt`.


## Dependencies

The project is written in Java 8 (JDK or OpenJDK) and relies the following external libraries:

* [Dagger](https://dagger.dev/).
* [AutoFactory](https://github.com/google/auto/tree/master/factory).
* [JavaCV](https://github.com/bytedeco/javacv), specifically the [OpenCV](https://opencv.org/) and [FFmpeg](https://ffmpeg.org/) APIs.
* [JavaCPP](https://github.com/bytedeco/javacpp), required by JavaCV.
* FasterXML [Jackson Databond](https://github.com/FasterXML/jackson-databind).
* Eclipse [Jetty](https://www.eclipse.org/jetty/).

Rendering relies on:

* [Debian](https://www.debian.org/) Linux.
* [Chromium](https://www.chromium.org/).
* [Youtube Player API](https://developers.google.com/youtube/iframe_api_reference).


## Building with IJ

Requirements:

* IJ Community 2020.1+
* JDK 1.8 or OpenJDK 8

IJ > Open project > Select top "_train-motion_" directory.

To create a run configuration, the easiest way is to open `src/main/java/com/alfray/trainmotion/Main.java`,
right click the Run action command, and then edit the Run Configuration:

* Template: `Application`
* Main Class: `com.alfray.trainmotion.Main`
* Program Arguments:
 `--debug -u CamUserName -1 CamPassword1 -2 CamPassword2 -3 CamPassword3 -w $PROJECT_DIR$/src/main/resources/web`
* Use classpath of module: `train-motion.main`
* JRE: `Default 1.8`

The first build/run will take about forever as the dependencies gets downloaded.

See below for an explanation of the command-line options.
 
## Building with Gradle

Requirements:

* JDK 1.8 or OpenJDK 8

From the command line:

`$ ./gradlew assemble fatJar`

The first build/run will take about forever as the dependencies gets downloaded.

To run it:

`$ java -jar build/libs/train-motion-0.1-SNAPSHOT-all.jar <command line options>`

## Command-line Options

* `-d,--debug`:               Debug Display.
* `-h,--help`:                This usage help.

Cameras:

* `-1,--pass1 <password-1>`:  Password $P1
* `-2,--pass2 <password-2>`:  Password $P2
* `-3,--pass3 <password-3>`:  Password $P3
* `-u,--user <username>`:     Default $U name

Web server:

* `-p,--port <port>`:         Web server port (default is 8080)
* `-s,--size <pixels>`:       Size/width of the 16:9 camera feed (analysis+output, default 640)
* `-w,--web-root <path>`:     Absolute directory for web root (default: use jar embedded web root)


~~
