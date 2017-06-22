var glob = require('glob');
var fs = require('fs');
var assert = require('assert');

Array.prototype.diff = (a) => {
    return this.filter(i => a.indexOf(i) < 0);
};

var parseJson = (str) => {
  return JSON.parse(fs.readFileSync(str));
}

var getSijoittelu = (json) => {
  return json.Sijoittelu[0];
}

var iterateSijoitteluajot = (sijoitteluajot, hakuOid) => {
  for (ajo of sijoitteluajot) {
    if (hakuOid) ajo.hakuOid = hakuOid;
  }
}

var setTilankuvaus = (hakemus, tarkenne) => {
  hakemus.tilankuvauksenTarkenne = tarkenne;
}

var iterateHakemukset = (hakemukset) => {
  for (hakemus of hakemukset) {
    if (!("tilankuvauksenTarkenne" in hakemus)) setTilankuvaus(hakemus, "EI_TILANKUVAUKSEN_TARKENNETTA");
    if ("pisteet" in hakemus) hakemus.pisteet = parseInt(hakemus.pisteet);
  }
}

var iterateValintatapajonot = (valintatapajonot) => {
  for (valintatapajono of valintatapajonot) {
    iterateHakemukset(valintatapajono.hakemukset)
  }
}

var iterateHakukohteet = (hakukohteet, sijoitteluajoId) => {
  for (hakukohde of hakukohteet) {
    iterateValintatapajonot(hakukohde.valintatapajonot)
    if (!('kaikkiJonotSijoiteltu' in hakukohde)) hakukohde.kaikkiJonotSijoiteltu = true;
  }
}

var iterateValintatulokset = (tulokset) => {
  for (tulos of tulokset) {
  }
}

glob('./*.json', (err,files) => {
  var now = new Date().getTime();
  var i = 0;
  for (file of files.filter(f => f != './sijoittelu-basedata.json')) {
    console.log(file)
    var json = parseJson(file)
    var sijoittelu = getSijoittelu(json)
    if (!sijoittelu) continue;

    var hakuOid = null;

    if (!json.Valintatulos || json.Valintatulos.length) {
      var hakuOidsUnique = [...new Set(json.Valintatulos.map(v => {return v.hakuOid}))];
      assert.equal(hakuOidsUnique.length, 1);
      hakuOid = hakuOidsUnique[0];
      sijoittelu.hakuOid = hakuOid;
    }
    
    iterateSijoitteluajot(sijoittelu.sijoitteluajot, hakuOid);

    iterateHakukohteet(json.Hakukohde);

    iterateValintatulokset(json.Valintatulos);

    fs.writeFile(file, JSON.stringify(json, null, 2))
    i++;
  }
})