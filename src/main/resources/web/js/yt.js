"use strict";


const tmPlaylistId = "PLjmlvzL_NxLrHU26aSPU5S1Z_iu3vRky-";
var tmStartInShuffle = false; // we shuffle *after* the 1st video plays.
var tmPlayer;
var tmFullSize;
var tmPlayerSize;
var tmShuffle = true;

function tmLog(s) {
    console.log(s);
}

function tmInit() {
    tmLog("@@ init");
}

function onYouTubeIframeAPIReady() {
    tmLog("onYouTubeIframeAPIReady");

// for debug only
//    tmFullSize   = [$("#tm-body").width(),      $("#tm-body").height()     ];
//    tmPlayerSize = [$("#tm-yt-player").width(), $("#tm-yt-player").height()];
//    tmLog("Body   size: " + tmFullSize);
//    tmLog("Player size: " + tmPlayerSize);

    tmPlayer = new YT.Player("tm-yt-player", {
        // width & height not set, forced via CSS.
        // Player options
        playerVars: {
            listType: "playlist",
            list: tmPlaylistId,
            autoplay: 0,
            controls: 0,
            fs: 0,              // fullscreen button
            iv_load_policy: 0,  // video annotations not shown
            loop: 1,
            rel: 0              // when paused only show suggested video from same channel
        },
        events: {
            "onReady": tmOnPlayerReady,
            "onStateChange": tmOnPlayerStateChange
        }
    })
}

function tmPlayerIsPlaying() {
    // Stated listed at https://developers.google.com/youtube/iframe_api_reference#Playback_status
    return tmPlayer.getPlayerState() == 1;
}

function tmOnPlayerReady(event) {
    tmLog("tmOnPlayerReady state=" + tmPlayer.getPlayerState()) ; // : " + JSON.stringify(event));
    event.target.mute();
    event.target.playVideo();
}

function tmOnPlayerStateChange(event) {
    var state = tmPlayer.getPlayerState();
    tmLog("tmOnPlayerStateChange state=" + state); // : " + JSON.stringify(event));
    tmLog("Playing: " + tmEvent.target.playerInfo.videoData.title);

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
    } else if (event.which == "n".charCodeAt()) {
        tmPlayer.nextVideo();
    } else if (event.which == "p".charCodeAt()) {
        tmPlayer.previousVideo();
    } else if (event.which == "k".charCodeAt()) {
        if (tmPlayerIsPlaying()) {
            tmPlayer.pauseVideo();
        } else {
            tmPlayer.playVideo();
        }
    }
});

function tmSetShuffle(shuffle) {
    tmLog("tmShuffle " + shuffle);
    tmShuffle = shuffle;
    tmPlayer.setShuffle(shuffle);
}

// ---

$(document).ready(tmInit);
