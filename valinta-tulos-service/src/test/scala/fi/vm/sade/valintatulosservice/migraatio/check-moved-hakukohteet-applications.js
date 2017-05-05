/*
* You can get the data from mongo for comparison before and after running the fix e.g. like this
 *  export HAKEMUSMONGO_PW=<secret>
 * time mongo -u oph -p $HAKEMUSMONGO_PW localhost:47017/hakulomake --quiet --authenticationDatabase admin ./check-moved-hakukohteet-applications.js > /tmp/old.json
 * <do the fix>
 * time mongo -u oph -p $HAKEMUSMONGO_PW localhost:47017/hakulomake --quiet --authenticationDatabase admin ./check-moved-hakukohteet-applications.js > /tmp/new.json
 * tkdiff /tmp/old.json /tmp/new.json
* */

var backupColletion = db.bug1400applications; // Change the correct collection here!
var oldHakuOid = "1.2.246.562.5.2014022711042555034240"; // change the correct haku OID here!

var hakemusOids = backupColletion.find().toArray().map(function(a) { return a.oid; });

var result = db.application.find({
  "applicationSystemId": oldHakuOid,
  "oid": { $in: hakemusOids }
});

printjson(result.toArray().sort(function(a, b) { return a.oid < b.oid ? 1 : -1; }));
