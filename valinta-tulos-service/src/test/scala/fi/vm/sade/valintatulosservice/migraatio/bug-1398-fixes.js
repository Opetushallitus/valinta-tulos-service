// Ks. Jiran BUG-1398

// 1) hakutoiveiden korjaus hakemuksilta
//    **********************************
// First some background checks in haku-app
/*
db.application.find({"applicationSystemId":"1.2.246.562.5.2013080813081926341927"}) // (Ammatillisen koulutuksen ja lukiokoulutuksen kevään 2014 yhteishaku)
db.application.count({"applicationSystemId":"1.2.246.562.5.2013080813081926341927"}) // 83982
*/

// https://virkailija.opintopolku.fi/tarjonta-app/index.html#/hakukohde/1.2.246.562.20.67124751198 vastaa kohdetta 1.2.246.562.5.42611100555

var hakukohdeFixMappings = {
  "1.2.246.562.14.2013102510244944903778": "1.2.246.562.20.50072287449",
  "1.2.246.562.14.2013110813213398882225": "1.2.246.562.20.22011956772",
  "1.2.246.562.5.45309566409": "1.2.246.562.20.44280111129",
  "1.2.246.562.5.42611100555": "1.2.246.562.20.67124751198"
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
        var suffix = toiveKey.split("\.")[2];
        hakemus.answers.hakutoiveet[suffix] = correctToiveOid;
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
// You might want to do some data dump before proceeding with the fix (see check-moved-hakukohteet-applications.js )
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


// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// 2) hakukohteiden korjaus Hakukohde-collectionista
//    **********************************
//
// Changing to sijoitteludb at this point.
// First some background checks in sijoitteludb

var hakukohdeFixMappings = {
  "1.2.246.562.14.2013102510244944903778": "1.2.246.562.20.50072287449",
  "1.2.246.562.14.2013110813213398882225": "1.2.246.562.20.22011956772",
  "1.2.246.562.5.45309566409": "1.2.246.562.20.44280111129",
  "1.2.246.562.5.42611100555": "1.2.246.562.20.67124751198"
};

var sijoitteluajoId = db.getCollection('Sijoittelu').find({"hakuOid":"1.2.246.562.5.2013080813081926341927"})[0].sijoitteluajot.sort(function(a, b) {
    return a.sijoitteluajoId < b.sijoitteluajoId ? 1 : -1;
})[0].sijoitteluajoId;
// 1418737548779 is the latest sijoitteluajoId of 1.2.246.562.5.2013080813081926341927 (Ammatillisen koulutuksen ja lukiokoulutuksen kevään 2014 yhteishaku)

db.Hakukohde.count({sijoitteluajoId: sijoitteluajoId}); // 2826

db.Hakukohde.count({sijoitteluajoId: sijoitteluajoId,
  oid: { $in: Object.keySet(hakukohdeFixMappings) } } ); // 4

// Store the erroneous Hakukohde documents in a safe place
db.Hakukohde.find({sijoitteluajoId: sijoitteluajoId,
  oid: { $in: Object.keySet(hakukohdeFixMappings) } } ).forEach(function(hk) {
    db.bug1398hakukohdes.insert(hk);
});

// And do it!
db.Hakukohde.find({sijoitteluajoId: sijoitteluajoId,
  oid: { $in: Object.keySet(hakukohdeFixMappings) } } ).forEach(function(hk) {
    var oldOid = hk.oid;
    var fixedOid = hakukohdeFixMappings[oldOid];
    print("Setting oid from", oldOid, "to", fixedOid, "in document", hk._id.toString());
    db.Hakukohde.update({_id: hk._id}, {$set: {
      oid: fixedOid
    } });
});


// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// 3) hakukohteiden korjaus Valintatulos-collectionista
//    **********************************
//
// Still in sijoitteludb.
// First some background checks in sijoitteludb

var hakukohdeFixMappings = {
  "1.2.246.562.14.2013102510244944903778": "1.2.246.562.20.50072287449",
  "1.2.246.562.14.2013110813213398882225": "1.2.246.562.20.22011956772",
  "1.2.246.562.5.45309566409": "1.2.246.562.20.44280111129",
  "1.2.246.562.5.42611100555": "1.2.246.562.20.67124751198"
};

db.Valintatulos.count({hakuOid: "1.2.246.562.5.2013080813081926341927",
  hakukohdeOid: { $in: Object.keySet(hakukohdeFixMappings) } } ); // 2037

// Store the erroneous Valintatulos documents in a safe place
db.Valintatulos.find({hakuOid: "1.2.246.562.5.2013080813081926341927",
  hakukohdeOid: { $in: Object.keySet(hakukohdeFixMappings) } } ).forEach(function(vt) {
    db.bug1398valintatulos.insert(vt);
});

// And do it!
db.Valintatulos.find({hakuOid: "1.2.246.562.5.2013080813081926341927",
  hakukohdeOid: { $in: Object.keySet(hakukohdeFixMappings) } } ).forEach(function(vt) {
    var oldOid = vt.hakukohdeOid;
    var fixedOid = hakukohdeFixMappings[oldOid];
    print("Setting oid from", oldOid, "to", fixedOid, "in document", vt._id.toString());
    db.Valintatulos.update({_id: vt._id}, {$set: {
      hakukohdeOid: fixedOid
    } });
});


// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// 4) vastaanottojen korjaus valintarekisteri-postgresistä
//    **********************************
//
// Now let's go to valintarekisteridb
// First some background checks again

/*

select count(*), year from (
select date_part('year', timestamp) as year from vastaanotot where hakukohde in (
      '1.2.246.562.14.2013102510244944903778',
      '1.2.246.562.14.2013110813213398882225',
      '1.2.246.562.5.45309566409',
      '1.2.246.562.5.4261110055'
)) as t
group by year;
-- 423   2015
-- 447   2014

select count(*), year, action from (
select date_part('year', timestamp) as year, action from vastaanotot where hakukohde in (
      '1.2.246.562.14.2013102510244944903778',
      '1.2.246.562.14.2013110813213398882225',
      '1.2.246.562.5.45309566409',
      '1.2.246.562.5.4261110055'
)) as t
group by year, action
order by year, action;
2	2014	MerkitseMyohastyneeksi
32	2014	Peru
413	2014	VastaanotaSitovasti
30	2015	Peru
393	2015	VastaanotaSitovasti

  */

// From sijoitteludb:
db.bug1398valintatulos.find({tila: {$ne: "KESKEN"} }, {_id: 0, "tila": 1}).toArray();
/*
$ pbpaste | grep tila | sort | uniq -c
      3         "tila" : "EI_VASTAANOTETTU_MAARA_AIKANA"
     33         "tila" : "PERUNUT"
    442         "tila" : "VASTAANOTTANUT"
*/

// Store old vastaanotto rows to a safe place
/*
create table bug1398vastaanotot (like vastaanotot);
insert into bug1398vastaanotot (select * from vastaanotot where hakukohde in (
      '1.2.246.562.14.2013102510244944903778',
      '1.2.246.562.14.2013110813213398882225',
      '1.2.246.562.5.45309566409',
      '1.2.246.562.5.4261110055'
));
commit;
*/

// Generate hakukohde insert code from sijoitteludb
Object.keySet(hakukohdeFixMappings).forEach(function(k) {
    var newHakukohdeOid = hakukohdeFixMappings[k];
    var hakuOid = "1.2.246.562.5.2013080813081926341927";
    print("insert into hakukohteet (hakukohde_oid, haku_oid, kk_tutkintoon_johtava, koulutuksen_alkamiskausi, yhden_paikan_saanto_voimassa) " +
     "values ('" + newHakukohdeOid + "', '" + hakuOid + "', false, '2014S', false);");
});
// And insert the hakukohde records to db.

// Ensure new hakukohde records are up to date wrt to tarjonta in valintarekisteri by passing the new oids to
// https://virkailija.opintopolku.fi/valinta-tulos-service/api-docs/index.html#!/virkistys/virkistaHakukohteet


// Generate db update code from sijoitteludb
db.bug1398valintatulos.find({tila: {$ne: "KESKEN"} }).forEach(function(vt) {
    var oldOid = vt.hakukohdeOid;
    var fixedOid = hakukohdeFixMappings[oldOid];
    var tilaMappings = {
        "EI_VASTAANOTETTU_MAARA_AIKANA": "MerkitseMyohastyneeksi",
        "PERUNUT": "Peru",
        "VASTAANOTTANUT": "VastaanotaSitovasti"
        };
    var valintarekisteriAction = tilaMappings[vt.tila];
    print("update vastaanotot set hakukohde = '" + fixedOid + "' where hakukohde = '" + oldOid +
        "' and henkilo = '" + vt.hakijaOid + "' and date_part('year', timestamp) = 2014 and action = '" + valintarekisteriAction + "';");
    } );

// Run the code to valintarekisteridb, and check the results before committing
/*
$ pbpaste |grep update | wc -l
478
$ pbpaste |grep '1 row affected' | wc -l
478
*/

// Now you can use the earlier check SQLs to see that there are no more results left for the old oids from the previous year.
