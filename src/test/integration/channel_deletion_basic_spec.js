require('./integration_config.js');

var request = require('request');
var channelName = utils.randomChannelName();
var jsonBody = JSON.stringify({ "name": channelName});
var channelResource = channelUrl + "/" + channelName;
var testName = "channel_deletion_basic_spec";

describe(testName, function () {
    utils.createChannel(channelName);

    it("deletes channel", function (done) {
        request.del({url: channelResource},
            function (err, response, body) {
                expect(err).toBeNull();
                expect(response.statusCode).toBe(202);
                done();
            });

    });

    it("gets deleted channel", function (done) {
        request.get({url: channelResource},
            function (err, response, body) {
                expect(err).toBeNull();
                expect(response.statusCode).toBe(404);
                done();
            });
    });

});
