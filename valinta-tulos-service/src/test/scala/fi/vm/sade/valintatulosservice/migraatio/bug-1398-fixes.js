// Ks. Jiran BUG-1398

// 1) hakutoiveiden korjaus hakemuksilta
//    **********************************
// First some background checks in haku-app
/*
db.application.find({"applicationSystemId":"1.2.246.562.5.2013080813081926341927"}) // (Ammatillisen koulutuksen ja lukiokoulutuksen kevään 2014 yhteishaku)
db.application.count({"applicationSystemId":"1.2.246.562.5.2013080813081926341927"}) // 83982
*/

var hakukohdeFixMappings = {
  "1.2.246.562.14.2013102510244944903778": "1.2.246.562.20.50072287449",
  "1.2.246.562.14.2013110813213398882225": "1.2.246.562.20.22011956772",
  "1.2.246.562.5.45309566409": "1.2.246.562.20.44280111129"
};

// There are almost 2000 applications with the wrong hakukohde oids
db.application.count({
  "applicationSystemId":"1.2.246.562.5.2013080813081926341927",
  $or: [
    {"answers.hakutoiveet.preference1-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) } },
    {"answers.hakutoiveet.preference2-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) }},
    {"answers.hakutoiveet.preference3-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) }},
    {"answers.hakutoiveet.preference4-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) }},
    {"answers.hakutoiveet.preference5-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) }},
    {"answers.hakutoiveet.preference6-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) }}
  ]
}); // 1991

// Let's store them in a safe place
db.application.find({
  "applicationSystemId":"1.2.246.562.5.2013080813081926341927",
  $or: [
    {"answers.hakutoiveet.preference1-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) } },
    {"answers.hakutoiveet.preference2-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) }},
    {"answers.hakutoiveet.preference3-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) }},
    {"answers.hakutoiveet.preference4-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) }},
    {"answers.hakutoiveet.preference5-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) }},
    {"answers.hakutoiveet.preference6-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) }}
  ]
}).forEach(function(a) {
  db.bug1398applications.insert(a)
});

// and then go about fixing the oids.
var deepValue = function(obj, path){
    for (var i=0, path=path.split('.'), len=path.length; i<len; i++){
        obj = obj[path[i]];
    };
    return obj;
};

function fixHakutoiveet(hakemusOid, dryRun) {
  print("Processing hakemus", hakemusOid)
  var oldHakutoiveOids = Object.keySet(hakukohdeFixMappings);

  // answers.hakutoiveet
  var toiveKeys = ["answers.hakutoiveet.preference1-Koulutus-id",
    "answers.hakutoiveet.preference2-Koulutus-id",
    "answers.hakutoiveet.preference3-Koulutus-id",
    "answers.hakutoiveet.preference4-Koulutus-id",
    "answers.hakutoiveet.preference5-Koulutus-id",
    "answers.hakutoiveet.preference6-Koulutus-id"];
  var hakemus = db.application.find({"oid": hakemusOid}).toArray()[0];
  toiveKeys.forEach(function(toiveKey) {
    var toiveOidOnHakemus = deepValue(hakemus, toiveKey);
    if (oldHakutoiveOids.indexOf(toiveOidOnHakemus) > -1) {
      var correctToiveOid = hakukohdeFixMappings[toiveOidOnHakemus];
      if (dryRun) {
        print('Would fix', toiveOidOnHakemus, 'to', correctToiveOid, 'on application', hakemusOid, toiveKey);
      } else {
        hakemus[toiveKey] = correctToiveOid;
      }
    }
  });

  // authorizationMeta
  if (hakemus.authorizationMeta.applicationPreferences) {
    hakemus.authorizationMeta.applicationPreferences.forEach(function(preference) {
      var toiveOidOnHakemus = preference.preferenceData["Koulutus-id"];
      if (oldHakutoiveOids.indexOf(toiveOidOnHakemus) > -1) {
        var correctToiveOid = hakukohdeFixMappings[toiveOidOnHakemus];
        if (dryRun) {
          print('Would fix', toiveOidOnHakemus, 'to', correctToiveOid, 'on application', hakemusOid, "authorizationMeta.applicationPreferences.preferenceData.Koulutus-id");
        } else {
          preference.preferenceData["Koulutus-id"] = correctToiveOid;
        }
      }
    });
  }

  // preferenceEligibilities
  if (hakemus.preferenceEligibilities) {
    hakemus.preferenceEligibilities.forEach(function(eligilibity) {
      var toiveOidOnHakemus = eligilibity.aoId;
      if (oldHakutoiveOids.indexOf(toiveOidOnHakemus) > -1) {
        var correctToiveOid = hakukohdeFixMappings[toiveOidOnHakemus];
        if (dryRun) {
          print('Would fix', toiveOidOnHakemus, 'to', correctToiveOid, 'on application', hakemusOid, "preferenceEligibilities.aoId");
        } else {
          eligilibity.aoId = correctToiveOid;
        }
      }
    });
  }

  // preferencesChecked
  if (hakemus.preferencesChecked) {
    hakemus.preferencesChecked.forEach(function(checked) {
      var toiveOidOnHakemus = checked.preferenceAoOid;
      if (oldHakutoiveOids.indexOf(toiveOidOnHakemus) > -1) {
        var correctToiveOid = hakukohdeFixMappings[toiveOidOnHakemus];
        if (dryRun) {
          print('Would fix', toiveOidOnHakemus, 'to', correctToiveOid, 'on application', hakemusOid, "hakemus.preferencesChecked.preferenceAoOid");
        } else {
          checked.preferenceAoOid = correctToiveOid;
        }
      }
    });
  }

  if (!dryRun) {
    db.application.update({oid: hakemusOid, _id: hakemus._id}, hakemus);
  }
}

// First try it out a bit with a single one
fixHakutoiveet(db.bug1398applications.find().toArray()[0].oid, true);

// And then do a bigger test run
db.bug1398applications.find().forEach(function(a) {
    fixHakutoiveet(a.oid, true);
});
// You should get a listing resembling this
/*
Would fix 1.2.246.562.14.2013102510244944903778 to 1.2.246.562.20.50072287449 on application 1.2.246.562.11.00000638867 answers.hakutoiveet.preference1-Koulutus-id
Would fix 1.2.246.562.14.2013102510244944903778 to 1.2.246.562.20.50072287449 on application 1.2.246.562.11.00000472036 authorizationMeta.applicationPreferences.preferenceData.Koulutus-id
Would fix 1.2.246.562.14.2013102510244944903778 to 1.2.246.562.20.50072287449 on application 1.2.246.562.11.00000472036 preferenceEligibilities.aoId
Would fix 1.2.246.562.14.2013102510244944903778 to 1.2.246.562.20.50072287449 on application 1.2.246.562.11.00000472036 hakemus.preferencesChecked.preferenceAoOid
Would fix 1.2.246.562.14.2013102510244944903778 to 1.2.246.562.20.50072287449 on application 1.2.246.562.11.00000472036
...
 */
// You might want to do some data dump before proceeding with the fix (see bug-1398-dumpdata.js )
// Make stuff happen:
fixHakutoiveet(db.bug1398applications.find().toArray()[0].oid, false);
// It should say something like "Updated 1 existing record(s) in 71ms".
// And if you ran it with the same argument again, nothing should be updated anymore.
// You should also check the single record to see that it's OK and the wanted changes are there.

// If you're sure that everything is perfect and beautiful, fix everything:
db.bug1398applications.find().forEach(function(a) {
    fixHakutoiveet(a.oid, false);
});
// and then check the results.
