var request = require('request');

var requests = 10000;
var skus = 500000;

function makeGetRequest() {
  var sku = makeRandomSKU();

  request('http://localhost:8181/product?sku=' + sku, function(err, res, body) {
    if (!err) {
      console.log("Status code: " + res.statusCode);
      console.log(body);
    } else {
      console.log(err);
    }
    if (requests > 0) {
      requests--;
      makeGetRequest();
    }
  });
}

function makeRandomSKU() {
  var skuNum = Math.floor(Math.random() * skus);
  skuNum = ("00000000"+(skuNum)).slice(-8);
  return "THING-" + skuNum;
}

function begin() {
  makeGetRequest();
}

begin();
