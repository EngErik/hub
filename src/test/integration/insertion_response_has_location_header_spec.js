require('./integration_config.js');

var channelName = utils.randomChannelName();
var thisChannelResource = channelUrl + "/" + channelName;
var messageText = "MY SUPER TEST CASE: this & <that>. " + Math.random().toString();
var testName = __filename;

utils.configureFrisby();

utils.runInTestChannel(testName, channelName, function () {
    frisby.create(testName + ': Inserting a value into a channel.')
        .post(thisChannelResource, null, { body : messageText})
        .addHeader("Content-Type", "text/plain")
        .expectStatus(201)
        .after(function (err, res, body) {
            var regex = new RegExp("^" + thisChannelResource.replace(/\//g, "\\/").replace(/\:/g, "\\:") + "\\/1000");
            expect(res.headers['location']).toMatch(regex);
        })
        .toss();
});
