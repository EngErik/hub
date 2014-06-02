require('./integration_config.js');

var request = require('request');
var http = require('http');
var groupName = utils.randomChannelName();
var testName = __filename;
var groupConfig = {
    callbackUrl : 'http://nothing/callback',
    channelUrl: 'http://nothing/channel/notHere',
    transactional: false
};

describe(testName, function () {

    utils.putGroup(groupName, groupConfig);

    var groupConfig2 = {
        callbackUrl : 'http://nothing/callback2',
        channelUrl: 'http://different/channel/notHere',
        transactional: true
    };

    utils.putGroup(groupName, groupConfig2, 409);

    utils.deleteGroup(groupName);

});

