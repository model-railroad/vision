"use strict";


const tmPlaylistId = "PLjmlvzL_NxLrHU26aSPU5S1Z_iu3vRky-";
const tmRetry = true; // set to false when debugging locally
const tmUseMjpeg = true;
const tmFullLockDelayMs = 5*1000; // don't toggle FS fo that delay
var tmStartInShuffle = false; // we shuffle *after* the 1st video plays.
var tmPlayer;
var tmFullSize;
var tmPlayerSize;
var tmFullscreen = false;
var tmShuffle = true;
var tmEvent; // for debugging mostly
var tmHighlights = {};
var tmHasMotion;
var tmFullToggleTs = 0;

function tmLog(s) {
    console.log(s);
}

function tmInit() {
    tmLog("@@ init");
    tmSetupCams();
}

function onYouTubeIframeAPIReady() {
    tmLog("onYouTubeIframeAPIReady");

    // Note: -20 due to the border:10px on table cells.
    tmFullSize = [$("#tm-table").width()-20, $("#tm-table").height()-20];
    tmPlayerSize = [$("#tm-yt-player").width(), $("#tm-yt-player").height()];
    tmLog("@@ tmFullSize: " + tmFullSize + ", playerSize: " + tmPlayerSize);

    tmFullscreen = false;
    tmPlayer = new YT.Player("tm-yt-player", {
        // Initial size, or call tmPlayer.setSize(w,h) later
        width: tmPlayerSize[0],
        height: tmPlayerSize[1],
        // Player options
        playerVars: {
            listType: "playlist",
            list: tmPlaylistId,
            autoplay: 0,
            controls: 0,
            fs: 0, // fullscreen button
            iv_load_policy: 0, // video annotations not shown
            loop: 1,
            rel: 0 // when paused only show suggested video from same channel
        },
        events: {
            "onReady": tmOnPlayerReady,
            "onStateChange": tmOnPlayerStateChange
        }
    })

}

function tmOnPlayerReady(event) {
    tmLog("tmOnPlayerReady state=" + tmPlayer.getPlayerState()) ; // : " + JSON.stringify(event));
    tmEvent = event;
    event.target.mute();
    event.target.playVideo();

    setTimeout( () => tmCheckMotionStatus(), 2*1000 );
}

function tmOnPlayerStateChange(event) {
    var state = tmPlayer.getPlayerState();
    tmLog("tmOnPlayerStateChange state=" + state); // : " + JSON.stringify(event));
    tmLog("Playing: " + tmEvent.target.playerInfo.videoData.title);
    tmEvent = event;

    var title = tmEvent.target.playerInfo.videoData.title;
    title = title.replace("Randall Museum Model Railroad", "");
    title = "[Youtube] " + title;
    $("#tm-yt-title").text(title);

    // Set the initial shuffle once the first video starts playing, with a little
    // delay. Without the delay, it seems to work half of the time.
    if (tmStartInShuffle && state == 1 /*playing*/) {
        tmStartInShuffle = false;
        setTimeout( () => tmSetShuffle(true) , 1000);
    }
}

$(document).keypress(function(event) {
    tmLog("Document KeyPress: " + event.which);
    if (event.which == "s".charCodeAt()) {
        tmSetShuffle(!tmShuffle);
    } else if (event.which == "f".charCodeAt() || event.which == "u".charCodeAt()) {
        tmSetFullscreen(!tmFullscreen);
    } else if (event.which == "1".charCodeAt()) {
        tmHighlightVideo(1, true, true);
    } else if (event.which == "2".charCodeAt()) {
        tmHighlightVideo(2, true, true);
    } else if (event.which == "3".charCodeAt()) {
        tmHighlightVideo(3, true, true);
    } else if (event.which == "n".charCodeAt()) {
        tmPlayer.nextVideo();
    } else if (event.which == "p".charCodeAt()) {
        tmPlayer.previousVideo();
    }
});

function tmSetShuffle(shuffle) {
    tmLog("tmShuffle " + shuffle);
    tmShuffle = shuffle;
    tmPlayer.setShuffle(shuffle);
}

// Returns true if actually switched, false if it did not.
function tmSetFullscreen(goToFullscreen) {
    if (tmFullscreen == goToFullscreen) {
        return true;
    }
    var now = Date.now();
    tmLog("tmFullscreen " + goToFullscreen + " // delta: " + (now - tmFullToggleTs));
    if (tmFullToggleTs > 0 && now - tmFullToggleTs < tmFullLockDelayMs) {
        return false;
    }
    tmFullToggleTs = now;
    tmFullscreen = goToFullscreen;

    // Animated version:
    var p = $("#tm-cell0");
    if (goToFullscreen) {
        var sx = tmPlayerSize[0];
        var dx = tmFullSize[0] - sx;
        var sy = tmPlayerSize[1];
        var dy = tmFullSize[1] - sy;
        $({value:0}).animate({value: 1}, {
            step: (val) => {
                p.width(sx + dx * val);
                p.height(sy + dy * val);
            },
            start: () => p.css("position", "fixed"),
            complete: () => tmPlayer.setSize(tmFullSize[0], tmFullSize[1]),
        })
    } else {
        var sx = tmFullSize[0];
        var dx = tmPlayerSize[0] - sx;
        var sy = tmFullSize[1];
        var dy = tmPlayerSize[1] - sy;
        $({value:0}).animate({value:1}, { 
            step: (val) => {
                p.width(sx + dx * val);
                p.height(sy + dy * val);
            },
            complete: () => {
                p.css("position", "absolute");
                tmPlayer.setSize(tmPlayerSize[0], tmPlayerSize[1])
            }
        })
    }

    // Non-animated version:
    // if (goToFullscreen) {
    //     tmLog("Switch to size: " + tmFullSize);
    //     $("#tm-yt-player").css("position", "fixed");
    //     tmPlayer.setSize(tmFullSize[0], tmFullSize[1]);
    // } else {
    //     tmLog("Switch to size: " + tmPlayerSize);
    //     $("#tm-yt-player").css("position", "absolute");
    //     tmPlayer.setSize(tmPlayerSize[0], tmPlayerSize[1]);
    // }

    return true;
}

function tmHighlightVideo(index123, highlight, auto_off) {
    if (tmHighlights[index123] == highlight) {
        return;
    }

    tmHighlights[index123] = highlight;
    var elem = $("#tm-cell" + index123);
    elem.css("border-color", highlight ? "yellow" : "darkgreen");

    if (highlight && auto_off) {
        setTimeout( () => {
            tmHighlights[index123] = false;
            elem.css("border-color", "darkgreen")
        }, 5000);
    }
}

function tmSetupCams() {
    tmSetupCamN(1);
    tmSetupCamN(2);
    tmSetupCamN(3);
}

function tmSetupCamN(index) {
    var e = $("#tm-video" + index);

    if (tmUseMjpeg) {
        e.error( () => {
            e.attr("src", "no_camera.jpg");
            if (tmRetry) {
                setTimeout( () => tmSetupCamN(index), 500);
            }
        })
        .attr("src", "/mjpeg/" + index)
    
    } else {
        // Experimental. May not work.
        e.error( () => {
            e.attr("src", "no_camera.mp4");
            if (tmRetry) {
                setTimeout( () => tmSetupCamN(index), 500);
            }
        })
        .attr("src", "/h264/" + index)
    }
}

function tmCheckMotionStatus() {
    $.ajax({
        url: "/status",
        method: "GET",
        dataType: "json",
        contentType: "application/json",
        error: (xhr, status, error) => {
            tmLog("Status failed: " + error);
            if (tmRetry) {
                // Retry in 2 seconds
                setTimeout( () => tmCheckMotionStatus(), 2*1000 );
            }
        },
        success: (result, status, xhr) => {
            // tmLog("Status OK: " + JSON.stringify(result));
            tmProcessStatus(result);
            // Check in 1/2 seconds
            setTimeout( () => tmCheckMotionStatus(), 0.5*1000 );
        }
    }) // end ajax
}

function tmProcessStatus(status) {
    var hasMotion = false;
    for (var i = 1; i <= 3; i++) {
        var camN = "cam" + i;
        var on = false;
        if (camN in status) {
            on = status[camN] == true;
        }
        hasMotion = hasMotion || on;
        tmHighlightVideo(i, on, false);
    }
    if (hasMotion != tmHasMotion) {
        if (tmSetFullscreen(!hasMotion)) {
            tmHasMotion = hasMotion;
        }
    }
}

// ---

$(document).ready(tmInit);
