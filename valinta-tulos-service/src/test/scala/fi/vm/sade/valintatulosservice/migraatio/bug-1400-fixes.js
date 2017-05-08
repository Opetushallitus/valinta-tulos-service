// Ks. Jiran BUG-1400

// 0) Parametrit
//    ************************

var oldHakuOid = '1.2.246.562.5.2014022711042555034240'; // Perusopetuksen jälkeisen valmistavan koulutuksen kesän 2014 haku
var currentHakuOid = '1.2.246.562.29.14865319314'; // Perusopetuksen jälkeisen koulutuksen kevään 2015 haku



// 1) hakutoiveiden korjaus hakemuksilta
//    **********************************
// First some background checks in haku-app
db.application.find({"applicationSystemId": oldHakuOid});
db.application.count({"applicationSystemId": oldHakuOid}); // 4547

/*
https://virkailija.opintopolku.fi/tarjonta-app/index.html#/hakukohde/1.2.246.562.20.897007206610
vastaa kohdetta https://virkailija.opintopolku.fi/valintalaskenta-ui/app/index.html#/haku/1.2.246.562.29.14865319314/hakukohde/1.2.246.562.20.12572218035/perustiedot

ja https://virkailija.opintopolku.fi/tarjonta-app/index.html#/hakukohde/1.2.246.562.20.33593482731
vastaa kohdetta https://virkailija.opintopolku.fi/valintalaskenta-ui/app/index.html#/haku/1.2.246.562.29.14865319314/hakukohde/1.2.246.562.20.71447532327/perustiedot
*/


var hakukohdeFixMappings = {
  "1.2.246.562.20.12572218035": "1.2.246.562.20.897007206610",
  "1.2.246.562.20.71447532327": "1.2.246.562.20.33593482731"
};

// There are 22 applications with the wrong hakukohde oids
db.application.count({
  "applicationSystemId":oldHakuOid,
  $or: [
    {"answers.hakutoiveet.preference1-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) } },
    {"answers.hakutoiveet.preference2-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) }},
    {"answers.hakutoiveet.preference3-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) }},
    {"answers.hakutoiveet.preference4-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) }},
    {"answers.hakutoiveet.preference5-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) }},
    {"answers.hakutoiveet.preference6-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) }}
  ]
}); // 22

// Let's store them in a safe place
db.application.find({
  "applicationSystemId":oldHakuOid,
  $or: [
    {"answers.hakutoiveet.preference1-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) } },
    {"answers.hakutoiveet.preference2-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) }},
    {"answers.hakutoiveet.preference3-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) }},
    {"answers.hakutoiveet.preference4-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) }},
    {"answers.hakutoiveet.preference5-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) }},
    {"answers.hakutoiveet.preference6-Koulutus-id": { $in: Object.keySet(hakukohdeFixMappings) }}
  ]
}).forEach(function(a) {
  db.bug1400applications.insert(a)
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
fixHakutoiveet(db.bug1400applications.find().toArray()[0].oid, true);

// And then do a bigger test run
db.bug1400applications.find().forEach(function(a) {
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
// You might want to do some data dump before proceeding with the fix (see check-moved-hakukohteet-applications.js , be sure to read from correct collection )
// Make stuff happen:
fixHakutoiveet(db.bug1400applications.find().toArray()[0].oid, false);
// It should say something like "Updated 1 existing record(s) in 71ms".
// And if you ran it with the same argument again, nothing should be updated anymore.
// You should also check the single record to see that it's OK and the wanted changes are there.

// If you're sure that everything is perfect and beautiful, fix everything:
db.bug1400applications.find().forEach(function(a) {
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
  "1.2.246.562.20.12572218035": "1.2.246.562.20.897007206610",
  "1.2.246.562.20.71447532327": "1.2.246.562.20.33593482731"
};

var oldHakuOid = '1.2.246.562.5.2014022711042555034240'; // Perusopetuksen jälkeisen valmistavan koulutuksen kesän 2014 haku
var currentHakuOid = '1.2.246.562.29.14865319314'; // Perusopetuksen jälkeisen koulutuksen kevään 2015 haku

var sijoitteluajoId = db.getCollection('Sijoittelu').find({"hakuOid": oldHakuOid})[0].sijoitteluajot.sort(function(a, b) {
    return a.sijoitteluajoId < b.sijoitteluajoId ? 1 : -1;
})[0].sijoitteluajoId;

db.Hakukohde.count({sijoitteluajoId: sijoitteluajoId}); // 217

db.Hakukohde.count({sijoitteluajoId: sijoitteluajoId,
  oid: { $in: Object.keySet(hakukohdeFixMappings) } } ); // 2

// Store the erroneous Hakukohde documents in a safe place
db.Hakukohde.find({sijoitteluajoId: sijoitteluajoId,
  oid: { $in: Object.keySet(hakukohdeFixMappings) } } ).forEach(function(hk) {
    db.bug1400hakukohdes.insert(hk);
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
  "1.2.246.562.20.12572218035": "1.2.246.562.20.897007206610",
  "1.2.246.562.20.71447532327": "1.2.246.562.20.33593482731"
};

db.Valintatulos.count({hakuOid: oldHakuOid,
  hakukohdeOid: { $in: Object.keySet(hakukohdeFixMappings) } } ); // 13

// Store the erroneous Valintatulos documents in a safe place
db.Valintatulos.find({hakuOid: oldHakuOid,
  hakukohdeOid: { $in: Object.keySet(hakukohdeFixMappings) } } ).forEach(function(vt) {
    db.bug1400valintatulos.insert(vt);
});

// And do it!
db.Valintatulos.find({hakuOid: oldHakuOid,
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

  "1.2.246.562.20.12572218035": "1.2.246.562.20.897007206610",
  "1.2.246.562.20.71447532327": "1.2.246.562.20.33593482731"


select count(*), year from (
select date_part('year', timestamp) as year from vastaanotot where hakukohde in (
      '1.2.246.562.20.12572218035',
      '1.2.246.562.20.71447532327'
)) as t
group by year;
-- 13	2014
-- 20	2015

select count(*), year, action from (
select date_part('year', timestamp) as year, action from vastaanotot where hakukohde in (
      '1.2.246.562.20.12572218035',
      '1.2.246.562.20.71447532327'
)) as t
group by year, action
order by year, action;
1	2014	Peru
1	2015	Peru
9	2015	MerkitseMyohastyneeksi
10	2015	VastaanotaSitovasti
12	2014	VastaanotaSitovasti

  */

// From sijoitteludb:
db.bug1400valintatulos.find({tila: {$ne: "KESKEN"} }, {_id: 0, "tila": 1}).toArray();
/*
$ pbpaste | grep tila | sort | uniq -c
      1         "tila" : "PERUNUT"
     12         "tila" : "VASTAANOTTANUT"
*/

// Store old vastaanotto rows to a safe place
/*
create table bug1400vastaanotot (like vastaanotot);
insert into bug1400vastaanotot (select * from vastaanotot where hakukohde in (
      '1.2.246.562.20.12572218035',
      '1.2.246.562.20.71447532327'
));
commit;
*/

// Generate hakukohde insert code from sijoitteludb
Object.keySet(hakukohdeFixMappings).forEach(function(k) {
    var newHakukohdeOid = hakukohdeFixMappings[k];
    var hakuOid = oldHakuOid;
    print("insert into hakukohteet (hakukohde_oid, haku_oid, kk_tutkintoon_johtava, koulutuksen_alkamiskausi, yhden_paikan_saanto_voimassa) " +
     "values ('" + newHakukohdeOid + "', '" + hakuOid + "', false, '2014S', false);");
});
// And insert the hakukohde records to db.

// Ensure new hakukohde records are up to date wrt to tarjonta in valintarekisteri by passing the new oids to
// https://virkailija.opintopolku.fi/valinta-tulos-service/api-docs/index.html#!/virkistys/virkistaHakukohteet


// Generate db update code from sijoitteludb
hakijaOidsByHakemusOids = {};  // see below
db.bug1400valintatulos.find({tila: {$ne: "KESKEN"} }).forEach(function(vt) {
    var oldOid = vt.hakukohdeOid;
    var fixedOid = hakukohdeFixMappings[oldOid];
    var tilaMappings = {
        "EI_VASTAANOTETTU_MAARA_AIKANA": "MerkitseMyohastyneeksi",
        "PERUNUT": "Peru",
        "VASTAANOTTANUT": "VastaanotaSitovasti"
        };
    var valintarekisteriAction = tilaMappings[vt.tila];
    var hakijaOid = (vt.hakijaOid ? vt.hakijaOid : hakijaOidsByHakemusOids[vt.hakemusOid]);
    if (!hakijaOid) {
      throw "No hakijaOid for valintatulos of hakemus " + vt.hakemusOid
    }
    print("update vastaanotot set hakukohde = '" + fixedOid + "' where hakukohde = '" + oldOid +
        "' and henkilo = '" + hakijaOid + "' and date_part('year', timestamp) = 2014 and action = '" + valintarekisteriAction + "';");
    } );

// If you get
// uncaught exception: No hakijaOid for valintatulos of hakemus 1.2.246.562.11.00000892726
// it means that there are hakijaOids missing from valintatulos documents.
// You can find them from haku-app mongo like this:
var applicationArray = db.bug1400applications.find({}, {_id: 0, oid: 1, personOid: 1}).toArray();
var hakijaOidsByHakemusOids = {};
applicationArray.forEach(function(h) {
    hakijaOidsByHakemusOids[h.oid] = h.personOid;
    });
hakijaOidsByHakemusOids;
// and then copy-paste that into the sijoittelumongo session for the name hakijaOidsByHakemusOids and run again

// Run the code to valintarekisteridb, and check the results before committing
/*
$ pbpaste |grep update | wc -l
13

$ pbpaste |grep '1 row affected' | wc -l
13

*/

// Now you can use the earlier check SQLs to see that there are no more results left for the old oids from the previous year.
