const Promise = require("bluebird")
const fetch = require('node-fetch')
const formurlencoded = require('form-urlencoded')
const urllib = require('url')
const fs = require('fs')
const parseActionAttributeFromHtml = (html) => {
  var actionAttr = 'action="'
  var actionInd = html.indexOf(actionAttr)
  var methodInd = html.indexOf('method="')
  return html.substring(actionInd + actionAttr.length, methodInd - 2)
}
const postURLEncoded = (data) => {
  const encoded = formurlencoded(data)
  return {
    "method": "POST",
    "body": encoded,
    "headers": {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Content-Length': new Buffer(encoded).length
    }
  }
}
const addQueryParam = (url, param) => {
  if(url.indexOf("?") == -1) {
    return url + "?" + param
  } else {
    return url + "&" + param
  }
}

module.exports = {
  getLinesFromFile: (file) => fs.readFileSync(file).toString().split("\n"),
  postText: (text) => {
    return {
      "method": "POST",
      "body": text,
      "headers": {
          'Content-Type': 'text/plain'
      }
    }
  },
  postURLEncoded: postURLEncoded,
  httpWithCas: function (configs) {
    var cookies = undefined
    const copyCookies = (response) => {
      const rawHeaders = response.headers.raw()
      const rawSetCookie = rawHeaders['set-cookie']
      if(rawSetCookie) {
        cookies = rawSetCookie.join('; ')
      }
      return response
    }
    const setCookies = (opts) => {
      var headers = opts.headers || {}
      headers['cookie'] = cookies
      opts.headers = headers
      return opts
    }

    return {
      "fetch": (url, options) => {
        if(cookies) {
          //console.log('fetching using cookies: ' + cookies)
          return fetch(url, setCookies(options))
        } else {
          return fetch(`https://${urllib.parse(url).host}/cas/v1/tickets`, postURLEncoded({
            "username": configs.username,
            "password": configs.password
          }))
          .then(copyCookies)
          .then(r => r.text())
          .then((response) => {
            const action = parseActionAttributeFromHtml(response)
            return fetch(action, setCookies(postURLEncoded({
              "service": configs.service
            })))
            .then(copyCookies)
            .then(r => r.text()).then((ticket) => {
              const urlWithTicket = addQueryParam(url, "ticket=" + ticket)
              return fetch(urlWithTicket, setCookies(options))
            }).then(copyCookies)
          })
        }
    }
  }
}
}
