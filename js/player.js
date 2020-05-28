"use strict";


const plPlaylistId = "PLjmlvzL_NxLp8GZfdG0cQkg5IwD_PF5Pc";
var plPlayer;
var plPlayerWidth;

function plLog(s) {
    console.log(s);
}

function plInit() {
    plLog("@@ init");
}

function onYouTubeIframeAPIReady() {
    plLog("onYouTubeIframeAPIReady");

    plPlayerWidth = window.innerWidth / 2;
    plPlayer = new YT.Player("pl-yt-player", {
        // Initial size, or call plPlayer.setSize(w,h) later
        width: plPlayerWidth,
        height: plPlayerWidth * 9 / 16,  
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

    // For demo/test purposes, switch to full screen after a few seconds
    // and a global key handler maps "f" to restore it to the quater size.
    setTimeout( function() { plPlayer.setSize(window.innerWidth, window.innerHeight) }, 5*1000 );
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
        plPlayer.setSize(plPlayerWidth, plPlayerWidth * 9 / 16);
    }
});

// ---

$(document).ready(plInit);
