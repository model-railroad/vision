"use strict";


const plPlaylistId = "PLjmlvzL_NxLrHU26aSPU5S1Z_iu3vRky-";
var plStartInShuffle = true; // we shuffle *after* the 1st video plays.
var plPlayer;
var plFullSize;
var plPlayerSize;
var plFullscreen = false;
var plShuffle = true;
var plEvent; // for debugging mostly
var plHighlights = {};
var plHasMotion;

function plLog(s) {
    console.log(s);
}

function plInit() {
    plLog("@@ init");
    plSetupCams();
}

function onYouTubeIframeAPIReady() {
    plLog("onYouTubeIframeAPIReady");

    //plFullSize = [$("#pl-grid").width(), $("#pl-grid").height()];
    plFullSize = [window.innerWidth, window.innerHeight];
    plPlayerSize = [$("#pl-yt-player").width(), $("#pl-yt-player").height()];
    plLog("@@ plFullSize: " + plFullSize + ", playerSize: " + plPlayerSize);

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

    setTimeout( () => plCheckMotionStatus(), 2*1000 );
}

function plOnPlayerStateChange(event) {
    var state = plPlayer.getPlayerState();
    plLog("plOnPlayerStateChange state=" + state); // : " + JSON.stringify(event));
    plLog("Playing: " + plEvent.target.playerInfo.videoData.title);
    plEvent = event;

    var title = plEvent.target.playerInfo.videoData.title;
    title = "[Youtube] " + title;
    $("#pl-yt-title").text(title);

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
        plHighlightVideo(1, true, true);
    } else if (event.which == "2".charCodeAt()) {
        plHighlightVideo(2, true, true);
    } else if (event.which == "3".charCodeAt()) {
        plHighlightVideo(3, true, true);
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
    if (plFullscreen == goToFullscreen) {
        return;
    }
    plLog("plFullscreen " + goToFullscreen);
    plFullscreen = goToFullscreen;

    // Animated version (currently not working with <table>):
    // if (goToFullscreen) {
    //     $("#pl-cell1").hide();
    //     $("#pl-cell2").hide();
    //     $("#pl-cell3").hide();
    //     var sx = plPlayerSize[0];
    //     var dx = plFullSize[0] - sx;
    //     var sy = plPlayerSize[1];
    //     var dy = plFullSize[1] - sy;
    //     $({value:0}).animate({value: 1}, {
    //         step: (val) => plPlayer.setSize(sx + dx * val, sy + dy * val),
    //         start: () => $("#pl-yt-player").css("position", "fixed"),
    //         complete: () => plPlayer.setSize(plFullSize[0], plFullSize[1]),
    //     })
    // } else {
    //     var sx = plFullSize[0];
    //     var dx = plPlayerSize[0] - sx;
    //     var sy = plFullSize[1];
    //     var dy = plPlayerSize[1] - sy;
    //     $({value:0}).animate({value:1}, { 
    //         step: (val) => plPlayer.setSize(sx + dx * val, sy + dy * val),
    //         complete: () => {
    //             $("#pl-yt-player").css("position", "absolute");
    //             plPlayer.setSize(plPlayerSize[0], plPlayerSize[1])
    //             $("#pl-cell1").show();
    //             $("#pl-cell2").show();
    //             $("#pl-cell3").show();
    //         }
    //     })
    // }

    // Non-animated version:
    if (goToFullscreen) {
        plLog("Switch to size: " + plFullSize);
        $("#pl-yt-player").css("position", "fixed");
        plPlayer.setSize(plFullSize[0], plFullSize[1]);
    } else {
        plLog("Switch to size: " + plPlayerSize);
        $("#pl-yt-player").css("position", "absolute");
        plPlayer.setSize(plPlayerSize[0], plPlayerSize[1]);
    }
}

function plHighlightVideo(index123, highlight, auto_off) {
    if (plHighlights[index123] == highlight) {
        return;
    }

    plHighlights[index123] = highlight;
    var elem = $("#pl-cell" + index123);
    elem.css("border-color", highlight ? "yellow" : "darkgreen");

    if (highlight && auto_off) {
        setTimeout( () => {
            plHighlights[index123] = false;
            elem.css("border-color", "darkgreen")
        }, 5000);
    }
}

function plSetupCams() {
    plSetupCamN(1);
    plSetupCamN(2);
    plSetupCamN(3);
}

function plSetupCamN(index) {
    var e = $("#pl-video" + index);
    e.error( () => {
        e.attr("src", "no_camera.jpg");    
        setTimeout( () => plSetupCamN(index), 500);
    })
    .attr("src", "/mjpeg/" + index)
}

function plCheckMotionStatus() {
    $.ajax({
        url: "/status",
        method: "GET",
        dataType: "json",
        contentType: "application/json",
        error: (xhr, status, error) => {
            plLog("Status failed: " + error);
            // Retry in 2 seconds
            setTimeout( () => plCheckMotionStatus(), 2*1000 );
        },
        success: (result, status, xhr) => {
            // plLog("Status OK: " + JSON.stringify(result));
            plProcessStatus(result);
            // Check in 1/2 seconds
            setTimeout( () => plCheckMotionStatus(), 0.5*1000 );
        }
    }) // end ajax
}

function plProcessStatus(status) {
    var hasMotion = false;
    for (var i = 1; i <= 3; i++) {
        var camN = "cam" + i;
        var on = false;
        if (camN in status) {
            on = status[camN] == true;
        }
        hasMotion = hasMotion || on;
        plHighlightVideo(i, on, false);
    }
    if (hasMotion != plHasMotion) {
        plHasMotion = hasMotion;
        plSetFullscreen(!hasMotion);
    }
}

// ---

$(document).ready(plInit);
