"use strict";


const plPlaylistId = "PLjmlvzL_NxLrHU26aSPU5S1Z_iu3vRky-";
var plStartInShuffle = true; // we shuffle *after* the 1st video plays.
var plPlayer;
var plGridSize;
var plPlayerSize;
var plFullscreen = false;
var plShuffle = true;
var plEvent; // for debugging mostly

function plLog(s) {
    console.log(s);
}

function plInit() {
    plLog("@@ init");
    plSetupCams();
}

function onYouTubeIframeAPIReady() {
    plLog("onYouTubeIframeAPIReady");

    //plGridSize = [$("#pl-grid").width(), $("#pl-grid").height()];
    plGridSize = [window.innerWidth, window.innerHeight];
    plPlayerSize = [$("#pl-yt-player").width(), $("#pl-yt-player").height()];
    plLog("@@ plGridSize: " + plGridSize + ", playerSize: " + plPlayerSize);

    plFullscreen = false;
    plPlayer = new YT.Player("pl-yt-player", {
        // Initial size, or call plPlayer.setSize(w,h) later
        width: plPlayerSize[0],
        height: plPlayerSize[1],
        // Player options
        playerVars: {
            listType: "playlist",
            list: plPlaylistId,
            autoplay: 0,
            controls: 0,
            fs: 0, // fullscreen button
            iv_load_policy: 0, // video annotations not shown
            loop: 1,
            rel: 0 // when paused only show suggested video from same channel
        },
        events: {
            "onReady": plOnPlayerReady,
            "onStateChange": plOnPlayerStateChange
        }
    })

}

function plOnPlayerReady(event) {
    plLog("plOnPlayerReady state=" + plPlayer.getPlayerState()) ; // : " + JSON.stringify(event));
    plEvent = event;
    event.target.mute();
    event.target.playVideo();
}

function plOnPlayerStateChange(event) {
    var state = plPlayer.getPlayerState();
    plLog("plOnPlayerStateChange state=" + state); // : " + JSON.stringify(event));
    plLog("Playing: " + plEvent.target.playerInfo.videoData.title);
    plEvent = event;

    // Set the initial shuffle once the first video starts playing, with a little
    // delay. Without the delay, it seems to work half of the time.
    if (plStartInShuffle && state == 1 /*playing*/) {
        plStartInShuffle = false;
        setTimeout( () => plSetShuffle(true) , 1000);
    }
}

$(document).keypress(function(event) {
    plLog("Document KeyPress: " + event.which);
    if (event.which == "s".charCodeAt()) {
        plSetShuffle(!plShuffle);
    } else if (event.which == "f".charCodeAt() || event.which == "u".charCodeAt()) {
        plSetFullscreen(!plFullscreen);
    } else if (event.which == "1".charCodeAt()) {
        plHighlightVideo(1);
    } else if (event.which == "2".charCodeAt()) {
        plHighlightVideo(2);
    } else if (event.which == "3".charCodeAt()) {
        plHighlightVideo(3);
    } else if (event.which == "n".charCodeAt()) {
        plPlayer.nextVideo();
    } else if (event.which == "p".charCodeAt()) {
        plPlayer.previousVideo();
    }
});

function plSetShuffle(shuffle) {
    plLog("plShuffle " + shuffle);
    plShuffle = shuffle;
    plPlayer.setShuffle(shuffle);
}

function plSetFullscreen(goToFullscreen) {
    plLog("plFullscreen " + goToFullscreen);
    plFullscreen = goToFullscreen;
    if (goToFullscreen) {
        $("#pl-video1").hide();
        $("#pl-video2").hide();
        $("#pl-video3").hide();
        var sx = plPlayerSize[0];
        var dx = plGridSize[0] - sx;
        var sy = plPlayerSize[1];
        var dy = plGridSize[1] - sy;
        $({value:0}).animate({value:1}, { 
            step: (val) => plPlayer.setSize(sx + dx * val, sy + dy * val),
            complete: () => plPlayer.setSize(plGridSize[0], plGridSize[1]),
        })
    } else {
        var sx = plGridSize[0];
        var dx = plPlayerSize[0] - sx;
        var sy = plGridSize[1];
        var dy = plPlayerSize[1] - sy;
        $({value:0}).animate({value:1}, { 
            step: (val) => plPlayer.setSize(sx + dx * val, sy + dy * val),
            complete: () => {
                plPlayer.setSize(plPlayerSize[0], plPlayerSize[1])
                $("#pl-video1").show();
                $("#pl-video2").show();
                $("#pl-video3").show();
            }
        })
    }
}

function plHighlightVideo(index123) {
    var elem = $("#pl-video" + index123);
    elem.css("border-color", "yellow");
    setTimeout( () => elem.css("border-color", "darkgreen"), 5000);
}

function plSetupCams() {
    $("#pl-video2").attr("src", "http://192.168.1.86/mjpg/video.mjpg")
}

// ---

$(document).ready(plInit);
