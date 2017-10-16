var puuttuvatAjaxCounter = 0;

// 1. Initial loading
function initializeUi() {
  puuttuvatFetch('/valinta-tulos-service/auth/login', { mode: 'no-cors' })
    .then(handleResponse)
    .then(loadHakuList)
    .catch(function(responseText) {
      showStatus(responseText);
  });
}
function loadHakuList() {
  showStatus('Ladataan kaikkien hakujen tiedot...');
  var url = '/valinta-tulos-service/auth/puuttuvat/yhteenveto';
  console.log('About to get from URL ' + url);
  showAjaxIndicator();
  puuttuvatFetch(url, { method: 'get'})
    .then(handleResponse)
    .then(displayHakuListResponse)
    .catch(handleResponse);
  puuttuvatFetch('/valinta-tulos-service/auth/puuttuvat/taustapaivityksenTila')
    .then(handleResponse)
    .then(function (responseText) {
      displayBackgroundUpdateStatus(JSON.parse(responseText));
    })
    .catch(handleResponse)
}
function displayHakuListResponse(responseText) {
  var list = document.getElementById('haku-list');
  var parsedResponse = JSON.parse(responseText);
  parsedResponse.forEach(function(row) {
    var rowElement = document.createElement("li");
    rowElement.classList.add('haku-row');
    var hakuOnTarkistettu = row.tarkistettu;
    var hakuRowText = 'Haku ' + row.hakuOid + ' : myöhäisin koulutuksen alkamiskausi ' + row.myohaisinKoulutuksenAlkamiskausi +
      ' , hakukohteita kaikkiaan ' + row.hakukohteidenLkm + ' , ' +
      (hakuOnTarkistettu ? 'puuttuvat tarkistettu ' + row.tarkistettu : 'puuttuvia ei ole tarkistettu') +
      (hakuOnTarkistettu ? ' , puuttuvia tuloksia yhteensä ' + (row.haunPuuttuvienMaara || 0) : '');
    var hakuRowTextSpan = document.createElement('span');
    hakuRowTextSpan.appendChild(document.createTextNode(hakuRowText));

    var tuloksiaPuuttuu = row.haunPuuttuvienMaara > 0;
    if (tuloksiaPuuttuu) {
      hakuRowTextSpan.classList.add('clickable');
    }
    if (hakuOnTarkistettu && !tuloksiaPuuttuu) {
      rowElement.classList.add('ei-loydy-puuttuvia');
    }
    hakuRowTextSpan.onclick = function() {
      if (rowElement.getElementsByClassName('tarjoaja-list').length === 0) {
        loadHakukohdeSpecificDataFor(row.hakuOid, rowElement);
      } else {
        rowElement.removeChild(rowElement.getElementsByTagName('ul')[0]);
      }
    };
    rowElement.appendChild(hakuRowTextSpan);
    rowElement.appendChild(createSingleHakuUpdatingInputsFor(row.hakuOid));
    list.appendChild(rowElement);
  });
  showStatus('Hakujen tiedot ladattu ' + new Date());
}

// 2. Global update
function updateMissingResultsForAllHakus() {
  var paivitaMyosOlemassaolevat = document.getElementById("paivitaMyosOlemassaolevat").checked;
  var request = {
    method: 'post',
    body: JSON.stringify({paivitaMyosOlemassaolevat: paivitaMyosOlemassaolevat}),
    headers: new Headers({'Content-Type': 'application/json'})
  };
  var statusSuffix = paivitaMyosOlemassaolevat ? ' tietojen päivitys puuttuvista tuloksista kaikille hauille.' :
            ' tietojen päivitys puuttuvista tuloksista hauille, joille ei vielä löydy tietoa puuttuvista.';
  showStatus('Käynnistetään' + statusSuffix + '..');
  showAjaxIndicator();
  puuttuvatFetch('/valinta-tulos-service/auth/puuttuvat/paivitaKaikki', request)
    .then(handleResponse)
    .then(function(responseText) {
      var taustapaivityksenTila = JSON.parse(responseText);
      displayBackgroundUpdateStatus(taustapaivityksenTila);
      if (taustapaivityksenTila.kaynnistettiin) {
        showStatus('Käynnistettiin' + statusSuffix);
      } else {
        showStatus('Ei käynnistetty taustapäivitystä, koska se on jo käynnistetty ' + taustapaivityksenTila.kaynnistetty +
          ' ' + taustapaivityksenTila.hakujenMaara + ' haulle.');
      }
    })
    .catch(handleResponse);
}

// 3. Load single haku data
function loadHakukohdeSpecificDataFor(hakuOid, hakuRowElement) {
  showAjaxIndicator();
  puuttuvatFetch('/valinta-tulos-service/auth/puuttuvat/haku/' + hakuOid, { method: 'get' })
    .then(handleResponse)
    .then(displaySingleHakuResponse(hakuRowElement))
    .catch(handleResponse);
}
function displaySingleHakuResponse(hakuRowElement) {
  return function(responseText) {
    var parsedResponse = JSON.parse(responseText);
    var tarjoajaList = document.createElement("ul");
    tarjoajaList.classList.add("tarjoaja-list");
    hakuRowElement.appendChild(tarjoajaList);
    parsedResponse.forEach(function(tarjoajaRow) {
      tarjoajaList.appendChild(createTarjoajaElement(tarjoajaRow));
    });
  };

}
function createTarjoajaElement(tarjoajaRow) {
  var tarjoajaElement = document.createElement("li");
  tarjoajaElement.appendChild(document.createTextNode(tarjoajaRow.tarjoajanNimi + " (" + tarjoajaRow.tarjoajaOid + ")"));
  var hakukohdeList = document.createElement("ul");
  tarjoajaRow.puuttuvatTulokset.forEach(function (hakukohdeRow) {
    var hakukohdeElement = document.createElement("li");
    var linkToSijoittelunTuloksetElement = document.createElement("a");
    linkToSijoittelunTuloksetElement.onclick = function (e) {
      e.stopPropagation();
    };
    var puuttuvaTulosText = hakukohdeRow.puuttuvienMaara === 1 ?
      "1 puuttuva tulos" : hakukohdeRow.puuttuvienMaara + " puuttuvaa tulosta";
    linkToSijoittelunTuloksetElement.appendChild(document.createTextNode(hakukohdeRow.kohteenNimi +
      " (" + hakukohdeRow.hakukohdeOid + "), " + puuttuvaTulosText));
    linkToSijoittelunTuloksetElement.href = hakukohdeRow.kohteenValintaUiUrl;
    hakukohdeElement.appendChild(linkToSijoittelunTuloksetElement);
    hakukohdeList.appendChild(hakukohdeElement);
  });
  tarjoajaElement.appendChild(hakukohdeList);
  return tarjoajaElement;
}
function createSingleHakuUpdatingInputsFor(hakuOid) {
  var wrapper = document.createElement("div");
  wrapper.classList.add('update-haku');
  var updateButton = document.createElement("button");
  updateButton.innerText = 'Päivitä haun tiedot puuttuvista';
  updateButton.onclick = function(e) {
    e.stopPropagation();
    var request = {
      method: 'post',
      body: JSON.stringify([ hakuOid ]),
      headers: new Headers({'Content-Type': 'application/json'})
    };
    puuttuvatFetch('/valinta-tulos-service/auth/puuttuvat/', request)
      .then(handleResponse)
      .then(function(responseText) {
        var taustapaivityksenTila = JSON.parse(responseText);
        displayBackgroundUpdateStatus(taustapaivityksenTila);
        if (taustapaivityksenTila.kaynnistettiin) {
          showStatus('Käynnistettiin taustapäivitys haulle ' + hakuOid);
        } else {
          showStatus('Ei käynnistetty taustapäivitystä haulle ' + hakuOid + ', koska se on jo käynnistetty ' +
            taustapaivityksenTila.kaynnistetty + ' ' + taustapaivityksenTila.hakujenMaara + ' haulle.');
        }
      })
      .catch(handleResponse);
    wrapper.appendChild(document.createTextNode('Käynnistettiin päivitys ' + new Date()));
  };
  wrapper.appendChild(updateButton);
  return wrapper;
}


// 4. More generic utilities and such
function displayBackgroundUpdateStatus(taustapaivityksenTila) {
  var finished = taustapaivityksenTila.valmistui;
  var statusBeginning = taustapaivityksenTila.kaynnistetty ?
      'Viimeisin päivitys ' + taustapaivityksenTila.hakujenMaara + ' haulle on käynnistetty ' + taustapaivityksenTila.kaynnistetty :
      'Päivitystä ei ole käynnistetty';
  var statusText = statusBeginning + (finished ? ' ja valmistui ' + finished : '');
  document.getElementById('latestBackgroundUpdateStatus').innerText = statusText;
}

function toggleShowingHakusWithNoMissingData() {
  var rowsOfHakusThatAreOk = document.getElementsByClassName('ei-loydy-puuttuvia');
  for (var i = 0; i < rowsOfHakusThatAreOk.length; i++) {
    var row = rowsOfHakusThatAreOk[i];
    row.style.display = (row.style.display === 'block' ? '' : 'block');
  }
}

function handleResponse(response) {
  markAjaxRequestFinished();
  if (response.status !== 200) {
    showStatus('Palvelimelta palasi virhekoodi ' + response.status);
    if (response.status === 401) {
      window.location.replace('/valinta-tulos-service/auth/login');
    }
  } else if (!response.text) {
    console.error('Kutsu palautti virheen', response);
    showStatus('Virhe palvelimelta: ' + response);
    return;
  }
  return response.text();
}

function showAjaxIndicator() {
  document.getElementById('ajaxIndicator').style.visibility = 'visible';
}
function hideAjaxIndicator() {
  document.getElementById('ajaxIndicator').style.visibility = 'hidden';
}

function showStatus(text) {
  document.getElementById('statusLine').innerText = text;
}

function puuttuvatFetch(url, request) {
  request = (request ? request : {});
  request.credentials = 'same-origin';
  showAjaxIndicator();
  puuttuvatAjaxCounter = puuttuvatAjaxCounter + 1;
  return fetch(url, request);
}

function markAjaxRequestFinished() {
  puuttuvatAjaxCounter = puuttuvatAjaxCounter - 1;
  if (puuttuvatAjaxCounter < 1) {
    hideAjaxIndicator();
  }
}
