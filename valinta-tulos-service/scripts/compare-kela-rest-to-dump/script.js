/*
    Install required packages with:
        > npm install

    Set fileLocation variable which is the location for the dump file
    Set your CAS username and CAS password in tools.js

    Run with:
        > node script.js
*/

let fs = require('fs');
let request = require('request');
let CASAPI = require('./tools.js')

let posList = {
    hakukohde: {start: '58', end: '80'},
    hetu: {start: '80', end: '91'},
    tutkinnonTaso: {start: '165', end: '168'},
    tutkinnonLaajuus1: {start: '180', end: '183'},
    tutkinnonLaajuus2: {start: '183', end: '186'}
}

let fileLocation = '';
let objectList = [];
fs.readFile(fileLocation, 'utf-8', function(err, data){
    if(err){
        return console.error("Something went wrong reading the file: ", err);
    }
    var lines = data.split(/\r?\n/);
    for(var i=0; i < lines.length; i++){
        let object = {}
        object.hetu = lines[i].substring(posList.hetu.start, posList.hetu.end);
        object.tutkinnonTaso = lines[i].substring(posList.tutkinnonTaso.start, posList.tutkinnonTaso.end);
        object.tutkinnonLaajuus1 = parseInt(lines[i].substring(posList.tutkinnonLaajuus1.start, posList.tutkinnonLaajuus1.end));
        object.tutkinnonLaajuus2 = parseInt(lines[i].substring(posList.tutkinnonLaajuus2.start, posList.tutkinnonLaajuus2.end));
        object.hakukohde = lines[i].substring(posList.hakukohde.start, posList.hakukohde.end);

        fixValues(object);
        if(object.hetu.trim().length == 11){
            objectList.push(object);
        }
    }
    CASAPI.compareHetus(objectList);
});

function fixValues(o){
    if(o.tutkinnonTaso.trim().length == 0){
        o.tutkinnonTaso = '---'
    }

    if(isNaN(o.tutkinnonLaajuus1)){
        o.tutkinnonLaajuus1 = '---';
    }

    if(isNaN(o.tutkinnonLaajuus2) || tutkinnonLaajuus2){
        o.tutkinnonLaajuus2 = '---'
    }
}