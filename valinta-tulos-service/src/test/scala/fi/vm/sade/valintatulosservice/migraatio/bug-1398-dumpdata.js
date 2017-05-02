/*
* You can get the data from mongo for comparison before and after running the fix e.g. like this
 *  export HAKEMUSMONGO_PW=<secret>
 * time mongo -u oph -p $HAKEMUSMONGO_PW localhost:47017/hakulomake --quiet --authenticationDatabase admin ./bug-1398-dumpdata.js > /tmp/old.json
 * <do the fix>
 * time mongo -u oph -p $HAKEMUSMONGO_PW localhost:47017/hakulomake --quiet --authenticationDatabase admin ./bug-1398-dumpdata.js > /tmp/new.json
 * tkdiff /tmp/old.json /tmp/new.json
* */

var hakemusOids = db.bug1398applications.find().toArray().map(function(a) { return a.oid; });

var result = db.application.find({
  "applicationSystemId":"1.2.246.562.5.2013080813081926341927",
  "oid": { $in: hakemusOids }
});

printjson(result.toArray().sort(function(a, b) { return a.oid < b.oid ? 1 : -1; }));
