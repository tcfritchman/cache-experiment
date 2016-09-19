var options = {
  numberItems: 1000,
  batchSize: 25 // max 25
};

function deleteExistingTable() {
  // Delete any existing table
  var deleteparams = {
      TableName: "Products"
  };

  console.log("Deleting existing table");
  dynamodb.deleteTable(deleteparams, function(err, data) {
    logResult(err, data);
    createNewTable();
  });
}

function createNewTable() {
  // Create the table in dynamodb
  var tableparams = {
    TableName : "Products",
    KeySchema: [
      { AttributeName: "SKU", KeyType: "HASH" },
    ],
    AttributeDefinitions: [
      { AttributeName: "SKU", AttributeType: "S" },
    ],
    ProvisionedThroughput: {
      ReadCapacityUnits: 1,
      WriteCapacityUnits: 1
    }
  };

  console.log("Creating new table");
  dynamodb.createTable(tableparams, function(err, data) {
    logResult(err, data);
    addItemsToTable(options.numberItems);
  });
}

function addItemsToTable(remainingItems) {

    // Build a list of generated "products" to add to db
    var productItems = [];

    var itemsInBatch;
    if (remainingItems < options.batchSize) {
      itemsInBatch = remainingItems;
    } else {
      itemsInBatch = options.batchSize;
    }

    for (var i = 0; i < itemsInBatch; i++) {
      productNumber = ("00000000" + (options.numberItems - remainingItems)).slice(-8);
      sku = "THING-" + productNumber;
      // Random price between $1 and $1000
      price = ((Math.random() * 100001) + 100) / 10;

      productItems.push({
        PutRequest: {
          Item: {
            "SKU": sku,
            "Price": price,
            "Type": "Regular"
          }
        }
      });

      remainingItems--;
    }

    var writeparams = {
      RequestItems: {
        "Products": productItems
      }
    };

    // Write fake products to db
    console.log("writing batch, " + remainingItems + " items to go");
    docClient.batchWrite(writeparams, function(err, data) {
      logResult(err, data);
      if (remainingItems > 0) {
        addItemsToTable(remainingItems);
      }
    });
}

function logResult(err, data) {
    if (err) {
      console.log(JSON.stringify(err, null, 2));
    } else {
      console.log(JSON.stringify(data, null, 2));
    }
}

function begin() {
  deleteExistingTable();
}

begin();
