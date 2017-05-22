const utils = require('./utils');
const postText = utils.postText
const getLinesFromFile = utils.getLinesFromFile
const Promise = require("bluebird")
const median = require('median')

const http = utils.httpWithCas({
  "username": "YOUR CAS USERNAME",
  "password": "YOUR CAS PASSWORD",
  "service": "https://virkailija.opintopolku.fi/valinta-tulos-service"
})

var times = []
const getHetu = (hetu) => {
  const begin = new Date().getTime()
  const f = http.fetch('https://virkailija.opintopolku.fi/valinta-tulos-service/cas/kela/vastaanotot/henkilo', postText(hetu))
  return f.then(r => {
    const end = new Date().getTime()
    const took = (end - begin)
    times.push(took)
    return r
  })
}

function getHetuArray(objectList){
  var hetuList = [];
  Object.keys(objectList).forEach(function(key) {
          hetuList.push(objectList[key].hetu);
  });

  return hetuList;
}

function fixValues(o){
  if(!o.tutkinnontaso){
    o.tutkinnontaso = '---'
  }

  if(o.tutkinnonlaajuus2 || isNaN(o.tutkinnonlaajuus2)){
    o.tutkinnonlaajuus2 = '---'
  }
}

module.exports = {
  compareHetus: function(dump) {
    var jsonPromise = Promise.resolve(getHetuArray(dump)).mapSeries(h => getHetu(h)
    .then(r => r.json())
    .catch(r => console.error("Something went wrong: ", r)));

    var getDumpList = Promise.resolve(dump).then(function(r){return r});
    
    Promise.join(jsonPromise, getDumpList, function(restList, dumpList){
      var matchedCount = 0;
      var mismatchedCount = 0;
      
      for(var k in restList){
        for(var o in dumpList){
          var restObject = restList[k];
          var dumpObject = dumpList[o];

          if(restObject && dumpObject && restObject.henkilotunnus === dumpObject.hetu){
            for(var vo in restObject.vastaanotot){
              var vastaanotto = restObject.vastaanotot[vo];
              var hakukohde = vastaanotto.hakukohde.substring(vastaanotto.hakukohde.lastIndexOf(".")+1);
              fixValues(vastaanotto);
              if(hakukohde === dumpObject.hakukohde.trim()){
                if(vastaanotto.tutkinnontaso == dumpObject.tutkinnonTaso && vastaanotto.tutkinnonlaajuus1 == dumpObject.tutkinnonLaajuus1
                 && vastaanotto.tutkinnonlaajuus2 == dumpObject.tutkinnonLaajuus2){
                    matchedCount++;
                } else {
                    mismatchedCount++;
                }
              }
            }
          }
        }
      }
      console.log("Total count compared: " + dumpList.length)
      console.log("Matched: " + matchedCount)
      console.log("Didn't Match: " + mismatchedCount)
    });
  }
}