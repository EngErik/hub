require('./integration_config.js');

var channelName = utils.randomChannelName();
var jsonBody = JSON.stringify({ 'name': channelName, 'tags': ['foo-bar', 'bar', 'tag:z']});
var channelResource = channelUrl + '/' + channelName;
var testName = __filename;

utils.configureFrisby();

frisby.create(testName + ': Making sure channel resource does not yet exist.')
    .get(channelResource)
    .expectStatus(404)
    .after(function () {
        frisby.create('Test create channel with valid tags')
            .post(channelUrl, null, { body: jsonBody})
            .addHeader('Content-Type', 'application/json')
            .expectStatus(201)
            .expectJSON({'tags': ['bar', 'foo-bar', 'tag:z']})
            .afterJSON(function (result) {
                frisby.create(testName + ': Now fetching metadata')
                    .get(channelResource)
                    .expectStatus(200)
                    .expectHeader('content-type', 'application/json')
                    .expectJSON({'name': channelName})
                    .expectJSON({'tags': ['bar', 'foo-bar', 'tag:z']})
                    .toss();
            })
            .toss();

    })
    .toss();

