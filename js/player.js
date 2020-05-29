"use strict";


const plPlaylistId = "PLjmlvzL_NxLrHU26aSPU5S1Z_iu3vRky-";
var plPlayer;
var plGridSize;
var plPlayerSize;
var isFullscreen = false;

function plLog(s) {
    console.log(s);
}

function plInit() {
    plLog("@@ init");
}

function onYouTubeIframeAPIReady() {
    plLog("onYouTubeIframeAPIReady");

    //plGridSize = [$("#pl-grid").width(), $("#pl-grid").height()];
    plGridSize = [window.innerWidth, window.innerHeight];
    plPlayerSize = [$("#pl-yt-player").width(), $("#pl-yt-player").height()];
    plLog("@@ plGridSize: " + plGridSize + ", playerSize: " + plPlayerSize);

    isFullscreen = false;
    plPlayer = new YT.Player("pl-yt-player", {
        // Initial size, or call plPlayer.setSize(w,h) later
        width: plPlayerSize[0],
        height: plPlayerSize[1],
        // Player options
        playerVars: {
            listType: "playlist",
            list: plPlaylistId,
            autoplay: 1,
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
    plLog("plOnPlayerReady") ; // : " + JSON.stringify(event));
    event.target.setVolume(0);
    event.target.playVideo();
}

var plEvent; // for debugging mostly
function plOnPlayerStateChange(event) {
    plLog("plOnPlayerStateChange") ; // : " + JSON.stringify(event));
    plEvent = event;

    plLog("Playing: " + plEvent.target.playerInfo.videoData.title);
}

$(document).keypress(function(event) {
    plLog("Document KeyPress: " + event.which);
    if (event.which == "f".charCodeAt()) {
        isFullscreen = !isFullscreen;
        plToggleYtFullscreen(isFullscreen);
    } else if (event.which == "1".charCodeAt()) {
        plHighlightVideo(1);
    } else if (event.which == "2".charCodeAt()) {
        plHighlightVideo(2);
    } else if (event.which == "3".charCodeAt()) {
        plHighlightVideo(3);
    }
});

function plToggleYtFullscreen(goToFullscreen) {
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

// ---

$(document).ready(plInit);
